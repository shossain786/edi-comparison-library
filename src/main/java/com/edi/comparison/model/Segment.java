package com.edi.comparison.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a segment in an EDI or XML message.
 * 
 * <p>A segment is a logical grouping of related fields. In EDI formats:
 * <ul>
 *   <li>EDIFACT: BGM, NAD, DTM, etc.</li>
 *   <li>ANSI X12: ISA, GS, ST, etc.</li>
 *   <li>XML: Represented as an element with child elements as fields</li>
 * </ul>
 * 
 * <p>This class is immutable and thread-safe.
 * 
 * <p><b>Example:</b>
 * <pre>
 * // Create a BGM segment
 * Segment bgm = Segment.builder()
 *     .tag("BGM")
 *     .addField(Field.builder()
 *         .position("BGM.C002.1001")
 *         .value("340")
 *         .name("documentCode")
 *         .build())
 *     .addField(Field.builder()
 *         .position("BGM.1004")
 *         .value("BOOKING123")
 *         .name("documentNumber")
 *         .build())
 *     .lineNumber(3)
 *     .build();
 * </pre>
 */
public final class Segment {
    
    private final String tag;
    private final List<Field> fields;
    private final int lineNumber;
    private final int sequenceNumber;
    private final String rawContent;
    
    private Segment(Builder builder) {
        this.tag = builder.tag;
        this.fields = Collections.unmodifiableList(new ArrayList<>(builder.fields));
        this.lineNumber = builder.lineNumber;
        this.sequenceNumber = builder.sequenceNumber;
        this.rawContent = builder.rawContent;
    }
    
    /**
     * Gets the segment tag/identifier.
     * 
     * @return segment tag (e.g., "BGM", "NAD", "DTM")
     */
    public String getTag() {
        return tag;
    }
    
    /**
     * Gets all fields in this segment.
     * 
     * @return unmodifiable list of fields
     */
    public List<Field> getFields() {
        return fields;
    }
    
    /**
     * Gets the line number where this segment appears in the source file.
     * 
     * @return line number (1-based) or 0 if not set
     */
    public int getLineNumber() {
        return lineNumber;
    }
    
    /**
     * Gets the sequence number of this segment within the message.
     * Useful for tracking segment order.
     * 
     * @return sequence number (0-based) or -1 if not set
     */
    public int getSequenceNumber() {
        return sequenceNumber;
    }
    
    /**
     * Gets the raw, unparsed content of this segment.
     * Useful for debugging or displaying original format.
     * 
     * @return raw segment content or null if not available
     */
    public String getRawContent() {
        return rawContent;
    }
    
    /**
     * Finds a field by its position identifier.
     * 
     * @param position field position (e.g., "BGM.C002.1001")
     * @return Optional containing the field if found, empty otherwise
     */
    public Optional<Field> getFieldByPosition(String position) {
        return fields.stream()
            .filter(f -> f.getPosition().equals(position))
            .findFirst();
    }
    
    /**
     * Finds a field by its name.
     * 
     * @param name field name
     * @return Optional containing the field if found, empty otherwise
     */
    public Optional<Field> getFieldByName(String name) {
        if (name == null) return Optional.empty();
        return fields.stream()
            .filter(f -> name.equals(f.getName()))
            .findFirst();
    }
    
    /**
     * Gets the value of a field by position.
     * 
     * @param position field position
     * @return field value or null if not found
     */
    public String getFieldValue(String position) {
        return getFieldByPosition(position)
            .map(Field::getValue)
            .orElse(null);
    }
    
    /**
     * Checks if this segment contains a field at the given position.
     * 
     * @param position field position to check
     * @return true if field exists
     */
    public boolean hasField(String position) {
        return getFieldByPosition(position).isPresent();
    }
    
    /**
     * Gets the number of fields in this segment.
     * 
     * @return field count
     */
    public int getFieldCount() {
        return fields.size();
    }
    
    /**
     * Creates a new builder for constructing Segment instances.
     * 
     * @return new Segment.Builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a builder initialized with values from this segment.
     * 
     * @return new Segment.Builder with current segment's values
     */
    public Builder toBuilder() {
        Builder builder = new Builder()
            .tag(this.tag)
            .lineNumber(this.lineNumber)
            .sequenceNumber(this.sequenceNumber)
            .rawContent(this.rawContent);
        
        this.fields.forEach(builder::addField);
        return builder;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Segment segment = (Segment) o;
        return Objects.equals(tag, segment.tag) &&
               Objects.equals(fields, segment.fields);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(tag, fields);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Segment{");
        sb.append("tag='").append(tag).append('\'');
        sb.append(", fields=").append(fields.size());
        if (lineNumber > 0) {
            sb.append(", line=").append(lineNumber);
        }
        if (sequenceNumber >= 0) {
            sb.append(", seq=").append(sequenceNumber);
        }
        sb.append('}');
        return sb.toString();
    }
    
    /**
     * Returns a detailed string representation including all fields.
     * 
     * @return detailed string representation
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder("Segment{tag='").append(tag).append("'");
        if (lineNumber > 0) {
            sb.append(", line=").append(lineNumber);
        }
        sb.append(", fields=[\n");
        for (Field field : fields) {
            sb.append("  ").append(field.toString()).append("\n");
        }
        sb.append("]}");
        return sb.toString();
    }
    
    /**
     * Builder for creating immutable Segment instances.
     */
    public static class Builder {
        private String tag;
        private final List<Field> fields = new ArrayList<>();
        private int lineNumber;
        private int sequenceNumber = -1;
        private String rawContent;
        
        private Builder() {}
        
        /**
         * Sets the segment tag/identifier.
         * 
         * @param tag segment tag (required)
         * @return this builder
         */
        public Builder tag(String tag) {
            this.tag = tag;
            return this;
        }
        
        /**
         * Adds a field to this segment.
         * 
         * @param field field to add
         * @return this builder
         */
        public Builder addField(Field field) {
            if (field != null) {
                this.fields.add(field);
            }
            return this;
        }
        
        /**
         * Adds multiple fields to this segment.
         * 
         * @param fields fields to add
         * @return this builder
         */
        public Builder addFields(List<Field> fields) {
            if (fields != null) {
                this.fields.addAll(fields);
            }
            return this;
        }
        
        /**
         * Sets the line number where this segment appears.
         * 
         * @param lineNumber line number (1-based)
         * @return this builder
         */
        public Builder lineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }
        
        /**
         * Sets the sequence number of this segment.
         * 
         * @param sequenceNumber sequence number (0-based)
         * @return this builder
         */
        public Builder sequenceNumber(int sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }
        
        /**
         * Sets the raw content of this segment.
         * 
         * @param rawContent original segment string
         * @return this builder
         */
        public Builder rawContent(String rawContent) {
            this.rawContent = rawContent;
            return this;
        }
        
        /**
         * Builds the immutable Segment instance.
         * 
         * @return new Segment
         * @throws IllegalStateException if tag is not set
         */
        public Segment build() {
            if (tag == null || tag.trim().isEmpty()) {
                throw new IllegalStateException("Segment tag is required");
            }
            return new Segment(this);
        }
    }
}
