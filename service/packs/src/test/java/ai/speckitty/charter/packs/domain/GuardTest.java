package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The guard's implementation spellings, the built-in list and the executable block (ADR-0054 decisions 1, 5, 6). */
@DisplayName("A guard names its implementation; an executable runs without a shell (ADR-0054)")
class GuardTest {

    @Test
    @DisplayName("implemented_by reads a built-in, a bare asset or a pack-qualified asset, and writes it back the same")
    void implementationSpellingsRoundTrip() {
        for (String spelling : List.of("builtin:review-approved", "asset:qa-traceability-lint", "qa::asset:lint")) {
            assertThat(GuardImplementation.parse(spelling).spelling()).isEqualTo(spelling);
        }
        assertThat(GuardImplementation.parse("qa::asset:lint"))
                .isEqualTo(new GuardImplementation.AssetBacked(Optional.of("qa"), "lint"));
        assertThatThrownBy(() -> GuardImplementation.parse("builtin:subtasks-complete"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a built-in guard");
        assertThatThrownBy(() -> GuardImplementation.parse("python lint.py"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the built-in list is the six ids the operator ruled, each a kebab id")
    void theBuiltinListIsClosed() {
        assertThat(BuiltinGuard.values())
                .extracting(BuiltinGuard::id)
                .containsExactly(
                        "transition-context-complete",
                        "dependency-ready",
                        "implementation-ready",
                        "review-approved",
                        "review-ref",
                        "spec-kitty-pre-review");
        assertThat(BuiltinGuard.fromId("review-ref")).contains(BuiltinGuard.REVIEW_REF);
    }

    @Test
    @DisplayName("a guard defaults to block when it could not evaluate, and to no network")
    void defaultsFailClosed() {
        Guard guard = new Guard(
                new GuardIdentifier("ready"),
                new PackIdentifier("p"),
                "1.0",
                "Ready",
                Optional.empty(),
                Guard.INTERFACE,
                GuardImplementation.parse("builtin:review-ref"),
                Optional.empty(),
                Optional.of(new GuardLimits(Optional.empty(), Optional.empty())),
                Optional.empty());

        assertThat(guard.indeterminate()).isEqualTo(OnIndeterminate.BLOCK);
        assertThat(guard.limits().orElseThrow().allowsNetwork()).isFalse();
        assertThatThrownBy(() -> new GuardLimits(Optional.of(0), Optional.empty()))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName(
            "an executable's argv is its entry then its args; the entry is one element, never repeated or a path out")
    void anExecutableIsOneArgvElement() {
        Executable lint = new Executable(ExecutableRuntime.PYTHON, Optional.of("3.11"), "lint.py", List.of("--guard"));

        assertThat(lint.argv()).containsExactly("lint.py", "--guard");
        assertThatThrownBy(
                        () -> new Executable(ExecutableRuntime.PYTHON, Optional.empty(), "lint.py", List.of("lint.py")))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("exactly one argv element");
        for (String entry : List.of("/usr/bin/lint.py", "../lint.py", "a/../lint.py", ".hidden.py", "lint py")) {
            assertThatThrownBy(() -> new Executable(ExecutableRuntime.PYTHON, Optional.empty(), entry, List.of()))
                    .as(entry)
                    .isInstanceOf(ConstraintBreachException.class);
        }
        assertThat(ExecutableRuntime.fromToken("sh")).isEmpty();
        assertThat(ExecutableRuntime.fromToken("python")).contains(ExecutableRuntime.PYTHON);
    }
}
