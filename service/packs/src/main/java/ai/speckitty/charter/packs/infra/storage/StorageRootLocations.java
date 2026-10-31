package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackLevel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The packs of the pack domain, {@code <config-root>/packs}: every directory directly under it is a pack, read and
 * written in place and named after its directory. Its level is the one its own {@code pack.yaml} declares
 * ({@code pack_level}), so built-in, org and project packs live side by side; a pack without a readable declaration is
 * an org pack, and the pack policy reports what is wrong with it.
 *
 * <p>The directory is listed on every call, so a pack copied in or removed while the service runs is seen on the next
 * read; a domain directory that does not exist yet holds no packs. A symbolic link to a directory is a pack named after
 * the link (#174, ADR-0038 decision 3 as amended 2026-10-31): the store reads it through the link, and a commit stages
 * and swaps beside the link's real target, so the link stays a link ({@link PackCommitStore}). Links below a pack's
 * root are not followed. These entries are never packs:
 *
 * <ul>
 *   <li>hidden entries, among them the {@code .pack-commit-*} staging directory of {@link PackCommitStore};
 *   <li>a {@code <name>.pack-old-*} entry, the aside copy a commit leaves when its swap is interrupted;
 *   <li>a file;
 *   <li>a link that resolves to no directory (a dangling link, a link to a file), or to the pack domain or a directory
 *       above it;
 *   <li>two or more entries that resolve to one directory (two links to one target, a link to a sibling pack): a
 *       commit to one name would change the pack under the other, and the per-pack write lock would not serialise
 *       them, so none of them is served.
 * </ul>
 *
 * <p>A refused link is logged as a warning once, naming the entry and why, not on every listing.
 */
public final class StorageRootLocations implements DomainRepositoryService.Locations {

    private static final String ASIDE_MARKER = ".pack-old-";

    private static final Logger LOG = LoggerFactory.getLogger(StorageRootLocations.class);

    private final Path packs;

    /** The warnings already logged, so a listing on every read does not repeat them. */
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

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
        Map<Path, List<Path>> byDirectory = new LinkedHashMap<>();
        try (Stream<Path> children = Files.list(packs)) {
            Path domain = packs.toRealPath();
            for (Path entry :
                    children.filter(StorageRootLocations::isPack).sorted().toList()) {
                directoryOf(entry, domain)
                        .ifPresent(real -> byDirectory
                                .computeIfAbsent(real, key -> new ArrayList<>())
                                .add(entry));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list the pack domain", e);
        }
        List<PackLocation> out = new ArrayList<>();
        byDirectory.forEach((real, entries) -> {
            if (entries.size() == 1) {
                out.add(location(entries.getFirst()));
            } else {
                report(entries.stream().map(StorageRootLocations::nameOf).toList() + " resolve to the same directory "
                        + real + "; none of them is served");
            }
        });
        out.sort(Comparator.comparing((PackLocation location) -> location.level() != PackLevel.BUILT_IN)
                .thenComparing(PackLocation::name));
        return List.copyOf(out);
    }

    /**
     * The real directory an entry is a pack of: a directory's own, a link's target's. Empty for a file, an entry that
     * vanished, and a link that resolves to no directory or to one that holds the pack domain; such a link is reported.
     */
    private Optional<Path> directoryOf(Path entry, Path domain) {
        boolean link = Files.isSymbolicLink(entry);
        if (!link && !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        Path real;
        try {
            real = entry.toRealPath();
        } catch (IOException unresolved) {
            if (link) {
                report("pack link " + nameOf(entry) + " does not resolve (" + unresolved + "); it is not served");
            }
            return Optional.empty();
        }
        if (!Files.isDirectory(real)) {
            report("pack link " + nameOf(entry) + " resolves to " + real
                    + ", which is not a directory; it is not served");
            return Optional.empty();
        }
        if (domain.startsWith(real)) {
            report("pack link " + nameOf(entry) + " resolves to " + real
                    + ", which holds the pack domain; it is not served");
            return Optional.empty();
        }
        return Optional.of(real);
    }

    private void report(String warning) {
        if (reported.add(warning)) {
            LOG.warn("{}", warning);
        }
    }

    private static String nameOf(Path entry) {
        return String.valueOf(entry.getFileName());
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
