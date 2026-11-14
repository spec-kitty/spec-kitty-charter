package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Outcome;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The vocabulary reads of an activation (ADR-0076; #106, FR-014 to FR-017): the terms in force, the Contextive
 * document and the taboo list. Each composes two reads, as the glossary context list does
 * ({@link EffectiveGlossaryReads}): the activations port names the glossary contexts in force and the source
 * fingerprint from one activation session ({@link ActivationArtifacts#contextsInForce}), then the packs port reads the
 * winning copies, each winning pack once, against that fingerprint ({@link WinningContexts}). That bounds each read to
 * one session and one read per winning pack (NFR-005).
 *
 * <p>A term is in force exactly when the winning copy of its context is in force and holds it: a context overrides
 * across levels whole, so a term only a replaced copy holds is not listed, and nothing is merged per term. Every term
 * of a context in force is listed whatever its status. The projections are pure functions of the terms
 * ({@link ContextiveProjection}, {@link TabooProjection}).
 *
 * <p>Refusals are the activations port's, judged before {@code If-None-Match}: {@code activation_not_found} and the
 * closure's refusals. A winning pack that changed since the activation named it is {@code stale_input}. A {@code 304}
 * passes through untouched, and no pack is read for it. An activation with no glossary in force answers empty lists.
 */
@Service
public final class VocabularyReads {

    private final ActivationArtifacts activations;
    private final WinningContexts packs;

    /**
     * The reads over the activations port and the packs port.
     *
     * @param activations the port that names the glossary contexts in force under an activation
     * @param packs the port that reads the winning copies of those contexts
     */
    @Autowired
    public VocabularyReads(ActivationArtifacts activations, WinningContexts packs) {
        this.activations = Objects.requireNonNull(activations, "activations");
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    /**
     * {@code listActivationVocabularyTerms}: every term in force, by context id and then term id.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the terms, or that the client holds the current representation
     * @throws CharterRefusal as the activations port refuses, or with {@code stale_input}
     */
    public Outcome<VocabularyTermList> terms(String activationId, @Nullable String ifNoneMatch) {
        return read(
                activationId,
                ContextsInForce.Vocabulary.TERMS,
                ifNoneMatch,
                inForce -> new VocabularyTermList(inForce.scope(), inForce.terms()));
    }

    /**
     * {@code getActivationVocabularyContextive}: the Contextive document of the contexts in force.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the document, or that the client holds the current representation
     * @throws CharterRefusal as {@link #terms} does
     */
    public Outcome<Contextive> contextive(String activationId, @Nullable String ifNoneMatch) {
        return read(
                activationId,
                ContextsInForce.Vocabulary.CONTEXTIVE,
                ifNoneMatch,
                inForce ->
                        new Contextive(inForce.scope(), ContextiveProjection.of(inForce.terms(), inForce.contexts())));
    }

    /**
     * {@code listActivationVocabularyTaboos}: the taboo list of the terms in force.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the entries, or that the client holds the current representation
     * @throws CharterRefusal as {@link #terms} does
     */
    public Outcome<TabooList> taboos(String activationId, @Nullable String ifNoneMatch) {
        return read(
                activationId,
                ContextsInForce.Vocabulary.TABOOS,
                ifNoneMatch,
                inForce -> new TabooList(inForce.scope(), TabooProjection.of(inForce.terms())));
    }

    private <T> Outcome<T> read(
            String activationId,
            ContextsInForce.Vocabulary vocabulary,
            @Nullable String ifNoneMatch,
            Function<InForce, T> answer) {
        return switch (activations.contextsInForce(activationId, vocabulary, ifNoneMatch)) {
            case Outcome.NotModified<ContextsInForce> notModified -> new Outcome.NotModified<>(notModified.etag());
            case Outcome.Found<ContextsInForce> found ->
                new Outcome.Found<>(answer.apply(inForce(found.value())), found.etag());
        };
    }

    /** The winning copies of the contexts in force, each winning pack read once, and their terms in order. */
    private InForce inForce(ContextsInForce named) {
        Map<String, GlossaryView> copies = WinnerReads.of(() -> packs.read(named));
        List<VocabularyTerm> terms = named.contexts().stream()
                .flatMap(winner -> copyOf(copies, winner).terms().stream().map(term -> VocabularyTerm.of(winner, term)))
                .sorted(Comparator.comparing(VocabularyTerm::contextId).thenComparing(VocabularyTerm::id))
                .toList();
        return new InForce(named.scope(), terms, copies);
    }

    private static GlossaryView copyOf(Map<String, GlossaryView> copies, Held winner) {
        GlossaryView copy = copies.get(winner.id());
        if (copy == null) {
            throw WinnerReads.stale();
        }
        return copy;
    }

    /** The vocabulary of one read: the scope, the terms in order and the winning copies by id. */
    private record InForce(Scope scope, List<VocabularyTerm> terms, Map<String, GlossaryView> contexts) {}

    /**
     * The port that reads the winning copies of the glossary contexts in force (research R6, NFR-005). The adapter is
     * {@code artifacts.infra.packs}, the one place of the edge that asks the packs slice.
     */
    public interface WinningContexts {

        /**
         * The winning copy of each context in force, as its pack stores it. Each winning pack is read once, however
         * many of its contexts are in force, and checked against {@link Scope#sourceFingerprint}.
         *
         * @param inForce the contexts in force and the read that named them
         * @return the winning copies by context id; a winner its pack no longer holds is absent
         * @throws CharterRefusal with {@code stale_input} when a pack's read verified another fingerprint, or as the
         *     packs slice refuses
         */
        Map<String, GlossaryView> read(ContextsInForce inForce);
    }

    /**
     * What {@code listActivationVocabularyTerms} answers.
     *
     * @param scope the activation and the read
     * @param terms the terms in force, by context id and then term id
     */
    public record VocabularyTermList(Scope scope, List<VocabularyTerm> terms) {

        /**
         * Checks and copies the fields.
         *
         * @param scope the scope
         * @param terms the terms
         */
        public VocabularyTermList {
            Objects.requireNonNull(scope, "scope");
            terms = List.copyOf(Objects.requireNonNull(terms, "terms"));
        }
    }

    /**
     * What {@code getActivationVocabularyContextive} answers.
     *
     * @param scope the activation and the read, for the tag and the source fingerprint header
     * @param document the Contextive document
     */
    public record Contextive(Scope scope, ContextiveProjection.Document document) {

        /**
         * Checks the fields.
         *
         * @param scope the scope
         * @param document the document
         */
        public Contextive {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(document, "document");
        }
    }

    /**
     * What {@code listActivationVocabularyTaboos} answers.
     *
     * @param scope the activation and the read
     * @param entries the taboo entries, by word and then term URN
     */
    public record TabooList(Scope scope, List<TabooProjection.Entry> entries) {

        /**
         * Checks and copies the fields.
         *
         * @param scope the scope
         * @param entries the entries
         */
        public TabooList {
            Objects.requireNonNull(scope, "scope");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }
}
