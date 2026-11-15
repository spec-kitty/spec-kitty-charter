package ai.speckitty.charter.profiles.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.Specialization;
import ai.speckitty.charter.profiles.domain.selection.Candidates;
import ai.speckitty.charter.profiles.domain.selection.Fit;
import ai.speckitty.charter.profiles.domain.selection.Pick;
import ai.speckitty.charter.profiles.domain.selection.SelectionCandidate;
import ai.speckitty.charter.profiles.domain.selection.SelectionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@link ProfileSelectionService}: the domain's selection over what the port reads, with the live order of the read.
 * The port's refusals come first; a role nothing fills is {@code no_profile_matched}, also against a matching
 * {@code If-None-Match}; only an answer is compared with the tag, so a matching one is a {@code 304}.
 */
class ProfileSelectionServiceTest {

    private static final String TAG = "sha256:" + "c".repeat(64);

    private final List<String> asked = new ArrayList<>();

    private static SelectionCandidate candidate(String id, String role, String pack, int position) {
        AgentProfile profile = AgentProfile.builder()
                .id(new AgentProfileIdentifier(id))
                .packId(new PackIdentifier(pack))
                .name("Name of " + id)
                .purpose("Purpose of " + id)
                .role(role)
                .specialization(new Specialization("Focus", Optional.empty(), Optional.empty(), Optional.empty()))
                .build();
        return new SelectionCandidate(profile, pack, position, 0);
    }

    private SelectionCandidates port(Candidates candidates) {
        return port(candidates, Optional.of("reviewer"));
    }

    /** A port whose every step names the role given, or none. */
    private SelectionCandidates port(Candidates candidates, Optional<String> stepRole) {
        return new SelectionCandidates() {
            @Override
            public Read read(String activationId, String role, SelectionContext context) {
                asked.add(activationId + "/" + role + "/" + context.language().orElse("-"));
                return new Read(activationId, candidates, "sha256:source", TAG);
            }

            @Override
            public StepRead readForStep(
                    String activationId, String missionType, String action, SelectionContext context) {
                asked.add(activationId + "/" + missionType + "/" + action + "/"
                        + context.language().orElse("-"));
                return new StepRead(stepRole, new Read(activationId, candidates, "sha256:source", TAG));
            }
        };
    }

    /** A port that refuses every read as the activations slice refuses an unknown activation. */
    private static SelectionCandidates refusing() {
        return new SelectionCandidates() {
            @Override
            public Read read(String activationId, String role, SelectionContext context) {
                throw new CharterRefusal(RefusalCode.ACTIVATION_NOT_FOUND, "no such activation");
            }

            @Override
            public StepRead readForStep(
                    String activationId, String missionType, String action, SelectionContext context) {
                throw new CharterRefusal(RefusalCode.ACTION_NOT_FOUND, "no such step");
            }
        };
    }

    private static Candidates tie() {
        return Candidates.unpinned(
                List.of(
                        candidate("reviewer-rita", "reviewer", "org-base", 1),
                        candidate("reviewer-renata", "reviewer", "built-in", 0)),
                Optional.of(candidate("generic-agent", "generic", "built-in", 0)));
    }

    @Test
    void theSelectionIsTheDomainsAnswerWithThePortsFingerprintAndTag() {
        ProfileSelectionService service = new ProfileSelectionService(port(tie()));

        ProfileSelectionService.Outcome outcome = service.select("a-1", "reviewer", SelectionContext.NONE, null);

        assertThat(outcome).isInstanceOfSatisfying(ProfileSelectionService.Outcome.Found.class, found -> {
            assertThat(found.etag()).isEqualTo(TAG);
            assertThat(found.sourceFingerprint()).isEqualTo("sha256:source");
            assertThat(found.selection().activationId()).isEqualTo("a-1");
            assertThat(found.selection().subject()).isEqualTo(new ProfileSelectionService.Subject.Role("reviewer"));
            assertThat(found.selection().subject().role()).contains("reviewer");
            assertThat(found.selection().answer().tie()).isTrue();
            assertThat(found.selection().answer().picks())
                    .containsExactly(
                            new Pick("reviewer-renata", "built-in", Fit.PRIMARY),
                            new Pick("reviewer-rita", "org-base", Fit.PRIMARY));
        });
        assertThat(asked).containsExactly("a-1/reviewer/-");
    }

    @Test
    void aRoleNothingFillsIsTheFallbackAndOperatorIsNoProfileMatchedEvenAgainstAMatchingTag() {
        ProfileSelectionService service = new ProfileSelectionService(port(tie()));

        assertThat(service.select("a-1", "facilitator", SelectionContext.NONE, null))
                .isInstanceOfSatisfying(
                        ProfileSelectionService.Outcome.Found.class,
                        found -> assertThat(found.selection().answer().picks())
                                .containsExactly(new Pick("generic-agent", "built-in", Fit.FALLBACK)));
        assertThatThrownBy(() -> service.select("a-1", "operator", SelectionContext.NONE, "*"))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.NO_PROFILE_MATCHED);
                    assertThat(refused.getMessage()).contains("a-1").contains("never falls back");
                });
        ProfileSelectionService unfallen =
                new ProfileSelectionService(port(Candidates.unpinned(List.of(), Optional.empty())));
        assertThatThrownBy(() -> unfallen.select("a-1", "facilitator", SelectionContext.NONE, null))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.NO_PROFILE_MATCHED);
                    assertThat(refused.getMessage()).contains("no fallback profile is in force");
                });
    }

    @Test
    void aMatchingIfNoneMatchIs304AndAnotherTagIsTheAnswer() {
        ProfileSelectionService service = new ProfileSelectionService(port(tie()));

        assertThat(service.select("a-1", "reviewer", SelectionContext.NONE, EntityTags.quote(TAG)))
                .isEqualTo(new ProfileSelectionService.Outcome.NotModified(TAG));
        assertThat(service.select("a-1", "reviewer", SelectionContext.NONE, "*"))
                .isEqualTo(new ProfileSelectionService.Outcome.NotModified(TAG));
        assertThat(service.select("a-1", "reviewer", SelectionContext.NONE, "\"sha256:other\""))
                .isInstanceOf(ProfileSelectionService.Outcome.Found.class);
    }

    @Test
    void aStepAnswersTheSelectionOfTheRoleItNamesWithTheStepAsItsSubject() {
        ProfileSelectionService service = new ProfileSelectionService(port(tie(), Optional.of("reviewer")));

        ProfileSelectionService.Outcome outcome = service.selectForStep(
                "a-1", "software-dev", "review", new SelectionContext(Optional.of("java"), Optional.empty()), null);

        assertThat(outcome).isInstanceOfSatisfying(ProfileSelectionService.Outcome.Found.class, found -> {
            assertThat(found.etag()).isEqualTo(TAG);
            assertThat(found.selection().subject())
                    .isEqualTo(new ProfileSelectionService.Subject.Step(
                            "software-dev", "review", Optional.of("reviewer")));
            assertThat(found.selection().answer().picks())
                    .as("what the role read answers for reviewer")
                    .isEqualTo(((ProfileSelectionService.Outcome.Found)
                                    service.select("a-1", "reviewer", SelectionContext.NONE, null))
                            .selection()
                            .answer()
                            .picks());
        });
        assertThat(asked).startsWith("a-1/software-dev/review/java");
        assertThat(service.selectForStep("a-1", "software-dev", "review", SelectionContext.NONE, "*"))
                .isEqualTo(new ProfileSelectionService.Outcome.NotModified(TAG));
    }

    @Test
    void aStepThatNamesNoRoleAnswersTheFallbackAndWithNoneIsNoProfileMatchedEvenAgainstAMatchingTag() {
        ProfileSelectionService service = new ProfileSelectionService(port(tie(), Optional.empty()));

        assertThat(service.selectForStep("a-1", "software-dev", "specify", SelectionContext.NONE, null))
                .isInstanceOfSatisfying(ProfileSelectionService.Outcome.Found.class, found -> {
                    assertThat(found.selection().answer().picks())
                            .containsExactly(new Pick("generic-agent", "built-in", Fit.FALLBACK));
                    assertThat(found.selection().subject().role()).isEmpty();
                });
        ProfileSelectionService unfallen = new ProfileSelectionService(
                port(Candidates.unpinned(tie().candidates(), Optional.empty()), Optional.empty()));
        assertThatThrownBy(() -> unfallen.selectForStep("a-1", "software-dev", "specify", SelectionContext.NONE, "*"))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.NO_PROFILE_MATCHED);
                    assertThat(refused.getMessage())
                            .contains("software-dev/specify")
                            .contains("names no role")
                            .contains("no fallback profile is in force");
                });
        ProfileSelectionService operator = new ProfileSelectionService(port(tie(), Optional.of("operator")));
        assertThatThrownBy(() -> operator.selectForStep("a-1", "software-dev", "sign-off", SelectionContext.NONE, null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.getMessage())
                                .contains("the role operator of the step software-dev/sign-off")
                                .contains("never falls back"));
    }

    @Test
    void thePortsRefusalIsTheAnswer() {
        ProfileSelectionService service = new ProfileSelectionService(refusing());

        assertThatThrownBy(() -> service.select("nope", "reviewer", SelectionContext.NONE, "*"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.ACTIVATION_NOT_FOUND));
        assertThatThrownBy(() -> service.selectForStep("a-1", "software-dev", "deploy", SelectionContext.NONE, "*"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.ACTION_NOT_FOUND));
        assertThatThrownBy(() -> new ProfileSelectionService(null)).isInstanceOf(NullPointerException.class);
    }
}
