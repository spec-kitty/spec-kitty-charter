package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.missions.domain.GuardEntry;
import ai.speckitty.charter.missions.domain.GuardReference;
import ai.speckitty.charter.missions.domain.Lifecycle;
import ai.speckitty.charter.missions.domain.LifecycleIdentifier;
import ai.speckitty.charter.missions.domain.LifecycleState;
import ai.speckitty.charter.missions.domain.LifecycleTransition;
import ai.speckitty.charter.missions.domain.TransitionSource;
import java.util.List;
import java.util.Optional;

/**
 * The {@link KindBinding} of the lifecycle (ADR-0050 decision 17): {@link LifecycleDso} to {@link Lifecycle} and back.
 * A lifecycle lives at {@code lifecycles/<id>.lifecycle.yaml}; a file anywhere else is refused by placement. Whether
 * its guard entries resolve, and their {@code config} fits, is checked when the pack is assembled
 * ({@link GuardClosure}), where the pack's guards are known.
 */
enum LifecycleBinding implements KindBinding<LifecycleDso, Lifecycle> {
    /** The binding. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.LIFECYCLE;
    }

    @Override
    public Class<Lifecycle> entityType() {
        return Lifecycle.class;
    }

    @Override
    public Class<LifecycleDso> dsoType() {
        return LifecycleDso.class;
    }

    @Override
    public String idOf(LifecycleDso dso) {
        return dso.id();
    }

    /** A lifecycle's place: {@code lifecycles/<id>.lifecycle.yaml}. */
    @Override
    public String fileName(String elementId) {
        return "lifecycles/" + elementId + ".lifecycle.yaml";
    }

    @Override
    public boolean idIsFileStem() {
        return true;
    }

    @Override
    public boolean placedAtItsFileName() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException also when a guard entry's {@code ref} is not a spelling the domain knows
     */
    @Override
    public Lifecycle toEntity(PackIdentifier pack, LifecycleDso dso) {
        return new Lifecycle(
                new LifecycleIdentifier(dso.id()),
                pack,
                dso.schemaVersion(),
                dso.title(),
                dso.description(),
                dso.initial(),
                dso.states().stream()
                        .map(s -> new LifecycleState(s.id(), s.title(), s.description(), s.terminal()))
                        .toList(),
                dso.transitions().stream().map(LifecycleBinding::transition).toList());
    }

    @Override
    public LifecycleDso toDso(Lifecycle lifecycle) {
        return new LifecycleDso(
                lifecycle.localId(),
                lifecycle.schemaVersion().orElseThrow(),
                lifecycle.title(),
                lifecycle.description(),
                lifecycle.initial(),
                lifecycle.states().stream()
                        .map(s -> new LifecycleDso.State(s.id(), s.title(), s.description(), s.terminal()))
                        .toList(),
                lifecycle.transitions().stream()
                        .map(LifecycleBinding::transitionDso)
                        .toList());
    }

    private static LifecycleTransition transition(LifecycleDso.Transition t) {
        TransitionSource from;
        if (!t.fromList().isEmpty()) {
            from = new TransitionSource.Many(t.fromList());
        } else if (t.from().orElseThrow().equals(TransitionSource.EveryNonTerminal.SPELLING)) {
            from = new TransitionSource.EveryNonTerminal();
        } else {
            from = new TransitionSource.One(t.from().orElseThrow());
        }
        return new LifecycleTransition(
                from,
                t.to(),
                t.event(),
                t.guard().stream()
                        .map(g -> new GuardEntry(GuardReference.parse(g.ref()), g.config(), g.blocking()))
                        .toList());
    }

    private static LifecycleDso.Transition transitionDso(LifecycleTransition t) {
        Optional<String> one = switch (t.from()) {
            case TransitionSource.One o -> Optional.of(o.state());
            case TransitionSource.Many m -> Optional.empty();
            case TransitionSource.EveryNonTerminal any -> Optional.of(TransitionSource.EveryNonTerminal.SPELLING);
        };
        List<String> many = t.from() instanceof TransitionSource.Many m ? m.states() : List.of();
        return new LifecycleDso.Transition(
                one,
                many,
                t.to(),
                t.event(),
                t.guards().stream()
                        .map(g -> new LifecycleDso.GuardEntry(g.ref().spelling(), g.config(), g.blocking()))
                        .toList());
    }
}
