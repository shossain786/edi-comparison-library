package com.edi.comparison.cucumber;

import com.edi.comparison.EdiVerifier;
import com.edi.comparison.batch.BatchResult;
import com.edi.comparison.batch.FileResult;
import com.edi.comparison.sftp.SftpClient;
import com.edi.comparison.sftp.SftpEnvironmentRegistry;
import com.edi.comparison.sftp.SftpRemoteFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Scenario-scoped context shared across Cucumber step definitions via PicoContainer.
 *
 * <p>PicoContainer creates a fresh instance for every scenario — state never leaks between
 * tests. Declare it as a constructor parameter in any step definition class to receive it:
 *
 * <pre>
 * public class MySteps {
 *     private final EdiTestContext ctx;
 *     public MySteps(EdiTestContext ctx) { this.ctx = ctx; }
 * }
 * </pre>
 *
 * <h3>Transport mode</h3>
 * <p>The context auto-detects whether to use SFTP or local filesystem:
 * <ul>
 *   <li><b>SFTP mode</b> — if {@code config/sftp-environments.yaml} is on the classpath,
 *       one SFTP connection is opened per scenario. Files are uploaded for drop and
 *       downloaded from the server for outbound verification.</li>
 *   <li><b>Local mode</b> — if no SFTP config is present, files are copied/scanned
 *       on the local filesystem. Useful for local development and CI without
 *       network access.</li>
 * </ul>
 *
 * <h3>Active environment</h3>
 * <p>In SFTP mode, the environment (beta/cvt/prod) is selected via:
 * <ol>
 *   <li>{@code -Dedi.env=cvt} JVM system property</li>
 *   <li>{@code EDI_ENV=cvt} OS environment variable</li>
 *   <li>{@code active} field in {@code sftp-environments.yaml}</li>
 * </ol>
 */
public class EdiTestContext implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(EdiTestContext.class);

    /** Classpath subdirectory containing test EDI input files. */
    static final String TEST_DATA_DIR = "testdata";

    /** Classpath subdirectory containing YAML verification templates. */
    static final String TEMPLATES_DIR = "templates";

    /** Extensions tried when looking up a test file without an explicit extension. */
    private static final String[] EDI_EXTENSIONS = { "", ".edi", ".x12", ".edifact", ".xml", ".txt" };

    private final LocationRegistry locationRegistry;
    private final SftpClient sftpClient;          // null in local filesystem mode
    private final Path tempDownloadDir;            // used only in SFTP mode
    private final Instant scenarioStartTime;
    private final List<FileResult> allResults = new ArrayList<>();
    private final List<String> scenarioFailures = new ArrayList<>();
    private boolean closed = false;

    /**
     * Constructed by PicoContainer at the start of each scenario.
     * Automatically connects to SFTP if {@code config/sftp-environments.yaml} is present.
     */
    public EdiTestContext() {
        this(new LocationRegistry(), new SftpEnvironmentRegistry());
    }

    /**
     * Constructor for testing or custom wiring.
     *
     * @param locationRegistry   alias → path registry
     * @param sftpEnvRegistry    SFTP environment config (may be unavailable)
     */
    public EdiTestContext(LocationRegistry locationRegistry,
                          SftpEnvironmentRegistry sftpEnvRegistry) {
        this.locationRegistry  = locationRegistry;
        this.scenarioStartTime = Instant.now();

        SftpClient client = null;
        Path tempDir = null;

        if (sftpEnvRegistry.isAvailable()) {
            try {
                client  = new SftpClient(sftpEnvRegistry.getActiveConfig());
                tempDir = Files.createTempDirectory("edi-sftp-");
                log.info("SFTP mode — environment: {}", sftpEnvRegistry.getActiveEnvironmentName());
            } catch (IOException e) {
                throw new RuntimeException(
                        "Cannot connect to SFTP for environment '"
                        + sftpEnvRegistry.getActiveEnvironmentName() + "': " + e.getMessage(), e);
            }
        } else {
            log.info("Local filesystem mode (no sftp-environments.yaml found on classpath)");
        }

        this.sftpClient     = client;
        this.tempDownloadDir = tempDir;
        log.debug("EdiTestContext created — scenario start: {}", scenarioStartTime);
    }

    // =========================================================================
    // Step actions
    // =========================================================================

    /**
     * Drops (uploads or copies) a test EDI file into the inbound folder for the alias.
     *
     * <p>The source file is located on the classpath under {@code testdata/}.
     * The name can be given with or without an extension; common EDI extensions are tried.
     *
     * @param fileName     test file name (with or without extension)
     * @param locationAlias alias for the inbound folder (from {@code config/edi-locations.yaml})
     */
    public void dropFile(String fileName, String locationAlias) {
        Path sourceFile = findTestFile(fileName);
        String targetFileName = sourceFile.getFileName().toString();

        if (sftpClient != null) {
            // SFTP mode — upload to remote server
            String remoteDir  = locationRegistry.resolveString(locationAlias);
            String remotePath = remoteDir.endsWith("/")
                    ? remoteDir + targetFileName
                    : remoteDir + "/" + targetFileName;
            try {
                sftpClient.upload(sourceFile, remotePath);
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to upload '" + fileName + "' to SFTP path "
                        + remotePath + ": " + e.getMessage(), e);
            }
        } else {
            // Local mode — copy to local directory
            Path targetDir  = locationRegistry.resolve(locationAlias);
            Path targetFile = targetDir.resolve(targetFileName);
            try {
                Files.createDirectories(targetDir);
                Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                log.info("Dropped '{}' → {}", fileName, targetFile);
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to copy '" + fileName + "' to " + targetDir
                        + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Verifies outbound EDI files that appeared in the given location since scenario start.
     *
     * <p>Only files with a last-modified timestamp strictly after {@link #scenarioStartTime}
     * are verified — stale files from earlier runs are ignored automatically.
     *
     * <p>The template is loaded from the classpath at
     * {@code templates/{templateName}.yaml}.
     *
     * @param templateName YAML template name without the {@code .yaml} extension
     * @param locationAlias alias for the outbound folder (from {@code config/edi-locations.yaml})
     */
    public void verifyOutbound(String templateName, String locationAlias) {
        List<Path> filesToVerify;

        if (sftpClient != null) {
            // SFTP mode — list remote, download to temp dir
            String remoteDir = locationRegistry.resolveString(locationAlias);
            filesToVerify = downloadNewFiles(remoteDir, locationAlias, templateName);
        } else {
            // Local mode — scan local directory
            Path outboundDir = locationRegistry.resolve(locationAlias);
            filesToVerify = findNewLocalFiles(outboundDir);
        }

        if (filesToVerify.isEmpty()) {
            String msg = "No new outbound files found in '" + locationAlias
                    + "' since scenario start (" + scenarioStartTime + ").";
            log.warn(msg);
            scenarioFailures.add(msg);
            return;
        }

        log.info("Verifying {} new outbound file(s) in '{}' against template '{}'",
                filesToVerify.size(), locationAlias, templateName);

        BatchResult result;
        try {
            result = EdiVerifier.withClasspath(TEMPLATES_DIR + "/" + templateName + ".yaml")
                    .verifyFiles(filesToVerify);
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

    /** Returns {@code true} if any step recorded a failure or verification error. */
    public boolean hasFailures() {
        return !scenarioFailures.isEmpty();
    }

    /** Returns all failure messages collected during the scenario. */
    public List<String> getFailures() {
        return Collections.unmodifiableList(scenarioFailures);
    }

    /** Returns all individual file results collected during the scenario. */
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
    // Lifecycle
    // =========================================================================

    /**
     * Closes the SFTP connection (if open) and deletes temporary download files.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (sftpClient != null) {
            sftpClient.close();
        }
        if (tempDownloadDir != null) {
            deleteTempDir(tempDownloadDir);
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Downloads SFTP files newer than scenario start into the temp directory
     * and returns their local paths for verification.
     */
    private List<Path> downloadNewFiles(String remoteDir, String locationAlias,
                                        String templateName) {
        List<SftpRemoteFile> remoteFiles;
        try {
            remoteFiles = sftpClient.listFilesModifiedAfter(remoteDir, scenarioStartTime);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Cannot list SFTP directory for alias '" + locationAlias
                    + "' (" + remoteDir + "): " + e.getMessage(), e);
        }

        if (remoteFiles.isEmpty()) {
            return List.of();
        }

        List<Path> localPaths = new ArrayList<>();
        for (SftpRemoteFile rf : remoteFiles) {
            Path tempFile = tempDownloadDir.resolve(rf.name());
            try {
                String content = sftpClient.downloadToString(rf.remotePath());
                Files.writeString(tempFile, content);
                localPaths.add(tempFile);
                log.debug("Downloaded '{}' to temp for verification", rf.name());
            } catch (IOException e) {
                log.warn("Cannot download '{}' from SFTP: {}", rf.remotePath(), e.getMessage());
                scenarioFailures.add(
                        "Cannot download outbound file '" + rf.name() + "': " + e.getMessage());
            }
        }
        return localPaths;
    }

    /** Scans a local directory for files newer than scenario start. */
    private List<Path> findNewLocalFiles(Path directory) {
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

    /**
     * Locates a test file by name from the classpath under {@code testdata/}.
     * Tries the name as-is first, then appends each known EDI extension.
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

    private void deleteTempDir(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                  .forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            log.debug("Could not fully clean up temp directory '{}': {}", dir, e.getMessage());
        }
    }
}
