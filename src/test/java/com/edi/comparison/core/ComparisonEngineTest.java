package com.edi.comparison.core;

import com.edi.comparison.model.*;
import com.edi.comparison.rule.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ComparisonEngine.
 */
class ComparisonEngineTest {

    private RuleSet ruleSet;
    private Map<String, Object> testDataMap;

    @BeforeEach
    void setup() {
        testDataMap = new HashMap<>();
        testDataMap.put("bgmCode", "340");
        testDataMap.put("bookingNumber", "BOOKING123");
    }

    @Test
    void testExactMatchSuccess() {
        // Create rule
        FieldRule fieldRule = new FieldRule();
        fieldRule.setPosition("BGM.0001");
        fieldRule.setValidation(FieldRule.ValidationType.EXACT_MATCH);
        fieldRule.setExpectedValue("340");

        ComparisonRule compRule = new ComparisonRule();
        compRule.setSegment("BGM");
        compRule.addFieldRule(fieldRule);

        ruleSet = new RuleSet();
        ruleSet.addRule(compRule);

        // Create test message
        Field field = Field.builder()
                .position("BGM.0001")
                .value("340")
                .build();

        Segment segment = Segment.builder()
                .tag("BGM")
                .addField(field)
                .build();

        Message message = Message.builder()
                .fileFormat(FileFormat.EDIFACT)
                .addSegment(segment)
                .build();

        // Compare
        ComparisonContext context = ComparisonContext.builder()
                .testData(testDataMap)
                .build();

        ComparisonEngine engine = new ComparisonEngine(ruleSet, context);
        ComparisonResult result = engine.compare(null, message);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getDifferenceCount());
    }

    @Test
    void testExactMatchFailure() {
        // Create rule
        FieldRule fieldRule = new FieldRule();
        fieldRule.setPosition("BGM.0001");
        fieldRule.setValidation(FieldRule.ValidationType.EXACT_MATCH);
        fieldRule.setExpectedValue("340");

        ComparisonRule compRule = new ComparisonRule();
        compRule.setSegment("BGM");
        compRule.addFieldRule(fieldRule);

        ruleSet = new RuleSet();
        ruleSet.addRule(compRule);

        // Create message with wrong value
        Field field = Field.builder()
                .position("BGM.0001")
                .value("350")  // Wrong value
                .build();

        Segment segment = Segment.builder()
                .tag("BGM")
                .addField(field)
                .lineNumber(2)
                .build();

        Message message = Message.builder()
                .fileFormat(FileFormat.EDIFACT)
                .addSegment(segment)
                .build();

        // Compare
        ComparisonContext context = ComparisonContext.builder().build();
        ComparisonEngine engine = new ComparisonEngine(ruleSet, context);
        ComparisonResult result = engine.compare(null, message);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getDifferenceCount());

        Difference diff = result.getDifferences().get(0);
        assertEquals(Difference.DifferenceType.VALUE_MISMATCH, diff.getType());
        assertEquals("340", diff.getExpected());
        assertEquals("350", diff.getActual());
        assertEquals(2, diff.getLineNumber());
    }

    @Test
    void testTestDataReference() {
        // Create rule referencing test data
        FieldRule fieldRule = new FieldRule();
        fieldRule.setPosition("BGM.0002");
        fieldRule.setValidation(FieldRule.ValidationType.EXACT_MATCH);
        fieldRule.setSource("testData.bookingNumber");

        ComparisonRule compRule = new ComparisonRule();
        compRule.setSegment("BGM");
        compRule.addFieldRule(fieldRule);

        ruleSet = new RuleSet();
        ruleSet.addRule(compRule);

        // Create message
        Field field = Field.builder()
                .position("BGM.0002")
                .value("BOOKING123")
                .build();

        Segment segment = Segment.builder()
                .tag("BGM")
                .addField(field)
                .build();

        Message message = Message.builder()
                .fileFormat(FileFormat.EDIFACT)
                .addSegment(segment)
                .build();

        // Compare with test data
        ComparisonContext context = ComparisonContext.builder()
                .testData(testDataMap)
                .build();

        ComparisonEngine engine = new ComparisonEngine(ruleSet, context);
        ComparisonResult result = engine.compare(null, message);

        assertTrue(result.isSuccess());
    }

    @Test
    void testPatternMatchSuccess() {
        // Create rule with pattern
        FieldRule fieldRule = new FieldRule();
        fieldRule.setPosition("EQD.C237.8260");
        fieldRule.setValidation(FieldRule.ValidationType.PATTERN_MATCH);
        fieldRule.setPattern("^[A-Z]{4}[0-9]{7}$"); // Container number pattern

        ComparisonRule compRule = new ComparisonRule();
        compRule.setSegment("EQD");
        compRule.addFieldRule(fieldRule);

        ruleSet = new RuleSet();
        ruleSet.addRule(compRule);

        // Create message with valid container number
        Field field = Field.builder()
                .position("EQD.C237.8260")
                .value("ABCD1234567")
                .build();

        Segment segment = Segment.builder()
                .tag("EQD")
                .addField(field)
                .build();

        Message message = Message.builder()
                .fileFormat(FileFormat.EDIFACT)
                .addSegment(segment)
                .build();

        ComparisonContext context = ComparisonContext.builder().build();
        ComparisonEngine engine = new ComparisonEngine(ruleSet, context);
        ComparisonResult result = engine.compare(null, message);

        assertTrue(result.isSuccess());
    }

    @Test
    void testPatternMatchFailure() {
        // Create rule with pattern
        FieldRule fieldRule = new FieldRule();
        fieldRule.setPosition("EQD.C237.8260");
        fieldRule.setValidation(FieldRule.ValidationType.PATTERN_MATCH);
        fieldRule.setPattern("^[A-Z]{4}[0-9]{7}$");

        ComparisonRule compRule = new ComparisonRule();
        compRule.setSegment("EQD");
        compRule.addFieldRule(fieldRule);

        ruleSet = new RuleSet();
        ruleSet.addRule(compRule);

        // Create message with invalid container number
        Field field = Field.builder()
                .position("EQD.C237.8260")
                .value("INVALID")
                .build();

        Segment segment = Segment.builder()
                .tag("EQD")
                .addField(field)
                .build();

        Message message = Message.builder()
                .fileFormat(FileFormat.EDIFACT)
                .addSegment(segment)
                .build();

        ComparisonContext context = ComparisonContext.builder().build();
        ComparisonEngine engine = new ComparisonEngine(ruleSet, context);
        ComparisonResult result = engine.compare(null, message);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getDifferenceCount());
        assertEquals(Difference.DifferenceType.PATTERN_MISMATCH,
                result.getDifferences().get(0).getType());
    }

    @Test
    void testMissingRequiredField() {
        // Create rule with required field
        FieldRule fieldRule = new FieldRule();
        fieldRule.setPosition("BGM.0001");
        fieldRule.setRequired(true);
        fieldRule.setValidation(FieldRule.ValidationType.EXISTS);

        ComparisonRule compRule = new ComparisonRule();
        compRule.setSegment("BGM");
        compRule.addFieldRule(fieldRule);

        ruleSet = new RuleSet();
        ruleSet.addRule(compRule);

        // Create message without the required field
        Segment segment = Segment.builder()
                .tag("BGM")
                .build();

        Message message = Message.builder()
                .fileFormat(FileFormat.EDIFACT)
                .addSegment(segment)
                .build();

        ComparisonContext context = ComparisonContext.builder().build();
        ComparisonEngine engine = new ComparisonEngine(ruleSet, context);
        ComparisonResult result = engine.compare(null, message);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getDifferenceCount());
        assertEquals(Difference.DifferenceType.MISSING_FIELD,
                result.getDifferences().get(0).getType());
    }

    @Test
    void testMissingRequiredSegment() {
        // Create rule for required segment
        ComparisonRule compRule = new ComparisonRule();
        compRule.setSegment("BGM");
        compRule.setRequired(true);

        ruleSet = new RuleSet();
        ruleSet.addRule(compRule);

        // Create message without BGM segment
        Message message = Message.builder()
                .fileFormat(FileFormat.EDIFACT)
                .build();

        ComparisonContext context = ComparisonContext.builder().build();
        ComparisonEngine engine = new ComparisonEngine(ruleSet, context);
        ComparisonResult result = engine.compare(null, message);

        assertFalse(result.isSuccess());
        assertEquals(1, result.getDifferenceCount());
        assertEquals(Difference.DifferenceType.MISSING_SEGMENT,
                result.getDifferences().get(0).getType());
    }

    @Test
    void testMultipleOccurrences() {
        // Create rule allowing multiple occurrences
        FieldRule fieldRule = new FieldRule();
        fieldRule.setPosition("NAD.3035");
        fieldRule.setValidation(FieldRule.ValidationType.EXISTS);

        ComparisonRule compRule = new ComparisonRule();
        compRule.setSegment("NAD");
        compRule.setMultipleOccurrences(true);
        compRule.addFieldRule(fieldRule);

        ruleSet = new RuleSet();
        ruleSet.addRule(compRule);

        // Create message with multiple NAD segments
        Field field1 = Field.builder().position("NAD.3035").value("CZ").build();
        Field field2 = Field.builder().position("NAD.3035").value("CN").build();

        Segment nad1 = Segment.builder().tag("NAD").addField(field1).build();
        Segment nad2 = Segment.builder().tag("NAD").addField(field2).build();

        Message message = Message.builder()
                .fileFormat(FileFormat.EDIFACT)
                .addSegment(nad1)
                .addSegment(nad2)
                .build();

        ComparisonContext context = ComparisonContext.builder().build();
        ComparisonEngine engine = new ComparisonEngine(ruleSet, context);
        ComparisonResult result = engine.compare(null, message);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getSegmentsCompared());
    }

    @Test
    void testDateFormatValidation() {
        // Create rule for date format
        FieldRule fieldRule = new FieldRule();
        fieldRule.setPosition("DTM.C507.2380");
        fieldRule.setValidation(FieldRule.ValidationType.DATE_FORMAT);
        fieldRule.setDateFormatField("DTM.C507.2379");

        ComparisonRule compRule = new ComparisonRule();
        compRule.setSegment("DTM");
        compRule.addFieldRule(fieldRule);

        ruleSet = new RuleSet();
        ruleSet.addRule(compRule);

        // Create message with valid date in format 102
        Field formatField = Field.builder().position("DTM.C507.2379").value("102").build();
        Field dateField = Field.builder().position("DTM.C507.2380").value("20240207").build();

        Segment segment = Segment.builder()
                .tag("DTM")
                .addField(formatField)
                .addField(dateField)
                .build();

        Message message = Message.builder()
                .fileFormat(FileFormat.EDIFACT)
                .addSegment(segment)
                .build();

        ComparisonContext context = ComparisonContext.builder().build();
        ComparisonEngine engine = new ComparisonEngine(ruleSet, context);
        ComparisonResult result = engine.compare(null, message);

        assertTrue(result.isSuccess());
    }

    @Test
    void testResultSummary() {
        // Create simple rule
        FieldRule fieldRule = new FieldRule();
        fieldRule.setPosition("BGM.0001");
        fieldRule.setExpectedValue("340");

        ComparisonRule compRule = new ComparisonRule();
        compRule.setSegment("BGM");
        compRule.addFieldRule(fieldRule);

        ruleSet = new RuleSet();
        ruleSet.addRule(compRule);

        // Create message
        Field field = Field.builder().position("BGM.0001").value("350").build();
        Segment segment = Segment.builder().tag("BGM").addField(field).build();
        Message message = Message.builder()
                .fileFormat(FileFormat.EDIFACT)
                .addSegment(segment)
                .build();

        ComparisonContext context = ComparisonContext.builder().build();
        ComparisonEngine engine = new ComparisonEngine(ruleSet, context);
        ComparisonResult result = engine.compare(null, message);

        String summary = result.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("FAILED"));
        assertTrue(summary.contains("Differences: 1"));

        String detailed = result.getDetailedReport();
        assertNotNull(detailed);
        assertTrue(detailed.contains("Expected '340' but got '350'"));
    }
}