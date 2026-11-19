package ai.speckitty.charter.packvalidation.set;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackReading;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.rules.CompositionRules;
import ai.speckitty.charter.packs.domain.rules.CompositionRules.CompositionFault;
import ai.speckitty.charter.packvalidation.PackCheck;
import ai.speckitty.charter.packvalidation.roundtrip.RoundTripping;
import ai.speckitty.charter.packvalidation.roundtrip.WholePackExpectation;
import ai.speckitty.charter.packvalidation.roundtrip.WholePackRoundTripRule;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.Check;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.PackResult;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.Skip;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.Unjudged;
import java.util.ArrayList;
import java.util.Collection;
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
 *   <li>Over the set as given (D5), {@code CLOSURE} with its preset phase ({@code PackRulesets.closure(presetIdsOf)}), on those facts. A pack's dependencies must be in the set. It runs only when every
 *       pack's facts are {@link PackReadout.Built} and every pack has a level; otherwise it is skipped and the verdict
 *       names the packs that stopped it (D3).
 *   <li>Per pack, when asked, the production round trip ({@link RoundTripping}), judged by {@link WholePackRoundTripRule}
 *       with no expected-red baseline ({@link WholePackExpectation#none()}). It runs whatever validate mode found: the two
 *       are independent checks.
 * </ol>
 *
 * <p>The faults of {@code CLOSURE} are taken from the fault collectors {@code PackRulesets.closure(presetIdsOf)} is built from ({@link CompositionRules#faults}, then {@link
 * CompositionRules#presetFaults}), which keep the packs each fault names; the ruleset's reasons are those faults'
 * reasons, in that order, so there is one judging path. No rule is defined here.
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
     */
    public PackSetVerdict checkWithRoundTrip(List<PackSetEntry> entries, RoundTripping roundTripping) {
        return run(entries, Optional.of(Objects.requireNonNull(roundTripping, "roundTripping")));
    }

    private PackSetVerdict run(List<PackSetEntry> entries, Optional<RoundTripping> roundTripping) {
        refuseEmptyOrRepeated(entries);
        List<PackResult> results = new ArrayList<>();
        for (PackSetEntry entry : entries) {
            PackCheck.Checked checked = check.check(entry.root());
            List<Reason> roundTrip =
                    roundTripping.map(flow -> roundTrip(flow, entry)).orElse(List.of());
            results.add(new PackResult(
                    entry,
                    checked.verdict(),
                    levelOf(entry, checked.readout().facts()),
                    checked.readout().facts(),
                    roundTrip));
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

    private static List<Reason> roundTrip(RoundTripping flow, PackSetEntry entry) {
        return new WholePackRoundTripRule(WholePackExpectation.none()).apply(flow.run(entry.root()));
    }

    /** The configured level, else the level the pack's {@code pack.yaml} declares as the composing read built it. */
    private static Optional<PackLevel> levelOf(PackSetEntry entry, PackReadout.Facts facts) {
        return entry.root()
                .configuredLevel()
                .or(() -> facts instanceof PackReadout.Built built
                        ? built.descriptor().declaredLevel()
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
        Map<PackUnderResolution, List<String>> presetIds = new HashMap<>();
        for (PackResult result : results) {
            PackReadout.Built built = (PackReadout.Built) result.facts();
            PackUnderResolution pack = new PackUnderResolution(
                    configured(result.entry(), result.level().orElseThrow()),
                    built.descriptor(),
                    built.facts().changed(),
                    built.facts().defined(),
                    Set.copyOf(built.facts().terms()));
            set.add(pack);
            presetIds.put(pack, built.facts().presets().stream().map(Preset::id).toList());
        }
        List<CompositionFault> faults = new ArrayList<>(CompositionRules.faults(set));
        faults.addAll(CompositionRules.presetFaults(set, pack -> presetIdsOf(presetIds, pack)));
        return faults;
    }

    private static Collection<String> presetIdsOf(
            Map<PackUnderResolution, List<String>> presetIds, PackUnderResolution pack) {
        return presetIds.getOrDefault(pack, List.of());
    }

    /** How the set configures a pack: the one place a pack's identity is taken, from the entry's name. */
    private static ConfiguredPack configured(PackSetEntry entry, PackLevel level) {
        return new ConfiguredPack(new PackIdentifier(entry.name()), level);
    }
}
