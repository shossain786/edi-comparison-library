package com.edi.comparison.core;

import com.edi.comparison.model.Message;

import java.util.HashMap;
import java.util.Map;

/**
 * Context for comparison operations.
 *
 * <p>Holds:
 * <ul>
 *   <li>Test data map (for resolving "testData.xxx" references)</li>
 *   <li>Inbound message (for resolving "inbound.xxx" references)</li>
 *   <li>Configuration options</li>
 * </ul>
 *
 * <p>This allows field rules to reference dynamic values:
 * <pre>
 * source: testData.bookingNumber  → context.getTestDataValue("bookingNumber")
 * source: inbound.BGM.0001        → context.getInboundFieldValue("BGM.0001")
 * </pre>
 */
public class ComparisonContext {

    private final Map<String, Object> testData;
    private final Message inboundMessage;
    private final Map<String, Object> config;

    private ComparisonContext(Builder builder) {
        this.testData = new HashMap<>(builder.testData);
        this.inboundMessage = builder.inboundMessage;
        this.config = new HashMap<>(builder.config);
    }

    /**
     * Gets a value from the test data map.
     *
     * @param key test data key
     * @return value or null if not found
     */
    public Object getTestDataValue(String key) {
        return testData.get(key);
    }

    /**
     * Gets a string value from test data.
     *
     * @param key test data key
     * @return string value or null
     */
    public String getTestDataString(String key) {
        Object value = testData.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Checks if test data contains a key.
     *
     * @param key key to check
     * @return true if key exists
     */
    public boolean hasTestData(String key) {
        return testData.containsKey(key);
    }

    /**
     * Gets a field value from the inbound message.
     *
     * @param fieldPath field path (e.g., "BGM.0001")
     * @return field value or null
     */
    public String getInboundFieldValue(String fieldPath) {
        if (inboundMessage == null || fieldPath == null) {
            return null;
        }

        // Parse field path: "SEGMENT.POSITION"
        String[] parts = fieldPath.split("\\.", 2);
        if (parts.length != 2) {
            return null;
        }

        String segmentTag = parts[0];
        String position = parts[1];

        return inboundMessage.getFirstSegmentByTag(segmentTag)
                .map(segment -> segment.getFieldValue(segmentTag + "." + position))
                .orElse(null);
    }

    /**
     * Gets the inbound message.
     *
     * @return inbound message or null
     */
    public Message getInboundMessage() {
        return inboundMessage;
    }

    /**
     * Gets a configuration value.
     *
     * @param key config key
     * @return config value or null
     */
    public Object getConfigValue(String key) {
        return config.get(key);
    }

    /**
     * Gets a configuration value as boolean.
     *
     * @param key config key
     * @param defaultValue default value if not found
     * @return boolean value
     */
    public boolean getConfigBoolean(String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    /**
     * Resolves a value source reference.
     *
     * <p>Handles:
     * <ul>
     *   <li>"testData.xxx" → from test data map</li>
     *   <li>"inbound.SEGMENT.POSITION" → from inbound message</li>
     *   <li>Anything else → returns as-is</li>
     * </ul>
     *
     * @param source source reference
     * @return resolved value or null
     */
    public String resolveSource(String source) {
        if (source == null) {
            return null;
        }

        if (source.startsWith("testData.")) {
            String key = source.substring("testData.".length());
            return getTestDataString(key);
        }

        if (source.startsWith("inbound.")) {
            String fieldPath = source.substring("inbound.".length());
            return getInboundFieldValue(fieldPath);
        }

        // Return as-is if not a reference
        return source;
    }

    /**
     * Creates a new builder.
     *
     * @return new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating ComparisonContext instances.
     */
    public static class Builder {
        private final Map<String, Object> testData = new HashMap<>();
        private Message inboundMessage;
        private final Map<String, Object> config = new HashMap<>();

        private Builder() {}

        /**
         * Sets the test data map.
         *
         * @param testData test data map
         * @return this builder
         */
        public Builder testData(Map<String, Object> testData) {
            if (testData != null) {
                this.testData.putAll(testData);
            }
            return this;
        }

        /**
         * Adds a single test data entry.
         *
         * @param key key
         * @param value value
         * @return this builder
         */
        public Builder addTestData(String key, Object value) {
            this.testData.put(key, value);
            return this;
        }

        /**
         * Sets the inbound message.
         *
         * @param inboundMessage inbound message
         * @return this builder
         */
        public Builder inboundMessage(Message inboundMessage) {
            this.inboundMessage = inboundMessage;
            return this;
        }

        /**
         * Adds configuration options.
         *
         * @param config configuration map
         * @return this builder
         */
        public Builder config(Map<String, Object> config) {
            if (config != null) {
                this.config.putAll(config);
            }
            return this;
        }

        /**
         * Adds a single configuration entry.
         *
         * @param key key
         * @param value value
         * @return this builder
         */
        public Builder addConfig(String key, Object value) {
            this.config.put(key, value);
            return this;
        }

        /**
         * Builds the ComparisonContext.
         *
         * @return new ComparisonContext
         */
        public ComparisonContext build() {
            return new ComparisonContext(this);
        }
    }
}