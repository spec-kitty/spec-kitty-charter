package ai.speckitty.charter.deployable;

import java.util.ArrayList;
import java.util.List;
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

    /** The command-line option that names the configuration root ({@code charter.config-root}). */
    static final String CONFIG_ROOT_OPTION = "--configRootLocation";

    /**
     * Starts the service.
     *
     * @param args Spring Boot command-line arguments, for example {@code --configRootLocation /srv/charter}
     */
    public static void main(String[] args) {
        SpringApplication.run(CharterServiceApplication.class, joinConfigRootOption(args));
    }

    /**
     * Spring reads an option only in its {@code --name=value} form; this also accepts
     * {@code --configRootLocation <path>} by joining the two arguments. Every other argument passes unchanged.
     *
     * @param args the command-line arguments
     * @return the arguments with {@code --configRootLocation <path>} written as {@code --configRootLocation=<path>}
     * @throws IllegalArgumentException when {@code --configRootLocation} is the last argument
     */
    static String[] joinConfigRootOption(String[] args) {
        List<String> joined = new ArrayList<>();
        int i = 0;
        while (i < args.length) {
            if (!CONFIG_ROOT_OPTION.equals(args[i])) {
                joined.add(args[i]);
                i += 1;
            } else if (i + 1 < args.length) {
                joined.add(CONFIG_ROOT_OPTION + "=" + args[i + 1]);
                i += 2;
            } else {
                throw new IllegalArgumentException(CONFIG_ROOT_OPTION + " needs a path");
            }
        }
        return joined.toArray(String[]::new);
    }
}
