package com.edi.comparison.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Result of a message comparison operation.
 *
 * <p>Contains:
 * <ul>
 *   <li>List of all differences found</li>
 *   <li>Overall success/failure status</li>
 *   <li>Summary statistics</li>
 *   <li>References to compared messages</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * ComparisonResult result = engine.compare(inbound, outbound);
 *
 * if (result.hasDifferences()) {
 *     for (Difference diff : result.getDifferences()) {
 *         System.out.println(diff);
 *     }
 * }
 * </pre>
 */
public class ComparisonResult {

    private final List<Difference> differences;
    private final Message expectedMessage;
    private final Message actualMessage;
    private final long comparisonTimeMs;
    private final int segmentsCompared;
    private final int fieldsCompared;

    private ComparisonResult(Builder builder) {
        this.differences = Collections.unmodifiableList(new ArrayList<>(builder.differences));
        this.expectedMessage = builder.expectedMessage;
        this.actualMessage = builder.actualMessage;
        this.comparisonTimeMs = builder.comparisonTimeMs;
        this.segmentsCompared = builder.segmentsCompared;
        this.fieldsCompared = builder.fieldsCompared;
    }

    /**
     * Checks if any differences were found.
     *
     * @return true if differences exist
     */
    public boolean hasDifferences() {
        return !differences.isEmpty();
    }

    /**
     * Checks if comparison was successful (no differences).
     *
     * @return true if no differences found
     */
    public boolean isSuccess() {
        return differences.isEmpty();
    }

    /**
     * Gets all differences found.
     *
     * @return unmodifiable list of differences
     */
    public List<Difference> getDifferences() {
        return differences;
    }

    /**
     * Gets the number of differences found.
     *
     * @return difference count
     */
    public int getDifferenceCount() {
        return differences.size();
    }

    /**
     * Gets differences of a specific type.
     *
     * @param type difference type
     * @return list of matching differences
     */
    public List<Difference> getDifferencesOfType(Difference.DifferenceType type) {
        return differences.stream()
                .filter(d -> d.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Gets differences for a specific segment.
     *
     * @param segmentTag segment tag
     * @return list of differences in that segment
     */
    public List<Difference> getDifferencesForSegment(String segmentTag) {
        return differences.stream()
                .filter(d -> segmentTag.equals(d.getSegmentTag()))
                .collect(Collectors.toList());
    }

    /**
     * Gets the expected message that was compared.
     *
     * @return expected message or null
     */
    public Message getExpectedMessage() {
        return expectedMessage;
    }

    /**
     * Gets the actual message that was compared.
     *
     * @return actual message or null
     */
    public Message getActualMessage() {
        return actualMessage;
    }

    /**
     * Gets the time taken for comparison in milliseconds.
     *
     * @return comparison time in ms
     */
    public long getComparisonTimeMs() {
        return comparisonTimeMs;
    }

    /**
     * Gets the number of segments compared.
     *
     * @return segment count
     */
    public int getSegmentsCompared() {
        return segmentsCompared;
    }

    /**
     * Gets the number of fields compared.
     *
     * @return field count
     */
    public int getFieldsCompared() {
        return fieldsCompared;
    }

    /**
     * Gets a summary of the comparison result.
     *
     * @return summary string
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Comparison Result:\n");
        sb.append("  Status: ").append(isSuccess() ? "SUCCESS" : "FAILED").append("\n");
        sb.append("  Differences: ").append(getDifferenceCount()).append("\n");
        sb.append("  Segments compared: ").append(segmentsCompared).append("\n");
        sb.append("  Fields compared: ").append(fieldsCompared).append("\n");
        sb.append("  Time taken: ").append(comparisonTimeMs).append(" ms\n");

        if (hasDifferences()) {
            sb.append("\nDifferences by type:\n");
            Map<Difference.DifferenceType, Long> counts = differences.stream()
                    .collect(Collectors.groupingBy(Difference::getType, Collectors.counting()));
            counts.forEach((type, count) ->
                    sb.append("  ").append(type).append(": ").append(count).append("\n")
            );
        }

        return sb.toString();
    }

    /**
     * Gets a detailed report of all differences.
     *
     * @return detailed report string
     */
    public String getDetailedReport() {
        StringBuilder sb = new StringBuilder();
        sb.append(getSummary());

        if (hasDifferences()) {
            sb.append("\nDetailed differences:\n");
            for (int i = 0; i < differences.size(); i++) {
                sb.append(i + 1).append(". ").append(differences.get(i)).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Generates an HTML report and saves it to a file.
     *
     * @param outputPath path to save HTML report
     * @throws java.io.IOException if file cannot be written
     */
    public void generateHtmlReport(String outputPath) throws java.io.IOException {
        com.edi.comparison.report.HtmlReportGenerator generator =
                new com.edi.comparison.report.HtmlReportGenerator();
        generator.generate(this, outputPath);
    }

    /**
     * Generates an HTML report as a string.
     *
     * @return HTML report string
     */
    public String generateHtmlReport() {
        com.edi.comparison.report.HtmlReportGenerator generator =
                new com.edi.comparison.report.HtmlReportGenerator();
        return generator.generateHtml(this);
    }

    /**
     * Creates a new builder.
     *
     * @return new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "ComparisonResult{" +
                "differences=" + differences.size() +
                ", success=" + isSuccess() +
                ", time=" + comparisonTimeMs + "ms" +
                '}';
    }

    /**
     * Builder for creating ComparisonResult instances.
     */
    public static class Builder {
        private final List<Difference> differences = new ArrayList<>();
        private Message expectedMessage;
        private Message actualMessage;
        private long comparisonTimeMs;
        private int segmentsCompared;
        private int fieldsCompared;

        private Builder() {}

        public Builder addDifference(Difference difference) {
            if (difference != null) {
                this.differences.add(difference);
            }
            return this;
        }

        public Builder addDifferences(List<Difference> differences) {
            if (differences != null) {
                this.differences.addAll(differences);
            }
            return this;
        }

        public Builder expectedMessage(Message expectedMessage) {
            this.expectedMessage = expectedMessage;
            return this;
        }

        public Builder actualMessage(Message actualMessage) {
            this.actualMessage = actualMessage;
            return this;
        }

        public Builder comparisonTimeMs(long comparisonTimeMs) {
            this.comparisonTimeMs = comparisonTimeMs;
            return this;
        }

        public Builder segmentsCompared(int segmentsCompared) {
            this.segmentsCompared = segmentsCompared;
            return this;
        }

        public Builder fieldsCompared(int fieldsCompared) {
            this.fieldsCompared = fieldsCompared;
            return this;
        }

        public ComparisonResult build() {
            return new ComparisonResult(this);
        }
    }
}