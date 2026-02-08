# EDI Comparison Library - User Guide

A lightweight Java library for validating EDIFACT, ANSI X12, and XML messages in test automation. Define what to check via YAML templates, run comparisons, and get detailed HTML reports.

---

## Table of Contents

1. [Add to Your Project](#1-add-to-your-project)
2. [Configure](#2-configure)
3. [Create a YAML Template](#3-create-a-yaml-template)
4. [Write a Test](#4-write-a-test)
5. [Run and View Reports](#5-run-and-view-reports)
6. [YAML Template Reference](#6-yaml-template-reference)
7. [Report Generation Options](#7-report-generation-options)
8. [Combined Report](#8-combined-report)
9. [Configuration Reference](#9-configuration-reference)
10. [Project Structure](#10-project-structure)

---

## 1. Add to Your Project

### Option A: Maven Multi-Module (if your project uses Maven)

Copy the `edi-comparison-library/` folder into your project root, then:

**Parent `pom.xml`:**
```xml
<modules>
    <module>your-existing-module</module>
    <module>edi-comparison-library</module>
</modules>
```

**Your test module's `pom.xml`:**
```xml
<dependency>
    <groupId>com.edi.comparison</groupId>
    <artifactId>edi-comparison-library</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Option B: Install as Local JAR

```bash
cd edi-comparison-library
mvn clean install
```

Then add the same `<dependency>` block above to your project's `pom.xml`.

---

## 2. Configure

Create `edi-comparison.properties` in your project's `src/test/resources/`:

```properties
# Where reports are saved
report.base.dir=target/reports

# Stop on first error (false = collect all errors)
comparison.fail.on.first.error=false

# Case sensitive string comparison
comparison.case.sensitive=true

# Flag segments in the file that are not in your template
comparison.detect.unexpected.segments=true

# Validate that segments appear in template-defined order
comparison.validate.segment.order=true
```

All properties have sensible defaults, so this file is optional. If absent, the library uses the defaults shown above.

---

## 3. Create a YAML Template

A template defines **what to validate** in an EDI message. Create it under `src/test/resources/rules/`.

### Minimal Example (`rules/simple-booking.yaml`)

```yaml
message_type: TEST
description: Verify booking number and shipper exist

rules:
  - segment: BGM
    fields:
      - position: BGM.0001
        validation: exact_match
        expected_value: "340"

      - position: BGM.0002
        validation: exact_match
        source: testData.bookingNumber    # value comes from test code

  - segment: NAD
    multiple_occurrences: true
    fields:
      - position: NAD.0001
        validation: exists                # just check the field is present
```

### Full Example (`rules/booking-confirmation.yaml`)

```yaml
message_type: IFTMBC
description: Booking confirmation verification

rules:
  - segment: BGM
    required: true
    expected_count: 1                      # exactly 1 BGM segment expected
    fields:
      - position: BGM.0001
        name: messageFunction
        validation: exact_match
        expected_value: "620"

      - position: BGM.0002
        name: bookingReference
        validation: pattern_match
        pattern: "^BK[0-9]+$"

  - segment: DTM
    required: true
    multiple_occurrences: true
    expected_count: 6
    fields:
      - position: DTM.C001.0001
        name: dateQualifier
        validation: exists

      - position: DTM.C001.0002
        name: dateValue
        validation: pattern_match
        pattern: "^[0-9]{8}$|^[0-9]{12}$"

  - segment: NAD
    required: true
    multiple_occurrences: true
    expected_count: 4
    order_matters: false                   # NAD segments can appear in any order
    fields:
      - position: NAD.0001
        name: partyQualifier
        validation: exists

  - segment: EQD
    required: true
    multiple_occurrences: true
    expected_count: 2
    fields:
      - position: EQD.0001
        name: equipmentQualifier
        validation: exact_match
        expected_value: "CN"

      - position: EQD.0002
        name: containerNumber
        validation: pattern_match
        pattern: "^[A-Z]{4}[0-9]{7}$"

  - segment: CNT
    required: true
    fields:
      - position: CNT.C001.0001
        name: controlQualifier
        validation: exact_match
        expected_value: "16"
```

---

## 4. Write a Test

### Basic Test (JUnit 5)

```java
import com.edi.comparison.config.ComparisonConfig;
import com.edi.comparison.core.ComparisonContext;
import com.edi.comparison.core.ComparisonEngine;
import com.edi.comparison.model.ComparisonResult;
import com.edi.comparison.model.Message;
import com.edi.comparison.parser.EdifactParser;
import com.edi.comparison.report.HtmlReportGenerator;
import com.edi.comparison.rule.RuleLoader;
import com.edi.comparison.rule.RuleSet;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BookingVerificationTest {

    @Test
    void verifyBookingOutbound() throws Exception {
        // 1. Load config and template
        ComparisonConfig config = ComparisonConfig.load();
        RuleSet template = new RuleLoader().loadFromResource("rules/simple-booking.yaml");

        // 2. Parse the EDI message you want to validate
        EdifactParser parser = new EdifactParser();
        String ediContent = "BGM+340+BOOKING123+9'\nNAD+CZ+SHIPPER001::92'";
        Message outbound = parser.parse(ediContent);

        // 3. Build context with test data (values referenced by 'source: testData.xxx')
        Map<String, Object> testData = new HashMap<>();
        testData.put("bookingNumber", "BOOKING123");

        ComparisonContext context = ComparisonContext.builder()
                .testData(testData)
                .addConfig("detect_unexpected_segments", config.isDetectUnexpectedSegments())
                .build();

        // 4. Run comparison
        ComparisonEngine engine = new ComparisonEngine(template, context);
        ComparisonResult result = engine.compare(null, outbound);

        // 5. Generate HTML report
        HtmlReportGenerator report = new HtmlReportGenerator();
        String reportPath = report.generate(result, config.getReportBaseDir(), "booking-test");

        // 6. Assert
        assertTrue(result.isSuccess(), "Verification failed. Report: " + reportPath);
    }
}
```

### Loading EDI from a File (e.g. downloaded from FTP)

```java
// From classpath resource
try (InputStream is = getClass().getClassLoader().getResourceAsStream("samples/outbounds/my-file")) {
    String content = new String(is.readAllBytes());
    Message outbound = parser.parse(content);
}

// From filesystem
String content = Files.readString(Path.of("/path/to/edi-file.edi"));
Message outbound = parser.parse(content);
```

### Using Inbound Reference Values

If you want to check that outbound field values match the inbound:

```yaml
# In your YAML template
- position: BGM.0002
  validation: exact_match
  source: inbound.BGM.0002        # compare against the inbound message's BGM.0002
```

```java
// In your test, pass both messages
Message inbound = parser.parse(inboundContent);
Message outbound = parser.parse(outboundContent);

ComparisonContext context = ComparisonContext.builder()
        .testData(testData)
        .inboundMessage(inbound)       // needed when template uses 'inbound.xxx'
        .build();

ComparisonResult result = engine.compare(inbound, outbound);
```

---

## 5. Run and View Reports

### Run Tests

```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=BookingVerificationTest

# Run a specific test method
mvn test -Dtest=BookingVerificationTest#verifyBookingOutbound
```

### View Reports

Reports are saved under the configured `report.base.dir` (default: `target/reports/`):

```
target/reports/
  booking-test/
    report_20260208_143000.html       <-- individual report
  combined_report_20260208_143000.html  <-- combined report (if using @AfterAll)
```

Open any `.html` file in your browser:

```bash
# Windows
start target\reports\booking-test\report_20260208_143000.html

# Mac/Linux
open target/reports/booking-test/report_20260208_143000.html
```

### What the Report Shows

- **Status badge** -- PASSED (green) or FAILED (red)
- **Statistics** -- differences count, segments compared, fields compared, time taken
- **Difference details** -- grouped by failure category, each showing:
  - Difference type (value mismatch, missing segment, pattern mismatch, etc.)
  - Location (segment, field position, line number)
  - Expected vs actual values side-by-side

---

## 6. YAML Template Reference

### Segment-Level Options

```yaml
rules:
  - segment: BGM                   # segment tag to match
    required: true                 # fail if segment is missing (default: false)
    multiple_occurrences: true     # segment can appear more than once (default: false)
    expected_count: 3              # exact number of occurrences expected (optional)
    order_matters: true            # occurrences must be in order (default: true)
    fields: [...]
```

### Field-Level Options

```yaml
fields:
  - position: BGM.0001            # field position within the segment (required)
    name: messageFunction          # human-readable label for reports (optional)
    required: true                 # fail if field is missing (default: false)
    validation: exact_match        # validation type (see table below)
    expected_value: "620"          # literal expected value
    source: testData.bookingNum    # OR get expected value from test data / inbound
    pattern: "^[A-Z]+$"           # regex pattern (for pattern_match)
```

### Validation Types

| Type | What It Does | Required Property |
|------|-------------|-------------------|
| `exact_match` | Value must equal expected exactly | `expected_value` or `source` |
| `pattern_match` | Value must match a regex | `pattern` |
| `exists` | Field must be present (any value) | (none) |
| `date_format` | Validates date format code | (uses format code field) |

### Source Resolution

The `source` property pulls expected values dynamically:

| Source Pattern | Resolves To |
|---------------|-------------|
| `testData.bookingNumber` | `context.getTestDataString("bookingNumber")` |
| `inbound.BGM.0002` | Field `BGM.0002` from the inbound message |

---

## 7. Report Generation Options

```java
HtmlReportGenerator report = new HtmlReportGenerator();

// Option 1: Base directory + scenario name (auto-generates timestamped filename)
report.generate(result, "target/reports", "my-scenario");
// -> target/reports/my-scenario/report_20260208_143000.html

// Option 2: Base directory + scenario name + custom filename
report.generate(result, "target/reports", "my-scenario", "latest.html");
// -> target/reports/my-scenario/latest.html

// Option 3: Explicit full path
report.generate(result, "target/reports/my-report.html");
// -> target/reports/my-report.html

// Option 4: Get HTML as a string (no file written)
String html = report.generateHtml(result);
```

All options create parent directories automatically.

---

## 8. Combined Report

When you have multiple test scenarios, generate a single dashboard page showing all results.

### Setup

```java
import com.edi.comparison.report.HtmlReportGenerator.ScenarioResult;
import org.junit.jupiter.api.AfterAll;

class MyVerificationTests {

    // Collect results from all tests
    private static final List<ScenarioResult> scenarioResults =
            Collections.synchronizedList(new ArrayList<>());

    @Test
    void testScenarioA() throws Exception {
        // ... run comparison ...
        ComparisonResult result = engine.compare(null, outbound);

        // Add to collection (3rd argument = did this test pass?)
        scenarioResults.add(new ScenarioResult("Scenario A", result, result.isSuccess()));

        // Individual report still generated
        report.generate(result, config.getReportBaseDir(), "scenario-a");

        assertTrue(result.isSuccess());
    }

    @Test
    void testExpectedFailure() throws Exception {
        // ... run comparison that is expected to fail ...
        ComparisonResult result = engine.compare(null, outbound);

        // For expected-failure tests, pass !result.isSuccess() as the 'passed' flag
        scenarioResults.add(new ScenarioResult("Expected Failure", result, !result.isSuccess()));

        assertFalse(result.isSuccess(), "Should have differences");
    }

    @AfterAll
    static void generateCombinedReport() throws Exception {
        if (!scenarioResults.isEmpty()) {
            HtmlReportGenerator generator = new HtmlReportGenerator();
            String path = generator.generateCombined(
                    scenarioResults, ComparisonConfig.load().getReportBaseDir());
            System.out.println("Combined report: " + path);
        }
    }
}
```

### What the Combined Report Shows

- **Header** -- timestamp, total/passed/failed scenario counts
- **Dashboard** -- aggregate stat cards (total scenarios, passed, failed, total differences, total time)
- **Scenario Index** -- clickable table; click a row to jump to that scenario
- **Scenario Sections** -- one collapsible section per scenario with full details
  - Failed scenarios are expanded by default
  - Passed scenarios are collapsed by default

### Combined Report Methods

```java
HtmlReportGenerator generator = new HtmlReportGenerator();

// Save to file (auto-generates: {baseDir}/combined_report_{timestamp}.html)
String path = generator.generateCombined(scenarioResults, "target/reports");

// Get HTML as string
String html = generator.generateCombinedHtml(scenarioResults);

// Write to any Writer
generator.generateCombined(scenarioResults, myWriter);
```

---

## 9. Configuration Reference

All properties in `edi-comparison.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `report.base.dir` | `target/reports` | Base directory for all report files |
| `report.filename.pattern` | `report_{timestamp}.html` | Filename pattern (`{timestamp}` = `yyyyMMdd_HHmmss`) |
| `comparison.fail.on.first.error` | `false` | `true` = stop at first error; `false` = collect all |
| `comparison.case.sensitive` | `true` | Case sensitive string comparison |
| `comparison.detect.unexpected.segments` | `true` | Report segments in the file not covered by template |
| `comparison.validate.segment.order` | `false` | Validate that segments appear in template order |

### Loading Config

```java
// From classpath (src/test/resources/edi-comparison.properties)
ComparisonConfig config = ComparisonConfig.load();

// From an external file
ComparisonConfig config = ComparisonConfig.load("C:/config/my-config.properties");

// From a Properties object
ComparisonConfig config = ComparisonConfig.fromProperties(myProperties);
```

---

## 10. Project Structure

When integrated into your test project, the layout looks like this:

```
your-project/
  src/test/
    java/
      com/yourcompany/tests/
        BookingVerificationTest.java        <-- your tests
    resources/
      edi-comparison.properties             <-- library config
      rules/
        simple-booking.yaml                 <-- your YAML templates
        booking-confirmation.yaml
      samples/
        outbounds/
          my-outbound-file.edi              <-- EDI files to validate
  target/reports/                           <-- generated reports (gitignored)
    booking-test/
      report_20260208_143000.html
    combined_report_20260208_143000.html
```

---

## Quick Checklist

1. Add the library dependency to your `pom.xml`
2. Create `edi-comparison.properties` in `src/test/resources/` (optional)
3. Create a YAML template in `src/test/resources/rules/`
4. Write a JUnit test: load template, parse EDI, build context, compare, generate report
5. Run `mvn test` and open the HTML report in your browser
