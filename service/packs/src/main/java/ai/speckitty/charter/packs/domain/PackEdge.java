package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.RelationType;
import java.util.Objects;

/**
 * One edge of a pack's graph documents, as the pack authors it (ADR-0064): the only place a relationship is authored.
 * The endpoints are as written, bare ({@code <kind>:<id>}) or pack-qualified ({@code <pack-name>::<kind>:<id>}).
 *
 * @param source the source, as written
 * @param target the target, as written
 * @param relation the relation
 */
public record PackEdge(String source, String target, RelationType relation) {

    /**
     * Checks the fields.
     *
     * @param source the source
     * @param target the target
     * @param relation the relation
     */
    public PackEdge {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(relation, "relation");
    }
}
