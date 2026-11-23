package ai.speckitty.charter.packdoctor;

import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packvalidation.set.PackSetEntry;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code remediate} command of pack-doctor (FR-009 of mission pack-doctor-coverage-01M7YMQK, ruling R-j, plan D7,
 * T030): it hands each pack, in the order given, to the storage edge's remediation ({@link Repair}, which
 * {@link PackDoctorMain}'s composition root plugs in) and prints its outcome, one line per pack:
 *
 * <pre>
 * &lt;dir&gt;: rewritten
 * &lt;dir&gt;: unchanged
 * &lt;dir&gt;: refused
 *   &lt;code&gt; at &lt;file&gt;[#&lt;pointer&gt;]: &lt;detail&gt;
 * </pre>
 *
 * <p>It judges nothing: a refusal is the storage verdict's, without the manifest gate, as the remediation states it. The
 * check that follows, {@code validate --round-trip} over the same set, is the main's.
 */
final class Remediate {

    private Remediate() {}

    /** What remediating one pack did, as the remediation answers it. */
    sealed interface Result permits Rewritten, Unchanged, Refused {}

    /**
     * The pack was rewritten in canonical form and its manifest restamped.
     *
     * @param oldCopyLeft the hidden copy of the pack as it was, when it could not be deleted after the swap
     */
    record Rewritten(Optional<Path> oldCopyLeft) implements Result {
        /**
         * Checks the field.
         *
         * @param oldCopyLeft the copy left behind, if any
         */
        Rewritten {
            Objects.requireNonNull(oldCopyLeft, "oldCopyLeft");
        }
    }

    /** The pack was already canonical and stamped: nothing was written. */
    record Unchanged() implements Result {}

    /**
     * The pack holds a fault remediation does not repair: nothing was written.
     *
     * @param violations the faults, as the storage verdict states them
     */
    record Refused(List<Reason> violations) implements Result {
        /**
         * Copies the list.
         *
         * @param violations the faults
         */
        Refused {
            violations = List.copyOf(violations);
        }
    }

    /** The storage edge's remediation of one pack, which the composition root plugs in. */
    @FunctionalInterface
    interface Repair {
        /**
         * Remediates one pack.
         *
         * @param root the pack, configured as {@code validate} configures it
         * @return what it did
         * @throws java.io.UncheckedIOException when the pack cannot be rewritten; its message says the pack is as it
         *     was, or names the aside copy that holds it when it could not be put back
         * @throws IllegalStateException when the remediation fails after its precondition passed; the pack is then as
         *     it was
         */
        Result repair(PackRoot root);
    }

    /**
     * Remediates each pack in order and prints its outcome on {@code out}. A failure stops the run: the packs before it
     * keep what was done to them, and the failing pack is as it was, or where the failure names. A rewritten pack whose
     * old copy could not be deleted is still rewritten; a warning on {@code err} names the copy to delete, and the exit
     * code does not change.
     *
     * @param packs the packs, in the order given
     * @param repair the remediation
     * @param out where the outcomes go
     * @param err where a warning goes
     * @return whether any pack was refused
     */
    static boolean run(List<PackSetEntry> packs, Repair repair, PrintStream out, PrintStream err) {
        Objects.requireNonNull(repair, "repair");
        boolean refused = false;
        for (PackSetEntry pack : packs) {
            String locator = pack.root().locator();
            switch (repair.repair(pack.root())) {
                case Rewritten rewritten -> {
                    out.println(locator + ": rewritten");
                    rewritten
                            .oldCopyLeft()
                            .ifPresent(copy -> err.println("pack-doctor: warning: " + locator
                                    + " was rewritten, but its old copy could not be deleted: " + copy
                                    + "; delete it"));
                }
                case Unchanged unchanged -> out.println(locator + ": unchanged");
                case Refused refusal -> {
                    refused = true;
                    out.println(locator + ": refused");
                    refusal.violations().forEach(violation -> out.println("  " + line(violation)));
                }
            }
        }
        return refused;
    }

    /** One fault: its code, where it lies, and its detail. */
    static String line(Reason violation) {
        String file = violation.location().file();
        String pointer = violation.location().pointer();
        String where = file.isEmpty() ? "" : " at " + file + (pointer.isEmpty() ? "" : "#" + pointer);
        return violation.code().value() + where + ": " + violation.detail();
    }
}
