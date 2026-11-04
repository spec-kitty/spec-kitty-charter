package ai.speckitty.charter.packs.domain;

import java.util.List;
import java.util.Objects;

/**
 * What the compose needs to know about one pack, read from one assembly of it: the artifacts its graph documents say it
 * changes, the artifacts it holds and the references it writes.
 *
 * @param changed the distinct targets of its {@code enhances} and {@code overrides} relations, in first-seen order
 * @param defined the artifacts it holds, as bare URNs ({@code <kind>:<id>}), in a stable order
 * @param references the references it writes, as written (bare or pack-qualified), in first-seen order
 */
public record PackCompositionFacts(List<String> changed, List<String> defined, List<String> references) {

    /** A pack that changes, holds and writes nothing. */
    public static final PackCompositionFacts NONE = new PackCompositionFacts(List.of(), List.of(), List.of());

    /**
     * Checks the fields and copies the lists.
     *
     * @param changed the artifacts it changes
     * @param defined the artifacts it holds
     * @param references the references it writes
     */
    public PackCompositionFacts {
        Objects.requireNonNull(changed, "changed");
        Objects.requireNonNull(defined, "defined");
        Objects.requireNonNull(references, "references");
        changed = List.copyOf(changed);
        defined = List.copyOf(defined);
        references = List.copyOf(references);
    }
}
