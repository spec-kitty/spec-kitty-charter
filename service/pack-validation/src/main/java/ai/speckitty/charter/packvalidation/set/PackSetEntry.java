package ai.speckitty.charter.packvalidation.set;

import ai.speckitty.charter.packs.domain.PackRoot;
import java.util.Objects;

/**
 * One pack of a pack set, as the composition root gives it: its root, whose name is the pack's configured name and whose
 * configured level, when present, is the level the set places it at. A root with no configured level is placed at the
 * level its {@code pack.yaml} declares.
 *
 * @param root the pack's root
 */
public record PackSetEntry(PackRoot root) {
    /**
     * Checks the field.
     *
     * @param root the root
     */
    public PackSetEntry {
        Objects.requireNonNull(root, "root");
    }

    /**
     * The pack's configured name: the one place the pack set takes a pack's identity from.
     *
     * @return the root's name
     */
    public String name() {
        return root.name();
    }
}
