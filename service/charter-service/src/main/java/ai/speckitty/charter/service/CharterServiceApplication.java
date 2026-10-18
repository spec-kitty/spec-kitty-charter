package ai.speckitty.charter.service;

import ai.speckitty.charter.rest.ContextController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** The charter service: Spring wiring only. Loopback binding and credentials arrive with the launcher work. */
@SpringBootApplication(scanBasePackageClasses = {CharterServiceApplication.class, ContextController.class})
@EnableConfigurationProperties(CharterProperties.class)
public class CharterServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CharterServiceApplication.class, args);
    }
}
