package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.PackRemovalStore;
import ai.speckitty.charter.packs.application.PackWriteStore;
import ai.speckitty.charter.packs.domain.Guard;
import ai.speckitty.charter.packs.domain.GuardImplementation;
import ai.speckitty.charter.packs.domain.Pack;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The per-file write path judges the reference closure (#355, operator rulings of 2026-11-17): the commit store's two
 * choke points, {@link CommitStorePackWriteStore#commit} and {@link CommitStorePackWriteStore#remove}, refuse a write
 * whose pack would not close with {@code 409 registration_conflict} and the reason the load would record, under the
 * pack's write lock and before anything is staged. A delete of an asset a guard names is refused, not cascaded.
 */
@DisplayName("A per-file write that would leave a pack that does not close is refused with registration_conflict")
class CommitStoreClosureTest {

    private static final String PACK = "guard-lifecycle";
    private static final PackIdentifier PACK_ID = new PackIdentifier(PACK);

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    @TempDir
    Path host;

    private CommitStorePackWriteStore store() {
        StorageRootLocations locations = new StorageRootLocations(host);
        return new CommitStorePackWriteStore(locations, strategy, new PackCommitStore(locations, strategy));
    }

    private Pack load() {
        return new DomainRepositoryService(new StorageRootLocations(host), strategy).load(PACK_ID);
    }

    /** Every file of the pack with its bytes, so a refused write is shown to change nothing. */
    private Map<String, String> tree() throws IOException {
        Path pack = host.resolve(PACK);
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(pack)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.put(pack.relativize(file).toString(), Files.readString(file));
            }
        }
        return files;
    }

    private static Guard implementedBy(Guard guard, String spelling) {
        return new Guard(
                guard.id(),
                guard.packId(),
                guard.schemaVersion().orElseThrow(),
                guard.title(),
                guard.description(),
                guard.interfaceName(),
                GuardImplementation.parse(spelling),
                guard.configSchema(),
                guard.limits(),
                guard.onIndeterminate());
    }

    private static Guard traceability(Pack pack) {
        return pack.artifacts(ArtifactKind.GUARD, Guard.class).stream()
                .filter(guard -> guard.localId().equals("traceability-ready"))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("a write of a guard whose asset the pack does not hold is refused, and nothing is written")
    void aGuardThatDoesNotCloseIsRefused() throws IOException {
        PackFixtures.copyOf(PACK, host);
        Pack loaded = load();
        Guard dangling = implementedBy(traceability(loaded), "asset:missing-lint");
        Map<String, String> before = tree();
        CommitStorePackWriteStore store = store();
        String revision = store.currentRevision(PACK_ID).token();

        assertThatThrownBy(() -> store.commit(
                        PACK_ID, revision, PackWriteStore.Change.artifact(loaded.with(dangling), dangling)))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT))
                .hasMessage("guard 'traceability-ready' is implemented_by asset:missing-lint, which the pack does not"
                        + " hold");
        assertThat(tree()).as("a refused write changes no file").isEqualTo(before);
    }

    @Test
    @DisplayName("the same write of a guard that closes is not refused by the closure")
    void aGuardThatClosesIsWritten() throws IOException {
        PackFixtures.copyOf(PACK, host);
        Pack loaded = load();
        Guard closing = implementedBy(traceability(loaded), "asset:carrier-lint");
        CommitStorePackWriteStore store = store();
        String revision = store.currentRevision(PACK_ID).token();

        store.commit(PACK_ID, revision, PackWriteStore.Change.artifact(loaded.with(closing), closing));

        assertThat(traceability(load()).implementation().spelling()).isEqualTo("asset:carrier-lint");
    }

    @Test
    @DisplayName("a delete of the asset a guard names is refused, not cascaded, and nothing is removed")
    void aDeleteOfAnAssetAGuardNamesIsRefused() throws IOException {
        PackFixtures.copyOf(PACK, host);
        Map<String, String> before = tree();
        CommitStorePackWriteStore store = store();

        assertThatThrownBy(() -> store.remove(
                        PACK_ID,
                        PackRemovalStore.Removal.ofArtifacts(
                                List.of(new Urn(NodeKind.ASSET, "carrier-lint")), List.of(), List.of(), List.of())))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.REGISTRATION_CONFLICT))
                .hasMessage("guard 'traceability-ready' is implemented_by asset:carrier-lint, which the pack does not"
                        + " hold");
        assertThat(tree()).as("a refused delete removes no file").isEqualTo(before);
    }

    @Test
    @DisplayName("a delete of an asset no guard names is not refused by the closure")
    void aDeleteOfAnAssetNoGuardNamesPasses() throws IOException {
        PackFixtures.copyOf(PACK, host);

        store().remove(
                        PACK_ID,
                        PackRemovalStore.Removal.ofArtifacts(
                                List.of(new Urn(NodeKind.ASSET, "notes")), List.of(), List.of(), List.of()));

        assertThat(load().assets()).extracting(asset -> asset.id().value()).containsExactly("carrier-lint");
    }
}
