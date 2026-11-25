package ai.speckitty.charter.packdoctor;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.schema.JsonSchema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A quoted number in a number field (#651). {@code validate} refuses it with the detail the registration gives
 * ({@code QuotedNumberRegistrationTest} of {@code :packs}), which says how to fix it; {@code remediate} repairs exactly
 * that fault: a quoted value that the reader reads plain as a number of the field's type is written unquoted, in the
 * file's canonical form, and the manifest is restamped. A quoted value that is no such number, or any other fault, still
 * refuses the pack, and nothing is written. A quoted string in a string field is left as it is.
 */
class QuotedNumberTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String CATALOG = "routing_catalogs/cost-led.routing-catalog.yaml";

    private static final String PROFILE = "agent_profiles/human-operator.agent.yaml";

    private static final String PRICE = CATALOG + "#/models/0/cost/input_per_1m_usd";

    @TempDir
    Path temp;

    /** What one run answered. */
    private record Run(int exit, String out, String err) {

        /** The report, which follows any outcome lines on standard output. */
        JsonNode report() {
            return JSON.readTree(out.substring(out.indexOf("{\n")));
        }

        /** The outcome lines {@code remediate} printed before the report. */
        List<String> outcome() {
            return out.substring(0, out.indexOf("{\n")).lines().toList();
        }

        /** Each violation of the report as {@code code pointer: detail}, in order. */
        List<String> violations() {
            List<String> violations = new ArrayList<>();
            report().get("problems")
                    .forEach(problem -> problem.get("violations")
                            .forEach(violation ->
                                    violations.add(violation.get("code").asString() + " "
                                            + violation.get("pointer").asString() + ": "
                                            + violation.get("detail").asString())));
            return violations;
        }
    }

    private Run run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Path temporary = temp.resolve("tmp");
        try {
            Files.createDirectories(temporary);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        int exit = PackDoctorMain.run(
                args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                temporary);
        return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    /** The built-in and org example packs under {@code temp/packs/}. */
    private Path packs() {
        Path packs = temp.resolve("packs");
        ExamplePacks.copyOf(ExamplePacks.BUILT_IN, packs);
        ExamplePacks.copyOf(ExamplePacks.ORG, packs);
        return packs;
    }

    private static String[] values(Path packs) {
        return new String[] {
            packs.resolve(ExamplePacks.BUILT_IN) + ":"
                    + ExamplePacks.level(ExamplePacks.BUILT_IN).token(),
            packs.resolve(ExamplePacks.ORG) + ":"
                    + ExamplePacks.level(ExamplePacks.ORG).token()
        };
    }

    private Run validate(Path packs) {
        String[] given = values(packs);
        return run("validate", "--pack", given[0], "--pack", given[1]);
    }

    private Run remediate(Path packs) {
        String[] given = values(packs);
        return run("remediate", given[0], given[1]);
    }

    /** Replaces the one occurrence of {@code from} in the org pack's {@code file}. */
    private static void edit(Path packs, String file, String from, String to) throws IOException {
        Path path = packs.resolve(ExamplePacks.ORG).resolve(file);
        String text = Files.readString(path, StandardCharsets.UTF_8);
        assertThat(text).as("the fixture holds %s once", from).containsOnlyOnce(from);
        Files.writeString(path, text.replace(from, to), StandardCharsets.UTF_8);
    }

    /** The catalog's model-a: its one priced model and its one scored fit. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> modelA(Path packs) throws IOException {
        Object catalog = new Load(
                        LoadSettings.builder().setSchema(new JsonSchema()).build())
                .loadFromString(Files.readString(packs.resolve(ExamplePacks.ORG).resolve(CATALOG)));
        return ((List<Map<String, Object>>) ((Map<String, Object>) catalog).get("models")).get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cost(Path packs) throws IOException {
        return (Map<String, Object>) modelA(packs).get("cost");
    }

    // --- the hint ---------------------------------------------------------------------------------------------------

    @Test
    void validateRefusesAQuotedNumberWithTheFixUnderTheCodeAndPointerItAlwaysHad() throws IOException {
        Path packs = packs();
        edit(packs, CATALOG, "input_per_1m_usd: 15\n", "input_per_1m_usd: \"15\"\n");

        Run run = validate(packs);

        assertThat(run.exit()).as(run.err()).isEqualTo(PackDoctorMain.PROBLEMS);
        assertThat(run.violations())
                .as(run.out())
                .contains("artifact_schema_error " + PRICE + ": " + CATALOG
                        + ": 'input_per_1m_usd': \"15\" must be a number: write 15 without quotes, or run pack-doctor"
                        + " remediate");
    }

    // --- the repair -------------------------------------------------------------------------------------------------

    @Test
    void remediateUnquotesEachQuotedNumberInCanonicalFormAndRestamps() throws IOException {
        Path packs = packs();
        edit(packs, CATALOG, "input_per_1m_usd: 15\n", "input_per_1m_usd: \"15\"\n");
        edit(packs, CATALOG, "score: 0.3\n", "score: '0.6'\n");
        edit(packs, CATALOG, "output_per_1m_usd: 75\n", "output_per_1m_usd: \"1e-3\"\n");

        Run run = remediate(packs);

        assertThat(run.exit()).as(run.out() + run.err()).isEqualTo(PackDoctorMain.VALID);
        assertThat(run.outcome())
                .as("the repairs in the verdict's order")
                .containsSubsequence(
                        packs.resolve(ExamplePacks.ORG) + ": rewritten, 3 quoted numbers repaired",
                        "  repaired at " + PRICE + ": \"15\" is now a number",
                        "  repaired at " + CATALOG + "#/models/0/cost/output_per_1m_usd: \"1e-3\" is now a number",
                        "  repaired at " + CATALOG + "#/models/0/task_fit/0/score: \"0.6\" is now a number");
        String text = Files.readString(packs.resolve(ExamplePacks.ORG).resolve(CATALOG));
        assertThat(text)
                .contains("    input_per_1m_usd: 15\n")
                .contains("    output_per_1m_usd: 0.001\n")
                .contains("    score: 0.6\n");
        assertThat(cost(packs).get("input_per_1m_usd")).isEqualTo(15);
        assertThat(validate(packs).exit()).isEqualTo(PackDoctorMain.VALID);
        assertThat(remediate(packs).outcome())
                .as("a second run has nothing to repair")
                .contains(packs.resolve(ExamplePacks.ORG) + ": unchanged");
    }

    @Test
    void remediateUnquotesAQuotedIntegerInAnIntegerField() throws IOException {
        Path packs = packs();
        edit(packs, PROFILE, "role: operator\n", "role: operator\nrouting-priority: \"40\"\n");

        Run run = remediate(packs);

        assertThat(run.exit()).as(run.out() + run.err()).isEqualTo(PackDoctorMain.VALID);
        assertThat(run.outcome()).contains("  repaired at " + PROFILE + "#/routing-priority: \"40\" is now an integer");
        assertThat(Files.readString(packs.resolve(ExamplePacks.ORG).resolve(PROFILE)))
                .contains("routing-priority: 40\n");
    }

    // --- not repaired -----------------------------------------------------------------------------------------------

    @Test
    void aQuotedWordIsRefusedAndNothingIsWritten() throws IOException {
        assertRefusedUnchanged(
                CATALOG, "input_per_1m_usd: 15\n", "input_per_1m_usd: \"fifteen\"\n", "must be a number");
    }

    @Test
    void aQuotedNonIntegerInAnIntegerFieldIsRefusedAndNothingIsWritten() throws IOException {
        assertRefusedUnchanged(
                PROFILE, "role: operator\n", "role: operator\nrouting-priority: \"1.5\"\n", "must be an integer");
    }

    @Test
    void aRepairableQuotedNumberBesideAnotherFaultIsRefusedAndNothingIsWritten() throws IOException {
        Path packs = packs();
        edit(packs, CATALOG, "input_per_1m_usd: 15\n", "input_per_1m_usd: \"15\"\n");
        edit(packs, CATALOG, "output_per_1m_usd: 75\n", "output_per_1m_usd: \"fifteen\"\n");
        SortedMap<String, String> before = treeHash(packs.resolve(ExamplePacks.ORG));

        Run run = remediate(packs);

        assertThat(run.exit()).isEqualTo(PackDoctorMain.PROBLEMS);
        assertThat(run.outcome()).contains(packs.resolve(ExamplePacks.ORG) + ": refused");
        assertThat(String.join("\n", run.outcome()))
                .contains("'input_per_1m_usd': \"15\" must be a number: write 15 without quotes")
                .contains("'output_per_1m_usd' must be a number");
        assertThat(treeHash(packs.resolve(ExamplePacks.ORG))).isEqualTo(before);
    }

    @Test
    void aQuotedNumberOutOfTheFieldsRangeIsRefusedForItsRangeAndNothingIsWritten() throws IOException {
        Path packs = packs();
        edit(packs, CATALOG, "input_per_1m_usd: 15\n", "input_per_1m_usd: \"-15\"\n");
        SortedMap<String, String> before = treeHash(packs.resolve(ExamplePacks.ORG));

        Run run = remediate(packs);

        assertThat(run.exit()).isEqualTo(PackDoctorMain.PROBLEMS);
        assertThat(run.outcome())
                .contains(
                        packs.resolve(ExamplePacks.ORG) + ": refused",
                        "  artifact_schema_error at " + PRICE + ": " + CATALOG + ": 'input_per_1m_usd' -15 is out of"
                                + " range");
        assertThat(treeHash(packs.resolve(ExamplePacks.ORG))).isEqualTo(before);
    }

    private void assertRefusedUnchanged(String file, String from, String to, String detail) throws IOException {
        Path packs = packs();
        edit(packs, file, from, to);
        SortedMap<String, String> before = treeHash(packs.resolve(ExamplePacks.ORG));

        Run run = remediate(packs);

        assertThat(run.exit()).isEqualTo(PackDoctorMain.PROBLEMS);
        assertThat(run.outcome()).contains(packs.resolve(ExamplePacks.ORG) + ": refused");
        assertThat(String.join("\n", run.outcome())).contains(detail).doesNotContain("without quotes");
        assertThat(treeHash(packs.resolve(ExamplePacks.ORG)))
                .as("nothing is written")
                .isEqualTo(before);
    }

    // --- unaffected -------------------------------------------------------------------------------------------------

    @Test
    void aQuotedNumberInAStringFieldIsLeftAString() throws IOException {
        Path packs = packs();
        edit(packs, CATALOG, "currency: USD\n", "currency: \"15\"\n");
        edit(packs, CATALOG, "input_per_1m_usd: 15\n", "input_per_1m_usd: \"15\"\n");

        Run run = remediate(packs);

        assertThat(run.exit()).as(run.out() + run.err()).isEqualTo(PackDoctorMain.VALID);
        assertThat(run.outcome()).contains("  repaired at " + PRICE + ": \"15\" is now a number");
        assertThat(cost(packs)).containsEntry("currency", "15").containsEntry("input_per_1m_usd", 15);
    }

    private static SortedMap<String, String> treeHash(Path root) throws IOException {
        SortedMap<String, String> tree = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                tree.put(
                        root.relativize(path).toString(),
                        Files.isDirectory(path) ? "<directory>" : digest(Files.readAllBytes(path)));
            }
        }
        return tree;
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
