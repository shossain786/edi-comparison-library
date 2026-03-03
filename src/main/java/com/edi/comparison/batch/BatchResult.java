package com.edi.comparison.batch;

import com.edi.comparison.model.Difference;
import com.edi.comparison.report.HtmlReportGenerator;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregated result of a batch EDI verification run.
 *
 * <p>Returned by {@link com.edi.comparison.EdiVerifier#verifyFolder} and
 * {@link com.edi.comparison.EdiVerifier#verifyFiles}.
 *
 * <p>Quick usage:
 * <pre>
 * BatchResult result = EdiVerifier.with("rules/template.yaml")
 *         .verifyFolder("output/files/");
 *
 * System.out.println(result.getSummary());
 * // → "500 files: 498 passed, 2 failed, 0 errored (12.3s)"
 *
 * result.assertAllPassed(); // throws AssertionError listing every failure
 * result.generateReport("target/reports/"); // writes combined HTML report
 * </pre>
 */
public class BatchResult {

    private final List<FileResult> fileResults;
    private final long totalTimeMs;

    public BatchResult(List<FileResult> fileResults, long totalTimeMs) {
        this.fileResults = Collections.unmodifiableList(fileResults);
        this.totalTimeMs = totalTimeMs;
    }

    /** Total number of files processed. */
    public int getTotal() {
        return fileResults.size();
    }

    /** Files that parsed successfully and satisfied all rules. */
    public int getPassed() {
        return (int) fileResults.stream().filter(FileResult::isPassed).count();
    }

    /** Files that parsed but had at least one rule violation. */
    public int getFailed() {
        return (int) fileResults.stream()
                .filter(r -> !r.isPassed() && !r.hasError())
                .count();
    }

    /** Files that could not be parsed due to IO or format errors. */
    public int getErrored() {
        return (int) fileResults.stream().filter(FileResult::hasError).count();
    }

    /** All individual file results. */
    public List<FileResult> getFileResults() {
        return fileResults;
    }

    /** Only the results that did not pass (failed + errored). */
    public List<FileResult> getFailedFiles() {
        return fileResults.stream()
                .filter(r -> !r.isPassed())
                .collect(Collectors.toList());
    }

    /** Wall-clock time to process the entire batch, in milliseconds. */
    public long getTotalTimeMs() {
        return totalTimeMs;
    }

    /**
     * One-line human-readable summary.
     *
     * <p>Example: {@code "500 files: 498 passed, 2 failed, 0 errored (12.3s)"}
     */
    public String getSummary() {
        return String.format("%d files: %d passed, %d failed, %d errored (%.1fs)",
                getTotal(), getPassed(), getFailed(), getErrored(), totalTimeMs / 1000.0);
    }

    /**
     * Asserts that every file in the batch passed verification.
     *
     * @throws AssertionError with a detailed list of every failing file
     */
    public void assertAllPassed() {
        List<FileResult> failures = getFailedFiles();
        if (failures.isEmpty()) {
            return;
        }
        StringBuilder msg = new StringBuilder("Batch verification failed: ");
        msg.append(getSummary()).append("\n\nFailed files:");
        for (FileResult f : failures) {
            msg.append("\n  ").append(f);
            if (!f.hasError() && f.getComparisonResult() != null) {
                f.getComparisonResult().getDifferences().stream().limit(5).forEach(d ->
                        msg.append("\n      ").append(d.getType()).append(": ").append(d.getDescription()));
                if (f.getDifferenceCount() > 5) {
                    msg.append("\n      ... and ").append(f.getDifferenceCount() - 5).append(" more");
                }
            }
        }
        throw new AssertionError(msg.toString());
    }

    /**
     * Generates a combined HTML report for all files in this batch.
     *
     * @param outputDir directory where the report file will be written
     * @return absolute path to the generated report file
     * @throws IOException if the report cannot be written
     */
    public String generateReport(String outputDir) throws IOException {
        HtmlReportGenerator generator = new HtmlReportGenerator();
        List<HtmlReportGenerator.ScenarioResult> scenarios = fileResults.stream()
                .filter(r -> r.getComparisonResult() != null)
                .map(r -> new HtmlReportGenerator.ScenarioResult(
                        r.getFileName(), r.getComparisonResult(), r.isPassed()))
                .collect(Collectors.toList());
        return generator.generateCombined(scenarios, outputDir);
    }

    /**
     * Returns the top-N most frequently occurring difference types across all files.
     *
     * <p>Useful for quickly spotting systematic issues:
     * <pre>
     * result.getMostCommonErrors(5).forEach((type, count) ->
     *     System.out.println(type + ": " + count + " occurrences"));
     * </pre>
     *
     * @param topN maximum number of entries to return
     * @return map of error-type name → occurrence count, ordered by count descending
     */
    public Map<String, Long> getMostCommonErrors(int topN) {
        return fileResults.stream()
                .filter(r -> r.getComparisonResult() != null)
                .flatMap(r -> r.getComparisonResult().getDifferences().stream())
                .collect(Collectors.groupingBy(d -> d.getType().name(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(topN)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    @Override
    public String toString() {
        return "BatchResult{" + getSummary() + "}";
    }
}
