package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.missions.domain.GuardEntry;
import ai.speckitty.charter.missions.domain.Lifecycle;
import ai.speckitty.charter.missions.domain.LifecycleTransition;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.BuiltinGuard;
import ai.speckitty.charter.packs.domain.Executable;
import ai.speckitty.charter.packs.domain.ExecutableRuntime;
import ai.speckitty.charter.packs.domain.Guard;
import ai.speckitty.charter.packs.domain.GuardImplementation;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Acceptance: a pack that declares guards and a lifecycle loads, derives each guard's {@code implemented_by} edge and
 * writes back meaning the same; a guard or lifecycle whose references do not close, or whose asset cannot run, is
 * refused at load and left out of the pack, failing closed (ADR-0054 decisions 1 to 7, ADR-0050 decision 17).
 */
@DisplayName("Guards and lifecycles load, close over their references and fail closed (ADR-0054, #156)")
class GuardAndLifecycleLoadTest {

    private static final String PACK = "guard-lifecycle";
    private static final PackIdentifier PACK_ID = new PackIdentifier(PACK);
    private static final String ASSET = "assets/carrier_lint.py.asset.yaml";
    private static final String BUILTIN_GUARD = "guards/subtasks-ready.guard.yaml";
    private static final String ASSET_GUARD = "guards/traceability-ready.guard.yaml";
    private static final String LIFECYCLE = "lifecycles/work-package.lifecycle.yaml";

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    @TempDir
    Path host;

    private Pack load(Path pack) {
        return new DomainRepositoryService(() -> List.of(new PackLocation(PACK, PackLevel.ORG, pack)), strategy)
                .load(PACK_ID);
    }

    private Path pack() throws IOException {
        return PackFixtures.copyOf(PACK, host);
    }

    private static void edit(Path pack, String file, UnaryOperator<String> change) throws IOException {
        Path path = pack.resolve(file);
        String before = Files.readString(path);
        String after = change.apply(before);
        assertThat(after).as("the edit of %s changes it", file).isNotEqualTo(before);
        Files.writeString(path, after);
    }

    /** The refusal of the lifecycle once a guard it names in_progress -> for_review is left out (the cascade). */
    private static PackElementViolation lifecycleLoses(String guard) {
        return new PackElementViolation(
                LIFECYCLE,
                "reference",
                "lifecycle 'work-package' transition in_progress -> for_review names guard '" + guard
                        + "', which the pack does not hold");
    }

    private static List<String> ids(Pack pack, ArtifactKind kind) {
        return pack.artifacts(kind).stream().map(a -> a.localId()).sorted().toList();
    }

    // ---- the pack that closes ------------------------------------------------------------------------------------

    @Test
    @DisplayName("the pack loads its guards, its lifecycle and its executable asset, with no violation")
    void thePackLoads() throws IOException {
        Pack loaded = load(pack());

        assertThat(loaded.violations()).isEmpty();
        assertThat(ids(loaded, ArtifactKind.GUARD)).containsExactly("subtasks-ready", "traceability-ready");
        assertThat(ids(loaded, ArtifactKind.LIFECYCLE)).containsExactly("work-package");
        Asset lint = loaded.assets().stream()
                .filter(a -> a.id().value().equals("carrier-lint"))
                .findFirst()
                .orElseThrow();
        assertThat(lint.executable()).hasValueSatisfying(executable -> {
            assertThat(executable.runtime().token()).isEqualTo("python");
            assertThat(executable.entry()).isEqualTo("carrier_lint.py");
            assertThat(executable.argv()).containsExactly("carrier_lint.py", "--guard");
        });
    }

    @Test
    @DisplayName("an asset-backed guard derives one implemented_by edge to its asset; a built-in guard derives none")
    void implementedByIsDerivedIntoTheGraph() throws IOException {
        Pack loaded = load(pack());
        List<Guard> guards = loaded.artifacts(ArtifactKind.GUARD, Guard.class);

        Guard scripted = guards.stream()
                .filter(g -> g.localId().equals("traceability-ready"))
                .findFirst()
                .orElseThrow();
        Guard builtin = guards.stream()
                .filter(g -> g.localId().equals("subtasks-ready"))
                .findFirst()
                .orElseThrow();

        assertThat(scripted.derivedRelationships())
                .containsExactly(Relationship.of(
                        new Urn(NodeKind.GUARD, "traceability-ready"),
                        new Urn(NodeKind.ASSET, "carrier-lint"),
                        RelationType.IMPLEMENTED_BY));
        assertThat(builtin.implementation())
                .isEqualTo(new GuardImplementation.Builtin(BuiltinGuard.IMPLEMENTATION_READY));
        assertThat(builtin.derivedRelationships()).isEmpty();
    }

    @Test
    @DisplayName("the lifecycle keeps its states, its transitions in order and each guard entry as authored")
    void theLifecycleKeepsItsMachine() throws IOException {
        Lifecycle lifecycle =
                load(pack()).artifacts(ArtifactKind.LIFECYCLE, Lifecycle.class).getFirst();

        assertThat(lifecycle.initial()).isEqualTo("planned");
        assertThat(lifecycle.terminalStates()).containsExactly("done", "canceled");
        assertThat(lifecycle.transitions()).hasSize(6);
        LifecycleTransition review = lifecycle.transitions().get(1);
        assertThat(review.guards())
                .extracting(GuardEntry::blocks)
                .as("an absent blocking blocks; blocking: false only reports")
                .containsExactly(true, false);
        assertThat(lifecycle.edgesFrom("for_review"))
                .as("from a list")
                .extracting(LifecycleTransition::to)
                .containsExactly("in_progress", "done", "canceled");
        assertThat(lifecycle.edgesFrom("done"))
                .as("a terminal state has no edge out")
                .isEmpty();
        assertThat(lifecycle.edgesFrom("blocked"))
                .as("* reaches every non-terminal state")
                .extracting(LifecycleTransition::to)
                .containsExactly("in_progress", "canceled");
    }

    @Test
    @DisplayName("the whole pack writes back meaning the same as its source (ADR-0036)")
    void thePackWritesBackMeaningTheSame() throws IOException {
        Path source = pack();
        WholePackStore store = new WholePackStore(strategy);
        WholePackStore.LoadedPack loaded = store.load(source);
        assertThat(loaded.pack().violations()).isEmpty();
        Path target = host.resolve("out").resolve(PACK);

        store.write(loaded, target);

        Map<String, String> before = yamlOf(source);
        Map<String, String> after = yamlOf(target);
        java.util.SortedSet<String> expected = new java.util.TreeSet<>(before.keySet());
        expected.add(PackFileSet.MANIFEST); // #110: every write generates the manifest
        assertThat(after.keySet()).containsExactlyElementsOf(expected);
        MeaningComparator comparator = MeaningComparator.adr0036();
        before.forEach((file, text) -> {
            String kind = file.startsWith("guards/") ? "guard" : file.startsWith("lifecycles/") ? "lifecycle" : null;
            if (kind != null || file.startsWith("assets/")) {
                assertThat(comparator.compareTexts(kind == null ? "asset" : kind, text, after.get(file)))
                        .as(file)
                        .isEmpty();
            }
        });
    }

    // ---- references that do not close are refused -----------------------------------------------------------------

    @Test
    @DisplayName("a guard implemented_by an asset the pack does not hold is refused, and so is a lifecycle naming it")
    void aGuardWithAMissingAssetIsRefused() throws IOException {
        Path pack = pack();
        edit(pack, ASSET_GUARD, text -> text.replace("asset:carrier-lint", "asset:missing-lint"));

        Pack loaded = load(pack);

        assertThat(ids(loaded, ArtifactKind.GUARD)).containsExactly("subtasks-ready");
        assertThat(ids(loaded, ArtifactKind.LIFECYCLE)).isEmpty();
        assertThat(loaded.violations())
                .containsExactly(
                        new PackElementViolation(
                                ASSET_GUARD,
                                "reference",
                                "guard 'traceability-ready' is implemented_by asset:missing-lint, which the pack does"
                                        + " not hold"),
                        new PackElementViolation(
                                LIFECYCLE,
                                "reference",
                                "lifecycle 'work-package' transition in_progress -> for_review names guard"
                                        + " 'traceability-ready', which the pack does not hold"));
    }

    @Test
    @DisplayName("a guard implemented_by an asset with no executable block is refused")
    void aGuardWhoseAssetDoesNotRunIsRefused() throws IOException {
        Path pack = pack();
        edit(pack, ASSET_GUARD, text -> text.replace("asset:carrier-lint", "asset:notes"));

        Pack loaded = load(pack);

        assertThat(ids(loaded, ArtifactKind.GUARD)).containsExactly("subtasks-ready");
        assertThat(ids(loaded, ArtifactKind.LIFECYCLE)).isEmpty();
        assertThat(loaded.violations())
                .containsExactly(
                        new PackElementViolation(
                                ASSET_GUARD,
                                "reference",
                                "guard 'traceability-ready' is implemented_by asset:notes, which declares no executable"
                                        + " block"),
                        lifecycleLoses("traceability-ready"));
    }

    @Test
    @DisplayName("a cross-pack implemented_by is refused until the pack dependency closure can resolve it")
    void aCrossPackImplementationIsRefused() throws IOException {
        Path pack = pack();
        edit(pack, ASSET_GUARD, text -> text.replace("asset:carrier-lint", "qa::asset:qa-traceability-lint"));

        Pack loaded = load(pack);

        assertThat(ids(loaded, ArtifactKind.GUARD)).containsExactly("subtasks-ready");
        assertThat(ids(loaded, ArtifactKind.LIFECYCLE)).isEmpty();
        assertThat(loaded.violations())
                .containsExactly(
                        new PackElementViolation(
                                ASSET_GUARD,
                                "reference",
                                "guard 'traceability-ready' is implemented_by qa::asset:qa-traceability-lint, a"
                                        + " pack-qualified reference; it is refused until the pack dependency closure"
                                        + " resolves it"),
                        lifecycleLoses("traceability-ready"));
    }

    @Test
    @DisplayName(
            "a lifecycle naming a guard of another pack is refused until the pack dependency closure can resolve it")
    void aCrossPackGuardEntryIsRefused() throws IOException {
        Path pack = pack();
        edit(pack, LIFECYCLE, text -> text.replace("  - ref: traceability-ready\n", "  - ref: qa::guard:other\n"));

        Pack loaded = load(pack);

        assertThat(ids(loaded, ArtifactKind.LIFECYCLE)).isEmpty();
        assertThat(loaded.violations())
                .containsExactly(new PackElementViolation(
                        LIFECYCLE,
                        "reference",
                        "lifecycle 'work-package' transition in_progress -> for_review names guard qa::guard:other,"
                                + " a pack-qualified reference; it is refused until the pack dependency closure"
                                + " resolves it"));
    }

    @Test
    @DisplayName("a guard entry whose config does not fit the guard's config_schema is refused")
    void aConfigThatDoesNotFitIsRefused() throws IOException {
        Path pack = pack();
        edit(pack, LIFECYCLE, text -> text.replace("      carrier: qa-carrier.yaml\n", "      carrier: 3\n"));

        Pack loaded = load(pack);

        assertThat(ids(loaded, ArtifactKind.LIFECYCLE)).isEmpty();
        assertThat(loaded.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(LIFECYCLE);
            assertThat(violation.kind()).isEqualTo("reference");
            assertThat(violation.reason())
                    .startsWith("lifecycle 'work-package' transition in_progress -> for_review: the config of guard"
                            + " 'traceability-ready' does not fit its config_schema:")
                    .contains("/carrier");
        });
    }

    @Test
    @DisplayName("a guard entry that leaves out a config its guard's config_schema requires is refused")
    void aMissingRequiredConfigIsRefused() throws IOException {
        Path pack = pack();
        edit(
                pack,
                LIFECYCLE,
                text -> text.replace("    config:\n      carrier: qa-carrier.yaml\n    blocking: false\n", ""));

        Pack loaded = load(pack);

        assertThat(ids(loaded, ArtifactKind.LIFECYCLE)).isEmpty();
        assertThat(loaded.violations())
                .containsExactly(new PackElementViolation(
                        LIFECYCLE,
                        "reference",
                        "lifecycle 'work-package' transition in_progress -> for_review: the config of guard"
                                + " 'traceability-ready' does not fit its config_schema: required at /"));
    }

    @Test
    @DisplayName("a guard entry with a config, for a guard that declares no config_schema, is refused")
    void aConfigWithNoSchemaIsRefused() throws IOException {
        Path pack = pack();
        edit(pack, LIFECYCLE, text -> text.replace("  - ref: subtasks-ready\n    blocking: true\n", """
                  - ref: subtasks-ready
                    config:
                      strict: true
                """));

        Pack loaded = load(pack);

        assertThat(ids(loaded, ArtifactKind.LIFECYCLE)).isEmpty();
        assertThat(loaded.violations())
                .containsExactly(new PackElementViolation(
                        LIFECYCLE,
                        "reference",
                        "lifecycle 'work-package' transition for_review -> done: guard 'subtasks-ready' declares no"
                                + " config_schema, so its config cannot be checked"));
    }

    @Test
    @DisplayName("a guard whose config_schema is not a valid JSON Schema is refused")
    void anInvalidConfigSchemaIsRefused() throws IOException {
        Path pack = pack();
        edit(pack, ASSET_GUARD, text -> text.replace("      type: string\n", "      type: text\n"));

        Pack loaded = load(pack);

        assertThat(ids(loaded, ArtifactKind.GUARD)).containsExactly("subtasks-ready");
        assertThat(ids(loaded, ArtifactKind.LIFECYCLE)).isEmpty();
        assertThat(loaded.violations()).hasSize(2);
        assertThat(loaded.violations().get(0)).satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(ASSET_GUARD);
            assertThat(violation.kind()).isEqualTo("reference");
            assertThat(violation.reason())
                    .startsWith("guard 'traceability-ready': its config_schema is not a valid JSON Schema:")
                    .contains("/properties/carrier/type");
        });
        assertThat(loaded.violations().get(1)).isEqualTo(lifecycleLoses("traceability-ready"));
    }

    // ---- shapes the domain refuses ---------------------------------------------------------------------------------

    @Test
    @DisplayName("a lifecycle whose transition names a state it does not declare is refused")
    void anUnknownStateIsRefused() throws IOException {
        Path pack = pack();
        edit(
                pack,
                LIFECYCLE,
                text -> text.replace("  to: in_progress\n  event: claim\n", "  to: doing\n  event: claim\n"));

        Pack loaded = load(pack);

        assertThat(ids(loaded, ArtifactKind.LIFECYCLE)).isEmpty();
        assertThat(loaded.violations())
                .singleElement()
                .satisfies(violation -> assertThat(violation.reason())
                        .contains("A lifecycle transition names only states the lifecycle declares."));
    }

    @Test
    @DisplayName(
            "an executable's entry names any payload of its pack; one the pack does not hold, or a sidecar, drops the asset")
    void anEntryNamesAPayloadThePackHolds() throws IOException {
        Path another = pack();
        edit(another, ASSET, text -> text.replace("  entry: carrier_lint.py\n", "  entry: notes.txt\n"));
        assertThat(load(another).violations()).as("another payload of the pack").isEmpty();

        Path missing = PackFixtures.copyOf(PACK, Files.createTempDirectory(host, "missing"));
        edit(missing, ASSET, text -> text.replace("  entry: carrier_lint.py\n", "  entry: other.py\n"));
        assertDroppedWithItsGuard(load(missing), "reference", "is not a payload file the pack holds");

        Path sidecar = PackFixtures.copyOf(PACK, Files.createTempDirectory(host, "sidecar"));
        edit(sidecar, ASSET, text -> text.replace("  entry: carrier_lint.py\n", "  entry: notes.txt.asset.yaml\n"));
        assertDroppedWithItsGuard(load(sidecar), "reference", "is not a payload file the pack holds");
    }

    @Test
    @DisplayName("an executable whose args repeat its entry is dropped with its guard, never fatal to the load")
    void aDomainRefusedAssetIsDroppedNotFatal() throws IOException {
        Path pack = pack();
        edit(pack, ASSET, text -> text.replace("  - --guard\n", "  - --guard\n  - carrier_lint.py\n"));

        assertDroppedWithItsGuard(load(pack), "domain", "exactly one argv element");
    }

    @Test
    @DisplayName("a schema violation in the asset drops the asset, then its guard, then the lifecycle that names it")
    void theExecutableHardeningIsCheckedAtLoad() throws IOException {
        Path pack = pack();
        edit(pack, ASSET, text -> text.replace("  runtime: python\n", "  runtime: sh\n"));

        Pack loaded = load(pack);

        assertThat(loaded.violations()).hasSize(3);
        assertThat(loaded.violations().get(0)).satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(ASSET);
            assertThat(violation.kind()).isEqualTo("schema");
            assertThat(violation.reason()).contains("runtime");
        });
        assertThat(loaded.violations().subList(1, 3))
                .containsExactly(guardLosesItsAsset(), lifecycleLoses("traceability-ready"));
        assertThat(loaded.assets()).extracting(a -> a.id().value()).containsExactly("notes");
        assertThat(ids(loaded, ArtifactKind.GUARD)).containsExactly("subtasks-ready");
    }

    @Test
    @DisplayName("a guard file outside guards/ is refused by placement")
    void aMisplacedGuardIsRefused() throws IOException {
        Path pack = pack();
        Files.move(pack.resolve(BUILTIN_GUARD), pack.resolve("subtasks-ready.guard.yaml"));

        Pack loaded = load(pack);

        assertThat(ids(loaded, ArtifactKind.GUARD)).containsExactly("traceability-ready");
        assertThat(ids(loaded, ArtifactKind.LIFECYCLE)).isEmpty();
        assertThat(loaded.violations())
                .extracting(PackElementViolation::reason)
                .containsExactly(
                        "guard 'subtasks-ready' is not at its place '" + BUILTIN_GUARD + "'",
                        lifecycleLoses("subtasks-ready").reason());
    }

    @Test
    @DisplayName("a whole-pack write refuses a pack holding a guard that does not close, and leaves nothing behind")
    void aWholePackWriteRefusesAGuardThatDoesNotClose() throws IOException {
        WholePackStore store = new WholePackStore(strategy);
        WholePackStore.LoadedPack loaded = store.load(pack());
        assertThat(loaded.pack().violations()).isEmpty();
        // A pack assembled in memory, with no read violation, whose guard names an asset the pack does not hold.
        Guard guard = loaded.pack().artifacts(ArtifactKind.GUARD, Guard.class).stream()
                .filter(g -> g.localId().equals("traceability-ready"))
                .findFirst()
                .orElseThrow();
        Guard dangling = danglingGuardOf(guard);
        WholePackStore.LoadedPack broken = new WholePackStore.LoadedPack(
                loaded.pack().with(dangling), loaded.layout(), loaded.graphs(), loaded.unmodelled(), loaded.manifest());
        Path out = host.resolve("out");
        Path target = out.resolve(PACK);

        assertThatThrownBy(() -> store.write(broken, target))
                .isInstanceOf(CharterRefusal.class)
                .hasMessage("guard 'traceability-ready' is implemented_by asset:missing-lint, which the pack does not"
                        + " hold");
        assertThat(target).doesNotExist();
        // The same refusal for an executable whose entry names a payload the pack does not hold.
        Asset asset = loaded.pack().assets().stream()
                .filter(a -> a.localId().equals("carrier-lint"))
                .findFirst()
                .orElseThrow();
        Asset strayEntry = strayEntryOf(asset);
        WholePackStore.LoadedPack strayed = new WholePackStore.LoadedPack(
                loaded.pack().with(strayEntry),
                loaded.layout(),
                loaded.graphs(),
                loaded.unmodelled(),
                loaded.manifest());
        assertThatThrownBy(() -> store.write(strayed, target))
                .isInstanceOf(CharterRefusal.class)
                .hasMessageContaining("executable entry 'other.py', which is not a payload file the pack holds");
        assertThat(target).doesNotExist();
        try (Stream<Path> left = Files.exists(out) ? Files.list(out) : Stream.empty()) {
            assertThat(left).as("no staging directory is left").isEmpty();
        }
    }

    /** The guard, implemented by an asset the pack does not hold. */
    private static Guard danglingGuardOf(Guard guard) {
        return new Guard(
                guard.id(),
                guard.packId(),
                guard.schemaVersion().orElseThrow(),
                guard.title(),
                guard.description(),
                guard.interfaceName(),
                GuardImplementation.parse("asset:missing-lint"),
                guard.configSchema(),
                guard.limits(),
                guard.onIndeterminate());
    }

    /** The asset, with an executable entry that names a payload the pack does not hold. */
    private static Asset strayEntryOf(Asset asset) {
        return Asset.builder()
                .id(asset.id())
                .packId(asset.packId())
                .mime(asset.mime())
                .path(asset.path())
                .title(asset.title().orElse(null))
                .executable(new Executable(
                        ExecutableRuntime.PYTHON,
                        asset.executable().orElseThrow().minVersion(),
                        "other.py",
                        asset.executable().orElseThrow().args()))
                .build();
    }

    private static PackElementViolation guardLosesItsAsset() {
        return new PackElementViolation(
                ASSET_GUARD,
                "reference",
                "guard 'traceability-ready' is implemented_by asset:carrier-lint, which the pack does not hold");
    }

    /** The asset is left out with one violation of the given kind naming its rule, and the guard and lifecycle it backs with it. */
    private static void assertDroppedWithItsGuard(Pack loaded, String kind, String rule) {
        assertThat(loaded.assets()).extracting(a -> a.id().value()).containsExactly("notes");
        assertThat(ids(loaded, ArtifactKind.GUARD))
                .as("the guard it backs fails closed")
                .containsExactly("subtasks-ready");
        assertThat(ids(loaded, ArtifactKind.LIFECYCLE))
                .as("and the lifecycle naming that guard")
                .isEmpty();
        assertThat(loaded.violations()).hasSize(3);
        assertThat(loaded.violations().get(0)).satisfies(violation -> {
            assertThat(violation.kind()).isEqualTo(kind);
            assertThat(violation.reason()).contains(rule);
        });
        assertThat(loaded.violations().subList(1, 3))
                .containsExactly(guardLosesItsAsset(), lifecycleLoses("traceability-ready"));
    }

    private static Map<String, String> yamlOf(Path root) throws IOException {
        Map<String, String> out = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (relative.endsWith(".yaml") && !relative.equals("pack.yaml")) {
                    out.put(relative, Files.readString(path));
                }
            }
        }
        return out;
    }
}
