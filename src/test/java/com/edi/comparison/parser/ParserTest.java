package com.edi.comparison.parser;

import com.edi.comparison.model.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for all parser implementations.
 */
class ParserTest {
    
    @Test
    void testEdifactParserFromString() throws ParseException {
        String edifact = "UNH+1+IFTMBF:D:96A:UN'\n" +
                        "BGM+340+BOOKING123+9'\n" +
                        "NAD+CZ+SHIPPER001::92'\n" +
                        "UNT+3+1'";
        
        EdifactParser parser = new EdifactParser();
        Message message = parser.parse(edifact);
        
        assertEquals(FileFormat.EDIFACT, message.getFileFormat());
        assertEquals("IFTMBF", message.getMessageType());
        assertEquals(4, message.getSegmentCount());
        
        // Check BGM segment
        Optional<Segment> bgm = message.getFirstSegmentByTag("BGM");
        assertTrue(bgm.isPresent());
        assertEquals("BGM", bgm.get().getTag());
        assertEquals(3, bgm.get().getFieldCount());
    }
    
    @Test
    void testEdifactParserFieldExtraction() throws ParseException {
        String edifact = "BGM+340+BOOKING123+9'";
        
        EdifactParser parser = new EdifactParser();
        Message message = parser.parse(edifact);
        
        Segment bgm = message.getSegments().get(0);
        
        // Check field positions and values
        assertEquals("340", bgm.getFieldValue("BGM.0001"));
        assertEquals("BOOKING123", bgm.getFieldValue("BGM.0002"));
        assertEquals("9", bgm.getFieldValue("BGM.0003"));
    }
    
    @Test
    void testEdifactParserCompositeFields() throws ParseException {
        String edifact = "NAD+CZ+SHIPPER001::92'";
        
        EdifactParser parser = new EdifactParser();
        Message message = parser.parse(edifact);
        
        Segment nad = message.getSegments().get(0);
        
        // Check composite field components
        assertTrue(nad.hasField("NAD.C001.0001"));
        assertTrue(nad.hasField("NAD.C001.0002"));
        assertTrue(nad.hasField("NAD.C001.0003"));
    }
    
    @Test
    void testEdifactParserCanParse() {
        EdifactParser parser = new EdifactParser();
        
        assertTrue(parser.canParse("UNH+1+IFTMBF'"));
        assertTrue(parser.canParse("BGM+340+TEST'"));
        assertFalse(parser.canParse("ISA*00*"));
        assertFalse(parser.canParse("<?xml"));
        assertFalse(parser.canParse(""));
        assertFalse(parser.canParse(null));
    }
    
    @Test
    void testEdifactParserMultipleSegments() throws ParseException {
        String edifact = "BGM+340+BOOKING123'\n" +
                        "NAD+CZ+SHIPPER001'\n" +
                        "NAD+CN+CONSIGNEE001'\n" +
                        "DTM+137:20240207:102'";
        
        EdifactParser parser = new EdifactParser();
        Message message = parser.parse(edifact);
        
        assertEquals(4, message.getSegmentCount());
        
        // Check multiple NAD segments
        List<Segment> nadSegments = message.getSegmentsByTag("NAD");
        assertEquals(2, nadSegments.size());
        assertEquals("CZ", nadSegments.get(0).getFieldValue("NAD.0001"));
        assertEquals("CN", nadSegments.get(1).getFieldValue("NAD.0001"));
    }
    
    @Test
    void testAnsiX12ParserFromString() throws ParseException {
        String ansi = "ISA*00*          *00*          *ZZ*SENDER~\n" +
                     "ST*214*0001~\n" +
                     "B10*SHIPMENT123*1234567~\n" +
                     "SE*3*0001~";
        
        AnsiX12Parser parser = new AnsiX12Parser();
        Message message = parser.parse(ansi);
        
        assertEquals(FileFormat.ANSI_X12, message.getFileFormat());
        assertEquals("214", message.getMessageType());
        assertTrue(message.getSegmentCount() > 0);
    }
    
    @Test
    void testAnsiX12ParserFieldExtraction() throws ParseException {
        String ansi = "B10*SHIPMENT123*1234567*SCAC~";
        
        AnsiX12Parser parser = new AnsiX12Parser();
        Message message = parser.parse(ansi);
        
        Segment b10 = message.getSegments().get(0);
        assertEquals("B10", b10.getTag());
        assertEquals("SHIPMENT123", b10.getFieldValue("B10.0001"));
        assertEquals("1234567", b10.getFieldValue("B10.0002"));
    }
    
    @Test
    void testAnsiX12ParserCanParse() {
        AnsiX12Parser parser = new AnsiX12Parser();
        
        assertTrue(parser.canParse("ISA*00*~"));
        assertTrue(parser.canParse("ST*214~"));
        assertFalse(parser.canParse("UNH+1+IFTMBF'"));
        assertFalse(parser.canParse("<?xml"));
        assertFalse(parser.canParse(""));
        assertFalse(parser.canParse(null));
    }
    
    @Test
    void testXmlParserFromString() throws ParseException {
        String xml = "<?xml version=\"1.0\"?>\n" +
                    "<Booking>\n" +
                    "  <BGM>\n" +
                    "    <DocumentCode>340</DocumentCode>\n" +
                    "    <BookingNumber>BOOKING123</BookingNumber>\n" +
                    "  </BGM>\n" +
                    "</Booking>";
        
        XmlParser parser = new XmlParser();
        Message message = parser.parse(xml);
        
        assertEquals(FileFormat.XML, message.getFileFormat());
        assertEquals("Booking", message.getMessageType());
        assertEquals(1, message.getSegmentCount());
        
        Segment bgm = message.getSegments().get(0);
        assertEquals("BGM", bgm.getTag());
        assertEquals("340", bgm.getFieldValue("BGM.DocumentCode"));
        assertEquals("BOOKING123", bgm.getFieldValue("BGM.BookingNumber"));
    }
    
    @Test
    void testXmlParserWithAttributes() throws ParseException {
        String xml = "<Root><Party type=\"consignor\"><ID>SHIPPER001</ID></Party></Root>";
        
        XmlParser parser = new XmlParser();
        Message message = parser.parse(xml);
        
        Segment party = message.getSegments().get(0);
        assertEquals("Party", party.getTag());
        assertEquals("consignor", party.getFieldValue("Party[@type]"));
        assertEquals("SHIPPER001", party.getFieldValue("Party.ID"));
    }
    
    @Test
    void testXmlParserCanParse() {
        XmlParser parser = new XmlParser();
        
        assertTrue(parser.canParse("<?xml version=\"1.0\"?>"));
        assertTrue(parser.canParse("<Root></Root>"));
        assertFalse(parser.canParse("BGM+340'"));
        assertFalse(parser.canParse("ISA*00*"));
        assertFalse(parser.canParse(""));
        assertFalse(parser.canParse(null));
    }
    
    @Test
    void testParseExceptionWithLineNumber() {
        ParseException exception = new ParseException("Invalid format", 5, "BGM+INVALID");
        
        assertEquals(5, exception.getLineNumber());
        assertEquals("BGM+INVALID", exception.getProblematicContent());
        assertTrue(exception.getMessage().contains("line 5"));
        assertTrue(exception.getMessage().contains("BGM+INVALID"));
    }
    
    @Test
    void testEdifactParserFromFile() throws Exception {
        File file = new File("src/test/resources/samples/sample-edifact.edi");
        
        if (file.exists()) {
            EdifactParser parser = new EdifactParser();
            Message message = parser.parse(file);
            
            assertEquals(FileFormat.EDIFACT, message.getFileFormat());
            assertTrue(message.getSourceFilePath().endsWith("sample-edifact.edi"));
            assertTrue(message.getSegmentCount() > 0);
        }
    }
    
    @Test
    void testSegmentSequenceNumbers() throws ParseException {
        String edifact = "BGM+340'\nNAD+CZ'\nDTM+137'";
        
        EdifactParser parser = new EdifactParser();
        Message message = parser.parse(edifact);
        
        List<Segment> segments = message.getSegments();
        assertEquals(0, segments.get(0).getSequenceNumber());
        assertEquals(1, segments.get(1).getSequenceNumber());
        assertEquals(2, segments.get(2).getSequenceNumber());
    }
    
    @Test
    void testSegmentLineNumbers() throws ParseException {
        String edifact = "BGM+340'\nNAD+CZ'\nDTM+137'";
        
        EdifactParser parser = new EdifactParser();
        Message message = parser.parse(edifact);
        
        List<Segment> segments = message.getSegments();
        assertTrue(segments.get(0).getLineNumber() > 0);
        assertTrue(segments.get(1).getLineNumber() > 0);
        assertTrue(segments.get(2).getLineNumber() > 0);
    }
}
