package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.DirectiveReference;
import ai.speckitty.charter.packs.domain.Enforcement;

/** The {@link KindBinding} of the directive: {@link DirectiveDso} to {@link Directive} and back. */
enum DirectiveBinding implements KindBinding<DirectiveDso, Directive> {
    /** The binding. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.DIRECTIVE;
    }

    @Override
    public Class<Directive> entityType() {
        return Directive.class;
    }

    @Override
    public Class<DirectiveDso> dsoType() {
        return DirectiveDso.class;
    }

    @Override
    public String idOf(DirectiveDso dso) {
        return dso.id();
    }

    @Override
    public Directive toEntity(PackIdentifier pack, DirectiveDso dso) {
        Directive.Builder builder = Directive.builder()
                .id(new DirectiveIdentifier(dso.id()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .title(dso.title())
                .intent(dso.intent())
                .enforcement(Enforcement.fromToken(dso.enforcement())
                        .orElseThrow(() -> new IllegalArgumentException("unknown enforcement: " + dso.enforcement())))
                .scope(dso.scope().orElse(null));
        dso.procedures().forEach(builder::procedure);
        dso.integrityRules().forEach(builder::integrityRule);
        dso.validationCriteria().forEach(builder::validationCriterion);
        dso.explicitAllowances().forEach(builder::explicitAllowance);
        dso.references()
                .forEach(ref ->
                        builder.reference(new DirectiveReference(ref.type(), ref.id(), ref.when(), ref.reason())));
        return builder.build();
    }

    @Override
    public DirectiveDso toDso(Directive directive) {
        return new DirectiveDso(
                directive.id().value(),
                directive.schemaVersion().orElseThrow(),
                directive.title(),
                directive.intent(),
                directive.enforcement().token(),
                directive.scope(),
                directive.procedures(),
                directive.integrityRules(),
                directive.validationCriteria(),
                directive.explicitAllowances(),
                directive.references().stream()
                        .map(ref -> new DirectiveDso.Reference(ref.type(), ref.id(), ref.when(), ref.reason()))
                        .toList());
    }
}
