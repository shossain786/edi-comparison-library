# Project Setup Guide

## 📦 Step 1: Import into Your Selenium Framework

### Option A: Maven Module (Recommended)

If your test framework uses Maven:

1. **Copy this entire folder** into your project:
   ```
   your-selenium-project/
   ├── edi-comparison-library/    ← Copy this folder here
   ├── pom.xml                     ← Your parent POM
   └── selenium-tests/
   ```

2. **Update your parent `pom.xml`**:
   ```xml
   <modules>
       <module>selenium-tests</module>
       <module>edi-comparison-library</module>
   </modules>
   ```

3. **Add dependency in your test module**:
   ```xml
   <dependency>
       <groupId>com.edi.comparison</groupId>
       <artifactId>edi-comparison-library</artifactId>
       <version>1.0.0-SNAPSHOT</version>
   </dependency>
   ```

### Option B: Standalone Library

If you want to keep it separate:

1. **Build the JAR**:
   ```bash
   cd edi-comparison-library
   mvn clean install
   ```

2. **Add to your project** via Maven coordinates or direct JAR reference.

---

## 🏗️ Current Project Structure

```
edi-comparison-library/
├── pom.xml                                    # Maven config (minimal deps)
├── README.md                                   # Library overview
├── .gitignore                                  # Git ignore rules
│
├── src/main/java/com/edi/comparison/
│   ├── core/                                   # Main API (Step 2)
│   │   ├── package-info.java                   # Architecture docs
│   │   ├── FileComparator.java                 # TODO: Main facade
│   │   └── ComparisonResult.java               # TODO: Result holder
│   │
│   ├── model/                                  # Domain models (Step 2)
│   │   ├── Message.java                        # TODO: Represents entire file
│   │   ├── Segment.java                        # TODO: Single segment
│   │   ├── Field.java                          # TODO: Field within segment
│   │   └── FileFormat.java                     # TODO: Enum (EDIFACT/ANSI/XML)
│   │
│   ├── parser/                                 # File parsers (Step 3)
│   │   ├── FileParser.java                     # TODO: Interface
│   │   ├── EdifactParser.java                  # TODO: EDIFACT impl
│   │   ├── AnsiX12Parser.java                  # TODO: ANSI impl
│   │   └── XmlParser.java                      # TODO: XML impl
│   │
│   ├── rule/                                   # Rule engine (Step 4)
│   │   ├── ComparisonRule.java                 # TODO: Rule model
│   │   ├── RuleLoader.java                     # TODO: YAML config loader
│   │   └── RuleEngine.java                     # TODO: Rule executor
│   │
│   ├── validator/                              # Validators (Step 5)
│   │   ├── FieldValidator.java                 # TODO: Interface
│   │   ├── ExactMatchValidator.java            # TODO: Built-in
│   │   ├── DateFormatValidator.java            # TODO: Built-in
│   │   └── CustomValidatorRegistry.java        # TODO: For user validators
│   │
│   ├── report/                                 # Report gen (Step 6)
│   │   ├── ReportGenerator.java                # TODO: Interface
│   │   ├── HtmlReportGenerator.java            # TODO: HTML impl
│   │   └── JsonReportGenerator.java            # TODO: JSON impl
│   │
│   └── exception/                              # Custom exceptions
│       ├── ParseException.java                 # TODO
│       ├── ValidationException.java            # TODO
│       └── ComparisonException.java            # TODO
│
├── src/main/resources/
│   ├── rules/                                  # Sample rule templates
│   │   └── iftmbf-template.yaml                # TODO: Example
│   │
│   └── templates/                              # HTML templates
│       └── comparison-report.html              # TODO: Report template
│
├── src/test/java/com/edi/comparison/          # Unit tests
│   └── (Will add as we build)
│
└── src/test/resources/
    ├── samples/                                # Sample EDI files for testing
    │   ├── sample-edifact.edi                  # TODO
    │   ├── sample-ansi.x12                     # TODO
    │   └── sample.xml                          # TODO
    │
    └── rules/                                  # Test rule configs
        └── test-rules.yaml                     # TODO
```

---

## ✅ Verification

After setup, verify Maven build:

```bash
cd edi-comparison-library
mvn clean compile
```

Expected output:
```
[INFO] BUILD SUCCESS
[INFO] Total time: X.XXX s
```

---

## 📋 Development Roadmap

### ✅ Step 1: Project Setup (DONE)
- Maven structure
- Dependencies
- Package layout

### 🔄 Step 2: Core Domain Models (NEXT)
We'll build:
- `Message`, `Segment`, `Field` classes
- `FileFormat` enum
- Basic `ComparisonResult` structure

**What you'll get**: Clean POJOs representing EDI structure

### 🔜 Step 3: Parser Layer
- Abstract parser interface
- EDIFACT parser (handles segment/field splitting)
- ANSI X12 parser
- XML parser

**What you'll get**: Convert any file → normalized `Message` object

### 🔜 Step 4: Rule Engine
- YAML rule loader
- Rule validation logic
- Context management (for testData access)

**What you'll get**: Define what to compare via config

### 🔜 Step 5: Comparison Engine
- Segment-by-segment comparison
- Handle multiple occurrences
- Order-sensitive vs order-insensitive logic

**What you'll get**: Core comparison logic

### 🔜 Step 6: Reporting
- HTML diff generator
- JSON report
- Summary statistics

**What you'll get**: Beautiful, actionable reports

### 🔜 Step 7: Integration & Polish
- Custom validator framework
- Memory context integration
- Performance optimization

---

## 🤝 Integration Points with Your Framework

```java
// In your Selenium test base class
public class EDITestBase {
    
    protected FileComparator comparator;
    protected Map<String, Object> testDataMap;
    
    @BeforeMethod
    public void setupComparator() {
        comparator = FileComparator.builder()
            .withRuleFile("rules/default-comparison.yaml")
            .withTestDataContext(testDataMap)
            .build();
    }
    
    protected void validateOutbound(String inbound, String outbound) {
        ComparisonResult result = comparator.compare(
            inbound, outbound, FileFormat.EDIFACT
        );
        
        if (result.hasDifferences()) {
            String reportPath = "reports/" + getTestName() + ".html";
            result.generateHtmlReport(reportPath);
            Assert.fail("Validation failed. See: " + reportPath);
        }
    }
}
```

---

## 🎯 Next Step

Ready for **Step 2: Domain Models**?

I'll create the core POJOs that represent:
- Message (entire file)
- Segment (like BGM, NAD)  
- Field (individual values)
- FileFormat enum

These will be immutable, well-documented, and ready to use.

**Shall I proceed with Step 2?**
