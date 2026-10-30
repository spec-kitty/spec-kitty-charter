package ai.speckitty.charter.deployable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.env.ConfigTreePropertySource;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

/**
 * The container profile's rules on keys outside {@code charter.container.*} (ADR-0041 decisions 2, 4, 5 and 6), checked
 * when the context starts: a fixed port, an existing absolute configuration root, and neither the development flag
 * nor a worktree's project configuration directory. The secrets directory (decision 3) may set only the token and
 * {@code charter.container.*} keys, and must be an absolute directory other than the root that only its owner can write
 * to, so a shared or misplaced mount cannot reconfigure the service. A breach fails the start; the message names the
 * property, the directory or the file, and never a value. Without the profile nothing here runs, so the local start is
 * unchanged.
 */
@Configuration(proxyBeanMethods = false)
@Profile(ContainerProperties.PROFILE)
public class ContainerStartupCheck implements InitializingBean {

    static final String PORT_PROPERTY = "server.port";
    static final String CONFIG_ROOT_PROPERTY = "charter.config-root";
    static final String PROJECT_CONFIG_DIR_PROPERTY = "charter.project-config-dir";

    static final String SECRETS_DIR_PROPERTY = "charter.container.secrets-dir";
    static final String DEFAULT_SECRETS_DIR = "/run/secrets";

    /** The namespace a file in the secrets directory may set, besides the token. */
    private static final String CONTAINER_PREFIX = "charter.container.";

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
        requireSafeSecretsDirectory(environment);
        requireOwnKeysInConfigTrees(environment);
    }

    private static void requireSafeSecretsDirectory(Environment environment) {
        String dir = environment.getProperty(SECRETS_DIR_PROPERTY, DEFAULT_SECRETS_DIR);
        Path path = absoluteNonRoot(dir)
                .orElseThrow(() -> new IllegalStateException(SECRETS_DIR_PROPERTY
                        + " must name an absolute directory other than the root under the container profile"));
        if (Files.isDirectory(path) && othersCanWrite(path)) {
            throw new IllegalStateException("the secrets directory " + path
                    + " must not be one others can write to under the container profile: anyone could plant a token");
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

    private static boolean othersCanWrite(Path dir) {
        PosixFileAttributeView view = Files.getFileAttributeView(dir, PosixFileAttributeView.class);
        if (view == null) {
            return false;
        }
        try {
            return view.readAttributes().permissions().contains(PosixFilePermission.OTHERS_WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Names the files, never their content: a config tree's value is the secret itself. */
    private static void requireOwnKeysInConfigTrees(ConfigurableEnvironment environment) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof ConfigTreePropertySource tree) {
                List<String> foreign = Arrays.stream(tree.getPropertyNames())
                        .filter(name -> !GuardWiring.TOKEN_PROPERTY.equals(name) && !name.startsWith(CONTAINER_PREFIX))
                        .sorted()
                        .toList();
                if (!foreign.isEmpty()) {
                    throw new IllegalStateException("the secrets directory " + tree.getSource()
                            + " may set only " + GuardWiring.TOKEN_PROPERTY + " and " + CONTAINER_PREFIX
                            + "* under the container profile: remove " + String.join(", ", foreign));
                }
            }
        }
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
