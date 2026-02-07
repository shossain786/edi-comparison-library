# EDI Comparison Library

A lightweight, loosely-coupled library for comparing EDIFACT, ANSI X12, and XML files in test automation frameworks.

## Features

- **Multi-format Support**: EDIFACT, ANSI X12, and XML
- **Rule-based Validation**: Define comparison rules via YAML config
- **Flexible Validation**: Exact match, pattern match, date format, exists check
- **HTML Reports**: Beautiful, detailed reports with differences highlighted
- **Configurable**: External configuration file for report paths and settings
- **Loosely Coupled**: Easy to integrate with any test framework

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.edi.comparison</groupId>
    <artifactId>edi-comparison-library</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Create Configuration File

Create `edi-comparison.properties` in your project's `src/test/resources/`:

```properties
# Report Configuration
report.base.dir=target/reports
report.filename.pattern=report_{timestamp}.html

# Comparison Configuration
comparison.fail.on.first.error=false
comparison.case.sensitive=true
```

### 3. Create Verification Template (YAML)

Create your template in `src/test/resources/rules/my-template.yaml`:

```yaml
message_type: IFTMBC
description: Booking confirmation verification

rules:
  - segment: BGM
    required: true
    fields:
      - position: BGM.0001
        name: messageFunction
        validation: exact_match
        expected_value: "620"

      - position: BGM.0002
        name: bookingReference
        validation: pattern_match
        pattern: "^BK[0-9]+$"

  - segment: NAD
    required: true
    multiple_occurrences: true
    fields:
      - position: NAD.0001
        name: partyQualifier
        validation: exists

  - segment: EQD
    required: true
    multiple_occurrences: true
    fields:
      - position: EQD.0001
        validation: exact_match
        expected_value: "CN"

      - position: EQD.0002
        name: containerNumber
        validation: pattern_match
        pattern: "^[A-Z]{4}[0-9]{7}$"
```

### 4. Write Your Test

```java
import com.edi.comparison.config.ComparisonConfig;
import com.edi.comparison.core.*;
import com.edi.comparison.parser.EdifactParser;
import com.edi.comparison.report.HtmlReportGenerator;
import com.edi.comparison.rule.RuleLoader;
import com.edi.comparison.rule.RuleSet;

public class MyVerificationTest {

    @Test
    void testOutboundVerification() throws Exception {
        // 1. Load configuration
        ComparisonConfig config = ComparisonConfig.load();

        // 2. Load template
        RuleLoader ruleLoader = new RuleLoader();
        RuleSet template = ruleLoader.loadFromResource("rules/my-template.yaml");

        // 3. Parse outbound file
        EdifactParser parser = new EdifactParser();
        Message outbound = parser.parse(outboundFileContent);

        // 4. Setup context with test data
        Map<String, Object> testData = new HashMap<>();
        testData.put("bookingNumber", "BK123456");

        ComparisonContext context = ComparisonContext.builder()
                .testData(testData)
                .build();

        // 5. Run verification
        ComparisonEngine engine = new ComparisonEngine(template, context);
        ComparisonResult result = engine.compare(null, outbound);

        // 6. Generate HTML report
        HtmlReportGenerator reportGenerator = new HtmlReportGenerator();
        String reportPath = reportGenerator.generate(
            result,
            config.getReportBaseDir(),  // From properties file
            "my-scenario"               // Scenario folder name
        );

        // 7. Assert
        assertTrue(result.isSuccess(),
            "Verification failed. Report: " + reportPath);
    }
}
```

## Configuration

### Properties File Location

Create `edi-comparison.properties` in your project:

| Location | When Used |
|----------|-----------|
| `src/test/resources/edi-comparison.properties` | For test execution |
| `src/main/resources/edi-comparison.properties` | For runtime usage |
| External path via `ComparisonConfig.load("/path/to/file")` | Custom location |

### Available Properties

```properties
# Base directory for all reports
report.base.dir=target/reports

# Filename pattern ({timestamp} is replaced with yyyyMMdd_HHmmss)
report.filename.pattern=report_{timestamp}.html

# Stop on first error or collect all
comparison.fail.on.first.error=false

# Case sensitive string comparison
comparison.case.sensitive=true
```

### Loading Configuration

```java
// Load from classpath (edi-comparison.properties)
ComparisonConfig config = ComparisonConfig.load();

// Load from external file
ComparisonConfig config = ComparisonConfig.load("C:/config/my-config.properties");

// Access properties
String reportDir = config.getReportBaseDir();
boolean caseSensitive = config.isCaseSensitive();
```

## Report Generation

### Report Methods

```java
HtmlReportGenerator generator = new HtmlReportGenerator();

// Option 1: Direct path (creates directories automatically)
generator.generate(result, "target/reports/my-report.html");

// Option 2: Base dir + scenario (auto-generates timestamped filename)
generator.generate(result, "target/reports", "booking-test");
// Creates: target/reports/booking-test/report_20260207_123456.html

// Option 3: Base dir + scenario + custom filename
generator.generate(result, "target/reports", "booking-test", "verification-result.html");
// Creates: target/reports/booking-test/verification-result.html
```

### Report Structure

```
target/reports/
├── booking-confirmation/
│   ├── report_20260207_100000.html
│   └── report_20260207_110000.html
├── booking-cancellation/
│   └── report_20260207_120000.html
└── shipment-advice/
    └── failure-report.html
```

## Validation Types

| Type | Description | Example |
|------|-------------|---------|
| `exact_match` | Exact string comparison | `expected_value: "620"` |
| `pattern_match` | Regex pattern matching | `pattern: "^[A-Z]{4}[0-9]{7}$"` |
| `exists` | Field must be present | Just checks presence |
| `date_format` | Validates date format | Uses format code field |

## Template YAML Reference

```yaml
message_type: IFTMBC
description: Template description

rules:
  - segment: BGM                    # Segment tag
    required: true                  # Is segment required?
    multiple_occurrences: false     # Can appear multiple times?
    order_matters: true             # Must maintain order?
    fields:
      - position: BGM.0001          # Field position
        name: fieldName             # Human-readable name
        validation: exact_match     # Validation type
        expected_value: "620"       # Expected literal value
        # OR
        source: testData.myKey      # Get expected from test data
        # OR
        pattern: "^[A-Z]+$"         # Regex pattern
        required: true              # Is field required?
```

## Project Structure

```
your-project/
├── src/test/
│   ├── java/
│   │   └── MyVerificationTest.java
│   └── resources/
│       ├── edi-comparison.properties    ← Configuration
│       ├── rules/
│       │   └── my-template.yaml         ← Verification templates
│       └── samples/
│           └── outbound-files/          ← Test data
└── target/reports/                      ← Generated reports
    └── scenario-name/
        └── report_20260207_123456.html
```

## License

Internal use - Your organization
