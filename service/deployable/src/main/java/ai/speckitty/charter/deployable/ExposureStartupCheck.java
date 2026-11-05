package ai.speckitty.charter.deployable;

import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

/**
 * The rules on what the process exposes, checked under both profiles when the context starts (ADR-0041 decisions 4
 * and 7):
 *
 * <ul>
 *   <li>without the {@code container} profile, {@code server.address} must be {@code localhost} or a loopback IP
 *       literal, never a name looked up in DNS: the local service never listens beyond this machine. Unset or blank
 *       is refused, because the server then binds every interface. Under the profile any address binds, loopback
 *       included (a sidecar, a test); its fixed port is {@link ContainerStartupCheck}'s rule;
 *   <li>no management server of its own ({@code management.server.port} or {@code .address}): it would answer outside
 *       the request guard;
 *   <li>the management endpoints stay where the guard expects them ({@code /actuator}), only {@code health} is
 *       exposed over HTTP, and health details, components and sanitized values stay {@code never}: an env var could
 *       otherwise widen what an unauthenticated health check, or a token holder, reads;
 *   <li>no health group is configured and the liveness and readiness probe groups stay off, since the health answer
 *       would list them; JMX stays off and exposes nothing;
 *   <li>under the container profile, {@code spring.mvc.log-request-details} is refused unless it is plainly off: it
 *       logs request headers, the bearer token among them.
 * </ul>
 *
 * A breach fails the start, and the message names the property, never a value. This check reads the environment
 * only, so it holds whatever the classpath. The profile is read when the service starts, not declared with
 * {@code @Profile} (ADR-0046 decision 5), and lazy initialization does not skip it.
 */
@Configuration(proxyBeanMethods = false)
public class ExposureStartupCheck implements InitializingBean {

    static final String ADDRESS_PROPERTY = "server.address";
    static final String MANAGEMENT_PORT_PROPERTY = "management.server.port";
    static final String MANAGEMENT_ADDRESS_PROPERTY = "management.server.address";
    static final String BASE_PATH_PROPERTY = "management.endpoints.web.base-path";
    static final String EXPOSURE_PROPERTY = "management.endpoints.web.exposure.include";
    static final String LOG_REQUEST_DETAILS_PROPERTY = "spring.mvc.log-request-details";
    static final String HEALTH_GROUPS_PROPERTY = "management.endpoint.health.group";
    static final String PROBES_PROPERTY = "management.endpoint.health.probes.enabled";
    static final String JMX_PROPERTY = "spring.jmx.enabled";
    static final String JMX_EXPOSURE_PROPERTY = "management.endpoints.jmx.exposure.include";

    /** Each must stay {@code never} when it is set: they decide what a health check or an endpoint reveals. */
    static final List<String> NEVER_PROPERTIES = List.of(
            "management.endpoint.health.show-details",
            "management.endpoint.health.show-components",
            "management.endpoint.env.show-values",
            "management.endpoint.configprops.show-values");

    private static final String BASE_PATH = "/actuator";
    private static final String HEALTH = "health";
    private static final String NEVER = "never";

    /** The values Spring binds to {@code false}; any other value, {@code 1} and {@code on} included, is on. */
    private static final Set<String> OFF_VALUES = Set.of("false", "off", "no", "0");

    private final ConfigurableEnvironment environment;

    /**
     * For Spring, which instantiates the configuration class.
     *
     * @param environment the environment the service starts with
     */
    public ExposureStartupCheck(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    /**
     * Checks the environment the service starts with.
     *
     * @throws IllegalStateException when a rule is broken, naming the property
     */
    @Override
    public void afterPropertiesSet() {
        boolean container = environment.matchesProfiles(ContainerProperties.PROFILE);
        if (!container) {
            requireLoopbackBind(environment);
        }
        refuse(
                environment,
                MANAGEMENT_PORT_PROPERTY,
                "a management server of its own answers outside the request guard");
        refuse(
                environment,
                MANAGEMENT_ADDRESS_PROPERTY,
                "a management server of its own answers outside the request guard");
        String basePath = environment.getProperty(BASE_PATH_PROPERTY);
        if (basePath != null && !BASE_PATH.equals(basePath.strip())) {
            throw new IllegalStateException(BASE_PATH_PROPERTY
                    + " must be /actuator when it is set: the request guard exempts only /actuator/health");
        }
        requireHealthOnly(environment);
        for (String property : NEVER_PROPERTIES) {
            requireValue(environment, property, NEVER, "it would reveal more than up or down (ADR-0041 decision 7)");
        }
        refuseHealthGroups(environment);
        refuseJmx(environment);
        if (isOn(environment, PROBES_PROPERTY)) {
            throw new IllegalStateException(PROBES_PROPERTY
                    + " must be false: the probe groups would be listed in the health answer (ADR-0041 decision 7)");
        }
        if (container && logsRequestDetails(environment)) {
            throw new IllegalStateException(LOG_REQUEST_DETAILS_PROPERTY
                    + " must be false under the container profile: it logs request headers, the token among them");
        }
    }

    /**
     * Keeps this check eager when {@code spring.main.lazy-initialization} is set, so no setting can skip it.
     *
     * @return the filter that excludes this check from lazy initialization
     */
    @Bean
    static LazyInitializationExcludeFilter exposureStartupCheckIsEager() {
        return LazyInitializationExcludeFilter.forBeanTypes(ExposureStartupCheck.class);
    }

    private static void requireLoopbackBind(Environment environment) {
        String address = environment.getProperty(ADDRESS_PROPERTY, "").strip();
        if (address.isEmpty() || !isLoopback(address)) {
            throw new IllegalStateException(ADDRESS_PROPERTY
                    + " must be a loopback address without the container profile: the local service never listens"
                    + " beyond this machine (ADR-0041 decision 4)");
        }
    }

    /**
     * {@code localhost} or a loopback IP literal. No name is looked up: a name whose DNS answer is loopback today may
     * not be tomorrow, and the start would wait on DNS.
     */
    private static boolean isLoopback(String address) {
        if ("localhost".equalsIgnoreCase(address)) {
            return true;
        }
        try {
            return InetAddress.ofLiteral(address).isLoopbackAddress();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Off only when Spring would bind it to {@code false}; {@code 1}, {@code on} and {@code yes} turn it on. */
    private static boolean logsRequestDetails(Environment environment) {
        return isOn(environment, LOG_REQUEST_DETAILS_PROPERTY);
    }

    private static boolean isOn(Environment environment, String property) {
        String value = environment.getProperty(property);
        return value != null && !OFF_VALUES.contains(value.strip().toLowerCase(Locale.ROOT));
    }

    /** No health group at all: the health answer lists every group's name, so it would say more than up or down. */
    private static void refuseHealthGroups(Environment environment) {
        if (!Binder.get(environment)
                .bind(HEALTH_GROUPS_PROPERTY, Bindable.mapOf(String.class, String.class))
                .orElse(Map.of())
                .isEmpty()) {
            throw new IllegalStateException(HEALTH_GROUPS_PROPERTY
                    + " must not be set: the health answer would list the group (ADR-0041 decision 7)");
        }
    }

    /** JMX stays off and exposes nothing, so health is never answered outside the request guard. */
    private static void refuseJmx(Environment environment) {
        if (isOn(environment, JMX_PROPERTY)) {
            throw new IllegalStateException(
                    JMX_PROPERTY + " must be false: management endpoints answer over HTTP only (ADR-0041 decision 7)");
        }
        if (!Binder.get(environment)
                .bind(JMX_EXPOSURE_PROPERTY, Bindable.listOf(String.class))
                .orElse(List.of())
                .isEmpty()) {
            throw new IllegalStateException(JMX_EXPOSURE_PROPERTY
                    + " must not be set: management endpoints answer over HTTP only (ADR-0041 decision 7)");
        }
    }

    /** Bound as a list, so a comma-separated value and an indexed one ({@code ..._INCLUDE_0_}) are both judged. */
    private static void requireHealthOnly(Environment environment) {
        List<String> exposed = Binder.get(environment)
                .bind(EXPOSURE_PROPERTY, Bindable.listOf(String.class))
                .orElse(List.of(HEALTH));
        if (exposed.size() != 1 || !HEALTH.equals(exposed.getFirst().strip().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(EXPOSURE_PROPERTY
                    + " must be health when it is set: only health is exposed over HTTP" + " (ADR-0041 decision 7)");
        }
    }

    private static void refuse(Environment environment, String property, String reason) {
        if (environment.containsProperty(property)) {
            throw new IllegalStateException(property + " must not be set: " + reason);
        }
    }

    private static void requireValue(Environment environment, String property, String expected, String reason) {
        String value = environment.getProperty(property);
        if (value != null && !expected.equals(value.strip().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(property + " must be " + expected + " when it is set: " + reason);
        }
    }
}
