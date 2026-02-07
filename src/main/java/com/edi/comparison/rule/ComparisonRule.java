package com.edi.comparison.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a rule for comparing segments in EDI/XML messages.
 * 
 * <p>A rule defines:
 * <ul>
 *   <li>Which segment to compare (tag)</li>
 *   <li>Which fields to validate</li>
 *   <li>Whether segment order matters</li>
 *   <li>Whether multiple occurrences are expected</li>
 * </ul>
 * 
 * <p>Example YAML representation:
 * <pre>
 * - segment: BGM
 *   fields:
 *     - position: BGM.C002.1001
 *       validation: exact_match
 *       source: testData.bgmCode
 * </pre>
 */
public class ComparisonRule {
    
    private String segment;
    private List<FieldRule> fields;
    private boolean orderMatters;
    private boolean multipleOccurrences;
    private boolean required;
    
    public ComparisonRule() {
        this.fields = new ArrayList<>();
        this.orderMatters = false;
        this.multipleOccurrences = false;
        this.required = true;
    }
    
    /**
     * Gets the segment tag this rule applies to.
     * 
     * @return segment tag (e.g., "BGM", "NAD")
     */
    public String getSegment() {
        return segment;
    }
    
    public void setSegment(String segment) {
        this.segment = segment;
    }
    
    /**
     * Gets the field validation rules for this segment.
     * 
     * @return list of field rules
     */
    public List<FieldRule> getFields() {
        return fields;
    }
    
    public void setFields(List<FieldRule> fields) {
        this.fields = fields != null ? fields : new ArrayList<>();
    }
    
    /**
     * Checks if segment order matters for comparison.
     * If true, segments must appear in the same sequence.
     * 
     * @return true if order matters
     */
    public boolean isOrderMatters() {
        return orderMatters;
    }
    
    public void setOrderMatters(boolean orderMatters) {
        this.orderMatters = orderMatters;
    }
    
    /**
     * Checks if multiple occurrences of this segment are expected.
     * If true, all occurrences will be validated.
     * 
     * @return true if multiple occurrences expected
     */
    public boolean isMultipleOccurrences() {
        return multipleOccurrences;
    }
    
    public void setMultipleOccurrences(boolean multipleOccurrences) {
        this.multipleOccurrences = multipleOccurrences;
    }
    
    /**
     * Checks if this segment is required to exist.
     * 
     * @return true if segment must exist
     */
    public boolean isRequired() {
        return required;
    }
    
    public void setRequired(boolean required) {
        this.required = required;
    }
    
    /**
     * Adds a field rule to this segment rule.
     * 
     * @param fieldRule field rule to add
     */
    public void addFieldRule(FieldRule fieldRule) {
        if (fieldRule != null) {
            this.fields.add(fieldRule);
        }
    }
    
    @Override
    public String toString() {
        return "ComparisonRule{" +
                "segment='" + segment + '\'' +
                ", fields=" + fields.size() +
                ", orderMatters=" + orderMatters +
                ", multiple=" + multipleOccurrences +
                ", required=" + required +
                '}';
    }
}
