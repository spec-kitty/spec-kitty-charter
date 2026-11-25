package ai.speckitty.charter.packdoctor;

import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packvalidation.set.PackSetEntry;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The {@code remediate} command of pack-doctor (FR-009 of mission pack-doctor-coverage-01M7YMQK, ruling R-j, plan D7,
 * T030): it hands each pack, in the order given, to the storage edge's remediation ({@link Repair}, which
 * {@link PackDoctorMain}'s composition root plugs in) and prints its outcome, one line per pack:
 *
 * <pre>
 * &lt;dir&gt;: rewritten
 * &lt;dir&gt;: rewritten, &lt;n&gt; quoted number(s) repaired
 *   repaired at &lt;file&gt;#&lt;pointer&gt;: "&lt;value&gt;" is now a number   (an integer, in an integer field)
 * &lt;dir&gt;: unchanged
 * &lt;dir&gt;: refused
 *   &lt;code&gt; at &lt;file&gt;[#&lt;pointer&gt;]: &lt;detail&gt;
 * &lt;dir&gt;: refused
 *   the round trip refused &lt;file&gt;: &lt;reason&gt;
 * </pre>
 *
 * <p>It judges nothing: a refusal is the storage verdict's, without the manifest gate, or the round trip's refusal of a
 * modelled file it cannot write in canonical form (Tier R B4), as the remediation states it. The remediation, not this
 * command, judges a pack whose quoted numbers it wrote plain again in its work copy (#651), and refuses it, nothing written, when anything is
 * left: a fault beside the quoted numbers, a number the field's range refuses, or a quoted number it could not write
 * plain; then a quoted number's detail says that remediate repairs it once the other faults are fixed, or why it
 * cannot, never to run remediate. Either is a fault of the
 * pack's content, so the run exits 1, never 2. The check that follows, {@code validate --round-trip} over the same set,
 * is the main's.
 */
final class Remediate {

    private Remediate() {}

    /**
     * What a run of {@code remediate} did: whether a pack was refused, and each quoted number written plain, which the
     * report lists as its {@code repairs} (operator ruling Q14 of 2026-11-25, #651).
     *
     * @param refused whether any pack was refused
     * @param repairs the quoted numbers written plain, per pack in the order given, each pack's in its lines' order
     */
    record Done(boolean refused, List<Listed> repairs) {
        /**
         * Copies the list.
         *
         * @param refused whether a pack was refused
         * @param repairs the repairs
         */
        Done {
            repairs = List.copyOf(repairs);
        }
    }

    /**
     * One quoted number written plain, with the pack it is in.
     *
     * @param pack the pack's id, the name the report's pack list gives it
     * @param repaired the repair
     */
    record Listed(String pack, Repaired repaired) {
        /**
         * Checks the fields.
         *
         * @param pack the pack
         * @param repaired the repair
         */
        Listed {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(repaired, "repaired");
        }
    }

    /** What remediating one pack did, as the remediation answers it. */
    sealed interface Result permits Rewritten, Unchanged, Refused, NotRoundTripped {}

    /**
     * The pack was rewritten in canonical form and its manifest restamped, its quoted numbers written plain first.
     *
     * @param oldCopyLeft the hidden copy of the pack as it was, when it could not be deleted after the swap
     * @param repaired the quoted numbers written plain, in the order the verdict found them
     */
    record Rewritten(Optional<Path> oldCopyLeft, List<Repaired> repaired) implements Result {
        /**
         * Checks the fields and copies the list.
         *
         * @param oldCopyLeft the copy left behind, if any
         * @param repaired the quoted numbers written plain
         */
        Rewritten {
            Objects.requireNonNull(oldCopyLeft, "oldCopyLeft");
            repaired = List.copyOf(repaired);
        }
    }

    /**
     * A quoted number in a number field, written plain (#651).
     *
     * @param file the pack-relative path of the file that held it
     * @param pointer the JSON pointer of the value
     * @param value the scalar's value as the file held it, a string, now written as a number
     * @param integer whether the field is typed {@code integer}, else {@code number}
     */
    record Repaired(String file, String pointer, String value, boolean integer) {
        /**
         * Checks the fields.
         *
         * @param file the file
         * @param pointer the pointer
         * @param value the value
         * @param integer whether the field takes integers only
         */
        Repaired {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(pointer, "pointer");
            Objects.requireNonNull(value, "value");
            if (!pointer.startsWith("/")) {
                throw new IllegalArgumentException("a repair is at a field, never a file's root: '" + pointer + "'");
            }
        }
    }

    /** The pack was already canonical and stamped: nothing was written. */
    record Unchanged() implements Result {}

    /**
     * The pack holds a fault remediation does not repair: nothing was written.
     *
     * @param violations the faults, as the storage verdict states them (once the pack's quoted numbers were written plain
     *     in its copy, as the remediation words them)
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

    /**
     * The round trip refuses some modelled files of the pack, so it cannot be rewritten in canonical form: nothing was
     * written.
     *
     * @param refused each refused file, pack-relative, with the round trip's reason, sorted by path
     */
    record NotRoundTripped(SortedMap<String, String> refused) implements Result {
        /**
         * Copies the map.
         *
         * @param refused the refused files
         */
        NotRoundTripped {
            refused = Collections.unmodifiableSortedMap(new TreeMap<>(refused));
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
         * @throws IllegalStateException when the remediation fails after its precondition passed (a manifest the stamp
         *     refuses); the pack is then as it was
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
     * @return whether any pack was refused, and the quoted numbers written plain
     */
    static Done run(List<PackSetEntry> packs, Repair repair, PrintStream out, PrintStream err) {
        Objects.requireNonNull(repair, "repair");
        boolean refused = false;
        List<Listed> listed = new ArrayList<>();
        for (PackSetEntry pack : packs) {
            String locator = pack.root().locator();
            switch (repair.repair(pack.root())) {
                case Rewritten rewritten -> {
                    out.println(locator + ": rewritten" + repairedCount(rewritten.repaired()));
                    rewritten.repaired().forEach(repaired -> {
                        out.println("  " + line(repaired));
                        listed.add(new Listed(pack.name(), repaired));
                    });
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
                case NotRoundTripped notRoundTripped -> {
                    refused = true;
                    out.println(locator + ": refused");
                    notRoundTripped
                            .refused()
                            .forEach((file, reason) -> out.println("  the round trip refused " + file + ": " + reason));
                }
            }
        }
        return new Done(refused, listed);
    }

    /** What the outcome line adds for the quoted numbers repaired: nothing when there was none. */
    static String repairedCount(List<Repaired> repaired) {
        return switch (repaired.size()) {
            case 0 -> "";
            case 1 -> ", 1 quoted number repaired";
            default -> ", " + repaired.size() + " quoted numbers repaired";
        };
    }

    /** One repair: where the value lies, and what it is now. */
    static String line(Repaired repaired) {
        // A repair is always at a typed field, never a file's root, so its pointer is never empty (the report's ^/).
        return "repaired at " + repaired.file() + "#" + repaired.pointer() + ": \"" + repaired.value() + "\" is now "
                + (repaired.integer() ? "an integer" : "a number");
    }

    /** Each repair of the run as the report's {@code repairs} item: pack, file, pointer, value and type. */
    static List<Map<String, String>> reported(Done done) {
        return done.repairs().stream()
                .map(listed -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("pack", listed.pack());
                    item.put("file", listed.repaired().file());
                    item.put("pointer", listed.repaired().pointer());
                    item.put("value", listed.repaired().value());
                    item.put("type", listed.repaired().integer() ? "integer" : "number");
                    return item;
                })
                .toList();
    }

    /** One fault: its code, where it lies, and its detail. */
    static String line(Reason violation) {
        String file = violation.location().file();
        String pointer = violation.location().pointer();
        String where = file.isEmpty() ? "" : " at " + file + (pointer.isEmpty() ? "" : "#" + pointer);
        return violation.code().value() + where + ": " + violation.detail();
    }
}
