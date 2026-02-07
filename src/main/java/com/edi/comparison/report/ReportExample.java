package com.edi.comparison.report;

import com.edi.comparison.core.ComparisonContext;
import com.edi.comparison.core.ComparisonEngine;
import com.edi.comparison.model.*;
import com.edi.comparison.parser.EdifactParser;
import com.edi.comparison.rule.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Example showing how to generate HTML reports.
 *
 * Run this to see a sample HTML report.
 */
public class ReportExample {

    public static void main(String[] args) throws Exception {
        // Create sample comparison result
        ComparisonResult result = createSampleComparison();

        // Generate HTML report
        String outputPath = "sample-comparison-report.html";
        result.generateHtmlReport(outputPath);

        System.out.println("✓ HTML report generated: " + outputPath);
        System.out.println("\nReport summary:");
        System.out.println(result.getSummary());

        // Also show how to get HTML as string
        String htmlString = result.generateHtmlReport();
        System.out.println("\nHTML length: " + htmlString.length() + " characters");
    }

    /**
     * Creates a sample comparison with various types of differences.
     */
    private static ComparisonResult createSampleComparison() throws Exception {
        // Setup test data
        Map<String, Object> testData = new HashMap<>();
        testData.put("bgmCode", "340");
        testData.put("bookingNumber", "BOOKING123");

        // Create rules
        RuleSet rules = createSampleRules();

        // Create sample EDI messages
        String inboundEdi =
                "BGM+340+BOOKING123+9'\n" +
                        "NAD+CZ+SHIPPER001::92'\n" +
                        "EQD+CN+ABCD1234567+22G1:102'\n" +
                        "DTM+137:20240207:102'";

        String outboundEdi =
                "BGM+340+BOOKING999+9'\n" +  // Wrong booking number
                        "NAD+CZ+SHIPPER001::92'\n" +
                        "EQD+CN+INVALID+22G1:102'\n" +  // Invalid container
                        "DTM+137:2024020:102'";  // Invalid date format

        // Parse messages
        EdifactParser parser = new EdifactParser();
        Message inbound = parser.parse(inboundEdi);
        Message outbound = parser.parse(outboundEdi);

        // Setup context
        ComparisonContext context = ComparisonContext.builder()
                .testData(testData)
                .inboundMessage(inbound)
                .build();

        // Compare
        ComparisonEngine engine = new ComparisonEngine(rules, context);
        return engine.compare(inbound, outbound);
    }

    /**
     * Creates sample validation rules.
     */
    private static RuleSet createSampleRules() {
        RuleSet ruleSet = new RuleSet();
        ruleSet.setMessageType("IFTMBF");
        ruleSet.setDescription("Sample booking validation rules");

        // BGM rule
        ComparisonRule bgmRule = new ComparisonRule();
        bgmRule.setSegment("BGM");

        FieldRule bgmCode = new FieldRule();
        bgmCode.setPosition("BGM.0001");
        bgmCode.setName("documentCode");
        bgmCode.setValidation(FieldRule.ValidationType.EXACT_MATCH);
        bgmCode.setSource("testData.bgmCode");
        bgmRule.addFieldRule(bgmCode);

        FieldRule bookingNum = new FieldRule();
        bookingNum.setPosition("BGM.0002");
        bookingNum.setName("bookingNumber");
        bookingNum.setValidation(FieldRule.ValidationType.EXACT_MATCH);
        bookingNum.setSource("testData.bookingNumber");
        bgmRule.addFieldRule(bookingNum);

        ruleSet.addRule(bgmRule);

        // EQD rule
        ComparisonRule eqdRule = new ComparisonRule();
        eqdRule.setSegment("EQD");

        FieldRule containerNum = new FieldRule();
        containerNum.setPosition("EQD.C237.8260");
        containerNum.setName("containerNumber");
        containerNum.setValidation(FieldRule.ValidationType.PATTERN_MATCH);
        containerNum.setPattern("^[A-Z]{4}[0-9]{7}$");
        eqdRule.addFieldRule(containerNum);

        ruleSet.addRule(eqdRule);

        // DTM rule
        ComparisonRule dtmRule = new ComparisonRule();
        dtmRule.setSegment("DTM");

        FieldRule dateValue = new FieldRule();
        dateValue.setPosition("DTM.C507.2380");
        dateValue.setName("dateValue");
        dateValue.setValidation(FieldRule.ValidationType.DATE_FORMAT);
        dateValue.setDateFormatField("DTM.C507.2379");
        dtmRule.addFieldRule(dateValue);

        ruleSet.addRule(dtmRule);

        return ruleSet;
    }
}