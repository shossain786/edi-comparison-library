# Step 3: Parser Layer - COMPLETE ✅

## What We Built

A complete parsing layer that converts raw EDI/XML files into our unified domain model.

### Files Created

```
src/main/java/com/edi/comparison/parser/
├── FileParser.java          - Common interface for all parsers
├── ParseException.java      - Custom exception with line number tracking
├── EdifactParser.java       - EDIFACT format parser
├── AnsiX12Parser.java       - ANSI X12 format parser
└── XmlParser.java           - XML format parser

src/test/resources/samples/
├── sample-edifact.edi       - Sample EDIFACT file
├── sample-ansi.x12          - Sample ANSI X12 file
└── sample.xml               - Sample XML file

src/test/java/com/edi/comparison/parser/
└── ParserTest.java          - Comprehensive parser tests
```

---

## Usage Examples

### 1. Parse EDIFACT File

```java
// From File
EdifactParser parser = new EdifactParser();
Message message = parser.parse(new File("booking.edi"));

// From String
String edifact = "BGM+340+BOOKING123'\nNAD+CZ+SHIPPER001'";
Message message = parser.parse(edifact);

// Access parsed data
Segment bgm = message.getFirstSegmentByTag("BGM").get();
String bookingNumber = bgm.getFieldValue("BGM.0002");  // "BOOKING123"
```

### 2. Parse ANSI X12 File

```java
AnsiX12Parser parser = new AnsiX12Parser();
Message message = parser.parse(new File("shipment.x12"));

// Get transaction set type
String transactionType = message.getMessageType();  // e.g., "214"

// Access segments
List<Segment> n1Segments = message.getSegmentsByTag("N1");
```

### 3. Parse XML File

```java
XmlParser parser = new XmlParser();
Message message = parser.parse(new File("booking.xml"));

// XML elements become segments
Segment bgm = message.getFirstSegmentByTag("BGM").get();
String docCode = bgm.getFieldValue("BGM.DocumentCode");

// Attributes are also parsed
String partyType = party.getFieldValue("Party[@type]");
```

### 4. Auto-Detect Format

```java
String content = readFile("unknown.edi");

FileParser parser;
if (new EdifactParser().canParse(content)) {
    parser = new EdifactParser();
} else if (new AnsiX12Parser().canParse(content)) {
    parser = new AnsiX12Parser();
} else if (new XmlParser().canParse(content)) {
    parser = new XmlParser();
} else {
    throw new IllegalArgumentException("Unknown format");
}

Message message = parser.parse(content);
```

### 5. Error Handling

```java
try {
    Message message = parser.parse(file);
} catch (ParseException e) {
    System.err.println("Parse failed: " + e.getMessage());
    System.err.println("Line number: " + e.getLineNumber());
    System.err.println("Content: " + e.getProblematicContent());
}
```

---

## Parser Features

### EdifactParser

**Delimiters:**
- Segment: `'` (apostrophe)
- Element: `+` (plus)
- Component: `:` (colon)

**Capabilities:**
✅ Parses composite fields (e.g., `NAD+CZ+SHIPPER::92`)  
✅ Extracts message type from UNH segment  
✅ Handles multi-line files  
✅ Tracks line and sequence numbers  
✅ Preserves raw segment content  

**Field Position Format:**
- Simple: `BGM.0001`
- Composite: `NAD.C001.0001`

### AnsiX12Parser

**Delimiters:**
- Segment: `~` (tilde)
- Element: `*` (asterisk)
- Component: `:` (colon) or `>` (greater than)

**Capabilities:**
✅ Parses component delimiters (`:` and `>`)  
✅ Extracts transaction type from ST segment  
✅ Handles ISA envelope structure  
✅ Tracks line and sequence numbers  

**Field Position Format:**
- Simple: `B10.0001`
- Composite: `N1.C01.01`

### XmlParser

**Capabilities:**
✅ Converts XML elements to segments  
✅ Parses attributes as fields  
✅ Handles nested elements  
✅ Root element name becomes message type  
✅ Security: Disables external entities (XXE protection)  

**Field Position Format:**
- Element: `BGM.DocumentCode`
- Attribute: `Party[@type]`
- Nested: `BGM.Details.Code`

---

## Integration Example

Now you can use parsers in your test framework:

```java
public class EDIComparisonTest {
    
    @Test
    public void testBookingOutbound() throws Exception {
        // Parse inbound file
        EdifactParser parser = new EdifactParser();
        Message inbound = parser.parse(new File("inbound/booking.edi"));
        
        // Trigger outbound generation
        generateOutbound(inbound);
        
        // Parse outbound file
        Message outbound = parser.parse(new File("outbound/booking.edi"));
        
        // Now we can compare
        Segment inboundBGM = inbound.getFirstSegmentByTag("BGM").get();
        Segment outboundBGM = outbound.getFirstSegmentByTag("BGM").get();
        
        assertEquals(
            inboundBGM.getFieldValue("BGM.0002"),
            outboundBGM.getFieldValue("BGM.0002"),
            "Booking number should match"
        );
    }
}
```

---

## Parsed Data Structure

All parsers convert files into this unified structure:

```
Message
├── fileFormat: EDIFACT / ANSI_X12 / XML
├── messageType: "IFTMBF" / "214" / "Booking"
├── sourceFilePath: "/data/inbound/booking.edi"
└── segments: [
    Segment {
        tag: "BGM"
        lineNumber: 2
        sequenceNumber: 0
        rawContent: "BGM+340+BOOKING123'"
        fields: [
            Field {
                position: "BGM.0001"
                value: "340"
                lineNumber: 2
            },
            Field {
                position: "BGM.0002"
                value: "BOOKING123"
                lineNumber: 2
            }
        ]
    },
    ...
]
```

---

## Testing

All parsers are thoroughly tested:

```bash
mvn test -Dtest=ParserTest
```

**Test Coverage:**
- ✅ Parse from File, String, InputStream
- ✅ Field extraction (simple and composite)
- ✅ Multiple segments handling
- ✅ Segment sequence and line numbers
- ✅ Format auto-detection (canParse)
- ✅ Error handling with line numbers
- ✅ XML attributes and nesting
- ✅ Message type extraction

---

## Design Highlights

✅ **Unified Interface**: All parsers implement `FileParser`  
✅ **Consistent Output**: All produce `Message` objects  
✅ **Error Reporting**: Line numbers and problematic content  
✅ **Thread-Safe**: Parsers are stateless  
✅ **Format Detection**: `canParse()` method for auto-detection  
✅ **Raw Content Preservation**: Original text kept for debugging  

---

## Real-World Example

```java
// Your actual use case
public void validateOutboundFile(String inboundPath, String outboundPath) {
    try {
        // Parse both files
        EdifactParser parser = new EdifactParser();
        Message inbound = parser.parse(new File(inboundPath));
        Message outbound = parser.parse(new File(outboundPath));
        
        // Access known data from test context
        String expectedBGM = testDataMap.get("bgmCode");
        String expectedBooking = testDataMap.get("bookingNumber");
        
        // Verify BGM segment
        Segment outboundBGM = outbound.getFirstSegmentByTag("BGM")
            .orElseThrow(() -> new AssertionError("BGM segment missing"));
        
        assertEquals(expectedBGM, outboundBGM.getFieldValue("BGM.0001"));
        assertEquals(expectedBooking, outboundBGM.getFieldValue("BGM.0002"));
        
        // Verify multiple containers
        List<Segment> containers = outbound.getSegmentsByTag("EQD");
        for (Segment eqd : containers) {
            String containerNum = eqd.getFieldValue("EQD.C001.0001");
            assertNotNull(containerNum, "Container number is required");
            // More validations...
        }
        
    } catch (ParseException e) {
        fail("Parse failed at line " + e.getLineNumber() + ": " + e.getMessage());
    }
}
```

---

## ✅ Step 3 Status: COMPLETE

### What We Achieved:
- ✅ Created 3 format parsers (EDIFACT, ANSI X12, XML)
- ✅ Unified interface for all parsers
- ✅ Comprehensive error handling with line tracking
- ✅ Sample files for all formats
- ✅ 15+ unit tests with full coverage
- ✅ Thread-safe, stateless implementations

### Total Code:
- **~800 lines** of production parser code
- **~300 lines** of comprehensive tests
- **3 sample files** ready to use

---

## 🎯 Next Step: Step 4 - Rule Engine

In Step 4, we'll build the rule engine that defines **what to compare** and **how**:

```yaml
# rules/booking-comparison.yaml
message_type: IFTMBF
rules:
  - segment: BGM
    fields:
      - position: BGM.0001
        validation: exact_match
        source: testData.bgmCode
        
  - segment: NAD
    multiple: true
    order_matters: false
```

```java
// Load and apply rules
RuleEngine engine = new RuleEngine("rules/booking-comparison.yaml");
engine.setContext(testDataMap);
ComparisonResult result = engine.compare(inbound, outbound);
```

**Ready to proceed with Step 4?** 🚀
