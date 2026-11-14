package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.packs.domain.AppliesTo;
import ai.speckitty.charter.packs.domain.GlossaryContext;
import ai.speckitty.charter.packs.domain.GlossaryContextIdentifier;
import ai.speckitty.charter.packs.domain.GlossaryTerm;
import ai.speckitty.charter.packs.domain.TermIdentifier;
import ai.speckitty.charter.packs.domain.TermStatus;

/**
 * The {@link KindBinding} of the glossary context (ADR-0049): {@link GlossaryDso} to {@link GlossaryContext} and back.
 * Its files are {@code *.glossary.yaml}, validated by the {@code glossary} schema. A context sits at
 * {@code glossaries/<id>.glossary.yaml}: its id is its file stem (ADR-0049 decision 2), a file anywhere else is refused
 * by placement, and a new context is created at that path.
 */
enum GlossaryBinding implements KindBinding<GlossaryDso, GlossaryContext> {
    /** The binding. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.GLOSSARY;
    }

    @Override
    public Class<GlossaryContext> entityType() {
        return GlossaryContext.class;
    }

    @Override
    public Class<GlossaryDso> dsoType() {
        return GlossaryDso.class;
    }

    @Override
    public String idOf(GlossaryDso dso) {
        return dso.id();
    }

    /** A context's place: {@code glossaries/<id>.glossary.yaml} (ADR-0053 decision 11). */
    @Override
    public String fileName(String elementId) {
        return "glossaries/" + elementId + ".glossary.yaml";
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
     * @throws IllegalArgumentException also when a term's {@code status} is not a status the domain knows, or a
     *     {@code see_also} entry's pack qualifier is malformed
     */
    @Override
    public GlossaryContext toEntity(PackIdentifier pack, GlossaryDso dso) {
        GlossaryContext.Builder builder = GlossaryContext.builder()
                .id(new GlossaryContextIdentifier(dso.id()))
                .packId(pack)
                .name(dso.name())
                .description(dso.description())
                .tags(dso.tags())
                .appliesTo(new AppliesTo(
                        dso.appliesTo().paths(),
                        dso.appliesTo().languages(),
                        dso.appliesTo().stacks()));
        for (GlossaryDso.Term term : dso.terms()) {
            builder.term(new GlossaryTerm(
                    new TermIdentifier(term.id()),
                    term.name(),
                    term.definition(),
                    term.examples(),
                    term.aliases(),
                    term.avoid(),
                    TermStatus.fromToken(term.status()),
                    term.seeAlso().stream().map(PackQualifiedReference::parse).toList()));
        }
        return builder.build();
    }

    @Override
    public GlossaryDso toDso(GlossaryContext context) {
        AppliesTo appliesTo = context.appliesTo();
        return new GlossaryDso(
                context.schemaVersion().orElseThrow(),
                context.id().value(),
                context.name(),
                context.description().orElseThrow(),
                context.tags(),
                new GlossaryDso.AppliesTo(appliesTo.paths(), appliesTo.languages(), appliesTo.stacks()),
                context.terms().stream()
                        .map(term -> new GlossaryDso.Term(
                                term.id().value(),
                                term.name(),
                                term.definition(),
                                term.examples(),
                                term.aliases(),
                                term.avoid(),
                                term.status().token(),
                                term.seeAlso().stream()
                                        .map(PackQualifiedReference::toString)
                                        .toList()))
                        .toList());
    }
}
