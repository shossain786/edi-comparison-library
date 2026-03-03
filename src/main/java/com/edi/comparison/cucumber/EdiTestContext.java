package com.edi.comparison.cucumber;

import com.edi.comparison.EdiVerifier;
import com.edi.comparison.batch.BatchResult;
import com.edi.comparison.batch.FileResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Scenario-scoped context shared across Cucumber step definitions via PicoContainer.
 *
 * <p>PicoContainer creates a fresh instance of this class for every scenario,
 * so scenario state never leaks between tests. Just declare it as a constructor
 * parameter in your step definition class:
 *
 * <pre>
 * public class MySteps {
 *     private final EdiTestContext ctx;
 *
 *     public MySteps(EdiTestContext ctx) {
 *         this.ctx = ctx;
 *     }
 * }
 * </pre>
 *
 * <h3>What it does</h3>
 * <ul>
 *   <li>{@link #dropFile(String, String)} — copies a test-resource file to the inbound folder</li>
 *   <li>{@link #verifyOutbound(String, String)} — finds and verifies new outbound files</li>
 *   <li>{@link #buildBatchResult()} — aggregates all verification results for reporting</li>
 * </ul>
 *
 * <h3>File resolution</h3>
 * <p>Input files are looked up from the classpath under {@code testdata/}. The file name
 * can be provided with or without extension; common EDI extensions are tried automatically:
 * {@code .edi}, {@code .x12}, {@code .edifact}, {@code .txt}, {@code .xml}.
 *
 * <h3>Outbound detection</h3>
 * <p>Only files whose last-modified timestamp is <em>after</em> this context was constructed
 * (scenario start) are considered new outbound files. This prevents false matches from old
 * files left in the directory by earlier test runs.
 */
public class EdiTestContext {

    private static final Logger log = LoggerFactory.getLogger(EdiTestContext.class);

    /** Subdirectory under classpath root where test EDI files live. */
    static final String TEST_DATA_DIR = "testdata";

    /** Subdirectory under classpath root where YAML templates live. */
    static final String TEMPLATES_DIR = "templates";

    /** Extensions tried when looking up a test file by name without extension. */
    private static final String[] EDI_EXTENSIONS = { "", ".edi", ".x12", ".edifact", ".xml", ".txt" };

    private final LocationRegistry locationRegistry;
    private final Instant scenarioStartTime;
    private final List<FileResult> allResults = new ArrayList<>();
    private final List<String> scenarioFailures = new ArrayList<>();

    /**
     * Constructed by PicoContainer at the start of each scenario.
     * Uses the default location registry config ({@code config/edi-locations.yaml}).
     */
    public EdiTestContext() {
        this(new LocationRegistry());
    }

    /**
     * Constructed by PicoContainer with a custom location registry.
     * Useful when overriding the default config path in tests.
     */
    public EdiTestContext(LocationRegistry locationRegistry) {
        this.locationRegistry = locationRegistry;
        this.scenarioStartTime = Instant.now();
        log.debug("EdiTestContext created — scenario start time: {}", scenarioStartTime);
    }

    // =========================================================================
    // Step actions
    // =========================================================================

    /**
     * Drops (copies) a test EDI file into the inbound folder for the given location alias.
     *
     * <p>The file is looked up by {@code fileName} from the classpath directory
     * {@code testdata/}. If no extension is given, common EDI extensions are tried.
     *
     * @param fileName     name of the test file (with or without extension)
     * @param locationAlias alias for the inbound folder (from {@code config/edi-locations.yaml})
     * @throws RuntimeException if the file is not found or cannot be copied
     */
    public void dropFile(String fileName, String locationAlias) {
        Path sourceFile = findTestFile(fileName);
        Path targetDir  = locationRegistry.resolve(locationAlias);
        Path targetFile = targetDir.resolve(sourceFile.getFileName());

        try {
            Files.createDirectories(targetDir);
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Dropped '{}' → {}", fileName, targetFile);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to drop file '" + fileName + "' to " + targetDir + ": " + e.getMessage(), e);
        }
    }

    /**
     * Verifies outbound EDI files that appeared in the given location since scenario start.
     *
     * <p>Only files with a last-modified time strictly after {@link #scenarioStartTime}
     * are verified — this prevents stale files from previous runs causing false failures.
     *
     * <p>The YAML template is loaded from the classpath at
     * {@code templates/{templateName}.yaml}.
     *
     * @param templateName name of the YAML template (without the {@code .yaml} extension)
     * @param locationAlias alias for the outbound folder (from {@code config/edi-locations.yaml})
     */
    public void verifyOutbound(String templateName, String locationAlias) {
        Path outboundDir = locationRegistry.resolve(locationAlias);
        List<Path> newFiles = findNewFiles(outboundDir);

        if (newFiles.isEmpty()) {
            String msg = "No new outbound files found in '" + locationAlias
                    + "' (" + outboundDir + ") since scenario start (" + scenarioStartTime + ").";
            log.warn(msg);
            scenarioFailures.add(msg);
            return;
        }

        log.info("Verifying {} new outbound file(s) in '{}' against template '{}'",
                newFiles.size(), locationAlias, templateName);

        BatchResult result;
        try {
            result = EdiVerifier.withClasspath(TEMPLATES_DIR + "/" + templateName + ".yaml")
                    .verifyFiles(newFiles);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load template '" + templateName + "': " + e.getMessage(), e);
        }

        allResults.addAll(result.getFileResults());
        if (result.getFailed() > 0 || result.getErrored() > 0) {
            scenarioFailures.add(
                    "Outbound verification failed [template=" + templateName
                    + ", location=" + locationAlias + "]: " + result.getSummary());
        }
    }

    // =========================================================================
    // Result access
    // =========================================================================

    /**
     * Returns {@code true} if any step recorded a failure or verification error.
     */
    public boolean hasFailures() {
        return !scenarioFailures.isEmpty();
    }

    /**
     * Returns all failure messages collected during the scenario.
     */
    public List<String> getFailures() {
        return Collections.unmodifiableList(scenarioFailures);
    }

    /**
     * Returns all individual file results collected during the scenario.
     */
    public List<FileResult> getAllResults() {
        return Collections.unmodifiableList(allResults);
    }

    /**
     * Builds a {@link BatchResult} aggregating every file verified during this scenario.
     * Pass to {@link BatchResult#generateReport(String)} to produce an HTML report.
     */
    public BatchResult buildBatchResult() {
        return new BatchResult(new ArrayList<>(allResults), 0L);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Looks up a test file from the classpath under {@code testdata/}.
     * Tries the name as-is first, then appends each of the known EDI extensions.
     */
    private Path findTestFile(String fileName) {
        for (String ext : EDI_EXTENSIONS) {
            String resourcePath = TEST_DATA_DIR + "/" + fileName + ext;
            URL url = getClass().getClassLoader().getResource(resourcePath);
            if (url != null) {
                try {
                    return Paths.get(url.toURI());
                } catch (URISyntaxException e) {
                    // try next extension
                }
            }
        }
        throw new IllegalArgumentException(
                "Test file not found: '" + fileName + "'. "
                + "Place it under src/test/resources/" + TEST_DATA_DIR + "/ "
                + "with a .edi, .x12, .edifact, .xml, or .txt extension.");
    }

    /**
     * Returns files in the given directory whose last-modified time is after scenario start.
     * Only scans immediate children (depth 1), not subdirectories.
     */
    private List<Path> findNewFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            log.warn("Outbound directory does not exist: {}", directory);
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(directory, 1)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isNewerThanScenarioStart)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("Cannot scan outbound directory '{}': {}", directory, e.getMessage());
            return List.of();
        }
    }

    private boolean isNewerThanScenarioStart(Path path) {
        try {
            FileTime modified = Files.getLastModifiedTime(path);
            return modified.toInstant().isAfter(scenarioStartTime);
        } catch (IOException e) {
            log.warn("Cannot read last-modified time for '{}': {}", path, e.getMessage());
            return false;
        }
    }
}
