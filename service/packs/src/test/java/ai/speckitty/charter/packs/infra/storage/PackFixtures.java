package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/** The shared {@code roundtrip-fixtures} packs, copied into a temporary directory so a test may write to them. */
final class PackFixtures {

    static final String ALPHA_FILE = "alpha-tactic.tactic.yaml";
    static final String BETA_FILE = "beta-tactic.tactic.yaml";
    static final String BROKEN_FILE = "broken-tactic.tactic.yaml";

    private PackFixtures() {}

    /**
     * The body of every step-enhancement fragment, as the canonical render of what the binding's shape holds (ADR-0036,
     * T-03): a dropped or altered fragment changes the meaning even if the entry that names it is unchanged.
     */
    static Map<String, String> fragments(WholePackStore.LoadedPack loaded) {
        Map<String, String> out = new LinkedHashMap<>();
        for (CharterArtifact artifact : loaded.pack().artifacts(ArtifactKind.STEP_ENHANCEMENT)) {
            for (StepEnhancementDso.Entry entry :
                    StepEnhancementBinding.INSTANCE.dsoOf(artifact).promptFragments()) {
                out.put(
                        artifact.localId() + " " + entry.slot() + " " + entry.path(),
                        entry.body().map(MarkdownCodec::write).orElse("<absent>"));
            }
        }
        return out;
    }

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
