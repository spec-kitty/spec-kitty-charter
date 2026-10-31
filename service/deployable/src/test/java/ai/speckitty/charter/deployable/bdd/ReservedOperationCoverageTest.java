package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * The 501 pins of {@code activations.feature} and the operations the contract marks {@code x-reserved} are one to
 * one (ADR-0020 D8): every reserved operation is pinned by a scenario that calls it, and every pin calls a reserved
 * operation. A reserved operation without a pin, or a pin whose operation was built (its {@code x-reserved} removed)
 * but whose scenario was not, fails here.
 */
class ReservedOperationCoverageTest {

    private static final String OPENAPI = "contract/charter/openapi.yaml";
    private static final String FEATURE = "scenarios/activations.feature";
    private static final Pattern REQUEST =
            Pattern.compile("^\\s*(?:When|And) I (?:send )?(GET|PUT|POST|DELETE|PATCH) \"([^\"?]+)(?:\\?[^\"]*)?\"");
    private static final List<String> METHODS = List.of("get", "put", "post", "delete", "patch");

    private final ContractConformance contract = new ContractConformance();

    @Test
    void everyReservedOperationIsPinnedAndEveryPinCallsAReservedOperation() {
        assertThat(pinnedOperations())
                .as("the operations the 501 pins of %s call", FEATURE)
                .isNotEmpty()
                .isEqualTo(reservedOperations());
    }

    @Test
    void theActivationSetReservesThirteenOperations() {
        assertThat(reservedOperations()).hasSize(13);
    }

    private Set<String> reservedOperations() {
        Map<?, ?> paths = (Map<?, ?>) load(OPENAPI).get("paths");
        Set<String> reserved = new TreeSet<>();
        for (Object template : paths.keySet()) {
            for (String method : METHODS) {
                String concrete = String.valueOf(template).replaceAll("\\{[^}]+}", "x");
                contract.operation(method, concrete)
                        .filter(operation -> operation.pathTemplate().equals(template))
                        .filter(ContractConformance.DeclaredOperation::reserved)
                        .ifPresent(operation -> reserved.add(key(method, operation.pathTemplate())));
            }
        }
        return reserved;
    }

    private Set<String> pinnedOperations() {
        Set<String> pinned = new TreeSet<>();
        for (String line : text(FEATURE).lines().toList()) {
            Matcher request = REQUEST.matcher(line);
            if (!request.find()) {
                continue;
            }
            String method = request.group(1).toLowerCase(Locale.ROOT);
            String path = request.group(2);
            ContractConformance.DeclaredOperation operation = contract.operation(method, path)
                    .orElseThrow(() -> new AssertionError(
                            "the pin calls " + method + " " + path + ", which the contract does not declare"));
            pinned.add(key(method, operation.pathTemplate()));
        }
        return pinned;
    }

    private static String key(String method, Object template) {
        return method.toUpperCase(Locale.ROOT) + " " + template;
    }

    private static Map<?, ?> load(String resource) {
        return (Map<?, ?>) new Load(LoadSettings.builder().build()).loadFromString(text(resource));
    }

    private static String text(String resource) {
        try (InputStream in =
                ReservedOperationCoverageTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new AssertionError(resource + " is not on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
