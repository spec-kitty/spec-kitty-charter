package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The optional members a refusal body carries beside its code and detail (ADR-0020 D10): the {@code violations} of a
 * {@code 422 activation_invalid} and the {@code requiredBy} packs of a {@code 409 artifact_required} or
 * {@code artifact_locked}.
 */
class CharterRefusalTest {

    private static final RefusalViolation UNKNOWN =
            new RefusalViolation("/deactivated/tactic/0", "unknown_artifact", "No tactic t-1 is in the packs.");
    private static final RequiringPack REQUIRING = new RequiringPack("team-pack", List.of("org-pack"));

    @Test
    void aRefusalWithViolationsExposesThemAsAnUnmodifiableList() {
        List<RefusalViolation> given = new ArrayList<>(List.of(UNKNOWN));
        CharterRefusal refusal = CharterRefusal.withViolations(RefusalCode.ACTIVATION_INVALID, "detail", given);
        given.clear();

        assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID);
        assertThat(refusal.getMessage()).isEqualTo("detail");
        assertThat(refusal.violations()).containsExactly(UNKNOWN);
        assertThat(refusal.requiredBy()).isEmpty();
        assertThat(refusal.packs()).isEmpty();
        assertThatThrownBy(() -> refusal.violations().add(UNKNOWN)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aRefusalCanNameBothPacksAndViolations() {
        RefusalPack referrer = new RefusalPack("01M6R0WP14AAAAAAAAAAAAAAAA", "other", "other-pack");
        List<RefusalPack> packs = new ArrayList<>(List.of(referrer));
        List<RefusalViolation> violations = new ArrayList<>(List.of(UNKNOWN));

        CharterRefusal refusal =
                CharterRefusal.withPacksAndViolations(RefusalCode.ARTIFACT_REFERENCED, "detail", packs, violations);
        packs.clear();
        violations.clear();

        assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REFERENCED);
        assertThat(refusal.getMessage()).isEqualTo("detail");
        assertThat(refusal.packs()).as("the referring packs").containsExactly(referrer);
        assertThat(refusal.violations()).as("the referring artifacts").containsExactly(UNKNOWN);
        assertThat(refusal.requiredBy()).isEmpty();
        assertThatThrownBy(() -> refusal.packs().add(referrer)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aRefusalWithRequiringPacksExposesThemAsAnUnmodifiableList() {
        CharterRefusal refusal =
                CharterRefusal.withRequiredBy(RefusalCode.ARTIFACT_REQUIRED, "detail", List.of(REQUIRING));

        assertThat(refusal.requiredBy()).containsExactly(REQUIRING);
        assertThat(refusal.violations()).isEmpty();
        assertThatThrownBy(() -> refusal.requiredBy().add(REQUIRING)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void theExistingConstructorsGiveEmptyLists() {
        RefusalPack pack = new RefusalPack("01J0000000000000000000000A", "pack-a", "a");
        List<CharterRefusal> refusals = List.of(
                new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "d"),
                new CharterRefusal(RefusalCode.PACK_NAME_CONFLICT, "d", List.of(pack)),
                new CharterRefusal(RefusalCode.PACK_UNREADABLE, "d", new IllegalStateException("cause")));

        assertThat(refusals).allSatisfy(refusal -> {
            assertThat(refusal.violations()).isEmpty();
            assertThat(refusal.requiredBy()).isEmpty();
        });
        assertThat(refusals.get(1).packs()).containsExactly(pack);
    }

    @Test
    void aViolationCodeIsASnakeCaseToken() {
        assertThat(new RefusalViolation("", "body_too_large", "d").pointer()).isEmpty();
        assertThatThrownBy(() -> new RefusalViolation("", "Unknown-Artifact", "d"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown-Artifact");
        assertThatThrownBy(() -> new RefusalViolation("", "", "d")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRequiringPackCopiesItsChain() {
        List<String> via = new ArrayList<>(List.of("org-pack"));
        RequiringPack pack = new RequiringPack("team-pack", via);
        via.add("other");

        assertThat(pack.via()).containsExactly("org-pack");
        assertThat(new RequiringPack("team-pack", List.of()).via()).isEmpty();
    }
}
