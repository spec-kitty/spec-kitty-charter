package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The read side ({@link DomainRepositoryService}), the commit store ({@link PackCommitStore}) and the write store
 * ({@link CommitStorePackWriteStore}) resolve a pack identifier to the same configured directory (#184): the location
 * configured as that registered id. The {@code pack_id} a {@code pack.yaml} declares is no address (#583, D3).
 */
@DisplayName("Reads, commits and writes resolve a registered pack id to the same directory")
class PackDirectoryResolutionTest {

    private static final String ID_A = "01M5YVECC0MPAN10ND1GEST00A";
    private static final String ID_B = "01M5YVECC0MPAN10ND1GEST00B";

    @TempDir
    Path host;

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    /** A pack directory {@code dir} whose descriptor declares {@code packId} and names itself {@code dir}. */
    private Path pack(String dir, String packId) throws IOException {
        Path pack = Files.createDirectories(host.resolve(dir));
        Files.writeString(pack.resolve(PackFileSet.PACK_YAML), """
                pack_id: %s
                pack_version: 1.0.0
                name: %s
                pack_level: org
                """.formatted(packId, dir), StandardCharsets.UTF_8);
        return pack;
    }

    private static PackLocation at(String name, Path root) {
        return new PackLocation(name, PackLevel.ORG, root);
    }

    /** The directory each of the three resolves {@code pack} to, read through what each exposes. */
    private void assertAllResolve(List<PackLocation> configured, String pack, Path expected) {
        Locations locations = () -> configured;
        PackCommitStore commitStore = new PackCommitStore(locations, strategy);
        PackIdentifier id = new PackIdentifier(pack);

        assertThat(new DomainRepositoryService(locations, strategy).get(id).name())
                .as("read side")
                .isEqualTo(expected.getFileName().toString());
        assertThat(commitStore.liveDirectory(id))
                .as("commit store")
                .isEqualTo(expected.toAbsolutePath().normalize());
        assertThat(new CommitStorePackWriteStore(locations, strategy, commitStore).digestOf(id))
                .as("write store")
                .isEqualTo(FileSystemPackSources.digestOfPack(
                        expected.toAbsolutePath().normalize(), pack));
    }

    private void assertAllRefuse(List<PackLocation> configured, String pack, RefusalCode code) {
        Locations locations = () -> configured;
        PackCommitStore commitStore = new PackCommitStore(locations, strategy);
        PackIdentifier id = new PackIdentifier(pack);

        assertThatThrownBy(() -> new DomainRepositoryService(locations, strategy).get(id))
                .as("read side")
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(code));
        assertThatThrownBy(() -> commitStore.liveDirectory(id))
                .as("commit store")
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(code));
        assertThatThrownBy(() -> new CommitStorePackWriteStore(locations, strategy, commitStore).digestOf(id))
                .as("write store")
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(code));
    }

    @Test
    void theRegisteredIdResolvesTheLocationConfiguredAsIt() throws IOException {
        Path other = pack("other", ID_B);
        Path mine = pack("my-pack", ID_A);

        assertAllResolve(List.of(at("other", other), at("my-pack", mine)), "my-pack", mine);
    }

    @Test
    void theDescriptorsPackIdIsNoAddressBecauseItIsNoPackIdentifier() throws IOException {
        pack("my-pack", ID_A);

        assertThatThrownBy(() -> new PackIdentifier(ID_A))
                .isInstanceOfSatisfying(
                        ConstraintBreachException.class,
                        breach -> assertThat(breach.reason().detail()).isEqualTo(PackIdentifier.GRAMMAR_DETAIL));
    }

    @Test
    void anIdNoLocationIsConfiguredAsIsNotFound() throws IOException {
        Path other = pack("other", ID_B);

        assertAllRefuse(List.of(at("other", other)), "absent", RefusalCode.PACK_NOT_FOUND);
    }
}
