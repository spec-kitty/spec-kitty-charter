package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import java.util.List;
import java.util.Objects;

/**
 * The glossary contexts in force under an activation (ADR-0076 decisions 1 and 2): the winning copy of each
 * {@code glossary} context whose state is in force, with the pack and the level that hold it, in id order. A context
 * the activation deselects, denies or does not select is not listed, and a context overrides across levels whole, so
 * no copy the winner replaced is named. What each winner's pack stores is read against {@link Scope#sourceFingerprint}.
 *
 * @param scope the activation and the read that named the contexts
 * @param contexts the winning copies in force, in id order
 */
public record ContextsInForce(Scope scope, List<Held> contexts) {

    /**
     * Checks and copies the fields.
     *
     * @param scope the scope
     * @param contexts the contexts
     */
    public ContextsInForce {
        Objects.requireNonNull(scope, "scope");
        contexts = List.copyOf(Objects.requireNonNull(contexts, "contexts"));
    }

    /** The vocabulary read the contexts are named for: each has a tag of its own (ADR-0076 decision 8). */
    public enum Vocabulary {
        /** {@code getActivationVocabularyTerms}. */
        TERMS,
        /** {@code getActivationVocabularyContextive}. */
        CONTEXTIVE,
        /** {@code getActivationVocabularyTaboos}. */
        TABOOS
    }
}
