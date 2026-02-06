# EDI Comparison Library

A lightweight, loosely-coupled library for comparing EDIFACT, ANSI X12, and XML files in test automation frameworks.

## 🎯 Features

- ✅ **Multi-format Support**: EDIFACT, ANSI X12, and XML
- ✅ **Rule-based Validation**: Define comparison rules via YAML config + custom code
- ✅ **Flexible Validation**: Exact match, pattern match, date format, custom business rules
- ✅ **Detailed Reporting**: HTML and JSON reports with line-by-line differences
- ✅ **Loosely Coupled**: Clean interfaces, easy to integrate with any test framework
- ✅ **Minimal Dependencies**: Only Jackson and SnakeYAML

## 📦 Project Structure

```
edi-comparison-library/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/edi/comparison/
│   │   │   ├── core/              # Main API facade
│   │   │   ├── parser/            # File parsers (EDIFACT, ANSI, XML)
│   │   │   ├── rule/              # Rule engine & config loader
│   │   │   ├── validator/         # Field validators
│   │   │   ├── report/            # Report generators
│   │   │   ├── model/             # Domain models
│   │   │   └── exception/         # Custom exceptions
│   │   └── resources/
│   │       ├── rules/             # Default rule templates
│   │       └── templates/         # HTML report templates
│   └── test/
│       ├── java/                  # Unit tests
│       └── resources/
│           ├── samples/           # Sample EDI/XML files
│           └── rules/             # Test rule configs
└── README.md
```

## 🚀 Quick Start

### 1. Add to Your Project

```xml
<dependency>
    <groupId>com.edi.comparison</groupId>
    <artifactId>edi-comparison-library</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Basic Usage

```java
// Simple comparison
FileComparator comparator = FileComparator.builder()
    .withRuleFile("rules/iftmbf-comparison.yaml")
    .withTestDataContext(testDataMap)
    .build();

ComparisonResult result = comparator.compare(
    inboundFile, 
    outboundFile, 
    FileFormat.EDIFACT
);

// Check results
if (result.hasDifferences()) {
    result.generateHtmlReport("reports/comparison-report.html");
    System.out.println(result.getSummary());
}
```

### 3. Define Rules (YAML)

```yaml
message_type: IFTMBF
rules:
  - segment: BGM
    fields:
      - position: C002.1001
        validation: exact_match
        source: testData.bgmCode
  
  - segment: NAD
    multiple: true
    order_matters: false
```

## 🔧 Integration with Selenium Framework

```java
@Test
public void testBookingOutbound() {
    // Drop inbound file
    dropInboundFile(inboundData);
    
    // Wait for outbound generation
    String outboundFile = waitForOutbound();
    
    // Compare
    ComparisonResult result = comparator.compare(
        inboundFile, 
        outboundFile, 
        FileFormat.EDIFACT
    );
    
    // Assert
    Assert.assertTrue(result.isSuccess(), 
        "Outbound validation failed: " + result.getSummary());
}
```

## 📋 Next Steps

This is **Step 1** - Project Setup. We'll build incrementally:

- **Step 2**: Core domain models (Segment, Field, Message)
- **Step 3**: Parser abstraction layer
- **Step 4**: Rule engine & config loader
- **Step 5**: Comparison engine
- **Step 6**: Reporting system
- **Step 7**: Custom validators framework

## 🏗️ Design Principles

1. **Loose Coupling**: Each layer depends only on interfaces
2. **Open-Closed**: Easy to extend without modifying core
3. **Single Responsibility**: Each class has one clear purpose
4. **Dependency Injection**: Testable and flexible
5. **Fail-Safe**: Collect all errors, don't fail fast

## 📝 License

Internal use - Your organization
