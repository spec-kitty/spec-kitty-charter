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
import tools.jackson.databind.ObjectMapper;

/**
 * The scenario validator agrees with the canonical {@code ActivationConfig} JSON Schema on the shared corpus (#125,
 * research D-2): every document of {@code contracts/offering/corpus/ActivationConfig/valid} conforms to the contract's
 * mirror, and every one of {@code invalid} does not. networknt gives the same verdicts on both schemas
 * ({@code ActivationConfigSchemaParityTest} in {@code service/packs}); this is the third validator.
 */
class ActivationConfigConformanceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MIRROR = "charter/schemas/ActivationConfig.yaml";

    private final ContractConformance contract = new ContractConformance();

    @TestFactory
    List<DynamicTest> theScenarioValidatorGivesTheCorpusVerdicts() throws IOException {
        Path corpus = corpus();
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
                            assertThat(violations).as(name).isNotEmpty();
                        }
                    }));
                }
            }
        }
        assertThat(tests).hasSizeGreaterThanOrEqualTo(20);
        return tests;
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
