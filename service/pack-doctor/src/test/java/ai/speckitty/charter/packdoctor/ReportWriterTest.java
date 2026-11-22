package ai.speckitty.charter.packdoctor;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packdoctor.Arguments.Parsed;
import ai.speckitty.charter.packdoctor.Problems.Problem;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackVerdict;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packvalidation.set.PackSetCheck;
import ai.speckitty.charter.packvalidation.set.PackSetEntry;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.Check;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.PackResult;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.Skip;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.Unjudged;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@link ReportWriter#tree}: the report is one tree (FR-008, plan D6, T025), standard output's problem JSON with the
 * tool, the inputs and a summary around it, each problem a {@link Problem} rendered as before. Each verdict is a real
 * pack's result, read by the one reader, with the reasons of the case put in its place.
 */
class ReportWriterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The build's version, which the test task passes, so the tree's is checked against the build, not itself. */
    private static final String VERSION = System.getProperty("charter.pack-doctor.version");

    @TempDir
    Path temp;

    private Path builtIn;

    private PackResult real;

    @BeforeEach
    void readAnExamplePack() {
        builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("packs"));
        real = new PackSetCheck(new FileSystemPackInspector())
                .check(List.of(
                        new PackSetEntry(new PackRoot(ExamplePacks.BUILT_IN, Optional.empty(), builtIn.toString()))))
                .packs()
                .getFirst();
    }

    private static Reason finding(String code, Severity severity, String file) {
        return new Reason(
                "test", Origin.NONE, Evaluation.FAIL, new ReasonCode(code, severity), Location.in(file), "a finding");
    }

    private PackResult with(List<Reason> findings) {
        return new PackResult(
                real.entry(), new PackVerdict(List.of(), findings), real.level(), real.facts(), List.of(), List.of());
    }

    private static Parsed parsed(String... options) {
        Arguments.Outcome outcome = Arguments.validate(options);
        assertThat(outcome).isInstanceOf(Parsed.class);
        return (Parsed) outcome;
    }

    private static ObjectNode tree(Parsed parsed, PackSetVerdict verdict) {
        return ReportWriter.tree(parsed, verdict, Problems.of(verdict));
    }

    @Test
    void aPlantedSetIsOneTreeTheToolTheInputsTheChecksThePacksTheProblemsAndTheSummary() {
        PackSetVerdict verdict = new PackSetVerdict(
                List.of(with(List.of(
                        finding("duplicate_artifact_id", Severity.VIOLATION, "tactics/a.tactic.yaml"),
                        finding("drg_schema_error", Severity.VIOLATION, "drg/fragment.yaml"),
                        finding("file_not_held", Severity.WARNING, "notes.md"),
                        finding("a_notice", Severity.NOTICE, "pack.yaml")))),
                List.of(),
                List.of(Check.PACK, Check.CLOSURE),
                List.of());

        ObjectNode tree = tree(parsed("--pack", builtIn.toString()), verdict);

        assertThat(tree.propertyNames()).containsExactly("tool", "inputs", "checks", "packs", "problems", "summary");
        assertThat(VERSION).as("the version the build passes").isNotBlank();
        assertThat(tree.get("tool"))
                .isEqualTo(JSON.readTree("{\"name\":\"pack-doctor\",\"version\":\"" + VERSION + "\"}"));
        assertThat(JSON.writeValueAsString(tree.get("inputs")))
                .isEqualTo(JSON.writeValueAsString(
                        JSON.readTree("{\"packs\":[" + JSON.writeValueAsString(builtIn.toString())
                                + "],\"options\":{\"full\":false,\"round_trip\":false}}")));
        assertThat(JSON.writeValueAsString(tree.get("summary")))
                .isEqualTo("{\"packs\":1,\"violations\":2,\"warnings\":1,\"notices\":1,\"exit_code\":1}");
    }

    @Test
    void theExistingKeysAreAsBeforeAndEachProblemIsTheProblemRendered() {
        PackSetVerdict verdict = new PackSetVerdict(
                List.of(with(List.of(finding("duplicate_artifact_id", Severity.VIOLATION, "tactics/a.tactic.yaml")))),
                List.of(),
                List.of(Check.PACK),
                List.of(new Skip(Check.CLOSURE, List.of(new Unjudged(ExamplePacks.BUILT_IN, "its level is unknown")))));
        Problem problem = Problems.of(verdict).getFirst();

        ObjectNode tree = tree(parsed("--pack", builtIn.toString()), verdict);

        assertThat(tree.propertyNames())
                .containsExactly("tool", "inputs", "checks", "packs", "skipped", "problems", "summary");
        assertThat(tree.get("checks")).isEqualTo(JSON.readTree("[\"PACK\"]"));
        assertThat(tree.get("packs"))
                .isEqualTo(JSON.readTree("[{\"name\":\"built-in\",\"level\":\"built-in\",\"path\":"
                        + JSON.writeValueAsString(builtIn.toString()) + "}]"));
        assertThat(tree.get("skipped"))
                .isEqualTo(JSON.readTree("[{\"check\":\"CLOSURE\",\"reason\":"
                        + JSON.writeValueAsString(verdict.skipped().getFirst().reason()) + "}]"));
        JsonNode written = tree.get("problems").get(0);
        assertThat(written.propertyNames())
                .containsExactly("title", "status", "detail", "instance", "code", "violations");
        assertThat(written.get("title").asString()).isEqualTo(problem.title());
        assertThat(written.get("status").asInt()).isEqualTo(problem.status());
        assertThat(written.get("detail").asString()).isEqualTo(problem.detail());
        assertThat(written.get("instance").asString())
                .isEqualTo(problem.instance().orElseThrow());
        assertThat(written.get("code").asString()).isEqualTo(problem.code());
        assertThat(written.get("violations").get(0).propertyNames()).containsExactly("pointer", "code", "detail");
    }

    @Test
    void aConfigRootIsTheInputAndTheRoundTripAnOption() {
        Path root = builtIn.getParent().getParent();
        PackSetVerdict verdict =
                new PackSetVerdict(List.of(real), List.of(), List.of(Check.PACK, Check.ROUND_TRIP), List.of());

        ObjectNode tree = tree(parsed("--config-root", root.toString(), "--round-trip"), verdict);

        assertThat(JSON.writeValueAsString(tree.get("inputs")))
                .isEqualTo("{\"config_root\":" + JSON.writeValueAsString(root.toString())
                        + ",\"options\":{\"full\":false,\"round_trip\":true}}");
    }

    @Test
    void aValidSetExitsZeroWithNoViolationItsWarningsCounted() {
        PackSetVerdict verdict = new PackSetVerdict(List.of(real), List.of(), List.of(Check.PACK), List.of());
        long warnings = real.verdict().findings().stream()
                .filter(reason -> reason.isFailing(Severity.WARNING))
                .count();

        ObjectNode tree = tree(parsed("--pack", builtIn.toString()), verdict);

        assertThat(warnings).as("the example pack's own warnings").isPositive();
        assertThat(tree.get("problems")).isEmpty();
        assertThat(JSON.writeValueAsString(tree.get("summary")))
                .isEqualTo(
                        "{\"packs\":1,\"violations\":0,\"warnings\":" + warnings + ",\"notices\":0,\"exit_code\":0}");
    }

    @Test
    void theJsonIsTheTreeIndentedWithALineEnd() {
        PackSetVerdict verdict = new PackSetVerdict(List.of(real), List.of(), List.of(Check.PACK), List.of());
        ObjectNode tree = tree(parsed("--pack", builtIn.toString()), verdict);

        String json = ReportWriter.json(tree);

        assertThat(json).startsWith("{\n  \"tool\" : {").endsWith("}" + System.lineSeparator());
        assertThat(JSON.readTree(json)).isEqualTo(tree);
    }
}
