package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts.Companion;
import ai.speckitty.charter.packs.domain.StepEnhancementFault.Finding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        List<Finding> found = new ArrayList<>();
        for (Companion companion : facts.companions()) {
            content(companion, found);
        }
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
}
