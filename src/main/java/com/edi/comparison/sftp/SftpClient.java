package com.edi.comparison.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Thin JSch-based SFTP client for EDI file drop and outbound file collection.
 *
 * <p>One instance per Cucumber scenario — opened in {@link
 * com.edi.comparison.cucumber.EdiTestContext} and closed via {@code close()} in the
 * {@code @After} hook. The connection is reused across all drop/verify steps within
 * a scenario.
 *
 * <h3>Operations</h3>
 * <ul>
 *   <li>{@link #upload(Path, String)} — drop a local file onto the SFTP server</li>
 *   <li>{@link #listFilesModifiedAfter(String, Instant)} — find new outbound files</li>
 *   <li>{@link #downloadToString(String)} — read a remote file as a UTF-8 string</li>
 * </ul>
 *
 * <p>Host key verification is disabled ({@code StrictHostKeyChecking=no}) because
 * test environment servers often do not have known-hosts entries. Do not use this
 * setting in production code.
 */
public class SftpClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(SftpClient.class);

    // SFTP error code for "no such file or directory"
    private static final int SFTP_ERR_NO_SUCH_FILE = 2;

    private final SftpConfig config;
    private Session session;
    private ChannelSftp channel;
    private boolean closed = false;

    /**
     * Opens an SFTP connection using the given config.
     *
     * @param config connection parameters (host, port, username, password)
     * @throws IOException if the connection or authentication fails
     */
    public SftpClient(SftpConfig config) throws IOException {
        this.config = config;
        connect();
    }

    // =========================================================================
    // Public operations
    // =========================================================================

    /**
     * Uploads a local file to the remote SFTP path.
     * Creates the remote parent directory if it does not exist.
     *
     * @param localFile  path to the local file to upload
     * @param remotePath full remote destination path (e.g. {@code /edi/inbound/cu2100/file.edi})
     * @throws IOException on connection or transfer error
     */
    public void upload(Path localFile, String remotePath) throws IOException {
        ensureRemoteDir(remoteParent(remotePath));
        try {
            channel.put(localFile.toString(), remotePath, ChannelSftp.OVERWRITE);
            log.info("Uploaded '{}' → sftp://{}{}",
                    localFile.getFileName(), config.getHost(), remotePath);
        } catch (SftpException e) {
            throw new IOException(
                    "SFTP upload failed: " + localFile + " → " + remotePath
                    + ": " + e.getMessage(), e);
        }
    }

    /**
     * Lists all regular files in a remote directory whose last-modified time is
     * strictly after {@code since}.
     *
     * <p>Only the immediate directory is scanned (non-recursive).
     *
     * @param remoteDir remote directory path (e.g. {@code /edi/outbound/ca2000/archive})
     * @param since     lower bound (exclusive) on last-modified time
     * @return list of matching files, sorted by name; empty if none found
     * @throws IOException if the directory cannot be listed
     */
    @SuppressWarnings("unchecked")
    public List<SftpRemoteFile> listFilesModifiedAfter(String remoteDir, Instant since)
            throws IOException {
        try {
            Vector<ChannelSftp.LsEntry> entries = channel.ls(remoteDir);
            List<SftpRemoteFile> result = new ArrayList<>();
            for (ChannelSftp.LsEntry entry : entries) {
                if (entry.getAttrs().isDir()) continue;
                // getMTime() returns Unix epoch seconds (int)
                Instant modified = Instant.ofEpochSecond(entry.getAttrs().getMTime());
                if (modified.isAfter(since)) {
                    String remotePath = remoteDir.endsWith("/")
                            ? remoteDir + entry.getFilename()
                            : remoteDir + "/" + entry.getFilename();
                    result.add(new SftpRemoteFile(entry.getFilename(), remotePath, modified));
                }
            }
            result.sort((a, b) -> a.name().compareTo(b.name()));
            log.debug("Found {} new file(s) in sftp://{}{}",
                    result.size(), config.getHost(), remoteDir);
            return result;
        } catch (SftpException e) {
            throw new IOException(
                    "Cannot list SFTP directory '" + remoteDir + "' on "
                    + config.getHost() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Downloads a remote file and returns its content as a UTF-8 string.
     *
     * @param remotePath full remote path of the file to download
     * @return file content as a string
     * @throws IOException if the file cannot be read
     */
    public String downloadToString(String remotePath) throws IOException {
        try (InputStream is = channel.get(remotePath)) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            log.debug("Downloaded {} bytes from sftp://{}{}",
                    content.length(), config.getHost(), remotePath);
            return content;
        } catch (SftpException e) {
            throw new IOException(
                    "Cannot download '" + remotePath + "' from "
                    + config.getHost() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Closes the SFTP channel and SSH session.
     * Safe to call multiple times.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
            log.info("Disconnected from SFTP: {}", config);
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private void connect() throws IOException {
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            session.setPassword(config.getPassword());
            // Disable strict host key checking for test environments
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "password");
            session.setTimeout(30_000);
            session.connect();

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            log.info("Connected to SFTP: {}", config);
        } catch (JSchException e) {
            throw new IOException(
                    "Cannot connect to SFTP " + config + ": " + e.getMessage(), e);
        }
    }

    /**
     * Creates a remote directory if it does not already exist.
     * Silently ignores errors if the directory was created concurrently.
     */
    private void ensureRemoteDir(String remoteDir) {
        if (remoteDir == null || remoteDir.isBlank() || remoteDir.equals("/")) return;
        try {
            channel.stat(remoteDir);
        } catch (SftpException statEx) {
            if (statEx.id == SFTP_ERR_NO_SUCH_FILE) {
                try {
                    channel.mkdir(remoteDir);
                    log.debug("Created remote directory: {}", remoteDir);
                } catch (SftpException mkdirEx) {
                    // Created concurrently by another process — safe to ignore
                    log.debug("Remote directory already exists (concurrent create): {}", remoteDir);
                }
            }
        }
    }

    /** Returns the parent directory of a remote path, or "/" for top-level paths. */
    private String remoteParent(String remotePath) {
        int idx = remotePath.lastIndexOf('/');
        return idx > 0 ? remotePath.substring(0, idx) : "/";
    }
}
