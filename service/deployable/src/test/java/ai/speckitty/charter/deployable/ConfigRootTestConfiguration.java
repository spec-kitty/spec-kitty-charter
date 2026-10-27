package ai.speckitty.charter.deployable;

import ai.speckitty.charter.packs.infra.storage.ConfigRoot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The test variant of the storage layer's configuration root: a {@code @Primary} {@link ConfigRoot} built from the
 * repository's test resources ({@code src/test/resources/config-root}), copied into a fresh temporary directory so a
 * test that writes never touches the repository. The production {@code StorageConfiguration} binds the root from
 * {@code charter.config-root}; its pack locations follow whichever root is primary.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ConfigRootTestConfiguration {

    /** The classpath directory the root is copied from. */
    static final String RESOURCE = "config-root";

    /** For Spring, which instantiates the configuration class. */
    public ConfigRootTestConfiguration() {}

    /**
     * A copy of the test configuration root.
     *
     * @return the root of the copy
     */
    @Bean
    @Primary
    ConfigRoot testConfigRoot() {
        try {
            Path target = Files.createTempDirectory("charter-config-root");
            copy(source(), target);
            return new ConfigRoot(target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path source() {
        URL url = ConfigRootTestConfiguration.class.getClassLoader().getResource(RESOURCE);
        if (url == null) {
            throw new IllegalStateException("The test resource " + RESOURCE + " is missing from the classpath");
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void copy(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path from : walk.toList()) {
                Path to = target.resolve(source.relativize(from).toString());
                if (Files.isDirectory(from)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(from, to);
                }
            }
        }
    }
}
