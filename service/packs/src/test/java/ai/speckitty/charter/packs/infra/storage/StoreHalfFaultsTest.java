package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The store half's own faults are faults of the one read, each stated once (#554, WP12, A-M1 rows 16 to 19 and 32): the
 * verdict the port returns names every fault only the store's assembly meets, and a fault the reader or a content rule
 * states already is not stated again in the store's words. Each plant is the built-in example pack with one fault.
 */
@DisplayName("the store half's faults are read faults, each stated once (#554, WP12)")
class StoreHalfFaultsTest {

    private static final FileSystemPackInspector READER = new FileSystemPackInspector();

    private static Path builtIn(Path host) {
        return ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
    }

    private static List<Reason> violations(Path pack) {
        return READER.read(new PackRoot("built-in", Optional.empty(), pack.toString()))
                .rationale()
                .violations();
    }

    private static void write(Path pack, String file, String text) throws IOException {
        Files.createDirectories(pack.resolve(file).getParent());
        Files.writeString(pack.resolve(file), text);
    }

    @Test
    @DisplayName("row 16: a template read twice is duplicate_artifact_id at the second file, in the store's words")
    void aTemplateReadTwiceIsStated(@TempDir Path host) throws IOException {
        Path pack = builtIn(host);
        write(pack, "templates/software-dev/research-template.md", "# again\n");

        assertThat(violations(pack))
                .extracting(
                        reason -> reason.code().value(),
                        reason -> reason.location().file(),
                        Reason::detail)
                .containsExactly(tuple(
                        "duplicate_artifact_id",
                        "templates/software-dev/research-template.md",
                        "template software-dev/research-template.md is already read from"
                                + " missions/software-dev/templates/research-template.md"));
    }

    @Test
    @DisplayName("row 17: a value-object breach is artifact_schema_error at its file")
    void aValueObjectBreachIsStated(@TempDir Path host) throws IOException {
        Path pack = builtIn(host);
        write(pack, "presets/dup.yaml", """
                schema_version: '1.0'
                id: dup
                description: D.
                activated_directives:
                - id: DIRECTIVE_001
                - id: DIRECTIVE_001
                  must_have: true
                """);

        assertThat(violations(pack)).singleElement().satisfies(fault -> {
            assertThat(fault.code().value()).isEqualTo("artifact_schema_error");
            assertThat(fault.location().file()).isEqualTo("presets/dup.yaml");
            assertThat(fault.detail()).startsWith("Constraint breached: Preset 'dup'");
        });
    }

    @Test
    @DisplayName("row 18: a template the mission type names and the pack lacks is stated once, in the reader's words")
    void anUnresolvedTemplateIsStatedOnce(@TempDir Path host) throws IOException {
        Path pack = builtIn(host);
        Files.delete(pack.resolve("missions/software-dev/templates/research-template.md"));

        assertThat(violations(pack))
                .extracting(
                        reason -> reason.code().value(),
                        reason -> reason.location().file(),
                        Reason::detail)
                .containsExactly(tuple(
                        "artifact_schema_error",
                        "missions/software-dev/software-dev.mission-type.yaml",
                        "missions/software-dev/software-dev.mission-type.yaml: names templates/research-template.md"
                                + " (named by the type): missing"));
    }

    @Test
    @DisplayName("row 18: a guard reference that does not close is stated at the lifecycle that names it")
    void aReferenceThatDoesNotCloseIsStated(@TempDir Path host) throws IOException {
        Path pack = builtIn(host);
        write(pack, "guards/subtasks-ready.guard.yaml", """
                schema_version: '1.0'
                id: subtasks-ready
                title: T
                interface: guard/1
                implemented_by: guard:nope
                """);

        assertThat(violations(pack))
                .extracting(
                        reason -> reason.code().value(),
                        reason -> reason.location().file())
                .containsExactly(
                        tuple("artifact_schema_error", "guards/subtasks-ready.guard.yaml"),
                        tuple("artifact_schema_error", "lifecycles/work-package.lifecycle.yaml"));
    }

    @Test
    @DisplayName("row 19: a companion the reader never meets is companion at the companion's own file")
    void aMissingCompanionIsStatedAtTheCompanion(@TempDir Path host) throws IOException {
        Path pack = builtIn(host);
        Files.delete(pack.resolve("toolguides/efficient-local-tooling.guide.md"));
        Files.write(pack.resolve("skills/review-checklist.skill.md"), new byte[] {(byte) 0xff, (byte) 0xfe, 0x41});

        assertThat(violations(pack))
                .extracting(
                        reason -> reason.code().value(),
                        reason -> reason.location().file(),
                        Reason::detail)
                .as("in the store's order of kinds")
                .containsExactly(
                        tuple("companion", "toolguides/efficient-local-tooling.guide.md", "missing"),
                        tuple("companion", "skills/review-checklist.skill.md", "is not valid UTF-8"));
    }

    @Test
    @DisplayName("row 19: a companion the reader judges is stated once, at the companion, in the reader's words")
    void aBrokenCompanionIsStatedOnce(@TempDir Path host) throws IOException {
        Path pack = builtIn(host);
        write(pack, "toolguides/efficient-local-tooling.guide.md", "no heading at all\n\n## Overview\n");
        write(pack, "missions/software-dev/steps/accept.prompt.md", "---\nfoo: [\n---\n# x\n");

        assertThat(violations(pack))
                .extracting(
                        reason -> reason.code().value(),
                        reason -> reason.location().file())
                .containsExactlyInAnyOrder(
                        tuple("companion", "missions/software-dev/steps/accept.prompt.md"),
                        tuple("artifact_schema_error", "toolguides/efficient-local-tooling.guide.md"));
    }

    @Test
    @DisplayName("row 32: a named policy file that does not read is unreadable, in registration's words, once")
    void anUnreadableNamedPolicyIsStatedOnce(@TempDir Path host) throws IOException {
        Path pack = builtIn(host);
        write(pack, "policy.yaml", "schema_version: 1\npins: []\n");

        assertThat(violations(pack))
                .extracting(
                        reason -> reason.code().value(),
                        reason -> reason.location().file(),
                        Reason::detail)
                .containsExactly(tuple(
                        "unreadable",
                        "policy.yaml",
                        "named by mutability_policy, the file cannot be read: unknown key 'pins'"));
    }

    @Test
    @DisplayName("row 32: a named policy file that is missing is unreadable, in registration's words")
    void aMissingNamedPolicyIsStated(@TempDir Path host) throws IOException {
        Path pack = builtIn(host);
        Files.delete(pack.resolve("policy.yaml"));

        assertThat(violations(pack))
                .extracting(
                        reason -> reason.code().value(),
                        reason -> reason.location().file(),
                        Reason::detail)
                .containsExactly(tuple("unreadable", "policy.yaml", "named by mutability_policy, the file is missing"));
    }

    @Test
    @DisplayName("an edge that joins an artifact to itself through the pack's own name is unreadable at pack")
    void aQualifiedSelfLoopIsStated(@TempDir Path host) throws IOException {
        Path pack = builtIn(host);
        write(pack, "self.graph.yaml", """
                schema_version: '1.0'
                generated_at: STATIC
                generated_by: x
                nodes: []
                edges:
                - source: built-in::tactic:acceptance-test-first
                  target: built-in::tactic:acceptance-test-first
                  relation: requires
                """);

        assertThat(violations(pack))
                .extracting(
                        reason -> reason.code().value(),
                        reason -> reason.location().file(),
                        Reason::detail)
                .containsExactly(tuple(
                        "unreadable",
                        "pack",
                        "the file self.graph.yaml of built-in cannot be read: an edge joins"
                                + " 'built-in::tactic:acceptance-test-first' to itself"));
    }

    @Test
    @DisplayName("the built-in example pack is clean: the store half adds nothing to a pack the read holds whole")
    void aCleanPackGainsNothing(@TempDir Path host) {
        assertThat(violations(builtIn(host))).isEmpty();
    }

    @Test
    @DisplayName(
            "row 25: a root that is not a directory is one pack_unreadable at pack, whatever name it is read under")
    void aRootReadUnderAnotherNameIsStatedOnce(@TempDir Path host) {
        Path missing = host.resolve("qa");

        assertThat(READER.read(new PackRoot("another-name", Optional.empty(), missing.toString()))
                        .rationale()
                        .violations())
                .extracting(
                        reason -> reason.code().value(),
                        reason -> reason.location().file(),
                        Reason::detail)
                .containsExactly(tuple("pack_unreadable", "pack", "pack another-name is not a directory"));
    }

    @Test
    @DisplayName("MAJOR-1: a blank fragment beside an enhancement that names its slot twice answers both faults")
    void aBlankFragmentBesideARepeatedSlotIsStated(@TempDir Path host) throws IOException {
        Path pack = builtIn(host);
        String enhancement = "missions/software-dev/enhancements/plan-risk-notes.step-enhancement.yaml";
        write(pack, enhancement, """
                schema_version: "1.0"
                id: plan-risk-notes
                prompt_fragments:
                  - slot: spdd:reasons-block
                    path: plan-risk-notes.prompt-fragment.md
                  - slot: spdd:reasons-block
                    path: plan-risk-notes.prompt-fragment.md
                """);
        write(pack, "missions/software-dev/enhancements/plan-risk-notes.prompt-fragment.md", "\n");

        List<Reason> violations = violations(pack);

        assertThat(violations)
                .as(
                        "every fault is at the enhancement, the fragment's read fault among them, as the contract places it")
                .allSatisfy(fault -> {
                    assertThat(fault.code().value()).isEqualTo("prompt_fragment_invalid");
                    assertThat(fault.location().file()).isEqualTo(enhancement);
                });
        assertThat(violations)
                .extracting(Reason::detail)
                .anySatisfy(detail -> assertThat(detail).contains("more than once"))
                .anySatisfy(detail -> assertThat(detail)
                        .startsWith("plan-risk-notes.prompt-fragment.md: ")
                        .doesNotContain("more than once"));
    }

    @Test
    @DisplayName("MAJOR-1: a preset whose id is not its stem and that lists a directive twice answers both faults")
    void aStemFaultDoesNotHideABreach(@TempDir Path host) throws IOException {
        Path pack = builtIn(host);
        write(pack, "presets/dup.yaml", """
                schema_version: '1.0'
                id: other
                description: D.
                activated_directives:
                - id: DIRECTIVE_001
                - id: DIRECTIVE_001
                  must_have: true
                """);

        assertThat(violations(pack))
                .allSatisfy(fault -> {
                    assertThat(fault.code().value()).isEqualTo("artifact_schema_error");
                    assertThat(fault.location().file()).isEqualTo("presets/dup.yaml");
                })
                .extracting(Reason::detail)
                .hasSize(2)
                .anySatisfy(detail -> assertThat(detail).contains("Constraint breached"))
                .anySatisfy(detail -> assertThat(detail).doesNotContain("Constraint breached"));
    }

    private static final String MISSION_TYPE = "missions/software-dev/software-dev.mission-type.yaml";

    private static void edit(Path pack, String from, String to) throws IOException {
        String text = Files.readString(pack.resolve(MISSION_TYPE));
        assertThat(text).contains(from);
        Files.writeString(pack.resolve(MISSION_TYPE), text.replace(from, to));
    }

    @Test
    @DisplayName("review cycle 2 MAJOR-A: a mission type whose step stands twice answers the reader's step fault and"
            + " the store's own distinct fault of the same type; the artifact key's breach is a cascade the store's"
            + " entity build does not reach")
    void aRepeatedStepDoesNotHideADistinctFault(@TempDir Path host) throws IOException {
        Path pack = builtIn(host);
        edit(pack, "- id: specify\n", "- id: discovery\n");
        edit(pack, "- artifact_key: evidence.gap-analysis", "- artifact_key: evidence.quickstart");

        assertThat(violations(pack))
                .extracting(reason -> reason.location().file(), Reason::detail)
                .containsExactly(
                        tuple(
                                MISSION_TYPE,
                                MISSION_TYPE + ": step 'discovery' stands twice across steps and on_demand_steps"),
                        tuple(
                                MISSION_TYPE,
                                "mission type 'software-dev' step 'discovery': names steps/specify.prompt.md, not its"
                                        + " derived path steps/discovery.prompt.md"));
    }

    @Test
    @DisplayName("a missing output template refuses the mission type at the store's read, before its entity is built,"
            + " so the artifact key's breach is a cascade neither half meets (recorded in the R2 note)")
    void aMissingTemplateStopsTheTypeBeforeItsBreach(@TempDir Path host) throws IOException {
        Path pack = builtIn(host);
        Files.delete(pack.resolve("missions/software-dev/templates/research-template.md"));
        edit(pack, "- artifact_key: evidence.gap-analysis", "- artifact_key: evidence.quickstart");

        assertThat(violations(pack))
                .extracting(reason -> reason.location().file(), Reason::detail)
                .containsExactly(tuple(
                        MISSION_TYPE,
                        MISSION_TYPE + ": names templates/research-template.md (named by the type): missing"));
    }

    @Test
    @DisplayName("the artifact key's breach alone is the store's row 17 fault, at the mission type")
    void anArtifactKeyBreachIsStated(@TempDir Path host) throws IOException {
        Path pack = builtIn(host);
        edit(pack, "- artifact_key: evidence.gap-analysis", "- artifact_key: evidence.quickstart");

        assertThat(violations(pack))
                .extracting(reason -> reason.location().file(), Reason::detail)
                .singleElement()
                .satisfies(fault -> assertThat(String.valueOf(fault))
                        .contains("An artifact key stands once in a mission type's expected outputs."));
    }
}
