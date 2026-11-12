package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.config.StartupRefusal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * What the process may expose (ADR-0041 decisions 4 and 7, issues #231 and #232): the local bind is loopback, no
 * management server answers outside the guard, only health is exposed and it reveals no details, and a container
 * never logs request details, the token in a header included, and keeps forwarded headers off.
 */
class ExposureStartupCheckTest {

    private static final String CONTAINER = "spring.profiles.active=" + ContainerProperties.PROFILE;
    private static final String LOOPBACK = "server.address=127.0.0.1";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ExposureStartupCheck.class);

    private void assertRefused(ApplicationContextRunner base, String property, String... values) {
        base.withPropertyValues(values)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseInstanceOf(StartupRefusal.class)
                        .rootCause()
                        .hasMessageStartingWith(property + " must"));
    }

    private void assertStarts(ApplicationContextRunner base, String... values) {
        base.withPropertyValues(values).run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"127.0.0.1", "127.0.0.2", "::1", "0:0:0:0:0:0:0:1", "localhost", "LOCALHOST", " 127.0.0.1 "})
    void withoutTheContainerProfileALoopbackAddressStarts(String address) {
        assertStarts(runner, "server.address=" + address);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "0.0.0.0",
                "::",
                "192.0.2.10",
                "10.0.0.5",
                "::ffff:10.0.0.1",
                "2001:db8::1",
                " ",
                "loopback.example",
                "localhost.localdomain"
            })
    void withoutTheContainerProfileANonLoopbackAddressFailsTheStartNamingTheProperty(String address) {
        assertRefused(runner, "server.address", "server.address=" + address);
    }

    @Test
    void withoutTheContainerProfileAnUnsetAddressFailsTheStartBecauseItBindsEveryInterface() {
        assertRefused(runner, "server.address");
    }

    @Test
    void theRefusalNeverEchoesTheAddress() {
        runner.withPropertyValues("server.address=192.0.2.10")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage(
                                "server.address must be a loopback address without the container profile: the local service"
                                        + " never listens beyond this machine (ADR-0041 decision 4)"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "127.0.0.1", "10.0.0.5"})
    void underTheContainerProfileAnyAddressBinds(String address) {
        assertStarts(runner, CONTAINER, "server.address=" + address);
    }

    @Test
    void underTheContainerProfileAnUnsetAddressStarts() {
        assertStarts(runner, CONTAINER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"management.server.port=9090", "management.server.address=127.0.0.1"})
    void aManagementServerOfItsOwnFailsTheStartUnderBothProfiles(String setting) {
        String property = setting.substring(0, setting.indexOf('='));
        assertRefused(runner, property, LOOPBACK, setting);
        assertRefused(runner, property, CONTAINER, setting);
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "health,env", "env", "health,info"})
    void anExposureBeyondHealthFailsTheStartUnderBothProfiles(String exposure) {
        String setting = ExposureStartupCheck.EXPOSURE_PROPERTY + "=" + exposure;
        assertRefused(runner, ExposureStartupCheck.EXPOSURE_PROPERTY, LOOPBACK, setting);
        assertRefused(runner, ExposureStartupCheck.EXPOSURE_PROPERTY, CONTAINER, setting);
    }

    @Test
    void anIndexedExposureIsJudgedToo() {
        assertRefused(
                runner,
                ExposureStartupCheck.EXPOSURE_PROPERTY,
                CONTAINER,
                ExposureStartupCheck.EXPOSURE_PROPERTY + "[0]=health",
                ExposureStartupCheck.EXPOSURE_PROPERTY + "[1]=env");
    }

    @ParameterizedTest
    @ValueSource(strings = {"health", "Health ", " HEALTH"})
    void anExposureOfHealthAloneStarts(String exposure) {
        assertStarts(runner, CONTAINER, ExposureStartupCheck.EXPOSURE_PROPERTY + "=" + exposure);
    }

    @Test
    void aMovedManagementBasePathFailsTheStart() {
        assertRefused(
                runner,
                ExposureStartupCheck.BASE_PATH_PROPERTY,
                CONTAINER,
                ExposureStartupCheck.BASE_PATH_PROPERTY + "=/manage");
        assertStarts(runner, CONTAINER, ExposureStartupCheck.BASE_PATH_PROPERTY + "=/actuator");
    }

    @Test
    void everyRevealingSettingOtherThanNeverFailsTheStartAndNeverStarts() {
        for (String property : ExposureStartupCheck.NEVER_PROPERTIES) {
            assertRefused(runner, property, CONTAINER, property + "=always");
            assertRefused(runner, property, LOOPBACK, property + "=when-authorized");
            assertStarts(runner, CONTAINER, property + "=never");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "1", "on", "yes", " true "})
    void underTheContainerProfileRequestDetailLoggingInAnySpellingFailsTheStart(String value) {
        assertRefused(
                runner,
                ExposureStartupCheck.LOG_REQUEST_DETAILS_PROPERTY,
                CONTAINER,
                ExposureStartupCheck.LOG_REQUEST_DETAILS_PROPERTY + "=" + value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "FALSE", "off", "no", "0"})
    void underTheContainerProfileRequestDetailLoggingOffStarts(String value) {
        assertStarts(runner, CONTAINER, ExposureStartupCheck.LOG_REQUEST_DETAILS_PROPERTY + "=" + value);
    }

    @Test
    void aConfiguredHealthGroupFailsTheStartBecauseTheAnswerWouldListIt() {
        String group = ExposureStartupCheck.HEALTH_GROUPS_PROPERTY + ".live";
        assertRefused(runner, ExposureStartupCheck.HEALTH_GROUPS_PROPERTY, CONTAINER, group + ".include=ping");
        assertRefused(runner, ExposureStartupCheck.HEALTH_GROUPS_PROPERTY, LOOPBACK, group + ".show-details=never");
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.jmx.enabled=true", "management.endpoints.jmx.exposure.include=health"})
    void jmxSwitchedOnOrExposedFailsTheStart(String setting) {
        String property = setting.substring(0, setting.indexOf('='));
        assertRefused(runner, property, CONTAINER, setting);
        assertRefused(runner, property, LOOPBACK, setting);
    }

    @Test
    void jmxLeftOffStarts() {
        assertStarts(runner, CONTAINER, "spring.jmx.enabled=false", "management.endpoints.jmx.exposure.exclude=*");
    }

    @Test
    void theManagementBasePathIsComparedWithItsCase() {
        assertRefused(
                runner,
                ExposureStartupCheck.BASE_PATH_PROPERTY,
                CONTAINER,
                ExposureStartupCheck.BASE_PATH_PROPERTY + "=/ACTUATOR");
    }

    @Test
    void withoutTheContainerProfileRequestDetailLoggingIsTheDevelopersChoice() {
        assertStarts(runner, LOOPBACK, ExposureStartupCheck.LOG_REQUEST_DETAILS_PROPERTY + "=true");
    }

    @Test
    void lazyInitializationDoesNotSkipTheCheck() {
        runner.withBean(LazyInitializationBeanFactoryPostProcessor.class)
                .withPropertyValues("server.address=0.0.0.0")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "1", "on"})
    void probeGroupsSwitchedOnFailTheStart(String value) {
        assertRefused(
                runner,
                ExposureStartupCheck.PROBES_PROPERTY,
                CONTAINER,
                ExposureStartupCheck.PROBES_PROPERTY + "=" + value);
        assertStarts(runner, CONTAINER, ExposureStartupCheck.PROBES_PROPERTY + "=false");
    }

    private static final String LEVEL = ExposureStartupCheck.LOGGING_LEVEL_PROPERTY;

    /** Every logger from the root down to the one that writes the raw request, and an upper-case spelling. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "root",
                "ROOT",
                "org",
                "org.apache",
                "org.apache.coyote",
                "org.apache.coyote.http11",
                "org.apache.coyote.http11.Http11InputBuffer"
            })
    void underTheContainerProfileTraceOnTheRequestLoggerOrAParentFailsTheStart(String logger) {
        assertRefused(runner, LEVEL, CONTAINER, LEVEL + "." + logger + "=TRACE");
        assertRefused(runner, LEVEL, CONTAINER, LEVEL + ".[" + logger + "]=trace");
        assertStarts(runner, LOOPBACK, LEVEL + "." + logger + "=TRACE");
    }

    @Test
    void theEnvironmentVariableSpellingOfTheLevelIsJudgedToo() {
        for (String variable :
                new String[] {"LOGGING_LEVEL_ROOT", "LOGGING_LEVEL_ORG_APACHE", "LOGGING_LEVEL_ORG_APACHE_COYOTE"}) {
            assertRefused(
                    runner.withInitializer(context -> context.getEnvironment()
                            .getPropertySources()
                            .addFirst(new SystemEnvironmentPropertySource(
                                    "systemEnvironment", Map.of(variable, "TRACE")))),
                    LEVEL,
                    CONTAINER);
        }
    }

    @Test
    void aLoggingGroupAtTraceThatHoldsTheCoyoteLoggerFailsTheStart() {
        assertRefused(runner, LEVEL, CONTAINER, "logging.group.wire=org.apache.coyote,org.xnio", LEVEL + ".wire=TRACE");
    }

    @Test
    void aNearerLoggerAboveTraceWinsOverATraceParent() {
        assertStarts(
                runner,
                CONTAINER,
                LEVEL + ".root=TRACE",
                LEVEL + ".org.apache.coyote=DEBUG",
                LEVEL + ".org.springframework.web=TRACE");
        assertStarts(runner, CONTAINER, LEVEL + ".org.apache.coyote=TRACE", LEVEL + ".org.apache.coyote.http11=DEBUG");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"org.apache.coyote.http11=DEBUG", "org.apache.coyote=DEBUG", "org.springframework.web=TRACE"})
    void debugOnTheCoyoteLoggersAndTraceElsewhereStart(String setting) {
        assertStarts(runner, CONTAINER, LEVEL + "." + setting);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%{Authorization}i %r", "%{authorization}i", "%h %{AUTHORIZATION}i", "%{ Authorization }i"})
    void underTheContainerProfileAnAccessLogPatternWithTheAuthorizationHeaderFailsTheStart(String pattern) {
        assertRefused(
                runner,
                ExposureStartupCheck.ACCESS_LOG_PATTERN_PROPERTY,
                CONTAINER,
                "server.tomcat.accesslog.enabled=true",
                ExposureStartupCheck.ACCESS_LOG_PATTERN_PROPERTY + "=" + pattern);
        assertStarts(runner, LOOPBACK, ExposureStartupCheck.ACCESS_LOG_PATTERN_PROPERTY + "=" + pattern);
    }

    @Test
    void underTheContainerProfileAnAccessLogPatternOfOtherHeadersStarts() {
        assertStarts(runner, CONTAINER, ExposureStartupCheck.ACCESS_LOG_PATTERN_PROPERTY + "=%h %r %{User-Agent}i %s");
    }

    @ParameterizedTest
    @ValueSource(strings = {"native", "NATIVE", "framework", " framework "})
    void underTheContainerProfileForwardedHeadersSwitchedOnFailTheStart(String strategy) {
        assertRefused(
                runner,
                ExposureStartupCheck.FORWARD_HEADERS_PROPERTY,
                CONTAINER,
                ExposureStartupCheck.FORWARD_HEADERS_PROPERTY + "=" + strategy);
        assertStarts(runner, LOOPBACK, ExposureStartupCheck.FORWARD_HEADERS_PROPERTY + "=" + strategy);
        assertStarts(runner, CONTAINER, ExposureStartupCheck.FORWARD_HEADERS_PROPERTY + "=none");
    }

    @Test
    void theManagementDiscoveryPageSwitchedOnFailsTheStart() {
        assertRefused(
                runner,
                ExposureStartupCheck.DISCOVERY_PROPERTY,
                CONTAINER,
                ExposureStartupCheck.DISCOVERY_PROPERTY + "=true");
        assertRefused(
                runner,
                ExposureStartupCheck.DISCOVERY_PROPERTY,
                LOOPBACK,
                ExposureStartupCheck.DISCOVERY_PROPERTY + "=on");
        assertStarts(runner, CONTAINER, ExposureStartupCheck.DISCOVERY_PROPERTY + "=false");
    }

    @Test
    void anEndpointMappedToAnotherPathFailsTheStart() {
        assertRefused(
                runner,
                ExposureStartupCheck.PATH_MAPPING_PROPERTY,
                CONTAINER,
                ExposureStartupCheck.PATH_MAPPING_PROPERTY + ".health=status");
        assertRefused(
                runner,
                ExposureStartupCheck.PATH_MAPPING_PROPERTY,
                LOOPBACK,
                ExposureStartupCheck.PATH_MAPPING_PROPERTY + ".health=health");
    }
}
