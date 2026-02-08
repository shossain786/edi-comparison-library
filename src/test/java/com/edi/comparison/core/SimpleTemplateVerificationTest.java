package com.edi.comparison.core;

import com.edi.comparison.config.ComparisonConfig;
import com.edi.comparison.model.ComparisonResult;
import com.edi.comparison.model.Difference;
import com.edi.comparison.model.Message;
import com.edi.comparison.parser.EdifactParser;
import com.edi.comparison.report.HtmlReportGenerator;
import com.edi.comparison.rule.RuleLoader;
import com.edi.comparison.rule.RuleSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test demonstrating template-based outbound verification with report generation.
 *
 * <p>This test shows the basic workflow:
 * <ol>
 *   <li>Load configuration from properties file</li>
 *   <li>Load a template (YAML rules file)</li>
 *   <li>Parse the generated outbound file</li>
 *   <li>Verify outbound against the template</li>
 *   <li>Generate an HTML report in configured location</li>
 * </ol>
 */
class SimpleTemplateVerificationTest {

    private static final List<HtmlReportGenerator.ScenarioResult> scenarioResults =
            Collections.synchronizedList(new ArrayList<>());

    private ComparisonConfig config;
    private RuleLoader ruleLoader;
    private EdifactParser parser;
    private HtmlReportGenerator reportGenerator;

    @BeforeEach
    void setup() {
        // Load configuration from properties file
        config = ComparisonConfig.load();
        ruleLoader = new RuleLoader();
        parser = new EdifactParser();
        reportGenerator = new HtmlReportGenerator();

        System.out.println("Using config: " + config);
    }

    @AfterAll
    static void generateCombinedReport() throws Exception {
        if (!scenarioResults.isEmpty()) {
            HtmlReportGenerator generator = new HtmlReportGenerator();
            String path = generator.generateCombined(scenarioResults, ComparisonConfig.load().getReportBaseDir());
            System.out.println("Combined report: " + path);
        }
    }

    @Test
    void testBookingConfirmationVerification() throws Exception {
        String scenarioName = "booking-confirmation";

        // Load template
        RuleSet template = ruleLoader.loadFromResource("rules/outbound-verification-template.yaml");
        assertNotNull(template, "Template should be loaded");

        // Load outbound file
        String outboundContent = loadResourceAsString("samples/outbounds/bk_iftmbf_request_outbound");
        Message outbound = parser.parse(outboundContent);

        // Setup context with config and run verification
        ComparisonContext context = ComparisonContext.builder()
                .testData(new HashMap<>())
                .addConfig("detect_unexpected_segments", config.isDetectUnexpectedSegments())
                .build();

        ComparisonEngine engine = new ComparisonEngine(template, context);
        ComparisonResult result = engine.compare(null, outbound);
        scenarioResults.add(new HtmlReportGenerator.ScenarioResult(scenarioName, result, result.isSuccess()));

        // Generate report using configured base directory
        String reportPath = reportGenerator.generate(result, config.getReportBaseDir(), scenarioName);

        assertTrue(Files.exists(Path.of(reportPath)), "Report file should exist");
        printResult(scenarioName, result, reportPath);
        assertTrue(result.isSuccess(), "Verification should pass");
    }

    @Test
    void testBookingValidationFailure() throws Exception {
        String scenarioName = "booking-validation-failure";

        RuleSet template = ruleLoader.loadFromResource("rules/outbound-verification-template.yaml");

        String outboundWithErrors =
                "UNH+1+IFTMBC:D:99B:UN'\n" +
                "BGM+999+INVALID+9'\n" +
                "DTM+137:20260207:102'\n" +
                "RFF+BN:BOOKING789456'\n" +
                "NAD+CZ+SHIPPER001::92'\n" +
                "EQD+CN+BADCONTAINER+22G1:102'\n" +
                "CNT+16:1'\n" +
                "UNT+7+1'";

        Message outbound = parser.parse(outboundWithErrors);

        ComparisonContext context = ComparisonContext.builder()
                .testData(new HashMap<>())
                .addConfig("detect_unexpected_segments", config.isDetectUnexpectedSegments())
                .build();

        ComparisonEngine engine = new ComparisonEngine(template, context);
        ComparisonResult result = engine.compare(null, outbound);
        scenarioResults.add(new HtmlReportGenerator.ScenarioResult(scenarioName, result, !result.isSuccess()));

        // Generate report with custom filename
        String reportPath = reportGenerator.generate(result, config.getReportBaseDir(), scenarioName, "failure-report.html");

        assertTrue(Files.exists(Path.of(reportPath)), "Failure report should exist");
        assertFalse(result.isSuccess(), "Should fail verification");
        printResult(scenarioName, result, reportPath, false);
    }

    @Test
    void testSimpleBookingVerification() throws Exception {
        String scenarioName = "simple-booking";

        RuleSet template = ruleLoader.loadFromResource("rules/simple-test.yaml");

        String outbound =
                "BGM+340+BOOKING123+9'\n" +
                "NAD+CZ+SHIPPER001::92'";

        Map<String, Object> testData = new HashMap<>();
        testData.put("bookingNumber", "BOOKING123");

        ComparisonContext context = ComparisonContext.builder()
                .testData(testData)
                .addConfig("detect_unexpected_segments", config.isDetectUnexpectedSegments())
                .build();

        Message message = parser.parse(outbound);
        ComparisonEngine engine = new ComparisonEngine(template, context);
        ComparisonResult result = engine.compare(null, message);
        scenarioResults.add(new HtmlReportGenerator.ScenarioResult(scenarioName, result, result.isSuccess()));

        // Generate report
        String reportPath = reportGenerator.generate(result, config.getReportBaseDir(), scenarioName);

        assertTrue(Files.exists(Path.of(reportPath)), "Report should exist");
        assertTrue(result.isSuccess(), "Verification should pass");
        printResult(scenarioName, result, reportPath);
    }

    /**
     * Test verifying a generated outbound file (e.g., downloaded from FTP)
     * against the strict verification template.
     *
     * <p>This simulates the real-world scenario where:
     * <ol>
     *   <li>A baseline outbound exists: bk_iftmbf_request_outbound</li>
     *   <li>A new outbound is generated/downloaded: bk_iftmbf_request_outbound_generated</li>
     *   <li>We verify the generated file against the strict template rules</li>
     * </ol>
     *
     * <p>If the generated file has extra or missing segments, the test will fail
     * with detailed reporting showing line numbers and segment content.
     */
    @Test
    void testGeneratedOutboundVerification() throws Exception {
        String scenarioName = "ftp-generated-outbound";

        // Load the strict verification template with expected segment counts
        RuleSet template = ruleLoader.loadFromResource("rules/outbound-verification-strict-template.yaml");
        assertNotNull(template, "Template should be loaded");

        // Load the generated outbound file (simulating FTP download)
        String generatedContent = loadResourceAsString("samples/outbounds/bk_iftmbf_request_outbound_generated");
        Message generatedOutbound = parser.parse(generatedContent);

        // Setup context with all config options
        ComparisonContext context = ComparisonContext.builder()
                .testData(new HashMap<>())
                .addConfig("detect_unexpected_segments", config.isDetectUnexpectedSegments())
                .addConfig("validate_segment_order", config.isValidateSegmentOrder())
                .build();

        ComparisonEngine engine = new ComparisonEngine(template, context);
        ComparisonResult result = engine.compare(null, generatedOutbound);
        scenarioResults.add(new HtmlReportGenerator.ScenarioResult(scenarioName, result, result.isSuccess()));

        // Generate report
        String reportPath = reportGenerator.generate(result, config.getReportBaseDir(), scenarioName);

        assertTrue(Files.exists(Path.of(reportPath)), "Report file should exist");
        printResult(scenarioName, result, reportPath);

        // Print differences if any
        if (result.hasDifferences()) {
            System.out.println("Differences found:");
            result.getDifferences().forEach(diff ->
                    System.out.println("  - " + diff.getType() + ": " + diff.getDescription()));
        }

        assertTrue(result.isSuccess(), "Generated outbound should pass verification");
    }

    private void printResult(String scenario, ComparisonResult result, String reportPath) {
        printResult(scenario, result, reportPath, true);
    }

    private void printResult(String scenario, ComparisonResult result, String reportPath, boolean expectPass) {
        System.out.println("=== " + scenario + " ===");
        System.out.println("Comparison Result: " + (result.isSuccess() ? "PASSED" : "FAILED"));
        System.out.println("Differences: " + result.getDifferenceCount());
        System.out.println("Report: " + reportPath);

        boolean testPassed = expectPass ? result.isSuccess() : !result.isSuccess();
        System.out.println("Test Verdict: " + (testPassed ? "PASSED" : "FAILED")
                + (expectPass ? " (expected: PASS)" : " (expected: FAIL)"));
    }

    private String loadResourceAsString(String resourcePath) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes());
        }
    }
}
