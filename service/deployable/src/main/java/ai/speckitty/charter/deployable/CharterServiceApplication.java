package ai.speckitty.charter.deployable;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * The charter service: Spring wiring only. It listens on loopback ({@code server.address} in
 * {@code application.yaml}); the port and credentials arrive with the launcher work.
 */
@SpringBootApplication
@EnableConfigurationProperties(CharterProperties.class)
@Import(DeployableConfiguration.class)
public class CharterServiceApplication {

    /** For Spring, which instantiates the configuration class; nothing else constructs it. */
    protected CharterServiceApplication() {}

    /**
     * Starts the service.
     *
     * @param args Spring Boot command-line arguments, for example {@code --charter.storage.root=/path/to/packs}
     */
    public static void main(String[] args) {
        SpringApplication.run(CharterServiceApplication.class, args);
    }
}
