package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.PackInspector.Scope;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The step enhancement and fragment rules at the load seat (FR-004 to FR-006, OD-3 (a), plan errata E-1 and E-3): a
 * fault is a violation at its file, never a throw, so the staged load of a registration lists every fault at once and a
 * composing read names the first. Each case plants a fault beside its passing twin; the cases that name a precedence
 * (T-08) run in validate mode too.
 */
@DisplayName("Step enhancement and fragment faults at the load seat are violations at their files (IC-02)")
class StepEnhancementLoadChecksTest {

    private static final String CODE = "prompt_fragment_invalid";
    private static final String TYPE = "software-dev";
    private static final String GRAPH = "enhancements.graph.yaml";

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();
    private final WholePackStore store = new WholePackStore(strategy);

    @TempDir
    Path host;

    /** One enhancing pack on disk: build it up from valid pairs, break one line, and read it at each moment. */
    private final class Enhancer {
        private final Path dir;
        private final List<String> edges = new ArrayList<>();

        Enhancer() throws IOException {
            this.dir = Files.createDirectories(host.resolve("enhancer"));
            file("pack.yaml", """
                    pack_id: 01M6ZRYK0000000000000000E1
                    pack_version: 1.0.0
                    parent_pack: null
                    name: enhancer
                    pack_level: org
                    """);
        }

        Enhancer file(String relative, String text) throws IOException {
            Path file = dir.resolve(relative);
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
            return this;
        }

        String yamlOf(String type, String id) {
            return "missions/" + type + "/enhancements/" + id + ".step-enhancement.yaml";
        }

        String fragmentOf(String type, String name) {
            return "missions/" + type + "/enhancements/" + name + ".prompt-fragment.md";
        }

        /** An enhancement of {@code type} with one entry per (slot, companion name) pair, no edge and no companion. */
        Enhancer enhancement(String type, String id, String... slotAndPath) throws IOException {
            StringBuilder yaml = new StringBuilder("schema_version: '1.0'\nid: " + id + "\nprompt_fragments:\n");
            for (int i = 0; i < slotAndPath.length; i += 2) {
                yaml.append("  - slot: ")
                        .append(slotAndPath[i])
                        .append("\n    path: ")
                        .append(slotAndPath[i + 1])
                        .append("\n");
            }
            return file(yamlOf(type, id), yaml.toString());
        }

        Enhancer fragment(String type, String name, String text) throws IOException {
            return file(fragmentOf(type, name), text);
        }

        Enhancer enhances(String id, String target) {
            edges.add("- source: step_enhancement:" + id + "\n  target: " + target + "\n  relation: enhances\n");
            return this;
        }

        /** An edge whose source is qualified with the pack's own name: the same enhancement as the bare spelling. */
        Enhancer selfQualified(String id, String relation, String target) {
            edges.add("- source: enhancer::step_enhancement:" + id + "\n  target: " + target + "\n  relation: "
                    + relation + "\n");
            return this;
        }

        Enhancer overrides(String id, String target) {
            edges.add("- source: step_enhancement:" + id + "\n  target: " + target + "\n  relation: overrides\n");
            return this;
        }

        /** A valid pair of {@code type}: one entry, its own companion, one qualified edge to the step. */
        Enhancer pair(String type, String id, String step) throws IOException {
            return enhancement(type, id, "spdd:reasons-block", id + ".prompt-fragment.md")
                    .fragment(type, id, "## Guidance\n\nFill it.\n")
                    .enhances(id, "base::action:" + type + "/" + step);
        }

        Enhancer pair(String id) throws IOException {
            return pair(TYPE, id, "plan");
        }

        /** Writes the graph document of the edges added so far. */
        Enhancer graph() throws IOException {
            return file(
                    GRAPH, "schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges:\n" + String.join("", edges));
        }

        /** Validate mode: every finding the policy raises for the code. */
        List<PackFinding> validate() {
            PackRoot root = new PackRoot("enhancer", Optional.of(PackLevel.ORG), dir.toString());
            return PackPolicy.judge(new FileSystemPackInspector().inspect(root, Scope.FULL)).findings().stream()
                    .filter(finding -> finding.code() == PackFindingCode.PROMPT_FRAGMENT_INVALID)
                    .toList();
        }

        /** Validate mode, every finding. */
        List<PackFinding> validateAll() {
            PackRoot root = new PackRoot("enhancer", Optional.of(PackLevel.ORG), dir.toString());
            return PackPolicy.judge(new FileSystemPackInspector().inspect(root, Scope.FULL))
                    .findings();
        }

        /** The registration's staged load: the violations of the refusal, none when the pack registers. */
        List<RefusalViolation> staged() {
            try {
                store.loadStaged(new StagedPack(dir));
                return List.of();
            } catch (CharterRefusal refusal) {
                assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                return refusal.violations();
            }
        }

        /** The aggregate the whole-pack load builds. */
        Pack loaded() {
            return store.load(dir).pack();
        }

        DomainRepositoryService service() {
            return new DomainRepositoryService(
                    () -> List.of(new PackLocation("enhancer", PackLevel.ORG, dir)), strategy);
        }

        /** The composing read: the refusal it answers, or none. */
        Optional<CharterRefusal> read() {
            try {
                service().compositionFacts(new PackIdentifier("enhancer"));
                return Optional.empty();
            } catch (CharterRefusal refusal) {
                return Optional.of(refusal);
            }
        }
    }

    private static List<String> pointers(List<RefusalViolation> violations) {
        return violations.stream().map(RefusalViolation::pointer).toList();
    }

    // ---- nothing is thrown; every fault at once ------------------------------------------------------------------

    @Test
    @DisplayName(
            "a rule fault is a violation of kind prompt_fragment_invalid at its file; the load still answers the rest")
    void aRuleFaultIsAViolationAndTheLoadStillAnswersTheOtherArtifacts() throws IOException {
        Enhancer pack = new Enhancer().pair("brief").pair(TYPE, "other", "plan");
        pack.file("tactics/t.tactic.yaml", "schema_version: '1.0'\nid: t\nname: t\nsteps:\n  - title: Only step\n");
        pack.overrides("other", "mission_type:software-dev").graph();

        Pack loaded = pack.loaded();

        assertThat(loaded.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(pack.yamlOf(TYPE, "other"));
            assertThat(violation.kind()).isEqualTo(CODE);
        });
        assertThat(loaded.artifacts(ArtifactKind.STEP_ENHANCEMENT))
                .as("both enhancements stay held: a rule fault withholds nothing")
                .extracting(artifact -> artifact.localId())
                .containsExactlyInAnyOrder("brief", "other");
        assertThat(loaded.artifacts(ArtifactKind.TACTIC)).hasSize(1);
    }

    @Test
    @DisplayName("the twin: the same pack without the overrides edge loads with no violation and registers")
    void theTwinWithoutTheOverridesEdgeLoadsWithNoViolation() throws IOException {
        Enhancer pack = new Enhancer().pair("brief").pair(TYPE, "other", "plan").graph();

        assertThat(pack.loaded().violations()).isEmpty();
        assertThat(pack.staged()).isEmpty();
        assertThat(pack.read()).isEmpty();
    }

    @Test
    @DisplayName("four faults in four files whose enhancements stay held are listed at once, each at its file (T-07)")
    void fourFaultsAreListedAtOnceEachAtItsFile() throws IOException {
        Enhancer pack = new Enhancer()
                .pair("a")
                .fragment(TYPE, "a", "## Guidance\n\n<!-- other:slot:start -->\n")
                .pair("b")
                .enhances("b", "base::action:software-dev/build")
                .pair("c")
                .overrides("c", "mission_type:software-dev")
                .pair("d")
                .graph();
        // d sits in the software-dev directory but its edge names another type
        pack.edges.removeIf(edge -> edge.contains("step_enhancement:d\n"));
        pack.enhances("d", "base::action:research/plan").graph();

        List<RefusalViolation> violations = pack.staged();

        assertThat(violations).hasSize(4);
        assertThat(violations).extracting(RefusalViolation::code).containsOnly(CODE);
        assertThat(pointers(violations))
                .as("path order, then rule order")
                .containsExactly(
                        pack.fragmentOf(TYPE, "a"),
                        pack.yamlOf(TYPE, "b"),
                        pack.yamlOf(TYPE, "c"),
                        pack.yamlOf(TYPE, "d"));
    }

    // ---- both paths: the registration lists it, a composing read names its file (E-1) -----------------------------

    @Test
    @DisplayName("an overrides edge is listed by the staged load and named by the composing read, an edge fault (E-1)")
    void anEdgeFaultReachesTheStagedLoadAndTheComposingRead() throws IOException {
        Enhancer pack = new Enhancer()
                .pair("brief")
                .overrides("brief", "mission_type:software-dev")
                .graph();

        assertThat(pack.staged()).singleElement().satisfies(violation -> {
            assertThat(violation.pointer()).isEqualTo(pack.yamlOf(TYPE, "brief"));
            assertThat(violation.code()).isEqualTo(CODE);
        });
        assertThat(pack.read()).hasValueSatisfying(refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refusal.getMessage()).contains(pack.yamlOf(TYPE, "brief"));
        });
    }

    @Test
    @DisplayName("a self-qualified source is the same enhancement: its overrides and second enhances edges are refused")
    void aSelfQualifiedSourceIsJudgedLikeABareOne() throws IOException {
        Enhancer overriding =
                new Enhancer().pair("brief").selfQualified("brief", "overrides", "mission_type:software-dev");
        overriding.graph();
        assertThat(pointers(overriding.staged())).containsExactly(overriding.yamlOf(TYPE, "brief"));
        assertThat(overriding.read()).isPresent();

        Enhancer twice =
                new Enhancer().pair("brief").selfQualified("brief", "enhances", "base::action:software-dev/build");
        twice.graph();
        assertThat(pointers(twice.staged())).containsExactly(twice.yamlOf(TYPE, "brief"));
        assertThat(twice.read()).isPresent();
    }

    @Test
    @DisplayName("the twin: a sole self-qualified enhances edge registers and reads")
    void aSoleSelfQualifiedEdgeIsAccepted() throws IOException {
        Enhancer sole = new Enhancer()
                .enhancement(TYPE, "brief", "spdd:reasons-block", "brief.prompt-fragment.md")
                .fragment(TYPE, "brief", "## Guidance\n\nFill it.\n")
                .selfQualified("brief", "enhances", "base::action:software-dev/plan");
        sole.graph();
        assertThat(sole.staged()).isEmpty();
        assertThat(sole.read()).isEmpty();
    }

    @Test
    @DisplayName("two enhances edges are listed by the staged load and named by the composing read")
    void twoEnhancesEdgesReachTheStagedLoadAndTheComposingRead() throws IOException {
        Enhancer pack = new Enhancer()
                .pair("brief")
                .enhances("brief", "base::action:software-dev/build")
                .graph();

        assertThat(pointers(pack.staged())).containsExactly(pack.yamlOf(TYPE, "brief"));
        assertThat(pack.read()).hasValueSatisfying(refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refusal.getMessage()).contains(pack.yamlOf(TYPE, "brief"));
        });
    }

    // ---- A2: a repeated id is withheld from the builder -----------------------------------------------------------

    private Enhancer repeatedId() throws IOException {
        Enhancer pack = new Enhancer();
        pack.pair("research", "spdd-plan", "plan");
        pack.pair(TYPE, "spdd-plan", "plan");
        // one edge only, to the first file's step: the files of one id share their edges (fourth analysis A2)
        pack.edges.removeIf(edge -> edge.contains("action:software-dev/plan"));
        return pack.graph();
    }

    @Test
    @DisplayName("a repeated id is prompt_fragment_invalid at the second file, which is withheld from the pack (A2)")
    void aRepeatedIdIsRefusedAtTheSecondFileAndWithheldFromThePack() throws IOException {
        Enhancer pack = repeatedId();

        assertThat(pack.staged())
                .extracting(RefusalViolation::code, RefusalViolation::pointer)
                .containsExactly(tuple(CODE, pack.yamlOf(TYPE, "spdd-plan")));
        Pack loaded = pack.loaded();
        assertThat(loaded.artifacts(ArtifactKind.STEP_ENHANCEMENT))
                .singleElement()
                .satisfies(artifact ->
                        assertThat(artifact.urn()).isEqualTo(new Urn(NodeKind.STEP_ENHANCEMENT, "spdd-plan")));
        assertThat(store.load(pack.dir).layout().paths())
                .containsEntry(new Urn(NodeKind.STEP_ENHANCEMENT, "spdd-plan"), pack.yamlOf("research", "spdd-plan"));
        assertThat(pack.read()).hasValueSatisfying(refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refusal.getMessage()).contains(pack.yamlOf(TYPE, "spdd-plan"));
        });
    }

    @Test
    @DisplayName("the twin: the second enhancement renamed, with an edge of its own, registers and reads")
    void theTwinOfARepeatedIdWithTheSecondRenamedRegisters() throws IOException {
        Enhancer pack = new Enhancer();
        pack.pair("research", "spdd-plan", "plan");
        pack.pair(TYPE, "spdd-research", "plan");

        pack.graph();

        assertThat(pack.staged()).isEmpty();
        assertThat(pack.read()).isEmpty();
        assertThat(pack.loaded().artifacts(ArtifactKind.STEP_ENHANCEMENT)).hasSize(2);
    }

    // ---- A3: the companion remap, by file, only in the graph-taking form ------------------------------------------

    private Enhancer blankCompanion() throws IOException {
        Enhancer pack = new Enhancer().pair("brief").fragment(TYPE, "brief", "\n");
        return pack.graph();
    }

    @Test
    @DisplayName(
            "a blank companion's violation at the yaml is prompt_fragment_invalid through load and the composing read")
    void aCompanionViolationIsRemappedByTheGraphTakingForm() throws IOException {
        Enhancer pack = blankCompanion();

        assertThat(pack.loaded().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(pack.yamlOf(TYPE, "brief"));
            assertThat(violation.kind()).isEqualTo(CODE);
            assertThat(violation.reason()).startsWith("brief.prompt-fragment.md: ");
        });
        assertThat(pack.service().load(new PackIdentifier("enhancer")).violations())
                .singleElement()
                .satisfies(violation -> assertThat(violation.kind()).isEqualTo(CODE));
        assertThat(pack.read())
                .hasValueSatisfying(refusal -> assertThat(refusal.getMessage()).contains(pack.yamlOf(TYPE, "brief")));
    }

    @Test
    @DisplayName("the three-argument form, which ArtifactFiles and the tests use, keeps the generic companion kind")
    void theThreeArgumentFormKeepsTheGenericCompanionKind() throws IOException {
        Enhancer pack = blankCompanion();
        FilesystemPackStore files = new FilesystemPackStore(host, strategy);

        Pack assembled = DomainRepositoryService.assembly(
                        new PackIdentifier("enhancer"), files.read(new PackIdentifier("enhancer")), new HashMap<>())
                .build();

        assertThat(assembled.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(pack.yamlOf(TYPE, "brief"));
            assertThat(violation.kind()).isEqualTo("companion");
        });
    }

    @Test
    @DisplayName("a companion fault stays one violation at the yaml; the valid companion beside it is no orphan (A4)")
    void theCompanionsOfARefusedEnhancementAreNoOrphans() throws IOException {
        Enhancer pack = new Enhancer()
                .enhancement(TYPE, "brief", "spdd:one", "one.prompt-fragment.md", "spdd:two", "two.prompt-fragment.md")
                .fragment(TYPE, "one", "\n")
                .fragment(TYPE, "two", "## Guidance\n\nFill it.\n")
                .enhances("brief", "base::action:software-dev/plan")
                .graph();

        assertThat(pack.staged())
                .extracting(RefusalViolation::code, RefusalViolation::pointer)
                .containsExactly(tuple(CODE, pack.yamlOf(TYPE, "brief")));
    }

    // ---- orphans: reclassified at the staged load, never on a composing read --------------------------------------

    @Test
    @DisplayName("an orphan fragment beside a valid pair is prompt_fragment_invalid at the orphan; its twin registers")
    void anOrphanIsReclassifiedAtTheStagedLoad() throws IOException {
        Enhancer pack = new Enhancer().pair("brief").graph();
        assertThat(pack.staged()).isEmpty();

        pack.fragment(TYPE, "stray", "## Stray\n\nNobody names it.\n");

        assertThat(pack.staged()).singleElement().satisfies(violation -> {
            assertThat(violation.pointer()).isEqualTo(pack.fragmentOf(TYPE, "stray"));
            assertThat(violation.code()).isEqualTo(CODE);
        });
        assertThat(pack.read())
                .as("an orphan is not held on a composing read, like every unmodelled file")
                .isEmpty();
    }

    @Test
    @DisplayName("an orphan beside an enhancement with a rule fault is judged at both moments (fifth analysis A5)")
    void anOrphanBesideAHeldEnhancementWithARuleFaultIsJudged() throws IOException {
        Enhancer pack = new Enhancer()
                .pair("brief")
                .enhances("brief", "base::action:software-dev/build")
                .fragment(TYPE, "stray", "## Stray\n\nNobody names it.\n")
                .graph();

        assertThat(pointers(pack.staged())).containsExactly(pack.yamlOf(TYPE, "brief"), pack.fragmentOf(TYPE, "stray"));
        assertThat(pack.validate())
                .extracting(PackFinding::file)
                .containsExactly(pack.yamlOf(TYPE, "brief"), pack.fragmentOf(TYPE, "stray"));
    }

    @Test
    @DisplayName("a fragment beside an enhancement the store refuses is no orphan and is not listed, at both moments")
    void aFragmentBesideARefusedEnhancementIsNoOrphan() throws IOException {
        Enhancer pack = new Enhancer()
                .enhancement(TYPE, "brief", "spdd:one", "missing.prompt-fragment.md")
                .enhances("brief", "base::action:software-dev/plan")
                .fragment(TYPE, "stray", "## Stray\n\nMaybe it was meant.\n")
                .file("missions/" + TYPE + "/enhancements/notes.txt", "x\n")
                .graph();

        assertThat(pack.staged())
                .extracting(RefusalViolation::code, RefusalViolation::pointer)
                .containsExactly(
                        tuple(CODE, pack.yamlOf(TYPE, "brief")),
                        tuple("unmodelled", "missions/" + TYPE + "/enhancements/notes.txt"));
        assertThat(pack.validate()).extracting(PackFinding::file).containsExactly(pack.yamlOf(TYPE, "brief"));
    }

    // ---- the companion twins: each at its file, all listed at once -------------------------------------------------

    @Test
    @DisplayName(
            "a shared companion, a path named twice, a missing companion and an orphan are listed at once, each at its file")
    void theCompanionFaultsAreListedAtOnceEachAtItsFile() throws IOException {
        Enhancer pack = new Enhancer();
        pack.pair("shared", "first", "plan");
        pack.enhancement("shared", "second", "spdd:reasons-block", "first.prompt-fragment.md")
                .enhances("second", "base::action:shared/plan");
        pack.enhancement("twice", "twice", "spdd:one", "t.prompt-fragment.md", "spdd:two", "t.prompt-fragment.md")
                .fragment("twice", "t", "## Guidance\n\nFill it.\n")
                .enhances("twice", "base::action:twice/plan");
        pack.enhancement("missing", "missing", "spdd:one", "gone.prompt-fragment.md")
                .enhances("missing", "base::action:missing/plan");
        pack.pair("orphan", "kept", "plan").fragment("orphan", "stray", "## Stray\n\nNobody names it.\n");

        List<RefusalViolation> violations = pack.graph().staged();

        // the companion faults of the store come first, remapped; then the orphan, one violation per fault
        assertThat(violations)
                .extracting(RefusalViolation::code, RefusalViolation::pointer)
                .containsExactly(
                        tuple(CODE, pack.yamlOf("missing", "missing")),
                        tuple(CODE, pack.yamlOf("shared", "second")),
                        tuple(CODE, pack.yamlOf("twice", "twice")),
                        tuple(CODE, pack.fragmentOf("orphan", "stray")));
    }

    // ---- precedence (T-08) -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("prompt_fragments: [] answers the schema path; an absent key answers prompt_fragment_invalid (D12)")
    void anEmptyListIsASchemaViolationAndAnAbsentKeyIsPromptFragmentInvalid() throws IOException {
        Enhancer pack = new Enhancer()
                .enhances("brief", "base::action:software-dev/plan")
                .graph();

        pack.file(pack.yamlOf(TYPE, "brief"), "schema_version: '1.0'\nid: brief\nprompt_fragments: []\n");
        assertThat(pack.staged())
                .extracting(RefusalViolation::code, RefusalViolation::pointer)
                .containsExactly(tuple("schema", pack.yamlOf(TYPE, "brief")));
        assertThat(pack.validate()).isEmpty();
        assertThat(pack.validateAll())
                .anySatisfy(finding -> assertThat(finding.code()).isEqualTo(PackFindingCode.ARTIFACT_SCHEMA_ERROR));

        pack.file(pack.yamlOf(TYPE, "brief"), "schema_version: '1.0'\nid: brief\n");
        assertThat(pack.staged()).singleElement().satisfies(violation -> {
            assertThat(violation.code()).isEqualTo(CODE);
            assertThat(violation.pointer()).isEqualTo(pack.yamlOf(TYPE, "brief"));
        });
        assertThat(pack.validate())
                .singleElement()
                .satisfies(finding -> assertThat(finding.file()).isEqualTo(pack.yamlOf(TYPE, "brief")));
    }

    @Test
    @DisplayName(
            "a slot that breaks <namespace>:<name> answers the schema path; a repeated slot answers prompt_fragment_invalid")
    void aBadSlotIsASchemaViolationAndARepeatedSlotIsPromptFragmentInvalid() throws IOException {
        Enhancer pack = new Enhancer()
                .fragment(TYPE, "one", "## One\n\nFirst.\n")
                .fragment(TYPE, "two", "## Two\n\nSecond.\n")
                .enhances("brief", "base::action:software-dev/plan")
                .graph();

        pack.enhancement(TYPE, "brief", "Bad:Id", "one.prompt-fragment.md");
        assertThat(pack.staged())
                .extracting(RefusalViolation::code, RefusalViolation::pointer)
                .containsExactly(tuple("schema", pack.yamlOf(TYPE, "brief")));
        assertThat(pack.validate()).isEmpty();

        pack.enhancement(TYPE, "brief", "spdd:slot", "one.prompt-fragment.md", "spdd:slot", "two.prompt-fragment.md");
        assertThat(pack.staged()).singleElement().satisfies(violation -> {
            assertThat(violation.code()).isEqualTo(CODE);
            assertThat(violation.pointer()).isEqualTo(pack.yamlOf(TYPE, "brief"));
        });
        assertThat(pack.validate()).extracting(PackFinding::file).containsExactly(pack.yamlOf(TYPE, "brief"));
    }

    @Test
    @DisplayName(
            "a schema-invalid file, or an id that differs from its stem, gets no semantic rule: only the schema violation")
    void aSchemaInvalidFileOrAMisnamedIdGetsNoSemanticRule() throws IOException {
        Enhancer pack = new Enhancer().pair("brief").graph();
        String yaml = pack.yamlOf(TYPE, "brief");

        pack.file(
                yaml,
                "schema_version: '1.0'\nid: other\nprompt_fragments:\n  - slot: spdd:one\n    path: brief.prompt-fragment.md\n");
        assertThat(pack.staged())
                .extracting(RefusalViolation::code, RefusalViolation::pointer)
                .containsExactly(tuple("schema", yaml));
        assertThat(pack.validate()).isEmpty();
        assertThat(pack.validateAll())
                .filteredOn(finding -> finding.file().equals(yaml))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.code()).isEqualTo(PackFindingCode.ARTIFACT_SCHEMA_ERROR);
                    assertThat(finding.place()).containsExactly("id");
                });

        pack.file(
                yaml,
                "schema_version: '1.0'\nid: brief\nunknown: key\nprompt_fragments:\n  - slot: spdd:one\n    path: brief.prompt-fragment.md\n");
        assertThat(pack.staged())
                .extracting(RefusalViolation::code)
                .as("a schema violation, and no semantic rule beside it")
                .startsWith("schema")
                .doesNotContain(CODE);
    }

    @Test
    @DisplayName("a fragment that breaks MarkdownCodec and holds a marker answers the codec fault only")
    void aCodecFaultWinsOverAMarker() throws IOException {
        Enhancer pack = new Enhancer().pair("brief").graph();
        pack.fragment(TYPE, "brief", "## Guidance\r\n\r\n<!-- other:slot:start -->\r\n");

        assertThat(pack.staged())
                .extracting(RefusalViolation::code, RefusalViolation::pointer)
                .containsExactly(tuple(CODE, pack.yamlOf(TYPE, "brief")));
        assertThat(pack.staged().getFirst().detail())
                .contains("brief.prompt-fragment.md")
                .doesNotContain("slot marker");
        assertThat(pack.validate()).singleElement().satisfies(finding -> {
            assertThat(finding.file()).isEqualTo(pack.fragmentOf(TYPE, "brief"));
            assertThat(finding.detail()).doesNotContain("slot marker");
        });
    }

    @Test
    @DisplayName("a fragment with a byte-order mark and a marker answers the codec fault only, as a CRLF one does")
    void aByteOrderMarkIsACodecFaultToo() throws IOException {
        Enhancer pack = new Enhancer().pair("brief").graph();
        pack.fragment(TYPE, "brief", "\uFEFF## Guidance\n\n<!-- other:slot:start -->\n");

        assertThat(pack.staged())
                .extracting(RefusalViolation::code, RefusalViolation::pointer)
                .containsExactly(tuple(CODE, pack.yamlOf(TYPE, "brief")));
        assertThat(pack.staged().getFirst().detail())
                .contains("brief.prompt-fragment.md")
                .doesNotContain("slot marker");
        assertThat(pack.validate()).singleElement().satisfies(finding -> {
            assertThat(finding.file()).isEqualTo(pack.fragmentOf(TYPE, "brief"));
            assertThat(finding.detail()).doesNotContain("slot marker");
        });
    }

    @Test
    @DisplayName("a slot marker in a fragment is prompt_fragment_invalid at the fragment; the enhancement is held")
    void aSlotMarkerIsAtTheFragment() throws IOException {
        Enhancer pack = new Enhancer().pair("brief").graph();
        pack.fragment(TYPE, "brief", "## Guidance\n\n<!-- other:slot:start -->\n");

        assertThat(pointers(pack.staged())).containsExactly(pack.fragmentOf(TYPE, "brief"));
        assertThat(pack.loaded().artifacts(ArtifactKind.STEP_ENHANCEMENT)).hasSize(1);
    }

    // ---- the first violation of a composing read, in assembly order (E-3) ------------------------------------------

    @Test
    @DisplayName("the composing read names the same first file on every read: two rule faults in two files")
    void theReadNamesTheFirstRuleFaultInPathOrderOnEveryRead() throws IOException {
        Enhancer pack = new Enhancer()
                .pair(TYPE, "zeta", "plan")
                .pair(TYPE, "alpha", "plan")
                .overrides("zeta", "mission_type:software-dev")
                .overrides("alpha", "mission_type:software-dev")
                .graph();

        for (int i = 0; i < 3; i++) {
            assertThat(pack.read())
                    .hasValueSatisfying(refusal -> assertThat(refusal.getMessage())
                            .contains(pack.yamlOf(TYPE, "alpha"))
                            .doesNotContain(pack.yamlOf(TYPE, "zeta")));
        }
        assertThat(pointers(pack.staged())).containsExactly(pack.yamlOf(TYPE, "alpha"), pack.yamlOf(TYPE, "zeta"));
    }

    @Test
    @DisplayName("a binding-level fault beside a rule fault is the one a composing read names")
    void aBindingLevelFaultIsNamedBeforeARuleFault() throws IOException {
        Enhancer pack = new Enhancer()
                .pair("brief")
                .overrides("brief", "mission_type:software-dev")
                .graph();
        pack.file("tactics/broken.tactic.yaml", "id: [unclosed");

        assertThat(pack.read())
                .hasValueSatisfying(refusal -> assertThat(refusal.getMessage())
                        .contains("tactics/broken.tactic.yaml")
                        .doesNotContain(pack.yamlOf(TYPE, "brief")));
        assertThat(pointers(pack.staged())).contains("tactics/broken.tactic.yaml", pack.yamlOf(TYPE, "brief"));
    }

    // ---- a graph that cannot be read is refused on its own ----------------------------------------------------------

    @Test
    @DisplayName(
            "a graph document that cannot be read is drg_schema_error at registration; no enhancement edge rule is judged")
    void anUnreadableGraphIsRefusedOnItsOwn() throws IOException {
        Enhancer pack = new Enhancer().pair("brief");
        pack.file(GRAPH, "nodes: [\n");

        assertThatThrownBy(() -> store.load(pack.dir))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR));
        assertThat(pack.staged())
                .singleElement()
                .satisfies(violation -> assertThat(violation.code()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR.token()));
        assertThat(pack.service().load(new PackIdentifier("enhancer")).violations())
                .as("the aggregate, which reads the graphs too, judges no edge rule over a graph it could not read")
                .extracting(PackElementViolation::kind)
                .doesNotContain(CODE);
    }
}
