package com.edi.comparison.core;

import com.edi.comparison.model.*;
import com.edi.comparison.rule.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Engine that compares two messages based on rules.
 *
 * <p>This is the core comparison logic that:
 * <ul>
 *   <li>Applies comparison rules to messages</li>
 *   <li>Validates field values using different validators</li>
 *   <li>Collects all differences (doesn't fail fast)</li>
 *   <li>Handles multiple segment occurrences</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * ComparisonContext context = ComparisonContext.builder()
 *     .testData(testDataMap)
 *     .inboundMessage(inbound)
 *     .build();
 *
 * ComparisonEngine engine = new ComparisonEngine(ruleSet, context);
 * ComparisonResult result = engine.compare(inbound, outbound);
 * </pre>
 */
public class ComparisonEngine {

    private final RuleSet ruleSet;
    private final ComparisonContext context;

    public ComparisonEngine(RuleSet ruleSet, ComparisonContext context) {
        if (ruleSet == null) {
            throw new IllegalArgumentException("RuleSet cannot be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("ComparisonContext cannot be null");
        }
        this.ruleSet = ruleSet;
        this.context = context;
    }

    /**
     * Compares expected message with actual message using loaded rules.
     *
     * @param expected expected message (can be null for outbound-only validation)
     * @param actual actual message to validate
     * @return comparison result
     */
    public ComparisonResult compare(Message expected, Message actual) {
        long startTime = System.currentTimeMillis();

        ComparisonResult.Builder resultBuilder = ComparisonResult.builder()
                .expectedMessage(expected)
                .actualMessage(actual);

        int segmentsCompared = 0;
        int fieldsCompared = 0;

        // Apply each rule
        for (ComparisonRule rule : ruleSet.getRules()) {
            List<Segment> actualSegments = actual.getSegmentsByTag(rule.getSegment());

            // Check if required segment exists
            if (rule.isRequired() && actualSegments.isEmpty()) {
                resultBuilder.addDifference(Difference.builder()
                        .segmentTag(rule.getSegment())
                        .type(Difference.DifferenceType.MISSING_SEGMENT)
                        .description("Required segment " + rule.getSegment() + " is missing")
                        .build());
                continue;
            }

            // Check multiple occurrences handling
            if (!rule.isMultipleOccurrences() && actualSegments.size() > 1) {
                resultBuilder.addDifference(Difference.builder()
                        .segmentTag(rule.getSegment())
                        .type(Difference.DifferenceType.SEGMENT_COUNT_MISMATCH)
                        .expected("1")
                        .actual(String.valueOf(actualSegments.size()))
                        .description("Segment " + rule.getSegment() + " should appear only once but found " + actualSegments.size())
                        .build());
            }

            // Compare each segment occurrence
            for (Segment actualSegment : actualSegments) {
                segmentsCompared++;
                List<Difference> segmentDiffs = compareSegment(rule, actualSegment);
                resultBuilder.addDifferences(segmentDiffs);
                fieldsCompared += rule.getFields().size();
            }
        }

        long endTime = System.currentTimeMillis();

        return resultBuilder
                .comparisonTimeMs(endTime - startTime)
                .segmentsCompared(segmentsCompared)
                .fieldsCompared(fieldsCompared)
                .build();
    }

    /**
     * Compares a single segment against its rule.
     */
    private List<Difference> compareSegment(ComparisonRule rule, Segment actualSegment) {
        List<Difference> differences = new ArrayList<>();

        for (FieldRule fieldRule : rule.getFields()) {
            Optional<Field> actualField = actualSegment.getFieldByPosition(fieldRule.getPosition());

            // Check if required field exists
            if (fieldRule.isRequired() && !actualField.isPresent()) {
                differences.add(Difference.builder()
                        .segmentTag(rule.getSegment())
                        .fieldPosition(fieldRule.getPosition())
                        .fieldName(fieldRule.getName())
                        .type(Difference.DifferenceType.MISSING_FIELD)
                        .description("Required field " + fieldRule.getPosition() + " is missing")
                        .lineNumber(actualSegment.getLineNumber())
                        .build());
                continue;
            }

            if (!actualField.isPresent()) {
                continue; // Optional field not present, skip
            }

            String actualValue = actualField.get().getValue();

            // Validate based on validation type
            Difference diff = validateField(fieldRule, actualValue, actualSegment, rule.getSegment());
            if (diff != null) {
                differences.add(diff);
            }
        }

        return differences;
    }

    /**
     * Validates a field value based on its validation type.
     */
    private Difference validateField(FieldRule fieldRule, String actualValue,
                                     Segment actualSegment, String segmentTag) {
        String expectedValue = getExpectedValue(fieldRule);

        switch (fieldRule.getValidation()) {
            case EXACT_MATCH:
                return validateExactMatch(fieldRule, expectedValue, actualValue, actualSegment, segmentTag);

            case PATTERN_MATCH:
                return validatePattern(fieldRule, actualValue, actualSegment, segmentTag);

            case DATE_FORMAT:
                return validateDateFormat(fieldRule, actualValue, actualSegment, segmentTag);

            case EXISTS:
                // Field exists, validation passed
                return null;

            case CUSTOM:
                // Custom validation will be implemented in Step 7
                return null;

            default:
                return null;
        }
    }

    /**
     * Gets the expected value for a field rule.
     */
    private String getExpectedValue(FieldRule fieldRule) {
        // Check if there's a literal expected value
        if (fieldRule.getExpectedValue() != null) {
            return fieldRule.getExpectedValue();
        }

        // Check if there's a source reference to resolve
        if (fieldRule.getSource() != null) {
            return context.resolveSource(fieldRule.getSource());
        }

        return null;
    }

    /**
     * Validates exact match.
     */
    private Difference validateExactMatch(FieldRule fieldRule, String expected, String actual,
                                          Segment segment, String segmentTag) {
        if (expected == null) {
            return null; // No expected value to compare
        }

        boolean caseSensitive = context.getConfigBoolean("case_sensitive", true);
        boolean ignoreWhitespace = context.getConfigBoolean("ignore_trailing_whitespace", false);

        String exp = expected;
        String act = actual;

        if (ignoreWhitespace) {
            exp = exp != null ? exp.trim() : "";
            act = act != null ? act.trim() : "";
        }

        boolean matches = caseSensitive ?
                exp.equals(act) :
                exp.equalsIgnoreCase(act);

        if (!matches) {
            return Difference.builder()
                    .segmentTag(segmentTag)
                    .fieldPosition(fieldRule.getPosition())
                    .fieldName(fieldRule.getName())
                    .expected(expected)
                    .actual(actual)
                    .type(Difference.DifferenceType.VALUE_MISMATCH)
                    .lineNumber(segment.getLineNumber())
                    .build();
        }

        return null;
    }

    /**
     * Validates pattern match.
     */
    private Difference validatePattern(FieldRule fieldRule, String actual,
                                       Segment segment, String segmentTag) {
        if (fieldRule.getPattern() == null) {
            return null; // No pattern to validate
        }

        try {
            Pattern pattern = Pattern.compile(fieldRule.getPattern());
            if (!pattern.matcher(actual != null ? actual : "").matches()) {
                return Difference.builder()
                        .segmentTag(segmentTag)
                        .fieldPosition(fieldRule.getPosition())
                        .fieldName(fieldRule.getName())
                        .expected("Pattern: " + fieldRule.getPattern())
                        .actual(actual)
                        .type(Difference.DifferenceType.PATTERN_MISMATCH)
                        .lineNumber(segment.getLineNumber())
                        .description("Value does not match pattern " + fieldRule.getPattern())
                        .build();
            }
        } catch (Exception e) {
            return Difference.builder()
                    .segmentTag(segmentTag)
                    .fieldPosition(fieldRule.getPosition())
                    .type(Difference.DifferenceType.PATTERN_MISMATCH)
                    .description("Invalid pattern: " + e.getMessage())
                    .build();
        }

        return null;
    }

    /**
     * Validates date format.
     * For DTM segments, checks date based on format code (102, 103, etc.)
     */
    private Difference validateDateFormat(FieldRule fieldRule, String actual,
                                          Segment segment, String segmentTag) {
        if (actual == null || actual.isEmpty()) {
            return null;
        }

        // Get format code if specified
        String formatCode = null;
        if (fieldRule.getDateFormatField() != null) {
            formatCode = segment.getFieldValue(fieldRule.getDateFormatField());
        }

        // Basic date format validation
        // Format 102: CCYYMMDD (8 digits)
        // Format 103: CCYYMMDDHHMM (12 digits)
        if ("102".equals(formatCode)) {
            if (!actual.matches("\\d{8}")) {
                return Difference.builder()
                        .segmentTag(segmentTag)
                        .fieldPosition(fieldRule.getPosition())
                        .fieldName(fieldRule.getName())
                        .expected("CCYYMMDD format (8 digits)")
                        .actual(actual)
                        .type(Difference.DifferenceType.DATE_FORMAT_INVALID)
                        .lineNumber(segment.getLineNumber())
                        .build();
            }
        } else if ("103".equals(formatCode)) {
            if (!actual.matches("\\d{12}")) {
                return Difference.builder()
                        .segmentTag(segmentTag)
                        .fieldPosition(fieldRule.getPosition())
                        .fieldName(fieldRule.getName())
                        .expected("CCYYMMDDHHMM format (12 digits)")
                        .actual(actual)
                        .type(Difference.DifferenceType.DATE_FORMAT_INVALID)
                        .lineNumber(segment.getLineNumber())
                        .build();
            }
        }

        return null;
    }
}