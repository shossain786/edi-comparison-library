package com.edi.comparison.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuration manager for EDI Comparison Library.
 *
 * <p>Users should create {@code edi-comparison.properties} in their project's
 * {@code src/test/resources/} or {@code src/main/resources/} directory.
 *
 * <p>Example properties file:
 * <pre>
 * # Report Configuration
 * report.base.dir=target/reports
 * report.filename.pattern=report_{timestamp}.html
 *
 * # Comparison Configuration
 * comparison.fail.on.first.error=false
 * comparison.case.sensitive=true
 * </pre>
 *
 * <p>Usage:
 * <pre>
 * // Load from classpath (looks for edi-comparison.properties in your project)
 * ComparisonConfig config = ComparisonConfig.load();
 *
 * // Load from external file path
 * ComparisonConfig config = ComparisonConfig.load("C:/config/my-config.properties");
 *
 * // Get values
 * String reportDir = config.getReportBaseDir();
 * </pre>
 *
 * <p>If no config file is found, sensible defaults are used.
 */
public class ComparisonConfig {

    private static final String DEFAULT_CONFIG_FILE = "edi-comparison.properties";

    // Default values
    private static final String DEFAULT_REPORT_BASE_DIR = "target/reports";
    private static final String DEFAULT_FILENAME_PATTERN = "report_{timestamp}.html";
    private static final boolean DEFAULT_FAIL_ON_FIRST_ERROR = false;
    private static final boolean DEFAULT_CASE_SENSITIVE = true;
    private static final boolean DEFAULT_DETECT_UNEXPECTED_SEGMENTS = true;
    private static final boolean DEFAULT_VALIDATE_SEGMENT_ORDER = false;

    private final Properties properties;

    private ComparisonConfig(Properties properties) {
        this.properties = properties;
    }

    /**
     * Loads configuration from default classpath resource.
     *
     * @return loaded configuration
     */
    public static ComparisonConfig load() {
        Properties props = new Properties();

        // Try to load from classpath
        try (InputStream is = ComparisonConfig.class.getClassLoader()
                .getResourceAsStream(DEFAULT_CONFIG_FILE)) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            // Use defaults
        }

        return new ComparisonConfig(props);
    }

    /**
     * Loads configuration from an external file path.
     *
     * @param configFilePath path to configuration file
     * @return loaded configuration
     * @throws IOException if file cannot be read
     */
    public static ComparisonConfig load(String configFilePath) throws IOException {
        Properties props = new Properties();
        Path path = Paths.get(configFilePath);

        if (Files.exists(path)) {
            try (InputStream is = Files.newInputStream(path)) {
                props.load(is);
            }
        } else {
            // Fall back to classpath
            try (InputStream is = ComparisonConfig.class.getClassLoader()
                    .getResourceAsStream(configFilePath)) {
                if (is != null) {
                    props.load(is);
                }
            }
        }

        return new ComparisonConfig(props);
    }

    /**
     * Creates configuration with custom properties.
     *
     * @param properties custom properties
     * @return configuration instance
     */
    public static ComparisonConfig fromProperties(Properties properties) {
        return new ComparisonConfig(properties);
    }

    /**
     * Gets the base directory for reports.
     *
     * @return report base directory
     */
    public String getReportBaseDir() {
        return properties.getProperty("report.base.dir", DEFAULT_REPORT_BASE_DIR);
    }

    /**
     * Gets the filename pattern for reports.
     * Supports {timestamp} placeholder.
     *
     * @return filename pattern
     */
    public String getReportFilenamePattern() {
        return properties.getProperty("report.filename.pattern", DEFAULT_FILENAME_PATTERN);
    }

    /**
     * Checks if comparison should fail on first error.
     *
     * @return true to fail fast, false to collect all errors
     */
    public boolean isFailOnFirstError() {
        return Boolean.parseBoolean(
                properties.getProperty("comparison.fail.on.first.error",
                        String.valueOf(DEFAULT_FAIL_ON_FIRST_ERROR)));
    }

    /**
     * Checks if comparison is case sensitive.
     *
     * @return true for case sensitive comparison
     */
    public boolean isCaseSensitive() {
        return Boolean.parseBoolean(
                properties.getProperty("comparison.case.sensitive",
                        String.valueOf(DEFAULT_CASE_SENSITIVE)));
    }

    /**
     * Checks if unexpected segments should be detected.
     *
     * @return true to report segments not defined in template
     */
    public boolean isDetectUnexpectedSegments() {
        return Boolean.parseBoolean(
                properties.getProperty("comparison.detect.unexpected.segments",
                        String.valueOf(DEFAULT_DETECT_UNEXPECTED_SEGMENTS)));
    }

    /**
     * Checks if segment order should be validated.
     * When true, segments must appear in the order defined in the template.
     *
     * @return true to validate segment order
     */
    public boolean isValidateSegmentOrder() {
        return Boolean.parseBoolean(
                properties.getProperty("comparison.validate.segment.order",
                        String.valueOf(DEFAULT_VALIDATE_SEGMENT_ORDER)));
    }

    /**
     * Gets a custom property value.
     *
     * @param key property key
     * @return property value or null
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Gets a custom property value with default.
     *
     * @param key property key
     * @param defaultValue default value if not found
     * @return property value or default
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    @Override
    public String toString() {
        return "ComparisonConfig{" +
                "reportBaseDir='" + getReportBaseDir() + '\'' +
                ", filenamePattern='" + getReportFilenamePattern() + '\'' +
                ", failOnFirstError=" + isFailOnFirstError() +
                ", caseSensitive=" + isCaseSensitive() +
                ", detectUnexpectedSegments=" + isDetectUnexpectedSegments() +
                ", validateSegmentOrder=" + isValidateSegmentOrder() +
                '}';
    }
}
