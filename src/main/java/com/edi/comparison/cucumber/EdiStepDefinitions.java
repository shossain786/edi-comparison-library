package com.edi.comparison.cucumber;

import com.edi.comparison.batch.BatchResult;

import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.datatable.DataTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Ready-to-use Cucumber step definitions for EDI inbound/outbound verification.
 *
 * <p>These steps cover the full integration test pattern:
 * <ol>
 *   <li>Drop one or more EDI input files into an inbound folder.</li>
 *   <li>Wait for the system under test to process them.</li>
 *   <li>Verify that the expected outbound files were produced and match the YAML template.</li>
 * </ol>
 *
 * <h3>Wiring into your project</h3>
 * <p>Cucumber picks up these step definitions automatically via classpath scanning.
 * No extra registration is needed — just add the library to your project's dependencies.
 *
 * <h3>Sample feature</h3>
 * <pre>
 * Scenario: Booking creates a 304 IFT outbound
 *   When I drop below input files
 *     | SI_Min_Edifact | CU2100_Inbound |
 *   And I wait for 30 Sec
 *   Then I verify below outbounds
 *     | Template Name  | Location                   |
 *     | SI_Min_Edifact | CA2000_304IFT_Min_Archieve  |
 * </pre>
 *
 * <h3>Inbound DataTable format (no header row)</h3>
 * <pre>
 * | &lt;file-name&gt; | &lt;location-alias&gt; |
 * </pre>
 * <ul>
 *   <li>Column 1 — EDI file name (in {@code src/test/resources/testdata/})</li>
 *   <li>Column 2 — inbound folder alias (from {@code config/edi-locations.yaml})</li>
 * </ul>
 *
 * <h3>Outbound DataTable format (with header row)</h3>
 * <pre>
 * | Template Name | Location |
 * | &lt;template&gt;    | &lt;alias&gt;  |
 * </pre>
 * <ul>
 *   <li>{@code Template Name} — YAML template name (in {@code src/test/resources/templates/})</li>
 *   <li>{@code Location} — outbound folder alias (from {@code config/edi-locations.yaml})</li>
 * </ul>
 *
 * <h3>Report</h3>
 * <p>An HTML report is automatically written to {@code target/edi-reports/} after each scenario.
 * The report is named after the scenario.
 */
public class EdiStepDefinitions {

    private static final Logger log = LoggerFactory.getLogger(EdiStepDefinitions.class);

    /** Where HTML reports are written after each scenario. */
    private static final String REPORT_OUTPUT_DIR = "target/edi-reports";

    private final EdiTestContext ctx;

    /**
     * PicoContainer injects a fresh {@link EdiTestContext} per scenario.
     *
     * @param ctx scenario-scoped context
     */
    public EdiStepDefinitions(EdiTestContext ctx) {
        this.ctx = ctx;
    }

    // =========================================================================
    // Step: drop input files
    // =========================================================================

    /**
     * Drops one or more EDI files into their respective inbound folders.
     *
     * <p>DataTable format (no header):
     * <pre>
     * | SI_Min_Edifact | CU2100_Inbound |
     * | OtherFile      | AnotherAlias   |
     * </pre>
     *
     * @param table headerless DataTable — each row is [fileName, locationAlias]
     */
    @When("I drop below input files")
    public void dropInputFiles(DataTable table) {
        List<List<String>> rows = table.asLists(String.class);
        log.info("Dropping {} input file(s)", rows.size());
        for (List<String> row : rows) {
            if (row.size() < 2) {
                throw new IllegalArgumentException(
                        "Each row in the inbound table must have exactly 2 columns: "
                        + "[fileName, locationAlias]. Got: " + row);
            }
            String fileName      = row.get(0).trim();
            String locationAlias = row.get(1).trim();
            ctx.dropFile(fileName, locationAlias);
        }
    }

    // =========================================================================
    // Step: wait
    // =========================================================================

    /**
     * Pauses the scenario for the given number of seconds.
     * Use this to allow the system under test time to process the dropped files.
     *
     * @param seconds number of seconds to wait
     * @throws InterruptedException if the thread is interrupted while sleeping
     */
    @And("I wait for {int} Sec")
    public void waitForSeconds(int seconds) throws InterruptedException {
        log.info("Waiting {} second(s) for processing...", seconds);
        Thread.sleep(seconds * 1000L);
    }

    // =========================================================================
    // Step: verify outbounds
    // =========================================================================

    /**
     * Verifies outbound EDI files produced after the scenario started.
     *
     * <p>DataTable format (with header row):
     * <pre>
     * | Template Name  | Location                  |
     * | SI_Min_Edifact | CA2000_304IFT_Min_Archieve |
     * </pre>
     *
     * <p>For each row, the step:
     * <ol>
     *   <li>Resolves the {@code Location} alias to a real directory path.</li>
     *   <li>Finds files in that directory newer than when the scenario started.</li>
     *   <li>Loads the YAML template from {@code templates/{Template Name}.yaml}.</li>
     *   <li>Runs {@link com.edi.comparison.EdiVerifier} against each new file.</li>
     * </ol>
     *
     * <p>All failures are collected; the scenario is failed at the end via {@link #generateReport}.
     *
     * @param table DataTable with headers {@code Template Name} and {@code Location}
     */
    @Then("I verify below outbounds")
    public void verifyOutbounds(DataTable table) {
        List<Map<String, String>> rows = table.asMaps(String.class, String.class);
        log.info("Verifying {} outbound row(s)", rows.size());
        for (Map<String, String> row : rows) {
            String templateName  = row.get("Template Name");
            String locationAlias = row.get("Location");
            if (templateName == null || locationAlias == null) {
                throw new IllegalArgumentException(
                        "Outbound table must have columns 'Template Name' and 'Location'. "
                        + "Got columns: " + row.keySet());
            }
            ctx.verifyOutbound(templateName.trim(), locationAlias.trim());
        }
    }

    // =========================================================================
    // After hook: report + assertion
    // =========================================================================

    /**
     * Runs after every scenario. Generates an HTML report and fails the scenario
     * if any verification errors or outbound differences were detected.
     *
     * @param scenario Cucumber scenario metadata (name, status, etc.)
     */
    @After
    public void generateReport(Scenario scenario) {
        BatchResult batchResult = ctx.buildBatchResult();

        if (batchResult.getTotal() > 0) {
            try {
                String reportPath = batchResult.generateReport(REPORT_OUTPUT_DIR);
                log.info("EDI verification report written to: {}", reportPath);
                scenario.log("EDI report: " + reportPath);
            } catch (IOException e) {
                log.warn("Failed to write EDI report: {}", e.getMessage());
            }
        }

        if (ctx.hasFailures()) {
            StringBuilder message = new StringBuilder("EDI verification failed:\n");
            for (String failure : ctx.getFailures()) {
                message.append("  - ").append(failure).append("\n");
            }
            scenario.log(message.toString());
            throw new AssertionError(message.toString());
        }

        log.info("Scenario '{}' — all EDI verifications passed ({})",
                scenario.getName(), batchResult.getSummary());
    }
}
