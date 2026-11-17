package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.infra.storage.rules.TreeRead;

/**
 * Test fixtures, staging inside #554's single pull request (R4 b): the readout {@link PackPolicy#judge} is given for one
 * read of a pack tree, whose rationale is the one reader's read faults alone. {@code PackPolicy} judges the content
 * rules and the files not held itself, so it is never given the storage edge's verdict; validate mode, which is, is
 * {@code pack-validation}'s {@code PackCheck} (WP09). WP13 deletes this with {@code PackPolicy}.
 */
final class PolicyReadouts {

    private PolicyReadouts() {}

    /**
     * The readout of one read of a pack tree, its rationale the read faults.
     *
     * @param inspector the one reader
     * @param root the pack to read
     * @return the readout
     */
    static PackReadout of(FileSystemPackInspector inspector, PackRoot root) {
        TreeRead tree = inspector.readTree(root);
        return new PackReadout(
                tree.contents(), tree.census().files(), tree.census().notHeld(), new Rationale(tree.readFaults()));
    }

    /**
     * {@link #of(FileSystemPackInspector, PackRoot)} with the reader production builds.
     *
     * @param root the pack to read
     * @return the readout
     */
    static PackReadout of(PackRoot root) {
        return of(new FileSystemPackInspector(), root);
    }
}
