package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.Fingerprint;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
    @DisplayName("the revision differs from the real kernel Fingerprint of the same content (ADR-0026 D5)")
    void theRevisionIsDistinctFromTheReadFingerprint() {
        List<String> content = List.of("descriptor", "child-a");
        PackRevision revision = PackRevision.compute(content);

        // The read path digests its files with the kernel Fingerprint (FileSystemPackSources). Digest the very same
        // items with it: both are length-prefixed sha256, and the tokens must stay apart (ADR-0026 D5) so freshness
        // and concurrency are never confused. The domain-separation tag and the differing length encodings both
        // keep them apart today.
        Fingerprint read = Fingerprint.sha256();
        content.forEach(read::add);

        assertThat(read.value()).matches("sha256:[0-9a-f]{64}");
        assertThat(revision.token()).isNotEqualTo(read.value());
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

    /**
     * Text inputs and the revision {@code compute} gave them on {@code main} at 90de8f9, before the byte-level
     * {@code computeOverBytes} existed. Captured by running the unmodified {@code compute(List<String>)} over exactly
     * these inputs (a throwaway test printing {@code PackRevision.compute(...)}) on this lane's base, whose
     * {@code PackRevision} was byte-identical to 90de8f9's; pinned so the revision every existing pack has cannot move.
     */
    static Stream<Arguments> textsPinnedOnMain() {
        return Stream.of(
                Arguments.of(List.of(), "sha256:f0cc7dbfcfb01c478e0c9636993d991d173d9749e1579fe8cd56c0a1e57bc250"),
                Arguments.of(
                        List.of("descriptor"),
                        "sha256:31689adf5d7c7c8ed9de5de39b879a2c5b27be18b8400105bbd34c73acebe263"),
                Arguments.of(
                        List.of("pack_id: caf\u00e9 \u20ac \u4e2d\n", "line one\nline two\r\n", ""),
                        "sha256:bfe20cc8a57a9073ee03bd4cd7a60c48777d4534cf4e1dba8a01fac19abec42e"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("textsPinnedOnMain")
    @DisplayName("compute over texts equals computeOverBytes over their UTF-8 bytes and the value main computed")
    void textRevisionIsTheByteRevisionOfItsUtf8AndUnchangedFromMain(List<String> texts, String pinnedOnMain) {
        PackRevision overText = PackRevision.compute(texts);
        PackRevision overBytes = PackRevision.computeOverBytes(utf8(texts));

        assertThat(overText).isEqualTo(overBytes);
        assertThat(overText.token()).isEqualTo(pinnedOnMain);
    }

    @Test
    @DisplayName("bytes that are not UTF-8 text (a 0x89 lead, NUL bytes) compute, and a one-byte change moves it")
    void nonUtf8BytesComputeAndAOneByteChangeMovesTheRevision() {
        byte[] binary = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0x00, 0x00, (byte) 0xFF, (byte) 0xFE};
        byte[] oneByteOff = binary.clone();
        oneByteOff[oneByteOff.length - 1] = (byte) 0xFD;
        byte[] descriptor = "descriptor".getBytes(StandardCharsets.UTF_8);

        PackRevision revision = PackRevision.computeOverBytes(List.of(descriptor, binary));
        PackRevision variant = PackRevision.computeOverBytes(List.of(descriptor, oneByteOff));

        assertThat(revision.token()).matches("sha256:[0-9a-f]{64}");
        assertThat(revision)
                .isEqualTo(PackRevision.computeOverBytes(List.of(descriptor, binary.clone())))
                .isNotEqualTo(variant);
    }

    private static List<byte[]> utf8(List<String> texts) {
        return texts.stream().map(text -> text.getBytes(StandardCharsets.UTF_8)).toList();
    }
}
