package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * R21: the vendored {@code packs/internal} and {@code packs/qa} equal the pinned spec-kitty-open-packs checkout
 * byte for byte, except {@code PROVENANCE.md}, {@code README.md} (both local on purpose) and the listed
 * {@link #LOCAL_DELTA}. The test fails on a file that differs and is not listed, on a file the pinned checkout has
 * and the vendored copy lacks, on a file the vendored copy has and the pinned checkout lacks, and on a listed file
 * that is identical to upstream or absent, so the list cannot go stale. Run by {@code openPacksRoundTrip}, like the
 * round-trip it shares the pinned checkout with.
 */
@Tag("open-packs")
class VendoredPacksTest {

    /** Local files of a vendored pack that are not upstream's, never compared. */
    private static final Set<String> LOCAL_FILES = Set.of("PROVENANCE.md", "README.md");

    /**
     * The local delta against the pin, as paths under {@code packs/}. Each edit was made on this repository's main
     * (commit 19cc37f) and goes upstream in spec-kitty/spec-kitty-open-packs#59. When the pin moves past #59 the set
     * empties and the test says which entries are stale.
     */
    private static final Set<String> LOCAL_DELTA = Set.of(
            // The validate-pr-body asset and its manifest: spec-kitty/spec-kitty-open-packs#59.
            "internal/assets/validate-pr-body.py",
            "internal/assets/validate-pr-body.py.asset.yaml",
            // The fragment title "advisory closes link": spec-kitty/spec-kitty-open-packs#59.
            "internal/drg/fragment.yaml",
            // The semi-auto-delivery procedure: spec-kitty/spec-kitty-open-packs#59.
            "internal/procedures/semi-auto-delivery.procedure.yaml");

    @ParameterizedTest
    @ValueSource(strings = {"internal", "qa"})
    void theVendoredPackEqualsThePinnedOneExceptTheListedDelta(String pack) throws IOException {
        OpenPacksCorpus.assertPinned();
        Path vendored = repositoryRoot().resolve("packs").resolve(pack);
        Path upstream = OpenPacksCorpus.packsDirectory().resolve(pack);
        Set<String> vendoredFiles = files(vendored);
        Set<String> upstreamFiles = files(upstream);

        List<String> problems = new ArrayList<>();
        for (String file : upstreamFiles) {
            if (!vendoredFiles.contains(file) && !LOCAL_FILES.contains(file)) {
                problems.add("missing from the vendored copy: " + pack + "/" + file);
            }
        }
        for (String file : vendoredFiles) {
            if (LOCAL_FILES.contains(file)) {
                continue;
            }
            String path = pack + "/" + file;
            if (!upstreamFiles.contains(file)) {
                if (!LOCAL_DELTA.contains(path)) {
                    problems.add("not in the pinned checkout and not in LOCAL_DELTA: " + path);
                }
                continue;
            }
            boolean identical = Files.mismatch(vendored.resolve(file), upstream.resolve(file)) == -1;
            if (!identical && !LOCAL_DELTA.contains(path)) {
                problems.add("differs from the pinned checkout and is not in LOCAL_DELTA: " + path);
            }
            if (identical && LOCAL_DELTA.contains(path)) {
                problems.add("in LOCAL_DELTA but identical to the pinned checkout (remove it): " + path);
            }
        }
        for (String path : LOCAL_DELTA) {
            if (path.startsWith(pack + "/") && !vendoredFiles.contains(path.substring(pack.length() + 1))) {
                problems.add("in LOCAL_DELTA but absent from the vendored copy: " + path);
            }
        }
        assertThat(problems)
                .as("vendored %s against the pinned open-packs", pack)
                .isEmpty();
    }

    private static Path repositoryRoot() {
        String root = System.getProperty("charter.repositoryRoot");
        if (root == null) {
            throw new IllegalStateException("charter.repositoryRoot is not set by the build");
        }
        return Path.of(root);
    }

    private static Set<String> files(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            Set<String> result = new TreeSet<>();
            walk.filter(Files::isRegularFile)
                    .forEach(p -> result.add(dir.relativize(p).toString().replace('\\', '/')));
            return result;
        }
    }
}
