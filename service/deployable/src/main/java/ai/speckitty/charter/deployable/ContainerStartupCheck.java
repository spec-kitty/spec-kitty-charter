package ai.speckitty.charter.deployable;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.boot.env.ConfigTreePropertySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

/**
 * The container profile's rules on keys outside {@code charter.container.*} (ADR-0041 decisions 2, 4, 5 and 6), checked
 * when the context starts: a fixed port, an existing absolute configuration root, and neither the development flag
 * nor a worktree's project configuration directory. The secrets directory (decision 3) must be an absolute directory
 * other than the root, and a config tree may set only the token and {@code charter.container.*} keys (not the secrets
 * directory itself), so a shared or misplaced mount cannot reconfigure the service; the service-account tree Kubernetes
 * mounts under {@code /run/secrets/kubernetes.io} is left alone. A breach fails the start; the message names the
 * property, the directory or the file, and never a value. Without the profile nothing here runs, so the local start is
 * unchanged, and lazy initialization does not skip it.
 */
@Configuration(proxyBeanMethods = false)
@Profile(ContainerProperties.PROFILE)
public class ContainerStartupCheck implements InitializingBean {

    static final String PORT_PROPERTY = "server.port";
    static final String CONFIG_ROOT_PROPERTY = "charter.config-root";
    static final String PROJECT_CONFIG_DIR_PROPERTY = "charter.project-config-dir";

    /** The namespace a config tree may set, besides the token. */
    private static final String CONTAINER_PREFIX = "charter.container.";

    /** The service-account files Kubernetes mounts under {@code /var/run/secrets}, often {@code /run/secrets}. */
    private static final String KUBERNETES_PREFIX = "kubernetes.io.";

    /** Spring Boot's own default when {@code server.port} is not set. */
    private static final int DEFAULT_PORT = 8080;

    private final ConfigurableEnvironment environment;

    /**
     * For Spring, which instantiates the configuration class.
     *
     * @param environment the environment the service starts with
     */
    public ContainerStartupCheck(ConfigurableEnvironment environment) {
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
        requireAbsoluteSecretsDirectory(environment);
        requireOwnKeysInConfigTrees(environment);
    }

    /**
     * Keeps this check eager when {@code spring.main.lazy-initialization} is set, so no setting can skip it.
     *
     * @return the filter that excludes this check from lazy initialization
     */
    @Bean
    static LazyInitializationExcludeFilter containerStartupCheckIsEager() {
        return LazyInitializationExcludeFilter.forBeanTypes(ContainerStartupCheck.class);
    }

    private static void requireAbsoluteSecretsDirectory(Environment environment) {
        String dir = environment.getProperty(
                ContainerProperties.SECRETS_DIR_PROPERTY, ContainerProperties.DEFAULT_SECRETS_DIR);
        if (absoluteNonRoot(dir).isEmpty()) {
            throw new IllegalStateException(ContainerProperties.SECRETS_DIR_PROPERTY
                    + " must name an absolute directory other than the root under the container profile");
        }
    }

    private static Optional<Path> absoluteNonRoot(String dir) {
        try {
            Path path = Path.of(dir);
            return path.isAbsolute() && path.getParent() != null ? Optional.of(path) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /** Names the files, never their content: a config tree's value is the secret itself. */
    private static void requireOwnKeysInConfigTrees(ConfigurableEnvironment environment) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof ConfigTreePropertySource tree) {
                List<String> foreign = Arrays.stream(tree.getPropertyNames())
                        .filter(name -> !isOwnKey(name))
                        .sorted()
                        .toList();
                if (!foreign.isEmpty()) {
                    throw new IllegalStateException("the config tree " + tree.getSource()
                            + " may set only " + GuardWiring.TOKEN_PROPERTY + " and " + CONTAINER_PREFIX
                            + "* under the container profile: remove " + String.join(", ", foreign));
                }
            }
        }
    }

    /** The token and the container namespace, but not the secrets directory, which a tree cannot redirect. */
    private static boolean isOwnKey(String name) {
        if (name.startsWith(KUBERNETES_PREFIX) || GuardWiring.TOKEN_PROPERTY.equals(name)) {
            return true;
        }
        return name.startsWith(CONTAINER_PREFIX) && !ContainerProperties.SECRETS_DIR_PROPERTY.equals(name);
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
