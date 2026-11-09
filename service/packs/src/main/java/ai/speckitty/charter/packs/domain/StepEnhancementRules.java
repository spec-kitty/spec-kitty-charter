package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts.Companion;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts.Enhancement;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts.Entry;
import ai.speckitty.charter.packs.domain.StepEnhancementFault.Finding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Judges one pack's step enhancements, their companions and its graph edges (FR-004, FR-005, FR-006). Pure: it reads
 * {@link StepEnhancementFacts} and answers {@link Finding}s, each naming the file it belongs to. It names no wire code
 * and touches no store; the caller maps each fault.
 *
 * <p>Fragment content (FR-004): {@link StepEnhancementFault#FRAGMENT_BLANK} and {@link
 * StepEnhancementFault#FRAGMENT_FRONTMATTER} are the pure statement of the rule. On both real paths the companion read
 * refuses those fragments first, through the closed {@code prompt-fragment} structure schema, so a pack meets them as
 * the companion read's fault; these two members stay unit-tested on facts. A companion the caller marked unreadable is
 * judged by no content rule.
 *
 * <p>Structure (FR-005, D11): a slot id occurs at most once per enhancement; each entry's companion exists beside the
 * file; each companion is named once across the pack's enhancements of that directory; no fragment is an orphan. The
 * slot id's grammar is not judged here: a bad id is a schema violation the store refuses first, never a rule fault and
 * never {@code prompt_fragment_invalid}, so {@link StepEnhancementFault} has no grammar member. Path order is
 * {@code PackPolicy.comparePaths} whatever order the caller passes.
 */
public final class StepEnhancementRules {

    private static final Comparator<Finding> ORDER = Comparator.comparing(Finding::path, PackPolicy::comparePaths)
            .thenComparing(Finding::rule)
            .thenComparing(Finding::reason);

    private StepEnhancementRules() {}

    /**
     * Judges the facts of one pack.
     *
     * @param facts the pack's facts
     * @return every fault found, ordered by pack-relative path and then rule order
     */
    public static List<Finding> judge(StepEnhancementFacts facts) {
        List<Enhancement> ordered = new ArrayList<>(facts.enhancements());
        ordered.sort(Comparator.comparing(Enhancement::path, PackPolicy::comparePaths));
        List<Finding> found = new ArrayList<>();
        for (Companion companion : facts.companions()) {
            content(companion, found);
        }
        structure(facts, ordered, found);
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
                    "the fragment holds a slot marker (any marker counts)"));
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
}
