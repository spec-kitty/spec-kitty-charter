package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.AssetIdentifier;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.PackSources;
import ai.speckitty.charter.packs.domain.Payload;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The reads beside the item (#105): a pack's artifacts of one kind ({@link ForeignArtifactRead#held}) and an asset with
 * its payload's bytes ({@link ForeignArtifactRead#payload(PackIdentifier, String)}), each one verified read with the
 * pack check of the item, and the payload read against a fingerprint the caller holds (research R15).
 */
class ForeignArtifactReadHeldTest {

    private static final PackIdentifier QA = new PackIdentifier("qa");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0, (byte) 0xff, '\r', '\n'};

    private final AtomicInteger digests = new AtomicInteger();
    private final AtomicInteger revisionReads = new AtomicInteger();
    private String digest = "1";

    private static PackDescriptor descriptor() {
        return new PackDescriptor(
                "id-qa",
                "1.0.0",
                Optional.empty(),
                "qa",
                Optional.empty(),
                Optional.empty(),
                PackPaths.none(),
                List.of());
    }

    private static Asset asset(String id, String mime, String path) {
        return Asset.builder()
                .id(new AssetIdentifier(id))
                .packId(QA)
                .mime(mime)
                .path(path)
                .build();
    }

    /** qa holds the assets logo (a PNG) and run (a script), a directive, and a payload file no sidecar names. */
    private static Pack qa() {
        return Pack.builder()
                .id(QA)
                .descriptor(descriptor())
                .artifact(asset("run", "text/x-python", "run.py"))
                .artifact(asset("logo", "image/png", "logo.png"))
                .artifact(Directive.builder()
                        .id(new DirectiveIdentifier("D"))
                        .packId(QA)
                        .schemaVersion("1.0")
                        .title("D")
                        .intent("Intent")
                        .enforcement(Enforcement.REQUIRED)
                        .build())
                .assetFile(new AssetFile("logo.png", Payload.of(PNG)))
                .assetFile(new AssetFile(
                        "run.py", Payload.of("print('hi')\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .assetFile(new AssetFile("loose.txt", Payload.of(new byte[] {'x'})))
                .build();
    }

    private ForeignArtifactRead service() {
        PackSources sources = new PackSources() {
            @Override
            public String fingerprint() {
                digests.incrementAndGet();
                return "sha256:" + digest.repeat(64);
            }

            @Override
            public String fingerprintOf(Collection<String> packNames) {
                return fingerprint();
            }
        };
        Map<String, Pack> stored = Map.of("qa", qa());
        PackRepository packs = pack -> Optional.ofNullable(stored.get(pack.value()))
                .orElseThrow(() ->
                        new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found."));
        PackDescriptorRepository descriptors = new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier pack) {
                return find(pack).orElseThrow();
            }

            @Override
            public Optional<PackDescriptor> find(PackIdentifier pack) {
                packs.load(pack);
                return Optional.of(descriptor());
            }

            @Override
            public void save(PackIdentifier pack, PackDescriptor descriptor) {
                throw new UnsupportedOperationException("a read never writes");
            }
        };
        PackRevisions revisions = pack -> {
            revisionReads.incrementAndGet();
            return PackRevision.compute(List.of(pack.value(), digest));
        };
        return new ForeignArtifactRead(
                packs,
                descriptors,
                () -> List.of(new ConfiguredPack(QA, PackLevel.ORG)),
                new ConsistentRead(sources),
                revisions,
                // A profile's governance is never walked by these reads.
                Mockito.mock(ProfileGovernance.class));
    }

    @Test
    void heldAnswersThePacksArtifactsOfOneKindAndTheRevisionInOneVerifiedRead() {
        ConsistentRead.Verified<ForeignArtifactRead.Held> verified = service().held(QA, ArtifactKind.ASSET);

        assertThat(verified.value().artifacts())
                .extracting(CharterArtifact::localId)
                .containsExactlyInAnyOrder("logo", "run");
        assertThat(verified.value().artifacts()).allMatch(artifact -> artifact.kind() == ArtifactKind.ASSET);
        assertThat(verified.value().revision()).isEqualTo(PackRevision.compute(List.of("qa", "1")));
        assertThat(verified.fingerprint()).isEqualTo("sha256:" + "1".repeat(64));
        assertThat(digests).as("one verified read: two whole-source digests").hasValue(2);
        assertThat(revisionReads)
                .as("the revision is read once, inside the read")
                .hasValue(1);
    }

    @Test
    void heldOfAKindThePackHoldsNoneOfIsEmpty() {
        assertThat(service().held(QA, ArtifactKind.SKILL).value().artifacts()).isEmpty();
    }

    @Test
    void heldOfAnUnknownPackIsPackNotFound() {
        assertThatThrownBy(() -> service().held(new PackIdentifier("nope"), ArtifactKind.ASSET))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    @Test
    void payloadAnswersTheAssetTheExactBytesOfTheFileItNamesAndTheRevision() {
        ConsistentRead.Verified<ForeignArtifactRead.AssetPayload> verified =
                service().payload(QA, "logo");

        assertThat(verified.value().asset().localId()).isEqualTo("logo");
        assertThat(verified.value().asset().mime()).isEqualTo("image/png");
        assertThat(verified.value().payload().bytes()).isEqualTo(PNG);
        assertThat(verified.value().revision()).isEqualTo(PackRevision.compute(List.of("qa", "1")));
        assertThat(verified.fingerprint()).isEqualTo("sha256:" + "1".repeat(64));
        assertThat(digests).as("the asset and its bytes are one verified read").hasValue(2);
    }

    @Test
    void payloadOfAnAssetThePackDoesNotHoldIsArtifactNotFoundEvenWhenAFileOfThatNameIsHeld() {
        assertThatThrownBy(() -> service().payload(QA, "loose"))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
                    assertThat(refusal.getMessage()).isEqualTo("Pack qa has no asset loose.");
                });
    }

    @Test
    void payloadOfAnUnknownPackIsPackNotFound() {
        assertThatThrownBy(() -> service().payload(new PackIdentifier("nope"), "logo"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    @Test
    void payloadAgainstTheFingerprintTheReadVerifiesAnswersIt() {
        ConsistentRead.Verified<ForeignArtifactRead.AssetPayload> verified =
                service().payload(QA, "run", "sha256:" + "1".repeat(64));

        assertThat(verified.value().asset().localId()).isEqualTo("run");
        assertThat(verified.value().payload().bytes()).asString().isEqualTo("print('hi')\n");
    }

    @Test
    void payloadAgainstAnotherFingerprintIsStaleInput() {
        ForeignArtifactRead service = service();
        String held = "sha256:" + "1".repeat(64);
        digest = "2";

        assertThatThrownBy(() -> service.payload(QA, "logo", held))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    /**
     * An asset whose payload file the pack does not hold cannot reach the read: the aggregate refuses it when it is
     * built, so the read's refusal for it ({@code pack_unreadable}) guards a store fault only.
     */
    @Test
    void anAssetWhosePayloadFileIsMissingCannotBeHeld() {
        assertThatThrownBy(() -> Pack.builder()
                        .id(QA)
                        .descriptor(descriptor())
                        .artifact(asset("logo", "image/png", "logo.png"))
                        .build())
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("names the asset file 'logo.png', which the pack does not hold");
    }
}
