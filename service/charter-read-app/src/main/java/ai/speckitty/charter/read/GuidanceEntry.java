package ai.speckitty.charter.read;

import ai.speckitty.charter.domain.NodeKind;
import ai.speckitty.charter.domain.Relation;
import ai.speckitty.charter.domain.Urn;
import java.util.Optional;

/** One artifact of an action-scoped context and the edge that brought it in. */
public record GuidanceEntry(Urn urn, NodeKind kind, Optional<String> label, Relation relation, Urn via) {
}
