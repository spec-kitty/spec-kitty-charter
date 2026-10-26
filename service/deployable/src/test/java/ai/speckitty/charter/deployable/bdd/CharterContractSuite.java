package ai.speckitty.charter.deployable.bdd;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Runs the contract scenarios of contracts/charter/scenarios against the service. Only scenarios tagged
 * {@code @contract} run, so a feature file placed there is not picked up before it is bound and tagged; scenarios
 * tagged {@code @WIP} describe a surface the contract does not have yet and are left out.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("scenarios")
@IncludeTags("contract")
@ExcludeTags("WIP")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "ai.speckitty.charter.deployable.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, summary")
public class CharterContractSuite {}
