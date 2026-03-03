package com.edi.comparison.cucumber;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;

/**
 * TestNG runner for the EDI verification Cucumber scenarios.
 *
 * <p>Drop this class (or a copy of it) into your own test project and adjust the
 * {@code features} and {@code glue} paths as needed. No other wiring is required —
 * the step definitions and PicoContainer DI are discovered automatically.
 *
 * <h3>Running from Maven</h3>
 * <pre>
 * mvn test -Dtest=EdiCucumberRunner
 * </pre>
 *
 * <h3>Running a single tag</h3>
 * <pre>
 * mvn test -Dtest=EdiCucumberRunner -Dcucumber.filter.tags="@smoke"
 * </pre>
 *
 * <h3>Reports</h3>
 * <p>HTML reports are generated per scenario to {@code target/edi-reports/} by the
 * {@link EdiStepDefinitions} {@code @After} hook. The Cucumber pretty/HTML plugins
 * below additionally produce a summary report under {@code target/cucumber-reports/}.
 */
@CucumberOptions(
        features = "src/test/resources/features",
        glue     = "com.edi.comparison.cucumber",
        plugin   = {
                "pretty",
                "html:target/cucumber-reports/cucumber.html",
                "json:target/cucumber-reports/cucumber.json"
        },
        tags     = "@edi"
)
public class EdiCucumberRunner extends AbstractTestNGCucumberTests {
    // TestNG discovers and runs scenarios via AbstractTestNGCucumberTests.
    // No additional code required.
}
