package ai.speckitty.charter.packs.infra.storage.rules;

import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.rules.FragmentRules;
import ai.speckitty.charter.packs.domain.rules.PackRulesets;
import ai.speckitty.charter.packs.domain.rules.ReferenceRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The storage edge's named compositions over the one read of a pack tree (#554, FR-002, S3, S6): what the tree holds
 * ({@link #holding()}), the registration's composition ({@link #registration()}), the manifest gate
 * ({@link #manifestMembership}) and the verdict of the read that the domain port {@code PackReading} returns
 * ({@link #verdict}). Nothing outside {@code packs.infra.storage} and the composition root names this class: a
 * registration and the validation edge see only the verdict, as the readout's {@link Rationale}.
 */
public final class StorageRulesets {

    private static final ModularRuleset<TreeRead> HOLDING = ModularRuleset.<TreeRead>emptyRules()
            .must(AssemblyRules.RULES)
            .must(NotHeldRules.RULES)
            .must(ReservedShardRules.RULES)
            .must(ManifestRules.RULES)
            .must(LinkRules.RULES);

    private static final ModularRuleset<TreeRead> REGISTRATION =
            HOLDING.must(PackRulesets.content().on(TreeRead::contents));

    /** The orphan prompt fragments of {@code content()}, which a registration lists after the pack's other faults. */
    private static final Rule<TreeRead> ORPHANS = FragmentRules.ORPHAN.on(TreeRead::contents);

    /**
     * The reference closure of {@code content()} (#355), which a verdict states among the store half's own faults, in the
     * order the load meets them: an asset's value-object breach (the store's) before the guard and the lifecycle it
     * takes with it (the closure's).
     */
    private static final Rule<TreeRead> REFERENCES = ReferenceRules.UNCLOSED.on(TreeRead::contents);

    /** Where the manifest stands in a pack. */
    private static final String MANIFEST = "pack-manifest.yaml";

    /**
     * The faults a registration lists last, in its order: the reserved shard, then a manifest that is not a regular file,
     * then the manifest's membership.
     */
    private static final List<Predicate<Reason>> LAST = List.of(
            reason -> StorageCodes.RESERVED_SHARD.equals(reason.code()),
            ManifestRules::statesNotRegular,
            reason -> StorageCodes.MANIFEST_MEMBERSHIP.equals(reason.code()));

    private StorageRulesets() {}

    /**
     * What the tree holds: the store's refusal of the pack as a whole, a {@code file_not_held} warning per file not held,
     * the reserved shard no registration wrote, something at the manifest's path that is not a regular file, and a
     * {@code pack_entry_linked} violation per link in the tree (D1).
     * Collect-all, over the census and the read's assembly fact.
     *
     * @return the composition
     */
    public static ModularRuleset<TreeRead> holding() {
        return HOLDING;
    }

    /**
     * What a registration judges beyond the read faults: {@link #holding()}, then the pack's consistency
     * ({@code PackRulesets.content()}) lifted onto the one read. Every rule of {@code content()} is a rule of this
     * composition, so what a registration finds, validate mode finds too.
     *
     * @return the composition
     */
    public static ModularRuleset<TreeRead> registration() {
        return REGISTRATION;
    }

    /**
     * The manifest gate (#110): the manifest's membership ({@link ManifestRules#membership}), a plain function, not a
     * member of a composition, since a composition is collect-all and the kernel has no combinator that runs a rule only
     * when the rules before it passed. {@link #verdict} runs it only when the read faults and {@link #registration()}
     * found no violation, as an artifact refused elsewhere would read as left out.
     *
     * @param read the one read
     * @return the membership reasons; empty when the manifest lists what the pack holds, or there is none
     */
    public static List<Reason> manifestMembership(TreeRead read) {
        return ManifestRules.membership(read);
    }

    /**
     * The storage edge's verdict of one read (S6): the read faults, then {@link #registration()}, then the store half's
     * own faults the two do not state ({@link AssemblyRules#held}), then the manifest gate when those pass, joined with
     * the kernel's {@link Rationale#and} and put in registration's wire order ({@link #inRegistrationOrder}).
     *
     * <p>Among the violations left in the order given, the read faults at the manifest and at the policy file
     * {@code mutability_policy} names follow the rule faults and the store's, and the orphan prompt fragments come last,
     * as the registration listed them (WP07 review MINOR-3). When the store could not read the named policy file, its
     * fault is stated once, in its words (R2 row 32), and the reader's own fault at that file is not; so is a prompt
     * fragment the store cannot read, at the step enhancement that names it (R2 row 19, as the contract places it),
     * and then nothing else is stated at the fragment. At a link's path only {@code pack_entry_linked} is stated: the
     * link is the cause of what else a read finds missing or irregular there (a {@code pack.yaml} or a manifest that is a
     * link), and the specific code wins (D1, R2).
     *
     * @param read the one read
     * @return the verdict
     */
    public static Rationale verdict(TreeRead read) {
        Optional<String> policy = AssemblyRules.unreadablePolicy(read);
        Set<String> trailing = new HashSet<>();
        trailing.add(MANIFEST);
        policy.ifPresent(trailing::add);
        List<Reason> readFaults = new ArrayList<>();
        List<Reason> trailingFaults = new ArrayList<>();
        for (Reason fault : read.readFaults()) {
            String file = fault.location().file();
            if (policy.filter(file::equals).isPresent()) {
                continue;
            }
            (trailing.contains(file) ? trailingFaults : readFaults).add(fault);
        }
        List<Reason> orphans = ORPHANS.apply(read);
        List<Reason> references = new ArrayList<>(REFERENCES.apply(read));
        List<Reason> rules = new ArrayList<>(registration().apply(read));
        rules.removeAll(orphans);
        rules.removeAll(references);
        List<Reason> stated = new ArrayList<>(readFaults);
        stated.addAll(trailingFaults);
        stated.addAll(rules);
        stated.addAll(references);
        stated.addAll(orphans);
        List<Reason> held = AssemblyRules.held(
                read,
                stated.stream()
                        .filter(reason -> reason.isFailing(Severity.VIOLATION))
                        .toList());
        // A prompt fragment whose read fault the store states at its enhancement: nothing else is stated at it.
        Set<String> fragments = AssemblyRules.unreadFragments(held);
        Predicate<Reason> kept = reason -> !(reason.isFailing(Severity.VIOLATION)
                && fragments.contains(reason.location().file()));
        readFaults.removeIf(kept.negate());
        trailingFaults.removeIf(kept.negate());
        rules.removeIf(kept.negate());
        references.removeIf(kept.negate());
        List<Reason> joined = new ArrayList<>(readFaults);
        joined.addAll(rules);
        List<Reason> storeFaults = held.stream()
                .filter(fault -> !trailing.contains(fault.location().file()))
                .toList();
        joined.addAll(inLoadOrder(read, references, storeFaults));
        joined.addAll(trailingFaults);
        held.stream()
                .filter(fault -> trailing.contains(fault.location().file()))
                .forEach(joined::add);
        joined.addAll(orphans);
        Rationale judged = new Rationale(atLinksOnlyTheLink(joined));
        Rationale gated = judged.isPassing() ? judged.and(new Rationale(manifestMembership(read))) : judged;
        return inRegistrationOrder(gated);
    }

    /** The violations, less any other than {@code pack_entry_linked} at a path a {@code pack_entry_linked} names. */
    private static List<Reason> atLinksOnlyTheLink(List<Reason> reasons) {
        Set<String> links = new HashSet<>();
        reasons.stream()
                .filter(reason -> StorageCodes.PACK_ENTRY_LINKED.equals(reason.code()))
                .forEach(reason -> links.add(reason.location().file()));
        return reasons.stream()
                .filter(reason -> StorageCodes.PACK_ENTRY_LINKED.equals(reason.code())
                        || !reason.isFailing(Severity.VIOLATION)
                        || !links.contains(reason.location().file()))
                .toList();
    }

    /**
     * The reference closure's findings and the store half's own faults in the order the load records them at their files
     * ({@code Assembly.Assembled#violations()}), so a cause is stated before what it takes with it. Each list keeps its
     * own order: a fault the load records in other words (a companion's, placed at the companion) takes the place of
     * the fault before it in its own list.
     */
    static List<Reason> inLoadOrder(TreeRead read, List<Reason> references, List<Reason> storeFaults) {
        if (!(read.contents().assembly() instanceof PackContents.Assembly.Assembled held)) {
            List<Reason> both = new ArrayList<>(references);
            both.addAll(storeFaults);
            return both;
        }
        List<PackElementViolation> recorded = held.violations();
        List<Reason> faults = new ArrayList<>(references);
        faults.addAll(storeFaults);
        int[] keys = new int[faults.size()];
        keyed(recorded, references, keys, 0);
        keyed(recorded, storeFaults, keys, references.size());
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < faults.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt(index -> keys[index]));
        return order.stream().map(faults::get).toList();
    }

    /** Each fault's place among the load's violations; an unmatched one takes the place of the one before it in its list. */
    private static void keyed(List<PackElementViolation> recorded, List<Reason> faults, int[] keys, int offset) {
        int carried = -1;
        for (int i = 0; i < faults.size(); i++) {
            Reason fault = faults.get(i);
            int found = -1;
            for (int j = 0; j < recorded.size() && found < 0; j++) {
                PackElementViolation violation = recorded.get(j);
                if (violation.file().equals(fault.location().file())
                        && violation.kind().equals(fault.code())
                        && violation.reason().equals(fault.detail())) {
                    found = j;
                }
            }
            carried = found >= 0 ? found : carried;
            keys[offset + i] = carried;
        }
    }

    /**
     * Registration's wire order of a verdict, so the API edge keeps the order it is given and names no storage code. The
     * violations: every graph fault ({@code drg_schema_error}) first, in path order; then the refusal of the pack as a
     * whole ({@code pack_unreadable} or the aggregate's, at {@code pack.yaml} or {@code pack}); then the read faults and
     * the rule faults in the order given (the reader's, then the composition's, the orphan fragments last); then the
     * reserved shard, the manifest that is not a regular file and the manifest's membership. The warnings: in the order
     * given, the files not held last, in path order. Any other reason keeps its place after them.
     *
     * @param verdict the joined verdict
     * @return the same reasons, in registration's order
     */
    static Rationale inRegistrationOrder(Rationale verdict) {
        List<Reason> graph = new ArrayList<>();
        List<Reason> whole = new ArrayList<>();
        List<Reason> violations = new ArrayList<>();
        List<Reason> storage = new ArrayList<>();
        List<Reason> warnings = new ArrayList<>();
        List<Reason> notHeld = new ArrayList<>();
        List<Reason> others = new ArrayList<>();
        for (Reason reason : verdict.details()) {
            if (reason.isFailing(Severity.VIOLATION)) {
                if (PackCodes.DRG_SCHEMA_ERROR.equals(reason.code())) {
                    graph.add(reason);
                } else if (AssemblyRules.statesRefusal(reason)) {
                    whole.add(reason);
                } else if (heldLast(reason)) {
                    storage.add(reason);
                } else {
                    violations.add(reason);
                }
            } else if (reason.isFailing(Severity.WARNING)) {
                (StorageCodes.FILE_NOT_HELD.equals(reason.code()) ? notHeld : warnings).add(reason);
            } else {
                others.add(reason);
            }
        }
        graph.sort(Comparator.comparing(reason -> reason.location().file()));
        storage.sort(Comparator.comparingInt(StorageRulesets::storageRank));
        notHeld.sort(Comparator.comparing(reason -> reason.location().file()));
        List<Reason> ordered = new ArrayList<>(graph);
        ordered.addAll(whole);
        ordered.addAll(violations);
        ordered.addAll(storage);
        ordered.addAll(warnings);
        ordered.addAll(notHeld);
        ordered.addAll(others);
        return new Rationale(ordered);
    }

    /** Whether a violation is one a registration lists after the pack's other faults: the reserved shard, the manifest's. */
    static boolean heldLast(Reason reason) {
        return storageRank(reason) < LAST.size();
    }

    /** The place of a storage fault among the last ones, by the reason the storage rule states; past them for any other. */
    static int storageRank(Reason reason) {
        for (int rank = 0; rank < LAST.size(); rank++) {
            if (LAST.get(rank).test(reason)) {
                return rank;
            }
        }
        return LAST.size();
    }
}
