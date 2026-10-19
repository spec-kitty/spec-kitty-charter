package ai.speckitty.charter.service;

import ai.speckitty.charter.rest.ContextController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * The charter service: Spring wiring only. It listens on loopback ({@code server.address} in
 * {@code application.yaml}); the port and credentials arrive with the launcher work.
 */
@SpringBootApplication(scanBasePackageClasses = {CharterServiceApplication.class, ContextController.class})
@EnableConfigurationProperties(CharterProperties.class)
public class CharterServiceApplication {

    /** For Spring, which instantiates the configuration class; nothing else constructs it. */
    protected CharterServiceApplication() {}

    /**
     * Starts the service.
     *
     * @param args Spring Boot command-line arguments, for example {@code --charter.packs[0].path=/packs/built-in}
     */
    public static void main(String[] args) {
        SpringApplication.run(CharterServiceApplication.class, args);
    }
}
