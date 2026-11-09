package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * NFR-001: a pack that holds no {@code enhancements/} directory keeps the source fingerprint it had before the
 * step-enhancement binding existed. The five digests below are {@code digestOfPack} of every pack of the pinned
 * spec-kitty-open-packs checkout ({@code openPacksRef} a2a9379) that holds no {@code enhancements/} directory:
 * {@code built-in}, {@code cave-kitty}, {@code event-driven}, {@code internal} and {@code qa}. The sixth pack at the
 * pin, {@code spdd} (added by open-packs 170dbc3), holds {@code missions/software-dev/enhancements/}, so NFR-001 does
 * not cover it and the test asserts it is the only pack left out.
 *
 * <p>The values are captured on code that binds no step enhancement, never recomputed by the code under test: they
 * are what this test's first version (commit 0ce147a5, whose parent 1d962f26 is the WP01 base and binds no step
 * enhancement) prints at the pin, run from a {@code git archive 0ce147a5} export with
 * {@code ./gradlew :packs:openPacksRoundTrip --tests '*PackDigestBaselineTest' -PopenPacksDir=<checkout>
 * -PopenPacksRef=<pin>}. They equal what the bound code computes, so the binding moves no fingerprint. The values at
 * 22c14b2d (8fc26a9a, 9d4cc137, db7226ce, 0adc1063, b12ebcb3, captured on lane commit 2e6280af) changed with the
 * pin's content only. If one goes red at an unchanged pin, the binding moved a fingerprint: fix the code (append, never
 * insert), not the constants.
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout and never
 * skips.
 */
@Tag("open-packs")
@DisplayName("Packs without step enhancements keep their fingerprint (NFR-001)")
class PackDigestBaselineTest {

    private static final Map<String, String> BASELINE = Map.of(
            "built-in", "sha256:124159bb55d8d5bdd69d17d3421b58c4d0597640f2ab1b576d4052a295de8612",
            "cave-kitty", "sha256:331d457434b72fddbe58aff41323b87fd47cbd293889a9c0ebe84354804a6245",
            "event-driven", "sha256:c922b1256135428e59fba341f4e47ffa508603b4f371a4c32da33cca1cbc8245",
            "internal", "sha256:9d0ebf86cf159db15b79d256d9264be59f97127e0653c9704fb0d0f36e934b20",
            "qa", "sha256:f191121f50b2800292c10be2f418e9a1e6aa2e61763c22b0dd3af49da18254ec");

    /** The packs at the pin that hold an {@code enhancements/} directory, so NFR-001 does not cover them. */
    private static final List<String> WITH_ENHANCEMENTS = List.of("spdd");

    /** Fail with the pin's own message rather than as a digest mismatch when the checkout is at another commit. */
    @BeforeAll
    static void theCheckoutIsAtThePin() {
        OpenPacksCorpus.assertPinned();
    }

    @Test
    void everyPinnedPackKeepsItsDigest() throws IOException {
        Map<String, String> actual = new LinkedHashMap<>();
        List<String> enhanced = new ArrayList<>();
        for (PackLocation pack : OpenPacksCorpus.packs()) {
            if (holdsEnhancements(pack.root())) {
                enhanced.add(pack.name());
            } else {
                actual.put(pack.name(), FileSystemPackSources.digestOfPack(pack.root(), pack.name()));
            }
        }

        assertThat(enhanced).as("packs with an enhancements/ directory").containsExactlyElementsOf(WITH_ENHANCEMENTS);
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(BASELINE);
    }

    private static boolean holdsEnhancements(Path pack) throws IOException {
        try (Stream<Path> walk = Files.walk(pack)) {
            return walk.anyMatch(path ->
                    Files.isDirectory(path) && path.getFileName().toString().equals("enhancements"));
        }
    }
}
