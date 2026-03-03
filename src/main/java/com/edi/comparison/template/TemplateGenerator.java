package com.edi.comparison.template;

import com.edi.comparison.model.Field;
import com.edi.comparison.model.Message;
import com.edi.comparison.model.Segment;
import com.edi.comparison.parser.AutoDetectParser;
import com.edi.comparison.parser.FileParser;
import com.edi.comparison.parser.ParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates a ready-to-use YAML template from one or more "golden" EDI files.
 *
 * <p>This eliminates the biggest pain point of manually writing YAML rules for
 * complex, multi-hundred-segment EDI files. Point at a known-good file, get a
 * complete template back in seconds.
 *
 * <h2>Three generation modes</h2>
 * <ul>
 *   <li>{@link GenerationMode#MINIMAL} — segment names only (quickest starting point)</li>
 *   <li>{@link GenerationMode#STRUCTURE} — segments + exact occurrence counts (default)</li>
 *   <li>{@link GenerationMode#FULL} — structure + field-level rules with smart classification</li>
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <h3>From a single golden file</h3>
 * <pre>
 * // Generates template, saves to disk — done
 * TemplateGenerator.from("samples/golden-booking.edi")
 *         .saveTo("rules/booking-template.yaml");
 *
 * // With options
 * TemplateGenerator.from("samples/golden-booking.edi")
 *         .mode(GenerationMode.FULL)
 *         .saveTo("rules/booking-template.yaml");
 *
 * // Get YAML string without saving
 * String yaml = TemplateGenerator.from("samples/golden-booking.edi").generate();
 * </pre>
 *
 * <h3>Learn from an entire folder (recommended for 500+ files)</h3>
 * <pre>
 * // Scans all files, marks segments as required only if present in every file,
 * // uses the most common occurrence count for each segment
 * TemplateGenerator.learnFrom("output/files/")
 *         .saveTo("rules/learned-template.yaml");
 *
 * // With file filter
 * TemplateGenerator.learnFrom("output/files/", "*.edi")
 *         .saveTo("rules/learned-template.yaml");
 * </pre>
 *
 * <h3>Typical workflow</h3>
 * <pre>
 * // Step 1: generate template from golden file
 * TemplateGenerator.from("samples/golden.edi")
 *         .mode(GenerationMode.STRUCTURE)
 *         .saveTo("rules/my-template.yaml");
 *
 * // Step 2: open my-template.yaml, review and tweak as needed
 *
 * // Step 3: verify your entire batch
 * EdiVerifier.with("rules/my-template.yaml")
 *         .verifyFolder("output/files/")
 *         .assertAllPassed();
 * </pre>
 *
 * <h2>FULL mode field classification</h2>
 * The generator smartly picks the right validation for each field:
 * <ul>
 *   <li>8 or 12 digits → {@code pattern_match} (date)</li>
 *   <li>Short uppercase/numeric code (≤4 chars) → {@code exact_match}</li>
 *   <li>Anything longer or dynamic-looking → {@code exists}</li>
 *   <li>Multi-occurrence segment fields → {@code exists} (values differ per record)</li>
 * </ul>
 */
public class TemplateGenerator {

    private static final Logger log = LoggerFactory.getLogger(TemplateGenerator.class);

    /** Standard envelope segments excluded from generated templates. */
    private static final Set<String> ENVELOPE_SEGMENTS = Set.of(
            // EDIFACT
            "UNA", "UNB", "UNG", "UNH", "UNT", "UNE", "UNZ",
            // ANSI X12
            "ISA", "GS", "ST", "SE", "GE", "IEA"
    );

    // =========================================================================
    // Static entry points
    // =========================================================================

    /**
     * Starts a single-file template builder.
     *
     * @param filePath path to a golden/reference EDI file (filesystem or classpath)
     * @return builder
     */
    public static Builder from(String filePath) {
        return new Builder(filePath);
    }

    /**
     * Starts a single-file template builder.
     *
     * @param filePath path to a golden/reference EDI file
     * @return builder
     */
    public static Builder from(Path filePath) {
        return new Builder(filePath);
    }

    /**
     * Starts a single-file template builder from an already-parsed message.
     * Useful when you have a {@code Message} in memory from a previous parse.
     *
     * @param message parsed EDI message
     * @return builder
     */
    public static Builder from(Message message) {
        return new Builder(message);
    }

    /**
     * Starts a batch-learning builder that scans an entire directory.
     *
     * <p>Segments that appear in <em>all</em> files are marked {@code required: true}.
     * Segments that appear in only some files are marked {@code required: false}.
     * The most common occurrence count is used for {@code expected_count}.
     *
     * @param directory path to the directory of EDI files
     * @return batch builder
     */
    public static BatchBuilder learnFrom(String directory) {
        return new BatchBuilder(Paths.get(directory), null);
    }

    /**
     * Starts a batch-learning builder with a glob filter.
     *
     * @param directory   path to the directory of EDI files
     * @param filePattern glob pattern matched against filenames, e.g. {@code "*.edi"}
     * @return batch builder
     */
    public static BatchBuilder learnFrom(String directory, String filePattern) {
        return new BatchBuilder(Paths.get(directory), filePattern);
    }

    /**
     * Starts a batch-learning builder from an explicit file list.
     *
     * @param files list of EDI file paths
     * @return batch builder
     */
    public static BatchBuilder learnFrom(List<Path> files) {
        return new BatchBuilder(files);
    }

    // =========================================================================
    // Core generation logic (package-private, used by both builders)
    // =========================================================================

    /**
     * Generates YAML from a single parsed message.
     */
    static String generateYaml(Message message, GenerationMode mode,
                                String sourceName, String messageType) {
        List<Segment> segments = message.getSegments().stream()
                .filter(s -> !ENVELOPE_SEGMENTS.contains(s.getTag()))
                .collect(Collectors.toList());

        // Group segments by tag, preserving first-appearance order
        LinkedHashMap<String, List<Segment>> byTag = new LinkedHashMap<>();
        for (Segment s : segments) {
            byTag.computeIfAbsent(s.getTag(), k -> new ArrayList<>()).add(s);
        }

        String detectedType = messageType != null ? messageType
                : (message.getMessageType() != null ? message.getMessageType() : "UNKNOWN");

        return buildYaml(byTag, mode, sourceName, detectedType, null);
    }

    /**
     * Generates YAML from multiple files (batch learning).
     * tagStats: tag → SegmentStats (across all files)
     */
    static String generateBatchYaml(Map<String, SegmentStats> tagStats,
                                     GenerationMode mode,
                                     int totalFiles,
                                     String sourceName) {
        StringBuilder sb = new StringBuilder();
        appendHeader(sb, sourceName, mode,
                "Learned from " + totalFiles + " file(s) — segments required only if present in all files");
        sb.append("message_type: \"UNKNOWN\"\n");
        sb.append("description: \"Learned template from ").append(sourceName).append("\"\n\n");
        sb.append("rules:\n");

        for (Map.Entry<String, SegmentStats> entry : tagStats.entrySet()) {
            String tag = entry.getKey();
            SegmentStats stats = entry.getValue();
            boolean required = stats.fileCount == totalFiles;
            int count = stats.mostCommonCount;
            boolean multi = count > 1;

            sb.append("  - segment: ").append(tag).append("\n");
            sb.append("    required: ").append(required).append("\n");
            if (multi) sb.append("    multiple_occurrences: true\n");
            if (mode != GenerationMode.MINIMAL && count > 0) {
                sb.append("    expected_count: ").append(count).append("\n");
            }
            appendFields(sb, tag, stats.sampleSegments, mode, multi);
        }
        return sb.toString();
    }

    // =========================================================================
    // YAML building helpers
    // =========================================================================

    private static String buildYaml(LinkedHashMap<String, List<Segment>> byTag,
                                     GenerationMode mode,
                                     String sourceName,
                                     String messageType,
                                     String extraComment) {
        StringBuilder sb = new StringBuilder();
        appendHeader(sb, sourceName, mode, extraComment);

        sb.append("message_type: \"").append(messageType).append("\"\n");
        sb.append("description: \"Auto-generated from ").append(sourceName).append("\"\n\n");
        sb.append("rules:\n");

        for (Map.Entry<String, List<Segment>> entry : byTag.entrySet()) {
            String tag = entry.getKey();
            List<Segment> occurrences = entry.getValue();
            int count = occurrences.size();
            boolean multi = count > 1;

            sb.append("  - segment: ").append(tag).append("\n");
            sb.append("    required: true\n");
            if (multi) sb.append("    multiple_occurrences: true\n");
            if (mode != GenerationMode.MINIMAL) {
                sb.append("    expected_count: ").append(count).append("\n");
            }
            appendFields(sb, tag, occurrences, mode, multi);
        }
        return sb.toString();
    }

    private static void appendHeader(StringBuilder sb, String source,
                                      GenerationMode mode, String extra) {
        sb.append("# Auto-generated template\n");
        sb.append("# Source: ").append(source).append("\n");
        sb.append("# Generated: ").append(LocalDate.now()).append("\n");
        sb.append("# Mode: ").append(mode.name()).append("\n");
        if (extra != null) sb.append("# ").append(extra).append("\n");
        sb.append("# Review and edit before use — especially field validations in FULL mode\n");
        sb.append("#\n");
        sb.append("# Validation types: exact_match | pattern_match | exists | date_format\n");
        sb.append("#\n\n");
    }

    private static void appendFields(StringBuilder sb, String tag,
                                      List<Segment> occurrences,
                                      GenerationMode mode,
                                      boolean multi) {
        if (mode == GenerationMode.MINIMAL || mode == GenerationMode.STRUCTURE) {
            sb.append("    fields: []\n\n");
            return;
        }

        // FULL mode — use first occurrence for field discovery
        Segment first = occurrences.get(0);
        List<Field> fields = first.getFields().stream()
                .filter(f -> f.hasValue())
                .collect(Collectors.toList());

        if (fields.isEmpty()) {
            sb.append("    fields: []\n\n");
            return;
        }

        sb.append("    fields:\n");
        for (Field field : fields) {
            String value = field.getValue();
            String validation = classifyValidation(value, multi);

            sb.append("      - position: ").append(field.getPosition()).append("\n");
            if (field.getName() != null) {
                sb.append("        name: ").append(field.getName()).append("\n");
            }
            sb.append("        validation: ").append(validation).append("\n");

            if ("exact_match".equals(validation)) {
                sb.append("        expected_value: \"").append(escapeYaml(value)).append("\"\n");
            } else if ("pattern_match".equals(validation)) {
                sb.append("        pattern: \"").append(datePattern(value)).append("\"\n");
            }
            // "exists" needs nothing extra
        }
        sb.append("\n");
    }

    /**
     * Smart field validation classification.
     *
     * <p>Rules (in priority order):
     * <ol>
     *   <li>Multi-occurrence segment → {@code exists} (values differ per occurrence)</li>
     *   <li>8 or 12 digit string → {@code pattern_match} (date)</li>
     *   <li>Short uppercase/numeric code (≤4 chars) → {@code exact_match}</li>
     *   <li>Pure number → {@code exact_match}</li>
     *   <li>Everything else → {@code exists} (likely dynamic reference)</li>
     * </ol>
     */
    private static String classifyValidation(String value, boolean multi) {
        if (value == null || value.isEmpty()) return "exists";
        if (multi) return "exists"; // values differ across occurrences
        if (value.matches("\\d{8}") || value.matches("\\d{12}")) return "pattern_match";
        if (value.length() <= 4 && value.matches("[A-Z0-9]+")) return "exact_match";
        if (value.matches("\\d+")) return "exact_match";
        return "exists";
    }

    private static String datePattern(String value) {
        if (value.matches("\\d{8}")) return "^[0-9]{8}$";
        if (value.matches("\\d{12}")) return "^[0-9]{12}$";
        return "^[0-9]{8}$|^[0-9]{12}$";
    }

    private static String escapeYaml(String value) {
        if (value == null) return "";
        return value.replace("\"", "\\\"");
    }

    // =========================================================================
    // Batch statistics helper
    // =========================================================================

    static class SegmentStats {
        int fileCount = 0;              // how many files contain this segment
        int mostCommonCount = 0;        // most frequent occurrence count
        Map<Integer, Integer> countFreq = new LinkedHashMap<>(); // count → frequency
        List<Segment> sampleSegments = new ArrayList<>(); // first occurrence from first file

        void addOccurrence(List<Segment> segs) {
            fileCount++;
            int n = segs.size();
            countFreq.merge(n, 1, Integer::sum);
            if (mostCommonCount == 0) {
                mostCommonCount = n;
            }
            // pick most frequent count
            int best = countFreq.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(n);
            mostCommonCount = best;
            if (sampleSegments.isEmpty()) {
                sampleSegments.addAll(segs);
            }
        }
    }

    // =========================================================================
    // Generation modes
    // =========================================================================

    /**
     * Controls how much detail is included in the generated template.
     */
    public enum GenerationMode {

        /**
         * Segment names only — the minimal skeleton.
         *
         * <p>Good when you just need to check that the right segments are present
         * and want to add field rules manually.
         *
         * <p>Output per segment:
         * <pre>
         *   - segment: BGM
         *     required: true
         *     fields: []
         * </pre>
         */
        MINIMAL,

        /**
         * Segment names + expected occurrence counts. <b>Recommended default.</b>
         *
         * <p>Output per segment:
         * <pre>
         *   - segment: DTM
         *     required: true
         *     multiple_occurrences: true
         *     expected_count: 6
         *     fields: []
         * </pre>
         */
        STRUCTURE,

        /**
         * Full template with field-level rules using smart classification.
         *
         * <p>Output per field:
         * <ul>
         *   <li>Short qualifier → {@code exact_match}</li>
         *   <li>Date (8/12 digits) → {@code pattern_match}</li>
         *   <li>Dynamic reference → {@code exists}</li>
         * </ul>
         *
         * <p>Review the output carefully — exact_match values may need to be
         * changed to {@code exists} for fields that vary across your files.
         */
        FULL
    }

    // =========================================================================
    // Single-file Builder
    // =========================================================================

    /**
     * Fluent builder for generating a template from a single golden file.
     */
    public static class Builder {

        private Message message;
        private String sourceName;
        private GenerationMode mode = GenerationMode.STRUCTURE;

        private Builder(String filePath) {
            this(Paths.get(filePath));
        }

        private Builder(Path filePath) {
            try {
                FileParser parser = new AutoDetectParser();
                this.message = parser.parse(filePath.toFile());
                this.sourceName = filePath.getFileName().toString();
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Cannot parse file '" + filePath + "': " + e.getMessage(), e);
            }
        }

        private Builder(Message message) {
            this.message = message;
            this.sourceName = message.getMessageType() != null
                    ? message.getMessageType() : "message";
        }

        /**
         * Sets the generation mode.
         * Default: {@link GenerationMode#STRUCTURE}.
         */
        public Builder mode(GenerationMode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * Generates the YAML template as a string.
         *
         * @return YAML string ready to save or inspect
         */
        public String generate() {
            log.info("Generating {} template from: {}", mode, sourceName);
            String yaml = generateYaml(message, mode, sourceName, null);
            log.info("Template generated: {} segment rule(s)", message.getSegments().stream()
                    .map(s -> s.getTag()).distinct().filter(t -> !ENVELOPE_SEGMENTS.contains(t)).count());
            return yaml;
        }

        /**
         * Generates the YAML and saves it to the given path.
         *
         * @param outputPath path to the output .yaml file
         * @throws IOException if the file cannot be written
         */
        public void saveTo(String outputPath) throws IOException {
            saveTo(Paths.get(outputPath));
        }

        /**
         * Generates the YAML and saves it to the given path.
         *
         * @param outputPath path to the output .yaml file
         * @throws IOException if the file cannot be written
         */
        public void saveTo(Path outputPath) throws IOException {
            Path parent = outputPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(outputPath, generate());
            log.info("Template saved to: {}", outputPath.toAbsolutePath());
        }
    }

    // =========================================================================
    // Batch-learning Builder
    // =========================================================================

    /**
     * Fluent builder for learning a template from multiple EDI files.
     *
     * <p>Segments present in <em>all</em> files → {@code required: true}.
     * Segments present in <em>some</em> files → {@code required: false}.
     * The most common occurrence count → {@code expected_count}.
     */
    public static class BatchBuilder {

        private List<Path> files;
        private final String filePattern;
        private GenerationMode mode = GenerationMode.STRUCTURE;
        private String sourceName;

        private BatchBuilder(Path directory, String filePattern) {
            if (!Files.isDirectory(directory)) {
                throw new IllegalArgumentException("Not a directory: " + directory);
            }
            this.filePattern = filePattern;
            this.sourceName = directory.getFileName().toString();
            try {
                this.files = collectFiles(directory, filePattern);
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Cannot list directory '" + directory + "': " + e.getMessage(), e);
            }
        }

        private BatchBuilder(List<Path> files) {
            this.files = new ArrayList<>(files);
            this.filePattern = null;
            this.sourceName = files.size() + " files";
        }

        /**
         * Sets the generation mode. Default: {@link GenerationMode#STRUCTURE}.
         */
        public BatchBuilder mode(GenerationMode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * Generates the YAML by scanning all files.
         *
         * @return YAML string
         * @throws IOException if any file cannot be read
         */
        public String generate() throws IOException {
            if (files.isEmpty()) {
                throw new IllegalStateException("No files found to learn from");
            }
            log.info("Learning {} template from {} file(s) in: {}", mode, files.size(), sourceName);
            FileParser parser = new AutoDetectParser();
            LinkedHashMap<String, SegmentStats> tagStats = new LinkedHashMap<>();
            int parsed = 0;
            int skipped = 0;

            for (Path file : files) {
                try {
                    String content = Files.readString(file);
                    Message message = parser.parse(content);
                    LinkedHashMap<String, List<Segment>> byTag = new LinkedHashMap<>();
                    for (Segment s : message.getSegments()) {
                        if (!ENVELOPE_SEGMENTS.contains(s.getTag())) {
                            byTag.computeIfAbsent(s.getTag(), k -> new ArrayList<>()).add(s);
                        }
                    }
                    for (Map.Entry<String, List<Segment>> e : byTag.entrySet()) {
                        tagStats.computeIfAbsent(e.getKey(), k -> new SegmentStats())
                                .addOccurrence(e.getValue());
                    }
                    parsed++;
                    log.debug("  Scanned [{}/{}]: {}", parsed, files.size(), file.getFileName());
                } catch (IOException | ParseException | RuntimeException e) {
                    skipped++;
                    log.warn("  Skipped (unparseable): {} — {}", file.getFileName(), e.getMessage());
                }
            }

            if (parsed == 0) {
                throw new IllegalStateException("No files could be parsed in: " + sourceName);
            }

            log.info("Learn complete: {}/{} files parsed, {} segment type(s) found, {} skipped",
                    parsed, files.size(), tagStats.size(), skipped);
            return generateBatchYaml(tagStats, mode, parsed,
                    sourceName + " (" + parsed + " files)");
        }

        /**
         * Generates the YAML and saves it to the given path.
         *
         * @param outputPath path to the output .yaml file
         * @throws IOException if a file cannot be read or the output cannot be written
         */
        public void saveTo(String outputPath) throws IOException {
            saveTo(Paths.get(outputPath));
        }

        /**
         * Generates the YAML and saves it to the given path.
         *
         * @param outputPath path to the output .yaml file
         * @throws IOException if a file cannot be read or the output cannot be written
         */
        public void saveTo(Path outputPath) throws IOException {
            Path parent = outputPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(outputPath, generate());
        }

        private static List<Path> collectFiles(Path dir, String pattern) throws IOException {
            try (Stream<Path> stream = Files.walk(dir, 1)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(p -> matchesGlob(p, pattern))
                        .sorted()
                        .collect(Collectors.toList());
            }
        }

        private static boolean matchesGlob(Path path, String pattern) {
            if (pattern == null) return true;
            return java.nio.file.FileSystems.getDefault()
                    .getPathMatcher("glob:" + pattern)
                    .matches(path.getFileName());
        }
    }
}
