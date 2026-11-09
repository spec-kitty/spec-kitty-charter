package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.LayerMerge;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Failure;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.StepEnhancement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A step enhancement composes through its kind binding alone (A8): the URN is in {@code defined}, so a same-id copy at a
 * higher level replaces the lower one by layer override, a sibling contest is refused until a descriptor
 * {@code overrides} entry settles it, and only the winning copy's fragments are served (A11). The control for the
 * source fingerprint (NFR-001) lives here too: a pack with one enhancement does not digest as the pack without it.
 *
 * <p>Every fixture is the two-pack shape of the spdd pack: a base whose software-dev step prompt declares the slot
 * empty and its graph declares {@code action:software-dev/plan}, and an enhancing pack that holds only the enhancement,
 * its fragment and one qualified {@code enhances} edge, and depends on the base.
 */
@DisplayName("A step enhancement composes through its binding (IC-01, A8, A11)")
class StepEnhancementCompositionTest {

    private static final String URN = "step_enhancement:spdd-plan";
    private static final String BASE_ID = "01M6ZRYK0000000000000000BS";

    private static final String BASE_MISSION = """
            schema_version: "1.0"
            id: software-dev
            title: Software development
            description: Build software
            steps:
              - id: plan
                title: Plan
                role: planner
                prompt_path: steps/plan.prompt.md
            """;
    private static final String BASE_PROMPT =
            "# Plan\n\n<!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->\n";
    private static final String BASE_GRAPH = """
            schema_version: '1.0'
            generated_at: STATIC
            nodes:
            - urn: action:software-dev/plan
              kind: action
              label: plan
            edges: []
            """;

    @TempDir
    Path host;

    private PackLocation base() throws IOException {
        Path dir = Files.createDirectories(host.resolve("base"));
        write(
                dir,
                "pack.yaml",
                "pack_id: " + BASE_ID + "\npack_version: 1.0.0\nparent_pack: null\nname: base\npack_level: org\n");
        write(dir, "missions/software-dev/software-dev.mission-type.yaml", BASE_MISSION);
        write(dir, "missions/software-dev/steps/plan.prompt.md", BASE_PROMPT);
        write(dir, "mission_type.graph.yaml", BASE_GRAPH);
        return new PackLocation("base", PackLevel.ORG, dir);
    }

    /** An enhancing pack of {@code name} at {@code level} that enhances base's plan step with enhancement {@code id}. */
    private PackLocation enhancer(String name, String packId, PackLevel level, String id, String fragment, String extra)
            throws IOException {
        Path dir = Files.createDirectories(host.resolve(name));
        write(
                dir,
                "pack.yaml",
                "pack_id: " + packId + "\npack_version: 1.0.0\nparent_pack: null\nname: " + name + "\npack_level: "
                        + level.token() + "\ndepends_on:\n- pack_id: " + BASE_ID + "\n  range: '>=1.0'\n" + extra);
        write(
                dir,
                "missions/software-dev/enhancements/" + id + ".step-enhancement.yaml",
                "schema_version: '1.0'\nid: " + id + "\nprompt_fragments:\n  - slot: spdd:reasons-block\n    path: "
                        + id + ".prompt-fragment.md\n");
        write(dir, "missions/software-dev/enhancements/" + id + ".prompt-fragment.md", fragment);
        write(
                dir,
                "enhancements.graph.yaml",
                "schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges:\n- source: step_enhancement:" + id
                        + "\n  target: base::action:software-dev/plan\n  relation: enhances\n");
        return new PackLocation(name, level, dir);
    }

    private PackLocation settler(String name, String packId, String member) throws IOException {
        Path dir = Files.createDirectories(host.resolve(name));
        write(
                dir,
                "pack.yaml",
                "pack_id: " + packId + "\npack_version: 1.0.0\nparent_pack: null\nname: " + name
                        + "\npack_level: org\noverrides:\n- artifact: " + URN + "\n  member: " + member + "\n");
        return new PackLocation(name, PackLevel.ORG, dir);
    }

    private static void write(Path pack, String relative, String text) throws IOException {
        Path file = pack.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    private static DomainRepositoryService service(List<PackLocation> locations) {
        return new DomainRepositoryService(() -> locations, new YamlSerializationStrategy());
    }

    /** What a read composes: the packs resolved as the compose resolves them, then merged by level. */
    private static MergedClosure compose(List<PackLocation> locations) {
        DomainRepositoryService service = service(locations);
        List<PackUnderResolution> packs = new ArrayList<>();
        Map<String, PackCompositionFacts> facts = new HashMap<>();
        for (PackLocation location : locations) {
            PackIdentifier key = new PackIdentifier(location.name());
            PackCompositionFacts held = service.compositionFacts(key);
            packs.add(new PackUnderResolution(
                    new ConfiguredPack(key, location.level()),
                    service.load(key).descriptor(),
                    held.changed(),
                    held.defined()));
            facts.put(location.name(), held);
        }
        return LayerMerge.merge(PackCompositionResolver.resolve(packs), facts, new PerPackPolicy(Map.of()));
    }

    private static String fragmentOf(PackLocation winner) {
        StepEnhancement enhancement = (StepEnhancement) service(List.of(winner))
                .load(new PackIdentifier(winner.name()))
                .artifacts(ArtifactKind.STEP_ENHANCEMENT)
                .getFirst();
        return enhancement.promptFragments().getFirst().fragment().body().preamble();
    }

    private static Urn urn() {
        return new Urn(NodeKind.STEP_ENHANCEMENT, "spdd-plan");
    }

    @Test
    @DisplayName("the URN is in defined through the binding alone, with no edit to the composition")
    void theUrnJoinsDefined() throws IOException {
        PackLocation base = base();
        PackLocation enhancer =
                enhancer("enhancer", "01M6ZRYK0000000000000000E1", PackLevel.ORG, "spdd-plan", "Fill it.\n", "");

        PackCompositionFacts facts = service(List.of(base, enhancer)).compositionFacts(new PackIdentifier("enhancer"));

        assertThat(facts.defined()).contains(URN);
        assertThat(service(List.of(base, enhancer))
                        .compositionFacts(new PackIdentifier("base"))
                        .defined())
                .as("the control: the base does not define the enhancement")
                .doesNotContain(URN);
        assertThat(compose(List.of(base, enhancer)).winnerOf(urn()))
                .hasValueSatisfying(
                        winner -> assertThat(winner.packId().value()).isEqualTo("enhancer"));
    }

    @Test
    @DisplayName("a same-id enhancement at a higher level replaces the lower one by layer override (ADR-0048 d10)")
    void aHigherLevelCopyReplacesTheLowerOne() throws IOException {
        PackLocation base = base();
        PackLocation lower =
                enhancer("enh-org", "01M6ZRYK0000000000000000E1", PackLevel.ORG, "spdd-plan", "From the org.\n", "");
        PackLocation higher = enhancer(
                "enh-project", "01M6ZRYK0000000000000000E2", PackLevel.PROJECT, "spdd-plan", "From the project.\n", "");

        MergedClosure merged = compose(List.of(base, lower, higher));

        assertThat(merged.winnerOf(urn()))
                .hasValueSatisfying(
                        winner -> assertThat(winner.packId().value()).isEqualTo("enh-project"));
        assertThat(merged.layerOverrides())
                .as("the lower copy is a layer override, not a conflict")
                .anySatisfy(override -> assertThat(override.urn()).isEqualTo(urn()));
        assertThat(fragmentOf(higher)).isEqualTo("From the project.");
        assertThat(compose(List.of(base, lower)).winnerOf(urn()))
                .as("the control: without the higher copy the lower one is in force")
                .hasValueSatisfying(
                        winner -> assertThat(winner.packId().value()).isEqualTo("enh-org"));
    }

    @Test
    @DisplayName(
            "a sibling contest over one enhancement id is refused, and a descriptor overrides entry leaves one winner")
    void aSettledSiblingContestLeavesOneWinner() throws IOException {
        PackLocation base = base();
        PackLocation first =
                enhancer("enh-a", "01M6ZRYK0000000000000000E1", PackLevel.ORG, "spdd-plan", "From a.\n", "");
        PackLocation second =
                enhancer("enh-b", "01M6ZRYK0000000000000000E2", PackLevel.ORG, "spdd-plan", "From b.\n", "");

        assertThatThrownBy(() -> compose(List.of(base, first, second)))
                .isInstanceOfSatisfying(
                        PackCompositionResolver.Refusal.class,
                        refusal -> assertThat(refusal.failure()).isEqualTo(Failure.SIBLING_CONFLICT));

        PackLocation settler = settler("settler", "01M6ZRYK0000000000000000S1", "01M6ZRYK0000000000000000E2");
        MergedClosure merged = compose(List.of(base, first, second, settler));

        assertThat(merged.winnerOf(urn()))
                .hasValueSatisfying(
                        winner -> assertThat(winner.packId().value()).isEqualTo("enh-b"));
        assertThat(fragmentOf(second)).isEqualTo("From b.");
    }

    @Test
    @DisplayName("two sibling enhancers of one step with different ids compose, each in force")
    void twoDifferentIdsCompose() throws IOException {
        PackLocation base = base();
        PackLocation first =
                enhancer("enh-a", "01M6ZRYK0000000000000000E1", PackLevel.ORG, "spdd-plan", "From a.\n", "");
        PackLocation second =
                enhancer("enh-b", "01M6ZRYK0000000000000000E2", PackLevel.ORG, "rival-plan", "From b.\n", "");

        MergedClosure merged = compose(List.of(base, first, second));

        assertThat(merged.winnerOf(urn())).isPresent();
        assertThat(merged.winnerOf(new Urn(NodeKind.STEP_ENHANCEMENT, "rival-plan")))
                .isPresent();
    }

    @Test
    @DisplayName("NFR-001 positive control: an enhancement changes the pack's digest, and so does a fragment byte")
    void anEnhancementAndAFragmentByteChangeTheDigest() throws IOException {
        PackLocation without = base();
        String plain = FileSystemPackSources.digestOfPack(without.root(), "base");
        assertThat(FileSystemPackSources.digestOfPack(without.root(), "base"))
                .as("the control: digesting twice is stable")
                .isEqualTo(plain);

        write(
                without.root(),
                "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml",
                "schema_version: '1.0'\nid: spdd-plan\nprompt_fragments:\n  - slot: spdd:reasons-block\n    path: spdd-plan.prompt-fragment.md\n");
        write(without.root(), "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md", "Fill it.\n");
        String with = FileSystemPackSources.digestOfPack(without.root(), "base");
        write(without.root(), "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md", "Fill it!\n");
        String edited = FileSystemPackSources.digestOfPack(without.root(), "base");

        assertThat(with).isNotEqualTo(plain);
        assertThat(edited).isNotEqualTo(with).isNotEqualTo(plain);
    }
}
