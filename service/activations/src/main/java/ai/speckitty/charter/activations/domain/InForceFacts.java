package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;

/**
 * What a write may ask of the in-force resolution about the document it is about to store (IC-13, research R12): the
 * answer of {@code PacksSession#inForce}, as a function of the request the document makes. The rule that judges a
 * deny entry on a required artifact reads it, and no copy of the resolution lives in the domain.
 *
 * <p>The question is asked only when a write adds a deny entry on a required artifact, and asking it may refuse (the
 * layer merge of the closure is refused, {@code pack_unreadable}); the refusal reaches the write.
 */
@FunctionalInterface
public interface InForceFacts {

    /** No facts: the answer is empty, so no requirement is satisfied by an override. */
    InForceFacts NONE = query -> InForceAnswer.empty();

    /**
     * What the resolution puts in force for a document's request.
     *
     * @param query what the document selects, denies and filters
     * @return the states and the rules not applied
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal when the closure cannot be resolved
     */
    InForceAnswer inForce(InForceQuery query);
}
