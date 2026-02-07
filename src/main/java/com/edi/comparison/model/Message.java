package com.edi.comparison.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a complete EDI or XML message/file.
 * 
 * <p>A message is the top-level container that holds all segments.
 * It provides methods for querying and navigating the segment structure.
 * 
 * <p>This class is immutable and thread-safe.
 * 
 * <p><b>Example:</b>
 * <pre>
 * Message message = Message.builder()
 *     .fileFormat(FileFormat.EDIFACT)
 *     .messageType("IFTMBF")
 *     .addSegment(bgmSegment)
 *     .addSegment(nadSegment)
 *     .addSegment(dtmSegment)
 *     .build();
 * 
 * // Query segments
 * List&lt;Segment&gt; nadSegments = message.getSegmentsByTag("NAD");
 * Optional&lt;Segment&gt; bgm = message.getFirstSegmentByTag("BGM");
 * </pre>
 */
public final class Message {
    
    private final FileFormat fileFormat;
    private final String messageType;
    private final List<Segment> segments;
    private final String sourceFilePath;
    private final Map<String, Object> metadata;
    
    // Cached index for faster lookups
    private final Map<String, List<Segment>> segmentsByTag;
    
    private Message(Builder builder) {
        this.fileFormat = builder.fileFormat;
        this.messageType = builder.messageType;
        this.segments = Collections.unmodifiableList(new ArrayList<>(builder.segments));
        this.sourceFilePath = builder.sourceFilePath;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
        
        // Build index for fast tag-based lookups
        this.segmentsByTag = this.segments.stream()
            .collect(Collectors.groupingBy(
                Segment::getTag,
                Collectors.toList()
            ));
    }
    
    /**
     * Gets the file format of this message.
     * 
     * @return file format (EDIFACT, ANSI_X12, or XML)
     */
    public FileFormat getFileFormat() {
        return fileFormat;
    }
    
    /**
     * Gets the message type identifier.
     * 
     * <p>Examples:
     * <ul>
     *   <li>EDIFACT: IFTMBF, IFTMBC, IFTMIN</li>
     *   <li>ANSI X12: 214, 990, 204</li>
     *   <li>XML: Root element name</li>
     * </ul>
     * 
     * @return message type or null if not set
     */
    public String getMessageType() {
        return messageType;
    }
    
    /**
     * Gets all segments in this message in order.
     * 
     * @return unmodifiable list of segments
     */
    public List<Segment> getSegments() {
        return segments;
    }
    
    /**
     * Gets the source file path this message was parsed from.
     * 
     * @return source file path or null if not set
     */
    public String getSourceFilePath() {
        return sourceFilePath;
    }
    
    /**
     * Gets all metadata associated with this message.
     * Metadata can store additional information like sender ID, receiver ID, etc.
     * 
     * @return unmodifiable map of metadata
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    /**
     * Gets a metadata value by key.
     * 
     * @param key metadata key
     * @return metadata value or null if not found
     */
    public Object getMetadataValue(String key) {
        return metadata.get(key);
    }
    
    /**
     * Gets the number of segments in this message.
     * 
     * @return segment count
     */
    public int getSegmentCount() {
        return segments.size();
    }
    
    /**
     * Gets all segments with the specified tag.
     * 
     * @param tag segment tag (e.g., "NAD", "DTM")
     * @return list of matching segments (may be empty)
     */
    public List<Segment> getSegmentsByTag(String tag) {
        return segmentsByTag.getOrDefault(tag, Collections.emptyList());
    }
    
    /**
     * Gets the first segment with the specified tag.
     * 
     * @param tag segment tag
     * @return Optional containing the first matching segment, empty if not found
     */
    public Optional<Segment> getFirstSegmentByTag(String tag) {
        List<Segment> matches = getSegmentsByTag(tag);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }
    
    /**
     * Checks if this message contains a segment with the specified tag.
     * 
     * @param tag segment tag
     * @return true if at least one segment with this tag exists
     */
    public boolean hasSegment(String tag) {
        return segmentsByTag.containsKey(tag);
    }
    
    /**
     * Gets the count of segments with the specified tag.
     * 
     * @param tag segment tag
     * @return number of segments with this tag
     */
    public int getSegmentCount(String tag) {
        return getSegmentsByTag(tag).size();
    }
    
    /**
     * Gets a segment by its sequence number.
     * 
     * @param sequenceNumber sequence number (0-based)
     * @return Optional containing the segment, empty if index out of bounds
     */
    public Optional<Segment> getSegmentBySequence(int sequenceNumber) {
        if (sequenceNumber >= 0 && sequenceNumber < segments.size()) {
            return Optional.of(segments.get(sequenceNumber));
        }
        return Optional.empty();
    }
    
    /**
     * Gets all unique segment tags in this message.
     * 
     * @return set of segment tags
     */
    public Set<String> getSegmentTags() {
        return segmentsByTag.keySet();
    }
    
    /**
     * Finds all segments that contain a field with the specified value.
     * 
     * @param fieldValue value to search for
     * @return list of segments containing fields with this value
     */
    public List<Segment> findSegmentsByFieldValue(String fieldValue) {
        if (fieldValue == null) return Collections.emptyList();
        
        return segments.stream()
            .filter(segment -> segment.getFields().stream()
                .anyMatch(field -> fieldValue.equals(field.getValue())))
            .collect(Collectors.toList());
    }
    
    /**
     * Creates a new builder for constructing Message instances.
     * 
     * @return new Message.Builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a builder initialized with values from this message.
     * Useful for creating modified copies.
     * 
     * @return new Message.Builder with current message's values
     */
    public Builder toBuilder() {
        Builder builder = new Builder()
            .fileFormat(this.fileFormat)
            .messageType(this.messageType)
            .sourceFilePath(this.sourceFilePath)
            .addMetadata(this.metadata);
        
        this.segments.forEach(builder::addSegment);
        return builder;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return fileFormat == message.fileFormat &&
               Objects.equals(messageType, message.messageType) &&
               Objects.equals(segments, message.segments);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(fileFormat, messageType, segments);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Message{");
        sb.append("format=").append(fileFormat);
        if (messageType != null) {
            sb.append(", type='").append(messageType).append('\'');
        }
        sb.append(", segments=").append(segments.size());
        if (sourceFilePath != null) {
            sb.append(", source='").append(sourceFilePath).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }
    
    /**
     * Returns a detailed string representation including segment summary.
     * 
     * @return detailed string representation
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder(toString());
        sb.append("\nSegments by type:\n");
        segmentsByTag.forEach((tag, segs) -> 
            sb.append("  ").append(tag).append(": ").append(segs.size()).append("\n")
        );
        return sb.toString();
    }
    
    /**
     * Builder for creating immutable Message instances.
     */
    public static class Builder {
        private FileFormat fileFormat;
        private String messageType;
        private final List<Segment> segments = new ArrayList<>();
        private String sourceFilePath;
        private final Map<String, Object> metadata = new HashMap<>();
        
        private Builder() {}
        
        /**
         * Sets the file format for this message.
         * 
         * @param fileFormat file format (required)
         * @return this builder
         */
        public Builder fileFormat(FileFormat fileFormat) {
            this.fileFormat = fileFormat;
            return this;
        }
        
        /**
         * Sets the message type identifier.
         * 
         * @param messageType message type (e.g., "IFTMBF", "214")
         * @return this builder
         */
        public Builder messageType(String messageType) {
            this.messageType = messageType;
            return this;
        }
        
        /**
         * Adds a segment to this message.
         * 
         * @param segment segment to add
         * @return this builder
         */
        public Builder addSegment(Segment segment) {
            if (segment != null) {
                this.segments.add(segment);
            }
            return this;
        }
        
        /**
         * Adds multiple segments to this message.
         * 
         * @param segments segments to add
         * @return this builder
         */
        public Builder addSegments(List<Segment> segments) {
            if (segments != null) {
                this.segments.addAll(segments);
            }
            return this;
        }
        
        /**
         * Sets the source file path.
         * 
         * @param sourceFilePath path to source file
         * @return this builder
         */
        public Builder sourceFilePath(String sourceFilePath) {
            this.sourceFilePath = sourceFilePath;
            return this;
        }
        
        /**
         * Adds a metadata entry.
         * 
         * @param key metadata key
         * @param value metadata value
         * @return this builder
         */
        public Builder addMetadata(String key, Object value) {
            if (key != null) {
                this.metadata.put(key, value);
            }
            return this;
        }
        
        /**
         * Adds multiple metadata entries.
         * 
         * @param metadata metadata map
         * @return this builder
         */
        public Builder addMetadata(Map<String, Object> metadata) {
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }
        
        /**
         * Builds the immutable Message instance.
         * 
         * @return new Message
         * @throws IllegalStateException if fileFormat is not set
         */
        public Message build() {
            if (fileFormat == null) {
                throw new IllegalStateException("FileFormat is required");
            }
            return new Message(this);
        }
    }
}
