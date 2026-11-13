package ai.speckitty.charter.artifacts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Outcome;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The asset reads under an activation compose two reads (research R15): the activation names the winner and the source
 * fingerprint, and the winner's pack is read against that fingerprint. The pack read is the winner's, never another
 * holder's; a {@code 304} reads no pack; a second read at another fingerprint is {@code stale_input}, and so is a winner
 * its pack no longer holds or a pack that is configured no longer.
 */
class EffectiveAssetReadsTest {

    private static final String FINGERPRINT = "sha256:" + "f".repeat(64);
    private static final PackIdentifier CORE = new PackIdentifier("core");
    private static final PackIdentifier PROJ = new PackIdentifier("proj");
    private static final Scope SCOPE = new Scope("a-1", "sha256:rev", "sha256:closure", FINGERPRINT);
    private static final Held RUN = new Held("run", PROJ, PackLevel.PROJECT, "open");
    private static final Held LOGO = new Held("logo", CORE, PackLevel.BUILT_IN, "open");
    private static final String ETAG = "sha256:" + "e".repeat(64);
    private static final String STALE = "stale_input";

    private final List<String> asked = new ArrayList<>();

    private static AssetView view(String id, PackIdentifier pack) {
        return new AssetView(
                id, pack, "text/x-python", true, id + ".py", Optional.of(id + " of " + pack.value()), Optional.empty());
    }

    /** An activation that names logo (core) and run (proj); run's copy in proj wins over core's. */
    private static ActivationArtifacts activation(boolean notModified) {
        return new ActivationArtifacts() {
            @Override
            public Outcome<Winners> winners(String activationId, ArtifactKind kind, @Nullable String ifNoneMatch) {
                return notModified
                        ? new Outcome.NotModified<>(ETAG)
                        : new Outcome.Found<>(new Winners(SCOPE, List.of(LOGO, RUN)), ETAG);
            }

            @Override
            public Outcome<Winner> asset(String activationId, String assetId, @Nullable String ifNoneMatch) {
                return notModified
                        ? new Outcome.NotModified<>(ETAG)
                        : new Outcome.Found<>(new Winner(SCOPE, RUN), ETAG);
            }

            @Override
            public Outcome<Winner> assetPayload(String activationId, String assetId, @Nullable String ifNoneMatch) {
                return asset(activationId, assetId, ifNoneMatch);
            }

            @Override
            public Outcome<SkillWinner> skill(String activationId, String skillId, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException("no skill is read here");
            }
        };
    }

    /** The packs: each holds its copy of run and logo; the read verifies {@code verified}. */
    private AssetReads packs(String verified, @Nullable CharterRefusal refusal) {
        return new AssetReads() {
            @Override
            public Listed list(PackIdentifier pack) {
                throw new UnsupportedOperationException("the twin reads against the activation's fingerprint");
            }

            @Override
            public Read asset(PackIdentifier pack, String assetId) {
                throw new UnsupportedOperationException("the twin reads against the activation's fingerprint");
            }

            @Override
            public PayloadRead payload(PackIdentifier pack, String assetId) {
                throw new UnsupportedOperationException("the twin reads against the activation's fingerprint");
            }

            @Override
            public Listed list(PackIdentifier pack, String expectedFingerprint) {
                asked.add("list " + pack.value() + " at " + expectedFingerprint);
                check(expectedFingerprint);
                return new Listed(
                        pack.equals(CORE) ? List.of(view("logo", pack), view("run", pack)) : List.of(view("run", pack)),
                        "sha256:revision",
                        verified);
            }

            @Override
            public Read asset(PackIdentifier pack, String assetId, String expectedFingerprint) {
                asked.add("asset " + pack.value() + "/" + assetId + " at " + expectedFingerprint);
                check(expectedFingerprint);
                return new Read(view(assetId, pack), "sha256:revision", verified);
            }

            @Override
            public PayloadRead payload(PackIdentifier pack, String assetId, String expectedFingerprint) {
                asked.add("payload " + pack.value() + "/" + assetId + " at " + expectedFingerprint);
                check(expectedFingerprint);
                return new PayloadRead(
                        view(assetId, pack),
                        ("print('" + pack.value() + "')").getBytes(StandardCharsets.UTF_8),
                        "sha256:revision",
                        verified);
            }

            private void check(String expected) {
                if (refusal != null) {
                    throw refusal;
                }
                if (!verified.equals(expected)) {
                    throw new CharterRefusal(RefusalCode.STALE_INPUT, "the packs changed");
                }
            }
        };
    }

    private static <T> T found(Outcome<T> outcome) {
        assertThat(outcome).isInstanceOf(Outcome.Found.class);
        return ((Outcome.Found<T>) outcome).value();
    }

    @Test
    void theAssetIsTheWinnersDescriptorReadFromTheWinnersPackAtTheActivationsFingerprint() {
        EffectiveAssetReads reads = new EffectiveAssetReads(activation(false), packs(FINGERPRINT, null));

        EffectiveAssetReads.Read read = found(reads.asset("a-1", "run", null));

        assertThat(read.winner()).isEqualTo(RUN);
        assertThat(read.asset().packId()).as("the project copy, which won").isEqualTo(PROJ);
        assertThat(read.scope()).isEqualTo(SCOPE);
        assertThat(asked).containsExactly("asset proj/run at " + FINGERPRINT);
    }

    @Test
    void thePayloadIsTheWinningCopysBytesReadAtTheActivationsFingerprint() {
        EffectiveAssetReads reads = new EffectiveAssetReads(activation(false), packs(FINGERPRINT, null));

        Outcome<AssetReads.PayloadRead> outcome = reads.payload("a-1", "run", null);

        assertThat(new String(found(outcome).bytes(), StandardCharsets.UTF_8)).isEqualTo("print('proj')");
        assertThat(((Outcome.Found<AssetReads.PayloadRead>) outcome).etag())
                .as("the tag is the activation's, not the pack revision")
                .isEqualTo(ETAG);
        assertThat(asked).containsExactly("payload proj/run at " + FINGERPRINT);
    }

    @Test
    void aPayloadReadAtAnotherFingerprintIsStaleInput() {
        EffectiveAssetReads reads = new EffectiveAssetReads(activation(false), packs("sha256:" + "0".repeat(64), null));

        assertThatThrownBy(() -> reads.payload("a-1", "run", null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code().token()).isEqualTo(STALE));
        assertThatThrownBy(() -> reads.asset("a-1", "run", null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code().token()).isEqualTo(STALE));
        assertThatThrownBy(() -> reads.list("a-1", null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code().token()).isEqualTo(STALE));
    }

    @Test
    void aWinnersPackThatIsGoneOrNoLongerHoldsTheAssetIsStaleInputAndNotANotFound() {
        for (RefusalCode code : new RefusalCode[] {RefusalCode.PACK_NOT_FOUND, RefusalCode.ARTIFACT_NOT_FOUND}) {
            EffectiveAssetReads reads = new EffectiveAssetReads(
                    activation(false), packs(FINGERPRINT, new CharterRefusal(code, "gone since")));

            assertThatThrownBy(() -> reads.payload("a-1", "run", null))
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            refusal -> assertThat(refusal.code().token()).isEqualTo(STALE));
            assertThatThrownBy(() -> reads.asset("a-1", "run", null))
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            refusal -> assertThat(refusal.code().token()).isEqualTo(STALE));
        }
    }

    @Test
    void anotherRefusalOfTheSecondReadIsTheAnswerUnchanged() {
        EffectiveAssetReads reads = new EffectiveAssetReads(
                activation(false), packs(FINGERPRINT, new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack.yaml")));

        assertThatThrownBy(() -> reads.payload("a-1", "run", null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @Test
    void theListIsEachWinnerWithItsWinningCopyEachPackReadOnce() {
        EffectiveAssetReads reads = new EffectiveAssetReads(activation(false), packs(FINGERPRINT, null));

        EffectiveAssetReads.Listed listed = found(reads.list("a-1", null));

        assertThat(listed.items())
                .extracting(
                        item -> item.winner().id() + "@" + item.asset().packId().value())
                .as("run is proj's copy though core holds one too")
                .containsExactly("logo@core", "run@proj");
        assertThat(asked).containsExactlyInAnyOrder("list core at " + FINGERPRINT, "list proj at " + FINGERPRINT);
    }

    @Test
    void aWinnerItsPacksListDoesNotHoldIsStaleInput() {
        ActivationArtifacts activation = new ActivationArtifacts() {
            @Override
            public Outcome<Winners> winners(String activationId, ArtifactKind kind, @Nullable String ifNoneMatch) {
                return new Outcome.Found<>(
                        new Winners(SCOPE, List.of(new Held("logo", PROJ, PackLevel.PROJECT, "open"))), ETAG);
            }

            @Override
            public Outcome<Winner> asset(String activationId, String assetId, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Outcome<Winner> assetPayload(String activationId, String assetId, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Outcome<SkillWinner> skill(String activationId, String skillId, @Nullable String ifNoneMatch) {
                throw new UnsupportedOperationException();
            }
        };

        assertThatThrownBy(() -> new EffectiveAssetReads(activation, packs(FINGERPRINT, null)).list("a-1", null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code().token()).isEqualTo(STALE));
    }

    @Test
    void aNotModifiedReadsNoPack() {
        EffectiveAssetReads reads = new EffectiveAssetReads(activation(true), packs(FINGERPRINT, null));

        assertThat(reads.list("a-1", "*")).isEqualTo(new Outcome.NotModified<EffectiveAssetReads.Listed>(ETAG));
        assertThat(reads.asset("a-1", "run", "*")).isEqualTo(new Outcome.NotModified<EffectiveAssetReads.Read>(ETAG));
        assertThat(reads.payload("a-1", "run", "*")).isEqualTo(new Outcome.NotModified<AssetReads.PayloadRead>(ETAG));
        assertThat(asked).isEmpty();
    }
}
