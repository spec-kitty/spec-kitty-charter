package ai.speckitty.charter.packvalidation;

import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackInspector;
import ai.speckitty.charter.packs.domain.PackRoot;
import java.util.Objects;

/**
 * How pack-validation reads a pack: through the packs slice's {@link PackInspector} port, whose file-system adapter is the
 * Spring-free load the service runs, never a second derivation (ADR-0075 decision 3, as amended 2026-11-13; #553). The
 * module names only the port; a composition root plugs the adapter in. It is the module's one read: the rulesets of
 * later slices (#554) read the {@link PackContents} it returns, and {@code pack-check} starts here (#478). Validate
 * mode's verdict stays {@code PackValidation}'s, in {@code packs.domain}, until #554 replaces it with those rulesets.
 */
public final class PackLoad {

    private final PackInspector inspector;

    /**
     * A load over the packs slice's inspector port. A composition root gives it the file-system adapter
     * ({@code FileSystemPackInspector}): the service's {@code deployable}, and {@code pack-check}'s main.
     *
     * @param inspector the port that reads one pack root as facts
     */
    public PackLoad(PackInspector inspector) {
        this.inspector = Objects.requireNonNull(inspector, "inspector");
    }

    /**
     * Reads every file of one pack as facts, with no verdict.
     *
     * @param root the pack to read; a root with no configured level is read as a bare path
     * @return what the pack holds, its unreadable files included
     */
    public PackContents contents(PackRoot root) {
        return inspector.inspect(root, PackInspector.Scope.FULL);
    }
}
