package ai.speckitty.charter.packcheck;

import ai.speckitty.charter.packs.domain.PackRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The packs of a config root, laid out as the service's {@code charter.config-root} is (#478, T015): one directory per
 * pack under {@code <root>/packs/}, each at the level its {@code pack.yaml} declares.
 */
final class ConfigRoot {

    /** The directory of a config root that holds its packs. */
    static final String PACKS = "packs";

    private ConfigRoot() {}

    /**
     * The packs of a config root: the directories directly under {@code root/packs}, in name order, each named by its
     * directory and with no configured level, so the level its {@code pack.yaml} declares applies. A directory reached
     * through a symbolic link is a pack (#174); a plain file is not.
     *
     * @param root the config root
     * @return the packs; empty when {@code root/packs} is absent or holds no directory
     * @throws IOException when {@code root/packs} cannot be listed
     */
    static List<PackRoot> packs(Path root) throws IOException {
        Path packs = root.resolve(PACKS);
        if (!Files.isDirectory(packs)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(packs)) {
            return entries.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getFileName())))
                    .map(entry -> new PackRoot(String.valueOf(entry.getFileName()), Optional.empty(), entry.toString()))
                    .toList();
        }
    }
}
