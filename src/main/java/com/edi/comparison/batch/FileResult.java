package com.edi.comparison.batch;

import com.edi.comparison.model.ComparisonResult;

import java.nio.file.Path;

/**
 * Result of verifying a single EDI file.
 *
 * <p>A {@code FileResult} is either:
 * <ul>
 *   <li><b>Successful parse + pass</b> — file was parsed and all rules passed</li>
 *   <li><b>Successful parse + fail</b> — file was parsed but differences were found</li>
 *   <li><b>Parse/IO error</b> — file could not be read or parsed</li>
 * </ul>
 *
 * <p>Returned by {@link com.edi.comparison.EdiVerifier#verify(Path)}.
 */
public class FileResult {

    private final Path filePath;
    private final ComparisonResult comparisonResult;
    private final Exception error;
    private final long processingTimeMs;

    private FileResult(Path filePath, ComparisonResult comparisonResult,
                       Exception error, long processingTimeMs) {
        this.filePath = filePath;
        this.comparisonResult = comparisonResult;
        this.error = error;
        this.processingTimeMs = processingTimeMs;
    }

    public static FileResult ofSuccess(Path path, ComparisonResult result, long ms) {
        return new FileResult(path, result, null, ms);
    }

    public static FileResult ofError(Path path, Exception e, long ms) {
        return new FileResult(path, null, e, ms);
    }

    /**
     * Returns true if the file was parsed successfully and all rules passed.
     */
    public boolean isPassed() {
        return error == null && comparisonResult != null && comparisonResult.isSuccess();
    }

    /**
     * Returns true if a parse or IO error occurred (file could not be processed).
     */
    public boolean hasError() {
        return error != null;
    }

    /**
     * Returns just the filename (without directory path).
     */
    public String getFileName() {
        return filePath.getFileName().toString();
    }

    /**
     * Returns the full path to the file.
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Returns the comparison result, or {@code null} if a parse/IO error occurred.
     */
    public ComparisonResult getComparisonResult() {
        return comparisonResult;
    }

    /**
     * Returns the error that prevented parsing, or {@code null} if parsing succeeded.
     */
    public Exception getError() {
        return error;
    }

    /**
     * Returns the number of differences found, or 0 if an error occurred.
     */
    public int getDifferenceCount() {
        return comparisonResult != null ? comparisonResult.getDifferenceCount() : 0;
    }

    /**
     * Returns how long this file took to process in milliseconds.
     */
    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    /**
     * Asserts this file passed verification.
     *
     * @throws AssertionError if the file failed or had a parse error
     */
    public void assertPassed() {
        if (hasError()) {
            throw new AssertionError(
                    "Failed to process file '" + getFileName() + "': " + error.getMessage(), error);
        }
        if (!isPassed()) {
            throw new AssertionError(
                    "Verification failed for '" + getFileName() + "': "
                    + getDifferenceCount() + " difference(s) found.\n"
                    + comparisonResult);
        }
    }

    @Override
    public String toString() {
        if (hasError()) {
            return getFileName() + " [ERROR: " + error.getMessage() + "]";
        }
        return getFileName() + (isPassed() ? " [PASSED]" : " [FAILED: " + getDifferenceCount() + " diff(s)]");
    }
}
