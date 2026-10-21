package ai.speckitty.charter.packs.infra.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** The shared {@code roundtrip-fixtures} packs, copied into a temporary directory so a test may write to them. */
final class PackFixtures {

    static final String ALPHA_FILE = "alpha-tactic.tactic.yaml";
    static final String BETA_FILE = "beta-tactic.tactic.yaml";
    static final String BROKEN_FILE = "broken-tactic.tactic.yaml";

    private PackFixtures() {}

    /** A copy of the fixture pack {@code name} at {@code host/name}; the directory name is part of file classification. */
    static Path copyOf(String name, Path host) throws IOException {
        Path source = fixture(name);
        Path target = host.resolve(name);
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.sorted(Comparator.naturalOrder()).toList()) {
                Path to = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(path, to);
                }
            }
        }
        return target;
    }

    private static Path fixture(String name) {
        try {
            return Path.of(PackFixtures.class
                    .getResource("/roundtrip-fixtures/" + name)
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        } catch (RuntimeException e) {
            throw new UncheckedIOException(new IOException("missing fixture " + name, e));
        }
    }
}
