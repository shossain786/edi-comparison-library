package com.edi.comparison.core;

import com.edi.comparison.model.*;
import com.edi.comparison.parser.EdifactParser;
import com.edi.comparison.parser.ParseException;
import com.edi.comparison.rule.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test showing complete workflow.
 *
 * <p>This demonstrates how to use the library in your actual test framework.
 */
class IntegrationTest {

    private Map<String, Object> testDataMap;
    private RuleSet ruleSet;

    @BeforeEach
    void setup() throws Exception {
        // Setup test data (from your datamap)
        testDataMap = new HashMap<>();
        testDataMap.put("bgmCode", "340");
        testDataMap.put("bookingNumber", "BOOKING123");
        testDataMap.put("containerNumber", "ABCD1234567");

        // Load rules from YAML (or create programmatically)
        ruleSet = createBookingRules();
    }

    @Test
    void testCompleteBookingValidation() throws ParseException {
        // 1. Create inbound EDI file content
        String inboundEdi =
                "UNH+1+IFTMBF:D:96A:UN'\n" +
                        "BGM+340+BOOKING123+9'\n" +
                        "NAD+CZ+SHIPPER001::92'\n" +
                        "EQD+CN+ABCD1234567+22G1:102'\n" +
                        "UNT+4+1'";

        // 2. Create expected outbound EDI file content
        String outboundEdi =
                "UNH+1+IFTMBF:D:96A:UN'\n" +
                        "BGM+340+BOOKING123+9'\n" +
                        "NAD+CZ+SHIPPER001::92'\n" +
                        "EQD+CN+ABCD1234567+22G1:102'\n" +
                        "UNT+4+1'";

        // 3. Parse both files
        EdifactParser parser = new EdifactParser();
        Message inbound = parser.parse(inboundEdi);
        Message outbound = parser.parse(outboundEdi);

        // 4. Setup comparison context
        ComparisonContext context = ComparisonContext.builder()
                .testData(testDataMap)
                .inboundMessage(inbound)
                .build();

        // 5. Run comparison
        ComparisonEngine engine = new ComparisonEngine(ruleSet, context);
        ComparisonResult result = engine.compare(inbound, outbound);

        // 6. Assert results
        assertTrue(result.isSuccess(),
                "Validation failed: " + result.getSummary());
        assertEquals(0, result.getDifferenceCount());
    }

    @Test
    void testBookingValidationWithMismatch() throws ParseException {
        // Inbound file
        String inboundEdi =
                "BGM+340+BOOKING123+9'\n" +
                        "NAD+CZ+SHIPPER001::92'";

        // Outbound with wrong booking number
        String outboundEdi =
                "BGM+340+BOOKING999+9'\n" +  // Wrong booking number!
                        "NAD+CZ+SHIPPER001::92'";

        EdifactParser parser = new EdifactParser();
        Message inbound = parser.parse(inboundEdi);
        Message outbound = parser.parse(outboundEdi);

        ComparisonContext context = ComparisonContext.builder()
                .testData(testDataMap)
                .inboundMessage(inbound)
                .build();

        ComparisonEngine engine = new ComparisonEngine(ruleSet, context);
        ComparisonResult result = engine.compare(inbound, outbound);

        // Should fail with value mismatch
        assertFalse(result.isSuccess());
        assertEquals(1, result.getDifferenceCount());

        Difference diff = result.getDifferences().get(0);
        assertEquals("BGM", diff.getSegmentTag());
        assertEquals("BOOKING123", diff.getExpected());
        assertEquals("BOOKING999", diff.getActual());

        // Print detailed report
        System.out.println(result.getDetailedReport());
    }

    @Test
    void testMultipleContainersValidation() throws ParseException {
        // Outbound with multiple containers
        String outboundEdi =
                "BGM+340+BOOKING123+9'\n" +
                        "EQD+CN+ABCD1234567+22G1:102'\n" +
                        "EQD+CN+WXYZ9876543+22G1:102'\n" +
                        "EQD+CN+PQRS5555555+22G1:102'";

        EdifactParser parser = new EdifactParser();
        Message outbound = parser.parse(outboundEdi);

        ComparisonContext context = ComparisonContext.builder()
                .testData(testDataMap)
                .build();

        ComparisonEngine engine = new ComparisonEngine(ruleSet, context);
        ComparisonResult result = engine.compare(null, outbound);

        // Should validate all 3 containers
        assertTrue(result.isSuccess());
        assertTrue(result.getSegmentsCompared() >= 3); // BGM + 3 EQD
    }

    @Test
    void testInvalidContainerNumber() throws ParseException {
        // Container with invalid format
        String outboundEdi =
                "BGM+340+BOOKING123+9'\n" +
                        "EQD+CN+INVALID+22G1:102'";  // Invalid container number

        EdifactParser parser = new EdifactParser();
        Message outbound = parser.parse(outboundEdi);

        ComparisonContext context = ComparisonContext.builder()
                .testData(testDataMap)
                .build();

        ComparisonEngine engine = new ComparisonEngine(ruleSet, context);
        ComparisonResult result = engine.compare(null, outbound);

        // Should fail pattern validation
        assertFalse(result.isSuccess());

        Difference diff = result.getDifferences().get(0);
        assertEquals(Difference.DifferenceType.PATTERN_MISMATCH, diff.getType());
        assertTrue(diff.getDescription().contains("pattern"));
    }

    /**
     * Creates booking validation rules programmatically.
     * In real usage, load from YAML file using RuleLoader.
     */
    private RuleSet createBookingRules() {
        RuleSet ruleSet = new RuleSet();
        ruleSet.setMessageType("IFTMBF");

        // BGM segment rule
        ComparisonRule bgmRule = new ComparisonRule();
        bgmRule.setSegment("BGM");
        bgmRule.setRequired(true);

        // BGM document code
        FieldRule bgmCode = new FieldRule();
        bgmCode.setPosition("BGM.0001");
        bgmCode.setName("documentCode");
        bgmCode.setValidation(FieldRule.ValidationType.EXACT_MATCH);
        bgmCode.setSource("testData.bgmCode");
        bgmRule.addFieldRule(bgmCode);

        // BGM booking number
        FieldRule bookingNum = new FieldRule();
        bookingNum.setPosition("BGM.0002");
        bookingNum.setName("bookingNumber");
        bookingNum.setValidation(FieldRule.ValidationType.EXACT_MATCH);
        bookingNum.setSource("testData.bookingNumber");
        bgmRule.addFieldRule(bookingNum);

        ruleSet.addRule(bgmRule);

        // EQD segment rule (container)
        ComparisonRule eqdRule = new ComparisonRule();
        eqdRule.setSegment("EQD");
        eqdRule.setMultipleOccurrences(true);
        eqdRule.setRequired(false);

        // Container qualifier
        FieldRule eqdQualifier = new FieldRule();
        eqdQualifier.setPosition("EQD.8053");
        eqdQualifier.setValidation(FieldRule.ValidationType.EXACT_MATCH);
        eqdQualifier.setExpectedValue("CN");
        eqdRule.addFieldRule(eqdQualifier);

        // Container number with pattern validation
        FieldRule containerNum = new FieldRule();
        containerNum.setPosition("EQD.C237.8260");
        containerNum.setName("containerNumber");
        containerNum.setValidation(FieldRule.ValidationType.PATTERN_MATCH);
        containerNum.setPattern("^[A-Z]{4}[0-9]{7}$");
        eqdRule.addFieldRule(containerNum);

        ruleSet.addRule(eqdRule);

        return ruleSet;
    }

    /**
     * Example: How you would use this in your actual Selenium test.
     */
    @Test
    void exampleSeleniumTestUsage() throws Exception {
        // Your existing test setup
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("bgmCode", "340");
        dataMap.put("bookingNumber", "BKG" + System.currentTimeMillis());

        // Load rules from your rules directory
        RuleLoader loader = new RuleLoader();
        RuleSet rules = createBookingRules();
        // In real code: loader.loadFromFile("src/test/resources/rules/booking.yaml");

        // Setup comparison context
        ComparisonContext context = ComparisonContext.builder()
                .testData(dataMap)
                .build();

        // Your test steps
        // 1. Drop inbound file (your existing code)
        String inboundPath = "path/to/inbound.edi";

        // 2. Wait for outbound generation (your existing code)
        String outboundPath = "path/to/outbound.edi";

        // 3. Parse and compare
        EdifactParser parser = new EdifactParser();
        // Message inbound = parser.parse(new File(inboundPath));
        // Message outbound = parser.parse(new File(outboundPath));

        // ComparisonEngine engine = new ComparisonEngine(rules, context);
        // ComparisonResult result = engine.compare(inbound, outbound);

        // 4. Assert
        // if (result.hasDifferences()) {
        //     String reportPath = "reports/comparison-" + testName + ".html";
        //     // Generate HTML report (Step 6)
        //     fail("Validation failed. See report: " + reportPath);
        // }

        // This replaces your line-by-line verification!
    }
}