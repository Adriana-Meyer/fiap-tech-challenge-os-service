package com.fiap.workshop.management.bdd;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Runs the Gherkin scenarios in {@code src/test/resources/features} with the
 * rest of the tests ({@code mvn verify}). The glue and the report formats are
 * in {@code junit-platform.properties}.
 */
@Suite
@SuiteDisplayName("BDD: saga do orçamento")
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
public class RunCucumberTest {
}
