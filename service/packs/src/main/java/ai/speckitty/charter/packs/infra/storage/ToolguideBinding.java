package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownDocument;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The {@link KindBinding} of the toolguide: {@link ToolguideDso} to {@link Toolguide} and back. Its guide is its one
 * companion (#194, ADR-0039 widened): the Markdown file its {@code guide_path} names, {@code <id>.guide.md} beside the
 * toolguide's file, validated by the {@value #GUIDE_SCHEMA} structure schema and mapped through {@link GuideMarkdown}.
 * The {@code file-kinds.yaml} rule {@code **}{@code /toolguides/*.guide.md} declares that schema in the markdown form,
 * so it is this companion's own rule and admits it ({@link Companions}).
 */
enum ToolguideBinding implements KindBinding<ToolguideDso, Toolguide> {
    /** The binding. */
    INSTANCE;

    /** The structure schema of a guide, as {@code file-kinds.yaml} and {@link PackSchemas} name it. */
    static final String GUIDE_SCHEMA = "toolguide-guide";

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
    public boolean hasCompanions() {
        return true;
    }

    /** The guide: a Markdown companion validated by the {@value #GUIDE_SCHEMA} structure schema. */
    @Override
    public List<Companion> companions(ToolguideDso dso) {
        return List.of(new Companion(
                dso.guidePath(),
                dso.guide().map(CompanionContent.Markdown::new),
                ContentForm.MARKDOWN,
                Optional.of(GUIDE_SCHEMA)));
    }

    @Override
    public ToolguideDso withCompanions(ToolguideDso dso, Map<String, CompanionContent> contents) {
        return dso.withGuide(Optional.ofNullable(contents.get(dso.guidePath())).map(content -> switch (content) {
            case CompanionContent.Markdown markdown -> markdown.document();
            case CompanionContent.Text text ->
                throw new IllegalStateException("the guide " + dso.guidePath() + " was read as text, not markdown");
        }));
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException also when the shape holds no guide, or the guide breaks a rule of
     *     {@link GuideMarkdown#fromDocument}
     */
    @Override
    public Toolguide toEntity(PackIdentifier pack, ToolguideDso dso) {
        MarkdownDocument guide = dso.guide()
                .orElseThrow(() ->
                        new IllegalArgumentException("toolguide '" + dso.id() + "' holds no guide " + dso.guidePath()));
        Toolguide.Builder builder = Toolguide.builder()
                .id(new ToolguideIdentifier(dso.id()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .tool(dso.tool())
                .title(dso.title())
                .guidePath(dso.guidePath())
                .summary(dso.summary())
                .guide(GuideMarkdown.fromDocument(dso.title(), guide))
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
                toolguide.references(),
                Optional.of(GuideMarkdown.toDocument(toolguide.title(), toolguide.guide())));
    }
}
