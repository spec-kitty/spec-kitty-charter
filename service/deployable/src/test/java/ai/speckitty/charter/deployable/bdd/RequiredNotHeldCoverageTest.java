package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The read path stays reachable (plan D3, WP03 T016): registration and every write now refuse a {@code must_have} item
 * that the declaring pack's own closure does not hold ({@code preset_item_not_held}), so a composing read meets such a
 * pack only when it was stored before the rule, or seeded straight into storage by a scenario. The read still reports
 * the item as {@code requiredNotHeld} (A10, C31), and this test keeps that report asserted: it counts the contract
 * scenarios with a step that asserts {@code requiredNotHeld}, and fails when the count drops to zero.
 */
class RequiredNotHeldCoverageTest {

    /** A step line: a Gherkin keyword, then the step text. */
    private static final Pattern STEP = Pattern.compile("^\\s*(?:Given|When|Then|And|But)\\s");

    private static final Pattern SCENARIO = Pattern.compile("^\\s*(?:Scenario|Scenario Outline|Example):");

    @Test
    void atLeastOneContractScenarioAssertsRequiredNotHeld() throws IOException, URISyntaxException {
        List<String> asserting = asserting(scenarios());

        assertThat(asserting)
                .as("the contract scenarios that assert requiredNotHeld on a composing read")
                .isNotEmpty();
    }

    @Test
    void theCountSeesAStepAndNotADescription() {
        String feature = """
                Feature: f
                  Scenario: described only
                    requiredNotHeld is named in the description, not asserted
                    When I GET "/activations/a-1/skipped"
                  Scenario: asserted
                    When I GET "/activations/a-1/skipped"
                    And the field at "/report/requiredNotHeld" holds exactly the ids:
                """;

        assertThat(assertingIn("f.feature", feature)).containsExactly("f.feature: asserted");
    }

    private static Path scenarios() throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                        RequiredNotHeldCoverageTest.class.getClassLoader().getResource("scenarios"),
                        "the contract scenarios on the test classpath")
                .toURI());
    }

    private static List<String> asserting(Path root) throws IOException {
        List<String> asserting = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".feature"))
                    .sorted()
                    .toList()) {
                asserting.addAll(
                        assertingIn(root.relativize(file).toString(), Files.readString(file, StandardCharsets.UTF_8)));
            }
        }
        return asserting;
    }

    /** Each scenario of a feature with a step that names {@code requiredNotHeld}, as {@code <file>: <scenario>}. */
    private static List<String> assertingIn(String file, String feature) {
        List<String> asserting = new ArrayList<>();
        String scenario = null;
        boolean counted = false;
        for (String line : feature.split("\n", -1)) {
            if (SCENARIO.matcher(line).find()) {
                scenario = line.substring(line.indexOf(':') + 1).strip();
                counted = false;
            } else if (scenario != null && !counted && STEP.matcher(line).find() && line.contains("requiredNotHeld")) {
                asserting.add(file + ": " + scenario);
                counted = true;
            }
        }
        return asserting;
    }
}
