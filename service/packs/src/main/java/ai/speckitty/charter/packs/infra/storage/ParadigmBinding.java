package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.ParadigmReference;

/** The {@link KindBinding} of the paradigm: {@link ParadigmDso} to {@link Paradigm} and back. */
enum ParadigmBinding implements KindBinding<ParadigmDso, Paradigm> {
    /** The binding. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.PARADIGM;
    }

    @Override
    public Class<Paradigm> entityType() {
        return Paradigm.class;
    }

    @Override
    public Class<ParadigmDso> dsoType() {
        return ParadigmDso.class;
    }

    @Override
    public String idOf(ParadigmDso dso) {
        return dso.id();
    }

    @Override
    public Paradigm toEntity(PackIdentifier pack, ParadigmDso dso) {
        Paradigm.Builder builder = Paradigm.builder()
                .id(new ParadigmIdentifier(dso.id()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .name(dso.name())
                .summary(dso.summary());
        dso.directiveRefs().forEach(builder::directiveRef);
        dso.references().forEach(ref -> builder.reference(new ParadigmReference(ref.type(), ref.id())));
        return builder.build();
    }

    @Override
    public ParadigmDso toDso(Paradigm paradigm) {
        return new ParadigmDso(
                paradigm.id().value(),
                paradigm.schemaVersion().orElseThrow(),
                paradigm.name(),
                paradigm.summary(),
                paradigm.directiveRefs(),
                paradigm.references().stream()
                        .map(ref -> new ParadigmDso.Reference(ref.type(), ref.id()))
                        .toList());
    }
}
