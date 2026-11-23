package ai.speckitty.charter.packvalidation.set;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackReading;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.rules.PackRulesets;
import ai.speckitty.charter.packvalidation.PackCheck;
import ai.speckitty.charter.packvalidation.placement.LevelRules;
import ai.speckitty.charter.packvalidation.roundtrip.RoundTripping;
import ai.speckitty.charter.packvalidation.roundtrip.WholePackExpectation;
import ai.speckitty.charter.packvalidation.roundtrip.WholePackRoundTripRule;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.Check;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.PackResult;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.Skip;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.Unjudged;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The pack-set check (#478, FR-002 to FR-004, FR-007, T011): what pack-doctor runs over a set of packs.
 *
 * <ol>
 *   <li>Per pack, validate mode ({@link PackCheck#check}): one read, whose readout also carries the pack's composition
 *       facts (S1), so no pack is read twice.
 *   <li>Over the set as given (D5), {@code CLOSURE} with its preset phase ({@link PackRulesets#closureFaults}, the
 *       faults of {@code PackRulesets.closure(factsOf)}: the contests, then the preset items), on those facts. A pack's dependencies must be in the set.
 *       It runs only when every pack's facts are {@link PackReadout.Built} and every pack has a level; otherwise it is
 *       skipped and the verdict names the packs that stopped it (D3).
 *   <li>Per pack, when asked, the production round trip ({@link RoundTripping}), judged by {@link
 *       WholePackRoundTripRule} with no expected-red baseline ({@link WholePackExpectation#none()}). It runs whatever
 *       validate mode found: the two are independent checks, but a round-trip reason at a file where validate mode
 *       already has a violation is dropped, so one cause is one problem. A pack the flow refuses is that pack's result,
 *       never an exception: one problem, its validate-mode violation or else a {@code round_trip_red}. A pack whose
 *       name is not a pack id is not round-tripped: the round trip configures a pack under its name (#583, D2b), and
 *       validate mode's {@code pack_id_invalid} at the pack is already its one problem.
 * </ol>
 *
 * <p>A pack's identity is its entry's name (#583, D5). A name that is not a pack id never throws: the read refuses the
 * pack with one {@code pack_id_invalid} violation, its facts are {@link PackReadout.Unreadable}, and so {@code CLOSURE}
 * is skipped and names it (D3).
 *
 * <p>No rule is defined here: {@code CLOSURE} has one derivation, in {@link PackRulesets}.
 */
public final class PackSetCheck {

    private static final String NO_LEVEL = "its level is unknown: none is configured and pack.yaml declares none";

    private final PackCheck check;

    /**
     * The pack-set check over a reading.
     *
     * @param reading the port that reads one pack root once
     */
    public PackSetCheck(PackReading reading) {
        this.check = new PackCheck(Objects.requireNonNull(reading, "reading"));
    }

    /**
     * Checks a set of packs: {@code PACK} and {@code CLOSURE}.
     *
     * @param entries the packs, in the order the results are reported; no two with one name
     * @return the verdict
     * @throws IllegalArgumentException when the set is empty or two entries share a name
     */
    public PackSetVerdict check(List<PackSetEntry> entries) {
        return run(entries, Optional.empty());
    }

    /**
     * Checks a set of packs: {@code PACK}, {@code CLOSURE} and {@code ROUND_TRIP}.
     *
     * @param entries the packs, in the order the results are reported; no two with one name
     * @param roundTripping the production round trip, which the composition root supplies
     * @return the verdict
     * @throws IllegalArgumentException when the set is empty or two entries share a name
     * @throws java.io.UncheckedIOException when the round trip fails outside a pack read, as {@link RoundTripping}
     *     propagates it
     */
    public PackSetVerdict checkWithRoundTrip(List<PackSetEntry> entries, RoundTripping roundTripping) {
        return run(entries, Optional.of(Objects.requireNonNull(roundTripping, "roundTripping")));
    }

    private PackSetVerdict run(List<PackSetEntry> entries, Optional<RoundTripping> roundTripping) {
        refuseEmptyOrRepeated(entries);
        List<PackResult> results = new ArrayList<>();
        for (PackSetEntry entry : entries) {
            PackCheck.Checked checked = check.check(entry.root());
            List<Reason> reasons = roundTripping
                    .filter(flow -> PackIdentifier.isPackId(entry.name()))
                    .map(flow -> roundTrip(flow, entry, checked))
                    .orElse(List.of());
            Optional<PackLevel> level = levelOf(entry, checked.readout());
            results.add(new PackResult(
                    entry,
                    checked.verdict(),
                    level,
                    checked.readout().facts(),
                    reasons,
                    levelMissing(entry, checked.readout().facts(), level)));
        }
        List<Check> checks = new ArrayList<>(List.of(Check.PACK));
        List<ClosureFault> faults = List.of();
        List<Skip> skipped = new ArrayList<>();
        List<Unjudged> unjudged = unjudged(results);
        if (unjudged.isEmpty()) {
            faults = closure(results);
            checks.add(Check.CLOSURE);
        } else {
            skipped.add(new Skip(Check.CLOSURE, unjudged));
        }
        if (roundTripping.isPresent()) {
            checks.add(Check.ROUND_TRIP);
        }
        return new PackSetVerdict(results, faults, checks, skipped);
    }

    private static void refuseEmptyOrRepeated(List<PackSetEntry> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("a pack set holds at least one pack");
        }
        Set<String> names = new HashSet<>();
        for (PackSetEntry entry : entries) {
            if (!names.add(entry.name())) {
                throw new IllegalArgumentException("the pack " + entry.name() + " is given twice");
            }
        }
    }

    /**
     * The round trip of one pack, judged: a refusal of the pack is its result ({@link #refused}); an I/O failure
     * propagates.
     */
    private static List<Reason> roundTrip(RoundTripping flow, PackSetEntry entry, PackCheck.Checked checked) {
        try {
            return notReported(
                    checked, new WholePackRoundTripRule(WholePackExpectation.none()).apply(flow.run(entry.root())));
        } catch (CharterRefusal refusal) {
            return refused(checked, refusal);
        }
    }

    /**
     * The round-trip reasons whose file validate mode has no violation at, so a pack never gets two problems for one
     * cause: a link validate mode refuses ({@code pack_entry_linked}) is also a census file no adapter models, which the
     * round trip would report red at the same file. A reason at another file, or at none, stays: an independent red is
     * never hidden.
     */
    static List<Reason> notReported(PackCheck.Checked checked, List<Reason> reasons) {
        Set<String> violated = checked.verdict().findings().stream()
                .filter(finding -> finding.isFailing(Severity.VIOLATION))
                .map(finding -> finding.location().file())
                .filter(file -> !file.isEmpty())
                .collect(Collectors.toSet());
        return reasons.stream()
                .filter(reason -> !violated.contains(reason.location().file()))
                .toList();
    }

    /**
     * The round-trip reasons of a pack the flow refused, so the pack has exactly one problem for the fault: none when
     * validate mode already reports a violation, or when the pack's facts are {@link PackReadout.Unreadable}, whose
     * refusal is then its one problem ({@link PackSetVerdict.PackResult#unreportedRefusal}); otherwise one
     * {@code round_trip_red} ({@link WholePackRoundTripRule#refused}) carrying the refusal's code and detail.
     */
    private static List<Reason> refused(PackCheck.Checked checked, CharterRefusal refusal) {
        boolean reported =
                checked.verdict().findings().stream().anyMatch(finding -> finding.isFailing(Severity.VIOLATION))
                        || checked.readout().facts() instanceof PackReadout.Unreadable;
        if (reported) {
            return List.of();
        }
        return List.of(WholePackRoundTripRule.refused(refusal.code().token(), String.valueOf(refusal.getMessage())));
    }

    /**
     * The configured level, else the level the pack's {@code pack.yaml} declares, from the read descriptor: the source
     * the level rules read, whatever the composition facts are.
     */
    private static Optional<PackLevel> levelOf(PackSetEntry entry, PackReadout readout) {
        return entry.root()
                .configuredLevel()
                .or(() -> readout.contents().descriptor() instanceof FileRead.Read<PackDescriptor> read
                        ? read.value().declaredLevel()
                        : Optional.empty());
    }

    /**
     * The set check's reason for a pack it cannot place (M3): its facts were built, so its {@code pack.yaml} was read,
     * but no level is configured and none is declared. A pack whose facts cannot be read is not given it: its refusal
     * or its verdict already speaks for it.
     */
    private static List<Reason> levelMissing(PackSetEntry entry, PackReadout.Facts facts, Optional<PackLevel> level) {
        if (level.isPresent() || !(facts instanceof PackReadout.Built)) {
            return List.of();
        }
        return List.of(LevelRules.unknownLevel(entry.name()));
    }

    /** Each pack {@code CLOSURE} cannot judge, in the order the set was given: no facts, or no level. */
    private static List<Unjudged> unjudged(List<PackResult> results) {
        List<Unjudged> unjudged = new ArrayList<>();
        for (PackResult result : results) {
            if (result.facts() instanceof PackReadout.Unreadable unreadable) {
                unjudged.add(new Unjudged(
                        result.entry().name(),
                        "its composition facts cannot be read: "
                                + unreadable.code().token()
                                + ": "
                                + unreadable.detail()));
            } else if (result.level().isEmpty()) {
                unjudged.add(new Unjudged(result.entry().name(), NO_LEVEL));
            }
        }
        return unjudged;
    }

    /** {@code CLOSURE} with its preset phase (contests, then preset items) over packs that all have built facts and a level. */
    private static List<ClosureFault> closure(List<PackResult> results) {
        List<PackUnderResolution> set = new ArrayList<>();
        Map<String, PackCompositionFacts> facts = new HashMap<>();
        for (PackResult result : results) {
            PackReadout.Built built = (PackReadout.Built) result.facts();
            ConfiguredPack configured =
                    configured(result.entry(), result.level().orElseThrow());
            set.add(built.underResolution(configured));
            facts.put(configured.id().value(), built.facts());
        }
        return PackRulesets.closureFaults(
                set, pack -> facts.getOrDefault(pack.configured().id().value(), PackCompositionFacts.NONE));
    }

    /**
     * How the set configures a pack: the one place a pack's identity is taken, from the entry's name. Only a pack with
     * {@link PackReadout.Built} facts reaches it, and the reader builds facts only for a pack named by a pack id
     * (#583, D5), so the name is one.
     */
    private static ConfiguredPack configured(PackSetEntry entry, PackLevel level) {
        return new ConfiguredPack(new PackIdentifier(entry.name()), level);
    }
}
