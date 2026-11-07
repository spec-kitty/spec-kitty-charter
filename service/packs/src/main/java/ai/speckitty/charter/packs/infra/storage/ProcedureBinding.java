package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.AntiPatternEntry;
import ai.speckitty.charter.packs.domain.Procedure;
import ai.speckitty.charter.packs.domain.ProcedureIdentifier;
import ai.speckitty.charter.packs.domain.ProcedureStep;

/**
 * The {@link KindBinding} of the procedure: {@link ProcedureDso} to {@link Procedure} and back.
 */
enum ProcedureBinding implements KindBinding<ProcedureDso, Procedure> {
    /** The binding. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.PROCEDURE;
    }

    @Override
    public Class<Procedure> entityType() {
        return Procedure.class;
    }

    @Override
    public Class<ProcedureDso> dsoType() {
        return ProcedureDso.class;
    }

    @Override
    public String idOf(ProcedureDso dso) {
        return dso.id();
    }

    /** A procedure's id is its file stem (ADR-0043 S1): {@code <id>.procedure.yaml}. */
    @Override
    public boolean idIsFileStem() {
        return true;
    }

    @Override
    public Procedure toEntity(PackIdentifier pack, ProcedureDso dso) {
        Procedure.Builder builder = Procedure.builder()
                .id(new ProcedureIdentifier(dso.id()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .title(dso.title())
                .description(dso.description())
                .entryCondition(dso.entryCondition())
                .exitCondition(dso.exitCondition())
                .notes(dso.notes().orElse(null));
        dso.steps()
                .forEach(step -> builder.step(new ProcedureStep(
                        step.title(), step.description(), step.role(), step.onSuccess(), step.onFailure())));
        dso.antiPatterns()
                .forEach(entry -> builder.antiPattern(
                        new AntiPatternEntry(entry.title(), entry.content(), entry.badExample(), entry.goodExample())));
        dso.appliesToLanguages().forEach(builder::appliesToLanguage);
        return builder.build();
    }

    @Override
    public ProcedureDso toDso(Procedure procedure) {
        return new ProcedureDso(
                procedure.id().value(),
                procedure.schemaVersion().orElseThrow(),
                procedure.title(),
                procedure.description().orElseThrow(),
                procedure.entryCondition(),
                procedure.exitCondition(),
                procedure.steps().stream()
                        .map(step -> new ProcedureDso.Step(
                                step.title(), step.description(), step.role(), step.onSuccess(), step.onFailure()))
                        .toList(),
                procedure.antiPatterns().stream()
                        .map(entry -> new ProcedureDso.AntiPattern(
                                entry.title(), entry.content(), entry.badExample(), entry.goodExample()))
                        .toList(),
                procedure.notes(),
                procedure.appliesToLanguages());
    }
}
