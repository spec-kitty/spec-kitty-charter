package ai.speckitty.charter.packvalidation;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.packs.domain.PackReading;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackVerdict;
import ai.speckitty.charter.packvalidation.placement.LevelRules;
import ai.speckitty.charter.packvalidation.placement.OrgFragmentRules;
import ai.speckitty.charter.packvalidation.placement.Placement;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Validate mode, the validation edge (#554, FR-005, S2, S6): it reads a pack once through the domain port
 * {@link PackReading}, appends the placement rules' rationale to the readout's with the kernel's {@link Rationale#and},
 * and maps every finding with the classification of every file ({@link PackVerdicts}).
 *
 * <p>The readout's rationale is the storage edge's verdict of the read: the read faults, the registration's
 * composition and, when those pass, the manifest gate. So validate mode finds every fault a registration finds, in the
 * same code and words, and more: placement (the configured level and the org fragment) is this edge's own extension,
 * and is checked after the gate, so it never gates it. It names the packs domain and the kernel only (C-003): a
 * composition root supplies the reading, the tests now and pack-check's main later (#478). A {@link PackRoot} with no
 * configured level judges a bare path, as core's {@code validate_pack(path)} does.
 *
 * <p>The pack is assembled under one registered id (#583, D2b): the id its caller names, or, when the caller names none,
 * the name of its directory. A name that is not a pack id is one {@code pack_id_invalid} violation at the pack, and the
 * pack is not assembled.
 */
public final class PackCheck {

    private static final ModularRuleset<Placement> PLACEMENT =
            ModularRuleset.<Placement>emptyRules().must(LevelRules.RULES).must(OrgFragmentRules.RULES);

    private final PackReading reading;

    /**
     * Validate mode over a reading.
     *
     * @param reading the port that reads one pack root once
     */
    public PackCheck(PackReading reading) {
        this.reading = Objects.requireNonNull(reading, "reading");
    }

    /**
     * The placement rules, in order: the level rules, then the org fragment's (S2). Validate mode alone runs them.
     *
     * @return the composition
     */
    static ModularRuleset<Placement> placement() {
        return PLACEMENT;
    }

    /**
     * One pack checked: the readout of its one read and the verdict validate mode gives it (#478, T010). The pack-set
     * check takes the pack's composition facts from the same readout, so a pack is never read twice.
     *
     * @param readout what the one read of the pack returned
     * @param verdict validate mode's verdict of that read
     */
    public record Checked(PackReadout readout, PackVerdict verdict) {
        /**
         * Checks the fields.
         *
         * @param readout the readout
         * @param verdict the verdict
         */
        public Checked {
            Objects.requireNonNull(readout, "readout");
            Objects.requireNonNull(verdict, "verdict");
        }
    }

    /**
     * Checks one pack: the pack is read once, every rule runs, and the readout is kept beside the verdict.
     *
     * @param root the pack to check
     * @return the readout and validate mode's verdict of it
     */
    public Checked check(PackRoot root) {
        PackReadout read = reading.read(root);
        Rationale all = read.rationale().and(placement().check(new Placement(read, root.configuredLevel())));
        return new Checked(read, PackVerdicts.of(read.files(), all));
    }

    /**
     * Validates the pack in a directory, assembled under the registered id its caller names (#583, D2b), whatever the
     * directory is called.
     *
     * @param packId the registered id the pack is assembled under
     * @param configuredLevel the level the pack is configured at; empty for a bare-path validation
     * @param directory the pack's directory
     * @return the classification of every file and every finding, in precedence order; a pack with no finding is
     *     valid
     */
    public PackVerdict validate(PackIdentifier packId, Optional<PackLevel> configuredLevel, Path directory) {
        Objects.requireNonNull(packId, "packId");
        return validate(new PackRoot(packId.value(), configuredLevel, directory.toString()));
    }

    /**
     * Validates the pack in a directory that its caller names no id for: it is assembled under the directory's name
     * (#583, D2b), and a directory name that is not a pack id is one {@code pack_id_invalid} violation.
     *
     * @param configuredLevel the level the pack is configured at; empty for a bare-path validation
     * @param directory the pack's directory
     * @return the classification of every file and every finding, in precedence order; a pack with no finding is
     *     valid
     */
    public PackVerdict validate(Optional<PackLevel> configuredLevel, Path directory) {
        return validate(new PackRoot(directoryName(directory), configuredLevel, directory.toString()));
    }

    /** The name of a directory, as the reader names it: the last segment of its normalized absolute path. */
    private static String directoryName(Path directory) {
        Path last = directory.toAbsolutePath().normalize().getFileName();
        return last == null ? "" : last.toString();
    }

    /**
     * Validates one pack, assembled under its root's name: the pack is read once and every rule runs ({@link #check}'s
     * verdict).
     *
     * @param root the pack to validate
     * @return the classification of every file and every finding, in precedence order; a pack with no finding is
     *     valid
     */
    public PackVerdict validate(PackRoot root) {
        return check(root).verdict();
    }
}
