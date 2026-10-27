package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;

/** The {@link KindBinding} of the toolguide: {@link ToolguideDso} to {@link Toolguide} and back. */
enum ToolguideBinding implements KindBinding<ToolguideDso, Toolguide> {
    /** The binding. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.TOOLGUIDE;
    }

    @Override
    public Class<Toolguide> entityType() {
        return Toolguide.class;
    }

    @Override
    public Class<ToolguideDso> dsoType() {
        return ToolguideDso.class;
    }

    @Override
    public String idOf(ToolguideDso dso) {
        return dso.id();
    }

    @Override
    public Toolguide toEntity(PackIdentifier pack, ToolguideDso dso) {
        Toolguide.Builder builder = Toolguide.builder()
                .id(new ToolguideIdentifier(dso.id()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .tool(dso.tool())
                .title(dso.title())
                .guidePath(dso.guidePath())
                .summary(dso.summary())
                .lastUpdated(dso.lastUpdated().orElse(null));
        dso.commands().forEach(builder::command);
        dso.appliesToLanguages().forEach(builder::appliesToLanguage);
        dso.references().forEach(builder::reference);
        return builder.build();
    }

    @Override
    public ToolguideDso toDso(Toolguide toolguide) {
        return new ToolguideDso(
                toolguide.id().value(),
                toolguide.schemaVersion().orElseThrow(),
                toolguide.tool(),
                toolguide.title(),
                toolguide.guidePath(),
                toolguide.summary(),
                toolguide.commands(),
                toolguide.appliesToLanguages(),
                toolguide.lastUpdated(),
                toolguide.references());
    }
}
