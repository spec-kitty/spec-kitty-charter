package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationService.RegistrationOutcome;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import ai.speckitty.charter.packs.domain.WritePolicyGate.Operation;
import ai.speckitty.charter.packs.infra.storage.CommitStorePackWriteStore;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.packs.infra.storage.PackCommitStore;
import ai.speckitty.charter.packs.infra.storage.RelationshipShard;
import ai.speckitty.charter.packs.infra.storage.SlowSwapCommitStores;
import ai.speckitty.charter.packs.infra.storage.StorageRootLocations;
import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The whole-pack registration flow of IC-04, one test per step's refusal and the happy paths (FR-001, FR-002, FR-003,
 * FR-007, SC-003). The flow runs over the real filesystem adapters: a pack is an extracted directory, the stored packs
 * live under a configuration root, and what a refusal leaves behind is read back from disk. Every refusal is paired
 * with a positive control on the same fixture.
 */
class PackRegistrationServiceTest {

    static final String ORG_PACK = ulid('A');
    static final String OTHER_PACK = ulid('B');

    /** A 26 character Crockford base32 identity ending in {@code c} repeated. */
    static String ulid(char c) {
        return "01M6R0WP14" + String.valueOf(c).repeat(16);
    }

    // ---- creating a pack -----------------------------------------------------------------------------------------

    @Test
    void aNewPackIsCreatedHoldingExactlyTheUploadedTree(@TempDir Path host) {
        Rig rig = new Rig(host);
        StagedPack upload =
                rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org").directive("DIRECTIVE_001", "One"));

        RegistrationOutcome outcome = rig.register("pack-x", upload, Preconditions.none());

        assertThat(outcome.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(rig.filesOf(rig.packs.resolve("pack-x"))).isEqualTo(rig.filesOf(upload.directory()));
        assertThat(outcome.revision()).isEqualTo(rig.writeStore.currentRevision(new PackIdentifier("pack-x")));
        assertThat(outcome.revision()).isEqualTo(rig.commits.revisionOf(upload));
        assertThat(outcome.descriptor().packId()).isEqualTo(ORG_PACK);
        assertThat(outcome.pack().value()).isEqualTo("pack-x");
    }

    // ---- replacing a pack: the stored pack becomes the upload, removals included (FR-001, SC-003) ----------------

    @Test
    void aReplaceLeavesExactlyTheUploadedPackAndDropsWhatTheUploadLeavesOut(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register(
                "pack-x",
                rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org")
                        .directive("DIRECTIVE_001", "One")
                        .directive("DIRECTIVE_002", "Two")
                        .tactic("a-tactic")),
                Preconditions.none());
        StagedPack second =
                rig.stage(Packs.of(ORG_PACK, "pack-x", "2.0.0", "org").directive("DIRECTIVE_001", "One, changed"));

        RegistrationOutcome outcome = rig.register("pack-x", second, Preconditions.none());

        assertThat(outcome.outcome()).isEqualTo(Outcome.REPLACED);
        Path stored = rig.packs.resolve("pack-x");
        assertThat(rig.filesOf(stored)).isEqualTo(rig.filesOf(second.directory()));
        assertThat(rig.filesOf(stored).keySet()).doesNotContain("directives/DIRECTIVE_002.directive.yaml");
        assertThat(outcome.revision()).isEqualTo(rig.writeStore.currentRevision(new PackIdentifier("pack-x")));
        assertThat(rig.asidesBeside(stored))
                .as("no aside copy of the old pack is left")
                .isEmpty();
    }

    @Test
    void anOlderPackVersionReplacesToo(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("pack-x", rig.stage(Packs.of(ORG_PACK, "pack-x", "2.0.0", "org")), Preconditions.none());

        RegistrationOutcome outcome = rig.register(
                "pack-x",
                rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org").directive("DIRECTIVE_001", "Old")),
                Preconditions.none());

        assertThat(outcome.outcome()).as("the version is a label, not an order").isEqualTo(Outcome.REPLACED);
        assertThat(rig.filesOf(rig.packs.resolve("pack-x")).get("pack.yaml")).contains("pack_version: 1.0.0");
    }

    // ---- the version rule (steps 4 to 6) -------------------------------------------------------------------------

    @Test
    void aReSendOfTheSameContentIsUnchangedAndWritesNothing(@TempDir Path host) {
        Rig rig = new Rig(host);
        Packs pack = Packs.of(ORG_PACK, "pack-x", "1.0.0", "org").directive("DIRECTIVE_001", "One");
        RegistrationOutcome first = rig.register("pack-x", rig.stage(pack), Preconditions.none());
        Map<String, String> before = rig.filesOf(rig.packs.resolve("pack-x"));
        long generationBefore = rig.generation.ofPack("pack-x");

        RegistrationOutcome again = rig.register("pack-x", rig.stage(pack), Preconditions.none());

        assertThat(again.outcome()).isEqualTo(Outcome.UNCHANGED);
        assertThat(again.revision()).isEqualTo(first.revision());
        assertThat(rig.filesOf(rig.packs.resolve("pack-x"))).isEqualTo(before);
        assertThat(rig.generation.ofPack("pack-x")).as("no swap ran").isEqualTo(generationBefore);
    }

    @Test
    void anIdenticalReSendIsUnchangedEvenUnderAStaleIfMatch(@TempDir Path host) {
        Rig rig = new Rig(host);
        Packs pack = Packs.of(ORG_PACK, "pack-x", "1.0.0", "org").directive("DIRECTIVE_001", "One");
        rig.register("pack-x", rig.stage(pack), Preconditions.none());

        RegistrationOutcome again =
                rig.register("pack-x", rig.stage(pack), Preconditions.ifMatch("\"sha256:" + "0".repeat(64) + "\""));

        assertThat(again.outcome()).isEqualTo(Outcome.UNCHANGED);
    }

    @Test
    void theSamePackVersionWithAnotherRevisionIsAVersionConflict(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register(
                "pack-x",
                rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org").directive("DIRECTIVE_001", "One")),
                Preconditions.none());
        Map<String, String> before = rig.filesOf(rig.packs.resolve("pack-x"));
        StagedPack changed =
                rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org").directive("DIRECTIVE_001", "Another"));

        assertThat(refusalOf(() -> rig.register("pack-x", changed, Preconditions.none())))
                .isEqualTo(RefusalCode.PACK_VERSION_CONFLICT);
        assertThat(rig.filesOf(rig.packs.resolve("pack-x")))
                .as("nothing changed")
                .isEqualTo(before);

        StagedPack control =
                rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.1", "org").directive("DIRECTIVE_001", "Another"));
        assertThat(rig.register("pack-x", control, Preconditions.none()).outcome())
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void aStaleIfMatchRefusesAReplaceAndTheCurrentOneAllowsIt(@TempDir Path host) {
        Rig rig = new Rig(host);
        RegistrationOutcome first =
                rig.register("pack-x", rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org")), Preconditions.none());
        Map<String, String> before = rig.filesOf(rig.packs.resolve("pack-x"));
        StagedPack next = rig.stage(Packs.of(ORG_PACK, "pack-x", "2.0.0", "org"));

        assertThat(preconditionOf(
                        () -> rig.register("pack-x", next, Preconditions.ifMatch("\"sha256:" + "0".repeat(64) + "\""))))
                .isEqualTo(PreconditionException.Kind.STALE);
        assertThat(rig.filesOf(rig.packs.resolve("pack-x")))
                .as("nothing changed")
                .isEqualTo(before);

        String current = "\"" + first.revision().token() + "\"";
        assertThat(rig.register("pack-x", next, Preconditions.ifMatch(current)).outcome())
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void ifNoneMatchAnyRefusesWhenThePackExistsAndCreatesWhenItDoesNot(@TempDir Path host) {
        Rig rig = new Rig(host);
        StagedPack upload = rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org"));

        assertThat(rig.register("pack-x", upload, Preconditions.createOnly()).outcome())
                .isEqualTo(Outcome.CREATED);

        StagedPack next = rig.stage(Packs.of(ORG_PACK, "pack-x", "2.0.0", "org"));
        assertThat(preconditionOf(() -> rig.register("pack-x", next, Preconditions.createOnly())))
                .isEqualTo(PreconditionException.Kind.STALE);
    }

    @Test
    void anIfMatchAgainstAPackThatDoesNotExistIsStale(@TempDir Path host) {
        Rig rig = new Rig(host);
        StagedPack upload = rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org"));

        assertThat(preconditionOf(() ->
                        rig.register("pack-x", upload, Preconditions.ifMatch("\"sha256:" + "0".repeat(64) + "\""))))
                .isEqualTo(PreconditionException.Kind.STALE);
        assertThat(rig.packs.resolve("pack-x")).doesNotExist();
    }

    // ---- the identity checks (step 3) ----------------------------------------------------------------------------

    @Test
    void aBuiltInPackIsRefusedAsAConflictAndAnOrgPackIsAccepted(@TempDir Path host) {
        Rig rig = new Rig(host);
        StagedPack builtIn = rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "built-in"));

        assertThat(refusalOf(() -> rig.register("pack-x", builtIn, Preconditions.none())))
                .isEqualTo(RefusalCode.REGISTRATION_CONFLICT);
        assertThat(rig.packs.resolve("pack-x")).doesNotExist();

        StagedPack control = rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "project"));
        assertThat(rig.register("pack-x", control, Preconditions.none()).outcome())
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void aReplaceMustKeepTheStoredPacksLevel(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("pack-x", rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org")), Preconditions.none());
        StagedPack otherLevel = rig.stage(Packs.of(ORG_PACK, "pack-x", "2.0.0", "project"));

        assertThat(refusalOf(() -> rig.register("pack-x", otherLevel, Preconditions.none())))
                .isEqualTo(RefusalCode.VALIDATION_FAILED);

        StagedPack control = rig.stage(Packs.of(ORG_PACK, "pack-x", "2.0.0", "org"));
        assertThat(rig.register("pack-x", control, Preconditions.none()).outcome())
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void aReplaceMustKeepTheStoredPacksPackId(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("pack-x", rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org")), Preconditions.none());
        StagedPack otherId = rig.stage(Packs.of(OTHER_PACK, "pack-x", "2.0.0", "org"));

        assertThat(refusalOf(() -> rig.register("pack-x", otherId, Preconditions.none())))
                .isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(rig.filesOf(rig.packs.resolve("pack-x")).get("pack.yaml")).contains(ORG_PACK);

        StagedPack control = rig.stage(Packs.of(ORG_PACK, "pack-x", "2.0.0", "org"));
        assertThat(rig.register("pack-x", control, Preconditions.none()).outcome())
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void aNewPackMayNotTakeThePackIdOfAnotherStoredPack(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("pack-x", rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org")), Preconditions.none());
        StagedPack sameId = rig.stage(Packs.of(ORG_PACK, "pack-y", "1.0.0", "org"));

        assertThat(refusalOf(() -> rig.register("pack-y", sameId, Preconditions.none())))
                .isEqualTo(RefusalCode.REGISTRATION_CONFLICT);
        assertThat(rig.packs.resolve("pack-y")).doesNotExist();

        StagedPack control = rig.stage(Packs.of(OTHER_PACK, "pack-y", "1.0.0", "org"));
        assertThat(rig.register("pack-y", control, Preconditions.none()).outcome())
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void aNewPackMayNotTakeTheNameOfAnotherStoredPack(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("pack-x", rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org")), Preconditions.none());
        StagedPack sameName = rig.stage(Packs.of(OTHER_PACK, "pack-x", "1.0.0", "org"));

        assertThat(refusalOf(() -> rig.register("pack-y", sameName, Preconditions.none())))
                .isEqualTo(RefusalCode.REGISTRATION_CONFLICT);
        assertThat(rig.packs.resolve("pack-y")).doesNotExist();

        StagedPack control = rig.stage(Packs.of(OTHER_PACK, "pack-z", "1.0.0", "org"));
        assertThat(rig.register("pack-y", control, Preconditions.none()).outcome())
                .isEqualTo(Outcome.CREATED);
    }

    // ---- the load (step 2): every fault of the upload is a 422, never a 500 --------------------------------------

    /** One way an upload is wrong, and the same file with the fault fixed. */
    record Fault(String label, String file, String fault, String control) {
        @Override
        public String toString() {
            return label;
        }
    }

    static Stream<Fault> loadFaults() {
        String directive = Packs.directiveText("DIRECTIVE_001", "One");
        String stamped = RelationshipShard.write(List.of());
        String plain = "schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges: []\n";
        String preset =
                "schema_version: \"1.0\"\nid: starter\ndescription: A starter.\nactivated_directives:\n  - id: DIRECTIVE_001\n";
        return Stream.of(
                new Fault(
                        "a registered graph without the registration stamp",
                        "registered.graph.yaml",
                        stamped.replaceAll("(?m)^generated_by:.*\\R", ""),
                        stamped),
                new Fault(
                        "a directive field that was removed: references",
                        "directives/DIRECTIVE_001.directive.yaml",
                        directive + "references:\n  - directive:DIRECTIVE_002\n",
                        directive),
                new Fault(
                        "a directive field that was removed: procedures",
                        "directives/DIRECTIVE_001.directive.yaml",
                        directive + "procedures:\n  - a-procedure\n",
                        directive),
                new Fault(
                        "a directive field that was removed: relationships",
                        "directives/DIRECTIVE_001.directive.yaml",
                        directive + "relationships:\n  - target: directive:DIRECTIVE_002\n",
                        directive),
                new Fault(
                        "a preset with activated_kinds",
                        "presets/starter.yaml",
                        preset + "activated_kinds:\n  - directive\n",
                        preset),
                new Fault(
                        "a preset item id outside the id grammar",
                        "presets/starter.yaml",
                        preset.replace("DIRECTIVE_001", "bad id"),
                        preset),
                new Fault("a graph document that does not parse", "extra.graph.yaml", "edges: [unclosed\n", plain),
                new Fault("a file nothing models", "notes/readme.txt", "stray", null),
                new Fault(
                        "a pack with no pack.yaml",
                        "pack.yaml",
                        null,
                        Packs.descriptorText(ORG_PACK, "pack-x", "1.0.0", "org", "")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("loadFaults")
    void aFaultOfTheUploadIsAValidationFailureAndItsControlRegisters(Fault fault, @TempDir Path host) {
        Rig rig = new Rig(host);
        Packs base = Packs.of(ORG_PACK, "pack-x", "1.0.0", "org").directive("DIRECTIVE_001", "One");
        StagedPack faulty = rig.stage(base.with(fault.file(), fault.fault()));

        CharterRefusal refusal = refusal(() -> rig.register("pack-x", faulty, Preconditions.none()));

        assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(refusal.violations()).as("the refusal lists what is wrong").isNotEmpty();
        assertThat(rig.packs.resolve("pack-x")).as("nothing was created").doesNotExist();

        StagedPack fixed = rig.stage(base.with(fault.file(), fault.control()));
        assertThat(rig.register("pack-x", fixed, Preconditions.none()).outcome())
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void aStampedRegisteredGraphIsAcceptedAndTheReplaceDropsEveryEdgeTheUploadLeavesOut(@TempDir Path host) {
        Rig rig = new Rig(host);
        Packs withEdge = Packs.of(ORG_PACK, "pack-x", "1.0.0", "org")
                .directive("DIRECTIVE_001", "One")
                .directive("DIRECTIVE_002", "Two")
                .registeredEdge("DIRECTIVE_001", "DIRECTIVE_002");
        rig.register("pack-x", rig.stage(withEdge), Preconditions.none());
        assertThat(rig.filesOf(rig.packs.resolve("pack-x"))).containsKey("registered.graph.yaml");

        rig.register(
                "pack-x",
                rig.stage(Packs.of(ORG_PACK, "pack-x", "2.0.0", "org")
                        .directive("DIRECTIVE_001", "One")
                        .directive("DIRECTIVE_002", "Two")),
                Preconditions.none());

        assertThat(rig.filesOf(rig.packs.resolve("pack-x"))).doesNotContainKey("registered.graph.yaml");
    }

    // ---- the level table of the write gate: REMOVE joins the operations (ADR-0012 decision 1, ADR-0030 D3) -------

    @Test
    void removeIsPermittedOnOpenAndExtendableAndRefusedOnLocked() {
        assertThat(MutabilityLevel.OPEN.permits(Operation.REMOVE)).isTrue();
        assertThat(MutabilityLevel.EXTENDABLE.permits(Operation.REMOVE)).isTrue();
        assertThat(MutabilityLevel.LOCKED.permits(Operation.REMOVE)).isFalse();
    }

    // ---- step 7: the write gate over the staged closure (ADR-0065 D5, ADR-0063 D3) ---------------------------------

    private static final String BASE_ID = ulid('C');
    private static final String DERIVED_ID = ulid('D');
    private static final String DEPENDS_ON_BASE = "depends_on:\n  - pack_id: " + BASE_ID + "\n    range: '>=1.0'\n";
    private static final String POLICY =
            "schema_version: 1\nartifacts:\n  directive:LOCKED_ONE: locked\n  directive:EXT_ONE: extendable\n";

    /** The pack the others build on: it locks one of its directives and makes another extendable. */
    private static Packs base(String version, String lockedTitle) {
        return Packs.of(BASE_ID, "base", version, "org", "mutability_policy: policy.yaml\n")
                .file("policy.yaml", POLICY)
                .directive("LOCKED_ONE", lockedTitle)
                .directive("EXT_ONE", "Extendable")
                .directive("OPEN_ONE", "Open");
    }

    private static Packs derived(String version) {
        return Packs.of(DERIVED_ID, "derived", version, "project", DEPENDS_ON_BASE)
                .directive("DERIVED_ONE", "Own");
    }

    private static String shard(String... sources) {
        StringBuilder text = new StringBuilder("schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges:\n");
        for (String source : sources) {
            text.append("- source: directive:")
                    .append(source)
                    .append("\n  target: directive:DERIVED_ONE\n  relation: requires\n");
        }
        return text.toString();
    }

    private Rig rigWithBase(Path host) {
        Rig rig = new Rig(host);
        assertThat(rig.register("base", rig.stage(base("1.0.0", "Locked")), Preconditions.none())
                        .outcome())
                .isEqualTo(Outcome.CREATED);
        return rig;
    }

    @Test
    void aPacksOwnPolicyNeverBindsIt(@TempDir Path host) {
        Rig rig = rigWithBase(host);

        RegistrationOutcome outcome =
                rig.register("base", rig.stage(base("2.0.0", "Locked, changed")), Preconditions.none());

        assertThat(outcome.outcome())
                .as("base locks LOCKED_ONE for the packs built on it, not for itself (B8)")
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void aNewPackThatRegistersALockedArtifactOfItsAncestorIsRefusedOverTheStagedClosure(@TempDir Path host) {
        Rig rig = rigWithBase(host);
        StagedPack locked = rig.stage(derived("1.0.0").directive("LOCKED_ONE", "Mine"));

        assertThat(refusalOf(() -> rig.register("derived", locked, Preconditions.none())))
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
        assertThat(rig.packs.resolve("derived")).as("nothing was created").doesNotExist();

        assertThat(rig.register("derived", rig.stage(derived("1.0.0")), Preconditions.none())
                        .outcome())
                .as("the control: the same pack without the locked id")
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    void anExtendableArtifactOfTheAncestorMayBeRegisteredButNotReplaced(@TempDir Path host) {
        Rig rig = rigWithBase(host);
        assertThat(rig.register(
                                "derived",
                                rig.stage(derived("1.0.0").directive("EXT_ONE", "First")),
                                Preconditions.none())
                        .outcome())
                .as("REGISTER is within extendable")
                .isEqualTo(Outcome.CREATED);
        Map<String, String> before = rig.filesOf(rig.packs.resolve("derived"));
        StagedPack replaced = rig.stage(derived("2.0.0").directive("EXT_ONE", "Second"));

        assertThat(refusalOf(() -> rig.register("derived", replaced, Preconditions.none())))
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
        assertThat(rig.filesOf(rig.packs.resolve("derived"))).isEqualTo(before);

        StagedPack kept = rig.stage(derived("2.0.0").directive("EXT_ONE", "First"));
        assertThat(rig.register("derived", kept, Preconditions.none()).outcome())
                .as("the control: EXT_ONE kept as it was")
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void aRemovalIsAllowedOnAnExtendableArtifactAndRefusedOnALockedOne(@TempDir Path host) {
        Rig rig = rigWithBase(host);
        rig.install("derived", derived("1.0.0").directive("LOCKED_ONE", "Mine").directive("EXT_ONE", "Mine"));
        StagedPack dropsExtendable = rig.stage(derived("2.0.0").directive("LOCKED_ONE", "Mine"));

        assertThat(rig.register("derived", dropsExtendable, Preconditions.none())
                        .outcome())
                .as("REMOVE is within extendable")
                .isEqualTo(Outcome.REPLACED);
        Map<String, String> before = rig.filesOf(rig.packs.resolve("derived"));

        assertThat(refusalOf(() -> rig.register("derived", rig.stage(derived("3.0.0")), Preconditions.none())))
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
        assertThat(rig.filesOf(rig.packs.resolve("derived")))
                .as("nothing changed")
                .isEqualTo(before);
    }

    @Test
    void anEdgeAddedFromALockedArtifactIsGatedAndOneFromAnOpenArtifactIsNot(@TempDir Path host) {
        Rig rig = rigWithBase(host);
        rig.register("derived", rig.stage(derived("1.0.0")), Preconditions.none());
        StagedPack fromLocked = rig.stage(derived("2.0.0").file("edges.graph.yaml", shard("LOCKED_ONE")));

        assertThat(refusalOf(() -> rig.register("derived", fromLocked, Preconditions.none())))
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);

        StagedPack fromOpen = rig.stage(derived("2.0.0").file("edges.graph.yaml", shard("OPEN_ONE")));
        assertThat(rig.register("derived", fromOpen, Preconditions.none()).outcome())
                .as("the control: the edge's source is open")
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void anEdgeRemovedFromALockedArtifactIsGatedAndOneFromAnOpenArtifactIsNot(@TempDir Path host) {
        Rig rig = rigWithBase(host);
        rig.install("derived", derived("1.0.0").file("edges.graph.yaml", shard("LOCKED_ONE", "OPEN_ONE")));
        StagedPack dropsOpen = rig.stage(derived("2.0.0").file("edges.graph.yaml", shard("LOCKED_ONE")));

        assertThat(rig.register("derived", dropsOpen, Preconditions.none()).outcome())
                .as("the control: the dropped edge's source is open")
                .isEqualTo(Outcome.REPLACED);

        StagedPack dropsLocked = rig.stage(derived("3.0.0"));
        assertThat(refusalOf(() -> rig.register("derived", dropsLocked, Preconditions.none())))
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
    }

    @Test
    void aStoredPackThatCannotBeReadIsReplacedByAFixedUpload(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install(
                "pack-x",
                Packs.of(ORG_PACK, "pack-x", "1.0.0", "org")
                        .file(
                                "directives/DIRECTIVE_001.directive.yaml",
                                Packs.directiveText("DIRECTIVE_001", "One") + "references:\n  - directive:X\n"));
        StagedPack fixed =
                rig.stage(Packs.of(ORG_PACK, "pack-x", "2.0.0", "org").directive("DIRECTIVE_001", "One"));

        assertThat(rig.register("pack-x", fixed, Preconditions.none()).outcome())
                .as("a whole-pack replace is how an unreadable pack is repaired")
                .isEqualTo(Outcome.REPLACED);
    }

    // ---- an unreadable stored pack is diffed by what can still be read (DM-01M6V4WMF8CD8NBN7A065E9ZQ5) --------------

    /** A directive that still carries a field ADR-0064 D5 removed: the pack that holds it cannot be read whole. */
    private static final String LEGACY_DIRECTIVE =
            Packs.directiveText("DERIVED_ONE", "Own") + "references:\n  - directive:X\n";

    private static Packs unreadableDerived(String version) {
        return derived(version).file("directives/DERIVED_ONE.directive.yaml", LEGACY_DIRECTIVE);
    }

    @Test
    void aStoredPackThatCannotBeReadStillGatesTheRemovalOfALockedArtifact(@TempDir Path host) {
        Rig rig = rigWithBase(host);
        rig.install("derived", unreadableDerived("1.0.0").directive("LOCKED_ONE", "Mine"));
        Map<String, String> before = rig.filesOf(rig.packs.resolve("derived"));

        assertThat(refusalOf(() -> rig.register("derived", rig.stage(derived("2.0.0")), Preconditions.none())))
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
        assertThat(rig.filesOf(rig.packs.resolve("derived")))
                .as("nothing changed")
                .isEqualTo(before);

        StagedPack keeps = rig.stage(derived("2.0.0").directive("LOCKED_ONE", "Mine"));
        assertThat(rig.register("derived", keeps, Preconditions.none()).outcome())
                .as("the control: the fixed upload keeps the locked artifact and repairs the pack")
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void aStoredPackThatCannotBeReadStillGatesTheDroppedEdgeOfALockedSource(@TempDir Path host) {
        Rig rig = rigWithBase(host);
        rig.install("derived", unreadableDerived("1.0.0").file("edges.graph.yaml", shard("LOCKED_ONE")));

        assertThat(refusalOf(() -> rig.register("derived", rig.stage(derived("2.0.0")), Preconditions.none())))
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);

        StagedPack keeps = rig.stage(derived("2.0.0").file("edges.graph.yaml", shard("LOCKED_ONE")));
        assertThat(rig.register("derived", keeps, Preconditions.none()).outcome())
                .as("the control: the fixed upload keeps the edge")
                .isEqualTo(Outcome.REPLACED);
    }

    private static final String BAD_EDGE =
            "- source: directive:OPEN_ONE\n  target: directive:DERIVED_ONE\n  relation: not_a_relation\n";

    /** Asserts that dropping a stored document, which breaks the graph schema, is refused when it held a locked edge. */
    private void assertDroppedGraphIsGated(Path host, String file, String lockedText, String openText) {
        Rig rig = rigWithBase(host.resolve("locked"));
        rig.install("derived", derived("1.0.0").file(file, lockedText));

        assertThat(refusalOf(() -> rig.register("derived", rig.stage(derived("2.0.0")), Preconditions.none())))
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);

        Rig control = rigWithBase(host.resolve("open"));
        control.install("derived", derived("1.0.0").file(file, openText));
        assertThat(control.register("derived", control.stage(derived("2.0.0")), Preconditions.none())
                        .outcome())
                .as("the control: the same document without the locked edge")
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void aDroppedLockedEdgeIsGatedWhenItsDocumentHoldsAnEntryWithAnUnknownRelation(@TempDir Path host) {
        assertDroppedGraphIsGated(
                host, "edges.graph.yaml", shard("LOCKED_ONE") + BAD_EDGE, shard("OPEN_ONE") + BAD_EDGE);
    }

    @Test
    void aDroppedLockedEdgeIsGatedWhenItsDocumentHasAnUnknownTopLevelKey(@TempDir Path host) {
        assertDroppedGraphIsGated(
                host, "edges.graph.yaml", shard("LOCKED_ONE") + "extra_key: 1\n", shard("OPEN_ONE") + "extra_key: 1\n");
    }

    @Test
    void aDroppedLockedEdgeIsGatedWhenTheRegisteredShardHoldsABadEntry(@TempDir Path host) {
        String locked = RelationshipShard.write(List.of(Relationship.of(
                        Urn.parse("directive:LOCKED_ONE").orElseThrow(),
                        Urn.parse("directive:DERIVED_ONE").orElseThrow(),
                        RelationType.REQUIRES)))
                + BAD_EDGE;
        String open = RelationshipShard.write(List.of(Relationship.of(
                        Urn.parse("directive:OPEN_ONE").orElseThrow(),
                        Urn.parse("directive:DERIVED_ONE").orElseThrow(),
                        RelationType.REQUIRES)))
                + BAD_EDGE;

        assertDroppedGraphIsGated(host, "registered.graph.yaml", locked, open);
    }

    @Test
    void anArtifactFileThatDoesNotBindCountsByTheIdItDeclaresNotByItsFileName(@TempDir Path host) {
        Rig rig = rigWithBase(host.resolve("locked"));
        rig.install(
                "derived",
                derived("1.0.0")
                        .file(
                                "directives/DECOY.directive.yaml",
                                Packs.directiveText("LOCKED_ONE", "Mine") + "references:\n  - directive:X\n"));

        assertThat(refusalOf(() -> rig.register("derived", rig.stage(derived("2.0.0")), Preconditions.none())))
                .as("DECOY.directive.yaml holds LOCKED_ONE")
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);

        Rig control = rigWithBase(host.resolve("open"));
        control.install(
                "derived",
                derived("1.0.0")
                        .file(
                                "directives/DECOY.directive.yaml",
                                Packs.directiveText("OPEN_ONE", "Mine") + "references:\n  - directive:X\n"));
        assertThat(control.register("derived", control.stage(derived("2.0.0")), Preconditions.none())
                        .outcome())
                .as("the control: the same file holding an open id")
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void aFileThatBindsUnderANameThatIsNotItsIdStillCountsByItsId(@TempDir Path host) {
        Rig rig = rigWithBase(host);
        rig.install(
                "derived",
                derived("1.0.0")
                        .file("directives/DECOY.directive.yaml", Packs.directiveText("LOCKED_ONE", "Mine"))
                        .file("directives/DERIVED_ONE.directive.yaml", LEGACY_DIRECTIVE));

        assertThat(refusalOf(() -> rig.register("derived", rig.stage(derived("2.0.0")), Preconditions.none())))
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
    }

    @Test
    void aReplaceMayNotTakeTheNameOfAnotherStoredPack(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("pack-x", rig.stage(Packs.of(ORG_PACK, "pack-x", "1.0.0", "org")), Preconditions.none());
        rig.register("other", rig.stage(Packs.of(OTHER_PACK, "other", "1.0.0", "org")), Preconditions.none());
        Map<String, String> before = rig.filesOf(rig.packs.resolve("pack-x"));

        assertThat(refusalOf(() -> rig.register(
                        "pack-x", rig.stage(Packs.of(ORG_PACK, "other", "2.0.0", "org")), Preconditions.none())))
                .isEqualTo(RefusalCode.REGISTRATION_CONFLICT);
        assertThat(rig.filesOf(rig.packs.resolve("pack-x"))).isEqualTo(before);

        assertThat(rig.register(
                                "pack-x",
                                rig.stage(Packs.of(ORG_PACK, "renamed", "2.0.0", "org")),
                                Preconditions.none())
                        .outcome())
                .as("the control: a name nothing holds")
                .isEqualTo(Outcome.REPLACED);
    }

    @Test
    void anEmptyDirectoryAnInterruptedCreateLeftDoesNotWedgeThePackId(@TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        Files.createDirectories(rig.packs.resolve("newpack"));

        StagedPack upload = rig.stage(Packs.of(ORG_PACK, "newpack", "1.0.0", "org"));
        RegistrationOutcome[] answered = new RegistrationOutcome[1];

        assertThatCode(() -> answered[0] = rig.register("newpack", upload, Preconditions.createOnly()))
                .as("an empty entry is no pack: the create takes it, even create-only")
                .doesNotThrowAnyException();
        assertThat(answered[0]).isNotNull();
        assertThat(answered[0].outcome()).isEqualTo(Outcome.CREATED);
        assertThat(rig.filesOf(rig.packs.resolve("newpack"))).containsKey("pack.yaml");
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    static RefusalCode refusalOf(Runnable action) {
        return refusal(action).code();
    }

    static CharterRefusal refusal(Runnable action) {
        CharterRefusal[] caught = new CharterRefusal[1];
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CharterRefusal.class, r -> caught[0] = r);
        return caught[0];
    }

    static PreconditionException.Kind preconditionOf(Runnable action) {
        PreconditionException.Kind[] caught = new PreconditionException.Kind[1];
        assertThatThrownBy(action::run).isInstanceOfSatisfying(PreconditionException.class, e -> caught[0] = e.kind());
        return caught[0];
    }

    /** A pack as files: pack-relative path to text, in a stable order. */
    static final class Packs {
        private final Map<String, String> files = new LinkedHashMap<>();

        static Packs of(String packId, String name, String version, String level) {
            return of(packId, name, version, level, "");
        }

        static Packs of(String packId, String name, String version, String level, String extraDescriptor) {
            Packs packs = new Packs();
            packs.files.put("pack.yaml", descriptorText(packId, name, version, level, extraDescriptor));
            return packs;
        }

        static String descriptorText(String packId, String name, String version, String level, String extra) {
            return "pack_id: " + packId + "\npack_version: " + version + "\nname: " + name + "\npack_level: " + level
                    + "\n" + extra;
        }

        static String directiveText(String id, String title) {
            return "schema_version: \"1.0\"\nid: " + id + "\ntitle: " + title + "\nintent: Intent of " + id
                    + "\nenforcement: required\n";
        }

        Packs directive(String id, String title) {
            files.put("directives/" + id + ".directive.yaml", directiveText(id, title));
            return this;
        }

        Packs tactic(String id) {
            files.put(
                    "tactics/" + id + ".tactic.yaml",
                    "schema_version: \"1.0\"\nid: " + id + "\nname: " + id + "\npurpose: Purpose of " + id
                            + ".\nsteps:\n  - title: Step\n    description: Do it.\n");
            return this;
        }

        Packs registeredEdge(String from, String to) {
            files.put(
                    "registered.graph.yaml",
                    RelationshipShard.write(List.of(Relationship.of(
                            Urn.parse("directive:" + from).orElseThrow(),
                            Urn.parse("directive:" + to).orElseThrow(),
                            RelationType.REQUIRES))));
            return this;
        }

        Packs with(String file, String text) {
            Packs copy = new Packs();
            copy.files.putAll(files);
            if (text == null) {
                copy.files.remove(file);
            } else {
                copy.files.put(file, text);
            }
            return copy;
        }

        Packs file(String file, String text) {
            files.put(file, text);
            return this;
        }

        Map<String, String> files() {
            return files;
        }
    }

    /** The real filesystem adapters, wired as the deployment wires them, over a temporary configuration root. */
    static final class Rig {
        final Path host;
        final Path packs;
        final Path staging;
        final PackWriteGeneration generation = new PackWriteGeneration();
        final RegistryWriteLock lock = new RegistryWriteLock(Duration.ofSeconds(30));
        final StorageRootLocations locations;
        final DomainRepositoryService repository;
        final PackCommitStore commits;
        final CommitStorePackWriteStore writeStore;
        final PackRegistrationService service;

        /** The root sets of the stored activations a test plants: an empty optional is an activation with no packs. */
        final List<Optional<List<String>>> activations = new ArrayList<>();

        /** The packs the activations slice was told were registered, in order. */
        final List<String> registered = new ArrayList<>();

        /** How often the impact check read the composition facts of each pack, by configured name. */
        final Map<String, Integer> factReads = new java.util.concurrent.ConcurrentHashMap<>();

        private final AtomicInteger staged = new AtomicInteger();

        /** What the swap does inside the window where the live directory is missing; a test sets it. */
        volatile Runnable midSwap = () -> {};

        Rig(Path host) {
            this.host = host;
            this.packs = host.resolve("packs");
            this.staging = host.resolve("staging");
            YamlSerializationStrategy strategy = new YamlSerializationStrategy();
            this.locations = new StorageRootLocations(packs);
            this.repository = new DomainRepositoryService(locations, strategy);
            this.commits =
                    SlowSwapCommitStores.pausingMidSwap(locations, strategy, live -> midSwap.run(), lock, generation);
            this.writeStore = new CommitStorePackWriteStore(locations, strategy, commits, lock, generation);
            ConsistentRead read = new ConsistentRead(new FileSystemPackSources(locations::all), generation);
            WritePolicyGate gate = new PackMutabilityPolicy(
                    new OfferingReadService(repository, repository, repository, read), repository, repository, read);
            this.service = new PackRegistrationService(
                    new WholePackStore(strategy),
                    commits,
                    writeStore,
                    repository,
                    repository,
                    gate,
                    new InboundReferences(repository, repository, repository),
                    new RegistrationImpact(
                            repository,
                            repository,
                            pack -> {
                                factReads.merge(pack.value(), 1, Integer::sum);
                                return repository.compositionFacts(pack);
                            },
                            new ActivationImpact() {
                                @Override
                                public List<Optional<List<String>>> activationRoots() {
                                    return List.copyOf(activations);
                                }

                                @Override
                                public void registered(String packId, RegistrationImpact.Notices notices) {
                                    registered.add(packId);
                                }
                            }));
            create(packs);
        }

        RegistrationOutcome register(String packId, StagedPack upload, Preconditions preconditions) {
            return service.register(packId, upload, preconditions);
        }

        /** Writes the pack's files into a fresh staging directory named after the pack. */
        StagedPack stage(Packs pack) {
            Path directory = staging.resolve("n" + staged.incrementAndGet()).resolve("upload");
            pack.files().forEach((file, text) -> write(directory.resolve(file), text));
            return new StagedPack(directory);
        }

        /** Puts a pack straight into the pack domain, as an operator would. */
        void install(String directoryName, Packs pack) {
            pack.files()
                    .forEach((file, text) -> write(packs.resolve(directoryName).resolve(file), text));
        }

        /** Every regular file of a tree, pack-relative to its text. */
        Map<String, String> filesOf(Path directory) {
            assertThat(directory).as("the pack is there to read").isDirectory();
            Map<String, String> out = new TreeMap<>();
            try (Stream<Path> walk = Files.walk(directory)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    out.put(
                            directory.relativize(file).toString().replace('\\', '/'),
                            Files.readString(file, StandardCharsets.UTF_8));
                }
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            return out;
        }

        /** Names under the pack domain that start with {@code .pack-} or hold {@code .pack-old-}: swap residue. */
        List<String> asidesBeside(Path live) {
            try (Stream<Path> siblings = Files.list(live.getParent())) {
                return siblings.map(sibling -> String.valueOf(sibling.getFileName()))
                        .filter(name -> name.contains(".pack-old-") || name.startsWith(".pack-"))
                        .toList();
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }

        private static void create(Path directory) {
            try {
                Files.createDirectories(directory);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }

        private static void write(Path file, String text) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, text, StandardCharsets.UTF_8);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }
}
