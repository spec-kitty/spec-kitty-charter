package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The files of artifacts of a stored pack: the file each is stored in and the companions it names (ADR-0039), which the
 * removal of the artifact removes with it. The place of each artifact is the one {@link DomainRepositoryService} records
 * when it assembles the pack, so a file kept anywhere the pack's layout allows is found where it is.
 */
final class ArtifactFiles {

    private ArtifactFiles() {}

    /**
     * The pack-relative files of the artifacts, read from one read of the pack.
     *
     * @param directory the stored pack's directory
     * @param artifacts the artifacts
     * @param strategy the serialization format
     * @return their files, each artifact's own then its companions, in the order the artifacts are given; an artifact
     *     the pack does not store adds none
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the pack cannot be read
     */
    static List<String> of(Path directory, List<Urn> artifacts, SerializationStrategy strategy) {
        Path packDir = directory.toAbsolutePath().normalize();
        Path parent = packDir.getParent();
        if (parent == null || packDir.getFileName() == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "a pack directory needs a parent directory");
        }
        Companions.Rules rules = Companions.Rules.bundled();
        FilesystemPackStore.PackRead read = new FilesystemPackStore(parent, strategy, rules)
                .read(new PackIdentifier(String.valueOf(packDir.getFileName())), KindBindings.all());
        Map<Urn, String> places = new HashMap<>();
        DomainRepositoryService.assembly(new PackIdentifier(read.descriptor().packId()), read, places);
        List<String> files = new ArrayList<>();
        for (Urn artifact : artifacts) {
            String file = places.get(artifact);
            if (file == null) {
                continue;
            }
            files.add(file);
            for (KindBinding<?, ?> kind : KindBindings.all()) {
                if (kind.hasCompanions()) {
                    files.addAll(companionsOf(packDir, rules, kind, read, file));
                }
            }
        }
        return files;
    }

    private static <D> List<String> companionsOf(
            Path packDir,
            Companions.Rules rules,
            KindBinding<D, ?> kind,
            FilesystemPackStore.PackRead read,
            String file) {
        List<String> out = new ArrayList<>();
        for (FilesystemPackStore.Stored<D> stored : read.elements(kind)) {
            if (!stored.file().equals(file)) {
                continue;
            }
            for (KindBinding.Companion companion : kind.companions(stored.dso())) {
                if (Companions.resolve(rules, packDir, stored.file(), companion) instanceof Companions.Inside at) {
                    out.add(at.packRelative());
                }
            }
        }
        return out;
    }
}
