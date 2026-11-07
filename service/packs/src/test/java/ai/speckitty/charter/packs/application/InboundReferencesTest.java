package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.ORG_PACK;
import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.ulid;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.InboundReferences.Referrer;
import ai.speckitty.charter.packs.application.InboundReferences.StagedTree;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
                .directive("KEEP", "Keep");
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
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        rig.register("derived", rig.stage(dependent), Preconditions.none());
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
        assertThat(rig.filesOf(rig.packs.resolve("base"))).as("nothing changed").containsKey(A_FILE);

        Packs withoutB = base("2.0.0").with("directives/B_ONE.directive.yaml", null);
        assertThat(rig.register("base", rig.stage(withoutB), Preconditions.none())
                        .outcome())
                .as("the control: B_ONE is referred to by nothing")
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void anEdgeOfAnotherPackOntoARemovedArtifactRefusesTheReplace(@TempDir Path host) {
        Rig rig =
                rigWith(host, derived("1.0.0", "").file("refs.graph.yaml", edge("directive:D_ONE", "directive:A_ONE")));

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

    // ---- the data-only fields that the facts keep only as references --------------------------------------------

    private static final Urn A_ONE = new Urn(NodeKind.DIRECTIVE, "A_ONE");

    private static PackDescriptor descriptor(String packId, String name, List<PackDependency> dependencies) {
        return new PackDescriptor(
                packId,
                "1.0.0",
                Optional.empty(),
                name,
                Optional.of(PackLevel.PROJECT),
                Optional.empty(),
                PackPaths.none(),
                dependencies);
    }

    /** The referrers of A_ONE when "derived" builds on the staged "base" and its facts are {@code derivedFacts}. */
    private static List<Referrer> scanWith(PackCompositionFacts derivedFacts) {
        PackDescriptor baseDescriptor = descriptor(BASE_ID, "base", List.of());
        PackDescriptor derivedDescriptor =
                descriptor(DERIVED_ID, "derived", List.of(new PackDependency(BASE_ID, ">=1.0")));
        PackIdentifier derivedId = new PackIdentifier("derived");
        PackDescriptorRepository descriptors = new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier pack) {
                return derivedDescriptor;
            }

            @Override
            public Optional<PackDescriptor> find(PackIdentifier pack) {
                return Optional.of(derivedDescriptor);
            }

            @Override
            public void save(PackIdentifier pack, PackDescriptor descriptor) {
                throw new UnsupportedOperationException("the scan only reads");
            }
        };
        InboundReferences scan = new InboundReferences(
                () -> List.of(new ConfiguredPack(derivedId, PackLevel.PROJECT)), descriptors, pack -> derivedFacts);
        StagedTree staged =
                new StagedTree(Optional.of(new PackIdentifier("base")), baseDescriptor, PackCompositionFacts.NONE);
        return scan.scan(staged, Set.of(A_ONE));
    }

    static Stream<String> dataOnlyFields() {
        return Stream.of(
                "agent-profile directive-references",
                "agent-profile tactic, toolguide and styleguide references",
                "glossary see_also",
                "mission-step-contract delegations");
    }

    @ParameterizedTest(name = "{0} names the removed artifact")
    @MethodSource("dataOnlyFields")
    void aDataOnlyFieldTheFactsKeepAsAReferenceIsNotInbound(String field) {
        PackCompositionFacts keepsItAsAReference =
                new PackCompositionFacts(List.of(), List.of("directive:D_ONE"), List.of("directive:A_ONE"));
        Relationship edge = Relationship.of(new Urn(NodeKind.DIRECTIVE, "D_ONE"), A_ONE, RelationType.REQUIRES);
        PackCompositionFacts writesAnEdge = new PackCompositionFacts(
                List.of(),
                List.of("directive:D_ONE"),
                List.of("directive:A_ONE"),
                MutabilityPolicySource.empty(),
                List.of(),
                List.of(edge));

        assertThat(scanWith(keepsItAsAReference)).as(field).isEmpty();
        assertThat(scanWith(writesAnEdge))
                .as("the control: the same pack writing an edge onto it")
                .singleElement()
                .satisfies(referrer -> {
                    assertThat(referrer.referrer()).isEqualTo("directive:D_ONE");
                    assertThat(referrer.removed()).isEqualTo(A_ONE);
                });
    }
}
