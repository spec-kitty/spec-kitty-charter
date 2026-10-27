package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.GlossaryPack;
import ai.speckitty.charter.packs.domain.GlossaryPackIdentifier;
import ai.speckitty.charter.packs.domain.GlossaryTerm;

/**
 * The {@link KindBinding} of the glossary pack: {@link GlossaryPackDso} to {@link GlossaryPack} and back. Its files are
 * {@code *.glossary-pack.yaml}, validated by the {@code glossary-pack} schema ({@code contracts/packs/file-kinds.yaml}),
 * neither of which is the kind's token. A glossary pack's file keeps its authored path, whatever its stem; a new one
 * is created as {@code <id>.glossary-pack.yaml}.
 */
enum GlossaryPackBinding implements KindBinding<GlossaryPackDso, GlossaryPack> {
    /** The binding. */
    INSTANCE;

    private static final String TOKEN = "glossary-pack";

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.GLOSSARY_PACK;
    }

    @Override
    public String fileToken() {
        return TOKEN;
    }

    @Override
    public String schemaName() {
        return TOKEN;
    }

    @Override
    public Class<GlossaryPack> entityType() {
        return GlossaryPack.class;
    }

    @Override
    public Class<GlossaryPackDso> dsoType() {
        return GlossaryPackDso.class;
    }

    @Override
    public String idOf(GlossaryPackDso dso) {
        return dso.id();
    }

    @Override
    public GlossaryPack toEntity(PackIdentifier pack, GlossaryPackDso dso) {
        GlossaryPack.Builder builder = GlossaryPack.builder()
                .id(new GlossaryPackIdentifier(dso.id()))
                .packId(pack)
                .provenance(dso.provenance())
                .description(dso.description());
        for (GlossaryPackDso.Term term : dso.terms()) {
            builder.term(new GlossaryTerm(
                    term.surface(),
                    term.definition(),
                    term.confidence(),
                    term.status(),
                    term.seeAlso(),
                    term.introducedInMission(),
                    term.synonymsToAvoid(),
                    term.aliases(),
                    term.bannedSynonyms()));
        }
        return builder.build();
    }

    @Override
    public GlossaryPackDso toDso(GlossaryPack pack) {
        return new GlossaryPackDso(
                pack.id().value(),
                pack.provenance(),
                pack.description(),
                pack.terms().stream()
                        .map(term -> new GlossaryPackDso.Term(
                                term.surface(),
                                term.definition(),
                                term.confidence(),
                                term.status(),
                                term.seeAlso(),
                                term.introducedInMission(),
                                term.synonymsToAvoid(),
                                term.aliases(),
                                term.bannedSynonyms()))
                        .toList());
    }
}
