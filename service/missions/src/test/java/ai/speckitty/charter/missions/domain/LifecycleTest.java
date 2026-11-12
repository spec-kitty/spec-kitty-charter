package ai.speckitty.charter.missions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The lifecycle's invariants (ADR-0050 decision 17) and its reading of a transition's source. */
@DisplayName("A lifecycle is a well-formed state machine (ADR-0050 decision 17)")
class LifecycleTest {

    private static final PackIdentifier PACK = new PackIdentifier("p");

    private static LifecycleState state(String id) {
        return new LifecycleState(id, id, Optional.empty(), Optional.empty());
    }

    private static LifecycleState terminal(String id) {
        return new LifecycleState(id, id, Optional.empty(), Optional.of(true));
    }

    private static LifecycleTransition edge(TransitionSource from, String to, GuardEntry... guards) {
        return new LifecycleTransition(from, to, Optional.empty(), List.of(guards));
    }

    private static LifecycleTransition edge(String from, String to) {
        return edge(new TransitionSource.One(from), to);
    }

    private static Lifecycle lifecycle(String initial, List<LifecycleState> states, LifecycleTransition... edges) {
        return new Lifecycle(
                new LifecycleIdentifier("wp"),
                PACK,
                "1.0",
                "Work package",
                Optional.empty(),
                initial,
                states,
                List.of(edges));
    }

    private static final List<LifecycleState> LANES =
            List.of(state("planned"), state("doing"), state("blocked"), terminal("done"), terminal("canceled"));

    @Test
    @DisplayName("* reaches every non-terminal state but the target, so * -> blocked adds no blocked loop")
    void everyNonTerminalLeavesOutTheTarget() {
        Lifecycle wp = lifecycle(
                "planned",
                LANES,
                edge("planned", "doing"),
                edge("doing", "done"),
                new LifecycleTransition(
                        new TransitionSource.EveryNonTerminal(), "blocked", Optional.of("block"), List.of()),
                new LifecycleTransition(
                        new TransitionSource.EveryNonTerminal(), "canceled", Optional.of("cancel"), List.of()));

        assertThat(wp.kind()).isEqualTo(ArtifactKind.LIFECYCLE);
        assertThat(wp.sources(wp.transitions().get(2))).containsExactly("planned", "doing");
        assertThat(wp.sources(wp.transitions().get(3))).containsExactly("planned", "doing", "blocked");
        assertThat(wp.edgesFrom("blocked")).extracting(LifecycleTransition::to).containsExactly("canceled");
        assertThat(wp.edgesFrom("done")).isEmpty();
        assertThat(wp.terminalStates()).containsExactly("done", "canceled");
        assertThat(wp.derivedRelationships()).isEmpty();
    }

    @Test
    @DisplayName("each malformed machine is refused with the rule it breaks")
    void malformedMachinesAreRefused() {
        assertMalformedStatesAreRefused();
        assertMalformedTransitionsAreRefused();
        assertMalformedGraphsAreRefused();
    }

    /** The states, the initial state and the title. */
    private static void assertMalformedStatesAreRefused() {
        assertRefused(
                "A lifecycle declares each state once.",
                () -> lifecycle(
                        "planned",
                        List.of(state("planned"), state("planned"), terminal("done")),
                        edge("planned", "done")));
        assertRefused(
                "A lifecycle's initial state is one of its states, and not a terminal one.",
                () -> lifecycle("nowhere", LANES, edge("planned", "done")));
        assertRefused(
                "A lifecycle's initial state is one of its states, and not a terminal one.",
                () -> lifecycle("done", LANES, edge("planned", "done")));
        assertRefused("A lifecycle declares at least one state.", () -> lifecycle("planned", List.of()));
        assertRefused(
                "A lifecycle needs a title that is not blank.",
                () -> new Lifecycle(
                        new LifecycleIdentifier("wp"),
                        PACK,
                        "1.0",
                        " ",
                        Optional.empty(),
                        "planned",
                        LANES,
                        List.of(edge("planned", "done"))));
    }

    /** The transitions, one by one. */
    private static void assertMalformedTransitionsAreRefused() {
        assertRefused("A lifecycle declares at least one transition.", () -> lifecycle("planned", LANES));
        assertRefused(
                "A lifecycle transition names only states the lifecycle declares.",
                () -> lifecycle("planned", LANES, edge(new TransitionSource.Many(List.of("planned", "x")), "done")));
        assertRefused(
                "A lifecycle transition's from list names each state once.",
                () -> lifecycle(
                        "planned", LANES, edge(new TransitionSource.Many(List.of("planned", "planned")), "done")));
        assertRefused(
                "No lifecycle transition leaves a terminal state.",
                () -> lifecycle("planned", LANES, edge("planned", "done"), edge("done", "planned")));
        assertRefused(
                "A lifecycle declares each transition once",
                () -> lifecycle(
                        "planned",
                        LANES,
                        edge("planned", "canceled"),
                        edge(new TransitionSource.EveryNonTerminal(), "canceled")));
        assertRefused(
                "No lifecycle transition leaves a terminal state.",
                () -> lifecycle(
                        "planned", LANES, edge(new TransitionSource.Many(List.of("planned", "done")), "doing")));
        GuardEntry ready = new GuardEntry(GuardReference.parse("ready"), Optional.empty(), Optional.empty());
        assertRefused(
                "A lifecycle transition names each guard once.",
                () -> lifecycle("planned", LANES, edge(new TransitionSource.One("planned"), "done", ready, ready)));
    }

    /** The machine as a whole: what it reaches, where it ends, and whether it is deterministic. */
    private static void assertMalformedGraphsAreRefused() {
        assertRefused(
                "A lifecycle transition leaves at least one state",
                () -> lifecycle(
                        "planned",
                        List.of(state("planned"), terminal("done")),
                        edge("planned", "done"),
                        edge(new TransitionSource.EveryNonTerminal(), "planned")));
        assertRefused(
                "A lifecycle declares at least one terminal state.",
                () -> lifecycle(
                        "planned",
                        List.of(state("planned"), state("doing")),
                        edge("planned", "doing"),
                        edge("doing", "planned")));
        assertRefused(
                "state 'island' is not reachable from the initial state",
                () -> lifecycle(
                        "planned",
                        List.of(state("planned"), state("island"), terminal("done")),
                        edge("planned", "done")));
        List<LifecycleState> fork = List.of(state("planned"), state("doing"), terminal("done"));
        assertRefused(
                "'planned' on 'next' leads to both 'doing' and 'done'",
                () -> lifecycle("planned", fork, edge("planned", "doing"), edge("planned", "done")));
        assertRefused(
                "'planned' on 'next' leads to both 'doing' and 'done'",
                () -> lifecycle(
                        "planned",
                        fork,
                        edge(new TransitionSource.EveryNonTerminal(), "doing"),
                        edge("planned", "done")));
    }

    @Test
    @DisplayName("a transition that differs only in its event is another transition")
    void anotherEventIsAnotherTransition() {
        Lifecycle wp = lifecycle(
                "planned",
                List.of(state("planned"), state("doing"), terminal("done")),
                edge("planned", "doing"),
                edge("doing", "done"),
                new LifecycleTransition(new TransitionSource.One("planned"), "doing", Optional.of("force"), List.of()));

        assertThat(wp.edgesFrom("planned")).hasSize(2);
        assertRefused(
                "A lifecycle declares each transition once",
                () -> lifecycle(
                        "planned",
                        List.of(state("planned"), state("doing"), terminal("done")),
                        edge("planned", "doing"),
                        edge("doing", "done"),
                        new LifecycleTransition(
                                new TransitionSource.One("planned"), "doing", Optional.of("next"), List.of())));
    }

    @Test
    @DisplayName("a guard reference reads bare in its pack and pack-qualified across packs, and writes back the same")
    void guardReferencesKeepTheirSpelling() {
        assertThat(GuardReference.parse("subtasks-ready"))
                .isEqualTo(new GuardReference(Optional.empty(), "subtasks-ready"))
                .satisfies(ref -> assertThat(ref.crossPack()).isFalse());
        assertThat(GuardReference.parse("qa::guard:traceability-ready"))
                .isEqualTo(new GuardReference(Optional.of("qa"), "traceability-ready"))
                .satisfies(ref -> assertThat(ref.spelling()).isEqualTo("qa::guard:traceability-ready"));
        assertThatThrownBy(() -> GuardReference.parse("guard:subtasks-ready"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new GuardEntry(GuardReference.parse("g"), Optional.empty(), Optional.empty()).blocks())
                .as("blocking defaults to true")
                .isTrue();
    }

    private static void assertRefused(String rule, org.assertj.core.api.ThrowableAssert.ThrowingCallable build) {
        assertThatThrownBy(build).isInstanceOf(ConstraintBreachException.class).hasMessageContaining(rule);
    }
}
