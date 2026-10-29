package ai.speckitty.charter.deployable;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * The container profile's rules on keys outside {@code charter.container.*} (ADR-0041 decisions 2, 4, 5 and 6), checked
 * when the context starts: a fixed port, an existing absolute configuration root, and neither the development flag
 * nor a worktree's project configuration directory. A breach fails the start; the message names the property and
 * never its value. Without the profile nothing here runs, so the local start is unchanged.
 */
@Configuration(proxyBeanMethods = false)
@Profile(ContainerProperties.PROFILE)
public class ContainerStartupCheck implements InitializingBean {

    static final String PORT_PROPERTY = "server.port";
    static final String CONFIG_ROOT_PROPERTY = "charter.config-root";
    static final String PROJECT_CONFIG_DIR_PROPERTY = "charter.project-config-dir";

    /** Spring Boot's own default when {@code server.port} is not set. */
    private static final int DEFAULT_PORT = 8080;

    private final Environment environment;

    /**
     * For Spring, which instantiates the configuration class.
     *
     * @param environment the environment the service starts with
     */
    public ContainerStartupCheck(Environment environment) {
        this.environment = environment;
    }

    /**
     * Checks the environment the container starts with.
     *
     * @throws IllegalStateException when a rule is broken, naming the property
     */
    @Override
    public void afterPropertiesSet() {
        requireFixedPort(environment);
        requireExistingConfigRoot(environment);
        refuse(environment, GuardWiring.INSECURE_PROPERTY, "the service never starts without a token in a container");
        refuse(
                environment,
                PROJECT_CONFIG_DIR_PROPERTY,
                "a shared container has no single worktree whose configuration joins the fingerprint");
    }

    private static void requireFixedPort(Environment environment) {
        if (environment.getProperty(PORT_PROPERTY, Integer.class, DEFAULT_PORT) == 0) {
            throw new IllegalStateException(PORT_PROPERTY
                    + " must be a fixed port under the container profile, not 0: a random port cannot be published");
        }
    }

    private static void requireExistingConfigRoot(Environment environment) {
        String root = environment.getProperty(CONFIG_ROOT_PROPERTY, "");
        if (root.isBlank()) {
            throw new IllegalStateException(
                    CONFIG_ROOT_PROPERTY + " is required under the container profile: name the mounted directory");
        }
        if (!isExistingAbsoluteDirectory(root)) {
            throw new IllegalStateException(CONFIG_ROOT_PROPERTY
                    + " must name an existing absolute directory under the container profile: is the volume mounted?");
        }
    }

    private static boolean isExistingAbsoluteDirectory(String root) {
        try {
            Path path = Path.of(root);
            return path.isAbsolute() && Files.isDirectory(path);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private static void refuse(Environment environment, String property, String reason) {
        if (environment.containsProperty(property)) {
            throw new IllegalStateException(property + " must not be set under the container profile: " + reason);
        }
    }
}
