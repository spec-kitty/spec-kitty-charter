package ai.speckitty.charter.packvalidation.set;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackReading;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.rules.CompositionRules.CompositionFault;
import ai.speckitty.charter.packs.domain.rules.PackRulesets;
import ai.speckitty.charter.packvalidation.PackCheck;
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

/**
 * The pack-set check (#478, FR-002 to FR-004, FR-007, T011): what pack-check runs over a set of packs.
 *
 * <ol>
 *   <li>Per pack, validate mode ({@link PackCheck#check}): one read, whose readout also carries the pack's composition
 *       facts (S1), so no pack is read twice.
 *   <li>Over the set as given (D5), {@code CLOSURE} with its preset phase ({@link PackRulesets#closureFaults}, the
 *       faults of {@code PackRulesets.closure(presetIdsOf)}), on those facts. A pack's dependencies must be in the set.
 *       It runs only when every pack's facts are {@link PackReadout.Built} and every pack has a level; otherwise it is
 *       skipped and the verdict names the packs that stopped it (D3).
 *   <li>Per pack, when asked, the production round trip ({@link RoundTripping}), judged by {@link
 *       WholePackRoundTripRule} with no expected-red baseline ({@link WholePackExpectation#none()}). It runs whatever
 *       validate mode found: the two are independent checks. A pack the flow refuses is that pack's result, never an
 *       exception: its round trip is skipped and the skip says why.
 * </ol>
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

    /** One pack's round trip: its failing reasons, or the refusal that stopped the flow. */
    private record RoundTrip(List<Reason> reasons, Optional<CharterRefusal> refusal) {
        static final RoundTrip NONE = new RoundTrip(List.of(), Optional.empty());
    }

    private PackSetVerdict run(List<PackSetEntry> entries, Optional<RoundTripping> roundTripping) {
        refuseEmptyOrRepeated(entries);
        List<PackResult> results = new ArrayList<>();
        List<Unjudged> notRoundTripped = new ArrayList<>();
        for (PackSetEntry entry : entries) {
            PackCheck.Checked checked = check.check(entry.root());
            RoundTrip roundTrip =
                    roundTripping.map(flow -> roundTrip(flow, entry)).orElse(RoundTrip.NONE);
            List<Reason> reasons = roundTrip.reasons();
            if (roundTrip.refusal().isPresent()) {
                CharterRefusal refusal = roundTrip.refusal().get();
                notRoundTripped.add(new Unjudged(
                        entry.name(),
                        "the round trip refused it: " + refusal.code().token() + ": " + detail(refusal)));
                reasons = refusedWithoutViolation(checked, refusal);
            }
            results.add(new PackResult(
                    entry,
                    checked.verdict(),
                    levelOf(entry, checked.readout()),
                    checked.readout().facts(),
                    reasons));
        }
        List<Check> checks = new ArrayList<>(List.of(Check.PACK));
        List<CompositionFault> faults = List.of();
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
        if (!notRoundTripped.isEmpty()) {
            skipped.add(new Skip(Check.ROUND_TRIP, notRoundTripped));
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

    /** The round trip of one pack: a refusal of the pack is its result; an I/O failure propagates. */
    private static RoundTrip roundTrip(RoundTripping flow, PackSetEntry entry) {
        try {
            return new RoundTrip(
                    new WholePackRoundTripRule(WholePackExpectation.none()).apply(flow.run(entry.root())),
                    Optional.empty());
        } catch (CharterRefusal refusal) {
            return new RoundTrip(List.of(), Optional.of(refusal));
        }
    }

    /**
     * The round-trip reasons of a pack the flow refused: none when validate mode already reports a violation, so the
     * pack gets no second problem for one fault; otherwise one {@code round_trip_red}, so the pack still fails.
     */
    private static List<Reason> refusedWithoutViolation(PackCheck.Checked checked, CharterRefusal refusal) {
        boolean reported =
                checked.verdict().findings().stream().anyMatch(finding -> finding.isFailing(Severity.VIOLATION));
        if (reported) {
            return List.of();
        }
        return List.of(WholePackRoundTripRule.refused(refusal.code().token(), detail(refusal)));
    }

    private static String detail(CharterRefusal refusal) {
        return String.valueOf(refusal.getMessage());
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

    /** Each pack {@code CLOSURE} cannot judge, in the order the set was given: no facts, or no level. */
    private static List<Unjudged> unjudged(List<PackResult> results) {
        List<Unjudged> unjudged = new ArrayList<>();
        for (PackResult result : results) {
            if (result.facts() instanceof PackReadout.Unreadable unreadable) {
                unjudged.add(new Unjudged(
                        result.entry().name(),
                        "its composition facts cannot be read: "
                                + unreadable.code().token()));
            } else if (result.level().isEmpty()) {
                unjudged.add(new Unjudged(result.entry().name(), NO_LEVEL));
            }
        }
        return unjudged;
    }

    /** {@code CLOSURE} with its preset phase over packs that all have built facts and a level. */
    private static List<CompositionFault> closure(List<PackResult> results) {
        List<PackUnderResolution> set = new ArrayList<>();
        Map<String, List<String>> presetIds = new HashMap<>();
        for (PackResult result : results) {
            PackReadout.Built built = (PackReadout.Built) result.facts();
            ConfiguredPack configured =
                    configured(result.entry(), result.level().orElseThrow());
            set.add(built.underResolution(configured));
            presetIds.put(
                    configured.id().value(),
                    built.facts().presets().stream().map(Preset::id).toList());
        }
        return PackRulesets.closureFaults(
                set, pack -> presetIds.getOrDefault(pack.configured().id().value(), List.of()));
    }

    /** How the set configures a pack: the one place a pack's identity is taken, from the entry's name. */
    private static ConfiguredPack configured(PackSetEntry entry, PackLevel level) {
        return new ConfiguredPack(new PackIdentifier(entry.name()), level);
    }
}
