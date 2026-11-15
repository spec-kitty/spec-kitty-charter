package ai.speckitty.charter.profiles.infra.activations;

import static ai.speckitty.charter.profiles.application.ProfileFixtures.leanProfile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.application.ProfileCandidates;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.profiles.application.SelectionCandidates;
import ai.speckitty.charter.profiles.domain.selection.Candidates;
import ai.speckitty.charter.profiles.domain.selection.SelectionCandidate;
import ai.speckitty.charter.profiles.domain.selection.SelectionContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The selection adapter over the activations slice: each candidate keeps the pack that holds its winner (an org-base or
 * a project profile is never given another pack), its closure position and depth; the pin carries the pinned profile's
 * in-force specializations; the fallback is the held profile; the service's refusals pass unchanged, and a conflict the
 * service answered instead of refusing is a fault.
 */
class ActivationsSelectionCandidatesTest {

    private static final ActivationId ID = new ActivationId("a-1");
    private static final SelectionContext JAVA = new SelectionContext(Optional.of("java"), Optional.empty());

    private final EffectiveReadService service = mock(EffectiveReadService.class);
    private final ActivationsSelectionCandidates adapter = new ActivationsSelectionCandidates(service);

    private static ProfileCandidates.Candidate candidate(String id, String pack, int position, String... ancestors) {
        return new ProfileCandidates.Candidate(
                leanProfile(pack, id), pack, position, ancestors.length, Set.of(ancestors));
    }

    private static EffectiveReadService.SelectionRead read(ProfileCandidates candidates) {
        StoredActivation stored = StoredActivation.of(
                ID,
                ActivationDocument.builder()
                        .packs(List.of(PackRef.of("project")))
                        .build()
                        .document());
        return new EffectiveReadService.SelectionRead(
                stored,
                "sha256:source",
                "sha256:closure",
                new EffectiveTag.SelectionQuery("implementer", Optional.of("java"), Optional.empty()),
                candidates,
                "sha256:" + "a".repeat(64));
    }

    @Test
    void eachCandidateKeepsTheRealPackThatHoldsItWithItsPositionAndDepth() {
        when(service.profileSelection(
                        eq(ID),
                        eq(new EffectiveTag.SelectionQuery("implementer", Optional.of("java"), Optional.empty()))))
                .thenReturn(read(new ProfileCandidates(
                        List.of(
                                candidate("implementer-ivan", "built-in", 0),
                                candidate("reviewer-rita", "org-base", 1),
                                candidate("project-reviewer", "project", 2)),
                        new ProfileCandidates.Pin.None(),
                        new ProfileCandidates.Fallback.Held(candidate("generic-agent", "built-in", 0), "project"))));

        SelectionCandidates.Read read = adapter.read("a-1", "implementer", JAVA);

        assertThat(read.activationId()).isEqualTo("a-1");
        assertThat(read.sourceFingerprint()).isEqualTo("sha256:source");
        assertThat(read.etag()).isEqualTo("sha256:" + "a".repeat(64));
        Candidates candidates = read.candidates();
        assertThat(candidates.candidates())
                .extracting(
                        SelectionCandidate::profileId, SelectionCandidate::pack, SelectionCandidate::closurePosition)
                .containsExactly(
                        tuple("implementer-ivan", "built-in", 0),
                        tuple("reviewer-rita", "org-base", 1),
                        tuple("project-reviewer", "project", 2));
        assertThat(candidates.pinnedProfileId()).isEmpty();
        assertThat(candidates.pinnedSpecializationIds()).isEmpty();
        assertThat(candidates.fallback()).hasValueSatisfying(fallback -> {
            assertThat(fallback.profileId()).isEqualTo("generic-agent");
            assertThat(fallback.pack())
                    .as("the pack that holds the fallback, not the declaring one")
                    .isEqualTo("built-in");
        });
    }

    @Test
    void aPinCarriesThePinnedProfileAndItsInForceSpecializations() {
        when(service.profileSelection(any(), any()))
                .thenReturn(read(new ProfileCandidates(
                        List.of(
                                candidate("implementer-ivan", "built-in", 0),
                                candidate("java-jenny", "built-in", 0, "implementer-ivan"),
                                candidate("project-reviewer", "project", 2)),
                        new ProfileCandidates.Pin.Pinned("implementer-ivan", "project"),
                        new ProfileCandidates.Fallback.None())));

        Candidates candidates = adapter.read("a-1", "implementer", JAVA).candidates();

        assertThat(candidates.pinnedProfileId()).contains("implementer-ivan");
        assertThat(candidates.pinnedSpecializationIds()).containsExactly("java-jenny");
        assertThat(candidates.candidates())
                .filteredOn(candidate -> candidate.profileId().equals("java-jenny"))
                .singleElement()
                .satisfies(jenny -> assertThat(jenny.depth()).isEqualTo(1));
        assertThat(candidates.fallback()).isEmpty();
    }

    @Test
    void aConflictTheServiceAnsweredIsAFault() {
        when(service.profileSelection(any(), any()))
                .thenReturn(read(new ProfileCandidates(
                        List.of(),
                        new ProfileCandidates.Pin.Conflict(List.of("a", "b")),
                        new ProfileCandidates.Fallback.None())))
                .thenReturn(read(new ProfileCandidates(
                        List.of(),
                        new ProfileCandidates.Pin.None(),
                        new ProfileCandidates.Fallback.Conflict(List.of("a", "b")))));

        assertThatThrownBy(() -> adapter.read("a-1", "implementer", JAVA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("role pin");
        assertThatThrownBy(() -> adapter.read("a-1", "implementer", JAVA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fallback");
    }

    @Test
    void theServicesRefusalPassesUnchanged() {
        when(service.profileSelection(any(), any()))
                .thenThrow(new CharterRefusal(RefusalCode.ROLE_PIN_CONFLICT, "pinned differently"));

        assertThatThrownBy(() -> adapter.read("a-1", "reviewer", SelectionContext.NONE))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.ROLE_PIN_CONFLICT));
    }
}
