package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.TacticReference;
import ai.speckitty.charter.packs.domain.TacticStep;

/** The {@link KindBinding} of the tactic: {@link TacticDso} to {@link Tactic} and back. */
enum TacticBinding implements KindBinding<TacticDso, Tactic> {
    /** The binding. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.TACTIC;
    }

    @Override
    public Class<Tactic> entityType() {
        return Tactic.class;
    }

    @Override
    public Class<TacticDso> dsoType() {
        return TacticDso.class;
    }

    @Override
    public String idOf(TacticDso dso) {
        return dso.id();
    }

    @Override
    public Tactic toEntity(PackIdentifier pack, TacticDso dso) {
        Tactic.Builder builder = Tactic.builder()
                .id(new TacticIdentifier(dso.id()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .name(dso.name())
                .purpose(dso.purpose().orElse(null))
                .notes(dso.notes().orElse(null));
        dso.steps()
                .forEach(step -> builder.step(new TacticStep(
                        step.title(),
                        step.description(),
                        step.examples(),
                        step.references().stream().map(TacticBinding::toEntity).toList())));
        dso.failureModes().forEach(builder::failureMode);
        dso.appliesToLanguages().forEach(builder::appliesToLanguage);
        dso.references().forEach(ref -> builder.reference(toEntity(ref)));
        return builder.build();
    }

    @Override
    public TacticDso toDso(Tactic tactic) {
        return new TacticDso(
                tactic.id().value(),
                tactic.schemaVersion().orElseThrow(),
                tactic.name(),
                tactic.steps().stream()
                        .map(step -> new TacticDso.Step(
                                step.title(),
                                step.description(),
                                step.examples(),
                                step.references().stream().map(TacticBinding::toDso).toList()))
                        .toList(),
                tactic.purpose(),
                tactic.notes(),
                tactic.failureModes(),
                tactic.appliesToLanguages(),
                tactic.references().stream().map(TacticBinding::toDso).toList());
    }

    private static TacticReference toEntity(TacticDso.Reference ref) {
        return new TacticReference(ref.name(), ref.type(), ref.id(), ref.when());
    }

    private static TacticDso.Reference toDso(TacticReference ref) {
        return new TacticDso.Reference(ref.name(), ref.type(), ref.id(), ref.when());
    }
}
