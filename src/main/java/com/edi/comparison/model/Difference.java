package com.edi.comparison.model;

/**
 * Represents a single difference found during comparison.
 *
 * <p>Contains all information needed to understand and report the difference:
 * <ul>
 *   <li>What was expected vs what was found</li>
 *   <li>Where it occurred (segment, field, line number)</li>
 *   <li>Why it's different (validation type that failed)</li>
 * </ul>
 */
public class Difference {

    private final String segmentTag;
    private final String fieldPosition;
    private final String fieldName;
    private final String expected;
    private final String actual;
    private final int lineNumber;
    private final DifferenceType type;
    private final String description;

    private Difference(Builder builder) {
        this.segmentTag = builder.segmentTag;
        this.fieldPosition = builder.fieldPosition;
        this.fieldName = builder.fieldName;
        this.expected = builder.expected;
        this.actual = builder.actual;
        this.lineNumber = builder.lineNumber;
        this.type = builder.type;
        this.description = builder.description;
    }

    /**
     * Gets the segment tag where difference occurred.
     *
     * @return segment tag (e.g., "BGM", "NAD")
     */
    public String getSegmentTag() {
        return segmentTag;
    }

    /**
     * Gets the field position where difference occurred.
     *
     * @return field position (e.g., "BGM.0001")
     */
    public String getFieldPosition() {
        return fieldPosition;
    }

    /**
     * Gets the human-readable field name.
     *
     * @return field name or null
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Gets the expected value.
     *
     * @return expected value
     */
    public String getExpected() {
        return expected;
    }

    /**
     * Gets the actual value found.
     *
     * @return actual value
     */
    public String getActual() {
        return actual;
    }

    /**
     * Gets the line number in the source file.
     *
     * @return line number (1-based) or 0 if not available
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Gets the type of difference.
     *
     * @return difference type
     */
    public DifferenceType getType() {
        return type;
    }

    /**
     * Gets the human-readable description of the difference.
     *
     * @return description
     */
    public String getDescription() {
        return description;
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
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(type).append("] ");
        if (segmentTag != null) {
            sb.append(segmentTag);
            if (fieldPosition != null) {
                sb.append(".").append(fieldPosition);
            }
            if (fieldName != null) {
                sb.append(" (").append(fieldName).append(")");
            }
        }
        if (lineNumber > 0) {
            sb.append(" at line ").append(lineNumber);
        }
        sb.append(": ");
        if (description != null) {
            sb.append(description);
        } else {
            sb.append("Expected '").append(expected).append("' but got '").append(actual).append("'");
        }
        return sb.toString();
    }

    /**
     * Types of differences that can be found.
     * Each type belongs to a persona-based failure category for intuitive reporting.
     */
    public enum DifferenceType {
        /** Field value mismatch */
        VALUE_MISMATCH(FailureCategory.THE_SHAPESHIFTER),

        /** Required field is missing */
        MISSING_FIELD(FailureCategory.THE_GHOSTED),

        /** Required segment is missing */
        MISSING_SEGMENT(FailureCategory.THE_GHOSTED),

        /** Unexpected segment found (not defined in template) */
        UNEXPECTED_SEGMENT(FailureCategory.THE_GATECRASHER),

        /** Pattern validation failed */
        PATTERN_MISMATCH(FailureCategory.THE_SHAPESHIFTER),

        /** Date format validation failed */
        DATE_FORMAT_INVALID(FailureCategory.THE_TIME_TRAVELER),

        /** Segment count mismatch */
        SEGMENT_COUNT_MISMATCH(FailureCategory.THE_CROWD_CONTROL),

        /** Segment order mismatch */
        SEGMENT_ORDER_MISMATCH(FailureCategory.THE_QUEUE_JUMPER),

        /** Custom validation failed */
        CUSTOM_VALIDATION_FAILED(FailureCategory.THE_REBEL);

        private final FailureCategory category;

        DifferenceType(FailureCategory category) {
            this.category = category;
        }

        public FailureCategory getCategory() {
            return category;
        }
    }

    /**
     * Persona-based failure categories for grouping and reporting differences.
     * Each persona represents a class of EDI validation failure in a memorable way.
     */
    public enum FailureCategory {
        THE_GHOSTED("The Ghosted", "Expected data that never showed up", "#8b5cf6"),
        THE_GATECRASHER("The Gatecrasher", "Uninvited data that appeared without invitation", "#f97316"),
        THE_SHAPESHIFTER("The Shapeshifter", "Data that changed form from what was expected", "#ef4444"),
        THE_TIME_TRAVELER("The Time Traveler", "Timestamps and dates that lost their way", "#3b82f6"),
        THE_CROWD_CONTROL("The Crowd Control", "Segments that multiplied or vanished unexpectedly", "#eab308"),
        THE_QUEUE_JUMPER("The Queue Jumper", "Segments that cut in line", "#14b8a6"),
        THE_REBEL("The Rebel", "Custom rules that were broken", "#6b7280");

        private final String label;
        private final String description;
        private final String accentColor;

        FailureCategory(String label, String description, String accentColor) {
            this.label = label;
            this.description = description;
            this.accentColor = accentColor;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }

        public String getAccentColor() {
            return accentColor;
        }
    }

    /**
     * Builder for creating Difference instances.
     */
    public static class Builder {
        private String segmentTag;
        private String fieldPosition;
        private String fieldName;
        private String expected;
        private String actual;
        private int lineNumber;
        private DifferenceType type = DifferenceType.VALUE_MISMATCH;
        private String description;

        private Builder() {}

        public Builder segmentTag(String segmentTag) {
            this.segmentTag = segmentTag;
            return this;
        }

        public Builder fieldPosition(String fieldPosition) {
            this.fieldPosition = fieldPosition;
            return this;
        }

        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder expected(String expected) {
            this.expected = expected;
            return this;
        }

        public Builder actual(String actual) {
            this.actual = actual;
            return this;
        }

        public Builder lineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public Builder type(DifferenceType type) {
            this.type = type;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Difference build() {
            return new Difference(this);
        }
    }
}