package ai.speckitty.charter.packs.infra.storage.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.NotHeldFile;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.ArtifactFacts;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import ai.speckitty.charter.packs.domain.rules.PackRulesets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StorageRulesets and the storage rules (#554, S3, S6, T038, T039)")
class StorageRulesetsTest {

    private static final TreeCensus.Entries LISTED =
            new TreeCensus.Entries(List.of("tactic:a at tactics/a.tactic.yaml"), List.of());

    private static final TreeCensus.Entries HELD = new TreeCensus.Entries(
            List.of("tactic:a at tactics/a.tactic.yaml", "tactic:b at tactics/b.tactic.yaml"), List.of());

    private static final TreeCensus.Manifest MISMATCHED =
            new TreeCensus.Manifest(true, true, Optional.of(LISTED), Optional.of(HELD));

    private static final String MISSING =
            "pack-manifest.yaml: leaves out constituent tactic:b at tactics/b.tactic.yaml," + " which the pack holds";

    private static PackContents contents(ArtifactFacts... artifacts) {
        return new PackContents(
                new PackRoot("qa", Optional.of(PackLevel.ORG), "loc"),
                new FileRead.Absent<>("pack.yaml"),
                List.of(),
                new FileRead.Absent<>("drg/fragment.yaml"),
                List.of(artifacts),
                List.of(),
                StepEnhancementFacts.none(),
                List.of(),
                List.of());
    }

    private static PackContents assembled(PackContents contents, PackContents.Assembly assembly) {
        return new PackContents(
                contents.root(),
                contents.descriptor(),
                contents.shards(),
                contents.fragment(),
                contents.artifacts(),
                contents.assets(),
                contents.stepEnhancements(),
                contents.promptSlots(),
                contents.missionTypes(),
                assembly);
    }

    private static TreeCensus census(
            List<NotHeldFile> notHeld, TreeCensus.Manifest manifest, TreeCensus.ReservedShard shard) {
        return new TreeCensus(List.of(), notHeld, manifest, shard);
    }

    private static TreeRead read(PackContents contents, TreeCensus census, Reason... readFaults) {
        return new TreeRead(contents, census, List.of(readFaults));
    }

    private static Reason fault(ReasonCode code, String file) {
        return new Reason(code.value(), Evaluation.FAIL, code, Location.in(file), file + ": " + code.value());
    }

    private static final ArtifactFacts A = new ArtifactFacts("tactics/a.tactic.yaml", "tactic", "same");

    private static final ArtifactFacts B = new ArtifactFacts("tactics/b.tactic.yaml", "tactic", "same");

    @Nested
    @DisplayName("the storage rules")
    class Rules {

        @Test
        @DisplayName("each file not held is one file_not_held warning at it, with its reason, in the census's order")
        void eachFileNotHeldIsOneWarning() {
            TreeRead read = read(
                    contents(),
                    census(
                            List.of(
                                    NotHeldFile.excluded("docs/a.md", "a reviewed reason"),
                                    NotHeldFile.unmodelled("notes.txt")),
                            TreeCensus.Manifest.ABSENT,
                            TreeCensus.ReservedShard.ABSENT));

            assertThat(NotHeldRules.RULES.apply(read))
                    .extracting(Reason::code, Reason::location, Reason::detail, Reason::isFailing)
                    .containsExactly(
                            tuple(
                                    StorageCodes.FILE_NOT_HELD,
                                    Location.in("docs/a.md"),
                                    "docs/a.md: not held (a reviewed reason)",
                                    true),
                            tuple(
                                    StorageCodes.FILE_NOT_HELD,
                                    Location.in("notes.txt"),
                                    "notes.txt: not held (no pack file kind models this file)",
                                    true));
            assertThat(NotHeldRules.RULES.apply(read))
                    .allSatisfy(reason -> assertThat(reason.severity()).isEqualTo(Severity.WARNING));
        }

        @Test
        @DisplayName("an unstamped reserved shard is one reserved_shard at it, from the census's fact alone")
        void anUnstampedReservedShardIsOneFault() {
            assertThat(ReservedShardRules.RULES.apply(read(
                            contents(),
                            census(List.of(), TreeCensus.Manifest.ABSENT, TreeCensus.ReservedShard.UNSTAMPED))))
                    .extracting(Reason::code, Reason::location, Reason::detail)
                    .containsExactly(tuple(
                            StorageCodes.RESERVED_SHARD,
                            Location.in("registered.graph.yaml"),
                            ReservedShardRules.RESERVED));
            for (TreeCensus.ReservedShard fine :
                    List.of(TreeCensus.ReservedShard.ABSENT, TreeCensus.ReservedShard.STAMPED)) {
                assertThat(ReservedShardRules.RULES.apply(
                                read(contents(), census(List.of(), TreeCensus.Manifest.ABSENT, fine))))
                        .isEmpty();
            }
        }

        @Test
        @DisplayName("something at the manifest's path that is not a regular file is one unreadable at the manifest")
        void aManifestThatIsNotARegularFileIsOneFault() {
            TreeCensus.Manifest directory = new TreeCensus.Manifest(true, false, Optional.empty(), Optional.empty());

            assertThat(ManifestRules.RULES.apply(
                            read(contents(), census(List.of(), directory, TreeCensus.ReservedShard.ABSENT))))
                    .extracting(Reason::code, Reason::location, Reason::detail)
                    .containsExactly(tuple(
                            StorageCodes.UNREADABLE,
                            Location.in("pack-manifest.yaml"),
                            "pack-manifest.yaml: the manifest is not a regular file"));
            assertThat(ManifestRules.RULES.apply(
                            read(contents(), census(List.of(), MISMATCHED, TreeCensus.ReservedShard.ABSENT))))
                    .isEmpty();
            assertThat(ManifestRules.RULES.apply(read(
                            contents(),
                            census(List.of(), TreeCensus.Manifest.ABSENT, TreeCensus.ReservedShard.ABSENT))))
                    .isEmpty();
        }

        @Test
        @DisplayName(
                "membership compares the listed and the derived lists; no manifest, or none read, is no difference")
        void membershipComparesTheTwoLists() {
            assertThat(StorageRulesets.manifestMembership(
                            read(contents(), census(List.of(), MISMATCHED, TreeCensus.ReservedShard.ABSENT))))
                    .extracting(Reason::code, Reason::location, Reason::detail)
                    .containsExactly(
                            tuple(StorageCodes.MANIFEST_MEMBERSHIP, Location.in("pack-manifest.yaml"), MISSING));
            for (TreeCensus.Manifest none : List.of(
                    TreeCensus.Manifest.ABSENT,
                    new TreeCensus.Manifest(true, true, Optional.empty(), Optional.empty()),
                    new TreeCensus.Manifest(true, true, Optional.of(LISTED), Optional.empty()),
                    new TreeCensus.Manifest(true, false, Optional.of(LISTED), Optional.of(HELD)),
                    new TreeCensus.Manifest(true, true, Optional.of(HELD), Optional.of(HELD)))) {
                assertThat(StorageRulesets.manifestMembership(
                                read(contents(), census(List.of(), none, TreeCensus.ReservedShard.ABSENT))))
                        .as("%s", none)
                        .isEmpty();
            }
        }

        @Test
        @DisplayName("a repeated or unheld listing is a reason of its own, after the unheld ones")
        void aRepeatedListingIsAReasonOfItsOwn() {
            TreeCensus.Entries twice = new TreeCensus.Entries(
                    List.of("tactic:a at tactics/a.tactic.yaml", "tactic:a at tactics/a.tactic.yaml", "tactic:x at x"),
                    List.of(
                            "p at presets/p.yaml",
                            "p at presets/p.yaml",
                            "p at presets/p.yaml",
                            "q at presets/q.yaml"));
            TreeCensus.Entries held = new TreeCensus.Entries(
                    List.of("tactic:a at tactics/a.tactic.yaml"),
                    List.of("p at presets/p.yaml", "r at presets/r.yaml"));

            assertThat(ManifestRules.mismatch(twice, held))
                    .containsExactly(
                            "lists constituent tactic:x at x, which the pack does not hold",
                            "lists preset q at presets/q.yaml, which the pack does not hold",
                            "lists constituent tactic:a at tactics/a.tactic.yaml twice",
                            "lists preset p at presets/p.yaml 3 times",
                            "leaves out preset r at presets/r.yaml, which the pack holds");
            assertThat(ManifestRules.mismatch(held, held)).isEmpty();
        }
    }

    @Nested
    @DisplayName("the compositions")
    class Compositions {

        @Test
        @DisplayName(
                "the store's refusal of a pack with no pack.yaml is one pack_unreadable at it, unless the read states it")
        void theStoresRefusalOfThePackIsOneFault() {
            PackContents.Assembly.Refused noDescriptor = new PackContents.Assembly.Refused(
                    List.of(),
                    ai.speckitty.charter.kernel.domain.RefusalCode.PACK_UNREADABLE,
                    "pack qa has no pack.yaml",
                    true);
            PackContents.Assembly.Refused breach = new PackContents.Assembly.Refused(
                    List.of(), ai.speckitty.charter.kernel.domain.RefusalCode.VALIDATION_FAILED, "two tactics", false);
            TreeCensus none = census(List.of(), TreeCensus.Manifest.ABSENT, TreeCensus.ReservedShard.ABSENT);
            Reason stated = new Reason(
                    "pack_unreadable",
                    Evaluation.FAIL,
                    StorageCodes.PACK_UNREADABLE,
                    Location.in("pack"),
                    "pack qa is not a directory");

            assertThat(AssemblyRules.RULES.apply(read(assembled(contents(), noDescriptor), none)))
                    .extracting(Reason::code, Reason::location, Reason::detail)
                    .containsExactly(
                            tuple(StorageCodes.PACK_UNREADABLE, Location.in("pack.yaml"), "pack qa has no pack.yaml"));
            assertThat(AssemblyRules.RULES.apply(read(assembled(contents(), breach), none)))
                    .extracting(Reason::code, Reason::location)
                    .containsExactly(tuple(StorageCodes.ASSEMBLY_REFUSED, Location.in("pack")));
            assertThat(AssemblyRules.RULES.apply(read(
                            assembled(
                                    contents(),
                                    new PackContents.Assembly.Refused(
                                            List.of(),
                                            ai.speckitty.charter.kernel.domain.RefusalCode.PACK_UNREADABLE,
                                            "pack qa is not a directory",
                                            false)),
                            none,
                            stated)))
                    .as("the read states the root it could not read")
                    .isEmpty();
            PackContents unreadableDescriptor = new PackContents(
                    contents().root(),
                    new FileRead.Unreadable<>(
                            "pack.yaml", new PackContents.ParseFailure("pack.yaml", "not YAML"), List.of()),
                    List.of(),
                    new FileRead.Absent<>("drg/fragment.yaml"),
                    List.of(),
                    List.of(),
                    StepEnhancementFacts.none(),
                    List.of(),
                    List.of(),
                    noDescriptor);
            assertThat(AssemblyRules.RULES.apply(read(unreadableDescriptor, none)))
                    .as("the reader states a descriptor it could not read")
                    .isEmpty();
            assertThat(AssemblyRules.RULES.apply(read(contents(), none))).isEmpty();
        }

        @Test
        @DisplayName("holding() is the not-held, reserved-shard and manifest rules, in that order")
        void holdingIsTheStorageRules() {
            TreeRead read = read(
                    contents(),
                    census(
                            List.of(NotHeldFile.unmodelled("notes.txt")),
                            new TreeCensus.Manifest(true, false, Optional.empty(), Optional.empty()),
                            TreeCensus.ReservedShard.UNSTAMPED));

            assertThat(StorageRulesets.holding().apply(read))
                    .extracting(Reason::code)
                    .containsExactly(StorageCodes.FILE_NOT_HELD, StorageCodes.RESERVED_SHARD, StorageCodes.UNREADABLE);
        }

        @Test
        @DisplayName("registration() is holding() and then content() over the read's contents")
        void registrationIsHoldingThenContent() {
            TreeRead read = read(
                    contents(A, B),
                    census(
                            List.of(NotHeldFile.unmodelled("notes.txt")),
                            TreeCensus.Manifest.ABSENT,
                            TreeCensus.ReservedShard.ABSENT));
            List<Reason> expected = new ArrayList<>(StorageRulesets.holding().apply(read));
            expected.addAll(PackRulesets.content().apply(read.contents()));

            assertThat(StorageRulesets.registration().apply(read))
                    .isEqualTo(expected)
                    .hasSize(2);
        }
    }

    @Nested
    @DisplayName("the manifest gate")
    class Gate {

        private Rationale verdict(PackContents contents, TreeCensus.ReservedShard shard, Reason... readFaults) {
            return StorageRulesets.verdict(read(
                    contents, census(List.of(NotHeldFile.unmodelled("notes.txt")), MISMATCHED, shard), readFaults));
        }

        private static List<ReasonCode> codes(Rationale verdict) {
            return verdict.failures().stream().map(Reason::code).toList();
        }

        @Test
        @DisplayName("with no other violation, the membership is judged; a warning does not gate it")
        void aCleanReadIsGated() {
            assertThat(codes(verdict(contents(), TreeCensus.ReservedShard.ABSENT)))
                    .containsExactly(StorageCodes.MANIFEST_MEMBERSHIP, StorageCodes.FILE_NOT_HELD);
        }

        @Test
        @DisplayName("a read fault switches the membership off")
        void aReadFaultSwitchesItOff() {
            assertThat(codes(verdict(
                            contents(),
                            TreeCensus.ReservedShard.ABSENT,
                            fault(StorageCodes.UNREADABLE, "a.tactic.yaml"))))
                    .containsExactly(StorageCodes.UNREADABLE, StorageCodes.FILE_NOT_HELD);
        }

        @Test
        @DisplayName("a content rule's violation switches the membership off")
        void aContentViolationSwitchesItOff() {
            assertThat(codes(verdict(contents(A, B), TreeCensus.ReservedShard.ABSENT)))
                    .containsExactly(PackCodes.DUPLICATE_ARTIFACT_ID, StorageCodes.FILE_NOT_HELD);
        }

        @Test
        @DisplayName("a storage rule's violation switches the membership off")
        void aStorageViolationSwitchesItOff() {
            assertThat(codes(verdict(contents(), TreeCensus.ReservedShard.UNSTAMPED)))
                    .containsExactly(StorageCodes.RESERVED_SHARD, StorageCodes.FILE_NOT_HELD);
        }
    }

    @Nested
    @DisplayName("registration's wire order")
    class Order {

        @Test
        @DisplayName(
                "the graph faults by path, the pack's refusal, the rest as given, the storage faults; not-held last")
        void theVerdictIsInRegistrationOrder() {
            Reason graphB = fault(PackCodes.DRG_SCHEMA_ERROR, "b.graph.yaml");
            Reason graphA = fault(PackCodes.DRG_SCHEMA_ERROR, "a.graph.yaml");
            Reason graphA2 = new Reason(
                    PackCodes.DRG_SCHEMA_ERROR.value(),
                    Evaluation.FAIL,
                    PackCodes.DRG_SCHEMA_ERROR,
                    Location.in("a.graph.yaml").at("/edges/0"),
                    "a.graph.yaml: second");
            Reason read = fault(StorageCodes.UNREADABLE, "z.tactic.yaml");
            Reason rule = fault(PackCodes.DUPLICATE_ARTIFACT_ID, "a.tactic.yaml");
            Reason shard = ReservedShardRules.UNSTAMPED
                    .apply(read(
                            contents(),
                            census(List.of(), TreeCensus.Manifest.ABSENT, TreeCensus.ReservedShard.UNSTAMPED)))
                    .getFirst();
            Reason notRegular = ManifestRules.REGULAR
                    .apply(read(
                            contents(),
                            census(
                                    List.of(),
                                    new TreeCensus.Manifest(true, false, Optional.empty(), Optional.empty()),
                                    TreeCensus.ReservedShard.ABSENT)))
                    .getFirst();
            Reason membership = fault(StorageCodes.MANIFEST_MEMBERSHIP, "pack-manifest.yaml");
            Reason manifestSchema = fault(StorageCodes.ARTIFACT_SCHEMA_ERROR, "pack-manifest.yaml");
            Reason slot = fault(PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING, "p.prompt.md");
            Reason notHeldZ = NotHeldRules.warning(NotHeldFile.unmodelled("z.txt"));
            Reason notHeldA = NotHeldRules.warning(NotHeldFile.unmodelled("a.txt"));
            Reason passed = Reason.passed("checked");
            Reason whole = fault(StorageCodes.PACK_UNREADABLE, "pack.yaml");

            Rationale ordered = StorageRulesets.inRegistrationOrder(new Rationale(List.of(
                    passed,
                    notHeldZ,
                    membership,
                    notRegular,
                    shard,
                    graphB,
                    read,
                    slot,
                    manifestSchema,
                    graphA,
                    notHeldA,
                    rule,
                    whole,
                    graphA2)));

            assertThat(ordered.details())
                    .containsExactly(
                            graphA,
                            graphA2,
                            graphB,
                            whole,
                            read,
                            manifestSchema,
                            rule,
                            shard,
                            notRegular,
                            membership,
                            slot,
                            notHeldA,
                            notHeldZ,
                            passed);
        }

        @Test
        @DisplayName("the verdict joins the read faults before the rules, so a binding-level fault is named first")
        void theReadFaultsComeBeforeTheRules() {
            Reason binding = fault(StorageCodes.ARTIFACT_SCHEMA_ERROR, "tactics/b.tactic.yaml");

            assertThat(StorageRulesets.verdict(read(
                                    contents(A, B),
                                    census(List.of(), TreeCensus.Manifest.ABSENT, TreeCensus.ReservedShard.ABSENT),
                                    binding))
                            .violations())
                    .extracting(Reason::code)
                    .containsExactly(StorageCodes.ARTIFACT_SCHEMA_ERROR, PackCodes.DUPLICATE_ARTIFACT_ID);
        }
    }
}
