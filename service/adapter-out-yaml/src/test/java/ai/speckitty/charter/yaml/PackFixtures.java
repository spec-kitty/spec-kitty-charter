package ai.speckitty.charter.yaml;

import ai.speckitty.charter.domain.Tactic;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
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

    /**
     * A pack directory that holds only the id of each given tactic, so that a write has a file to replace but
     * nothing of the fixture's content: whatever a refetch returns came from the write.
     */
    static Path placeholderOf(String name, Path host, List<Tactic> tactics, List<String> files) throws IOException {
        Path target = Files.createDirectories(host.resolve(name));
        for (int i = 0; i < tactics.size(); i++) {
            Files.writeString(
                    target.resolve(files.get(i)), "id: " + tactics.get(i).id() + "\n");
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
