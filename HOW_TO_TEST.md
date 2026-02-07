# How to Test & Generate Reports

## Quick Start - Generate Your First Report

### Option 1: Run the Demo (Recommended)

**In your IDE:**
1. Open: `src/test/java/com/edi/comparison/demo/GenerateReportDemo.java`
2. Right-click → Run 'GenerateReportDemo.main()'
3. Check console output for report location
4. Open `comparison-report.html` in your browser

**Expected Console Output:**
```
=== EDI Comparison Report Demo ===

1. Setting up test data...
2. Creating validation rules...
3. Creating sample EDI messages...
4. Parsing messages...
5. Running comparison...
6. Comparison complete!

✅ Report generated successfully!

📄 Report location:
   /your/project/path/edi-comparison-library/comparison-report.html

📊 Summary:
Comparison Result:
  Status: FAILED
  Differences: 3
  Segments compared: 5
  Fields compared: 4
  Time taken: 15 ms

Differences by type:
  VALUE_MISMATCH: 1
  PATTERN_MISMATCH: 1
  DATE_FORMAT_INVALID: 1

💡 To view the report:
   1. Open the file in your browser
   2. Or run: open comparison-report.html (Mac)
   3. Or run: start comparison-report.html (Windows)
```

**Report Location:**
The report is generated in your **project root directory**:
```
edi-comparison-library/
├── comparison-report.html  ← HERE!
├── pom.xml
├── src/
└── ...
```

---

### Option 2: Run the Sample Example

**In your IDE:**
1. Open: `src/test/java/com/edi/comparison/report/ReportExample.java`
2. Right-click → Run 'ReportExample.main()'
3. Open `sample-comparison-report.html` in your browser

---

### Option 3: Run Unit Tests

**From IDE:**
1. Open: `src/test/java/com/edi/comparison/report/HtmlReportGeneratorTest.java`
2. Run any test method (they use @TempDir so reports go to temp folder)

**From Command Line:**
```bash
mvn test -Dtest=HtmlReportGeneratorTest
```

**Note:** Unit tests create reports in temp directories that get cleaned up.
Use Option 1 or 2 to see persistent reports.

---

## Understanding Report Locations

### When You Run the Demo
```
Your Project Root/
├── comparison-report.html          ← Demo output
├── sample-comparison-report.html   ← Example output
├── pom.xml
└── src/
```

### In Your Real Tests
Specify the path explicitly:
```java
@Test
public void testBookingValidation() throws Exception {
    // ... your comparison code ...
    
    if (result.hasDifferences()) {
        // Specify full path
        String reportPath = "target/reports/booking-test.html";
        result.generateHtmlReport(reportPath);
        
        System.out.println("Report: " + new File(reportPath).getAbsolutePath());
        fail("Validation failed - see report");
    }
}
```

**Reports will be in:**
```
edi-comparison-library/
└── target/
    └── reports/
        └── booking-test.html  ← Your test reports here
```

---

## Viewing the Report

### Method 1: Browser
1. Find the `.html` file
2. Double-click to open in default browser
3. Or right-click → Open With → Your Browser

### Method 2: Command Line
```bash
# Mac/Linux
open comparison-report.html

# Windows
start comparison-report.html

# Linux alternative
xdg-open comparison-report.html
```

### Method 3: From IDE
1. Right-click the `.html` file in Project Explorer
2. Select "Open in Browser" or "Open With → Browser"

---

## What You'll See in the Report

### Header Section
```
📊 EDI Comparison Report
Generated: 2024-02-07 14:30:45
```

### Status Badge
```
✗ FAILED  (or ✓ PASSED if successful)
```

### Statistics Dashboard
```
┌─────────────────────┐  ┌─────────────────────┐
│ Differences Found   │  │ Segments Compared   │
│        3            │  │        5            │
└─────────────────────┘  └─────────────────────┘

┌─────────────────────┐  ┌─────────────────────┐
│ Fields Compared     │  │ Time Taken          │
│        4            │  │       15 ms         │
└─────────────────────┘  └─────────────────────┘
```

### Differences (Color-Coded)
Each difference shows:
- **Type Badge** (red) - VALUE_MISMATCH, PATTERN_MISMATCH, etc.
- **Location** - BGM.0002 (bookingNumber) • Line 2
- **Expected Value** (green box) - BOOKING123
- **Actual Value** (red box) - BOOKING999

---

## Creating Reports in Your Tests

### Example Test Case

```java
package com.yourcompany.tests;

import com.edi.comparison.core.*;
import com.edi.comparison.model.*;
import com.edi.comparison.parser.EdifactParser;
import com.edi.comparison.rule.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class BookingValidationTest {
    
    @Test
    public void testBookingOutbound() throws Exception {
        // 1. Setup test data
        Map<String, Object> testData = new HashMap<>();
        testData.put("bgmCode", "340");
        testData.put("bookingNumber", "BOOKING123");
        
        // 2. Load rules
        RuleLoader loader = new RuleLoader();
        RuleSet rules = loader.loadFromResource("rules/simple-test.yaml");
        
        // 3. Parse your EDI files
        EdifactParser parser = new EdifactParser();
        
        String inbound = "BGM+340+BOOKING123+9'";
        String outbound = "BGM+340+BOOKING123+9'";
        
        Message inboundMsg = parser.parse(inbound);
        Message outboundMsg = parser.parse(outbound);
        
        // 4. Compare
        ComparisonContext context = ComparisonContext.builder()
            .testData(testData)
            .build();
        
        ComparisonEngine engine = new ComparisonEngine(rules, context);
        ComparisonResult result = engine.compare(inboundMsg, outboundMsg);
        
        // 5. Generate report if failed
        if (result.hasDifferences()) {
            String reportPath = "target/reports/booking-test.html";
            
            // Create directory if needed
            new File("target/reports").mkdirs();
            
            // Generate report
            result.generateHtmlReport(reportPath);
            
            // Print location
            System.err.println("Report: " + new File(reportPath).getAbsolutePath());
            
            // Fail test
            fail("Validation failed. Differences: " + result.getDifferenceCount());
        }
        
        System.out.println("✅ Validation passed!");
    }
}
```

---

## Troubleshooting

### Report Not Found?
Check the absolute path:
```java
File reportFile = new File("comparison-report.html");
System.out.println("Report is at: " + reportFile.getAbsolutePath());
```

### Permission Issues?
Make sure you have write permissions:
```java
// Use a writable directory
String reportPath = System.getProperty("user.home") + "/reports/test.html";
result.generateHtmlReport(reportPath);
```

### Report Not Opening?
1. Check file exists: `ls -la comparison-report.html`
2. Check file size: Should be > 10KB
3. Try opening manually in browser
4. Check console for errors

---

## Best Practices

### 1. Organize Reports by Test
```java
String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
String reportPath = String.format("target/reports/%s-%s.html", 
    testName, timestamp);
```

### 2. Clean Up Old Reports
```java
@BeforeClass
public static void cleanReports() {
    File reportsDir = new File("target/reports");
    if (reportsDir.exists()) {
        // Delete files older than 7 days
    }
}
```

### 3. Include in CI/CD Artifacts
```yaml
# .gitlab-ci.yml
artifacts:
  when: on_failure
  paths:
    - target/reports/*.html
  expire_in: 7 days
```

---

## Next Steps

1. ✅ **Run the Demo** - See it working
2. ✅ **Open the Report** - Understand the output
3. ✅ **Write Your Test** - Integrate into your framework
4. ✅ **Customize Rules** - Create your YAML rules
5. ✅ **Debug Easily** - Use reports to fix issues

**Happy Testing! 🚀**
