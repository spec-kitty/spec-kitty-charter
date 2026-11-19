package ai.speckitty.charter.activations.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A tension in force, as the activations slice reads it (ADR-0045 decisions 8 to 10): two guidance artifacts in force
 * that pull against each other, with the first writer's words and the artifacts in force that reconcile both sides. The
 * packs slice resolved it over the merged closure; this is the adapter's copy, as values only.
 *
 * @param source the side with the smaller URN
 * @param target the side with the larger URN
 * @param when the authored condition
 * @param reason the authored reason
 * @param reconciledBy the artifacts in force that reconcile the pair, in URN order; empty when none does
 */
public record TensionSnapshot(
        NodeRef source, NodeRef target, Optional<String> when, Optional<String> reason, List<NodeRef> reconciledBy) {

    /**
     * Checks and copies the fields.
     *
     * @param source the source
     * @param target the target
     * @param when the condition
     * @param reason the reason
     * @param reconciledBy the reconcilers
     */
    public TensionSnapshot {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(reason, "reason");
        reconciledBy = List.copyOf(Objects.requireNonNull(reconciledBy, "reconciledBy"));
    }

    /**
     * Whether both sides are among the nodes.
     *
     * @param nodes the nodes a read lists
     * @return true when the read lists the source and the target
     */
    public boolean between(Set<NodeRef> nodes) {
        return nodes.contains(source) && nodes.contains(target);
    }
}
