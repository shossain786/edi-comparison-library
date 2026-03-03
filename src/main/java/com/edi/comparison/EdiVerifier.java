package com.edi.comparison;

import com.edi.comparison.batch.BatchResult;
import com.edi.comparison.batch.FileResult;
import com.edi.comparison.config.ComparisonConfig;
import com.edi.comparison.core.ComparisonContext;
import com.edi.comparison.core.ComparisonEngine;
import com.edi.comparison.model.ComparisonResult;
import com.edi.comparison.model.FileFormat;
import com.edi.comparison.parser.AnsiX12Parser;
import com.edi.comparison.parser.AutoDetectParser;
import com.edi.comparison.parser.EdifactParser;
import com.edi.comparison.parser.FileParser;
import com.edi.comparison.parser.XmlParser;
import com.edi.comparison.rule.RuleLoader;
import com.edi.comparison.rule.RuleSet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Main entry point for EDI file verification — single files and large batches.
 *
 * <p>Wraps all library internals (parsers, rule loading, engine, reporting)
 * behind one fluent API. The only things you need to provide are a YAML
 * template and the file(s) to verify.
 *
 * <h2>Usage examples</h2>
 *
 * <h3>Single file (e.g. in a test)</h3>
 * <pre>
 * EdiVerifier.with("rules/booking-template.yaml")
 *         .verify("output/booking-001.edi")
 *         .assertPassed();
 * </pre>
 *
 * <h3>Entire folder — 500+ files processed in parallel</h3>
 * <pre>
 * BatchResult result = EdiVerifier.with("rules/booking-template.yaml")
 *         .verifyFolder("output/files/");
 *
 * result.generateReport("target/reports/");  // combined HTML report
 * result.assertAllPassed();                   // throws with full failure summary
 * </pre>
 *
 * <h3>With options</h3>
 * <pre>
 * BatchResult result = EdiVerifier.with("rules/template.yaml")
 *         .parallelism(8)               // 8 threads — great for 500+ files
 *         .filePattern("*.edi")         // only .edi files
 *         .format(FileFormat.EDIFACT)   // skip auto-detection
 *         .recursive(true)              // walk subdirectories
 *         .verifyFolder("output/");
 * </pre>
 *
 * <h3>Explicit file list (e.g. from FTP download)</h3>
 * <pre>
 * BatchResult result = EdiVerifier.with("rules/template.yaml")
 *         .verifyFiles(ftpClient.downloadedFiles());
 * result.assertAllPassed();
 * </pre>
 *
 * <h3>Dynamic field values via test data</h3>
 * <pre>
 * EdiVerifier.with("rules/template.yaml")
 *         .testData("bookingNumber", "BK123")
 *         .verify("output/booking.edi")
 *         .assertPassed();
 * </pre>
 *
 * <h3>Inspect batch results</h3>
 * <pre>
 * BatchResult result = EdiVerifier.with("rules/template.yaml")
 *         .verifyFolder("output/");
 *
 * System.out.println(result.getSummary());
 * // → "500 files: 498 passed, 2 failed, 0 errored (12.3s)"
 *
 * result.getFailedFiles().forEach(f ->
 *         System.out.println(f + " — " + f.getDifferenceCount() + " diff(s)"));
 *
 * result.getMostCommonErrors(5).forEach((type, count) ->
 *         System.out.println(type + ": " + count));
 * </pre>
 */
public class EdiVerifier {

    private final RuleSet ruleSet;
    private final FileParser parser;
    private final int parallelism;
    private final String filePattern;
    private final boolean recursive;
    private final Map<String, Object> testData;
    private final ComparisonConfig config;

    private EdiVerifier(Builder builder) {
        this.ruleSet = builder.ruleSet;
        this.parser = builder.parser;
        this.parallelism = builder.parallelism;
        this.filePattern = builder.filePattern;
        this.recursive = builder.recursive;
        this.testData = Collections.unmodifiableMap(new HashMap<>(builder.testData));
        this.config = builder.config;
    }

    // =========================================================================
    // Static factories
    // =========================================================================

    /**
     * Starts building a verifier with the given YAML template.
     *
     * <p>The path is tried as a filesystem path first; if not found it falls
     * back to a classpath resource lookup.
     *
     * @param templatePath file system path or classpath resource path to the YAML rule file
     * @return fluent builder
     */
    public static Builder with(String templatePath) {
        return new Builder().template(templatePath);
    }

    /**
     * Starts building a verifier using a classpath resource for the YAML template.
     * Handy inside tests where rules live under {@code src/test/resources/}.
     *
     * @param resourcePath classpath resource path (e.g. {@code "rules/booking.yaml"})
     * @return fluent builder
     */
    public static Builder withClasspath(String resourcePath) {
        return new Builder().templateFromClasspath(resourcePath);
    }

    // =========================================================================
    // Instance verify methods
    // =========================================================================

    /**
     * Verifies a single EDI/XML file.
     *
     * @param filePath path to the file
     * @return result — call {@link FileResult#assertPassed()} to throw on failure
     * @throws IOException if the file cannot be read
     */
    public FileResult verify(String filePath) throws IOException {
        return verify(Paths.get(filePath));
    }

    /**
     * Verifies a single EDI/XML file.
     *
     * @param filePath path to the file
     * @return result
     * @throws IOException if the file cannot be read
     */
    public FileResult verify(Path filePath) throws IOException {
        long start = System.currentTimeMillis();
        try {
            String content = Files.readString(filePath);
            com.edi.comparison.model.Message message = parser.parse(content);
            ComparisonResult result = new ComparisonEngine(ruleSet, buildContext())
                    .compare(null, message);
            return FileResult.ofSuccess(filePath, result, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return FileResult.ofError(filePath, e, System.currentTimeMillis() - start);
        }
    }

    /**
     * Verifies all files in a directory.
     * Processes files in parallel (see {@link Builder#parallelism(int)}).
     *
     * @param directory path to the directory
     * @return aggregated batch result
     * @throws IOException if the directory cannot be walked
     */
    public BatchResult verifyFolder(String directory) throws IOException {
        return verifyFolder(Paths.get(directory));
    }

    /**
     * Verifies all files in a directory.
     *
     * @param directory path to the directory
     * @return aggregated batch result
     * @throws IOException if the directory cannot be walked
     */
    public BatchResult verifyFolder(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }
        int depth = recursive ? Integer.MAX_VALUE : 1;
        List<Path> files;
        try (Stream<Path> stream = Files.walk(directory, depth)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(this::matchesPattern)
                    .sorted()
                    .collect(Collectors.toList());
        }
        return verifyFiles(files);
    }

    /**
     * Verifies an explicit list of files in parallel.
     *
     * @param files list of paths to verify
     * @return aggregated batch result
     */
    public BatchResult verifyFiles(List<Path> files) {
        long start = System.currentTimeMillis();
        int threads = Math.min(parallelism, Math.max(1, files.size()));
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<FileResult>> futures = new ArrayList<>(files.size());
        for (Path file : files) {
            futures.add(executor.submit(() -> verify(file)));
        }
        executor.shutdown();
        List<FileResult> results = new ArrayList<>(files.size());
        for (Future<FileResult> f : futures) {
            try {
                results.add(f.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // verify() never throws — this should not happen
            }
        }
        return new BatchResult(results, System.currentTimeMillis() - start);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private ComparisonContext buildContext() {
        return ComparisonContext.builder()
                .testData(testData)
                .addConfig("detect_unexpected_segments", config.isDetectUnexpectedSegments())
                .addConfig("validate_segment_order", config.isValidateSegmentOrder())
                .addConfig("case_sensitive", config.isCaseSensitive())
                .build();
    }

    private boolean matchesPattern(Path path) {
        if (filePattern == null) {
            return true;
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + filePattern);
        return matcher.matches(path.getFileName());
    }

    // =========================================================================
    // Builder
    // =========================================================================

    /**
     * Fluent builder for {@link EdiVerifier}.
     *
     * <p>The terminal methods ({@code verify}, {@code verifyFolder},
     * {@code verifyFiles}) automatically call {@link #build()}, so you can
     * omit the explicit {@code .build()} in the common one-liner case.
     */
    public static class Builder {

        private RuleSet ruleSet;
        /** Auto-detect format by default — works for EDIFACT, ANSI X12, and XML. */
        private FileParser parser = new AutoDetectParser();
        /** Default: use all available CPU cores — good for large batches. */
        private int parallelism = Runtime.getRuntime().availableProcessors();
        /** Glob pattern matched against filenames; null = accept everything. */
        private String filePattern = null;
        /** Walk sub-directories when verifyFolder is called. Default: false. */
        private boolean recursive = false;
        private final Map<String, Object> testData = new HashMap<>();
        private ComparisonConfig config = ComparisonConfig.load();

        private Builder() {}

        /**
         * Loads the YAML template. Tries filesystem path first, then classpath.
         *
         * @param templatePath file path or classpath resource
         */
        public Builder template(String templatePath) {
            try {
                RuleLoader loader = new RuleLoader();
                try {
                    this.ruleSet = loader.loadFromFile(templatePath);
                } catch (FileNotFoundException e) {
                    this.ruleSet = loader.loadFromResource(templatePath);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Cannot load template '" + templatePath + "': " + e.getMessage(), e);
            }
            return this;
        }

        /**
         * Loads the YAML template explicitly from the classpath.
         *
         * @param resourcePath e.g. {@code "rules/booking.yaml"}
         */
        public Builder templateFromClasspath(String resourcePath) {
            try {
                this.ruleSet = new RuleLoader().loadFromResource(resourcePath);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Cannot load classpath template '" + resourcePath + "': " + e.getMessage(), e);
            }
            return this;
        }

        /**
         * Forces a specific parser instead of auto-detection.
         * Use when all files in a batch share the same known format.
         *
         * @param format EDIFACT, ANSI_X12, or XML
         */
        public Builder format(FileFormat format) {
            this.parser = switch (format) {
                case EDIFACT -> new EdifactParser();
                case ANSI_X12 -> new AnsiX12Parser();
                case XML -> new XmlParser();
            };
            return this;
        }

        /**
         * Number of threads for parallel batch processing.
         *
         * <p>Default: {@code Runtime.getRuntime().availableProcessors()}.
         * Set to {@code 1} to disable parallelism (sequential).
         * For I/O-heavy workloads (network drives, FTP), try 2–4× CPU count.
         *
         * @param threads number of threads (≥ 1)
         */
        public Builder parallelism(int threads) {
            if (threads < 1) throw new IllegalArgumentException("parallelism must be >= 1");
            this.parallelism = threads;
            return this;
        }

        /**
         * Glob pattern for filtering files in a directory scan.
         * Examples: {@code "*.edi"}, {@code "*.{edi,x12}"}, {@code "booking_*"}.
         *
         * <p>Pattern is matched against the filename only, not the full path.
         * Default: all files.
         */
        public Builder filePattern(String glob) {
            this.filePattern = glob;
            return this;
        }

        /**
         * Whether to walk sub-directories when calling {@code verifyFolder}.
         * Default: {@code false} — only files in the immediate directory.
         */
        public Builder recursive(boolean recursive) {
            this.recursive = recursive;
            return this;
        }

        /**
         * Sets test data values for dynamic field resolution.
         * Keys are referenced in rule YAML as {@code source: testData.keyName}.
         */
        public Builder testData(Map<String, Object> testData) {
            if (testData != null) this.testData.putAll(testData);
            return this;
        }

        /**
         * Adds a single test data entry.
         */
        public Builder testData(String key, Object value) {
            this.testData.put(key, value);
            return this;
        }

        /**
         * Overrides the default {@link ComparisonConfig}.
         * Use {@link ComparisonConfig#load(String)} to load from a custom properties file.
         */
        public Builder config(ComparisonConfig config) {
            this.config = config;
            return this;
        }

        /** Builds and returns the configured {@link EdiVerifier}. */
        public EdiVerifier build() {
            if (ruleSet == null) {
                throw new IllegalStateException("A template must be specified via .template(...)");
            }
            return new EdiVerifier(this);
        }

        // --- Convenience terminals: .build() is implicit ---

        /** Builds and immediately verifies a single file. */
        public FileResult verify(String filePath) throws IOException {
            return build().verify(filePath);
        }

        /** Builds and immediately verifies a single file. */
        public FileResult verify(Path filePath) throws IOException {
            return build().verify(filePath);
        }

        /** Builds and immediately verifies all files in a directory. */
        public BatchResult verifyFolder(String directory) throws IOException {
            return build().verifyFolder(directory);
        }

        /** Builds and immediately verifies all files in a directory. */
        public BatchResult verifyFolder(Path directory) throws IOException {
            return build().verifyFolder(directory);
        }

        /** Builds and immediately verifies a list of files. */
        public BatchResult verifyFiles(List<Path> files) {
            return build().verifyFiles(files);
        }
    }
}
