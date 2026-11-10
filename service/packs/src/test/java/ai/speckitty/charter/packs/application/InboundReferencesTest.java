package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.ORG_PACK;
import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.ulid;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The inbound scan of a whole-pack replace (FR-036, ADR-0065 D4, IC-04 step 8): an artifact the replace removes that
 * another artifact, preset or descriptor still refers to refuses the replace with {@code 409 artifact_referenced},
 * listing the referrers, before any compose. The scan reads the edges, the presets and the descriptor {@code overrides}
 * of every pack, and never the data-only fields of ADR-0064 decision 11. Each refusal is paired with a control that
 * removes something nothing refers to.
 */
class InboundReferencesTest {

    private static final String BASE_ID = ulid('C');
    private static final String DERIVED_ID = ulid('D');
    private static final String OTHER_ID = ulid('E');
    private static final String DEPENDS_ON_BASE = "depends_on:\n  - pack_id: " + BASE_ID + "\n    range: '>=1.0'\n";
    private static final String A_FILE = "directives/A_ONE.directive.yaml";

    private static Packs base(String version) {
        return Packs.of(BASE_ID, "base", version, "org")
                .directive("A_ONE", "A")
                .directive("B_ONE", "B")
                .directive("KEEP", "Keep")
                .tactic("t-one");
    }

    private static Packs derived(String version, String extraDescriptor) {
        return Packs.of(DERIVED_ID, "derived", version, "project", DEPENDS_ON_BASE + extraDescriptor)
                .directive("D_ONE", "Own");
    }

    private static String edge(String source, String target) {
        return "schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges:\n- source: " + source + "\n  target: "
                + target + "\n  relation: requires\n";
    }

    private static final String PRESET =
            "schema_version: \"1.0\"\nid: starter\ndescription: A starter.\nactivated_directives:\n  - id: A_ONE\n";

    private static Rig rigWith(Path host, Packs dependent) {
        Rig rig = new Rig(host);
        assertThat(rig.register("base", rig.stage(base("1.0.0")), Preconditions.none())
                        .outcome())
                .isEqualTo(Outcome.CREATED);
        assertThat(rig.register("derived", rig.stage(dependent), Preconditions.none())
                        .outcome())
                .as("the dependent pack is a sound upload")
                .isEqualTo(Outcome.CREATED);
        return rig;
    }

    /** Asserts that removing A_ONE is refused naming the referrer, and removing B_ONE, which nothing refers to, is not. */
    private static void assertRemovalOfAIsRefusedAndOfBIsNot(Rig rig, String referrer) {
        Packs withoutA = base("2.0.0").with(A_FILE, null);

        CharterRefusal refused = refusal(() -> rig.register("base", rig.stage(withoutA), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.ARTIFACT_REFERENCED);
        assertThat(refused.violations())
                .extracting(RefusalViolation::pointer)
                .as("every referrer is named, by its pack and artifact")
                .contains("derived/" + referrer);
        assertThat(refused.packs())
                .extracting(RefusalPack::packId)
                .as("the referring packs, each once, as the contract's packs member lists them")
                .containsExactly("derived");
        assertThat(rig.filesOf(rig.packs.resolve("base"))).as("nothing changed").containsKey(A_FILE);

        Packs withoutB = base("2.0.0").with("directives/B_ONE.directive.yaml", null);
        assertThat(rig.register("base", rig.stage(withoutB), Preconditions.none())
                        .outcome())
                .as("the control: B_ONE is referred to by nothing")
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void anEdgeOfAnotherPackOntoARemovedArtifactRefusesTheReplace(@TempDir Path host) {
        Rig rig = rigWith(
                host, derived("1.0.0", "").file("refs.graph.yaml", edge("directive:D_ONE", "base::directive:A_ONE")));

        assertRemovalOfAIsRefusedAndOfBIsNot(rig, "directive:D_ONE");
    }

    @Test
    void aQualifiedEdgeOntoARemovedArtifactRefusesTheReplace(@TempDir Path host) {
        Rig rig = rigWith(
                host, derived("1.0.0", "").file("refs.graph.yaml", edge("directive:D_ONE", "base::directive:A_ONE")));

        assertRemovalOfAIsRefusedAndOfBIsNot(rig, "directive:D_ONE");
    }

    @Test
    void aPresetItemOfAnotherPackNamingARemovedArtifactRefusesTheReplace(@TempDir Path host) {
        Rig rig = rigWith(host, derived("1.0.0", "").file("presets/starter.yaml", PRESET));

        assertRemovalOfAIsRefusedAndOfBIsNot(rig, "preset:starter");
    }

    @Test
    void aDescriptorOverridesEntryOnARemovedArtifactRefusesTheReplace(@TempDir Path host) {
        Rig rig = rigWith(
                host, derived("1.0.0", "overrides:\n  - artifact: directive:A_ONE\n    member: " + BASE_ID + "\n"));

        assertRemovalOfAIsRefusedAndOfBIsNot(rig, "overrides:directive:A_ONE");
    }

    @Test
    void aReferrerTheSameRequestRemovesDoesNotBlockButOneItKeepsDoes(@TempDir Path host) {
        Rig rig = new Rig(host);
        Packs holder = Packs.of(ORG_PACK, "pack-x", "1.0.0", "org")
                .directive("X", "X")
                .directive("Y", "Y")
                .file("refs.graph.yaml", edge("directive:Y", "directive:X"));
        rig.register("pack-x", rig.stage(holder), Preconditions.none());

        CharterRefusal keepsY = refusal(() -> rig.register(
                "pack-x",
                rig.stage(holder.with("directives/X.directive.yaml", null).with("pack.yaml", versioned("2.0.0"))),
                Preconditions.none()));

        assertThat(keepsY.code()).isEqualTo(RefusalCode.ARTIFACT_REFERENCED);
        assertThat(keepsY.violations()).extracting(RefusalViolation::pointer).contains("pack-x/directive:Y");

        Packs removesBoth = Packs.of(ORG_PACK, "pack-x", "2.0.0", "org");
        assertThat(rig.register("pack-x", rig.stage(removesBoth), Preconditions.none())
                        .outcome())
                .as("Y is removed by the same request, so its edge onto the removed X refers to nothing")
                .isEqualTo(Outcome.REPLACED);
    }

    private static String versioned(String version) {
        return Packs.descriptorText(ORG_PACK, "pack-x", version, "org", "");
    }

    @Test
    void aBareReferenceThatAnotherPackResolvesToItsOwnArtifactIsNotInbound(@TempDir Path host) {
        Packs own = derived("1.0.0", "")
                .directive("A_ONE", "Derived's own A_ONE")
                .file("refs.graph.yaml", edge("directive:D_ONE", "directive:A_ONE"));
        Rig rig = rigWith(host, own);

        assertThat(rig.register("base", rig.stage(base("2.0.0").with(A_FILE, null)), Preconditions.none())
                        .outcome())
                .as("derived reads its own A_ONE, not base's")
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void aPackOutsideTheClosureWhoseBareReferenceNamesTheIdIsNotInbound(@TempDir Path host) {
        Rig rig = rigWith(host, derived("1.0.0", ""));
        Packs unrelated = Packs.of(OTHER_ID, "other", "1.0.0", "org")
                .directive("O_ONE", "O")
                .file("refs.graph.yaml", edge("directive:O_ONE", "directive:A_ONE"));
        rig.register("other", rig.stage(unrelated), Preconditions.none());

        assertThat(rig.register("base", rig.stage(base("2.0.0").with(A_FILE, null)), Preconditions.none())
                        .outcome())
                .as("other does not build on base, so its A_ONE is not base's")
                .isEqualTo(Outcome.REPLACED);
    }

    /** One data-only field of ADR-0064 decision 11 that names the removed artifact, which is no inbound reference. */
    static Stream<String> skillFields() {
        return Stream.of("enhances", "overrides");
    }

    @ParameterizedTest(name = "a skill {0} naming the removed artifact")
    @MethodSource("skillFields")
    void aSkillsDataOnlyFieldIsNotInbound(String field, @TempDir Path host) {
        String skill = "schema_version: \"1.0\"\nid: land\ntitle: Land\ndescription: Use when landing.\nform: prompt\n"
                + "body_path: land.skill.md\ninvocation:\n  user_invocable: true\n" + field + ": directive:A_ONE\n";
        Packs withSkill =
                derived("1.0.0", "").file("skills/land.skill.yaml", skill).file("skills/land.skill.md", "Land it.\n");
        Rig rig = rigWith(host, withSkill);

        assertThat(rig.register("base", rig.stage(base("2.0.0").with(A_FILE, null)), Preconditions.none())
                        .outcome())
                .as("the skill's %s is its own install data, not an edge", field)
                .isEqualTo(Outcome.REPLACED);
    }

    // ---- the data-only fields of ADR-0064 decision 11, each as a real pack file
    // ---------------------------------------

    /** One data-only field: a real file of the dependent pack that names {@code target}, and the artifact it names. */
    record NearMiss(String label, String file, String text, String removedFile, String target) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final String T_FILE = "tactics/t-one.tactic.yaml";
    private static final String PROFILE = """
            profile-id: prof
            name: Prof
            role: implementer
            purpose: x
            specialization:
              primary-focus: f
            """;

    private static final String GLOSSARY = """
            id: g
            provenance: org
            terms:
            - surface: term
              definition: d
              confidence: 1.0
              status: active
              see_also: [A_ONE, directive:A_ONE]
            """;

    private static final String CONTRACT = """
            id: c
            schema_version: '1.0'
            action: plan
            mission: m
            steps:
            - id: first
              description: d
              delegates_to:
                kind: directive
                candidates: [A_ONE]
            """;

    static Stream<NearMiss> nearMisses() {
        return Stream.of(
                new NearMiss(
                        "agent-profile directive-references",
                        "agent_profiles/prof.agent.yaml",
                        PROFILE + "directive-references:\n  - code: A_ONE\n    name: A\n    rationale: r\n",
                        A_FILE,
                        "directive:A_ONE"),
                new NearMiss(
                        "agent-profile tactic-references",
                        "agent_profiles/prof.agent.yaml",
                        PROFILE + "tactic-references:\n  - id: t-one\n    rationale: r\n",
                        T_FILE,
                        "tactic:t-one"),
                new NearMiss(
                        "glossary see_also",
                        "glossary_packs/g.glossary-pack.yaml",
                        GLOSSARY,
                        A_FILE,
                        "directive:A_ONE"),
                new NearMiss(
                        "mission-step-contract delegation",
                        "mission_step_contracts/c.step-contract.yaml",
                        CONTRACT,
                        A_FILE,
                        "directive:A_ONE"));
    }

    @ParameterizedTest(name = "{0} naming the removed artifact")
    @MethodSource("nearMisses")
    void aDataOnlyFieldIsNotInboundButAnEdgeOntoTheSameArtifactIs(NearMiss field, @TempDir Path host) {
        Packs withField = derived("1.0.0", "").file(field.file(), field.text());
        Rig near = rigWith(host.resolve("near"), withField);
        Packs withoutTarget = base("2.0.0").with(field.removedFile(), null);

        assertThat(near.register("base", near.stage(withoutTarget), Preconditions.none())
                        .outcome())
                .as("the %s is selection or cross-reference data, not an edge", field.label())
                .isEqualTo(Outcome.REPLACED);

        Packs withEdge = withField.file("refs.graph.yaml", edge("directive:D_ONE", "base::" + field.target()));
        Rig control = rigWith(host.resolve("control"), withEdge);
        assertThat(refusal(() -> control.register("base", control.stage(withoutTarget), Preconditions.none()))
                        .code())
                .as("the control: the same pack with an edge onto the same artifact")
                .isEqualTo(RefusalCode.ARTIFACT_REFERENCED);
    }

    // ---- an unreadable stored pack, and a rename (DM-01M6V4WMF8CD8NBN7A065E9ZQ5) ---------------------------------

    @Test
    void aStoredPackThatCannotBeReadStillHasItsRemovedArtifactScanned(@TempDir Path host) throws IOException {
        Rig rig = rigWith(
                host, derived("1.0.0", "").file("refs.graph.yaml", edge("directive:D_ONE", "base::directive:A_ONE")));
        Files.writeString(
                rig.packs.resolve("base/directives/KEEP.directive.yaml"),
                Packs.directiveText("KEEP", "Keep") + "references:\n  - directive:X\n");
        Packs withoutA = base("2.0.0").with(A_FILE, null);

        CharterRefusal refused = refusal(() -> rig.register("base", rig.stage(withoutA), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.ARTIFACT_REFERENCED);
        assertThat(rig.filesOf(rig.packs.resolve("base")))
                .as("A_ONE is still there")
                .containsKey(A_FILE);

        Packs keepsA = base("2.0.0");
        assertThat(rig.register("base", rig.stage(keepsA), Preconditions.none()).outcome())
                .as("the control: the fixed upload keeps every referenced id and repairs the pack")
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void anArtifactFileThatDoesNotBindCountsByTheIdItDeclaresForTheScan(@TempDir Path host) throws IOException {
        Rig rig = rigWith(
                host.resolve("a"),
                derived("1.0.0", "").file("refs.graph.yaml", edge("directive:D_ONE", "base::directive:A_ONE")));
        Files.delete(rig.packs.resolve("base").resolve(A_FILE));
        Files.writeString(
                rig.packs.resolve("base/directives/DECOY.directive.yaml"),
                Packs.directiveText("A_ONE", "A") + "references:\n  - directive:X\n");
        Packs withoutDecoy = base("2.0.0").with(A_FILE, null);

        assertThat(refusal(() -> rig.register("base", rig.stage(withoutDecoy), Preconditions.none()))
                        .code())
                .as("DECOY.directive.yaml holds A_ONE, which derived refers to")
                .isEqualTo(RefusalCode.ARTIFACT_REFERENCED);

        Rig control = rigWith(
                host.resolve("b"),
                derived("1.0.0", "").file("refs.graph.yaml", edge("directive:D_ONE", "base::directive:A_ONE")));
        Files.writeString(
                control.packs.resolve("base/directives/DECOY.directive.yaml"),
                Packs.directiveText("FREE_ONE", "Free") + "references:\n  - directive:X\n");
        assertThat(control.register("base", control.stage(base("2.0.0")), Preconditions.none())
                        .outcome())
                .as("the control: the same file holding an id nothing refers to")
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void aQualifiedReferenceToTheOldNameStillBlocksARenamingReplace(@TempDir Path host) {
        Rig rig = rigWith(
                host, derived("1.0.0", "").file("refs.graph.yaml", edge("directive:D_ONE", "base::directive:A_ONE")));
        Packs renamedWithoutA = Packs.of(BASE_ID, "base2", "2.0.0", "org")
                .directive("B_ONE", "B")
                .directive("KEEP", "Keep");

        assertThat(refusal(() -> rig.register("base", rig.stage(renamedWithoutA), Preconditions.none()))
                        .code())
                .isEqualTo(RefusalCode.ARTIFACT_REFERENCED);

        Packs renamedWithA = renamedWithoutA.directive("A_ONE", "A");
        assertThat(refusal(() -> rig.register("base", rig.stage(renamedWithA), Preconditions.none()))
                        .code())
                .as("the scan has nothing to refuse once A_ONE stays; the impact check refuses the rename, since"
                        + " derived's qualified references name base")
                .isEqualTo(RefusalCode.PACK_REFERENCE_PACK_MISSING);
    }
}
