package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The scenario validator agrees with the canonical {@code ActivationConfig} JSON Schema on the shared corpus (#125,
 * research D-2): every document of {@code contracts/offering/corpus/ActivationConfig/valid} conforms to the contract's
 * mirror, and every one of {@code invalid} does not, for exactly the reasons {@code invalid-reasons.json} gives it
 * (#295): each violation says, at a reason's location, what that reason's keyword refuses, about its member where it
 * names one, and each reason has one such violation. networknt gives the same verdicts on both schemas
 * ({@code ActivationConfigSchemaParityTest} in {@code service/packs}); this is the third validator.
 */
class ActivationConfigConformanceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MIRROR = "charter/schemas/ActivationConfig.yaml";

    private final ContractConformance contract = new ContractConformance();

    @TestFactory
    List<DynamicTest> theScenarioValidatorGivesTheCorpusVerdicts() throws IOException {
        Path corpus = corpus();
        JsonNode reasons =
                JSON.readTree(Files.readString(corpus.resolve("invalid-reasons.json"), StandardCharsets.UTF_8));
        try (Stream<Path> invalid = Files.list(corpus.resolve("invalid"))) {
            assertThat(reasons.propertyNames())
                    .as("invalid-reasons.json gives a reason to exactly the invalid documents")
                    .containsExactlyInAnyOrderElementsOf(
                            invalid.map(file -> file.getFileName().toString()).toList());
        }
        List<DynamicTest> tests = new ArrayList<>();
        for (String verdict : List.of("valid", "invalid")) {
            try (Stream<Path> files = Files.list(corpus.resolve(verdict))) {
                for (Path file : files.sorted().toList()) {
                    String name = verdict + "/" + file.getFileName();
                    tests.add(DynamicTest.dynamicTest(name, () -> {
                        List<String> violations = contract.schemaViolations(
                                MIRROR, JSON.readTree(Files.readString(file, StandardCharsets.UTF_8)));
                        if (verdict.equals("valid")) {
                            assertThat(violations).as(name).isEmpty();
                        } else {
                            List<JsonNode> expected = reasons.path(
                                            file.getFileName().toString())
                                    .valueStream()
                                    .toList();
                            assertThat(expected)
                                    .as("invalid-reasons.json gives %s a reason", name)
                                    .isNotEmpty();
                            assertThat(violations)
                                    .as("%s fails for exactly its reasons %s", name, expected)
                                    .hasSameSizeAs(expected)
                                    .allMatch(violation ->
                                            expected.stream().anyMatch(reason -> saysTheReason(violation, reason)));
                            assertThat(expected)
                                    .as("each reason of %s has its violation in %s", name, violations)
                                    .allMatch(reason -> violations.stream()
                                            .anyMatch(violation -> saysTheReason(violation, reason)));
                        }
                    }));
                }
            }
        }
        assertThat(tests).hasSizeGreaterThanOrEqualTo(20);
        return tests;
    }

    /**
     * Whether a violation of the scenario validator gives a reason of {@code invalid-reasons.json}: it is at the
     * reason's location (a JSON Pointer, written here as the validator's {@code $.a[0].b}) and says what the reason's
     * keyword refuses, naming the reason's member where it names one.
     */
    private static boolean saysTheReason(String violation, JsonNode reason) {
        String at = path(reason.path("at").asString());
        String member = reason.has("member") ? reason.get("member").asString() : "";
        return switch (reason.path("keyword").asString()) {
            case "required" -> violation.equals(at + " lacks the required member '" + member + "'");
            case "additionalProperties" -> violation.equals(at + " has the undeclared member '" + member + "'");
            case "type" -> violation.startsWith(at + " is ") && violation.contains(", not [");
            case "enum" -> violation.startsWith(at + " is ") && violation.contains(", not one of ");
            case "pattern" -> violation.startsWith(at + " '") && violation.contains("' does not match ");
            case "minLength" -> violation.startsWith(at + " is shorter than ");
            case "minItems" -> violation.startsWith(at + " holds fewer than ");
            case "maximum" -> violation.startsWith(at + " is above ");
            default -> throw new AssertionError("no reason is known for the keyword " + reason.path("keyword"));
        };
    }

    /**
     * A JSON Pointer as the scenario validator writes a location: {@code /packs/0/packId} is
     * {@code $.packs[0].packId}. A pointer token of digits is taken as an array index; the activation document has
     * no member named by digits.
     */
    private static String path(String pointer) {
        StringBuilder path = new StringBuilder("$");
        for (String token :
                pointer.isEmpty() ? new String[0] : pointer.substring(1).split("/", -1)) {
            String name = token.replace("~1", "/").replace("~0", "~");
            boolean index = !name.isEmpty() && name.chars().allMatch(Character::isDigit);
            path.append(index ? "[" + name + "]" : "." + name);
        }
        return path.toString();
    }

    private static Path corpus() {
        URL url =
                ActivationConfigConformanceTest.class.getClassLoader().getResource("offering/corpus/ActivationConfig");
        if (url == null) {
            throw new AssertionError("the ActivationConfig corpus is not on the test classpath");
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }
}
