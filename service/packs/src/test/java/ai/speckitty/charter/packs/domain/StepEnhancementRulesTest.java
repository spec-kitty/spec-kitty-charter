package ai.speckitty.charter.packs.domain;

import static ai.speckitty.charter.packs.domain.StepEnhancementFault.COMPANION_MISSING;
import static ai.speckitty.charter.packs.domain.StepEnhancementFault.COMPANION_ORPHAN;
import static ai.speckitty.charter.packs.domain.StepEnhancementFault.COMPANION_SHARED;
import static ai.speckitty.charter.packs.domain.StepEnhancementFault.DIRECTORY_MISMATCH;
import static ai.speckitty.charter.packs.domain.StepEnhancementFault.EDGE_COUNT;
import static ai.speckitty.charter.packs.domain.StepEnhancementFault.EDGE_TARGET_NOT_STEP;
import static ai.speckitty.charter.packs.domain.StepEnhancementFault.FRAGMENT_BLANK;
import static ai.speckitty.charter.packs.domain.StepEnhancementFault.FRAGMENT_FRONTMATTER;
import static ai.speckitty.charter.packs.domain.StepEnhancementFault.FRAGMENT_HAS_MARKER;
import static ai.speckitty.charter.packs.domain.StepEnhancementFault.NO_FRAGMENTS;
import static ai.speckitty.charter.packs.domain.StepEnhancementFault.OVERRIDES_EDGE;
import static ai.speckitty.charter.packs.domain.StepEnhancementFault.SLOT_REPEATED;
import static ai.speckitty.charter.packs.domain.StepEnhancementFault.STEM_REPEATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts.Companion;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts.Edge;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts.Enhancement;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts.Entry;
import ai.speckitty.charter.packs.domain.StepEnhancementFault.Finding;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The step enhancement and fragment rules (FR-004 to FR-006, SC-002), on in-memory facts. Every rule has a failing
 * fixture and a passing twin that differ only in the fault.
 *
 * <p>Not cases here, because the store refuses them as schema violations before any rule runs (operator ruling A1,
 * FR-005): an id that differs from its file stem, an entry whose {@code slot} breaks {@code <namespace>:<name>}, and
 * {@code prompt_fragments: []} (`minItems`). WP04 pins that side (the T016 scenario and the T019 precedence test).
 * This test pins the {@code prompt_fragment_invalid} side of FR-005: a repeated slot, a missing companion and an
 * orphan.
 */
class StepEnhancementRulesTest {

    private static final String PLAN = "software-dev";

    /** One pack's facts, built up from valid pairs so a twin and its fixture differ in one line. */
    private static final class PackFixture {
        private final List<Enhancement> enhancements = new ArrayList<>();
        private final List<Companion> companions = new ArrayList<>();
        private final Set<String> fragmentFiles = new LinkedHashSet<>();
        private final Set<String> unparsed = new LinkedHashSet<>();
        private final List<Edge> edges = new ArrayList<>();

        static String path(String type, String id) {
            return "missions/" + type + "/enhancements/" + id + ".step-enhancement.yaml";
        }

        static String fragmentPath(String type, String name) {
            return "missions/" + type + "/enhancements/" + name;
        }

        /** A valid enhancement of {@code type}: one slot, its own companion, one qualified edge to the step. */
        PackFixture valid(String type, String id, String step) {
            return enhancement(type, id, new Entry("tone:style", id + ".prompt-fragment.md"))
                    .fragment(type, id + ".prompt-fragment.md", "Be brief.\n")
                    .enhances(id, "base", "action:" + type + "/" + step);
        }

        PackFixture enhancement(String type, String id, Entry... entries) {
            enhancements.add(new Enhancement(path(type, id), type, id, List.of(entries)));
            return this;
        }

        PackFixture fragment(String type, String name, String text) {
            return fragment(Companion.read(fragmentPath(type, name), false, false, text));
        }

        PackFixture fragment(Companion companion) {
            companions.add(companion);
            fragmentFiles.add(companion.path());
            return this;
        }

        PackFixture orphan(String type, String name) {
            fragmentFiles.add(fragmentPath(type, name));
            return this;
        }

        PackFixture unparsed(String type, String id) {
            unparsed.add(path(type, id));
            return this;
        }

        PackFixture overrides(String id, String target) {
            edges.add(new Edge(RelationType.OVERRIDES, StepEnhancementFacts.URN_PREFIX + id, target, Optional.empty()));
            return this;
        }

        PackFixture edge(Edge edge) {
            edges.add(edge);
            return this;
        }

        PackFixture enhances(String id, String qualifier, String target) {
            edges.add(new Edge(
                    RelationType.ENHANCES, StepEnhancementFacts.URN_PREFIX + id, target, qualifier(qualifier)));
            return this;
        }

        private static Optional<String> qualifier(String qualifier) {
            return qualifier.isEmpty() ? Optional.empty() : Optional.of(qualifier);
        }

        StepEnhancementFacts facts() {
            return new StepEnhancementFacts(enhancements, companions, fragmentFiles, unparsed, edges);
        }
    }

    private static List<Finding> judge(PackFixture pack) {
        return StepEnhancementRules.judge(pack.facts());
    }

    private static void assertOnly(List<Finding> found, StepEnhancementFault rule, String path) {
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().rule()).isEqualTo(rule);
        assertThat(found.getFirst().path()).isEqualTo(path);
        assertThat(found.getFirst().reason()).isNotBlank();
    }

    @Test
    void aValidPackAnswersNoFault() {
        PackFixture pack = new PackFixture().valid(PLAN, "brief", "plan");

        assertThat(judge(pack)).isEmpty();
    }

    @Nested
    class FragmentContent {

        private static final String FRAGMENT = PackFixture.fragmentPath(PLAN, "brief.prompt-fragment.md");

        private PackFixture packWith(Companion companion) {
            PackFixture pack = new PackFixture();
            pack.enhancement(PLAN, "brief", new Entry("tone:style", "brief.prompt-fragment.md"))
                    .enhances("brief", "base", "action:software-dev/plan")
                    .fragment(companion);
            return pack;
        }

        private Companion text(String text) {
            return Companion.read(FRAGMENT, false, false, text);
        }

        @Test
        void aBlankFragmentIsRefusedAtTheFragmentAndItsNonBlankTwinIsNot() {
            Companion blank = Companion.read(FRAGMENT, false, true, "");

            assertOnly(judge(packWith(blank)), FRAGMENT_BLANK, FRAGMENT);
            assertThat(judge(packWith(text("Be brief.\n")))).isEmpty();
        }

        @Test
        void aFragmentWithFrontmatterIsRefusedAtTheFragmentAndItsPlainTwinIsNot() {
            Companion framed = Companion.read(FRAGMENT, true, false, "---\nk: v\n---\n\nBe brief.\n");

            assertOnly(judge(packWith(framed)), FRAGMENT_FRONTMATTER, FRAGMENT);
            assertThat(judge(packWith(text("Be brief.\n")))).isEmpty();
        }

        @Test
        void aFragmentWithASlotMarkerIsRefusedAtTheFragmentAndItsTwinWithoutOneIsNot() {
            assertOnly(judge(packWith(text("Be <!-- x:y:start --> brief.\n"))), FRAGMENT_HAS_MARKER, FRAGMENT);
            // an unclosed comment would swallow the base prompt once rendered; a closed one is plain text
            assertOnly(judge(packWith(text("Use the plan. <!-- \n"))), FRAGMENT_HAS_MARKER, FRAGMENT);
            assertThat(judge(packWith(text("Use the plan. <!-- note -->\n")))).isEmpty();
            assertThat(judge(packWith(text("Be brief.\n")))).isEmpty();
        }

        @Test
        void aMarkerOfAnInvalidIdStillCounts() {
            assertOnly(judge(packWith(text("Be brief. <!-- note: see x:start -->\n"))), FRAGMENT_HAS_MARKER, FRAGMENT);
            assertThat(judge(packWith(text("Be brief. <!-- decision_id: x -->\n"))))
                    .isEmpty();
        }

        /** d7: any marker counts, a fenced one too. A later exemption has to be a visible decision: this test breaks. */
        @Test
        void aMarkerInsideAFencedCodeBlockCounts() {
            String fenced = "Example:\n\n```\n<!-- x:y:start -->\n```\n";

            assertOnly(judge(packWith(text(fenced))), FRAGMENT_HAS_MARKER, FRAGMENT);
            assertThat(judge(packWith(text("Example:\n\n```\nplain\n```\n")))).isEmpty();
        }

        @Test
        void aFragmentThatNamesAnArtifactInProseHasNoFault() {
            // d3: prose naming an artifact is text, never a reference; no rule reads it.
            assertThat(judge(packWith(text("Follow tactic:x and action:other/plan.\n"))))
                    .isEmpty();
        }
    }

    @Nested
    class Structure {

        private static final String BRIEF = PackFixture.path(PLAN, "brief");
        private static final String FIRST = "first.prompt-fragment.md";
        private static final String SECOND = "second.prompt-fragment.md";

        /** An enhancement {@code brief} with two entries and both companions present. */
        private PackFixture twoEntries(String firstSlot, String secondSlot) {
            return new PackFixture()
                    .enhancement(PLAN, "brief", new Entry(firstSlot, FIRST), new Entry(secondSlot, SECOND))
                    .enhances("brief", "base", "action:software-dev/plan")
                    .fragment(PLAN, FIRST, "One.\n")
                    .fragment(PLAN, SECOND, "Two.\n");
        }

        @Test
        void aSlotNamedTwiceInOneEnhancementIsRefusedAtTheEnhancementAndDistinctSlotsAreNot() {
            assertOnly(judge(twoEntries("tone:style", "tone:style")), SLOT_REPEATED, BRIEF);
            assertThat(judge(twoEntries("tone:style", "tone:other"))).isEmpty();
        }

        @Test
        void anEntryWhoseCompanionIsMissingIsRefusedAtTheEnhancementAndItsTwinIsNot() {
            PackFixture missing = new PackFixture()
                    .enhancement(PLAN, "brief", new Entry("tone:style", FIRST))
                    .enhances("brief", "base", "action:software-dev/plan");
            PackFixture present = new PackFixture()
                    .enhancement(PLAN, "brief", new Entry("tone:style", FIRST))
                    .enhances("brief", "base", "action:software-dev/plan")
                    .fragment(PLAN, FIRST, "One.\n");

            List<Finding> found = judge(missing);

            assertOnly(found, COMPANION_MISSING, BRIEF);
            assertThat(found.getFirst().reason()).contains(FIRST);
            assertThat(judge(present)).isEmpty();
        }

        @Test
        void oneEnhancementNamingAPathInTwoEntriesIsRefusedAtTheEnhancementAndItsTwinIsNot() {
            PackFixture shared = new PackFixture()
                    .enhancement(PLAN, "brief", new Entry("tone:style", FIRST), new Entry("tone:other", FIRST))
                    .enhances("brief", "base", "action:software-dev/plan")
                    .fragment(PLAN, FIRST, "One.\n");

            assertOnly(judge(shared), COMPANION_SHARED, BRIEF);
            assertThat(judge(twoEntries("tone:style", "tone:other"))).isEmpty();
        }

        @Test
        void twoEnhancementsNamingOneCompanionAreRefusedAtTheSecondInPathOrderAndTheirTwinIsNot() {
            PackFixture shared = new PackFixture()
                    .valid(PLAN, "alpha", "plan")
                    .enhancement(PLAN, "beta", new Entry("tone:style", "alpha.prompt-fragment.md"))
                    .enhances("beta", "base", "action:software-dev/plan");
            PackFixture own = new PackFixture().valid(PLAN, "alpha", "plan").valid(PLAN, "beta", "plan");

            assertOnly(judge(shared), COMPANION_SHARED, PackFixture.path(PLAN, "beta"));
            assertThat(judge(own)).isEmpty();
        }

        @Test
        void anOrphanBesideAValidPairIsRefusedAtTheFragmentAndTheSamePackWithoutItIsNot() {
            PackFixture pack = new PackFixture().valid(PLAN, "brief", "plan");
            String stray = PackFixture.fragmentPath(PLAN, "stray.prompt-fragment.md");

            assertThat(judge(pack)).isEmpty();
            assertOnly(judge(pack.orphan(PLAN, "stray.prompt-fragment.md")), COMPANION_ORPHAN, stray);
        }

        @Test
        void aFragmentBesideARefusedEnhancementIsNoOrphanAndItsTwinBesideAParsedOneIs() {
            String stray = PackFixture.fragmentPath(PLAN, "stray.prompt-fragment.md");
            PackFixture refused = new PackFixture()
                    .valid(PLAN, "good", "plan")
                    .unparsed(PLAN, "refused")
                    .orphan(PLAN, "stray.prompt-fragment.md");
            PackFixture parsed = new PackFixture().valid(PLAN, "good", "plan").orphan(PLAN, "stray.prompt-fragment.md");

            assertThat(judge(refused)).isEmpty();
            assertOnly(judge(parsed), COMPANION_ORPHAN, stray);
        }

        /** New coverage: a rule fault on a held enhancement does not excuse an orphan (only an unparsed or withheld one does). */
        @Test
        void anOrphanBesideAHeldEnhancementWithARuleFaultIsStillAnOrphan() {
            PackFixture pack = new PackFixture()
                    .valid(PLAN, "brief", "plan")
                    .enhances("brief", "base", "action:software-dev/tasks")
                    .orphan(PLAN, "a-stray.prompt-fragment.md");

            assertThat(judge(pack))
                    .extracting(Finding::rule, Finding::path)
                    .containsExactly(
                            tuple(COMPANION_ORPHAN, PackFixture.fragmentPath(PLAN, "a-stray.prompt-fragment.md")),
                            tuple(EDGE_COUNT, BRIEF));
        }

        @Test
        void aRefusedEnhancementInAnotherDirectoryDoesNotExcuseAnOrphan() {
            PackFixture elsewhere = new PackFixture()
                    .valid(PLAN, "good", "plan")
                    .unparsed("research", "refused")
                    .orphan(PLAN, "stray.prompt-fragment.md");

            assertOnly(judge(elsewhere), COMPANION_ORPHAN, PackFixture.fragmentPath(PLAN, "stray.prompt-fragment.md"));
        }

        @Test
        void anUnreadableCompanionAnswersNoFaultAndTheSameEntryWithTheCompanionMissingAnswersMissing() {
            String path = PackFixture.fragmentPath(PLAN, FIRST);
            PackFixture unreadable = new PackFixture()
                    .enhancement(PLAN, "brief", new Entry("tone:style", FIRST))
                    .enhances("brief", "base", "action:software-dev/plan")
                    .fragment(Companion.unreadable(path));
            PackFixture missing = new PackFixture()
                    .enhancement(PLAN, "brief", new Entry("tone:style", FIRST))
                    .enhances("brief", "base", "action:software-dev/plan");

            assertThat(judge(unreadable)).isEmpty();
            assertOnly(judge(missing), COMPANION_MISSING, BRIEF);
        }
    }

    @Nested
    class IdentityAndTarget {

        private static final String BRIEF = PackFixture.path(PLAN, "brief");
        private static final String STEP = "action:software-dev/plan";

        /** An enhancement {@code brief} with its companion and no edge yet. */
        private PackFixture noEdge() {
            return new PackFixture()
                    .enhancement(PLAN, "brief", new Entry("tone:style", "brief.prompt-fragment.md"))
                    .fragment(PLAN, "brief.prompt-fragment.md", "Be brief.\n");
        }

        @Test
        void noEnhancesEdgeIsRefusedAndOneEdgeIsNot() {
            assertOnly(judge(noEdge()), EDGE_COUNT, BRIEF);
            assertThat(judge(noEdge().enhances("brief", "base", STEP))).isEmpty();
        }

        @Test
        void twoEnhancesEdgesAreRefusedOnceAndOneEdgeIsNot() {
            PackFixture two =
                    noEdge().enhances("brief", "base", STEP).enhances("brief", "base", "action:software-dev/tasks");

            assertOnly(judge(two), EDGE_COUNT, BRIEF);
            assertThat(judge(noEdge().enhances("brief", "base", STEP))).isEmpty();
        }

        @Test
        void aTargetThatIsNotAnActionStepIsRefusedAndAnActionTargetIsNot() {
            // not an action, an action without a step, an action with a further segment, a step with a blank segment
            for (String target : List.of("tactic:x", "action:software-dev", "action:t/a/b", "action:software-dev/ x")) {
                assertOnly(judge(noEdge().enhances("brief", "base", target)), EDGE_TARGET_NOT_STEP, BRIEF);
            }
            assertThat(judge(noEdge().enhances("brief", "base", STEP))).isEmpty();
        }

        @Test
        void anIdThatBeginsAnotherIdKeepsItsOwnEdges() {
            // brief-x begins with the URN of brief: an edge is brief's only when its source is exactly brief's URN
            PackFixture pack = new PackFixture().valid(PLAN, "brief", "plan").valid(PLAN, "brief-x", "tasks");

            assertThat(judge(pack)).isEmpty();
        }

        @Test
        void aTargetOfAnotherTypeThanTheDirectoryIsRefusedAndTheMatchingTypeIsNot() {
            assertOnly(judge(noEdge().enhances("brief", "base", "action:other/plan")), DIRECTORY_MISMATCH, BRIEF);
            assertThat(judge(noEdge().enhances("brief", "base", STEP))).isEmpty();
        }

        @Test
        void theTargetIsReadFromTheEdgeNeverFromTheDirectory() {
            // C-003: a directory named after another type does not become the target; the edge is checked against it.
            PackFixture pack = new PackFixture()
                    .enhancement("other", "brief", new Entry("tone:style", "brief.prompt-fragment.md"))
                    .fragment("other", "brief.prompt-fragment.md", "Be brief.\n")
                    .enhances("brief", "base", STEP);

            assertOnly(judge(pack), DIRECTORY_MISMATCH, PackFixture.path("other", "brief"));
        }

        @Test
        void aQualifiedAndABareStepTargetAreBothValid() {
            // The only test in the mission that uses the bare form; every composing fixture qualifies the target.
            assertThat(judge(noEdge().enhances("brief", "base", STEP))).isEmpty();
            assertThat(judge(noEdge().enhances("brief", "", STEP))).isEmpty();
        }

        @Test
        void anOverridesEdgeFromAnEnhancementIsRefusedWhateverItsTarget() {
            PackFixture twin = noEdge().enhances("brief", "base", STEP);

            assertThat(judge(twin)).isEmpty();
            for (String target : List.of("mission_type:software-dev", "tactic:x")) {
                List<Finding> found =
                        judge(noEdge().enhances("brief", "base", STEP).overrides("brief", target));

                assertOnly(found, OVERRIDES_EDGE, BRIEF);
                assertThat(found.getFirst().reason()).contains(target);
            }
        }

        @Test
        void anOverridesEdgeOfAnotherSourceKindIsNoStepEnhancementFault() {
            // Positive control for R-OVR: the rule keys on the source being a step enhancement.
            PackFixture pack = noEdge().enhances("brief", "base", STEP)
                    .edge(new Edge(RelationType.OVERRIDES, "tactic:a", "tactic:b", Optional.empty()));

            assertThat(judge(pack)).isEmpty();
        }

        @Test
        void anOverridesEdgeIsJudgedOnItsOwnBesideABadEdgeCount() {
            List<Finding> found = judge(noEdge().overrides("brief", "tactic:x"));

            assertThat(found).extracting(Finding::rule).containsExactly(EDGE_COUNT, OVERRIDES_EDGE);
        }

        @Test
        void twoEnhancesEdgesAnswerOnlyTheCountEvenWhenTheyAlsoBreakTheTargetAndDirectoryRules() {
            PackFixture pack =
                    noEdge().enhances("brief", "base", "tactic:x").enhances("brief", "base", "action:other/plan");

            assertOnly(judge(pack), EDGE_COUNT, BRIEF);
        }

        @Test
        void aRepeatedIdAcrossTypeDirectoriesIsRefusedOnceAtTheSecondFileInPathOrder() {
            PackFixture repeated = new PackFixture()
                    .valid("a", "brief", "plan")
                    .enhancement("b", "brief", new Entry("tone:style", "brief.prompt-fragment.md"))
                    .fragment("b", "brief.prompt-fragment.md", "Be brief.\n");
            PackFixture renamed = new PackFixture().valid("a", "brief", "plan").valid("b", "other", "plan");

            assertOnly(judge(repeated), STEM_REPEATED, PackFixture.path("b", "brief"));
            assertThat(judge(renamed)).isEmpty();
        }

        @Test
        void aRepeatedFileWithNoEntriesAnswersOnlyStemRepeatedForIdentity() {
            PackFixture pack = new PackFixture().valid("a", "brief", "plan").enhancement("b", "brief");

            assertOnly(judge(pack), STEM_REPEATED, PackFixture.path("b", "brief"));
        }

        @Test
        void thePathOrderIsTheComparatorsNotTheInputOrNotStringOrder() {
            // 'plan-x/' sorts below 'plan/' as a String, so a caller listing by String order passes it first.
            // PackPolicy.comparePaths puts the shorter 'plan' segment first, so 'plan-x' is the second file.
            PackFixture stringOrder = new PackFixture()
                    .enhancement("plan-x", "brief", new Entry("tone:style", "brief.prompt-fragment.md"))
                    .fragment("plan-x", "brief.prompt-fragment.md", "Be brief.\n")
                    .enhancement("plan", "brief", new Entry("tone:style", "brief.prompt-fragment.md"))
                    .fragment("plan", "brief.prompt-fragment.md", "Be brief.\n")
                    .enhances("brief", "base", "action:plan/specify");

            assertOnly(judge(stringOrder), STEM_REPEATED, PackFixture.path("plan-x", "brief"));
        }

        /** D12. The schema alone keeps "an enhancement without fragments stays valid": see PromptFragmentContractsTest:53-55. */
        @Test
        void anEnhancementWithoutAnyEntryIsRefusedAndOneWithAnEntryIsNot() {
            PackFixture empty = new PackFixture().enhancement(PLAN, "brief").enhances("brief", "base", STEP);

            assertOnly(judge(empty), NO_FRAGMENTS, BRIEF);
            assertThat(judge(noEdge().enhances("brief", "base", STEP))).isEmpty();
        }
    }

    @Nested
    class Order {

        private PackFixture fourFaults(boolean reversed) {
            PackFixture pack = new PackFixture();
            List<Runnable> steps = List.of(
                    () -> pack.valid(PLAN, "b", "plan").enhances("b", "base", "action:software-dev/tasks"),
                    () -> pack.valid(PLAN, "c", "plan").overrides("c", "mission_type:software-dev"),
                    () -> pack.enhancement(PLAN, "d", new Entry("tone:style", "d.prompt-fragment.md"))
                            .fragment(PLAN, "d.prompt-fragment.md", "Be brief.\n")
                            .enhances("d", "base", "action:other/plan"),
                    () -> pack.enhancement(PLAN, "a", new Entry("tone:style", "a.prompt-fragment.md"))
                            .fragment(PLAN, "a.prompt-fragment.md", "Be <!-- x:y:start --> brief.\n")
                            .enhances("a", "base", "action:software-dev/plan"));
            List<Runnable> ordered = reversed ? steps.reversed() : steps;
            ordered.forEach(Runnable::run);
            return pack;
        }

        @Test
        void fourFaultsInOnePackAreAllReportedAtTheirFilesInPathThenRuleOrder() {
            List<Finding> found = judge(fourFaults(false));

            assertThat(found)
                    .extracting(Finding::rule, Finding::path)
                    .containsExactly(
                            tuple(FRAGMENT_HAS_MARKER, PackFixture.fragmentPath(PLAN, "a.prompt-fragment.md")),
                            tuple(EDGE_COUNT, PackFixture.path(PLAN, "b")),
                            tuple(OVERRIDES_EDGE, PackFixture.path(PLAN, "c")),
                            tuple(DIRECTORY_MISMATCH, PackFixture.path(PLAN, "d")));
        }

        @Test
        void theAnswerDoesNotFollowTheOrderOfTheFactsAndRepeats() {
            List<Finding> once = judge(fourFaults(false));

            assertThat(once).hasSize(4);
            assertThat(judge(fourFaults(true))).isEqualTo(once);
            assertThat(judge(fourFaults(false))).isEqualTo(once);
        }
    }

    @Nested
    class EdgeFact {

        @Test
        void aTargetStillCarryingItsPackQualifierIsRefusedLoudlyAndTheSplitFormIsNot() {
            assertThatThrownBy(() -> new Edge(
                            RelationType.ENHANCES,
                            "step_enhancement:brief",
                            "base::action:software-dev/plan",
                            Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("base::action:software-dev/plan");
        }
    }
}
