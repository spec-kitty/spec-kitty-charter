package ai.speckitty.charter.packvalidation.set;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackVerdict;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What the pack-set check concludes about a set of packs (#478, FR-002 to FR-004, FR-007): each pack's result in the
 * order the set was given, the composition faults of {@code CLOSURE}, the checks that ran and the checks skipped.
 *
 * @param packs each pack's result, in the order the set was given
 * @param faults the closure faults of {@code CLOSURE} and its preset phase, in the order the ruleset reports them,
 *     then, when {@code CLOSURE} found no composition fault and {@code --full} asked for them, those of
 *     {@code REFERENCES} and then those of {@code RELATIONS}; empty when the checks that ran passed or none ran
 * @param checks the checks that ran over the whole set: {@link Check#PACK}, then {@link Check#CLOSURE} when it ran, then
 *     {@link Check#REFERENCES} and {@link Check#RELATIONS} when they were asked for and ran ({@code RELATIONS} when
 *     at least one root's closure passed {@code REFERENCES}), then {@link Check#ROUND_TRIP} when it was asked for
 * @param skipped the checks skipped, with why; empty when every check asked for ran
 */
public record PackSetVerdict(
        List<PackResult> packs, List<ClosureFault> faults, List<Check> checks, List<Skip> skipped) {

    /**
     * Copies the lists.
     *
     * @param packs the per-pack results
     * @param faults the composition faults
     * @param checks the checks that ran
     * @param skipped the checks skipped
     */
    public PackSetVerdict {
        packs = List.copyOf(packs);
        faults = List.copyOf(faults);
        checks = List.copyOf(checks);
        skipped = List.copyOf(skipped);
    }

    /** A check of the pack set, in the order the checks run and are reported. */
    public enum Check {
        /** Validate mode, per pack. */
        PACK,
        /** The pack set's composition rules, with their preset phase. */
        CLOSURE,
        /**
         * {@code --full}: per root of the set, over the closure the root composes, every pack of it (ruling R-ae), the
         * delegation, reference and enhancement target checks, in registration's phase order (#632 item 3, ruling R-s;
         * named by ruling R-u). It runs when {@code CLOSURE} ran and found no composition fault (ruling R-aa).
         */
        REFERENCES,
        /**
         * {@code --full}: per root of the set, over the closure the root composes, the factory's relation rules, the
         * {@code specializes_from} cycle among them, with registration's codes (#632 item 3, FR-012). It runs when
         * {@code REFERENCES} runs, after it, over each root whose closure {@code REFERENCES} passed, and is listed
         * only when it judged at least one.
         */
        RELATIONS,
        /** The production round trip, per pack, judged with no expected-red baseline. */
        ROUND_TRIP
    }

    /**
     * Whether the set fails: a pack fails ({@link PackResult#failing()}), or {@code CLOSURE} found a fault.
     *
     * @return {@code true} when the set holds a violation
     */
    public boolean failing() {
        return !faults.isEmpty() || packs.stream().anyMatch(PackResult::failing);
    }

    /**
     * One pack's result.
     *
     * @param entry the pack as the set gave it
     * @param verdict validate mode's verdict of the pack
     * @param level the level the set places the pack at: its configured level, else the level its read
     *     {@code pack.yaml} declares; empty when neither is known
     * @param facts the pack's composition facts from the same read, or the refusal that stopped them
     * @param roundTrip the round trip's failing reasons; empty when it was not asked for, when the pack round-trips
     *     whole, or when the flow refused a pack whose verdict already reports a violation
     * @param setCheck the set check's own reasons for the pack: {@code pack_level_missing}, a violation at
     *     {@code pack.yaml}, when its facts were built but its level is unknown; empty otherwise
     */
    public record PackResult(
            PackSetEntry entry,
            PackVerdict verdict,
            Optional<PackLevel> level,
            PackReadout.Facts facts,
            List<Reason> roundTrip,
            List<Reason> setCheck) {

        /**
         * Checks the fields and copies the list.
         *
         * @param entry the entry
         * @param verdict the verdict
         * @param level the level, possibly empty
         * @param facts the facts
         * @param roundTrip the round trip's reasons
         * @param setCheck the set check's reasons
         */
        public PackResult {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(verdict, "verdict");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(facts, "facts");
            roundTrip = List.copyOf(roundTrip);
            setCheck = List.copyOf(setCheck);
        }

        /**
         * Whether the pack fails (D3): its verdict holds a violation, its round trip does, the set check does (an
         * unknown level, so a skipped {@code CLOSURE} is never a silent pass), or its composition facts cannot be read.
         * A pack whose facts are {@link PackReadout.Unreadable} fails even when its verdict holds no violation, since
         * the composing read would refuse it.
         *
         * @return {@code true} when the pack fails
         */
        public boolean failing() {
            return hasViolation(verdict.findings())
                    || hasViolation(roundTrip)
                    || hasViolation(setCheck)
                    || facts instanceof PackReadout.Unreadable;
        }

        /**
         * The refusal to report as the pack's own problem (D3, S10): the {@link PackReadout.Unreadable} facts when the
         * verdict holds no violation, so the pack still fails; empty when the facts were built, or when the verdict
         * already reports a violation, so a pack never gets a second problem for one fault.
         *
         * @return the refusal not yet reported, if any
         */
        public Optional<PackReadout.Unreadable> unreportedRefusal() {
            if (facts instanceof PackReadout.Unreadable unreadable && !hasViolation(verdict.findings())) {
                return Optional.of(unreadable);
            }
            return Optional.empty();
        }

        private static boolean hasViolation(Collection<Reason> reasons) {
            return reasons.stream().anyMatch(reason -> reason.isFailing(Severity.VIOLATION));
        }
    }

    /**
     * A check that did not run, and the packs that stopped it: {@code CLOSURE}, skipped for the whole set when a pack
     * has no facts or no level (D3), and with it {@code REFERENCES} when {@code --full} asked for it. No other check is
     * skipped.
     *
     * @param check the check skipped
     * @param packs each pack that stopped it, in the order the set was given, never empty
     */
    public record Skip(Check check, List<Unjudged> packs) {

        /**
         * Checks the fields and copies the list.
         *
         * @param check the check
         * @param packs the packs that stopped it
         */
        public Skip {
            Objects.requireNonNull(check, "check");
            packs = List.copyOf(packs);
            if (packs.isEmpty()) {
                throw new IllegalArgumentException("a skip names the packs that stopped the check");
            }
        }

        /**
         * Why the check was skipped, naming every pack that stopped it.
         *
         * @return the reason, in one line
         */
        public String reason() {
            return check + " needs the composition facts and the level of every pack; it has none for "
                    + packs.stream().map(Unjudged::text).collect(Collectors.joining("; "));
        }
    }

    /**
     * A pack a check cannot judge.
     *
     * @param pack the pack's configured name
     * @param why what is missing
     */
    public record Unjudged(String pack, String why) {

        /**
         * Checks the fields.
         *
         * @param pack the pack
         * @param why why it cannot be judged
         */
        public Unjudged {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(why, "why");
        }

        private String text() {
            return pack + " (" + why + ")";
        }
    }
}
