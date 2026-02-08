package com.edi.comparison.report;

import com.edi.comparison.model.*;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Generates HTML comparison reports.
 *
 * <p>Creates a detailed, color-coded HTML report showing:
 * <ul>
 *   <li>Summary statistics</li>
 *   <li>All differences with context</li>
 *   <li>Side-by-side segment comparison</li>
 *   <li>Line numbers for easy debugging</li>
 * </ul>
 *
 * <p>Example:
 * <pre>
 * HtmlReportGenerator generator = new HtmlReportGenerator();
 * generator.generate(result, "reports/comparison-report.html");
 * </pre>
 */
public class HtmlReportGenerator {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Pairs a scenario name with its comparison result for combined reporting.
     *
     * @param scenarioName display name for the scenario
     * @param result       the comparison result
     * @param passed       whether the test considers this scenario passed
     *                     (e.g. an expected-failure test passes when comparison fails)
     */
    public record ScenarioResult(String scenarioName, ComparisonResult result, boolean passed) {}

    /**
     * Generates HTML report and saves to file.
     * Creates parent directories if they don't exist.
     *
     * @param result comparison result
     * @param outputPath output file path
     * @return the absolute path where the report was saved
     * @throws IOException if file cannot be written
     */
    public String generate(ComparisonResult result, String outputPath) throws IOException {
        Path path = Paths.get(outputPath);

        // Create parent directories if they don't exist
        Path parentDir = path.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        try (FileWriter writer = new FileWriter(path.toFile())) {
            generate(result, writer);
        }

        return path.toAbsolutePath().toString();
    }

    /**
     * Generates HTML report in the specified directory with scenario-based organization.
     * Creates directory structure: {baseDir}/{scenarioName}/report_{timestamp}.html
     *
     * @param result comparison result
     * @param baseDir base directory for reports (e.g., "target/reports")
     * @param scenarioName scenario or test name for folder organization
     * @return the absolute path where the report was saved
     * @throws IOException if file cannot be written
     */
    public String generate(ComparisonResult result, String baseDir, String scenarioName) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("report_%s.html", timestamp);

        Path reportPath = Paths.get(baseDir, scenarioName, fileName);
        return generate(result, reportPath.toString());
    }

    /**
     * Generates HTML report with a custom filename in the specified directory.
     * Creates directory structure: {baseDir}/{scenarioName}/{fileName}
     *
     * @param result comparison result
     * @param baseDir base directory for reports (e.g., "target/reports")
     * @param scenarioName scenario or test name for folder organization
     * @param fileName custom filename for the report
     * @return the absolute path where the report was saved
     * @throws IOException if file cannot be written
     */
    public String generate(ComparisonResult result, String baseDir, String scenarioName, String fileName) throws IOException {
        Path reportPath = Paths.get(baseDir, scenarioName, fileName);
        return generate(result, reportPath.toString());
    }

    /**
     * Generates HTML report to a Writer.
     *
     * @param result comparison result
     * @param writer output writer
     * @throws IOException if writing fails
     */
    public void generate(ComparisonResult result, Writer writer) throws IOException {
        writer.write(generateHtml(result));
    }

    /**
     * Generates HTML report as string.
     *
     * @param result comparison result
     * @return HTML string
     */
    public String generateHtml(ComparisonResult result) {
        StringBuilder html = new StringBuilder();

        // HTML header
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>EDI Comparison Report</title>\n");
        html.append(getStyles());
        html.append("</head>\n");
        html.append("<body>\n");

        // Report header
        html.append(generateHeader(result));

        // Summary section
        html.append(generateSummary(result));

        // Differences section
        if (result.hasDifferences()) {
            html.append(generateDifferences(result));
        } else {
            html.append(generateSuccessMessage());
        }

        // Footer
        html.append(generateFooter());

        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    /**
     * Generates CSS styles (dark theme).
     */
    private String getStyles() {
        return """
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }

                body {
                    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                    background: #0d1117;
                    padding: 20px;
                    line-height: 1.6;
                    color: #c9d1d9;
                }

                .container {
                    max-width: 1200px;
                    margin: 0 auto;
                    background: #161b22;
                    border-radius: 8px;
                    box-shadow: 0 4px 20px rgba(0,0,0,0.4);
                    overflow: hidden;
                    border: 1px solid #30363d;
                }

                .header {
                    background: linear-gradient(135deg, #238636 0%, #1f6feb 100%);
                    color: white;
                    padding: 30px;
                }

                .header h1 {
                    font-size: 2em;
                    margin-bottom: 10px;
                }

                .header .timestamp {
                    opacity: 0.9;
                    font-size: 0.9em;
                }

                .summary {
                    padding: 30px;
                    background: #21262d;
                    border-bottom: 1px solid #30363d;
                }

                .status-badge {
                    display: inline-block;
                    padding: 8px 16px;
                    border-radius: 20px;
                    font-weight: bold;
                    font-size: 1.1em;
                    margin-bottom: 20px;
                }

                .status-success {
                    background: #238636;
                    color: white;
                }

                .status-failure {
                    background: #da3633;
                    color: white;
                }

                .stats {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 20px;
                    margin-top: 20px;
                }

                .stat-card {
                    background: #0d1117;
                    padding: 20px;
                    border-radius: 8px;
                    border-left: 4px solid #58a6ff;
                    border: 1px solid #30363d;
                    border-left: 4px solid #58a6ff;
                }

                .stat-label {
                    color: #8b949e;
                    font-size: 0.9em;
                    margin-bottom: 5px;
                }

                .stat-value {
                    font-size: 2em;
                    font-weight: bold;
                    color: #f0f6fc;
                }

                .differences {
                    padding: 30px;
                }

                .differences h2 {
                    color: #f0f6fc;
                    margin-bottom: 20px;
                    padding-bottom: 10px;
                    border-bottom: 2px solid #58a6ff;
                }

                .difference-item {
                    background: #0d1117;
                    border: 1px solid #30363d;
                    border-radius: 8px;
                    padding: 20px;
                    margin-bottom: 15px;
                    border-left: 4px solid #da3633;
                }

                .difference-item strong {
                    color: #f0f6fc;
                }

                .difference-item p {
                    color: #c9d1d9;
                }

                .difference-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-bottom: 15px;
                }

                .difference-type {
                    color: white;
                    padding: 4px 12px;
                    border-radius: 4px;
                    font-size: 0.85em;
                    font-weight: bold;
                }

                .category-group {
                    margin-bottom: 30px;
                }

                .category-header {
                    display: flex;
                    align-items: center;
                    gap: 12px;
                    margin-bottom: 15px;
                    padding: 12px 16px;
                    border-radius: 8px;
                    background: #21262d;
                    border: 1px solid #30363d;
                }

                .category-label {
                    font-size: 1.2em;
                    font-weight: bold;
                    color: #f0f6fc;
                }

                .category-desc {
                    font-size: 0.85em;
                    color: #8b949e;
                    font-style: italic;
                }

                .category-count {
                    margin-left: auto;
                    padding: 4px 12px;
                    border-radius: 12px;
                    font-size: 0.85em;
                    font-weight: bold;
                    color: white;
                }

                .difference-location {
                    color: #8b949e;
                    font-size: 0.9em;
                }

                .difference-details {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 20px;
                    margin-top: 15px;
                }

                .value-box {
                    padding: 15px;
                    border-radius: 6px;
                    font-family: 'Courier New', monospace;
                }

                .expected-value {
                    background: #1b4332;
                    border: 1px solid #238636;
                    color: #7ee787;
                }

                .actual-value {
                    background: #3d1418;
                    border: 1px solid #da3633;
                    color: #ffa198;
                }

                .value-label {
                    font-size: 0.85em;
                    font-weight: bold;
                    margin-bottom: 8px;
                    text-transform: uppercase;
                    color: #8b949e;
                }

                .value-content {
                    font-size: 1.1em;
                    word-break: break-all;
                }

                .success-message {
                    padding: 50px 30px;
                    text-align: center;
                }

                .success-icon {
                    font-size: 4em;
                    color: #238636;
                    margin-bottom: 20px;
                }

                .success-message h2 {
                    color: #7ee787;
                    margin-bottom: 10px;
                }

                .success-message p {
                    color: #8b949e;
                }

                .footer {
                    background: #21262d;
                    padding: 20px 30px;
                    text-align: center;
                    color: #8b949e;
                    font-size: 0.9em;
                    border-top: 1px solid #30363d;
                }

                @media (max-width: 768px) {
                    .difference-details {
                        grid-template-columns: 1fr;
                    }
                }
            </style>
            """;
    }

    /**
     * Generates report header.
     */
    private String generateHeader(ComparisonResult result) {
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);

        return String.format("""
            <div class="container">
                <div class="header">
                    <h1>📊 EDI Comparison Report</h1>
                    <div class="timestamp">Generated: %s</div>
                </div>
            """, timestamp);
    }

    /**
     * Generates summary section.
     */
    private String generateSummary(ComparisonResult result) {
        String statusClass = result.isSuccess() ? "status-success" : "status-failure";
        String statusText = result.isSuccess() ? "✓ PASSED" : "✗ FAILED";

        StringBuilder html = new StringBuilder();
        html.append("    <div class=\"summary\">\n");
        html.append(String.format("        <div class=\"status-badge %s\">%s</div>\n",
                statusClass, statusText));

        html.append("        <div class=\"stats\">\n");

        // Differences count
        html.append(generateStatCard("Differences Found",
                String.valueOf(result.getDifferenceCount())));

        // Segments compared
        html.append(generateStatCard("Segments Compared",
                String.valueOf(result.getSegmentsCompared())));

        // Fields compared
        html.append(generateStatCard("Fields Compared",
                String.valueOf(result.getFieldsCompared())));

        // Time taken
        html.append(generateStatCard("Time Taken",
                result.getComparisonTimeMs() + " ms"));

        html.append("        </div>\n");
        html.append("    </div>\n");

        return html.toString();
    }

    /**
     * Generates a stat card.
     */
    private String generateStatCard(String label, String value) {
        return String.format("""
                <div class="stat-card">
                    <div class="stat-label">%s</div>
                    <div class="stat-value">%s</div>
                </div>
            """, label, value);
    }

    /**
     * Generates differences section grouped by persona-based failure categories.
     */
    private String generateDifferences(ComparisonResult result) {
        StringBuilder html = new StringBuilder();
        html.append("    <div class=\"differences\">\n");
        html.append("        <h2>Differences Details</h2>\n");

        // Group by failure category, preserving enum declaration order
        Map<Difference.FailureCategory, List<Difference>> byCategory =
                result.getDifferences().stream()
                        .collect(Collectors.groupingBy(
                                d -> d.getType().getCategory(),
                                LinkedHashMap::new,
                                Collectors.toList()));

        int index = 1;
        for (Map.Entry<Difference.FailureCategory, List<Difference>> entry : byCategory.entrySet()) {
            Difference.FailureCategory category = entry.getKey();
            List<Difference> diffs = entry.getValue();

            html.append("        <div class=\"category-group\">\n");
            html.append("            <div class=\"category-header\" style=\"border-left: 4px solid ")
                    .append(category.getAccentColor()).append(";\">\n");
            html.append("                <span class=\"category-label\">").append(category.getLabel()).append("</span>\n");
            html.append("                <span class=\"category-desc\">").append(category.getDescription()).append("</span>\n");
            html.append("                <span class=\"category-count\" style=\"background: ")
                    .append(category.getAccentColor()).append(";\">").append(diffs.size()).append("</span>\n");
            html.append("            </div>\n");

            for (Difference diff : diffs) {
                html.append(generateDifferenceItem(diff, index++, category.getAccentColor()));
            }

            html.append("        </div>\n");
        }

        html.append("    </div>\n");
        return html.toString();
    }

    /**
     * Generates a single difference item with category-specific accent color.
     */
    private String generateDifferenceItem(Difference diff, int index, String accentColor) {
        StringBuilder html = new StringBuilder();

        html.append("        <div class=\"difference-item\" style=\"border-left-color: ")
                .append(accentColor).append(";\">\n");
        html.append("            <div class=\"difference-header\">\n");
        html.append(String.format("                <strong>#%d</strong>\n", index));
        html.append(String.format("                <span class=\"difference-type\" style=\"background: %s;\">%s</span>\n",
                accentColor, diff.getType()));
        html.append("            </div>\n");

        // Location
        StringBuilder location = new StringBuilder();
        if (diff.getSegmentTag() != null) {
            location.append(diff.getSegmentTag());
        }
        if (diff.getFieldPosition() != null) {
            location.append(".").append(diff.getFieldPosition());
        }
        if (diff.getFieldName() != null) {
            location.append(" (").append(diff.getFieldName()).append(")");
        }
        if (diff.getLineNumber() > 0) {
            location.append(" • Line ").append(diff.getLineNumber());
        }

        html.append(String.format("            <div class=\"difference-location\">%s</div>\n",
                location.toString()));

        // Description
        if (diff.getDescription() != null) {
            html.append(String.format("            <p>%s</p>\n",
                    escapeHtml(diff.getDescription())));
        }

        // Expected vs Actual
        if (diff.getExpected() != null || diff.getActual() != null) {
            html.append("            <div class=\"difference-details\">\n");

            html.append("                <div class=\"value-box expected-value\">\n");
            html.append("                    <div class=\"value-label\">Expected</div>\n");
            html.append(String.format("                    <div class=\"value-content\">%s</div>\n",
                    escapeHtml(diff.getExpected() != null ? diff.getExpected() : "(not specified)")));
            html.append("                </div>\n");

            html.append("                <div class=\"value-box actual-value\">\n");
            html.append("                    <div class=\"value-label\">Actual</div>\n");
            html.append(String.format("                    <div class=\"value-content\">%s</div>\n",
                    escapeHtml(diff.getActual() != null ? diff.getActual() : "(missing)")));
            html.append("                </div>\n");

            html.append("            </div>\n");
        }

        html.append("        </div>\n");

        return html.toString();
    }

    /**
     * Generates success message.
     */
    private String generateSuccessMessage() {
        return """
                <div class="success-message">
                    <div class="success-icon">✓</div>
                    <h2>All Validations Passed!</h2>
                    <p>No differences were found between expected and actual values.</p>
                </div>
            """;
    }

    /**
     * Generates footer.
     */
    private String generateFooter() {
        return """
                <div class="footer">
                    Generated by EDI Comparison Library
                </div>
            </div>
            """;
    }

    // ==================== Combined Report Methods ====================

    /**
     * Generates a combined HTML report as a string containing all scenarios.
     *
     * @param scenarios list of scenario results
     * @return HTML string
     */
    public String generateCombinedHtml(List<ScenarioResult> scenarios) {
        StringBuilder html = new StringBuilder();
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);

        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>EDI Combined Comparison Report</title>\n");
        html.append(getCombinedStyles());
        html.append("</head>\n");
        html.append("<body>\n");

        html.append(generateCombinedHeader(scenarios, timestamp));
        html.append(generateDashboard(scenarios));
        html.append(generateScenarioIndex(scenarios));
        html.append(generateScenarioSections(scenarios));
        html.append(generateFooter());
        html.append(getCombinedScript());

        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    /**
     * Generates a combined HTML report and saves to a file in the specified directory.
     * Creates file: {baseDir}/combined_report_{timestamp}.html
     *
     * @param scenarios list of scenario results
     * @param baseDir base directory for reports
     * @return the absolute path where the report was saved
     * @throws IOException if file cannot be written
     */
    public String generateCombined(List<ScenarioResult> scenarios, String baseDir) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("combined_report_%s.html", timestamp);
        Path reportPath = Paths.get(baseDir, fileName);

        Path parentDir = reportPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        try (FileWriter writer = new FileWriter(reportPath.toFile())) {
            generateCombined(scenarios, writer);
        }

        return reportPath.toAbsolutePath().toString();
    }

    /**
     * Generates a combined HTML report to a Writer.
     *
     * @param scenarios list of scenario results
     * @param writer output writer
     * @throws IOException if writing fails
     */
    public void generateCombined(List<ScenarioResult> scenarios, Writer writer) throws IOException {
        writer.write(generateCombinedHtml(scenarios));
    }

    /**
     * CSS styles for the combined report (extends base styles).
     */
    private String getCombinedStyles() {
        return """
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }

                body {
                    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                    background: #0d1117;
                    padding: 20px;
                    line-height: 1.6;
                    color: #c9d1d9;
                }

                .container {
                    max-width: 1200px;
                    margin: 0 auto;
                    background: #161b22;
                    border-radius: 8px;
                    box-shadow: 0 4px 20px rgba(0,0,0,0.4);
                    overflow: hidden;
                    border: 1px solid #30363d;
                }

                .header {
                    background: linear-gradient(135deg, #238636 0%, #1f6feb 100%);
                    color: white;
                    padding: 30px;
                }

                .header h1 {
                    font-size: 2em;
                    margin-bottom: 10px;
                }

                .header .timestamp {
                    opacity: 0.9;
                    font-size: 0.9em;
                }

                .summary {
                    padding: 30px;
                    background: #21262d;
                    border-bottom: 1px solid #30363d;
                }

                .status-badge {
                    display: inline-block;
                    padding: 8px 16px;
                    border-radius: 20px;
                    font-weight: bold;
                    font-size: 1.1em;
                    margin-bottom: 20px;
                }

                .status-success {
                    background: #238636;
                    color: white;
                }

                .status-failure {
                    background: #da3633;
                    color: white;
                }

                .stats {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 20px;
                    margin-top: 20px;
                }

                .stat-card {
                    background: #0d1117;
                    padding: 20px;
                    border-radius: 8px;
                    border: 1px solid #30363d;
                    border-left: 4px solid #58a6ff;
                }

                .stat-label {
                    color: #8b949e;
                    font-size: 0.9em;
                    margin-bottom: 5px;
                }

                .stat-value {
                    font-size: 2em;
                    font-weight: bold;
                    color: #f0f6fc;
                }

                .differences {
                    padding: 30px;
                }

                .differences h2 {
                    color: #f0f6fc;
                    margin-bottom: 20px;
                    padding-bottom: 10px;
                    border-bottom: 2px solid #58a6ff;
                }

                .difference-item {
                    background: #0d1117;
                    border: 1px solid #30363d;
                    border-radius: 8px;
                    padding: 20px;
                    margin-bottom: 15px;
                    border-left: 4px solid #da3633;
                }

                .difference-item strong {
                    color: #f0f6fc;
                }

                .difference-item p {
                    color: #c9d1d9;
                }

                .difference-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-bottom: 15px;
                }

                .difference-type {
                    color: white;
                    padding: 4px 12px;
                    border-radius: 4px;
                    font-size: 0.85em;
                    font-weight: bold;
                }

                .category-group {
                    margin-bottom: 30px;
                }

                .category-header {
                    display: flex;
                    align-items: center;
                    gap: 12px;
                    margin-bottom: 15px;
                    padding: 12px 16px;
                    border-radius: 8px;
                    background: #21262d;
                    border: 1px solid #30363d;
                }

                .category-label {
                    font-size: 1.2em;
                    font-weight: bold;
                    color: #f0f6fc;
                }

                .category-desc {
                    font-size: 0.85em;
                    color: #8b949e;
                    font-style: italic;
                }

                .category-count {
                    margin-left: auto;
                    padding: 4px 12px;
                    border-radius: 12px;
                    font-size: 0.85em;
                    font-weight: bold;
                    color: white;
                }

                .difference-location {
                    color: #8b949e;
                    font-size: 0.9em;
                }

                .difference-details {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 20px;
                    margin-top: 15px;
                }

                .value-box {
                    padding: 15px;
                    border-radius: 6px;
                    font-family: 'Courier New', monospace;
                }

                .expected-value {
                    background: #1b4332;
                    border: 1px solid #238636;
                    color: #7ee787;
                }

                .actual-value {
                    background: #3d1418;
                    border: 1px solid #da3633;
                    color: #ffa198;
                }

                .value-label {
                    font-size: 0.85em;
                    font-weight: bold;
                    margin-bottom: 8px;
                    text-transform: uppercase;
                    color: #8b949e;
                }

                .value-content {
                    font-size: 1.1em;
                    word-break: break-all;
                }

                .success-message {
                    padding: 50px 30px;
                    text-align: center;
                }

                .success-icon {
                    font-size: 4em;
                    color: #238636;
                    margin-bottom: 20px;
                }

                .success-message h2 {
                    color: #7ee787;
                    margin-bottom: 10px;
                }

                .success-message p {
                    color: #8b949e;
                }

                .footer {
                    background: #21262d;
                    padding: 20px 30px;
                    text-align: center;
                    color: #8b949e;
                    font-size: 0.9em;
                    border-top: 1px solid #30363d;
                }

                /* Combined report specific styles */
                .dashboard {
                    padding: 30px;
                    background: #21262d;
                    border-bottom: 1px solid #30363d;
                }

                .scenario-index {
                    padding: 30px;
                    border-bottom: 1px solid #30363d;
                }

                .scenario-index h2 {
                    color: #f0f6fc;
                    margin-bottom: 20px;
                    padding-bottom: 10px;
                    border-bottom: 2px solid #58a6ff;
                }

                .index-table {
                    width: 100%;
                    border-collapse: collapse;
                    background: #0d1117;
                    border-radius: 8px;
                    overflow: hidden;
                    border: 1px solid #30363d;
                }

                .index-table th {
                    background: #21262d;
                    color: #f0f6fc;
                    padding: 12px 16px;
                    text-align: left;
                    font-weight: 600;
                    border-bottom: 2px solid #30363d;
                }

                .index-table td {
                    padding: 10px 16px;
                    border-bottom: 1px solid #30363d;
                    color: #c9d1d9;
                }

                .index-table tr:hover td {
                    background: #161b22;
                    cursor: pointer;
                }

                .index-table tr:last-child td {
                    border-bottom: none;
                }

                .scenario-section {
                    border-bottom: 1px solid #30363d;
                }

                .scenario-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    padding: 20px 30px;
                    cursor: pointer;
                    background: #161b22;
                    border-bottom: 1px solid #30363d;
                    user-select: none;
                }

                .scenario-header:hover {
                    background: #1c2129;
                }

                .scenario-header h3 {
                    color: #f0f6fc;
                    font-size: 1.3em;
                }

                .scenario-toggle {
                    color: #8b949e;
                    font-size: 1.2em;
                    transition: transform 0.2s;
                }

                .scenario-toggle.collapsed {
                    transform: rotate(-90deg);
                }

                .scenario-content {
                    overflow: hidden;
                    transition: max-height 0.3s ease;
                }

                .scenario-content.collapsed {
                    max-height: 0 !important;
                }

                @media (max-width: 768px) {
                    .difference-details {
                        grid-template-columns: 1fr;
                    }

                    .index-table {
                        font-size: 0.85em;
                    }
                }
            </style>
            """;
    }

    /**
     * Generates the combined report header.
     */
    private String generateCombinedHeader(List<ScenarioResult> scenarios, String timestamp) {
        long passed = scenarios.stream().filter(ScenarioResult::passed).count();
        long failed = scenarios.size() - passed;

        return String.format("""
            <div class="container">
                <div class="header">
                    <h1>EDI Combined Comparison Report</h1>
                    <div class="timestamp">Generated: %s | %d scenario(s) | %d passed | %d failed</div>
                </div>
            """, timestamp, scenarios.size(), passed, failed);
    }

    /**
     * Generates the dashboard with aggregate statistics.
     */
    private String generateDashboard(List<ScenarioResult> scenarios) {
        long totalScenarios = scenarios.size();
        long passed = scenarios.stream().filter(ScenarioResult::passed).count();
        long failed = totalScenarios - passed;
        int totalDifferences = scenarios.stream().mapToInt(s -> s.result().getDifferenceCount()).sum();
        long totalTimeMs = scenarios.stream().mapToLong(s -> s.result().getComparisonTimeMs()).sum();

        StringBuilder html = new StringBuilder();
        html.append("    <div class=\"dashboard\">\n");
        html.append("        <div class=\"stats\">\n");
        html.append(generateStatCard("Total Scenarios", String.valueOf(totalScenarios)));
        html.append(generateStatCard("Passed", String.valueOf(passed)));
        html.append(generateStatCard("Failed", String.valueOf(failed)));
        html.append(generateStatCard("Total Differences", String.valueOf(totalDifferences)));
        html.append(generateStatCard("Total Time", totalTimeMs + " ms"));
        html.append("        </div>\n");
        html.append("    </div>\n");
        return html.toString();
    }

    /**
     * Generates the scenario index table.
     */
    private String generateScenarioIndex(List<ScenarioResult> scenarios) {
        StringBuilder html = new StringBuilder();
        html.append("    <div class=\"scenario-index\">\n");
        html.append("        <h2>Scenario Index</h2>\n");
        html.append("        <table class=\"index-table\">\n");
        html.append("            <thead>\n");
        html.append("                <tr>\n");
        html.append("                    <th>#</th>\n");
        html.append("                    <th>Scenario</th>\n");
        html.append("                    <th>Status</th>\n");
        html.append("                    <th>Differences</th>\n");
        html.append("                    <th>Segments</th>\n");
        html.append("                    <th>Fields</th>\n");
        html.append("                    <th>Time</th>\n");
        html.append("                </tr>\n");
        html.append("            </thead>\n");
        html.append("            <tbody>\n");

        IntStream.range(0, scenarios.size()).forEach(i -> {
            ScenarioResult s = scenarios.get(i);
            ComparisonResult r = s.result();
            String statusBadge = s.passed()
                    ? "<span class=\"status-badge status-success\" style=\"font-size:0.8em;padding:4px 10px;\">PASSED</span>"
                    : "<span class=\"status-badge status-failure\" style=\"font-size:0.8em;padding:4px 10px;\">FAILED</span>";

            html.append(String.format(
                    "                <tr onclick=\"scrollToScenario('scenario-%d')\">\n", i));
            html.append(String.format("                    <td>%d</td>\n", i + 1));
            html.append(String.format("                    <td>%s</td>\n", escapeHtml(s.scenarioName())));
            html.append(String.format("                    <td>%s</td>\n", statusBadge));
            html.append(String.format("                    <td>%d</td>\n", r.getDifferenceCount()));
            html.append(String.format("                    <td>%d</td>\n", r.getSegmentsCompared()));
            html.append(String.format("                    <td>%d</td>\n", r.getFieldsCompared()));
            html.append(String.format("                    <td>%d ms</td>\n", r.getComparisonTimeMs()));
            html.append("                </tr>\n");
        });

        html.append("            </tbody>\n");
        html.append("        </table>\n");
        html.append("    </div>\n");
        return html.toString();
    }

    /**
     * Generates all scenario sections (collapsible).
     */
    private String generateScenarioSections(List<ScenarioResult> scenarios) {
        StringBuilder html = new StringBuilder();

        IntStream.range(0, scenarios.size()).forEach(i -> {
            ScenarioResult s = scenarios.get(i);
            ComparisonResult r = s.result();
            boolean failed = !s.passed();
            String collapseClass = failed ? "" : " collapsed";
            String statusBadge = failed
                    ? "<span class=\"status-badge status-failure\" style=\"font-size:0.8em;padding:4px 10px;\">FAILED</span>"
                    : "<span class=\"status-badge status-success\" style=\"font-size:0.8em;padding:4px 10px;\">PASSED</span>";

            html.append(String.format("    <div class=\"scenario-section\" id=\"scenario-%d\">\n", i));

            // Clickable header
            html.append(String.format(
                    "        <div class=\"scenario-header\" onclick=\"toggleScenario('scenario-content-%d', 'toggle-%d')\">\n", i, i));
            html.append(String.format("            <h3>%s %s</h3>\n",
                    escapeHtml(s.scenarioName()), statusBadge));
            html.append(String.format(
                    "            <span class=\"scenario-toggle%s\" id=\"toggle-%d\">&#9660;</span>\n", collapseClass, i));
            html.append("        </div>\n");

            // Collapsible content
            html.append(String.format(
                    "        <div class=\"scenario-content%s\" id=\"scenario-content-%d\">\n", collapseClass, i));

            // Reuse existing summary
            html.append(generateSummary(r));

            // Reuse existing differences or success message
            if (r.hasDifferences()) {
                html.append(generateDifferences(r));
            } else {
                html.append(generateSuccessMessage());
            }

            html.append("        </div>\n");
            html.append("    </div>\n");
        });

        return html.toString();
    }

    /**
     * JavaScript for combined report interactivity.
     */
    private String getCombinedScript() {
        return """
            <script>
                function scrollToScenario(id) {
                    var el = document.getElementById(id);
                    if (el) {
                        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
                        // Expand if collapsed
                        var contentId = id.replace('scenario-', 'scenario-content-');
                        var toggleId = id.replace('scenario-', 'toggle-');
                        var content = document.getElementById(contentId);
                        var toggle = document.getElementById(toggleId);
                        if (content && content.classList.contains('collapsed')) {
                            content.classList.remove('collapsed');
                            if (toggle) toggle.classList.remove('collapsed');
                        }
                    }
                }

                function toggleScenario(contentId, toggleId) {
                    var content = document.getElementById(contentId);
                    var toggle = document.getElementById(toggleId);
                    if (content) {
                        content.classList.toggle('collapsed');
                    }
                    if (toggle) {
                        toggle.classList.toggle('collapsed');
                    }
                }
            </script>
            """;
    }

    /**
     * Escapes HTML special characters.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}