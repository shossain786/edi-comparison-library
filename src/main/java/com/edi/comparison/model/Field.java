package com.edi.comparison.model;

import java.util.Objects;

/**
 * Represents a single field within a segment.
 * 
 * <p>A field is the smallest unit of data in EDI/XML files. It contains:
 * <ul>
 *   <li>Position - Location within the segment (e.g., "BGM.C002.1001")</li>
 *   <li>Value - The actual data value</li>
 *   <li>Name - Optional human-readable name (e.g., "documentCode")</li>
 * </ul>
 * 
 * <p>This class is immutable for thread-safety and consistency.
 * 
 * <p><b>Examples:</b>
 * <pre>
 * // Simple field
 * Field bgmCode = Field.builder()
 *     .position("BGM.C002.1001")
 *     .value("340")
 *     .name("documentCode")
 *     .build();
 * 
 * // Field with composite position
 * Field dateTime = Field.builder()
 *     .position("DTM.C507.2380")
 *     .value("20240207")
 *     .name("dateTime")
 *     .build();
 * </pre>
 */
public final class Field {
    
    private final String position;
    private final String value;
    private final String name;
    private final int lineNumber;
    
    private Field(Builder builder) {
        this.position = builder.position;
        this.value = builder.value;
        this.name = builder.name;
        this.lineNumber = builder.lineNumber;
    }
    
    /**
     * Gets the position/path of this field within the segment.
     * 
     * <p>Format examples:
     * <ul>
     *   <li>Simple: "BGM.1001" (segment.element)</li>
     *   <li>Composite: "BGM.C002.1001" (segment.composite.element)</li>
     *   <li>XML: "Booking/Container/Number" (XPath-like)</li>
     * </ul>
     * 
     * @return field position identifier
     */
    public String getPosition() {
        return position;
    }
    
    /**
     * Gets the actual value of this field.
     * 
     * @return field value (may be null or empty)
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Gets the human-readable name of this field.
     * 
     * @return field name or null if not set
     */
    public String getName() {
        return name;
    }
    
    /**
     * Gets the line number where this field appears in the source file.
     * 
     * @return line number (1-based) or 0 if not set
     */
    public int getLineNumber() {
        return lineNumber;
    }
    
    /**
     * Checks if this field has a non-empty value.
     * 
     * @return true if value is not null and not empty
     */
    public boolean hasValue() {
        return value != null && !value.trim().isEmpty();
    }
    
    /**
     * Creates a new builder for constructing Field instances.
     * 
     * @return new Field.Builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a builder initialized with values from this field.
     * Useful for creating modified copies.
     * 
     * @return new Field.Builder with current field's values
     */
    public Builder toBuilder() {
        return new Builder()
            .position(this.position)
            .value(this.value)
            .name(this.name)
            .lineNumber(this.lineNumber);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Field field = (Field) o;
        return Objects.equals(position, field.position) &&
               Objects.equals(value, field.value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(position, value);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Field{");
        if (name != null) {
            sb.append("name='").append(name).append("', ");
        }
        sb.append("position='").append(position).append('\'');
        sb.append(", value='").append(value).append('\'');
        if (lineNumber > 0) {
            sb.append(", line=").append(lineNumber);
        }
        sb.append('}');
        return sb.toString();
    }
    
    /**
     * Builder for creating immutable Field instances.
     */
    public static class Builder {
        private String position;
        private String value;
        private String name;
        private int lineNumber;
        
        private Builder() {}
        
        /**
         * Sets the position identifier for this field.
         * 
         * @param position field position (required)
         * @return this builder
         */
        public Builder position(String position) {
            this.position = position;
            return this;
        }
        
        /**
         * Sets the value for this field.
         * 
         * @param value field value
         * @return this builder
         */
        public Builder value(String value) {
            this.value = value;
            return this;
        }
        
        /**
         * Sets the human-readable name for this field.
         * 
         * @param name field name (optional)
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        /**
         * Sets the line number where this field appears.
         * 
         * @param lineNumber line number (1-based)
         * @return this builder
         */
        public Builder lineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }
        
        /**
         * Builds the immutable Field instance.
         * 
         * @return new Field
         * @throws IllegalStateException if position is not set
         */
        public Field build() {
            if (position == null || position.trim().isEmpty()) {
                throw new IllegalStateException("Field position is required");
            }
            return new Field(this);
        }
    }
}
