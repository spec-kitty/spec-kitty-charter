package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.PackInspector.Scope;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackPolicy.PromptSlotFault;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A step prompt's slots are judged at load, in validate mode and at registration (FR-007, IC-03): a malformed marker is
 * blocking whatever the descriptor's version, a slot that holds text is blocking under {@code schema_version "1.0"} and
 * a warning, listed apart from the violations, for an unversioned descriptor (A4). Each case sits beside its twin.
 */
@DisplayName("Step prompt slots are judged behind the descriptor-version gate (IC-03)")
class PromptSlotLoadChecksTest {

    private static final Optional<String> UNVERSIONED = Optional.empty();
    private static final Optional<String> VERSIONED = Optional.of("1.0");
    private static final String FILE = "missions/software-dev/steps/plan.prompt.md";

    private static final String UNPAIRED = "# Plan\n\n<!-- spdd:reasons-block:start -->\n";
    private static final String FILLED =
            "# Plan\n\n<!-- spdd:reasons-block:start -->legacy<!-- spdd:reasons-block:end -->\n";
    private static final String EMPTY = "# Plan\n\n<!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->\n";

    private static List<PromptSlotFault> faults(String text, Optional<String> version) {
        return PackPolicy.promptSlotFaults(PromptSlots.parse(text), version);
    }

    // ---- the gate, as the policy decides it (T023) ----------------------------------------------------------------

    @Test
    @DisplayName("a malformed marker is prompt_slot_malformed whatever the version, beside the empty slot that is none")
    void aMalformedMarkerIsBlockingWhateverTheVersion() {
        assertThat(faults(UNPAIRED, UNVERSIONED))
                .singleElement()
                .satisfies(fault -> assertThat(fault.code()).isEqualTo(PackFindingCode.PROMPT_SLOT_MALFORMED));
        assertThat(faults(UNPAIRED, VERSIONED))
                .singleElement()
                .satisfies(fault -> assertThat(fault.code()).isEqualTo(PackFindingCode.PROMPT_SLOT_MALFORMED));
        assertThat(faults(EMPTY, UNVERSIONED)).isEmpty();
        assertThat(faults(EMPTY, VERSIONED)).isEmpty();
    }

    @Test
    @DisplayName("a slot with text is blocking under schema_version 1.0 and a warning for an unversioned descriptor")
    void aSlotWithTextFollowsTheVersionGate() {
        assertThat(faults(FILLED, VERSIONED))
                .singleElement()
                .satisfies(fault -> assertThat(fault.code()).isEqualTo(PackFindingCode.PROMPT_SLOT_NOT_EMPTY));
        assertThat(faults(FILLED, UNVERSIONED))
                .singleElement()
                .satisfies(
                        fault -> assertThat(fault.code()).isEqualTo(PackFindingCode.PROMPT_SLOT_NOT_EMPTY_UNVERSIONED));
    }

    @Test
    @DisplayName("any other declared version gates like an unversioned one: only 1.0 is the versioned contract")
    void onlyVersionOneZeroIsVersioned() {
        assertThat(faults(FILLED, Optional.of("0.9")))
                .singleElement()
                .satisfies(
                        fault -> assertThat(fault.code()).isEqualTo(PackFindingCode.PROMPT_SLOT_NOT_EMPTY_UNVERSIONED));
    }

    @Test
    @DisplayName("a fault names its marker's line, and the warning says why it is only a warning")
    void aFaultNamesItsLine() {
        assertThat(faults(UNPAIRED, VERSIONED).getFirst().reason()).startsWith("line 3: ");
        assertThat(faults(FILLED, VERSIONED).getFirst().reason())
                .startsWith("line 3: ")
                .contains("spdd:reasons-block");
        assertThat(faults(FILLED, UNVERSIONED).getFirst().reason())
                .contains("spdd:reasons-block")
                .contains("the descriptor declares no schema_version, so this is a warning");
    }

    @Test
    @DisplayName("a blocking fault is a violation of its wire token at the file; the warning is a finding only")
    void aBlockingFaultIsAViolationAndAWarningIsNot() {
        PromptSlotFault malformed = faults(UNPAIRED, UNVERSIONED).getFirst();
        PromptSlotFault notEmpty = faults(FILLED, VERSIONED).getFirst();
        PromptSlotFault legacy = faults(FILLED, UNVERSIONED).getFirst();

        assertThat(PackPolicy.violationOf(FILE, malformed))
                .isEqualTo(new PackElementViolation(FILE, "prompt_slot_malformed", malformed.reason()));
        assertThat(PackPolicy.violationOf(FILE, notEmpty).kind()).isEqualTo("prompt_slot_not_empty");
        assertThatThrownBy(() -> PackPolicy.violationOf(FILE, legacy)).isInstanceOf(IllegalArgumentException.class);

        PackFinding warning = PackPolicy.findingOf(FILE, legacy);
        assertThat(warning.severity()).isEqualTo(PackFindingCode.Severity.WARNING);
        assertThat(warning.file()).isEqualTo(FILE);
        assertThat(warning.detail()).startsWith(FILE + ": ").contains(legacy.reason());
        assertThat(PackPolicy.findingOf(FILE, notEmpty).severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
    }

    @Test
    @DisplayName("the codes project onto the refusal codes and the diagnostic code the contract declares")
    void theCodesProjectOntoTheWire() {
        assertThat(PackFindingCode.PROMPT_SLOT_MALFORMED.refusal()).isEqualTo(RefusalCode.PROMPT_SLOT_MALFORMED);
        assertThat(PackFindingCode.PROMPT_SLOT_NOT_EMPTY.refusal()).isEqualTo(RefusalCode.PROMPT_SLOT_NOT_EMPTY);
        assertThat(PackFindingCode.PROMPT_SLOT_NOT_EMPTY_UNVERSIONED.diagnostic())
                .isEqualTo(Diagnostic.Code.PROMPT_SLOT_NOT_EMPTY);
    }

    // ---- the moments: validate, load, the composing read, the registration's staged load (T024) ---------------------

    private static final String TYPE = "software-dev";
    private static final String BUILD_FILE = "missions/software-dev/steps/build.prompt.md";
    private static final String NOT_EMPTY = "prompt_slot_not_empty";

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();
    private final WholePackStore store = new WholePackStore(strategy);

    @TempDir
    Path host;

    /** One pack on disk with a mission type: build it up from prompts, and read it at each moment. */
    private final class Pack {
        private final Path dir;
        private final List<String> steps = new ArrayList<>();
        private final List<String> onDemand = new ArrayList<>();

        Pack(boolean versioned) throws IOException {
            this.dir = Files.createDirectories(host.resolve("slots-" + (versioned ? "v" : "u")));
            file(
                    "pack.yaml",
                    "pack_id: 01M6ZRYK0000000000000000S1\npack_version: 1.0.0\nparent_pack: null\nname: slots\n"
                            + "pack_level: org\n" + (versioned ? "schema_version: \"1.0\"\n" : ""));
        }

        Pack file(String relative, String text) throws IOException {
            Path file = dir.resolve(relative);
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
            return this;
        }

        /** A step with a prompt of its own text, in {@code steps} or {@code on_demand_steps}. */
        Pack step(String id, String prompt) throws IOException {
            steps.add(id);
            return file("missions/" + TYPE + "/steps/" + id + ".prompt.md", prompt)
                    .missionType();
        }

        Pack onDemandStep(String id, String prompt) throws IOException {
            onDemand.add(id);
            return file("missions/" + TYPE + "/steps/" + id + ".prompt.md", prompt)
                    .missionType();
        }

        /** A step that has no prompt at all. */
        Pack stepWithoutPrompt(String id) throws IOException {
            steps.add(id + "!");
            return missionType();
        }

        private Pack missionType() throws IOException {
            StringBuilder yaml = new StringBuilder("schema_version: \"1.0\"\nid: " + TYPE
                    + "\ntitle: Software development\ndescription: Build\nsteps:\n");
            steps.forEach(id -> yaml.append(entry(id)));
            if (!onDemand.isEmpty()) {
                yaml.append("on_demand_steps:\n");
                onDemand.forEach(id -> yaml.append(entry(id)));
            }
            return file("missions/" + TYPE + "/" + TYPE + ".mission-type.yaml", yaml.toString());
        }

        private static String entry(String id) {
            boolean bare = id.endsWith("!");
            String name = bare ? id.substring(0, id.length() - 1) : id;
            return "  - id: " + name + "\n    title: " + name + "\n"
                    + (bare ? "" : "    prompt_path: steps/" + name + ".prompt.md\n");
        }

        /** Validate mode: every finding about a slot. */
        List<PackFinding> validate() {
            PackRoot root = new PackRoot("slots", Optional.of(PackLevel.ORG), dir.toString());
            return PackPolicy.judge(new FileSystemPackInspector().inspect(root, Scope.FULL)).findings().stream()
                    .filter(finding -> finding.code().name().startsWith("PROMPT_SLOT"))
                    .toList();
        }

        /** The whole-pack load: the violations on the aggregate. */
        List<PackElementViolation> violations() {
            return store.load(dir).pack().violations();
        }

        /** The whole-pack load: the warnings, apart from the violations. */
        List<PackFinding> warnings() {
            return store.load(dir).warnings();
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

        /** What the registration's staged load holds when it registers. */
        WholePackWriteStore.Contents stagedContents() {
            return store.loadStaged(new StagedPack(dir));
        }

        DomainRepositoryService service() {
            return new DomainRepositoryService(() -> List.of(new PackLocation("slots", PackLevel.ORG, dir)), strategy);
        }

        /** The composing read: the refusal it answers, or none. */
        Optional<CharterRefusal> read() {
            try {
                service().compositionFacts(new PackIdentifier("slots"));
                return Optional.empty();
            } catch (CharterRefusal refusal) {
                return Optional.of(refusal);
            }
        }
    }

    private static Pack plan(Pack pack, String prompt) throws IOException {
        return pack.step("plan", prompt);
    }

    @Test
    @DisplayName(
            "validate mode: a malformed marker is blocking in a versioned and an unversioned pack, the twin is clean")
    void validateModeRefusesAMalformedMarkerWhateverTheVersion() throws IOException {
        for (boolean versioned : List.of(true, false)) {
            Pack pack = plan(new Pack(versioned), UNPAIRED);

            assertThat(pack.validate()).singleElement().satisfies(finding -> {
                assertThat(finding.code()).isEqualTo(PackFindingCode.PROMPT_SLOT_MALFORMED);
                assertThat(finding.severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
                assertThat(finding.file()).isEqualTo(FILE);
            });
        }
        assertThat(plan(new Pack(true), EMPTY).validate()).isEmpty();
    }

    @Test
    @DisplayName("validate mode: text in a slot is blocking under 1.0 and a warning for the unversioned twin")
    void validateModeGatesATextSlotOnTheDescriptorVersion() throws IOException {
        assertThat(plan(new Pack(true), FILLED).validate()).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.PROMPT_SLOT_NOT_EMPTY);
            assertThat(finding.file()).isEqualTo(FILE);
        });
        assertThat(plan(new Pack(false), FILLED).validate()).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo(PackFindingCode.PROMPT_SLOT_NOT_EMPTY_UNVERSIONED);
            assertThat(finding.severity()).isEqualTo(PackFindingCode.Severity.WARNING);
            assertThat(finding.file()).isEqualTo(FILE);
        });
    }

    @Test
    @DisplayName("load: a malformed marker is a violation of kind prompt_slot_malformed at the prompt, in both packs")
    void loadRecordsAMalformedMarkerAsAViolation() throws IOException {
        for (boolean versioned : List.of(true, false)) {
            Pack pack = plan(new Pack(versioned), UNPAIRED);

            assertThat(pack.violations()).singleElement().satisfies(violation -> {
                assertThat(violation.file()).isEqualTo(FILE);
                assertThat(violation.kind()).isEqualTo("prompt_slot_malformed");
                assertThat(violation.reason()).startsWith("line 3: ");
            });
            assertThat(pack.warnings()).isEmpty();
        }
        Pack twin = plan(new Pack(true), EMPTY);
        assertThat(twin.violations()).isEmpty();
        assertThat(twin.warnings()).isEmpty();
    }

    @Test
    @DisplayName("load: text in a slot is a violation under 1.0; the unversioned twin loads and lists a warning apart")
    void loadGatesATextSlotOnTheDescriptorVersion() throws IOException {
        Pack versioned = plan(new Pack(true), FILLED);
        assertThat(versioned.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(FILE);
            assertThat(violation.kind()).isEqualTo(NOT_EMPTY);
        });
        assertThat(versioned.warnings()).isEmpty();

        Pack unversioned = plan(new Pack(false), FILLED);
        assertThat(unversioned.violations())
                .as("a warning is never a violation (A4)")
                .isEmpty();
        assertThat(unversioned.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.code()).isEqualTo(PackFindingCode.PROMPT_SLOT_NOT_EMPTY_UNVERSIONED);
            assertThat(warning.file()).isEqualTo(FILE);
            assertThat(warning.detail()).contains("spdd:reasons-block");
        });
    }

    @Test
    @DisplayName("a step of the on-demand list is judged like a step, and a step without a prompt has nothing to judge")
    void onDemandStepsAreJudgedAndAStepWithoutAPromptIsNot() throws IOException {
        Pack pack =
                new Pack(true).stepWithoutPrompt("intake").step("plan", EMPTY).onDemandStep("build", UNPAIRED);

        assertThat(pack.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(BUILD_FILE);
            assertThat(violation.kind()).isEqualTo("prompt_slot_malformed");
        });
    }

    @Test
    @DisplayName("the composing read: a refused prompt makes the closure unreadable and names the file; the twins read")
    void theComposingReadNamesThePromptFile() throws IOException {
        for (String prompt : List.of(UNPAIRED)) {
            assertThat(plan(new Pack(false), prompt).read()).hasValueSatisfying(refusal -> {
                assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                assertThat(refusal.getMessage()).contains(FILE);
            });
        }
        assertThat(plan(new Pack(true), FILLED).read()).hasValueSatisfying(refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refusal.getMessage()).contains(FILE);
        });
        assertThat(plan(new Pack(false), FILLED).read())
                .as("the unversioned warning refuses nothing (NFR-002)")
                .isEmpty();
        assertThat(plan(new Pack(true), EMPTY).read()).isEmpty();
    }

    @Test
    @DisplayName("registration: the staged load lists the code at the prompt file; the unversioned twin registers")
    void theStagedLoadListsTheViolationAndRegistersTheWarning() throws IOException {
        assertThat(plan(new Pack(false), UNPAIRED).staged()).singleElement().satisfies(violation -> {
            assertThat(violation.pointer()).isEqualTo(FILE);
            assertThat(violation.code()).isEqualTo("prompt_slot_malformed");
        });
        assertThat(plan(new Pack(true), FILLED).staged()).singleElement().satisfies(violation -> {
            assertThat(violation.pointer()).isEqualTo(FILE);
            assertThat(violation.code()).isEqualTo(NOT_EMPTY);
        });

        Pack twin = plan(new Pack(false), FILLED);
        assertThat(twin.staged()).isEmpty();
        assertThat(twin.stagedContents().warnings())
                .singleElement()
                .satisfies(warning -> assertThat(warning.file()).isEqualTo(FILE));
        assertThat(plan(new Pack(false), EMPTY).stagedContents().warnings()).isEmpty();
    }

    @Test
    @DisplayName("the slot faults come before the step enhancement rule faults in assembly order (E-3)")
    void slotFaultsPrecedeTheEnhancementRuleFaults() throws IOException {
        Pack pack = plan(new Pack(false), UNPAIRED)
                .file("missions/software-dev/enhancements/brief.step-enhancement.yaml", """
                        schema_version: '1.0'
                        id: brief
                        prompt_fragments:
                          - slot: spdd:reasons-block
                            path: brief.prompt-fragment.md
                        """)
                .file("missions/software-dev/enhancements/brief.prompt-fragment.md", "## Guidance\n\nFill it.\n")
                .file("enhancements.graph.yaml", """
                        schema_version: '1.0'
                        generated_at: STATIC
                        nodes: []
                        edges:
                        - source: step_enhancement:brief
                          target: base::action:software-dev/plan
                          relation: enhances
                        - source: step_enhancement:brief
                          target: mission_type:software-dev
                          relation: overrides
                        """);

        assertThat(pack.violations())
                .extracting(PackElementViolation::kind)
                .containsExactly("prompt_slot_malformed", "prompt_fragment_invalid");
        assertThat(pack.read())
                .hasValueSatisfying(refusal -> assertThat(refusal.getMessage()).contains(FILE));
    }

    @Test
    @DisplayName("a prompt the codec refuses is reported by the read before any grammar runs: no slot finding (A5)")
    void aCanonicalisationRefusalIsReportedBeforeTheGrammar() throws IOException {
        Pack pack = plan(new Pack(true), UNPAIRED.replace("\n", "\r\n"));

        assertThat(pack.validate()).isEmpty();
        assertThat(pack.violations())
                .singleElement()
                .satisfies(violation -> assertThat(violation.kind()).isEqualTo("companion"));
    }

    @Test
    @DisplayName("a prompt with a byte-order mark is reported by the read before any grammar runs, as CRLF is")
    void aByteOrderMarkIsReportedBeforeTheGrammar() throws IOException {
        Pack pack = plan(new Pack(true), "\uFEFF" + UNPAIRED);

        assertThat(pack.validate()).isEmpty();
        assertThat(pack.violations())
                .singleElement()
                .satisfies(violation -> assertThat(violation.kind()).isEqualTo("companion"));
    }

    // ---- A11: the load seat parses the document the store read, and it agrees with the raw text -------------------

    private static final List<String> FIXTURE_PROMPTS = List.of(
            UNPAIRED,
            FILLED,
            EMPTY,
            """
                    ---
                    description: A detailed description <!-- note:start -->
                    ---

                    # Plan

                    <!-- spdd:reasons-block:start -->

                    <!-- spdd:reasons-block:end -->
                    """,
            "# Plan\n\n## Section\n\n<!-- a:b:start --> <!-- a:b:end -->\n\n## Next\n\n<!-- c:d:start -->x<!-- c:d:end -->\n",
            "# Plan\n\n<!-- decision_id: plan-1 -->\n\n<!--\na:b:start\n--><!-- a:b:end -->\n");

    @Test
    @DisplayName("the parse of the raw text equals the parse of the document the store reads, for every fixture prompt")
    void rawTextAndStoredDocumentAgreeOnTheSlots() throws IOException {
        for (String raw : FIXTURE_PROMPTS) {
            assertThat(slotsOfTheStoredDocument(new Pack(false), raw))
                    .as(raw)
                    .isEqualTo(PromptSlots.parse(raw).slots());
        }
    }

    /** The slots of the prompt as {@code FilesystemPackStore.read} fills it into the mission type's DSO. */
    private List<PromptSlots.Slot> slotsOfTheStoredDocument(Pack pack, String raw) throws IOException {
        plan(pack, raw);
        FilesystemPackStore files = new FilesystemPackStore(host, strategy);
        MissionTypeDso dso = files.read(
                        new PackIdentifier(pack.dir.getFileName().toString()))
                .dsos(MissionTypeBinding.INSTANCE)
                .getFirst();
        return PromptSlots.parse(
                        MarkdownCodec.write(dso.steps().getFirst().prompt().orElseThrow()))
                .slots();
    }

    @Test
    @Tag("open-packs")
    @DisplayName(
            "the five pinned built-in prompts that hold slots parse alike from the raw text and the stored document")
    void theBuiltInPromptsAgreeOnTheLoadPath() throws IOException {
        OpenPacksCorpus.assertPinned();
        Path packs = OpenPacksCorpus.packsDirectory();
        List<MissionTypeDso> types = new FilesystemPackStore(packs, strategy)
                .read(new PackIdentifier("built-in"), List.of(MissionTypeBinding.INSTANCE))
                .dsos(MissionTypeBinding.INSTANCE);
        List<String> withSlots = new ArrayList<>();
        for (MissionTypeDso type : types) {
            List<MissionTypeDso.Step> steps = new ArrayList<>(type.steps());
            steps.addAll(type.onDemandSteps());
            for (MissionTypeDso.Step step : steps) {
                String file = "missions/" + type.id() + "/" + step.promptPath().orElseThrow();
                String raw = Files.readString(packs.resolve("built-in").resolve(file));
                List<PromptSlots.Slot> slots = PromptSlots.parse(raw).slots();
                assertThat(PromptSlots.parse(MarkdownCodec.write(step.prompt().orElseThrow()))
                                .slots())
                        .as(file)
                        .isEqualTo(slots);
                if (!slots.isEmpty()) {
                    withSlots.add(file);
                }
            }
        }
        assertThat(withSlots).as("the prompts that declare a slot at the pin").hasSize(5);
    }
}
