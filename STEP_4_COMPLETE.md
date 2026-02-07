# Step 4: Rule Engine - COMPLETE ✅

## What We Built

A powerful, YAML-based rule engine that defines **what to compare** and **how** with minimal code changes.

### Files Created

```
src/main/java/com/edi/comparison/rule/
├── ComparisonRule.java      - Rule for a single segment
├── FieldRule.java           - Rule for a single field (with validation types)
├── RuleSet.java             - Complete set of rules for a message type
└── RuleLoader.java          - Loads rules from YAML files

src/test/resources/rules/
├── iftmbf-booking.yaml      - Full production example
└── simple-test.yaml         - Simple test example

src/test/java/com/edi/comparison/rule/
└── RuleLoaderTest.java      - Comprehensive rule tests
```

---

## Usage Examples

### 1. Load Rules from YAML

```java
RuleLoader loader = new RuleLoader();

// From file path
RuleSet rules = loader.loadFromFile("rules/iftmbf-booking.yaml");

// From classpath resource
RuleSet rules = loader.loadFromResource("rules/default-comparison.yaml");

// From string (useful for testing)
String yaml = "message_type: IFTMBF\n" +
              "rules:\n" +
              "  - segment: BGM\n" +
              "    fields:\n" +
              "      - position: BGM.0001\n";
RuleSet rules = loader.loadFromString(yaml);
```

### 2. Query Rules

```java
RuleSet rules = loader.loadFromFile("rules/booking.yaml");

// Get rule for specific segment
ComparisonRule bgmRule = rules.getRuleForSegment("BGM");

// Check if rule exists
boolean hasNadRule = rules.hasRuleForSegment("NAD");

// Get all rules
List<ComparisonRule> allRules = rules.getRules();
```

### 3. Access Rule Properties

```java
ComparisonRule nadRule = rules.getRuleForSegment("NAD");

// Check segment options
boolean allowsMultiple = nadRule.isMultipleOccurrences();  // true
boolean orderMatters = nadRule.isOrderMatters();            // false
boolean isRequired = nadRule.isRequired();                  // true

// Get field rules
for (FieldRule fieldRule : nadRule.getFields()) {
    String position = fieldRule.getPosition();              // "NAD.3035"
    ValidationType validation = fieldRule.getValidation();  // EXACT_MATCH
    String source = fieldRule.getSource();                  // "testData.partyQualifier"
}
```

---

## YAML Rule Format

### Complete Example

```yaml
message_type: IFTMBF
description: Booking confirmation comparison

rules:
  # Simple exact match
  - segment: BGM
    required: true
    fields:
      - position: BGM.0001
        name: documentCode
        validation: exact_match
        expected_value: "340"
  
  # Match from test data
  - segment: BGM
    fields:
      - position: BGM.0002
        name: bookingNumber
        validation: exact_match
        source: testData.bookingNumber
  
  # Multiple occurrences allowed
  - segment: NAD
    multiple_occurrences: true
    order_matters: false
    fields:
      - position: NAD.3035
        validation: exact_match
      
      - position: NAD.C082.3039
        validation: pattern_match
        pattern: "^[A-Z0-9]{1,35}$"
  
  # Date format validation
  - segment: DTM
    multiple_occurrences: true
    fields:
      - position: DTM.C507.2380
        name: dateValue
        validation: date_format
        date_format_field: DTM.C507.2379  # Format code field
  
  # Optional segment
  - segment: EQD
    required: false
    multiple_occurrences: true
    fields:
      - position: EQD.C237.8260
        name: containerNumber
        validation: pattern_match
        pattern: "^[A-Z]{4}[0-9]{7}$"

config:
  ignore_trailing_whitespace: true
  case_sensitive: true
```

---

## Validation Types

### 1. EXACT_MATCH (default)
Exact string comparison.

```yaml
- position: BGM.0001
  validation: exact_match
  expected_value: "340"
```

### 2. PATTERN_MATCH
Regex pattern validation.

```yaml
- position: NAD.C082.3039
  validation: pattern_match
  pattern: "^[A-Z0-9]{1,35}$"
```

### 3. DATE_FORMAT
Date validation with format codes (102, 103, etc.).

```yaml
- position: DTM.C507.2380
  validation: date_format
  date_format_field: DTM.C507.2379  # Field containing format code
```

### 4. EXISTS
Just check field exists (value doesn't matter).

```yaml
- position: EQD.C224.8155
  validation: exists
```

### 5. CUSTOM
Use custom validator class (Step 7).

```yaml
- position: CTS.6066
  validation: custom
  custom_validator: com.mycompany.CargoTotalValidator
```

---

## Value Sources

### 1. Literal Value
```yaml
expected_value: "340"
```

### 2. From Test Data Context
```yaml
source: testData.bgmCode
source: testData.bookingNumber
```

### 3. From Inbound Message
```yaml
source: inbound.BGM.0002
source: inbound.NAD.3035
```

---

## Segment Options

### Required
```yaml
- segment: BGM
  required: true  # Segment must exist (default: true)
```

### Multiple Occurrences
```yaml
- segment: NAD
  multiple_occurrences: true  # Can appear multiple times
```

### Order Matters
```yaml
- segment: LOC
  order_matters: true  # Sequence must match
```

---

## Integration Example

```java
public class EDIComparisonTest {
    
    private RuleLoader ruleLoader;
    private Map<String, Object> testDataMap;
    
    @BeforeEach
    public void setup() {
        ruleLoader = new RuleLoader();
        testDataMap = new HashMap<>();
        testDataMap.put("bgmCode", "340");
        testDataMap.put("bookingNumber", "BOOKING123");
    }
    
    @Test
    public void testBookingComparison() throws Exception {
        // Load rules
        RuleSet rules = ruleLoader.loadFromFile("rules/iftmbf-booking.yaml");
        
        // Parse files
        EdifactParser parser = new EdifactParser();
        Message inbound = parser.parse(new File("inbound/booking.edi"));
        Message outbound = parser.parse(new File("outbound/booking.edi"));
        
        // Apply rules (Next step - comparison engine will use these)
        // For now, you can manually check rules:
        ComparisonRule bgmRule = rules.getRuleForSegment("BGM");
        Segment outboundBGM = outbound.getFirstSegmentByTag("BGM").get();
        
        for (FieldRule fieldRule : bgmRule.getFields()) {
            String actualValue = outboundBGM.getFieldValue(fieldRule.getPosition());
            String expectedValue = getExpectedValue(fieldRule, testDataMap);
            
            assertEquals(expectedValue, actualValue, 
                "Field " + fieldRule.getName() + " mismatch");
        }
    }
    
    private String getExpectedValue(FieldRule rule, Map<String, Object> testData) {
        if (rule.getExpectedValue() != null) {
            return rule.getExpectedValue();
        }
        if (rule.getSource() != null && rule.getSource().startsWith("testData.")) {
            String key = rule.getSource().substring("testData.".length());
            return (String) testData.get(key);
        }
        return null;
    }
}
```

---

## Real-World Scenario Example

Your actual use case with booking confirmation:

```yaml
# rules/booking-outbound-validation.yaml
message_type: IFTMBF
description: Validate outbound booking confirmation

rules:
  # BGM - Booking header
  - segment: BGM
    required: true
    fields:
      - position: BGM.C002.1001
        name: documentCode
        validation: exact_match
        source: testData.bgmCode  # From your datamap
      
      - position: BGM.1004
        name: bookingNumber
        validation: exact_match
        source: testData.bookingNumber
  
  # Multiple containers
  - segment: EQD
    multiple_occurrences: true
    order_matters: false
    required: false
    fields:
      - position: EQD.8053
        validation: exact_match
        expected_value: CN
      
      - position: EQD.C237.8260
        name: containerNumber
        validation: pattern_match
        pattern: "^[A-Z]{4}[0-9]{7}$"
  
  # DTM with format validation
  - segment: DTM
    multiple_occurrences: true
    fields:
      - position: DTM.C507.2005
        name: qualifier
        validation: exact_match
      
      - position: DTM.C507.2380
        name: dateValue
        validation: date_format
        date_format_field: DTM.C507.2379  # 102, 103, etc.
```

**Usage:**
```java
// Load rules once
RuleSet rules = ruleLoader.loadFromFile("rules/booking-outbound-validation.yaml");

// For each test scenario
testDataMap.put("bgmCode", "340");
testDataMap.put("bookingNumber", "BKG" + generateRandomNumber());

// Drop inbound, wait for outbound, compare
// (Next step will automate this comparison)
```

---

## Benefits

✅ **No Code Changes** - New scenarios = update YAML, not Java  
✅ **Declarative** - Rules are self-documenting  
✅ **Reusable** - Same rules across multiple tests  
✅ **Validation** - Rules are validated on load  
✅ **Flexible** - Supports all validation scenarios  
✅ **Type-Safe** - Enums for validation types  

---

## Testing

All rule loading is thoroughly tested:

```bash
mvn test -Dtest=RuleLoaderTest
```

**Test Coverage:**
- ✅ Load from file, resource, string, stream
- ✅ Multiple fields and segments
- ✅ All validation types
- ✅ Segment options (multiple, order, required)
- ✅ Source references (testData, inbound)
- ✅ Validation errors (missing segment, position)
- ✅ Rule queries (getRuleForSegment, hasRule)

---

## ✅ Step 4 Status: COMPLETE

### What We Achieved:
- ✅ Created rule model classes (ComparisonRule, FieldRule, RuleSet)
- ✅ Built YAML loader with validation
- ✅ Defined 5 validation types
- ✅ Support for multiple value sources
- ✅ Comprehensive test coverage
- ✅ Production-ready example rule file

### Total Code:
- **~600 lines** of rule engine code
- **~350 lines** of comprehensive tests
- **2 sample YAML** rule files

---

## 🎯 Next Step: Step 5 - Comparison Engine

In Step 5, we'll build the engine that **executes these rules** and compares messages:

```java
// What we'll create next:
ComparisonEngine engine = new ComparisonEngine(rules, testDataContext);
ComparisonResult result = engine.compare(inbound, outbound);

// Check results
if (result.hasDifferences()) {
    List<Difference> diffs = result.getDifferences();
    for (Difference diff : diffs) {
        System.out.println(diff.getDescription());
        System.out.println("  Expected: " + diff.getExpected());
        System.out.println("  Actual: " + diff.getActual());
        System.out.println("  Line: " + diff.getLineNumber());
    }
}
```

**Ready to proceed with Step 5?** 🚀
