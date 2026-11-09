package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.StyleguideAntiPattern;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.StyleguidePattern;
import ai.speckitty.charter.packs.domain.StyleguideScope;

/** The {@link KindBinding} of the styleguide: {@link StyleguideDso} to {@link Styleguide} and back. */
enum StyleguideBinding implements KindBinding<StyleguideDso, Styleguide> {
    /** The binding. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.STYLEGUIDE;
    }

    @Override
    public Class<Styleguide> entityType() {
        return Styleguide.class;
    }

    @Override
    public Class<StyleguideDso> dsoType() {
        return StyleguideDso.class;
    }

    @Override
    public String idOf(StyleguideDso dso) {
        return dso.id();
    }

    @Override
    public Styleguide toEntity(PackIdentifier pack, StyleguideDso dso) {
        Styleguide.Builder builder = Styleguide.builder()
                .id(new StyleguideIdentifier(dso.id()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .title(dso.title())
                .scope(StyleguideScope.fromToken(dso.scope())
                        .orElseThrow(() -> new IllegalArgumentException("unknown scope: " + dso.scope())))
                .tooling(dso.tooling().orElse(null))
                .qualityTest(dso.qualityTest().orElse(null))
                .structuralLintConfig(dso.structuralLintConfig().orElse(null));
        dso.principles().forEach(builder::principle);
        dso.patterns()
                .forEach(p -> builder.pattern(
                        new StyleguidePattern(p.name(), p.description(), p.badExample(), p.goodExample())));
        dso.antiPatterns()
                .forEach(a -> builder.antiPattern(
                        new StyleguideAntiPattern(a.name(), a.description(), a.badExample(), a.goodExample())));
        dso.appliesToLanguages().forEach(builder::appliesToLanguage);
        dso.references().forEach(builder::reference);
        return builder.build();
    }

    @Override
    public StyleguideDso toDso(Styleguide styleguide) {
        return new StyleguideDso(
                styleguide.id().value(),
                styleguide.schemaVersion().orElseThrow(),
                styleguide.title(),
                styleguide.scope().token(),
                styleguide.principles(),
                styleguide.patterns().stream()
                        .map(p -> new StyleguideDso.Pattern(p.name(), p.description(), p.badExample(), p.goodExample()))
                        .toList(),
                styleguide.antiPatterns().stream()
                        .map(a -> new StyleguideDso.AntiPattern(
                                a.name(), a.description(), a.badExample(), a.goodExample()))
                        .toList(),
                styleguide.tooling(),
                styleguide.qualityTest(),
                styleguide.structuralLintConfig(),
                styleguide.appliesToLanguages(),
                styleguide.references());
    }
}
