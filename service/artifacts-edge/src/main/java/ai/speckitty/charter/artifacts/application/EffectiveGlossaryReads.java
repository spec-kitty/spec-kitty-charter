package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Facts;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.GlossaryWinner;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Outcome;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The glossary reads under an activation (#106; FR-011, research R5 and R7): the glossary contexts the activation's
 * closure holds, one context and one term of it, each from the copy the layer merge keeps, whatever its state. A context
 * overrides whole, so a term is answered only when the winning copy holds it. The context and the term are each one
 * read of the activations port, which answers the winner's facts and the context as its pack stores it from one
 * session; the packs port maps it ({@link GlossaryReads#view}). The list composes two reads, as the skill list does
 * ({@link EffectiveSkillReads}): the activation names the winners and the source fingerprint, and each winner's pack is
 * read for the summary against that fingerprint.
 *
 * <p>Refusals come in order: the activations port judges the activation ({@code activation_not_found}), then whether a
 * pack of the closure holds the context, then whether the winning copy holds the term ({@code artifact_not_found}),
 * all before {@code If-None-Match}. A second read at another fingerprint, or of a winner its pack no longer holds, is
 * {@code stale_input}. A term has no state of its own: its answer carries its context's facts.
 */
@Service
public final class EffectiveGlossaryReads {

    private final ActivationArtifacts activations;
    private final GlossaryReads packs;

    /**
     * The reads over the activations port and the packs port.
     *
     * @param activations the port that names the winners under an activation
     * @param packs the port that reads and maps what a pack stores of a glossary context
     */
    @Autowired
    public EffectiveGlossaryReads(ActivationArtifacts activations, GlossaryReads packs) {
        this.activations = Objects.requireNonNull(activations, "activations");
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    /**
     * The glossary contexts the activation's closure holds, each the winning copy, in id order, whatever their state.
     *
     * @param activationId the activation's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the contexts, or that the client holds the current representation
     * @throws CharterRefusal as the activations port refuses, or with {@code stale_input}
     */
    public Outcome<Listed> list(String activationId, @Nullable String ifNoneMatch) {
        return switch (activations.winners(activationId, ArtifactKind.GLOSSARY, ifNoneMatch)) {
            case Outcome.NotModified<ActivationArtifacts.Winners> notModified ->
                new Outcome.NotModified<>(notModified.etag());
            case Outcome.Found<ActivationArtifacts.Winners> found ->
                new Outcome.Found<>(listed(found.value()), found.etag());
        };
    }

    /**
     * One glossary context under the activation, whatever its state: its facts and the winning copy, every term
     * included.
     *
     * @param activationId the activation's id
     * @param contextId the context's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the context, or that the client holds the current representation
     * @throws CharterRefusal as the activations port refuses
     */
    public Outcome<Read> context(String activationId, String contextId, @Nullable String ifNoneMatch) {
        String read = "The read of the glossary context " + contextId + " under activation " + activationId;
        return switch (activations.glossary(activationId, contextId, ifNoneMatch)) {
            case Outcome.NotModified<GlossaryWinner> notModified -> new Outcome.NotModified<>(notModified.etag());
            case Outcome.Found<GlossaryWinner> found -> {
                GlossaryWinner winner = found.value();
                yield new Outcome.Found<>(
                        new Read(winner.scope(), winner.facts(), packs.view(winner.context(), read)), found.etag());
            }
        };
    }

    /**
     * One term of a glossary context under the activation, as the winning copy of the context holds it, with the
     * context's facts.
     *
     * @param activationId the activation's id
     * @param contextId the context's id
     * @param termId the term's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the term, or that the client holds the current representation
     * @throws CharterRefusal as the activations port refuses
     * @throws IllegalStateException when the context the port answered does not hold the term: a broken invariant of
     *     the read, which judged the term
     */
    public Outcome<TermRead> term(String activationId, String contextId, String termId, @Nullable String ifNoneMatch) {
        String read = "The read of the term " + termId + " of the glossary context " + contextId + " under activation "
                + activationId;
        return switch (activations.glossaryTerm(activationId, contextId, termId, ifNoneMatch)) {
            case Outcome.NotModified<GlossaryWinner> notModified -> new Outcome.NotModified<>(notModified.etag());
            case Outcome.Found<GlossaryWinner> found -> {
                GlossaryWinner winner = found.value();
                GlossaryView.Term term = packs.view(winner.context(), read).terms().stream()
                        .filter(held -> held.id().equals(termId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                read + ": the winning context does not hold the term the activation judged held."));
                yield new Outcome.Found<>(new TermRead(winner.scope(), winner.facts(), term), found.etag());
            }
        };
    }

    /** Each winner with the context its pack holds, every pack read once against the activation's fingerprint. */
    private Listed listed(ActivationArtifacts.Winners winners) {
        String fingerprint = winners.scope().sourceFingerprint();
        Map<PackIdentifier, Map<String, GlossaryView>> byPack = new HashMap<>();
        List<Item> items = winners.winners().stream()
                .map(winner -> {
                    Map<String, GlossaryView> held = byPack.computeIfAbsent(
                            winner.packId(),
                            pack -> WinnerReads.of(() -> packs.list(pack, fingerprint)).contexts().stream()
                                    .collect(Collectors.toMap(GlossaryView::id, Function.identity())));
                    GlossaryView context = held.get(winner.id());
                    if (context == null) {
                        throw WinnerReads.stale();
                    }
                    return new Item(winner, context);
                })
                .toList();
        return new Listed(winners.scope(), items);
    }

    /**
     * One glossary context of the list: the winner and the winning copy.
     *
     * @param winner the winner
     * @param context the winning copy
     */
    public record Item(Held winner, GlossaryView context) {

        /**
         * Checks the fields.
         *
         * @param winner the winner
         * @param context the context
         */
        public Item {
            Objects.requireNonNull(winner, "winner");
            Objects.requireNonNull(context, "context");
        }
    }

    /**
     * What the list answers.
     *
     * @param scope the activation and the read
     * @param items the contexts, by id
     */
    public record Listed(Scope scope, List<Item> items) {

        /**
         * Checks and copies the fields.
         *
         * @param scope the scope
         * @param items the items
         */
        public Listed {
            Objects.requireNonNull(scope, "scope");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }

    /**
     * What the context's read answers.
     *
     * @param scope the activation and the read
     * @param facts the winner's facts under the activation
     * @param context the winning copy
     */
    public record Read(Scope scope, Facts facts, GlossaryView context) {

        /**
         * Checks the fields.
         *
         * @param scope the scope
         * @param facts the facts
         * @param context the context
         */
        public Read {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(facts, "facts");
            Objects.requireNonNull(context, "context");
        }
    }

    /**
     * What the term's read answers: the term and the facts of the context that holds it.
     *
     * @param scope the activation and the read
     * @param facts the facts of the term's context under the activation
     * @param term the term, as the winning copy of its context holds it
     */
    public record TermRead(Scope scope, Facts facts, GlossaryView.Term term) {

        /**
         * Checks the fields.
         *
         * @param scope the scope
         * @param facts the facts
         * @param term the term
         */
        public TermRead {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(facts, "facts");
            Objects.requireNonNull(term, "term");
        }
    }
}
