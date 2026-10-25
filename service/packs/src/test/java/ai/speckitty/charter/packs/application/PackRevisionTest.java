package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The write-side revision (ADR-0026 Decision 5): a stable digest over committed content, kept deliberately distinct
 * from the read-path source fingerprint. The distinctness case is load-bearing: it fails if the domain-separation tag
 * is dropped and the revision collapses onto a plain content hash.
 */
@DisplayName("PackRevision ETag digest")
class PackRevisionTest {

    @Test
    @DisplayName("a change to the committed content changes the revision")
    void aChangeChangesTheRevision() {
        PackRevision before = PackRevision.compute(List.of("descriptor", "child-a"));
        PackRevision after = PackRevision.compute(List.of("descriptor", "child-a-renamed"));

        assertThat(after).isNotEqualTo(before);
        assertThat(after.token()).isNotEqualTo(before.token());
    }

    @Test
    @DisplayName("an unchanged commit yields an equal revision with the sha256 shape")
    void anUnchangedCommitYieldsAnEqualRevision() {
        PackRevision one = PackRevision.compute(List.of("descriptor", "child-a"));
        PackRevision again = PackRevision.compute(List.of("descriptor", "child-a"));

        assertThat(again).isEqualTo(one).hasSameHashCodeAs(one);
        assertThat(one.token()).matches("sha256:[0-9a-f]{64}");
    }

    @Test
    @DisplayName("re-partitioning the same bytes across items changes the revision (length-prefixing)")
    void repartitioningChangesTheRevision() {
        PackRevision split = PackRevision.compute(List.of("ab", "c"));
        PackRevision joined = PackRevision.compute(List.of("a", "bc"));

        assertThat(split).isNotEqualTo(joined);
    }

    @Test
    @DisplayName("the revision differs from the read-path fingerprint scheme for the same bytes (ADR-0026 D5)")
    void theRevisionIsDistinctFromTheReadFingerprint() {
        List<String> content = List.of("descriptor", "child-a");
        PackRevision revision = PackRevision.compute(content);

        // The read-path fingerprint (ConsistentRead / PackSources) is sha256 over the raw bytes with no
        // domain-separation tag. Compute that undifferentiated digest over the same bytes and show the two tokens
        // never coincide: PackRevision carries its own tag, so freshness and concurrency stay apart.
        String undifferentiated =
                "sha256:" + hex(sha256(String.join("", content).getBytes(StandardCharsets.UTF_8)));

        assertThat(revision.token()).isNotEqualTo(undifferentiated);
    }

    @Test
    @DisplayName("matches() is true only for the current revision's own token")
    void matchesOnlyTheCurrentToken() {
        PackRevision current = PackRevision.compute(List.of("descriptor", "child-a"));
        PackRevision other = PackRevision.compute(List.of("descriptor", "child-b"));

        assertThat(PackRevision.matches(current.token(), current)).isTrue();
        assertThat(PackRevision.matches(other.token(), current)).isFalse();
        assertThat(PackRevision.matches(null, current)).isFalse();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sha256(byte[] bytes) {
        return sha256().digest(bytes);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }
}
