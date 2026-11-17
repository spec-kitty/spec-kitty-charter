package ai.speckitty.charter.packs.domain;

/**
 * Port: reads one pack root once (#554, S6, "Port, no rule change"). Registration and the validation edge reach the
 * storage edge's checks through it, so neither names a storage type: the readout carries the pack's contents, its
 * classified files, the files it does not hold and the storage edge's verdict of the read, as kernel and domain values.
 * The file-system reader implements it in the storage edge, and a composition root supplies it.
 */
public interface PackReading {

    /**
     * Reads one pack root. It never throws on file content: a file that is absent, unparseable or invalid, and a root
     * that cannot be read, are reported in the readout's rationale.
     *
     * @param root the pack to read
     * @return what the read found
     */
    PackReadout read(PackRoot root);
}
