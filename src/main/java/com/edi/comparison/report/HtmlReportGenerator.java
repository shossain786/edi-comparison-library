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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     * Generates CSS styles.
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
                    background: #f5f5f5;
                    padding: 20px;
                    line-height: 1.6;
                }
                
                .container {
                    max-width: 1200px;
                    margin: 0 auto;
                    background: white;
                    border-radius: 8px;
                    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                    overflow: hidden;
                }
                
                .header {
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
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
                    background: #f8f9fa;
                    border-bottom: 1px solid #dee2e6;
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
                    background: #28a745;
                    color: white;
                }
                
                .status-failure {
                    background: #dc3545;
                    color: white;
                }
                
                .stats {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 20px;
                    margin-top: 20px;
                }
                
                .stat-card {
                    background: white;
                    padding: 20px;
                    border-radius: 8px;
                    border-left: 4px solid #667eea;
                }
                
                .stat-label {
                    color: #6c757d;
                    font-size: 0.9em;
                    margin-bottom: 5px;
                }
                
                .stat-value {
                    font-size: 2em;
                    font-weight: bold;
                    color: #333;
                }
                
                .differences {
                    padding: 30px;
                }
                
                .differences h2 {
                    color: #333;
                    margin-bottom: 20px;
                    padding-bottom: 10px;
                    border-bottom: 2px solid #667eea;
                }
                
                .difference-item {
                    background: #fff;
                    border: 1px solid #dee2e6;
                    border-radius: 8px;
                    padding: 20px;
                    margin-bottom: 15px;
                    border-left: 4px solid #dc3545;
                }
                
                .difference-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-bottom: 15px;
                }
                
                .difference-type {
                    background: #dc3545;
                    color: white;
                    padding: 4px 12px;
                    border-radius: 4px;
                    font-size: 0.85em;
                    font-weight: bold;
                }
                
                .difference-location {
                    color: #6c757d;
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
                    background: #d4edda;
                    border: 1px solid #c3e6cb;
                }
                
                .actual-value {
                    background: #f8d7da;
                    border: 1px solid #f5c6cb;
                }
                
                .value-label {
                    font-size: 0.85em;
                    font-weight: bold;
                    margin-bottom: 8px;
                    text-transform: uppercase;
                    color: #495057;
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
                    color: #28a745;
                    margin-bottom: 20px;
                }
                
                .success-message h2 {
                    color: #28a745;
                    margin-bottom: 10px;
                }
                
                .footer {
                    background: #f8f9fa;
                    padding: 20px 30px;
                    text-align: center;
                    color: #6c757d;
                    font-size: 0.9em;
                    border-top: 1px solid #dee2e6;
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
     * Generates differences section.
     */
    private String generateDifferences(ComparisonResult result) {
        StringBuilder html = new StringBuilder();
        html.append("    <div class=\"differences\">\n");
        html.append("        <h2>Differences Details</h2>\n");

        // Group by type
        Map<Difference.DifferenceType, List<Difference>> byType =
                result.getDifferences().stream()
                        .collect(Collectors.groupingBy(Difference::getType));

        int index = 1;
        for (Map.Entry<Difference.DifferenceType, List<Difference>> entry : byType.entrySet()) {
            for (Difference diff : entry.getValue()) {
                html.append(generateDifferenceItem(diff, index++));
            }
        }

        html.append("    </div>\n");
        return html.toString();
    }

    /**
     * Generates a single difference item.
     */
    private String generateDifferenceItem(Difference diff, int index) {
        StringBuilder html = new StringBuilder();

        html.append("        <div class=\"difference-item\">\n");
        html.append("            <div class=\"difference-header\">\n");
        html.append(String.format("                <strong>#%d</strong>\n", index));
        html.append(String.format("                <span class=\"difference-type\">%s</span>\n",
                diff.getType()));
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