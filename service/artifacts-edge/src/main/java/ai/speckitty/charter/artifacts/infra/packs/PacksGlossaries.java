package ai.speckitty.charter.artifacts.infra.packs;

import ai.speckitty.charter.artifacts.application.GlossaryReads;
import ai.speckitty.charter.artifacts.application.GlossaryView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import ai.speckitty.charter.packs.domain.GlossaryContext;
import ai.speckitty.charter.packs.domain.GlossaryTerm;
import ai.speckitty.charter.packs.domain.TermIdentifier;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The {@link GlossaryReads} over the packs slice: the one class of the artifacts edge that asks it for glossary
 * contexts, and only its application ring ({@link ForeignArtifactRead}) and domain. Each read is one verified read of
 * the packs slice; this adapter narrows the entities to the packs domain's {@link GlossaryContext} and maps them to the
 * edge's own {@link GlossaryView}, so nothing above it names a packs type.
 *
 * <p>The pack and the context are judged by the packs slice, in that order, with its refusals unchanged. The term is
 * looked up in the verified context ({@link GlossaryContext#term}), so it is of the same inputs; a term the context
 * does not hold is {@code artifact_not_found} with {@link GlossaryReads#termNotHeld}'s detail.
 */
@Component
public final class PacksGlossaries implements GlossaryReads {

    private final ForeignArtifactRead packs;

    /**
     * Reads over the packs slice's read of the artifacts another slice renders.
     *
     * @param packs the verified reads of an artifact and of a kind's artifacts
     */
    @Autowired
    public PacksGlossaries(ForeignArtifactRead packs) {
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    @Override
    public Listed list(PackIdentifier pack) {
        ConsistentRead.Verified<ForeignArtifactRead.Held> verified = packs.held(pack, ArtifactKind.GLOSSARY);
        return new Listed(
                verified.value().artifacts().stream()
                        .map(GlossaryContext.class::cast)
                        .map(PacksGlossaries::viewOf)
                        .sorted(Comparator.comparing(GlossaryView::id))
                        .toList(),
                verified.value().revision().token(),
                verified.fingerprint());
    }

    @Override
    public Listed list(PackIdentifier pack, String expectedFingerprint) {
        Listed listed = list(pack);
        Fingerprints.require(listed.sourceFingerprint(), expectedFingerprint);
        return listed;
    }

    @Override
    public GlossaryView view(CharterArtifact context, String read) {
        if (!(context instanceof GlossaryContext held)) {
            throw new IllegalStateException(read + " holds " + context.kind().token() + " " + context.localId()
                    + ", which is no glossary context of the packs slice.");
        }
        return viewOf(held);
    }

    @Override
    public Read context(PackIdentifier pack, String contextId) {
        ConsistentRead.Verified<ForeignArtifactRead.Foreign> verified = verifiedContext(pack, contextId);
        return new Read(
                viewOf(GlossaryContext.class.cast(verified.value().artifact())),
                verified.value().revision().token(),
                verified.fingerprint());
    }

    @Override
    public TermRead term(PackIdentifier pack, String contextId, String termId) {
        ConsistentRead.Verified<ForeignArtifactRead.Foreign> verified = verifiedContext(pack, contextId);
        GlossaryContext context = GlossaryContext.class.cast(verified.value().artifact());
        GlossaryTerm term = heldTerm(context, termId)
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.ARTIFACT_NOT_FOUND, GlossaryReads.termNotHeld(contextId, termId)));
        return new TermRead(
                termOf(context, term),
                context.localId(),
                context.packId(),
                verified.value().revision().token(),
                verified.fingerprint());
    }

    private ConsistentRead.Verified<ForeignArtifactRead.Foreign> verifiedContext(
            PackIdentifier pack, String contextId) {
        return packs.read(pack, ArtifactKind.GLOSSARY, contextId, 0);
    }

    /** The context's term of an id; an id outside the term grammar names no term, so it is not held, never a fault. */
    private static Optional<GlossaryTerm> heldTerm(GlossaryContext context, String termId) {
        return context.holdsTerm(termId) ? context.term(new TermIdentifier(termId)) : Optional.empty();
    }

    /**
     * The edge's view of a packs glossary context: every field its file declares.
     *
     * @param context the packs slice's context
     * @return the view
     */
    static GlossaryView viewOf(GlossaryContext context) {
        return new GlossaryView(
                context.localId(),
                context.packId(),
                context.schemaVersion().orElseThrow(),
                context.name(),
                context.description().orElseThrow(),
                context.tags(),
                context.appliesTo().paths(),
                context.appliesTo().languages(),
                context.appliesTo().stacks(),
                context.terms().stream().map(term -> termOf(context, term)).toList());
    }

    private static GlossaryView.Term termOf(GlossaryContext context, GlossaryTerm term) {
        return new GlossaryView.Term(
                term.id().value(),
                term.name(),
                term.definition(),
                term.status().token(),
                term.examples(),
                term.aliases(),
                term.avoid(),
                term.seeAlso().stream().map(PackQualifiedReference::toString).toList(),
                term.urn(context.id()).toString());
    }
}
