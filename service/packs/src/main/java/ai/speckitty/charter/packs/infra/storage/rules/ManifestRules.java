package ai.speckitty.charter.packs.infra.storage.rules;

import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.rules.Findings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The storage rules of the pack manifest (#110, ADR-0042, #554 S3, T038): what stands at {@value #FILE} is a regular
 * file, since a registration writes the manifest there; and, as a plain function a surface runs only when nothing else
 * failed ({@link #membership}), the manifest lists what the pack holds. Both read the census's manifest facts
 * ({@link TreeCensus.Manifest}): the lists the manifest declares and the lists the reader derived from what the pack
 * holds. Neither derives anything.
 */
public final class ManifestRules {

    /** The manifest's pack-relative path. */
    public static final String FILE = "pack-manifest.yaml";

    /** Why something at {@value #FILE} that is not a regular file is refused (R2 row 30). */
    static final String NOT_REGULAR = "the manifest is not a regular file";

    private static final Reason NOT_REGULAR_REASON = at(StorageCodes.UNREADABLE, NOT_REGULAR);

    /**
     * One {@link StorageCodes#UNREADABLE} at {@value #FILE} when something stands there that is not a regular file (a
     * directory, a link: the write would follow it).
     */
    public static final Rule<TreeRead> REGULAR = ManifestRules::notRegular;

    /** Every manifest rule of the holding composition, in this class's order; membership is the gate's, not one. */
    public static final ModularRuleset<TreeRead> RULES =
            ModularRuleset.<TreeRead>emptyRules().must(REGULAR);

    private ManifestRules() {}

    static List<Reason> notRegular(TreeRead read) {
        TreeCensus.Manifest manifest = read.census().manifest();
        if (!manifest.present() || manifest.regular()) {
            return List.of();
        }
        return List.of(NOT_REGULAR_REASON);
    }

    /**
     * Whether a reason is the one {@link #REGULAR} states.
     *
     * @param reason a reason
     * @return true for a manifest that is not a regular file
     */
    static boolean statesNotRegular(Reason reason) {
        return NOT_REGULAR_REASON.equals(reason);
    }

    /**
     * The manifest's membership (#110, R2 row 29): one {@link StorageCodes#MANIFEST_MEMBERSHIP} at {@value #FILE} per
     * difference between the lists a regular manifest declares and the lists derived from what the pack holds
     * ({@link #mismatch}); none for a pack with no manifest, or whose manifest could not be read. A plain function, not a
     * rule: a surface runs it only when nothing else failed, as an artifact refused elsewhere would read as left out.
     *
     * @param read the one read
     * @return the reasons; empty when the lists hold the same entries
     */
    public static List<Reason> membership(TreeRead read) {
        TreeCensus.Manifest manifest = read.census().manifest();
        if (!manifest.regular()
                || manifest.listed().isEmpty()
                || manifest.derived().isEmpty()) {
            return List.of();
        }
        return mismatch(manifest.listed().get(), manifest.derived().get()).stream()
                .map(reason -> at(StorageCodes.MANIFEST_MEMBERSHIP, reason))
                .toList();
    }

    /**
     * Each difference between a manifest's lists and the derived ones, as a reason: first the listed entries the pack
     * does not hold, then the held entries the manifest leaves out, each in the order of its own list. A constituent is
     * compared as {@code <kind>:<id> at <path>}, a preset entry as {@code <id> at <path>}; the order of the entries and
     * every derived field are not compared. An entry the manifest lists more than once is a reason of its own, after
     * the unheld ones.
     *
     * @param listed the manifest's lists as read
     * @param derived the derived lists of the same pack
     * @return the reasons; empty when the lists hold the same entries
     */
    public static List<String> mismatch(TreeCensus.Entries listed, TreeCensus.Entries derived) {
        List<String> listedConstituents = listed.constituents();
        Set<String> derivedConstituents = new LinkedHashSet<>(derived.constituents());
        List<String> listedPresets = listed.presets();
        Set<String> derivedPresets = new LinkedHashSet<>(derived.presets());
        List<String> out = new ArrayList<>();
        distinct(listedConstituents)
                .filter(entry -> !derivedConstituents.contains(entry))
                .forEach(entry -> out.add("lists constituent " + entry + ", which the pack does not hold"));
        distinct(listedPresets)
                .filter(entry -> !derivedPresets.contains(entry))
                .forEach(entry -> out.add("lists preset " + entry + ", which the pack does not hold"));
        repeated(listedConstituents, "constituent", out);
        repeated(listedPresets, "preset", out);
        derivedConstituents.stream()
                .filter(entry -> !listedConstituents.contains(entry))
                .forEach(entry -> out.add("leaves out constituent " + entry + ", which the pack holds"));
        derivedPresets.stream()
                .filter(entry -> !listedPresets.contains(entry))
                .forEach(entry -> out.add("leaves out preset " + entry + ", which the pack holds"));
        return List.copyOf(out);
    }

    private static Stream<String> distinct(List<String> entries) {
        return new LinkedHashSet<>(entries).stream();
    }

    /** One reason per entry the manifest lists more than once, in the order of its first listing. */
    private static void repeated(List<String> entries, String what, List<String> out) {
        Map<String, Long> counts =
                entries.stream().collect(Collectors.groupingBy(e -> e, LinkedHashMap::new, Collectors.counting()));
        counts.forEach((entry, count) -> {
            if (count > 1) {
                out.add("lists " + what + " " + entry + (count == 2 ? " twice" : " " + count + " times"));
            }
        });
    }

    /** A failing reason at the manifest, in the one wording ({@link PackCodes#detail}). */
    private static Reason at(ReasonCode code, String reason) {
        return Findings.at(code, FILE, List.of(), PackCodes.detail(FILE, reason));
    }
}
