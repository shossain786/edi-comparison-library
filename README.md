# EDI Comparison Library - User Guide

A lightweight Java library for validating EDIFACT, ANSI X12, and XML messages in test automation. Define what to check via YAML templates, run comparisons, and get detailed HTML reports.

---

## Table of Contents

1. [Add to Your Project](#1-add-to-your-project)
2. [Configure](#2-configure)
3. [Quick Start — EdiVerifier](#3-quick-start--ediverifier)
4. [Generate Templates Automatically](#4-generate-templates-automatically)
5. [Create a YAML Template Manually](#5-create-a-yaml-template-manually)
6. [Advanced: Low-Level API](#6-advanced-low-level-api)
7. [Run and View Reports](#7-run-and-view-reports)
8. [YAML Template Reference](#8-yaml-template-reference)
9. [Report Generation Options](#9-report-generation-options)
10. [Combined Report](#10-combined-report)
11. [Configuration Reference](#11-configuration-reference)
12. [Project Structure](#12-project-structure)

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
comparison.validate.segment.order=false
```

All properties have sensible defaults — this file is optional.

---

## 3. Quick Start — EdiVerifier

`EdiVerifier` is the recommended entry point. It wraps parsing, rule loading, comparison, and reporting behind a single fluent API — no boilerplate required.

### Single file

```java
import com.edi.comparison.EdiVerifier;

// Three lines: load template → verify file → assert
EdiVerifier.with("rules/booking-template.yaml")
        .verify("output/booking-001.edi")
        .assertPassed();                       // throws AssertionError if failed
```

### Batch — 500+ files in parallel

```java
import com.edi.comparison.EdiVerifier;
import com.edi.comparison.batch.BatchResult;

BatchResult result = EdiVerifier.with("rules/booking-template.yaml")
        .verifyFolder("output/files/");

System.out.println(result.getSummary());
// → "500 files: 498 passed, 2 failed, 0 errored (12.3s)"

result.generateReport("target/reports/");     // combined HTML report
result.assertAllPassed();                      // throws with full failure list
```

### With options

```java
BatchResult result = EdiVerifier.with("rules/template.yaml")
        .parallelism(8)               // threads — defaults to available CPU cores
        .filePattern("*.edi")         // glob filter for filenames
        .format(FileFormat.EDIFACT)   // skip auto-detection (faster for uniform batches)
        .recursive(true)              // walk subdirectories
        .verifyFolder("output/files/");
```

### Explicit file list (e.g. from FTP download)

```java
List<Path> files = ftpClient.downloadedFiles();   // or any List<Path>

BatchResult result = EdiVerifier.with("rules/template.yaml")
        .verifyFiles(files);

result.assertAllPassed();
```

### Dynamic field values (test data)

```java
EdiVerifier.with("rules/template.yaml")
        .testData("bookingNumber", "BK123")      // referenced in YAML as: source: testData.bookingNumber
        .verify("output/booking.edi")
        .assertPassed();
```

### Inspect failures

```java
BatchResult result = EdiVerifier.with("rules/template.yaml")
        .verifyFolder("output/");

// Per-file detail
result.getFailedFiles().forEach(f ->
        System.out.println(f.getFileName() + ": " + f.getDifferenceCount() + " diff(s)"));

// Top 5 error types across all files — find systemic issues fast
result.getMostCommonErrors(5).forEach((type, count) ->
        System.out.println(type + ": " + count + " occurrences"));
```

### Template on classpath (test resources)

```java
// Loads from src/test/resources/rules/booking-template.yaml
EdiVerifier.withClasspath("rules/booking-template.yaml")
        .verifyFolder("output/files/");
```

---

## 4. Generate Templates Automatically

Writing YAML templates by hand for complex EDI files (hundreds of segments) is slow and error-prone. `TemplateGenerator` generates a ready-to-use template from a golden/reference file in seconds.

### From a single golden file

```java
import com.edi.comparison.template.TemplateGenerator;

// Generates template and saves it — done
TemplateGenerator.from("samples/golden-booking.edi")
        .saveTo("rules/booking-template.yaml");
```

That's it. Open the generated file, review it, tweak if needed, then use it.

### Generation modes

| Mode | What's included | Best for |
|------|----------------|----------|
| `MINIMAL` | Segment names only | Quick starting point to build on |
| `STRUCTURE` *(default)* | Segment names + exact occurrence counts | Checking structure is identical |
| `FULL` | Structure + field rules with smart classification | Full regression testing |

```java
import com.edi.comparison.template.TemplateGenerator.GenerationMode;

// Default (STRUCTURE)
TemplateGenerator.from("samples/golden.edi").saveTo("rules/template.yaml");

// FULL — includes field-level validation rules
TemplateGenerator.from("samples/golden.edi")
        .mode(GenerationMode.FULL)
        .saveTo("rules/template-full.yaml");

// MINIMAL — just segment names
TemplateGenerator.from("samples/golden.edi")
        .mode(GenerationMode.MINIMAL)
        .saveTo("rules/template-minimal.yaml");
```

### Learn from a folder of files (best for large batches)

Instead of learning from a single file, scan the whole batch:
- Segments present in **every** file → `required: true`
- Segments present in **some** files → `required: false`
- The **most common** occurrence count → `expected_count`

```java
// Scan all files in the folder
TemplateGenerator.learnFrom("output/files/")
        .saveTo("rules/learned-template.yaml");

// With file filter
TemplateGenerator.learnFrom("output/files/", "*.edi")
        .saveTo("rules/learned-template.yaml");

// From an explicit list
TemplateGenerator.learnFrom(listOfPaths)
        .saveTo("rules/learned-template.yaml");
```

### FULL mode — smart field classification

In `FULL` mode, field validation types are chosen automatically:

| Field value example | Generated validation |
|---------------------|----------------------|
| `"CN"`, `"340"`, `"9"` (short code, ≤4 chars) | `exact_match: "CN"` |
| `"20260207"` (8 digits) | `pattern_match: "^[0-9]{8}$"` |
| `"202602121430"` (12 digits) | `pattern_match: "^[0-9]{12}$"` |
| `"BOOKING123456"` (dynamic reference) | `exists` |
| Multi-occurrence segment fields | `exists` (values differ per record) |

Review FULL mode output before use — some `exact_match` fields may need to be changed to `exists` for values that vary across files.

### Typical workflow

```java
// Step 1: generate a template skeleton
TemplateGenerator.from("samples/golden.edi")
        .mode(GenerationMode.STRUCTURE)
        .saveTo("rules/my-template.yaml");

// Step 2: open my-template.yaml in an editor
//   - add field rules for fields you care about
//   - change required: true/false as needed
//   - adjust expected_count if needed

// Step 3: verify your entire batch
EdiVerifier.with("rules/my-template.yaml")
        .verifyFolder("output/files/")
        .assertAllPassed();
```

---

## 5. Create a YAML Template Manually

If you prefer to write templates by hand (or edit a generated one), create them under `src/test/resources/rules/`.

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

## 6. Advanced: Low-Level API

Use this when you need full control — custom parsers, inbound/outbound comparison, or programmatic rule building. For most use cases, prefer `EdiVerifier` (section 3).

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

class BookingVerificationTest {

    @Test
    void verifyBookingOutbound() throws Exception {
        // 1. Load config and template
        ComparisonConfig config = ComparisonConfig.load();
        RuleSet template = new RuleLoader().loadFromResource("rules/simple-booking.yaml");

        // 2. Parse the EDI message
        EdifactParser parser = new EdifactParser();
        String ediContent = "BGM+340+BOOKING123+9'\nNAD+CZ+SHIPPER001::92'";
        Message outbound = parser.parse(ediContent);

        // 3. Build context
        Map<String, Object> testData = Map.of("bookingNumber", "BOOKING123");
        ComparisonContext context = ComparisonContext.builder()
                .testData(testData)
                .addConfig("detect_unexpected_segments", config.isDetectUnexpectedSegments())
                .build();

        // 4. Run comparison
        ComparisonEngine engine = new ComparisonEngine(template, context);
        ComparisonResult result = engine.compare(null, outbound);

        // 5. Generate HTML report
        String reportPath = new HtmlReportGenerator()
                .generate(result, config.getReportBaseDir(), "booking-test");

        // 6. Assert
        assertTrue(result.isSuccess(), "Verification failed. Report: " + reportPath);
    }
}
```

### Auto-Detect Format

```java
import com.edi.comparison.parser.AutoDetectParser;

// Works for EDIFACT, ANSI X12, and XML — format detected from content
FileParser parser = new AutoDetectParser();
Message msg = parser.parse(new File("booking.edi"));    // format detected automatically
Message msg2 = parser.parse(fileContentString);          // also accepts String
```

### Explicit Parser Selection

```java
// EDIFACT
Message msg = new EdifactParser().parse(content);

// ANSI X12
Message msg = new AnsiX12Parser().parse(content);

// XML
Message msg = new XmlParser().parse(content);
```

### Loading EDI from a File

```java
// From classpath resource
try (InputStream is = getClass().getClassLoader().getResourceAsStream("samples/outbounds/my-file")) {
    Message outbound = new AutoDetectParser().parse(is);
}

// From filesystem
String content = Files.readString(Path.of("/path/to/edi-file.edi"));
Message outbound = new AutoDetectParser().parse(content);
```

### Inbound → Outbound Comparison

If you want to check that outbound field values match the inbound:

```yaml
# In your YAML template
- position: BGM.0002
  validation: exact_match
  source: inbound.BGM.0002        # compare against the inbound message's BGM.0002
```

```java
Message inbound  = new AutoDetectParser().parse(inboundContent);
Message outbound = new AutoDetectParser().parse(outboundContent);

ComparisonContext context = ComparisonContext.builder()
        .testData(testData)
        .inboundMessage(inbound)       // needed when template uses 'inbound.xxx'
        .build();

ComparisonResult result = engine.compare(inbound, outbound);
```

---

## 7. Run and View Reports

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
    report_20260208_143000.html         <-- individual report
  combined_report_20260208_143000.html  <-- combined report (if generated)
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

## 8. YAML Template Reference

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

## 9. Report Generation Options

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

### Batch Report (via EdiVerifier)

```java
BatchResult result = EdiVerifier.with("rules/template.yaml")
        .verifyFolder("output/files/");

// Generates a combined HTML report for all files in the batch
String reportPath = result.generateReport("target/reports/");
```

---

## 10. Combined Report

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
        ComparisonResult result = engine.compare(null, outbound);

        // Add to collection (3rd argument = did this test pass?)
        scenarioResults.add(new ScenarioResult("Scenario A", result, result.isSuccess()));

        report.generate(result, config.getReportBaseDir(), "scenario-a");
        assertTrue(result.isSuccess());
    }

    @Test
    void testExpectedFailure() throws Exception {
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

## 11. Configuration Reference

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

## 12. Project Structure

```
edi-comparison-library/
  src/main/java/com/edi/comparison/
    EdiVerifier.java                    ← main entry point (start here)
    batch/
      BatchResult.java                  ← aggregated result for folder/list verification
      FileResult.java                   ← per-file result
    template/
      TemplateGenerator.java            ← auto-generate YAML templates from EDI files
    parser/
      AutoDetectParser.java             ← detects EDIFACT / ANSI X12 / XML automatically
      EdifactParser.java
      AnsiX12Parser.java
      XmlParser.java
      FileParser.java                   ← parser interface
    core/
      ComparisonEngine.java             ← comparison logic
      ComparisonContext.java            ← runtime context (test data, config)
    model/
      Message.java / Segment.java / Field.java
      ComparisonResult.java / Difference.java
      FileFormat.java
    rule/
      RuleLoader.java                   ← loads YAML templates
      RuleSet.java / ComparisonRule.java / FieldRule.java
    report/
      HtmlReportGenerator.java
    config/
      ComparisonConfig.java

your-test-project/
  src/test/
    java/
      com/yourcompany/tests/
        BookingVerificationTest.java    ← your tests
    resources/
      edi-comparison.properties         ← library config (optional)
      rules/
        booking-template.yaml           ← YAML templates (hand-written or generated)
      samples/
        outbounds/
          booking-001.edi               ← EDI files to validate
  target/reports/                       ← generated reports (gitignore this)
    combined_report_20260208_143000.html
```

---

## Quick Reference

### Choose your workflow

| Scenario | Recommended approach |
|----------|---------------------|
| Single file verification | `EdiVerifier.with(...).verify(...).assertPassed()` |
| Batch — 500+ files | `EdiVerifier.with(...).verifyFolder(...).assertAllPassed()` |
| Don't have a template yet | `TemplateGenerator.from(goldenFile).saveTo(...)` |
| Learn template from many files | `TemplateGenerator.learnFrom(folder).saveTo(...)` |
| Mixed EDIFACT + X12 + XML in one folder | `EdiVerifier` with default `AutoDetectParser` |
| Full control / inbound comparison | Low-level API (section 6) |

### Quick Checklist

1. Add the library dependency to your `pom.xml`
2. Create `edi-comparison.properties` in `src/test/resources/` (optional)
3. Generate a YAML template: `TemplateGenerator.from("golden.edi").saveTo("rules/template.yaml")`
4. Review/edit the template as needed
5. Write a test: `EdiVerifier.with("rules/template.yaml").verifyFolder("output/").assertAllPassed()`
6. Run `mvn test` and open the HTML report in your browser
