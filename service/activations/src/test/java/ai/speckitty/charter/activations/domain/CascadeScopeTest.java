package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.kernel.domain.ArtifactKind.AGENT_PROFILE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.MISSION_TYPE;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.TACTIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CascadeScopeTest {

    private static CascadeScope.Parsed parse(String... tokens) {
        return CascadeScope.parse(Optional.of(List.of(tokens)));
    }

    @Test
    void anAbsentCascadeIsNoCascade() {
        CascadeScope.Parsed parsed = CascadeScope.parse(Optional.empty());

        assertThat(parsed.violations()).isEmpty();
        assertThat(parsed.scope()).isEqualTo(CascadeScope.none());
        assertThat(parsed.scope().cascades()).isFalse();
        assertThat(parsed.scope().admits(DIRECTIVE)).isFalse();
    }

    @Test
    void allCoversEveryActivatableKindMissionTypesIncluded() {
        // Rule W21 (A7): mission_type is a kind like any other, so all admits it.
        CascadeScope.Parsed parsed = parse("all");

        assertThat(parsed.violations()).isEmpty();
        assertThat(parsed.scope()).isEqualTo(CascadeScope.all());
        assertThat(parsed.scope().cascades()).isTrue();
        assertThat(parsed.scope().kinds())
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(ArtifactKind.values())
                        .filter(ArtifactKind::activatable)
                        .toList());
        assertThat(parsed.scope().admits(AGENT_PROFILE)).isTrue();
        assertThat(parsed.scope().admits(MISSION_TYPE)).isTrue();
    }

    @Test
    void aCascadeMayNameMissionTypes() {
        CascadeScope.Parsed parsed = parse("directive", "mission_type");

        assertThat(parsed.violations()).isEmpty();
        assertThat(parsed.scope().kinds()).containsExactlyInAnyOrder(DIRECTIVE, MISSION_TYPE);
    }

    @Test
    void namedKindsCoverThoseKindsOnly() {
        CascadeScope scope = parse("directive", "tactic").scope();

        assertThat(scope.cascades()).isTrue();
        assertThat(scope.kinds()).containsExactlyInAnyOrder(DIRECTIVE, TACTIC);
        assertThat(scope.admits(DIRECTIVE)).isTrue();
        assertThat(scope.admits(TACTIC)).isTrue();
        assertThat(scope.admits(AGENT_PROFILE)).isFalse();
    }

    @Test
    void allMixedWithKindsIsRefusedAtTheCascadeMember() {
        CascadeScope.Parsed parsed = parse("all", "tactic");

        assertThat(parsed.violations())
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/cascade", "all_mixed_with_kinds"));
        assertThat(parsed.scope().cascades()).isFalse();
    }

    @Test
    void anUnknownKindIsNamedAtItsIndex() {
        CascadeScope.Parsed parsed = parse("directive", "nope", "template");

        assertThat(parsed.violations())
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(tuple("/cascade/1", "unknown_kind"), tuple("/cascade/2", "unknown_kind"));
        assertThat(parsed.scope().cascades()).isFalse();
    }

    @Test
    void allRepeatedStaysAll() {
        assertThat(parse("all", "all").scope()).isEqualTo(CascadeScope.all());
    }
}
