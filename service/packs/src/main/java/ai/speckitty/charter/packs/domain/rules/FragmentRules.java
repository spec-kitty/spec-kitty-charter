package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts.Companion;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts.Edge;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts.Enhancement;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts.Entry;
import ai.speckitty.charter.packs.domain.StepEnhancementFault;
import ai.speckitty.charter.packs.domain.StepEnhancementFault.Finding;
import ai.speckitty.charter.packs.domain.StepHolding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The content rules of a pack's step enhancements and prompt fragments (#554, FR-001, T037; FR-004 to FR-006 of
 * ADR-0066): kernel rules over the facts of the one read ({@link PackContents}), each fault one {@link Reason} of
 * {@link PackCodes#PROMPT_FRAGMENT_INVALID} at the file the fault belongs to. They read no file and know no surface.
 *
 * <p>{@link #judge} is the pure function the rules state: it reads {@link StepEnhancementFacts} and answers
 * {@link Finding}s, each naming the file it belongs to, and names no wire code.
 *
 * <p>Fragment content (FR-004): {@link StepEnhancementFault#FRAGMENT_BLANK} and {@link
 * StepEnhancementFault#FRAGMENT_FRONTMATTER} are the pure statement of the rule. On both real paths the companion read
 * refuses those fragments first, through the closed {@code prompt-fragment} structure schema, so a pack meets them as
 * the companion read's fault; these two members stay unit-tested on facts. A companion the caller marked unreadable is
 * judged by no content rule, so a codec fault wins over a marker.
 *
 * <p>Structure (FR-005, D11): a slot id occurs at most once per enhancement; each entry's companion exists beside the
 * file; each companion is named once across the pack's enhancements of that directory; no fragment is an orphan. The
 * slot id's grammar is not judged here: a bad id is a schema violation the store refuses first, never a rule fault and
 * never {@code prompt_fragment_invalid}, so {@link StepEnhancementFault} has no grammar member. Path order is
 * {@link Precedence#comparePaths} whatever order the caller passes. An orphan is judged from the fragment facts of the
 * read, once: the census does not list an orphan as not held as well, and the fragments of a directory whose
 * enhancement the read refused are no orphans, since that enhancement's own fault is reported.
 *
 * <p>Identity and target (FR-006, D8, D9, D12, R-OVR, C-003): exactly one {@code enhances} edge, to an {@code
 * action:<type>/<step>}, bare or qualified; no {@code overrides} edge from the enhancement at all (an enhancement only
 * adds); the file's {@code <type>} directory equals the edge's, checked against the edge and never used as the
 * target; the id is unique across type directories; at least one entry. The edges key on the URN, so the files of one
 * id share them: the edge rules are judged once per id, at its first file in path order, a repeated file gets only
 * {@link StepEnhancementFault#STEM_REPEATED} for its identity, and when the {@code enhances} count is not one the
 * target and directory rules are skipped (the {@code overrides} rule is judged on its own). Whether the target step
 * exists is not judged here: target resolution across packs is a later mission's. The rules that read the edges are
 * not judged when a graph document could not be read ({@link GraphRules#graphsRead}).
 */
public final class FragmentRules {

    /** The id of a step action: {@code <type>/<step>}, each segment without whitespace or a further slash. */
    private static final Pattern STEP_ID = Pattern.compile("^[^/\\s]+/[^/\\s]+$");

    private static final Comparator<Finding> ORDER = Comparator.comparing(Finding::path, Precedence::comparePaths)
            .thenComparing(Finding::rule)
            .thenComparing(Finding::reason);

    /** The faults that read the pack's graph documents: not judged when a graph document could not be read. */
    private static final Set<StepEnhancementFault> EDGE_RULES = EnumSet.of(
            StepEnhancementFault.EDGE_COUNT,
            StepEnhancementFault.EDGE_TARGET_NOT_STEP,
            StepEnhancementFault.OVERRIDES_EDGE,
            StepEnhancementFault.DIRECTORY_MISMATCH);

    /**
     * Every fault of the pack's step enhancements and their companions but an orphan fragment, in {@link #judge}'s
     * order (path, then rule): one {@link PackCodes#PROMPT_FRAGMENT_INVALID} per fault, at its file. A fault of the
     * enhancement's binding (its schema, its companion's codec) is a read fault, named before these.
     */
    public static final Rule<PackContents> ENHANCEMENT = FragmentRules::enhancementFaults;

    /**
     * A prompt fragment no entry of an enhancement names (D11): one {@link PackCodes#PROMPT_FRAGMENT_INVALID} per
     * orphan, at the fragment, in path order.
     */
    public static final Rule<PackContents> ORPHAN = FragmentRules::orphanFaults;

    /** Every fragment rule, in this class's order: the orphans last, as a registration lists them. */
    public static final ModularRuleset<PackContents> RULES =
            ModularRuleset.<PackContents>emptyRules().must(ENHANCEMENT).must(ORPHAN);

    private FragmentRules() {}

    static List<Reason> enhancementFaults(PackContents contents) {
        return reasons(contents, false);
    }

    static List<Reason> orphanFaults(PackContents contents) {
        return reasons(contents, true);
    }

    /** The faults of the read's facts, the edge rules only when every graph document was read, as reasons. */
    static List<Reason> reasons(PackContents contents, boolean orphans) {
        List<Reason> findings = new ArrayList<>();
        for (Finding fault : judge(contents.stepEnhancements(), GraphRules.graphsRead(contents))) {
            if ((fault.rule() == StepEnhancementFault.COMPANION_ORPHAN) == orphans) {
                findings.add(Findings.at(
                        PackCodes.PROMPT_FRAGMENT_INVALID,
                        fault.path(),
                        List.of(),
                        Paths.origin(contents.root().name(), fault.path()) + ": " + fault.reason()));
            }
        }
        return findings;
    }

    /**
     * Judges the facts of one pack, the rules that read the pack's edges only when every graph document was read: a
     * graph document that could not be read is refused on its own, and an enhancement would otherwise lack an edge the
     * pack may well hold. The content rules state each fault as a finding; the store half, which the composing read
     * answers from (S7), records the same faults on the aggregate.
     *
     * @param facts the pack's facts
     * @param edgesRead whether every graph document of the pack was read
     * @return every fault found, ordered by pack-relative path and then rule order
     */
    public static List<Finding> judge(StepEnhancementFacts facts, boolean edgesRead) {
        return judge(facts).stream()
                .filter(fault -> edgesRead || !EDGE_RULES.contains(fault.rule()))
                .toList();
    }

    /**
     * Judges the facts of one pack, every rule.
     *
     * @param facts the pack's facts
     * @return every fault found, ordered by pack-relative path and then rule order
     */
    public static List<Finding> judge(StepEnhancementFacts facts) {
        List<Enhancement> ordered = new ArrayList<>(facts.enhancements());
        ordered.sort(Comparator.comparing(Enhancement::path, Precedence::comparePaths));
        List<Finding> found = new ArrayList<>();
        for (Companion companion : facts.companions()) {
            content(companion, found);
        }
        structure(facts, ordered, found);
        identity(facts, ordered, found);
        return found.stream().distinct().sorted(ORDER).toList();
    }

    private static void content(Companion companion, List<Finding> found) {
        if (companion.unreadable()) {
            return;
        }
        String path = companion.path();
        if (companion.blank()) {
            found.add(new Finding(StepEnhancementFault.FRAGMENT_BLANK, path, "the fragment is blank"));
        }
        if (companion.hasFrontmatter()) {
            found.add(new Finding(StepEnhancementFault.FRAGMENT_FRONTMATTER, path, "the fragment has frontmatter"));
        }
        if (PromptSlots.containsMarker(companion.canonicalText())) {
            found.add(new Finding(
                    StepEnhancementFault.FRAGMENT_HAS_MARKER,
                    path,
                    "the fragment holds a slot marker (any marker counts) or leaves a comment open"));
        }
    }

    private static void structure(StepEnhancementFacts facts, List<Enhancement> ordered, List<Finding> found) {
        Map<String, Companion> existing = new HashMap<>();
        for (Companion companion : facts.companions()) {
            existing.put(companion.path(), companion);
        }
        Set<String> named = new HashSet<>();
        for (Enhancement enhancement : ordered) {
            slotsRepeated(enhancement, found);
            for (Entry entry : enhancement.entries()) {
                String companion = enhancement.companionPath(entry);
                if (!existing.containsKey(companion)) {
                    found.add(new Finding(
                            StepEnhancementFault.COMPANION_MISSING,
                            enhancement.path(),
                            "the companion " + companion + " does not exist"));
                }
                if (!named.add(companion)) {
                    found.add(new Finding(
                            StepEnhancementFault.COMPANION_SHARED,
                            enhancement.path(),
                            "the companion " + companion + " is named more than once"));
                }
            }
        }
        orphans(facts, named, found);
    }

    private static void slotsRepeated(Enhancement enhancement, List<Finding> found) {
        Set<String> seen = new HashSet<>();
        for (Entry entry : enhancement.entries()) {
            if (!seen.add(entry.slot())) {
                found.add(new Finding(
                        StepEnhancementFault.SLOT_REPEATED,
                        enhancement.path(),
                        "the slot " + entry.slot() + " is filled more than once"));
            }
        }
    }

    /** A fragment named by no entry, unless its directory holds an enhancement file the caller could not judge (A4). */
    private static void orphans(StepEnhancementFacts facts, Set<String> named, List<Finding> found) {
        Set<String> unjudged = new HashSet<>();
        for (String file : facts.unparsedEnhancementFiles()) {
            unjudged.add(directoryOf(file));
        }
        for (String fragment : facts.fragmentFiles()) {
            if (!named.contains(fragment) && !unjudged.contains(directoryOf(fragment))) {
                found.add(new Finding(
                        StepEnhancementFault.COMPANION_ORPHAN,
                        fragment,
                        "no entry of an enhancement names this fragment"));
            }
        }
    }

    private static String directoryOf(String path) {
        return path.substring(0, path.lastIndexOf('/') + 1);
    }

    private static void identity(StepEnhancementFacts facts, List<Enhancement> ordered, List<Finding> found) {
        Set<String> seen = new HashSet<>();
        for (Enhancement enhancement : ordered) {
            if (seen.add(enhancement.id())) {
                if (enhancement.entries().isEmpty()) {
                    found.add(new Finding(
                            StepEnhancementFault.NO_FRAGMENTS,
                            enhancement.path(),
                            "the enhancement has no prompt_fragments entry"));
                }
                edges(enhancement, facts.edges(), found);
            } else {
                found.add(new Finding(
                        StepEnhancementFault.STEM_REPEATED,
                        enhancement.path(),
                        "the id " + enhancement.id() + " is already used by an earlier enhancement of the pack"));
            }
        }
    }

    private static void edges(Enhancement enhancement, List<Edge> all, List<Finding> found) {
        String urn = StepEnhancementFacts.URN_PREFIX + enhancement.id();
        List<Edge> enhances = new ArrayList<>();
        for (Edge edge : all) {
            if (!edge.source().equals(urn)) {
                continue;
            }
            if (edge.relation() == RelationType.ENHANCES) {
                enhances.add(edge);
            } else if (edge.relation() == RelationType.OVERRIDES) {
                found.add(new Finding(
                        StepEnhancementFault.OVERRIDES_EDGE,
                        enhancement.path(),
                        "the enhancement overrides " + written(edge) + "; an enhancement only adds"));
            }
        }
        if (enhances.size() == 1) {
            target(enhancement, enhances.getFirst(), found);
        } else {
            found.add(new Finding(
                    StepEnhancementFault.EDGE_COUNT,
                    enhancement.path(),
                    "the enhancement has " + enhances.size() + " enhances edges, but needs exactly one"));
        }
    }

    private static void target(Enhancement enhancement, Edge edge, List<Finding> found) {
        // StepHolding.missionTypeOf alone would accept action:t/a/b, so the segments are still checked.
        Optional<String> type = Urn.parse(edge.target())
                .filter(urn -> urn.kind() == NodeKind.ACTION
                        && STEP_ID.matcher(urn.id()).matches())
                .flatMap(StepHolding::missionTypeOf)
                .map(Urn::id);
        if (type.isEmpty()) {
            found.add(new Finding(
                    StepEnhancementFault.EDGE_TARGET_NOT_STEP,
                    enhancement.path(),
                    "the enhances target " + written(edge) + " is not an action:<type>/<step>"));
        } else if (!type.get().equals(enhancement.typeDirectory())) {
            found.add(new Finding(
                    StepEnhancementFault.DIRECTORY_MISMATCH,
                    enhancement.path(),
                    "the file sits in the " + enhancement.typeDirectory() + " directory, but the enhances target "
                            + written(edge) + " is of type " + type.get()));
        }
    }

    private static String written(Edge edge) {
        return edge.qualifier().map(pack -> pack + "::").orElse("") + edge.target();
    }
}
