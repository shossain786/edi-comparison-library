# 🚀 Quick Reference - EDI Comparison Library

## Generate Your First Report (30 seconds)

### Step 1: Run the Demo
```
1. Open: src/test/java/com/edi/comparison/demo/GenerateReportDemo.java
2. Right-click → Run main()
3. Look for console output showing report location
```

### Step 2: Open the Report
```
File: comparison-report.html (in project root)
Action: Double-click to open in browser
```

### Step 3: See the Differences
```
✗ FAILED
Differences: 3
- Booking number mismatch (line 2)
- Invalid container format (line 4)
- Invalid date format (line 6)
```

---

## Basic Usage Pattern

```java
// 1. Load rules
RuleSet rules = new RuleLoader().loadFromFile("rules/booking.yaml");

// 2. Setup context with test data
ComparisonContext context = ComparisonContext.builder()
    .testData(yourDataMap)
    .build();

// 3. Parse files
EdifactParser parser = new EdifactParser();
Message inbound = parser.parse(inboundFile);
Message outbound = parser.parse(outboundFile);

// 4. Compare
ComparisonEngine engine = new ComparisonEngine(rules, context);
ComparisonResult result = engine.compare(inbound, outbound);

// 5. Generate report
if (result.hasDifferences()) {
    result.generateHtmlReport("reports/test-report.html");
    fail("Validation failed");
}
```

---

## File Locations Cheat Sheet

### Demo Outputs
```
comparison-report.html           ← GenerateReportDemo output
sample-comparison-report.html    ← ReportExample output
```

### Your Test Reports
```
target/reports/your-test.html    ← Recommended location
```

### Rule Files
```
src/test/resources/rules/
├── iftmbf-booking.yaml          ← Full example
└── simple-test.yaml             ← Simple example
```

---

## Common Commands

### Run Demo
```bash
# From IDE: Right-click GenerateReportDemo.java → Run

# From command line:
mvn test-compile exec:java \
  -Dexec.mainClass="com.edi.comparison.demo.GenerateReportDemo"
```

### Run Tests
```bash
mvn test
mvn test -Dtest=ComparisonEngineTest
mvn test -Dtest=HtmlReportGeneratorTest
```

### Open Report (from project root)
```bash
# Mac
open comparison-report.html

# Windows
start comparison-report.html

# Linux
xdg-open comparison-report.html
```

---

## Sample YAML Rule

```yaml
message_type: IFTMBF
rules:
  - segment: BGM
    fields:
      - position: BGM.0001
        validation: exact_match
        source: testData.bgmCode
      
      - position: BGM.0002
        validation: exact_match
        source: testData.bookingNumber
  
  - segment: EQD
    multiple_occurrences: true
    fields:
      - position: EQD.C237.8260
        validation: pattern_match
        pattern: "^[A-Z]{4}[0-9]{7}$"
```

---

## Validation Types Quick Ref

| Type | Usage | Example |
|------|-------|---------|
| `exact_match` | String equality | `expected_value: "340"` |
| `pattern_match` | Regex validation | `pattern: "^[A-Z]{4}[0-9]{7}$"` |
| `date_format` | Date validation | `date_format_field: DTM.C507.2379` |
| `exists` | Field present | Just checks existence |
| `custom` | Your logic | Step 7 (optional) |

---

## Troubleshooting One-Liners

**Can't find report?**
```java
System.out.println(new File("comparison-report.html").getAbsolutePath());
```

**Report empty?**
```java
String html = result.generateHtmlReport();
System.out.println("HTML size: " + html.length());
```

**Rules not loading?**
```java
RuleSet rules = new RuleLoader().loadFromResource("rules/simple-test.yaml");
System.out.println("Rules loaded: " + rules.getRuleCount());
```

**Parser failing?**
```java
EdifactParser parser = new EdifactParser();
System.out.println("Can parse: " + parser.canParse(content));
```

---

## Integration Checklist

- [ ] Run GenerateReportDemo.java
- [ ] Open comparison-report.html
- [ ] Understand the report structure
- [ ] Copy sample YAML rule to your project
- [ ] Modify rule to match your message type
- [ ] Update test data map keys
- [ ] Create your test following the pattern
- [ ] Run test and check report
- [ ] Celebrate! 🎉

---

## Support Files Included

| File | Purpose |
|------|---------|
| `GenerateReportDemo.java` | Generate sample report |
| `ReportExample.java` | Alternative demo |
| `HOW_TO_TEST.md` | Detailed guide |
| `STEP_6_COMPLETE.md` | Full documentation |
| `iftmbf-booking.yaml` | Example rules |

---

## One-Command Test

```bash
# Just run this:
mvn test-compile exec:java \
  -Dexec.mainClass="com.edi.comparison.demo.GenerateReportDemo"

# Then open:
comparison-report.html
```

**That's it! You should see a beautiful comparison report! 🎯**
