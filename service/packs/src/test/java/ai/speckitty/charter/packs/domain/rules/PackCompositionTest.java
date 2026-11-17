package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.packs.domain.MissionTypeFacts;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackContents.PromptSlotFacts;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import ai.speckitty.charter.packs.domain.rules.PlantedFixtures.Planted;
import ai.speckitty.charter.packs.examples.PlantedPacks;
import ai.speckitty.charter.packs.infra.storage.TreeReads;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import ai.speckitty.charter.packs.infra.storage.rules.StorageRulesets;
import ai.speckitty.charter.packs.infra.storage.rules.TreeRead;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The compositions' interactions (#554, M-7, T041): one test per gate, suppression or precedence the content rules and
 * the storage rules share. The manifest gate over a {@code TreeRead} is pinned in {@code StorageRulesetsTest}; here it
 * meets a file not held.
 */
@DisplayName("PackRulesets.content() and the storage compositions: their interactions (#554, M-7)")
class PackCompositionTest {

    private static final String TYPE = "missions/rx/rx.mission-type.yaml";

    private static final String SHARD = "mission_type.graph.yaml";

    private static FileRead<PackDescriptor> descriptor(Optional<String> schemaVersion) {
        return new FileRead.Read<>(
                "pack.yaml",
                new PackDescriptor(
                        "01ARWG13C00000000000001ABC",
                        "1.0.0",
                        Optional.empty(),
                        "qa",
                        Optional.of(PackLevel.ORG),
                        Optional.empty(),
                        PackPaths.none(),
                        List.of(),
                        List.of(),
                        schemaVersion));
    }

    private static PackContents contents(
            FileRead<PackDescriptor> descriptor,
            List<FileRead<RawGraph>> shards,
            StepEnhancementFacts enhancements,
            List<PromptSlotFacts> prompts,
            List<MissionTypeFacts> types) {
        return new PackContents(
                new PackRoot("qa", Optional.of(PackLevel.ORG), "loc"),
                descriptor,
                shards,
                new FileRead.Absent<>("drg/fragment.yaml"),
                List.of(),
                List.of(),
                enhancements,
                prompts,
                types);
    }

    private static Path pack(Path host) throws IOException {
        Path pack = PlantedPacks.copyOf("control-a-clean-org-pack", host);
        Path named = pack.resolveSibling("qa");
        Files.move(pack, named);
        return named;
    }

    private static void write(Path pack, String relative, String text) throws IOException {
        Path file = pack.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static TreeRead read(Path pack) {
        return TreeReads.of(new PackRoot("qa", Optional.of(PackLevel.ORG), pack.toString()));
    }

    private static List<ReasonCode> codes(List<Reason> reasons) {
        return reasons.stream().map(Reason::code).toList();
    }

    @Test
    @DisplayName("a file that breaks its schema gets no semantic rule: its repeated id is not judged")
    void aSchemaInvalidFileGetsNoSemanticRule(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        write(pack, "tactics/b.tactic.yaml", "id: a\nschema_version: '1.0'\nname: T\nsteps: []\nbogus: 1\n");

        TreeRead read = read(pack);

        assertThat(read.readFaults())
                .isNotEmpty()
                .allSatisfy(fault -> assertThat(fault.location().file()).isEqualTo("tactics/b.tactic.yaml"))
                .extracting(Reason::code)
                .containsOnly(StorageCodes.ARTIFACT_SCHEMA_ERROR);
        assertThat(PackRulesets.content().apply(read.contents())).isEmpty();
        assertThat(StorageRulesets.verdict(read).violations()).isEqualTo(read.readFaults());

        write(pack, "tactics/b.tactic.yaml", "id: a\nschema_version: '1.0'\nname: T\nsteps:\n- title: s\n");
        assertThat(codes(StorageRulesets.verdict(read(pack)).violations()))
                .as("the twin, read whole, is the repeat")
                .containsExactly(PackCodes.DUPLICATE_ARTIFACT_ID);
    }

    @Test
    @DisplayName("a codec fault wins over a marker: a fragment the read refused is judged by no fragment rule")
    void aCodecFaultWinsOverAMarker() {
        String fragment = "missions/rx/enhancements/brief.prompt-fragment.md";
        StepEnhancementFacts marked = new StepEnhancementFacts(
                List.of(),
                List.of(StepEnhancementFacts.Companion.read(fragment, false, false, "<!-- spdd:x:start -->\n")),
                Set.of(fragment),
                Set.of(),
                List.of());
        StepEnhancementFacts refused = new StepEnhancementFacts(
                List.of(),
                List.of(StepEnhancementFacts.Companion.unreadable(fragment)),
                Set.of(fragment),
                Set.of(),
                List.of());

        assertThat(PackRulesets.content()
                        .apply(contents(descriptor(Optional.empty()), List.of(), marked, List.of(), List.of())))
                .extracting(reason -> reason.detail())
                .anyMatch(detail -> detail.contains("slot marker"));
        assertThat(PackRulesets.content()
                        .apply(contents(descriptor(Optional.empty()), List.of(), refused, List.of(), List.of())))
                .noneMatch(reason -> reason.detail().contains("slot marker"));
    }

    @Test
    @DisplayName("the companions of an enhancement the read refused are no orphans")
    void theCompanionsOfARefusedEnhancementAreNoOrphans() {
        String directory = "missions/rx/enhancements/";
        StepEnhancementFacts facts = new StepEnhancementFacts(
                List.of(),
                List.of(),
                Set.of(directory + "brief.prompt-fragment.md"),
                Set.of(directory + "brief.step-enhancement.yaml"),
                List.of());
        StepEnhancementFacts held = new StepEnhancementFacts(
                List.of(), List.of(), Set.of(directory + "brief.prompt-fragment.md"), Set.of(), List.of());

        assertThat(PackRulesets.content()
                        .apply(contents(descriptor(Optional.empty()), List.of(), facts, List.of(), List.of())))
                .isEmpty();
        assertThat(PackRulesets.content()
                        .apply(contents(descriptor(Optional.empty()), List.of(), held, List.of(), List.of())))
                .extracting(Reason::code, reason -> reason.location().file())
                .containsExactly(tuple(PackCodes.PROMPT_FRAGMENT_INVALID, directory + "brief.prompt-fragment.md"));
    }

    @Test
    @DisplayName("the edges-read gate: one unreadable graph document switches off every rule that reads the edges")
    void theEdgesReadGate() {
        MissionTypeFacts unlinked = new MissionTypeFacts(TYPE, "rx", List.of("a"), List.of());
        StepEnhancementFacts overriding = new StepEnhancementFacts(
                List.of(new StepEnhancementFacts.Enhancement(
                        "missions/rx/enhancements/e.step-enhancement.yaml",
                        "rx",
                        "e",
                        List.of(new StepEnhancementFacts.Entry("tone:style", "e.prompt-fragment.md")))),
                List.of(StepEnhancementFacts.Companion.read(
                        "missions/rx/enhancements/e.prompt-fragment.md", false, false, "Be brief.\n")),
                Set.of("missions/rx/enhancements/e.prompt-fragment.md"),
                Set.of(),
                List.of(new StepEnhancementFacts.Edge(
                        RelationType.OVERRIDES, "step_enhancement:e", "action:rx/a", Optional.empty())));
        FileRead<RawGraph> read = new FileRead.Read<>(
                SHARD,
                new RawGraph(
                        SHARD,
                        RawGraph.Shape.SHARD,
                        List.of(),
                        List.of(new RawEdge(
                                "step_enhancement:e",
                                "action:rx/a",
                                "overrides",
                                Optional.empty(),
                                Optional.empty()))));
        FileRead<RawGraph> unreadable = new FileRead.Unreadable<>(
                "other.graph.yaml", new ParseFailure("other.graph.yaml", "not YAML"), List.of());

        List<Reason> judged = PackRulesets.content()
                .apply(contents(descriptor(Optional.empty()), List.of(read), overriding, List.of(), List.of(unlinked)));
        List<Reason> gated = PackRulesets.content()
                .apply(contents(
                        descriptor(Optional.empty()),
                        List.of(read, unreadable),
                        overriding,
                        List.of(),
                        List.of(unlinked)));

        assertThat(codes(judged)).contains(PackCodes.MISSION_STEP_UNLINKED, PackCodes.PROMPT_FRAGMENT_INVALID);
        assertThat(gated).isEmpty();
    }

    @Test
    @DisplayName("a binding-level fault is named before a rule fault: the read faults lead the verdict")
    void aBindingFaultComesBeforeARuleFault(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        write(pack, "tactics/b.tactic.yaml", "id: a\nschema_version: '1.0'\nname: T\nsteps:\n- title: s\n");
        write(pack, "tactics/c.tactic.yaml", "id: c\nschema_version: 1.0\nname: T\nsteps:\n- title: s\n");

        assertThat(codes(StorageRulesets.verdict(read(pack)).violations()))
                .containsExactly(StorageCodes.ARTIFACT_SCHEMA_ERROR, PackCodes.DUPLICATE_ARTIFACT_ID);
    }

    @Test
    @DisplayName("a file not held is a warning, so the manifest's membership is still judged beside it")
    void aFileNotHeldAndAManifestMismatch(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        write(pack, "notes.txt", "notes\n");
        write(
                pack,
                "pack-manifest.yaml",
                "schema_version: '1.0'\nmanifest_hash: " + "a".repeat(64) + "\nconstituents: []\n");

        Rationale verdict = StorageRulesets.verdict(read(pack));

        assertThat(verdict.failures())
                .extracting(Reason::code, reason -> reason.location().file())
                .contains(
                        tuple(StorageCodes.MANIFEST_MEMBERSHIP, "pack-manifest.yaml"),
                        tuple(StorageCodes.FILE_NOT_HELD, "notes.txt"));
        assertThat(verdict.failures().getLast().code()).isEqualTo(StorageCodes.FILE_NOT_HELD);
    }

    @Test
    @DisplayName("a slot that holds text answers prompt_slot_not_empty as a violation or as a warning, by the version")
    void theTwoPromptSlotNotEmptyCodes() {
        List<PromptSlotFacts> prompts = List.of(new PromptSlotFacts(
                "missions/rx/steps/a.prompt.md",
                PromptSlots.parse("# A\n\n<!-- spdd:x:start -->text<!-- spdd:x:end -->\n")));

        Rationale versioned = PackRulesets.content()
                .check(contents(
                        descriptor(Optional.of("1.0")), List.of(), StepEnhancementFacts.none(), prompts, List.of()));
        Rationale unversioned = PackRulesets.content()
                .check(contents(
                        descriptor(Optional.empty()), List.of(), StepEnhancementFacts.none(), prompts, List.of()));

        assertThat(codes(versioned.violations())).containsExactly(PackCodes.PROMPT_SLOT_NOT_EMPTY);
        assertThat(versioned.isFailing()).isTrue();
        assertThat(codes(unversioned.warnings())).containsExactly(PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING);
        assertThat(unversioned.isPassing()).isTrue();
    }

    static List<Planted> planted() {
        return PlantedFixtures.all();
    }

    @ParameterizedTest
    @MethodSource("planted")
    @DisplayName("content()'s findings are registration()'s, in the same order: the forward subset by construction")
    void contentIsASubsetOfRegistration(Planted planted) {
        List<Reason> content = PackRulesets.content().apply(planted.contents());
        List<Reason> registration =
                new ArrayList<>(StorageRulesets.registration().apply(planted.tree()));
        registration.retainAll(content);

        assertThat(registration).isEqualTo(content);
        assertThat(planted.readout().rationale().failures()).containsAll(content);
    }
}
