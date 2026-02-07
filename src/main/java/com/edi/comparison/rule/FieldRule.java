package com.edi.comparison.rule;

/**
 * Represents a validation rule for a single field within a segment.
 * 
 * <p>A field rule defines:
 * <ul>
 *   <li>Field position (e.g., "BGM.C002.1001")</li>
 *   <li>Validation type (exact_match, pattern, date_format, custom)</li>
 *   <li>Expected value source (testData, literal, or field reference)</li>
 *   <li>Additional validation parameters</li>
 * </ul>
 * 
 * <p>Example YAML:
 * <pre>
 * - position: BGM.C002.1001
 *   validation: exact_match
 *   source: testData.bgmCode
 *   name: documentCode
 * </pre>
 */
public class FieldRule {
    
    private String position;
    private String name;
    private ValidationType validation;
    private String source;
    private String expectedValue;
    private String pattern;
    private String dateFormatField;
    private String customValidator;
    private boolean required;
    
    public FieldRule() {
        this.validation = ValidationType.EXACT_MATCH;
        this.required = true;
    }
    
    /**
     * Gets the field position identifier.
     * 
     * @return field position (e.g., "BGM.C002.1001")
     */
    public String getPosition() {
        return position;
    }
    
    public void setPosition(String position) {
        this.position = position;
    }
    
    /**
     * Gets the human-readable field name.
     * 
     * @return field name
     */
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * Gets the validation type to apply.
     * 
     * @return validation type
     */
    public ValidationType getValidation() {
        return validation;
    }
    
    public void setValidation(ValidationType validation) {
        this.validation = validation;
    }
    
    /**
     * Convenience method to set validation type from string.
     * 
     * @param validation validation type string
     */
    public void setValidation(String validation) {
        if (validation != null) {
            this.validation = ValidationType.fromString(validation);
        }
    }
    
    /**
     * Gets the source for expected value.
     * Can be:
     * <ul>
     *   <li>"testData.key" - from test data context</li>
     *   <li>"inbound.BGM.0001" - from inbound message field</li>
     *   <li>null - use expectedValue</li>
     * </ul>
     * 
     * @return value source
     */
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    /**
     * Gets the literal expected value.
     * Used when source is not specified.
     * 
     * @return expected value
     */
    public String getExpectedValue() {
        return expectedValue;
    }
    
    public void setExpectedValue(String expectedValue) {
        this.expectedValue = expectedValue;
    }
    
    /**
     * Gets the regex pattern for pattern validation.
     * 
     * @return regex pattern
     */
    public String getPattern() {
        return pattern;
    }
    
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
    
    /**
     * Gets the field that contains date format code.
     * Used for DTM segments where format is in a separate field.
     * 
     * @return date format field position
     */
    public String getDateFormatField() {
        return dateFormatField;
    }
    
    public void setDateFormatField(String dateFormatField) {
        this.dateFormatField = dateFormatField;
    }
    
    /**
     * Gets the custom validator class name.
     * 
     * @return custom validator class name
     */
    public String getCustomValidator() {
        return customValidator;
    }
    
    public void setCustomValidator(String customValidator) {
        this.customValidator = customValidator;
    }
    
    /**
     * Checks if this field is required.
     * 
     * @return true if field must exist
     */
    public boolean isRequired() {
        return required;
    }
    
    public void setRequired(boolean required) {
        this.required = required;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FieldRule{");
        sb.append("position='").append(position).append('\'');
        if (name != null) {
            sb.append(", name='").append(name).append('\'');
        }
        sb.append(", validation=").append(validation);
        if (source != null) {
            sb.append(", source='").append(source).append('\'');
        }
        if (expectedValue != null) {
            sb.append(", expected='").append(expectedValue).append('\'');
        }
        sb.append(", required=").append(required);
        sb.append('}');
        return sb.toString();
    }
    
    /**
     * Validation types supported by the rule engine.
     */
    public enum ValidationType {
        /**
         * Exact string match.
         */
        EXACT_MATCH,
        
        /**
         * Regex pattern match.
         */
        PATTERN_MATCH,
        
        /**
         * Date format validation (handles DTM segments with format codes).
         */
        DATE_FORMAT,
        
        /**
         * Custom validator class.
         */
        CUSTOM,
        
        /**
         * Field must exist but value is not validated.
         */
        EXISTS;
        
        /**
         * Converts string to ValidationType.
         * 
         * @param value string value
         * @return ValidationType
         */
        public static ValidationType fromString(String value) {
            if (value == null) return EXACT_MATCH;
            
            switch (value.toLowerCase().replace("_", "")) {
                case "exactmatch":
                case "exact":
                    return EXACT_MATCH;
                case "patternmatch":
                case "pattern":
                case "regex":
                    return PATTERN_MATCH;
                case "dateformat":
                case "date":
                    return DATE_FORMAT;
                case "custom":
                    return CUSTOM;
                case "exists":
                    return EXISTS;
                default:
                    return EXACT_MATCH;
            }
        }
    }
}
