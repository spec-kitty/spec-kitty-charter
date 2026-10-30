package ai.speckitty.charter.deployable;

import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.packs.infra.api.WorktreeGuard;
import ai.speckitty.charter.packs.infra.api.WorktreeToken;
import jakarta.servlet.DispatcherType;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers the {@link WorktreeGuard} and decides whether the service may start at all (ADR-0011, D9). There is no
 * way to start without the guard, and none to start it open by accident:
 *
 * <ul>
 *   <li>the token is {@code charter.worktree-token}, which the launcher passes in the environment as
 *       {@code CHARTER_WORKTREE_TOKEN}. Under the {@code container} profile a file in the secrets directory,
 *       {@code /run/secrets/charter.worktree-token} by default, is the other form, and the refusals name both
 *       (ADR-0041 decision 3);
 *   <li>the start is refused when the token is blank or shorter than 32 characters, or when it comes from the
 *       command line or a system property, where other processes can read it. That includes the route through
 *       {@code spring.application.json} given on the command line or as a system property, judged on the keys Boot
 *       unpacks from it, not on its text; the same JSON in the
 *       {@code SPRING_APPLICATION_JSON} environment variable is accepted;
 *   <li>the start is refused when there is no token, unless {@code charter.insecure-no-token} is exactly
 *       {@code true}, a development flag that logs a warning at every start. The guard's browser checks are on
 *       either way;
 *   <li>the guard is registered at the highest precedence for every request dispatch, so it runs before anything that
 *       reads a file, and a direct request for the error page is guarded like any other.
 * </ul>
 *
 * The token is never put in a message or a log line.
 */
@Configuration(proxyBeanMethods = false)
public class GuardWiring {

    static final String TOKEN_PROPERTY = "charter.worktree-token";
    static final String INSECURE_PROPERTY = "charter.insecure-no-token";
    static final int MIN_TOKEN_LENGTH = 32;

    private static final Log LOG = LogFactory.getLog(GuardWiring.class);

    /** The property sources a process other than the service can read: its argument vector and its system properties. */
    private static final Set<String> READABLE_SOURCES =
            Set.of("commandLineArgs", "springApplicationCommandLineArgs", "systemProperties");

    private static final String TOKEN_NAME = "charterworktreetoken";
    private static final String APPLICATION_JSON_NAME = "springapplicationjson";
    private static final String UNPACKED_JSON_SOURCE = "spring.application.json";

    /** For Spring, which instantiates the configuration class. */
    public GuardWiring() {}

    /**
     * The guard and its registration.
     *
     * @param environment the environment the token is read from
     * @param info the use case that knows the worktree id the refusals report
     * @param mapper the application's JSON mapper, resolved when a refusal is first written
     * @return the registration of the guard at the highest precedence, for request dispatches only
     * @throws IllegalStateException when the token is missing, blank, too short or from the command line or a system
     *     property; the message names the property and never the token
     */
    @Bean
    FilterRegistrationBean<WorktreeGuard> worktreeGuardRegistration(
            ConfigurableEnvironment environment, ServiceInfoQuery info, ObjectProvider<ObjectMapper> mapper) {
        FilterRegistrationBean<WorktreeGuard> registration =
                new FilterRegistrationBean<>(new WorktreeGuard(configuredToken(environment), info, mapper::getObject));
        registration.setName("worktreeGuard");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
        registration.addUrlPatterns("/*");
        return registration;
    }

    /** The token the guard enforces, or {@code null} for the development flag; fails the start otherwise. */
    private static @Nullable WorktreeToken configuredToken(ConfigurableEnvironment environment) {
        refuseReadableSources(environment);
        boolean insecure =
                "true".equals(environment.getProperty(INSECURE_PROPERTY, "").strip());
        String token = environment.getProperty(TOKEN_PROPERTY);
        if (token == null) {
            if (!insecure) {
                throw new IllegalStateException(missingTokenMessage(environment));
            }
            LOG.warn("The service starts WITHOUT a worktree token (" + INSECURE_PROPERTY
                    + " is set): any local process can read this worktree's charter. Development only.");
            return null;
        }
        if (token.isBlank()) {
            throw new IllegalStateException(blankTokenMessage(environment));
        }
        if (token.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalStateException(
                    TOKEN_PROPERTY + " is too short; it must be at least " + MIN_TOKEN_LENGTH + " characters");
        }
        if (insecure) {
            LOG.warn(INSECURE_PROPERTY + " is set but a worktree token is configured; the token is enforced");
        }
        return WorktreeToken.of(token);
    }

    private static void refuseReadableSources(ConfigurableEnvironment environment) {
        boolean jsonFromReadableSource = false;
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (READABLE_SOURCES.contains(source.getName()) && source instanceof EnumerablePropertySource<?> readable) {
                if (namesToken(readable.getPropertyNames())) {
                    throw unreadableSourceRefusal(environment);
                }
                jsonFromReadableSource |= namesApplicationJson(readable.getPropertyNames());
            }
        }
        // Boot's own unpacking has run: the judgement is on the parsed keys, whatever the text's order or escapes.
        PropertySource<?> unpacked = environment.getPropertySources().get(UNPACKED_JSON_SOURCE);
        if (jsonFromReadableSource
                && unpacked instanceof EnumerablePropertySource<?> json
                && namesToken(json.getPropertyNames())) {
            throw unreadableSourceRefusal(environment);
        }
    }

    private static IllegalStateException unreadableSourceRefusal(ConfigurableEnvironment environment) {
        String accepted =
                inContainer(environment) ? containerSources(environment) : "the environment (CHARTER_WORKTREE_TOKEN)";
        return new IllegalStateException(TOKEN_PROPERTY + " must come from " + accepted
                + ", not from the command line or a system property, where other processes can read it");
    }

    private static String missingTokenMessage(ConfigurableEnvironment environment) {
        if (inContainer(environment)) {
            return TOKEN_PROPERTY + " is required under the container profile: set " + containerSources(environment);
        }
        return TOKEN_PROPERTY + " is required: set CHARTER_WORKTREE_TOKEN in the environment (for development only, "
                + INSECURE_PROPERTY + "=true starts the service without one)";
    }

    private static String blankTokenMessage(ConfigurableEnvironment environment) {
        String message = TOKEN_PROPERTY + " is blank; it must be at least " + MIN_TOKEN_LENGTH + " characters";
        return inContainer(environment)
                ? message + "; an empty CHARTER_WORKTREE_TOKEN hides a mounted token file"
                : message;
    }

    private static boolean inContainer(ConfigurableEnvironment environment) {
        return environment.matchesProfiles(ContainerProperties.PROFILE);
    }

    /** Where a container takes the token from: the env var, or the file in its secrets directory. A path, no secret. */
    private static String containerSources(ConfigurableEnvironment environment) {
        String dir = environment.getProperty(
                ContainerProperties.SECRETS_DIR_PROPERTY, ContainerProperties.DEFAULT_SECRETS_DIR);
        String trimmed = dir.endsWith("/") ? dir.substring(0, dir.length() - 1) : dir;
        return "CHARTER_WORKTREE_TOKEN in the environment or the file " + trimmed + "/charter.worktree-token";
    }

    private static boolean namesApplicationJson(String[] names) {
        for (String name : names) {
            if (APPLICATION_JSON_NAME.equals(normalized(name))) {
                return true;
            }
        }
        return false;
    }

    private static String normalized(String text) {
        return text.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    /** Whether a name is {@code charter.worktree-token} in any spelling: case, dots, dashes and underscores aside. */
    private static boolean namesToken(String[] names) {
        for (String name : names) {
            if (TOKEN_NAME.equals(normalized(name))) {
                return true;
            }
        }
        return false;
    }
}
