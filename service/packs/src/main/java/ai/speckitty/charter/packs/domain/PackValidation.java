package ai.speckitty.charter.packs.domain;

import java.util.Objects;

/**
 * Validate mode, the use case of FR-010: reads a pack root in full and returns every finding with the classification
 * of every file, where a load stops at the first blocking finding of the load path.
 *
 * <p>It is a pure domain service over the {@link PackInspector} port and owns no rule: the inspector reads the facts
 * and {@link PackPolicy#judge} decides. It is the seam the pack doctor (#115) composes, with a {@link PackRoot} whose
 * configured level is empty to judge a bare path as core's {@code validate_pack(path)} does.
 */
public final class PackValidation {

    private final PackInspector inspector;

    /**
     * A validation that reads packs through {@code inspector}.
     *
     * @param inspector the port that reads one pack root as facts
     */
    public PackValidation(PackInspector inspector) {
        this.inspector = Objects.requireNonNull(inspector, "inspector");
    }

    /**
     * Validates one pack: every file of the pack is read and every rule runs.
     *
     * @param root the pack to validate
     * @return the classification of every file and every finding, in precedence order; a pack with no finding is
     *     valid
     */
    public PackVerdict validate(PackRoot root) {
        return PackPolicy.judge(inspector.inspect(root, PackInspector.Scope.FULL));
    }
}
