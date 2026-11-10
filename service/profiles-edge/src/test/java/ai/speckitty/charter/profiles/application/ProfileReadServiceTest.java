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

        ProfileRead read = new ProfileReadService(source).read(PACK, "P", 1);

        assertThat(source.asked).containsExactly("built-in/P/1");
        assertThat(read.profile().localId()).isEqualTo("P");
        assertThat(read.revision()).isEqualTo("revision");
        assertThat(read.sourceFingerprint()).isEqualTo("fingerprint");
        assertThat(read.governance()).hasSize(1);
    }

    @Test
    void theDepthIsZeroOrOne() {
        RecordingSource source = new RecordingSource();
        ProfileReadService service = new ProfileReadService(source);

        service.read(PACK, "P", 0);
        service.read(PACK, "P", ProfileReadService.MAX_DEPTH);

        assertThat(source.asked).containsExactly("built-in/P/0", "built-in/P/1");
        assertThat(ProfileReadService.MAX_DEPTH).isEqualTo(1);
    }

    @Test
    void aDepthOutsideTheBoundsIsRefusedBeforeAnythingIsRead() {
        RecordingSource source = new RecordingSource();
        ProfileReadService service = new ProfileReadService(source);

        assertThatThrownBy(() -> service.read(PACK, "P", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.read(PACK, "P", 2)).isInstanceOf(IllegalArgumentException.class);
        assertThat(source.reads).hasValue(0);
    }

    @Test
    void aRefusalOfTheSourceIsTheServicesRefusalUnchanged() {
        ProfileSource refusing = (pack, profileId, depth) -> {
            throw new CharterRefusal(RefusalCode.ARTIFACT_NOT_FOUND, "no such profile");
        };

        assertThatThrownBy(() -> new ProfileReadService(refusing).read(PACK, "P", 0))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
                    assertThat(refusal.getMessage()).isEqualTo("no such profile");
                });
    }

    @Test
    void aSourceThatAnswersAnotherProfileIsAFaultNotAnAnswer() {
        ProfileSource wrong = (pack, profileId, depth) ->
                new ProfileRead(leanProfile(pack.value(), "other"), "r", "f", List.of(), List.of());

        assertThatThrownBy(() -> new ProfileReadService(wrong).read(PACK, "P", 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("asked for the profile P and answered other");
    }

    @Test
    void theArgumentsAreRequired() {
        ProfileReadService service = new ProfileReadService(new RecordingSource());

        assertThatThrownBy(() -> new ProfileReadService(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.read(null, "P", 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.read(PACK, null, 0)).isInstanceOf(NullPointerException.class);
    }
}
