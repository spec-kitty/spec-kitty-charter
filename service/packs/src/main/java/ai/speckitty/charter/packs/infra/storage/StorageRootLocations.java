package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackLevel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The packs of the pack domain, {@code <config-root>/packs}: every directory directly under it is a pack, read and
 * written in place and named after its directory. Its level is the one its own {@code pack.yaml} declares
 * ({@code pack_level}), so built-in, org and project packs live side by side; a pack without a readable declaration is
 * an org pack, and the pack policy reports what is wrong with it.
 *
 * <p>The directory is listed on every call, so a pack copied in or removed while the service runs is seen on the next
 * read; a domain directory that does not exist yet holds no packs. Three kinds of entry are never packs:
 *
 * <ul>
 *   <li>hidden directories, among them the {@code .pack-commit-*} staging directory of {@link PackCommitStore};
 *   <li>a {@code <name>.pack-old-*} directory, the aside copy a commit leaves when its swap is interrupted;
 *   <li>a symbolic link: a commit stages a copy of the pack and swaps it into place, and on a link that would replace
 *       the link with a copy holding only the written file. Copy a pack in; do not link it.
 * </ul>
 */
public final class StorageRootLocations implements DomainRepositoryService.Locations {

    private static final String ASIDE_MARKER = ".pack-old-";

    private final Path packs;

    /**
     * The packs under {@code packs}.
     *
     * @param packs the pack domain's directory; it need not exist yet
     */
    public StorageRootLocations(Path packs) {
        this.packs = packs.toAbsolutePath().normalize();
    }

    @Override
    public List<PackLocation> all() {
        if (!Files.isDirectory(packs, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(packs)) {
            return children.filter(child -> Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS))
                    .filter(StorageRootLocations::isPack)
                    .map(StorageRootLocations::location)
                    .sorted(Comparator.comparing((PackLocation location) -> location.level() != PackLevel.BUILT_IN)
                            .thenComparing(PackLocation::name))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list the pack domain", e);
        }
    }

    private static boolean isPack(Path directory) {
        String name = String.valueOf(directory.getFileName());
        return !name.startsWith(".") && !name.contains(ASIDE_MARKER);
    }

    private static PackLocation location(Path directory) {
        return new PackLocation(String.valueOf(directory.getFileName()), declaredLevel(directory), directory);
    }

    /** The {@code pack_level} the pack's {@code pack.yaml} declares; org when there is no readable declaration. */
    private static PackLevel declaredLevel(Path directory) {
        Path packYaml = directory.resolve(PackFileSet.PACK_YAML);
        if (!Files.isRegularFile(packYaml, LinkOption.NOFOLLOW_LINKS)) {
            return PackLevel.ORG;
        }
        try {
            Object document = Yaml.load(Files.readString(packYaml, StandardCharsets.UTF_8));
            if (document instanceof Map<?, ?> map && map.get("pack_level") instanceof String token) {
                return PackLevel.fromToken(token).orElse(PackLevel.ORG);
            }
            return PackLevel.ORG;
        } catch (IOException | RuntimeException unreadable) {
            return PackLevel.ORG;
        }
    }
}
