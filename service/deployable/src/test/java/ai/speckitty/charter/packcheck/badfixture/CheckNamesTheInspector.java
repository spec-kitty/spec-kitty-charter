package ai.speckitty.charter.packcheck.badfixture;

import ai.speckitty.charter.packs.domain.PackReading;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;

/**
 * A pack-check class other than its main that names the file-system reader: the known-good control of the main's
 * exemption (#478). Only {@code PackCheckMain}, by its fully qualified name, may name it, so the rules must refuse this.
 */
public final class CheckNamesTheInspector {

    private CheckNamesTheInspector() {}

    /**
     * Builds the reader outside the composition root.
     *
     * @return the reader
     */
    public static PackReading reader() {
        return new FileSystemPackInspector();
    }
}
