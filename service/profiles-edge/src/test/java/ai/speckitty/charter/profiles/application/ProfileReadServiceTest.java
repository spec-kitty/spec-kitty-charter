package ai.speckitty.charter.profiles.application;

import static ai.speckitty.charter.profiles.application.ProfileFixtures.leanProfile;
import static ai.speckitty.charter.profiles.application.ProfileFixtures.requires;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProfileReadServiceTest {

    private static final PackIdentifier PACK = new PackIdentifier("built-in");
    private static final EffectiveProfiles NO_ACTIVATIONS = ProfileFixtures.NO_ACTIVATIONS;

    /** A fake port that answers the profile it is asked for, and records what it was asked. */
    private static final class RecordingSource implements ProfileSource {
        final List<String> asked = new ArrayList<>();
        final AtomicInteger reads = new AtomicInteger();

        @Override
        public ProfileRead read(PackIdentifier pack, String profileId, int depth) {
            reads.incrementAndGet();
            asked.add(pack.value() + "/" + profileId + "/" + depth);
            return new ProfileRead(
                    leanProfile(pack.value(), profileId),
                    "revision",
                    "fingerprint",
                    List.of(requires("directive:D", pack.value())),
                    List.of());
        }
    }

    @Test
    void theReadIsTheSourcesAnswerForTheProfileAskedFor() {
        RecordingSource source = new RecordingSource();

        ProfileRead read = new ProfileReadService(source, NO_ACTIVATIONS).read(PACK, "P", 1);

        assertThat(source.asked).containsExactly("built-in/P/1");
        assertThat(read.profile().localId()).isEqualTo("P");
        assertThat(read.revision()).isEqualTo("revision");
        assertThat(read.sourceFingerprint()).isEqualTo("fingerprint");
        assertThat(read.governance()).hasSize(1);
    }

    @Test
    void theDepthIsZeroOrOne() {
        RecordingSource source = new RecordingSource();
        ProfileReadService service = new ProfileReadService(source, NO_ACTIVATIONS);

        service.read(PACK, "P", 0);
        service.read(PACK, "P", ProfileReadService.MAX_DEPTH);

        assertThat(source.asked).containsExactly("built-in/P/0", "built-in/P/1");
        assertThat(ProfileReadService.MAX_DEPTH).isEqualTo(1);
    }

    @Test
    void aDepthOutsideTheBoundsIsRefusedBeforeAnythingIsRead() {
        RecordingSource source = new RecordingSource();
        ProfileReadService service = new ProfileReadService(source, NO_ACTIVATIONS);

        assertThatThrownBy(() -> service.read(PACK, "P", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.read(PACK, "P", 2)).isInstanceOf(IllegalArgumentException.class);
        assertThat(source.reads).hasValue(0);
    }

    @Test
    void aRefusalOfTheSourceIsTheServicesRefusalUnchanged() {
        ProfileSource refusing = (pack, profileId, depth) -> {
            throw new CharterRefusal(RefusalCode.ARTIFACT_NOT_FOUND, "no such profile");
        };

        assertThatThrownBy(() -> new ProfileReadService(refusing, NO_ACTIVATIONS).read(PACK, "P", 0))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
                    assertThat(refusal.getMessage()).isEqualTo("no such profile");
                });
    }

    @Test
    void aSourceThatAnswersAnotherProfileIsAFaultNotAnAnswer() {
        ProfileSource wrong = (pack, profileId, depth) ->
                new ProfileRead(leanProfile(pack.value(), "other"), "r", "f", List.of(), List.of());

        assertThatThrownBy(() -> new ProfileReadService(wrong, NO_ACTIVATIONS).read(PACK, "P", 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("asked for the profile P and answered other");
    }

    @Test
    void theArgumentsAreRequired() {
        ProfileReadService service = new ProfileReadService(new RecordingSource(), NO_ACTIVATIONS);

        assertThatThrownBy(() -> new ProfileReadService(null, NO_ACTIVATIONS)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.read(null, "P", 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.read(PACK, null, 0)).isInstanceOf(NullPointerException.class);
    }

    // ---- under an activation

    /** A fake port that answers the profile it is asked for under the activation, and records what it was asked. */
    private static final class RecordingActivations implements EffectiveProfiles {
        final List<String> asked = new ArrayList<>();

        @Override
        public Outcome read(String activationId, String profileId, int depth, String ifNoneMatch) {
            asked.add(activationId + "/" + profileId + "/" + depth + "/" + ifNoneMatch);
            return "match".equals(ifNoneMatch)
                    ? new Outcome.NotModified("tag")
                    : new Outcome.Found(ProfileFixtures.effectiveRead(), "tag");
        }
    }

    private static ProfileReadService under(EffectiveProfiles activations) {
        return new ProfileReadService(new RecordingSource(), activations);
    }

    @Test
    void aReadUnderAnActivationIsThePortsAnswerForTheProfileAskedFor() {
        RecordingActivations activations = new RecordingActivations();

        EffectiveProfiles.Outcome outcome = under(activations).underActivation("a-1", "P", 1, null);

        assertThat(activations.asked).containsExactly("a-1/P/1/null");
        assertThat(outcome).isInstanceOfSatisfying(EffectiveProfiles.Outcome.Found.class, found -> {
            assertThat(found.etag()).isEqualTo("tag");
            assertThat(found.read().profile().localId()).isEqualTo("P");
        });
    }

    @Test
    void aMatchingIfNoneMatchIsThePortsNotModified() {
        EffectiveProfiles.Outcome outcome = under(new RecordingActivations()).underActivation("a-1", "P", 0, "match");

        assertThat(outcome).isEqualTo(new EffectiveProfiles.Outcome.NotModified("tag"));
    }

    @Test
    void theDepthUnderAnActivationIsZeroOrOneAndAnythingElseIsRefusedBeforeAnythingIsRead() {
        RecordingActivations activations = new RecordingActivations();
        ProfileReadService service = under(activations);

        service.underActivation("a-1", "P", 0, null);
        service.underActivation("a-1", "P", ProfileReadService.MAX_DEPTH, null);
        assertThatThrownBy(() -> service.underActivation("a-1", "P", -1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.underActivation("a-1", "P", 2, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(activations.asked).containsExactly("a-1/P/0/null", "a-1/P/1/null");
    }

    @Test
    void aRefusalOfThePortIsTheServicesRefusalUnchanged() {
        EffectiveProfiles refusing = (activationId, profileId, depth, ifNoneMatch) -> {
            throw new CharterRefusal(RefusalCode.ACTIVATION_NOT_FOUND, "no such activation");
        };

        assertThatThrownBy(() -> under(refusing).underActivation("a-1", "P", 0, null))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND);
                    assertThat(refusal.getMessage()).isEqualTo("no such activation");
                });
    }

    @Test
    void aPortThatAnswersAnotherProfileIsAFaultNotAnAnswer() {
        EffectiveProfiles wrong = (activationId, profileId, depth, ifNoneMatch) -> new EffectiveProfiles.Outcome.Found(
                new EffectiveProfileRead(
                        leanProfile("main", "other"),
                        "a-1",
                        "r",
                        "g",
                        "s",
                        ProfileFixtures.active("agent_profile:other", "main"),
                        List.of(),
                        List.of()),
                "tag");

        assertThatThrownBy(() -> under(wrong).underActivation("a-1", "P", 0, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("asked for the profile P and answered other");
    }

    @Test
    void aPortThatAnswersAnotherActivationIsAFaultNotAnAnswer() {
        EffectiveProfiles wrong = (activationId, profileId, depth, ifNoneMatch) ->
                new EffectiveProfiles.Outcome.Found(ProfileFixtures.effectiveRead(), "tag");

        assertThatThrownBy(() -> under(wrong).underActivation("a-2", "P", 0, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("asked for the activation a-2 and answered a-1");
    }

    @Test
    void theArgumentsUnderAnActivationAreRequired() {
        ProfileReadService service = under(new RecordingActivations());

        assertThatThrownBy(() -> service.underActivation(null, "P", 0, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.underActivation("a-1", null, 0, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProfileReadService(new RecordingSource(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
