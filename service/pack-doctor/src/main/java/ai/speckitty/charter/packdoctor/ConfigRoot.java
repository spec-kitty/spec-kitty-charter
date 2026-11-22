package ai.speckitty.charter.packdoctor;

import ai.speckitty.charter.packs.domain.PackEntryNames;
import ai.speckitty.charter.packs.domain.PackRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The packs of a config root, laid out as the service's {@code charter.config-root} is (#478, T015): one directory per
 * pack under {@code <root>/packs/}, each at the level its {@code pack.yaml} declares. The entries are the ones the
 * service's listing can serve, by the one rule both share ({@link PackEntryNames#canBeAPack}).
 */
final class ConfigRoot {

    /** The directory of a config root that holds its packs. */
    static final String PACKS = "packs";

    private ConfigRoot() {}

    /**
     * What a config root's {@code packs/} holds for pack-doctor.
     *
     * @param packs the packs, in name order
     * @param strayLinks the entries that can be packs but are links resolving to no directory (to nothing, or to a
     *     file), in name order; a pack the author expects to be checked, so the run refuses them rather than dropping
     *     them
     */
    record Listing(List<PackRoot> packs, List<Path> strayLinks) {
        /**
         * Copies the lists.
         *
         * @param packs the packs
         * @param strayLinks the links that resolve to no directory
         */
        Listing {
            packs = List.copyOf(packs);
            strayLinks = List.copyOf(strayLinks);
        }
    }

    /**
     * The packs of a config root: the entries directly under {@code root/packs} that can be packs (not hidden, not an
     * aside copy) and are directories, in name order, each named by its entry and with no configured level, so the level
     * its {@code pack.yaml} declares applies. A link to a directory is a pack (#174); a plain file is not; a link that
     * resolves to nothing or to a file is listed as a stray link, never dropped.
     *
     * @param root the config root
     * @return the listing; no pack when {@code root/packs} is absent or holds no directory
     * @throws IOException when {@code root/packs} cannot be listed
     */
    static Listing packs(Path root) throws IOException {
        Path packs = root.resolve(PACKS);
        if (!Files.isDirectory(packs)) {
            return new Listing(List.of(), List.of());
        }
        List<Path> entries;
        try (Stream<Path> listed = Files.list(packs)) {
            entries = listed.filter(entry -> PackEntryNames.canBeAPack(nameOf(entry)))
                    .sorted(Comparator.comparing(ConfigRoot::nameOf))
                    .toList();
        }
        List<PackRoot> roots = new ArrayList<>();
        List<Path> strayLinks = new ArrayList<>();
        for (Path entry : entries) {
            if (Files.isDirectory(entry)) {
                roots.add(new PackRoot(nameOf(entry), Optional.empty(), entry.toString()));
            } else if (Files.isSymbolicLink(entry)) {
                strayLinks.add(entry);
            }
        }
        return new Listing(roots, strayLinks);
    }

    private static String nameOf(Path entry) {
        return String.valueOf(entry.getFileName());
    }
}
