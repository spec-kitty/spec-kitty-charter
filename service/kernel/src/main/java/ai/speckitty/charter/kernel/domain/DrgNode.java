package ai.speckitty.charter.kernel.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One node of the Doctrine Reference Graph, with every field a graph document declares for it (ADR-0036: a node's value
 * is every declared field). {@code provenance} and {@code tags} are fields of a graph-shard node, {@code bodyPath} of an
 * org-fragment node; a writer refuses a field its document shape cannot hold rather than drop it.
 *
 * @param urn the node's identity
 * @param label the node's human-readable label (a shard's {@code label}, a fragment's {@code title}); empty when it has
 *     none
 * @param provenance where the node came from, as written; empty when it has none
 * @param tags the node's tags in written order, duplicates kept (ADR-0036 E15: a sequence); empty when it has none
 * @param bodyPath the path of the node's body file, as written; empty when it has none
 */
public record DrgNode(
        Urn urn, Optional<String> label, Optional<String> provenance, List<String> tags, Optional<String> bodyPath) {
    /**
     * Checks that every field is present and copies the tags.
     *
     * @param urn the node's identity
     * @param label the node's label, possibly empty
     * @param provenance the node's provenance, possibly empty
     * @param tags the node's tags, possibly empty
     * @param bodyPath the node's body path, possibly empty
     */
    public DrgNode {
        Objects.requireNonNull(urn, "urn");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(provenance, "provenance");
        tags = List.copyOf(tags);
        Objects.requireNonNull(bodyPath, "bodyPath");
    }

    /**
     * A node with no provenance, no tags and no body path.
     *
     * @param urn the node's identity
     * @param label the node's label, possibly empty
     */
    public DrgNode(Urn urn, Optional<String> label) {
        this(urn, label, Optional.empty(), List.of(), Optional.empty());
    }
}
