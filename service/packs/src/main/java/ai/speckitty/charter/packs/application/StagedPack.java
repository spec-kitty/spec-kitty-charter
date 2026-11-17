package ai.speckitty.charter.packs.application;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A pack directory extracted into staging (IC-03).
 *
 * @param directory the pack's directory inside the staging root; the archive's one shared leading directory, when it
 *     had one, is already stripped
 * @param declaredName the name a fetched source's descriptor must declare, its target pack id (FR-004,
 *     {@code name_mismatch}); empty for an upload, whose name is the path's
 */
public record StagedPack(Path directory, Optional<String> declaredName) {

    /**
     * Checks the fields.
     *
     * @param directory the pack's directory
     * @param declaredName the name its descriptor must declare, if any
     */
    public StagedPack {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(declaredName, "declaredName");
    }

    /**
     * An uploaded tree: no name is required of it beyond the registration's own checks.
     *
     * @param directory the pack's directory
     */
    public StagedPack(Path directory) {
        this(directory, Optional.empty());
    }

    /**
     * The same tree, whose descriptor must declare {@code name} (#554, F8): the registration checks it once it has
     * judged the tree, so the tree is read once.
     *
     * @param name the target pack id
     * @return the tree with the name required
     */
    public StagedPack declaring(String name) {
        return new StagedPack(directory, Optional.of(name));
    }
}
