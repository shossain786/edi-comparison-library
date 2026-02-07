package com.edi.comparison.report;

import com.edi.comparison.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HtmlReportGenerator.
 */
class HtmlReportGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void testGenerateSuccessReport() {
        // Create successful result
        ComparisonResult result = ComparisonResult.builder()
                .segmentsCompared(5)
                .fieldsCompared(12)
                .comparisonTimeMs(15)
                .build();

        HtmlReportGenerator generator = new HtmlReportGenerator();
        String html = generator.generateHtml(result);

        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("PASSED"));
        assertTrue(html.contains("All Validations Passed"));
        assertTrue(html.contains("5")); // segments compared
        assertTrue(html.contains("12")); // fields compared
    }

    @Test
    void testGenerateFailureReport() {
        // Create result with differences
        Difference diff1 = Difference.builder()
                .segmentTag("BGM")
                .fieldPosition("BGM.0001")
                .fieldName("documentCode")
                .expected("340")
                .actual("350")
                .type(Difference.DifferenceType.VALUE_MISMATCH)
                .lineNumber(2)
                .build();

        Difference diff2 = Difference.builder()
                .segmentTag("EQD")
                .fieldPosition("EQD.C237.8260")
                .fieldName("containerNumber")
                .expected("Pattern: ^[A-Z]{4}[0-9]{7}$")
                .actual("INVALID")
                .type(Difference.DifferenceType.PATTERN_MISMATCH)
                .lineNumber(4)
                .description("Value does not match pattern")
                .build();

        ComparisonResult result = ComparisonResult.builder()
                .addDifference(diff1)
                .addDifference(diff2)
                .segmentsCompared(3)
                .fieldsCompared(8)
                .comparisonTimeMs(18)
                .build();

        HtmlReportGenerator generator = new HtmlReportGenerator();
        String html = generator.generateHtml(result);

        assertNotNull(html);
        assertTrue(html.contains("FAILED"));
        assertTrue(html.contains("BGM"));
        assertTrue(html.contains("documentCode"));
        assertTrue(html.contains("340"));
        assertTrue(html.contains("350"));
        assertTrue(html.contains("INVALID"));
        assertTrue(html.contains("VALUE_MISMATCH"));
        assertTrue(html.contains("PATTERN_MISMATCH"));
        assertTrue(html.contains("Line 2"));
        assertTrue(html.contains("Line 4"));
    }

    @Test
    void testGenerateToFile() throws Exception {
        Difference diff = Difference.builder()
                .segmentTag("BGM")
                .expected("340")
                .actual("350")
                .type(Difference.DifferenceType.VALUE_MISMATCH)
                .build();

        ComparisonResult result = ComparisonResult.builder()
                .addDifference(diff)
                .segmentsCompared(1)
                .fieldsCompared(1)
                .comparisonTimeMs(10)
                .build();

        File reportFile = tempDir.resolve("test-report.html").toFile();

        HtmlReportGenerator generator = new HtmlReportGenerator();
        generator.generate(result, reportFile.getAbsolutePath());

        assertTrue(reportFile.exists());

        String content = Files.readString(reportFile.toPath());
        assertTrue(content.contains("<!DOCTYPE html>"));
        assertTrue(content.contains("BGM"));
        assertTrue(content.contains("340"));
    }

    @Test
    void testGenerateWithMissingField() {
        Difference diff = Difference.builder()
                .segmentTag("BGM")
                .fieldPosition("BGM.0001")
                .fieldName("documentCode")
                .type(Difference.DifferenceType.MISSING_FIELD)
                .description("Required field BGM.0001 is missing")
                .lineNumber(2)
                .build();

        ComparisonResult result = ComparisonResult.builder()
                .addDifference(diff)
                .segmentsCompared(1)
                .fieldsCompared(1)
                .comparisonTimeMs(5)
                .build();

        HtmlReportGenerator generator = new HtmlReportGenerator();
        String html = generator.generateHtml(result);

        assertTrue(html.contains("MISSING_FIELD"));
        assertTrue(html.contains("Required field"));
        assertTrue(html.contains("missing"));
    }

    @Test
    void testGenerateWithMissingSegment() {
        Difference diff = Difference.builder()
                .segmentTag("BGM")
                .type(Difference.DifferenceType.MISSING_SEGMENT)
                .description("Required segment BGM is missing")
                .build();

        ComparisonResult result = ComparisonResult.builder()
                .addDifference(diff)
                .segmentsCompared(0)
                .fieldsCompared(0)
                .comparisonTimeMs(3)
                .build();

        HtmlReportGenerator generator = new HtmlReportGenerator();
        String html = generator.generateHtml(result);

        assertTrue(html.contains("MISSING_SEGMENT"));
        assertTrue(html.contains("BGM"));
    }

    @Test
    void testGenerateWithMultipleDifferenceTypes() {
        ComparisonResult result = ComparisonResult.builder()
                .addDifference(Difference.builder()
                        .type(Difference.DifferenceType.VALUE_MISMATCH)
                        .segmentTag("BGM")
                        .expected("A")
                        .actual("B")
                        .build())
                .addDifference(Difference.builder()
                        .type(Difference.DifferenceType.PATTERN_MISMATCH)
                        .segmentTag("EQD")
                        .expected("Pattern")
                        .actual("Invalid")
                        .build())
                .addDifference(Difference.builder()
                        .type(Difference.DifferenceType.MISSING_FIELD)
                        .segmentTag("NAD")
                        .build())
                .segmentsCompared(3)
                .fieldsCompared(5)
                .comparisonTimeMs(12)
                .build();

        HtmlReportGenerator generator = new HtmlReportGenerator();
        String html = generator.generateHtml(result);

        assertTrue(html.contains("Differences Found"));
        assertTrue(html.contains("3")); // 3 differences
        assertTrue(html.contains("VALUE_MISMATCH"));
        assertTrue(html.contains("PATTERN_MISMATCH"));
        assertTrue(html.contains("MISSING_FIELD"));
    }

    @Test
    void testHtmlEscaping() {
        Difference diff = Difference.builder()
                .segmentTag("TEST")
                .expected("<script>alert('xss')</script>")
                .actual("A & B < C > D")
                .type(Difference.DifferenceType.VALUE_MISMATCH)
                .build();

        ComparisonResult result = ComparisonResult.builder()
                .addDifference(diff)
                .segmentsCompared(1)
                .fieldsCompared(1)
                .comparisonTimeMs(5)
                .build();

        HtmlReportGenerator generator = new HtmlReportGenerator();
        String html = generator.generateHtml(result);

        // Should be escaped
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("&amp;"));
        assertFalse(html.contains("<script>alert"));
    }

    @Test
    void testComparisonResultGenerateHtmlReport() throws Exception {
        Difference diff = Difference.builder()
                .segmentTag("BGM")
                .expected("340")
                .actual("350")
                .type(Difference.DifferenceType.VALUE_MISMATCH)
                .build();

        ComparisonResult result = ComparisonResult.builder()
                .addDifference(diff)
                .segmentsCompared(1)
                .fieldsCompared(1)
                .comparisonTimeMs(10)
                .build();

        // Test generating to string
        String html = result.generateHtmlReport();
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("BGM"));

        // Test generating to file
        File reportFile = tempDir.resolve("result-report.html").toFile();
        result.generateHtmlReport(reportFile.getAbsolutePath());

        assertTrue(reportFile.exists());
        String fileContent = Files.readString(reportFile.toPath());
        assertTrue(fileContent.contains("BGM"));
    }
}