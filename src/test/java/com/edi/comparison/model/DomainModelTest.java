package com.edi.comparison.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests demonstrating the usage of domain models.
 * These tests also serve as examples for library users.
 */
class DomainModelTest {
    
    @Test
    void testFieldCreation() {
        // Create a simple field
        Field field = Field.builder()
            .position("BGM.C002.1001")
            .value("340")
            .name("documentCode")
            .lineNumber(3)
            .build();
        
        assertEquals("BGM.C002.1001", field.getPosition());
        assertEquals("340", field.getValue());
        assertEquals("documentCode", field.getName());
        assertEquals(3, field.getLineNumber());
        assertTrue(field.hasValue());
    }
    
    @Test
    void testFieldRequiresPosition() {
        // Position is required
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            Field.builder()
                .value("340")
                .build();
        });
        
        assertTrue(exception.getMessage().contains("position is required"));
    }
    
    @Test
    void testSegmentCreation() {
        // Create fields
        Field docCode = Field.builder()
            .position("BGM.C002.1001")
            .value("340")
            .name("documentCode")
            .build();
        
        Field docNumber = Field.builder()
            .position("BGM.1004")
            .value("BOOKING123")
            .name("documentNumber")
            .build();
        
        // Create segment with fields
        Segment segment = Segment.builder()
            .tag("BGM")
            .addField(docCode)
            .addField(docNumber)
            .lineNumber(3)
            .sequenceNumber(0)
            .rawContent("BGM+340+BOOKING123'")
            .build();
        
        assertEquals("BGM", segment.getTag());
        assertEquals(2, segment.getFieldCount());
        assertEquals(3, segment.getLineNumber());
        assertTrue(segment.hasField("BGM.C002.1001"));
        assertEquals("340", segment.getFieldValue("BGM.C002.1001"));
    }
    
    @Test
    void testSegmentFieldLookup() {
        Field field = Field.builder()
            .position("NAD.3035")
            .value("CZ")
            .name("partyQualifier")
            .build();
        
        Segment segment = Segment.builder()
            .tag("NAD")
            .addField(field)
            .build();
        
        // Lookup by position
        Optional<Field> foundByPos = segment.getFieldByPosition("NAD.3035");
        assertTrue(foundByPos.isPresent());
        assertEquals("CZ", foundByPos.get().getValue());
        
        // Lookup by name
        Optional<Field> foundByName = segment.getFieldByName("partyQualifier");
        assertTrue(foundByName.isPresent());
        assertEquals("CZ", foundByName.get().getValue());
        
        // Not found
        Optional<Field> notFound = segment.getFieldByPosition("NAD.9999");
        assertFalse(notFound.isPresent());
    }
    
    @Test
    void testMessageCreation() {
        // Create segments
        Segment bgm = Segment.builder()
            .tag("BGM")
            .addField(Field.builder()
                .position("BGM.C002.1001")
                .value("340")
                .build())
            .build();
        
        Segment nad1 = Segment.builder()
            .tag("NAD")
            .addField(Field.builder()
                .position("NAD.3035")
                .value("CZ")
                .build())
            .build();
        
        Segment nad2 = Segment.builder()
            .tag("NAD")
            .addField(Field.builder()
                .position("NAD.3035")
                .value("CN")
                .build())
            .build();
        
        // Create message
        Message message = Message.builder()
            .fileFormat(FileFormat.EDIFACT)
            .messageType("IFTMBF")
            .sourceFilePath("/data/inbound/booking.edi")
            .addSegment(bgm)
            .addSegment(nad1)
            .addSegment(nad2)
            .addMetadata("sender", "ACME")
            .addMetadata("receiver", "CORP")
            .build();
        
        assertEquals(FileFormat.EDIFACT, message.getFileFormat());
        assertEquals("IFTMBF", message.getMessageType());
        assertEquals(3, message.getSegmentCount());
        assertEquals("ACME", message.getMetadataValue("sender"));
    }
    
    @Test
    void testMessageSegmentQueries() {
        Segment bgm = Segment.builder().tag("BGM").build();
        Segment nad1 = Segment.builder().tag("NAD").build();
        Segment nad2 = Segment.builder().tag("NAD").build();
        Segment dtm = Segment.builder().tag("DTM").build();
        
        Message message = Message.builder()
            .fileFormat(FileFormat.EDIFACT)
            .addSegment(bgm)
            .addSegment(nad1)
            .addSegment(nad2)
            .addSegment(dtm)
            .build();
        
        // Get all NAD segments
        List<Segment> nadSegments = message.getSegmentsByTag("NAD");
        assertEquals(2, nadSegments.size());
        
        // Get first BGM segment
        Optional<Segment> firstBgm = message.getFirstSegmentByTag("BGM");
        assertTrue(firstBgm.isPresent());
        assertEquals("BGM", firstBgm.get().getTag());
        
        // Check segment existence
        assertTrue(message.hasSegment("DTM"));
        assertFalse(message.hasSegment("LOC"));
        
        // Count specific segments
        assertEquals(2, message.getSegmentCount("NAD"));
        assertEquals(1, message.getSegmentCount("BGM"));
        
        // Get segment tags
        assertEquals(3, message.getSegmentTags().size());
        assertTrue(message.getSegmentTags().contains("BGM"));
        assertTrue(message.getSegmentTags().contains("NAD"));
        assertTrue(message.getSegmentTags().contains("DTM"));
    }
    
    @Test
    void testFileFormatDetection() {
        assertEquals(FileFormat.EDIFACT, FileFormat.fromFilename("booking.edi"));
        assertEquals(FileFormat.EDIFACT, FileFormat.fromFilename("data.edifact"));
        assertEquals(FileFormat.ANSI_X12, FileFormat.fromFilename("invoice.x12"));
        assertEquals(FileFormat.XML, FileFormat.fromFilename("manifest.xml"));
        assertNull(FileFormat.fromFilename("unknown.txt"));
    }
    
    @Test
    void testFileFormatProperties() {
        assertEquals("'", FileFormat.EDIFACT.getSegmentDelimiter());
        assertEquals("+", FileFormat.EDIFACT.getElementDelimiter());
        assertEquals(":", FileFormat.EDIFACT.getComponentDelimiter());
        
        assertEquals("~", FileFormat.ANSI_X12.getSegmentDelimiter());
        assertEquals("*", FileFormat.ANSI_X12.getElementDelimiter());
        
        assertTrue(FileFormat.EDIFACT.isEdiFormat());
        assertTrue(FileFormat.ANSI_X12.isEdiFormat());
        assertFalse(FileFormat.XML.isEdiFormat());
    }
    
    @Test
    void testImmutability() {
        // Fields are immutable - no setters
        Field field = Field.builder()
            .position("BGM.1001")
            .value("340")
            .build();
        
        // Can create modified copy via toBuilder()
        Field modifiedField = field.toBuilder()
            .value("350")
            .build();
        
        assertEquals("340", field.getValue());
        assertEquals("350", modifiedField.getValue());
        
        // Same for segments
        Segment segment = Segment.builder()
            .tag("BGM")
            .addField(field)
            .build();
        
        // Segment fields list is unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> 
            segment.getFields().add(modifiedField)
        );
    }
}
