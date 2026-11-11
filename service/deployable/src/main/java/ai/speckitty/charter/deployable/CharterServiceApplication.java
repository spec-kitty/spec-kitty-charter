package ai.speckitty.charter.deployable;

import ai.speckitty.charter.kernel.config.StartupRefusal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * The charter service: Spring wiring only. It listens on loopback ({@code server.address} in
 * {@code application.yaml}); the port and credentials arrive with the launcher work. What its native executable needs
 * beyond what Spring AOT finds is in {@link CharterRuntimeHints} (ADR-0046).
 */
@SpringBootApplication
@EnableConfigurationProperties(CharterProperties.class)
@Import(DeployableConfiguration.class)
@ImportRuntimeHints(CharterRuntimeHints.class)
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
        String[] joined;
        try {
            joined = joinConfigRootOption(args);
        } catch (StartupRefusal refused) {
            // Before Spring runs, so no failure analyzer reports it: the one line, and the exit code of a failed start.
            System.err.println(refused.getMessage());
            System.exit(1);
            return;
        }
        SpringApplication.run(CharterServiceApplication.class, joined);
    }

    /**
     * Spring reads an option only in its {@code --name=value} form; this also accepts
     * {@code --configRootLocation <path>} by joining the two arguments. Every other argument passes unchanged.
     *
     * @param args the command-line arguments
     * @return the arguments with {@code --configRootLocation <path>} written as {@code --configRootLocation=<path>}
     * @throws StartupRefusal when {@code --configRootLocation} is the last argument
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
                throw new StartupRefusal(CONFIG_ROOT_OPTION + " needs a path: --configRootLocation <path>");
            }
        }
        return joined.toArray(String[]::new);
    }
}
