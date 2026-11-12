package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.EdgeIndexTest.edge;
import static ai.speckitty.charter.packs.application.EdgeIndexTest.urn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ActionContextWalk.Context;
import ai.speckitty.charter.packs.application.ActionContextWalk.Entry;
import ai.speckitty.charter.packs.application.ActionContextWalk.Pass;
import ai.speckitty.charter.packs.application.ActionContextWalk.Step;
import ai.speckitty.charter.packs.application.ActionContextWalk.SuggestionCandidate;
import ai.speckitty.charter.packs.application.EdgeIndexTest.Closure;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The effective form of the action-context walk: the ownership rule (research R1, Decision Moment
 * {@code 01M774HQ8N5EVYDRTP16MBJCN3}). An in-force directive, tactic or paradigm applies to {@code action:M/a} when the
 * action's walk reaches it (pass {@link Pass#ACTION}), when {@code mission_type:M} scopes it (pass
 * {@link Pass#MISSION_TYPE}), or when no action and no mission type scopes it (pass {@link Pass#PROJECT}); what a pass
 * root reaches by steps 2 to 5 comes with it, and the first pass that reaches an artifact lists it (research R3).
 * Scope owners are every authored {@code scope} edge from an action or a mission type, held or not (research R2, squad
 * A6).
 */
class ActionContextWalkOwnershipTest {

    private static final String START = "action:M/a";

    private static Predicate<Urn> inForce(String... urns) {
        Set<Urn> set = Set.copyOf(Stream.of(urns).map(EdgeIndexTest::urn).toList());
        return set::contains;
    }

    private static boolean everything(Urn urn) {
        return true;
    }

    /** A closure of one pack that holds mission type {@code M}, its steps {@code a} and {@code b}, and the rest. */
    private static Closure closure(List<String> artifacts, Relationship... edges) {
        List<String> held = new ArrayList<>(List.of("mission_type:M", START, "action:M/b"));
        held.addAll(artifacts);
        return new Closure().pack("alpha", held, edges);
    }

    private static Context effective(Closure closure, String action, int depth, Predicate<Urn> inForce) {
        return new ActionContextWalk(closure.merged()).walkEffective("M", action, depth, inForce);
    }

    private static Context effective(Closure closure, int depth, Predicate<Urn> inForce) {
        return effective(closure, "a", depth, inForce);
    }

    private static Entry entry(Context context, String urn) {
        Optional<Entry> found = context.entries().stream()
                .filter(candidate -> candidate.urn().equals(urn(urn)))
                .findFirst();
        assertThat(found).as(urn + " is listed").isPresent();
        return found.orElseThrow();
    }

    private static List<String> listed(Context context) {
        return context.entries().stream().map(entry -> entry.urn().toString()).toList();
    }

    // ---- pass 3, project-wide ---------------------------------------------------------------------------------------

    @Test
    void anUnscopedDirectiveInForceIsListedProjectWideOnEveryActionOfAMissionTypeInForce() {
        Closure closure = closure(List.of("directive:U"));

        for (String action : List.of("a", "b")) {
            Context context = effective(closure, action, 0, inForce("mission_type:M", "directive:U"));

            assertThat(context.entries())
                    .as("the context of action:M/%s", action)
                    .extracting(Entry::urn, Entry::step, Entry::pass, Entry::root, Entry::via, Entry::hops)
                    .containsExactly(tuple(
                            urn("directive:U"),
                            Step.SCOPE,
                            Pass.PROJECT,
                            Optional.of(urn("directive:U")),
                            Urn.action("M", action),
                            1));
        }
    }

    @Test
    void aDirectiveScopedOnlyByAnotherActionIsAbsentWhetherOrNotItIsRequired() {
        Closure closure = closure(List.of("directive:X"), edge("action:M/b", RelationType.SCOPE, "directive:X"));

        Context context = effective(closure, 1, ActionContextWalkOwnershipTest::everything);

        assertThat(listed(context))
                .as("in force (the predicate says so, whatever made it so) and scoped only by action:M/b")
                .doesNotContain("directive:X");
        assertThat(listed(effective(closure, "b", 1, ActionContextWalkOwnershipTest::everything)))
                .as("its owner lists it through the action")
                .containsExactly("directive:X");
    }

    // ---- pass 2, the mission type -----------------------------------------------------------------------------------

    @Test
    void aDirectiveScopedByTheMissionTypeIsListedThroughTheMissionType() {
        Closure closure = closure(
                List.of("directive:T", "directive:R"),
                edge("mission_type:M", RelationType.SCOPE, "directive:T"),
                edge("directive:T", RelationType.REQUIRES, "directive:R"));

        Context context = effective(closure, 0, ActionContextWalkOwnershipTest::everything);

        assertThat(context.entries())
                .extracting(Entry::urn, Entry::step, Entry::pass, Entry::root, Entry::via, Entry::hops)
                .containsExactly(
                        tuple(
                                urn("directive:R"),
                                Step.REQUIRES,
                                Pass.MISSION_TYPE,
                                Optional.of(urn("directive:T")),
                                urn("directive:T"),
                                2),
                        tuple(
                                urn("directive:T"),
                                Step.SCOPE,
                                Pass.MISSION_TYPE,
                                Optional.of(urn("directive:T")),
                                urn("mission_type:M"),
                                1));
    }

    @Test
    void aDirectiveScopedByAnotherMissionTypeIsAbsent() {
        Closure closure = closure(
                List.of("mission_type:N", "action:N/x", "directive:O"),
                edge("mission_type:N", RelationType.SCOPE, "directive:O"));

        assertThat(listed(effective(closure, 0, ActionContextWalkOwnershipTest::everything)))
                .doesNotContain("directive:O");
        assertThat(new ActionContextWalk(closure.merged())
                        .walkEffective("N", "x", 0, ActionContextWalkOwnershipTest::everything)
                        .entries())
                .extracting(Entry::urn, Entry::pass)
                .containsExactly(tuple(urn("directive:O"), Pass.MISSION_TYPE));
    }

    // ---- owners, held or not (squad A6) -----------------------------------------------------------------------------

    @Test
    void aScopeEdgeFromAnUnheldSourceStillOwnsItsTarget() {
        Closure owned = closure(List.of("directive:Q"), edge("action:qa/x", RelationType.SCOPE, "directive:Q"));

        assertThat(listed(effective(owned, 0, ActionContextWalkOwnershipTest::everything)))
                .as("no place holds action:qa/x, and the closure holds no mission_type:qa; the edge still owns Q")
                .doesNotContain("directive:Q");
    }

    @Test
    void theSameDirectiveWithoutTheEdgeIsListedProjectWide() {
        Closure unowned = closure(List.of("action:qa/x", "directive:Q"));

        assertThat(entry(effective(unowned, 0, ActionContextWalkOwnershipTest::everything), "directive:Q")
                        .pass())
                .isEqualTo(Pass.PROJECT);
    }

    // ---- the roots --------------------------------------------------------------------------------------------------

    @Test
    void onlyDirectivesTacticsAndParadigmsAreRoots() {
        Closure closure = closure(
                List.of(
                        "procedure:P",
                        "styleguide:S",
                        "toolguide:G",
                        "agent_profile:A",
                        "tactic:Ta",
                        "paradigm:Pa",
                        "directive:W",
                        "procedure:P2"),
                edge("directive:W", RelationType.REQUIRES, "procedure:P2"));

        Context context = effective(closure, 0, ActionContextWalkOwnershipTest::everything);

        assertThat(listed(context))
                .as("an unscoped procedure, styleguide, toolguide or profile is no root")
                .containsExactly("directive:W", "paradigm:Pa", "procedure:P2", "tactic:Ta");
        assertThat(entry(context, "procedure:P2"))
                .extracting(Entry::step, Entry::pass, Entry::root, Entry::via, Entry::hops)
                .containsExactly(Step.REQUIRES, Pass.PROJECT, Optional.of(urn("directive:W")), urn("directive:W"), 2);
    }

    @Test
    void theFirstPassThatReachesAnArtifactListsIt() {
        Closure closure = closure(
                List.of("directive:S", "directive:W"),
                edge(START, RelationType.SCOPE, "directive:S"),
                edge("directive:S", RelationType.REQUIRES, "directive:W"));

        Context context = effective(closure, 0, ActionContextWalkOwnershipTest::everything);

        assertThat(context.entries())
                .extracting(Entry::urn, Entry::step, Entry::pass, Entry::root, Entry::via, Entry::hops)
                .as("W is project-wide, and the action's walk reached it first: listed once, through the action")
                .containsExactly(
                        tuple(urn("directive:S"), Step.SCOPE, Pass.ACTION, Optional.empty(), urn(START), 1),
                        tuple(urn("directive:W"), Step.REQUIRES, Pass.ACTION, Optional.empty(), urn("directive:S"), 2));
    }

    @Test
    void anArtifactTheActionScopesAndTheMissionTypeAlsoScopesIsListedThroughTheAction() {
        Closure closure = closure(
                List.of("directive:D"),
                edge(START, RelationType.SCOPE, "directive:D"),
                edge("mission_type:M", RelationType.SCOPE, "directive:D"));

        assertThat(effective(closure, 0, ActionContextWalkOwnershipTest::everything)
                        .entries())
                .extracting(Entry::urn, Entry::pass, Entry::via)
                .containsExactly(tuple(urn("directive:D"), Pass.ACTION, urn(START)));
    }

    @Test
    void anEligibleRootFirstReachedByVocabularyIsListedThroughTheActionAndStillSeedsItsPass() {
        // Research R3, revised after squad A7: first reach wins in every step, vocabulary included. V is project-wide,
        // but the action's vocabulary step reaches it first, so it is listed through the action; as a root of the
        // project pass it still seeds steps 2 to 5, which list what it requires through the project.
        Closure closure = closure(
                List.of("tactic:T", "directive:V", "procedure:R"),
                edge(START, RelationType.SCOPE, "tactic:T"),
                edge("tactic:T", RelationType.VOCABULARY, "directive:V"),
                edge("directive:V", RelationType.REQUIRES, "procedure:R"));

        Context context = effective(closure, 0, ActionContextWalkOwnershipTest::everything);

        assertThat(context.entries())
                .extracting(Entry::urn, Entry::step, Entry::pass, Entry::root, Entry::via, Entry::hops)
                .containsExactly(
                        tuple(urn("directive:V"), Step.VOCABULARY, Pass.ACTION, Optional.empty(), urn("tactic:T"), 2),
                        tuple(
                                urn("procedure:R"),
                                Step.REQUIRES,
                                Pass.PROJECT,
                                Optional.of(urn("directive:V")),
                                urn("directive:V"),
                                3),
                        tuple(urn("tactic:T"), Step.SCOPE, Pass.ACTION, Optional.empty(), urn(START), 1));
    }

    @Test
    void whatARootReachesThroughAnArtifactAnEarlierPassListedNamesThatRoot() {
        // V is listed by the action's vocabulary step, so its requires are not walked in pass 1; the project root W
        // requires V and walks through it to R, which the project pass lists under W's root.
        Closure closure = closure(
                List.of("tactic:T", "glossary_pack:V", "directive:W", "procedure:R"),
                edge(START, RelationType.SCOPE, "tactic:T"),
                edge("tactic:T", RelationType.VOCABULARY, "glossary_pack:V"),
                edge("directive:W", RelationType.REQUIRES, "glossary_pack:V"),
                edge("glossary_pack:V", RelationType.REQUIRES, "procedure:R"));

        Context context = effective(closure, 0, ActionContextWalkOwnershipTest::everything);

        assertThat(entry(context, "procedure:R"))
                .extracting(Entry::step, Entry::pass, Entry::root, Entry::via)
                .containsExactly(Step.REQUIRES, Pass.PROJECT, Optional.of(urn("directive:W")), urn("glossary_pack:V"));
    }

    /**
     * The project root W requires V, which the action's vocabulary step listed first; V's vocabulary G is what W reaches
     * by step 5, so the project pass lists it through V (review cycle 1, probe 1).
     */
    @Test
    void anArtifactAnEarlierPassListedStillSeedsTheVocabularyStepOfALaterPass() {
        Closure closure = closure(
                List.of("tactic:T", "glossary_pack:V", "directive:W", "glossary_pack:G"),
                edge(START, RelationType.SCOPE, "tactic:T"),
                edge("tactic:T", RelationType.VOCABULARY, "glossary_pack:V"),
                edge("directive:W", RelationType.REQUIRES, "glossary_pack:V"),
                edge("glossary_pack:V", RelationType.VOCABULARY, "glossary_pack:G"));

        Context context = effective(closure, 0, ActionContextWalkOwnershipTest::everything);

        assertThat(context.entries())
                .extracting(Entry::urn, Entry::step, Entry::pass, Entry::root, Entry::via, Entry::hops)
                .containsExactly(
                        tuple(urn("directive:W"), Step.SCOPE, Pass.PROJECT, Optional.of(urn("directive:W")), urn(START), 1),
                        tuple(
                                urn("glossary_pack:G"),
                                Step.VOCABULARY,
                                Pass.PROJECT,
                                Optional.of(urn("directive:W")),
                                urn("glossary_pack:V"),
                                3),
                        tuple(urn("glossary_pack:V"), Step.VOCABULARY, Pass.ACTION, Optional.empty(), urn("tactic:T"), 2),
                        tuple(urn("tactic:T"), Step.SCOPE, Pass.ACTION, Optional.empty(), urn(START), 1));
    }

    @Test
    void withoutTheActionReachingItTheProjectPassListsTheSameVocabularyThroughTheSameRoot() {
        Closure closure = closure(
                List.of("tactic:T", "glossary_pack:V", "directive:W", "glossary_pack:G"),
                edge(START, RelationType.SCOPE, "tactic:T"),
                edge("directive:W", RelationType.REQUIRES, "glossary_pack:V"),
                edge("glossary_pack:V", RelationType.VOCABULARY, "glossary_pack:G"));

        Context context = effective(closure, 0, ActionContextWalkOwnershipTest::everything);

        assertThat(entry(context, "glossary_pack:V"))
                .extracting(Entry::step, Entry::pass, Entry::root)
                .containsExactly(Step.REQUIRES, Pass.PROJECT, Optional.of(urn("directive:W")));
        assertThat(entry(context, "glossary_pack:G"))
                .extracting(Entry::step, Entry::pass, Entry::root, Entry::via)
                .containsExactly(Step.VOCABULARY, Pass.PROJECT, Optional.of(urn("directive:W")), urn("glossary_pack:V"));
    }

    /**
     * The project root W suggests S, which the action's vocabulary step listed first; what S requires is what W reaches
     * by step 4, so the project pass lists it through S (review cycle 1, probe 2).
     */
    @Test
    void anArtifactAnEarlierPassListedStillSeedsTheRequiresOfSuggestedStepOfALaterPass() {
        Closure closure = closure(
                List.of("tactic:T", "procedure:S", "directive:W", "procedure:R"),
                edge(START, RelationType.SCOPE, "tactic:T"),
                edge("tactic:T", RelationType.VOCABULARY, "procedure:S"),
                edge("directive:W", RelationType.SUGGESTS, "procedure:S"),
                edge("procedure:S", RelationType.REQUIRES, "procedure:R"));

        Context context = effective(closure, 1, ActionContextWalkOwnershipTest::everything);

        assertThat(entry(context, "procedure:S"))
                .extracting(Entry::step, Entry::pass)
                .containsExactly(Step.VOCABULARY, Pass.ACTION);
        assertThat(entry(context, "procedure:R"))
                .extracting(Entry::step, Entry::pass, Entry::root, Entry::via, Entry::hops)
                .containsExactly(
                        Step.REQUIRES_OF_SUGGESTED, Pass.PROJECT, Optional.of(urn("directive:W")), urn("procedure:S"), 3);
    }

    @Test
    void withoutTheActionReachingItTheProjectPassListsTheSameRequirementThroughTheSameRoot() {
        Closure closure = closure(
                List.of("tactic:T", "procedure:S", "directive:W", "procedure:R"),
                edge(START, RelationType.SCOPE, "tactic:T"),
                edge("directive:W", RelationType.SUGGESTS, "procedure:S"),
                edge("procedure:S", RelationType.REQUIRES, "procedure:R"));

        Context context = effective(closure, 1, ActionContextWalkOwnershipTest::everything);

        assertThat(entry(context, "procedure:S"))
                .extracting(Entry::step, Entry::pass, Entry::root)
                .containsExactly(Step.SUGGESTS, Pass.PROJECT, Optional.of(urn("directive:W")));
        assertThat(entry(context, "procedure:R"))
                .extracting(Entry::step, Entry::pass, Entry::root, Entry::via)
                .containsExactly(
                        Step.REQUIRES_OF_SUGGESTED, Pass.PROJECT, Optional.of(urn("directive:W")), urn("procedure:S"));
    }

    @Test
    void theMissionTypePassRunsBeforeTheProjectPass() {
        Closure closure = closure(
                List.of("directive:T", "directive:W", "directive:Both"),
                edge("mission_type:M", RelationType.SCOPE, "directive:T"),
                edge("directive:T", RelationType.REQUIRES, "directive:Both"),
                edge("directive:W", RelationType.REQUIRES, "directive:Both"));

        assertThat(entry(effective(closure, 0, ActionContextWalkOwnershipTest::everything), "directive:Both"))
                .as("Both is reached by the mission type's root first")
                .extracting(Entry::pass, Entry::root)
                .containsExactly(Pass.MISSION_TYPE, Optional.of(urn("directive:T")));
    }

    @Test
    void aRootOutOfForceIsNoRoot() {
        Closure closure = closure(
                List.of("directive:T", "directive:U"), edge("mission_type:M", RelationType.SCOPE, "directive:T"));

        assertThat(effective(closure, 0, inForce("mission_type:M")).entries()).isEmpty();
    }

    // ---- suggestions ------------------------------------------------------------------------------------------------

    @Test
    void aSuggestionCandidateCarriesThePassThatListsIt() {
        Closure closure = closure(
                List.of("tactic:T", "tactic:Out", "directive:W", "tactic:Later"),
                edge(START, RelationType.SCOPE, "tactic:T"),
                edge("tactic:T", RelationType.SUGGESTS, "tactic:Out"),
                edge("directive:W", RelationType.SUGGESTS, "tactic:Later"),
                edge("directive:W", RelationType.SUGGESTS, "tactic:Out"));

        Context context = effective(closure, 1, inForce("mission_type:M", "tactic:T", "directive:W"));

        assertThat(context.suggestionCandidates())
                .extracting(
                        SuggestionCandidate::urn,
                        SuggestionCandidate::via,
                        SuggestionCandidate::hops,
                        SuggestionCandidate::pass)
                .as("Later comes from the project root W; Out from the action's T first, so W does not take it")
                .containsExactly(
                        tuple(urn("tactic:Later"), urn("directive:W"), 2, Pass.PROJECT),
                        tuple(urn("tactic:Out"), urn("tactic:T"), 2, Pass.ACTION));
    }

    @Test
    void aSuggestedArtifactInForceOfAProjectRootIsListedThroughTheProject() {
        Closure closure = closure(
                List.of("directive:W", "procedure:S"), edge("directive:W", RelationType.SUGGESTS, "procedure:S"));

        assertThat(entry(effective(closure, 1, ActionContextWalkOwnershipTest::everything), "procedure:S"))
                .extracting(Entry::step, Entry::pass, Entry::root, Entry::hops)
                .containsExactly(Step.SUGGESTS, Pass.PROJECT, Optional.of(urn("directive:W")), 2);
    }

    // ---- a mission type not in force --------------------------------------------------------------------------------

    @Test
    void aMissionTypeNotInForceAnswersNoEntryInEveryPass() {
        Closure closure = closure(
                List.of("directive:U", "directive:T", "tactic:Out"),
                edge("mission_type:M", RelationType.SCOPE, "directive:T"),
                edge("directive:U", RelationType.SUGGESTS, "tactic:Out"));

        Context context = effective(closure, 1, inForce("directive:U", "directive:T"));

        assertThat(context.missionTypeInForce()).isFalse();
        assertThat(context.entries()).isEmpty();
        assertThat(context.suggestionCandidates()).isEmpty();
    }

    // ---- the offering form ------------------------------------------------------------------------------------------

    @Test
    void theOfferingWalkIsUnchanged() {
        Closure closure = closure(
                List.of("tactic:T", "directive:U", "directive:D", "tactic:Out"),
                edge(START, RelationType.SCOPE, "tactic:T"),
                edge("mission_type:M", RelationType.SCOPE, "directive:D"),
                edge("tactic:T", RelationType.SUGGESTS, "tactic:Out"));
        Predicate<Urn> predicate = inForce("mission_type:M", "tactic:T", "directive:U", "directive:D");

        Context offering = new ActionContextWalk(closure.merged()).walk("M", "a", 1, predicate);

        assertThat(offering.entries())
                .as("the five-step walk lists neither the unscoped U nor the type-scoped D")
                .extracting(Entry::urn, Entry::step, Entry::pass, Entry::root, Entry::via, Entry::hops)
                .containsExactly(tuple(urn("tactic:T"), Step.SCOPE, Pass.ACTION, Optional.empty(), urn(START), 1));
        assertThat(offering.suggestionCandidates())
                .extracting(SuggestionCandidate::urn, SuggestionCandidate::pass)
                .containsExactly(tuple(urn("tactic:Out"), Pass.ACTION));
        assertThat(listed(effective(closure, 1, predicate))).containsExactly("directive:D", "directive:U", "tactic:T");
    }

    @Test
    void whereNoRootAddsAnythingTheEffectiveFormAnswersWhatTheOfferingFormAnswers() {
        Closure closure = closure(
                List.of("tactic:T", "directive:R", "tactic:S", "glossary_pack:G", "tactic:Out", "directive:X"),
                edge(START, RelationType.SCOPE, "tactic:T"),
                edge("action:M/b", RelationType.SCOPE, "directive:X"),
                edge("tactic:T", RelationType.REQUIRES, "directive:R"),
                edge("tactic:T", RelationType.SUGGESTS, "tactic:S"),
                edge("tactic:S", RelationType.SUGGESTS, "tactic:Out"),
                edge("tactic:T", RelationType.SUGGESTS, "tactic:Out"),
                edge("directive:R", RelationType.VOCABULARY, "glossary_pack:G"),
                edge("action:M/b", RelationType.SCOPE, "tactic:S"));
        Predicate<Urn> predicate =
                inForce("mission_type:M", "tactic:T", "directive:R", "tactic:S", "glossary_pack:G", "directive:X");
        ActionContextWalk walk = new ActionContextWalk(closure.merged());

        assertThat(walk.walkEffective("M", "a", 2, predicate)).isEqualTo(walk.walk("M", "a", 2, predicate));
    }

    // ---- the evidence of 2026-11-12 (spec, section "Evidence") ------------------------------------------------------

    private static final List<String> IMPLEMENT_SCOPED = List.of(
            "DIRECTIVE_001",
            "DIRECTIVE_010",
            "DIRECTIVE_025",
            "DIRECTIVE_030",
            "DIRECTIVE_034",
            "DIRECTIVE_037",
            "DIRECTIVE_051");

    private static final List<String> UNSCOPED = List.of(
            "DIRECTIVE_032",
            "DIRECTIVE_041",
            "NO_FULL_HEAVY_SUITES_IN_MISSION",
            "QA_003_NO_FAKE_GREEN",
            "RECONCILE_CHANGE_SCOPE_TENSIONS",
            "USE_C4_MODEL_TECHNIQUES",
            "PROJECT_001",
            "PROJECT_002");

    @Test
    void theEvidenceListsFifteenOfSixteen() {
        String implement = "action:software-dev/implement";
        List<String> held = new ArrayList<>(List.of(
                "mission_type:software-dev",
                implement,
                "action:software-dev/plan",
                "action:software-dev/review",
                "action:software-dev/specify",
                "directive:DIRECTIVE_003",
                "directive:QA_003"));
        List<Relationship> edges = new ArrayList<>();
        IMPLEMENT_SCOPED.forEach(id -> {
            held.add("directive:" + id);
            edges.add(edge(implement, RelationType.SCOPE, "directive:" + id));
        });
        UNSCOPED.forEach(id -> held.add("directive:" + id));
        for (String other : List.of("plan", "review", "specify")) {
            edges.add(edge("action:software-dev/" + other, RelationType.SCOPE, "directive:DIRECTIVE_003"));
        }
        edges.add(edge("mission_type:qa", RelationType.SCOPE, "directive:QA_003"));
        Closure closure = new Closure().pack("open-packs", held, edges.toArray(Relationship[]::new));

        Context context = new ActionContextWalk(closure.merged())
                .walkEffective("software-dev", "implement", 1, ActionContextWalkOwnershipTest::everything);

        assertThat(context.entries())
                .filteredOn(entry -> IMPLEMENT_SCOPED.contains(entry.urn().id()))
                .as("the seven implement-scoped directives come through the action")
                .hasSize(7)
                .allSatisfy(entry -> assertThat(entry.pass()).isEqualTo(Pass.ACTION));
        assertThat(context.entries())
                .filteredOn(entry -> UNSCOPED.contains(entry.urn().id()))
                .as("the eight unscoped directives come project-wide")
                .hasSize(8)
                .allSatisfy(entry -> assertThat(entry.pass()).isEqualTo(Pass.PROJECT));
        assertThat(listed(context))
                .as("15 of the 16: DIRECTIVE_003 is scoped only by other actions, QA_003 by another mission type")
                .hasSize(15)
                .doesNotContain("directive:DIRECTIVE_003", "directive:QA_003");
    }

    // ---- NFR-004: deterministic ----------------------------------------------------------------------------------

    private static final Relationship[] ALPHA = {
        edge(START, RelationType.SCOPE, "directive:A1"),
        edge("directive:A1", RelationType.REQUIRES, "directive:A2"),
        edge("directive:A1", RelationType.SUGGESTS, "tactic:AS"),
        edge("action:M/b", RelationType.SCOPE, "directive:AB")
    };

    private static final Relationship[] BETA = {
        edge("alpha::mission_type:M", RelationType.SCOPE, "directive:B1"),
        edge("directive:B1", RelationType.REQUIRES, "directive:B2"),
        edge("directive:B2", RelationType.VOCABULARY, "glossary_pack:BG"),
        edge("directive:BU", RelationType.SUGGESTS, "tactic:BOut")
    };

    private static final Relationship[] GAMMA = {
        edge("alpha::action:M/b", RelationType.SCOPE, "directive:GB"),
        edge("directive:GU", RelationType.REQUIRES, "paradigm:GP"),
        edge("paradigm:GP", RelationType.SUGGESTS, "alpha::tactic:AS"),
        edge("tactic:GT", RelationType.SUGGESTS, "directive:GU")
    };

    private static Relationship[] inOrder(Relationship[] links, boolean reversed) {
        List<Relationship> written = List.of(links);
        return (reversed ? written.reversed() : written).toArray(Relationship[]::new);
    }

    private static Closure threePacks(List<String> order, boolean reversedLinks) {
        Closure closure = new Closure();
        for (String pack : order) {
            switch (pack) {
                case "alpha" ->
                    closure.pack(
                            "alpha",
                            List.of(
                                    "mission_type:M",
                                    START,
                                    "action:M/b",
                                    "directive:A1",
                                    "directive:A2",
                                    "tactic:AS",
                                    "directive:AB"),
                            inOrder(ALPHA, reversedLinks));
                case "beta" ->
                    closure.pack(
                            "beta",
                            List.of("alpha"),
                            List.of("directive:B1", "directive:B2", "glossary_pack:BG", "directive:BU", "tactic:BOut"),
                            inOrder(BETA, reversedLinks));
                default ->
                    closure.pack(
                            "gamma",
                            List.of("alpha"),
                            List.of("directive:GB", "directive:GU", "paradigm:GP", "tactic:GT"),
                            inOrder(GAMMA, reversedLinks));
            }
        }
        return closure;
    }

    @Test
    void theContextIsTheSameAcrossRepeatedWalksAndEveryOrderOfThePacksAndTheirLinks() {
        Predicate<Urn> predicate = urn -> !urn.equals(urn("tactic:BOut"));
        List<List<String>> orders = List.of(
                List.of("alpha", "beta", "gamma"),
                List.of("alpha", "gamma", "beta"),
                List.of("beta", "alpha", "gamma"),
                List.of("beta", "gamma", "alpha"),
                List.of("gamma", "alpha", "beta"),
                List.of("gamma", "beta", "alpha"));
        Context first = new ActionContextWalk(
                        threePacks(orders.getFirst(), false).merged())
                .walkEffective("M", "a", 1, predicate);

        assertThat(listed(first))
                .containsExactly(
                        "directive:A1",
                        "directive:A2",
                        "directive:B1",
                        "directive:B2",
                        "directive:BU",
                        "directive:GU",
                        "glossary_pack:BG",
                        "paradigm:GP",
                        "tactic:AS",
                        "tactic:GT");
        assertThat(first.suggestionCandidates())
                .extracting(SuggestionCandidate::urn, SuggestionCandidate::pass)
                .containsExactly(tuple(urn("tactic:BOut"), Pass.PROJECT));
        ActionContextWalk walk =
                new ActionContextWalk(threePacks(orders.getFirst(), false).merged());
        for (int read = 0; read < 100; read++) {
            assertThat(walk.walkEffective("M", "a", 1, predicate)).isEqualTo(first);
        }
        for (List<String> order : orders) {
            for (boolean reversed : List.of(false, true)) {
                assertThat(new ActionContextWalk(threePacks(order, reversed).merged())
                                .walkEffective("M", "a", 1, predicate))
                        .as("packs in the order %s, each pack's links reversed: %s", order, reversed)
                        .isEqualTo(first);
            }
        }
    }

    // ---- NFR-003: bounded cost --------------------------------------------------------------------------------------

    /**
     * An index of {@code size} in-force directives, a quarter each scoped by the action, by the mission type, by another
     * action and by nothing; each scoped or type-scoped one requires the next of its quarter, each unscoped one suggests
     * an out-of-force tactic.
     */
    private static Closure ofSize(int size) {
        List<String> held = new ArrayList<>(List.of("mission_type:M", START, "action:M/b"));
        List<Relationship> edges = new ArrayList<>();
        int quarter = size / 4;
        for (int i = 0; i < quarter; i++) {
            for (String group : List.of("A", "T", "B", "U")) {
                held.add("directive:" + group + i);
            }
            held.add("tactic:Out" + i);
            edges.add(edge(START, RelationType.SCOPE, "directive:A" + i));
            edges.add(edge("mission_type:M", RelationType.SCOPE, "directive:T" + i));
            edges.add(edge("action:M/b", RelationType.SCOPE, "directive:B" + i));
            edges.add(edge("directive:U" + i, RelationType.SUGGESTS, "tactic:Out" + i));
            if (i + 1 < quarter) {
                edges.add(edge("directive:A" + i, RelationType.REQUIRES, "directive:A" + (i + 1)));
                edges.add(edge("directive:T" + i, RelationType.REQUIRES, "directive:T" + (i + 1)));
            }
        }
        return new Closure().pack("alpha", held, edges.toArray(Relationship[]::new));
    }

    /**
     * The visits of a node or an edge one effective walk makes, from a merged index nothing has read yet: every edge
     * the index and the walk iterate (the walk's own link index, its steps, its enhancers, the memoized scope-owner
     * build), every held node the memoized held-URN build visits, and every test of the predicate.
     */
    private static long operations(int size) {
        AtomicLong count = new AtomicLong();
        EdgeIndex merged = ofSize(size).merged(count::incrementAndGet);
        Predicate<Urn> counting = urn -> {
            count.incrementAndGet();
            return !urn.kind().token().equals("tactic");
        };
        long before = count.get();
        Context context = new ActionContextWalk(merged).walkEffective("M", "a", 1, counting);
        assertThat(context.entries()).hasSize(size / 4 * 3);
        assertThat(context.suggestionCandidates()).hasSize(size / 4);
        return count.get() - before;
    }

    @Test
    void theOwnershipRuleAddsAtMostLinearWork() {
        long thousand = operations(1_000);
        long twoThousand = operations(2_000);

        assertThat((double) twoThousand / thousand)
                .as("operations at 2,000 (%d) over operations at 1,000 (%d)", twoThousand, thousand)
                .isLessThanOrEqualTo(2.5);
    }
}
