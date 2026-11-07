package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
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
import ai.speckitty.charter.packs.infra.storage.PackCommitStore;
import ai.speckitty.charter.packs.infra.storage.RelationshipShard;
import ai.speckitty.charter.packs.infra.storage.StorageRootLocations;
import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            Packs packs = new Packs();
            packs.files.put("pack.yaml", descriptorText(packId, name, version, level, ""));
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
        private final AtomicInteger staged = new AtomicInteger();

        Rig(Path host) {
            this(host, new PackMutabilityPolicy());
        }

        Rig(Path host, WritePolicyGate gate) {
            this.host = host;
            this.packs = host.resolve("packs");
            this.staging = host.resolve("staging");
            YamlSerializationStrategy strategy = new YamlSerializationStrategy();
            this.locations = new StorageRootLocations(packs);
            this.repository = new DomainRepositoryService(locations, strategy);
            this.commits = new PackCommitStore(locations, strategy, lock, generation);
            this.writeStore = new CommitStorePackWriteStore(locations, strategy, commits, lock, generation);
            this.service = new PackRegistrationService(
                    new WholePackStore(strategy),
                    commits,
                    writeStore,
                    repository,
                    repository,
                    gate,
                    new InboundReferences(repository, repository, repository));
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
