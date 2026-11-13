package ai.speckitty.charter.artifacts.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.artifacts.application.AssetReads;
import ai.speckitty.charter.artifacts.application.AssetView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.AssetIdentifier;
import ai.speckitty.charter.packs.domain.Executable;
import ai.speckitty.charter.packs.domain.ExecutableRuntime;
import ai.speckitty.charter.packs.domain.Payload;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The packs adapter maps the packs slice's asset into the edge's view, judging the mime by the packs domain's rule, and
 * passes the revision and the verified fingerprint through; the list is in id order.
 */
class PacksAssetsTest {

    private static final PackIdentifier QA = new PackIdentifier("qa");
    private static final String FINGERPRINT = "sha256:" + "f".repeat(64);
    private static final PackRevision REVISION = PackRevision.compute(List.of("qa"));

    private final ForeignArtifactRead packs = mock(ForeignArtifactRead.class);

    private static Asset asset(String id, String mime, @Nullable Executable executable) {
        return Asset.builder()
                .id(new AssetIdentifier(id))
                .packId(QA)
                .mime(mime)
                .path(id + ".bin")
                .title(executable == null ? null : "Title of " + id)
                .executable(executable)
                .build();
    }

    @Test
    void theListIsInIdOrderWithEachMimeJudgedByTheDomainRule() {
        when(packs.held(QA, ArtifactKind.ASSET))
                .thenReturn(new ConsistentRead.Verified<>(
                        new ForeignArtifactRead.Held(
                                List.of(asset("zeta", "plain", null), asset("alpha", "image/png", null)), REVISION),
                        FINGERPRINT));

        AssetReads.Listed listed = new PacksAssets(packs).list(QA);

        assertThat(listed.assets()).extracting(AssetView::id).containsExactly("alpha", "zeta");
        assertThat(listed.assets()).extracting(AssetView::wellFormedMime).containsExactly(true, false);
        assertThat(listed.revision()).isEqualTo(REVISION.token());
        assertThat(listed.sourceFingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void theItemMapsEveryFieldOfTheDescriptor() {
        Executable run = new Executable(ExecutableRuntime.PYTHON, Optional.of("3.11"), "run.py", List.of("--guard"));
        ForeignArtifactRead.Foreign foreign = new ForeignArtifactRead.Foreign(
                asset("run", "text/x-python", run), REVISION, Optional.empty(), List.of());
        when(packs.read(QA, ArtifactKind.ASSET, "run", 0))
                .thenReturn(new ConsistentRead.Verified<>(foreign, FINGERPRINT));

        AssetReads.Read read = new PacksAssets(packs).asset(QA, "run");

        assertThat(read.asset())
                .isEqualTo(new AssetView(
                        "run",
                        QA,
                        "text/x-python",
                        true,
                        "run.bin",
                        Optional.of("Title of run"),
                        Optional.of(new AssetView.Executable(
                                "python", Optional.of("3.11"), "run.py", List.of("--guard")))));
        assertThat(read.revision()).isEqualTo(REVISION.token());
        assertThat(read.sourceFingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void thePayloadCarriesTheBytesOfTheReadThatVerifiedThem() {
        byte[] bytes = {0, 1, (byte) 0xff};
        when(packs.payload(QA, "logo"))
                .thenReturn(new ConsistentRead.Verified<>(
                        new ForeignArtifactRead.AssetPayload(
                                asset("logo", "image/png", null), Payload.of(bytes), REVISION),
                        FINGERPRINT));

        AssetReads.PayloadRead read = new PacksAssets(packs).payload(QA, "logo");

        assertThat(read.bytes()).isEqualTo(bytes);
        assertThat(read.asset().mime()).isEqualTo("image/png");
        assertThat(read.asset().title()).isEmpty();
        assertThat(read.asset().executable()).isEmpty();
        assertThat(read.revision()).isEqualTo(REVISION.token());
        assertThat(read.sourceFingerprint()).isEqualTo(FINGERPRINT);
    }
}
