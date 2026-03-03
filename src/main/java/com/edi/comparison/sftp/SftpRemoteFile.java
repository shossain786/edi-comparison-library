package com.edi.comparison.sftp;

import java.time.Instant;

/**
 * Metadata for a file found on an SFTP server.
 *
 * <p>Returned by {@link SftpClient#listFilesModifiedAfter(String, Instant)}.
 *
 * @param name         filename only (no directory path)
 * @param remotePath   full remote path on the SFTP server
 * @param lastModified last-modified time reported by the SFTP server
 */
public record SftpRemoteFile(String name, String remotePath, Instant lastModified) {

    @Override
    public String toString() {
        return name + " [" + remotePath + ", modified=" + lastModified + "]";
    }
}
