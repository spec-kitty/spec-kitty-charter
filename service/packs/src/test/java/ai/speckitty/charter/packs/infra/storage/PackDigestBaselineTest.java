package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * NFR-001: a pack that holds no {@code enhancements/} directory keeps the source fingerprint it had before the
 * step-enhancement binding existed. The five digests below are {@code digestOfPack} of every pack of the pinned
 * spec-kitty-open-packs checkout ({@code openPacksRef} 22c14b2d): {@code built-in}, {@code cave-kitty},
 * {@code event-driven}, {@code internal} and {@code qa}, none of which holds an {@code enhancements/} directory. They
 * were captured on lane commit 2e6280af (the WP01 base, mission base 0b60fd2d), which binds no step enhancement, and
 * are never edited: if one goes red the binding moved a fingerprint, so fix the code (append, never insert).
 *
 * <p>Runs under {@code :packs:openPacksRoundTrip} (tag {@code open-packs}), which supplies the checkout and never
 * skips.
 */
@Tag("open-packs")
@DisplayName("Packs without step enhancements keep their fingerprint (NFR-001)")
class PackDigestBaselineTest {

    private static final Map<String, String> BASELINE = Map.of(
            "built-in", "sha256:8fc26a9aba5353296adf1f578f249078a53d05ecf2d7ad64f466d8fbe61bef63",
            "cave-kitty", "sha256:9d4cc137bf896f7d5eb90ddb6aef804814bd3d4b0018030b61458e9bb5dc4a40",
            "event-driven", "sha256:db7226ce6e1910a5971ee897cf7a2623710b15c90d8a9432ae17fe9abb277f5f",
            "internal", "sha256:0adc10634f40cf069acbe56f43c7d1113ddbdb58b62b9c577086beaefaba9039",
            "qa", "sha256:b12ebcb3f25b6cdd2ad96f5723ef2f2eb677a1288f22d0a7871f1e6a13344a5d");

    /** Fail with the pin's own message rather than as a digest mismatch when the checkout is at another commit. */
    @BeforeAll
    static void theCheckoutIsAtThePin() {
        OpenPacksCorpus.assertPinned();
    }

    @Test
    void everyPinnedPackKeepsItsDigest() throws IOException {
        Map<String, String> actual = new LinkedHashMap<>();
        for (PackLocation pack : OpenPacksCorpus.packs()) {
            actual.put(pack.name(), FileSystemPackSources.digestOfPack(pack.root(), pack.name()));
        }

        assertThat(actual).containsExactlyInAnyOrderEntriesOf(BASELINE);
    }
}
