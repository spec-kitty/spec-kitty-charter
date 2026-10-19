package ai.speckitty.charter.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import ai.speckitty.charter.domain.CharterOffering;
import ai.speckitty.charter.domain.CharterPack;
import ai.speckitty.charter.domain.CharterPackRepository;
import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.Diagnostic;
import ai.speckitty.charter.domain.DoctrineGraph;
import ai.speckitty.charter.domain.DrgEdge;
import ai.speckitty.charter.domain.DrgNode;
import ai.speckitty.charter.domain.NodeKind;
import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.domain.RefusalCode;
import ai.speckitty.charter.domain.Relation;
import ai.speckitty.charter.domain.Urn;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ActionContextQueryTest {

    private static final String TYPE_NAME = "software-dev";
    private static final Urn TYPE = Urn.missionType(TYPE_NAME);
    private static final Urn PLAN = Urn.action(TYPE_NAME, "plan");

    private static Urn tactic(String id) {
        return new Urn(NodeKind.TACTIC, id);
    }

    private static DrgEdge scope(Urn from, Urn to) {
        return DrgEdge.of(from, to, Relation.SCOPE);
    }

    private static DrgEdge requires(Urn from, Urn to) {
        return DrgEdge.of(from, to, Relation.REQUIRES);
    }

    private static DrgEdge suggests(Urn from, Urn to) {
        return DrgEdge.of(from, to, Relation.SUGGESTS);
    }

    private static DrgEdge vocabulary(Urn from, Urn to) {
        return DrgEdge.of(from, to, Relation.VOCABULARY);
    }

    /** A query over one pack whose graph declares the mission type, the action and every edge endpoint. */
    private static final String SOURCES = "sha256:" + "5".repeat(64);

    /** A query over a repository whose input files never change. */
    private static ActionContextQuery queryOver(CharterPackRepository packs) {
        return new ActionContextQuery(new ConsistentLoad(packs, () -> SOURCES));
    }

    private static ActionContextQuery over(DrgEdge... edges) {
        return over(List.of(), edges);
    }

    private static ActionContextQuery over(List<Diagnostic> diagnostics, DrgEdge... edges) {
        Set<Urn> urns = new HashSet<>(List.of(TYPE, PLAN));
        for (DrgEdge edge : edges) {
            urns.add(edge.source());
            urns.add(edge.target());
        }
        List<DrgNode> nodes =
                urns.stream().map(u -> new DrgNode(u, Optional.empty())).toList();
        return queryOver(() -> new CharterOffering(List.of(new CharterPack(
                "built-in",
                PackLevel.BUILT_IN,
                Optional.empty(),
                new DoctrineGraph(nodes, List.of(edges)),
                diagnostics))));
    }

    private static List<GuidanceEntry> walk(ActionContextQuery query, int depth) {
        return query.answer(TYPE_NAME, "plan", depth).guidance();
    }

    private static List<Urn> urns(List<GuidanceEntry> guidance) {
        return guidance.stream().map(GuidanceEntry::urn).toList();
    }

    private static GuidanceEntry entry(List<GuidanceEntry> guidance, Urn urn) {
        return guidance.stream().filter(e -> e.urn().equals(urn)).findFirst().orElseThrow();
    }

    @Test
    void scopeIsWalkedFromTheActionOnlyAndSortedByUrn() {
        Urn d1 = new Urn(NodeKind.DIRECTIVE, "D1");
        Urn t1 = tactic("t1");
        Urn typeOnly = new Urn(NodeKind.DIRECTIVE, "TYPE_ONLY");
        ActionContextQuery query = over(scope(PLAN, t1), scope(PLAN, d1), scope(TYPE, typeOnly), requires(TYPE, PLAN));
        assertThat(walk(query, 2))
                .extracting(GuidanceEntry::urn, GuidanceEntry::relation, GuidanceEntry::via, GuidanceEntry::hops)
                .containsExactly(tuple(d1, Relation.SCOPE, PLAN, 1), tuple(t1, Relation.SCOPE, PLAN, 1));
    }

    @Test
    void requiresIsTransitiveAndACycleEnds() {
        Urn a = tactic("a");
        Urn b = tactic("b");
        Urn c = tactic("c");
        ActionContextQuery chain = over(scope(PLAN, a), requires(a, b), requires(b, c));
        assertThat(walk(chain, 0))
                .extracting(GuidanceEntry::urn, GuidanceEntry::via, GuidanceEntry::hops)
                .containsExactly(tuple(a, PLAN, 1), tuple(b, a, 2), tuple(c, b, 3));
        ActionContextQuery cycle = over(scope(PLAN, a), requires(a, b), requires(b, a));
        assertThat(walk(cycle, 2))
                .extracting(GuidanceEntry::urn, GuidanceEntry::relation)
                .containsExactly(tuple(a, Relation.SCOPE), tuple(b, Relation.REQUIRES));
    }

    @Test
    void aSuggestsCycleEnds() {
        Urn a = tactic("a");
        Urn b = tactic("b");
        Urn c = tactic("c");
        ActionContextQuery cycle = over(scope(PLAN, a), suggests(a, b), suggests(b, c), suggests(c, a));
        assertThat(urns(walk(cycle, 2))).containsExactly(a, b, c);
    }

    @Test
    void depthCountsSuggestsHopsAndZeroListsNoSuggestedArtifact() {
        Urn a = tactic("a");
        Urn b = tactic("b");
        Urn c = tactic("c");
        ActionContextQuery query = over(scope(PLAN, a), suggests(a, b), suggests(b, c));
        assertThat(urns(walk(query, 0))).containsExactly(a);
        assertThat(urns(walk(query, 1))).containsExactly(a, b);
        assertThat(urns(walk(query, 2))).containsExactly(a, b, c);
        assertThat(query.answer(TYPE_NAME, "plan", 1).depth()).isEqualTo(1);
    }

    @Test
    void aDepthOutsideTheRangeIsAProgrammingError() {
        ActionContextQuery query = over(scope(PLAN, tactic("a")));
        assertThatThrownBy(() -> query.answer(TYPE_NAME, "plan", ActionContextQuery.MIN_DEPTH - 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> query.answer(TYPE_NAME, "plan", ActionContextQuery.MAX_DEPTH + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDiamondKeepsTheShortPathSoItsChildIsListedAtDepthTwo() {
        Urn root = tactic("root");
        Urn branch = tactic("branch");
        Urn shared = tactic("shared");
        Urn child = tactic("child");
        // branch is declared before shared: a depth-first walk would reach shared at two hops and hide its child.
        ActionContextQuery query = over(
                scope(PLAN, root),
                suggests(root, branch),
                suggests(root, shared),
                suggests(branch, shared),
                suggests(shared, child));
        List<GuidanceEntry> depthTwo = walk(query, 2);
        assertThat(entry(depthTwo, shared).via()).isEqualTo(root);
        assertThat(entry(depthTwo, shared).hops()).isEqualTo(2);
        assertThat(entry(depthTwo, child).via()).isEqualTo(shared);
        assertThat(entry(depthTwo, child).hops()).isEqualTo(3);
        assertThat(urns(walk(query, 1))).doesNotContain(child);
    }

    @Test
    void whatARequiredArtifactSuggestsIsNotListedAtAnyDepth() {
        // As in core: suggests is walked "from scoped artifacts" (offering/drg/query.py:196-197). Seeding it from
        // the required artifacts too was measured at +30% governance and rejected, so this is no divergence.
        Urn scoped = tactic("scoped");
        Urn required = new Urn(NodeKind.DIRECTIVE, "required");
        Urn suggested = tactic("suggested");
        ActionContextQuery query = over(scope(PLAN, scoped), requires(scoped, required), suggests(required, suggested));
        for (int depth = ActionContextQuery.MIN_DEPTH; depth <= ActionContextQuery.MAX_DEPTH; depth++) {
            assertThat(urns(walk(query, depth))).containsExactly(required, scoped);
        }
    }

    @Test
    void aSuggestsWalkPassesThroughAnArtifactAnEarlierStepListed() {
        Urn scoped = tactic("scoped");
        Urn required = new Urn(NodeKind.DIRECTIVE, "required");
        Urn child = tactic("child");
        ActionContextQuery query = over(
                scope(PLAN, scoped), requires(scoped, required), suggests(scoped, required), suggests(required, child));
        List<GuidanceEntry> depthTwo = walk(query, 2);
        assertThat(entry(depthTwo, required).relation()).isEqualTo(Relation.REQUIRES);
        assertThat(entry(depthTwo, child))
                .extracting(GuidanceEntry::relation, GuidanceEntry::via, GuidanceEntry::hops)
                .containsExactly(
                        Relation.SUGGESTS, required, entry(depthTwo, required).hops() + 1);
        assertThat(urns(walk(query, 1))).doesNotContain(child);
    }

    @Test
    void whatASuggestedArtifactRequiresIsListedAndTheWalkEndsThere() {
        // DIV-010, an intentional difference from core: core lists a suggested artifact without its hard
        // dependencies (offering/drg/query.py:226 against its own "hard dependencies" at :194-195).
        Urn scoped = tactic("scoped");
        Urn suggested = tactic("suggested");
        Urn dependency = new Urn(NodeKind.DIRECTIVE, "dependency");
        Urn deeperDependency = new Urn(NodeKind.DIRECTIVE, "deeper");
        Urn notWalked = tactic("not-walked");
        ActionContextQuery query = over(
                scope(PLAN, scoped),
                suggests(scoped, suggested),
                requires(suggested, dependency),
                requires(dependency, deeperDependency),
                suggests(dependency, notWalked));
        List<GuidanceEntry> guidance = walk(query, 2);
        assertThat(entry(guidance, dependency))
                .extracting(GuidanceEntry::relation, GuidanceEntry::via, GuidanceEntry::hops)
                .containsExactly(Relation.REQUIRES, suggested, 3);
        assertThat(entry(guidance, deeperDependency).hops()).isEqualTo(4);
        assertThat(urns(guidance)).doesNotContain(notWalked);
        assertThat(urns(walk(query, 0))).containsExactly(scoped);
    }

    @Test
    void vocabularyIsWalkedOneHopFromEverythingListedAtAnyDepth() {
        Urn scoped = tactic("scoped");
        Urn glossary = new Urn(NodeKind.GLOSSARY_PACK, "terms");
        Urn farther = new Urn(NodeKind.GLOSSARY_PACK, "farther");
        ActionContextQuery query =
                over(scope(PLAN, scoped), vocabulary(scoped, glossary), vocabulary(glossary, farther));
        assertThat(walk(query, 0))
                .extracting(GuidanceEntry::urn, GuidanceEntry::relation, GuidanceEntry::via, GuidanceEntry::hops)
                .containsExactly(
                        tuple(glossary, Relation.VOCABULARY, scoped, 2), tuple(scoped, Relation.SCOPE, PLAN, 1));
    }

    @Test
    void anArtifactTheStepsCouldReachSeveralTimesKeepsTheEarliestStepsEntry() {
        Urn scoped = tactic("scoped");
        Urn both = tactic("both");
        Urn requiredAndSuggested = tactic("required-and-suggested");
        Urn glossary = new Urn(NodeKind.GLOSSARY_PACK, "terms");
        ActionContextQuery query = over(
                scope(PLAN, scoped),
                scope(PLAN, both),
                requires(scoped, both),
                requires(scoped, requiredAndSuggested),
                suggests(scoped, requiredAndSuggested),
                suggests(scoped, glossary),
                vocabulary(scoped, glossary));
        List<GuidanceEntry> guidance = walk(query, 2);
        assertThat(entry(guidance, both).relation()).isEqualTo(Relation.SCOPE);
        assertThat(entry(guidance, both).hops()).isEqualTo(1);
        assertThat(entry(guidance, requiredAndSuggested).relation()).isEqualTo(Relation.REQUIRES);
        assertThat(entry(guidance, glossary).relation()).isEqualTo(Relation.SUGGESTS);
        assertThat(urns(guidance)).doesNotHaveDuplicates();
    }

    @Test
    void amongParentsAtTheSameDistanceTheFewerHopsWinThenTheSmallerUrn() {
        Urn middle = tactic("middle");
        Urn deep = new Urn(NodeKind.DIRECTIVE, "deep");
        Urn fresh = tactic("fresh");
        Urn byHops = tactic("by-hops");
        Urn scoped = tactic("scoped");
        // deep is required with hops 3 and its URN sorts first; fresh is a new suggestion with hops 2. Both reach
        // by-hops at the second suggests step, and fresh wins on hops.
        ActionContextQuery fewer = over(
                scope(PLAN, scoped),
                requires(scoped, middle),
                requires(middle, deep),
                suggests(scoped, deep),
                suggests(scoped, fresh),
                suggests(deep, byHops),
                suggests(fresh, byHops));
        List<GuidanceEntry> guidance = walk(fewer, 2);
        assertThat(entry(guidance, deep).hops()).isEqualTo(3);
        assertThat(entry(guidance, byHops).via()).isEqualTo(fresh);
        assertThat(entry(guidance, byHops).hops()).isEqualTo(3);

        Urn p = tactic("p");
        Urn q = tactic("q");
        Urn byUrn = tactic("by-urn");
        ActionContextQuery tie = over(scope(PLAN, q), scope(PLAN, p), suggests(q, byUrn), suggests(p, byUrn));
        assertThat(entry(walk(tie, 1), byUrn).via()).isEqualTo(p);
    }

    @Test
    void anArtifactBothRequiredAndInTheVocabularyKeepsItsRequiresEntry() {
        Urn scoped = tactic("scoped");
        Urn both = new Urn(NodeKind.GLOSSARY_PACK, "both");
        ActionContextQuery query = over(scope(PLAN, scoped), requires(scoped, both), vocabulary(scoped, both));
        assertThat(entry(walk(query, 0), both).relation()).isEqualTo(Relation.REQUIRES);
    }

    @Test
    void anEdgeBackToTheActionOrTheMissionTypeListsNeitherAndWalksNotThroughThem() {
        Urn a = tactic("a");
        Urn unreachable = tactic("unreachable");
        ActionContextQuery query = over(
                scope(PLAN, a),
                requires(a, PLAN),
                suggests(a, TYPE),
                scope(TYPE, unreachable),
                requires(PLAN, unreachable),
                requires(TYPE, PLAN));
        assertThat(urns(walk(query, 2))).containsExactly(a);
    }

    @Test
    void aTargetTheGraphDoesNotDeclareIsListedWithoutALabelAndWalkedNoFurther() {
        Urn undeclared = new Urn(NodeKind.STYLEGUIDE, "undeclared");
        ActionContextQuery query = queryOver(() -> new CharterOffering(List.of(new CharterPack(
                "built-in",
                PackLevel.BUILT_IN,
                Optional.empty(),
                new DoctrineGraph(
                        List.of(new DrgNode(TYPE, Optional.empty()), new DrgNode(PLAN, Optional.empty())),
                        List.of(scope(PLAN, undeclared))),
                List.of()))));
        assertThat(walk(query, 2)).singleElement().satisfies(e -> {
            assertThat(e.urn()).isEqualTo(undeclared);
            assertThat(e.kind()).isEqualTo(NodeKind.STYLEGUIDE);
            assertThat(e.label()).isEmpty();
            assertThat(e.via()).isEqualTo(PLAN);
            assertThat(e.hops()).isEqualTo(1);
        });
    }

    @Test
    void anEntryCarriesTheLabelOfItsNodeAndNoImpacts() {
        Urn a = tactic("a");
        ActionContextQuery query = queryOver(() -> new CharterOffering(List.of(new CharterPack(
                "built-in",
                PackLevel.BUILT_IN,
                Optional.empty(),
                new DoctrineGraph(
                        List.of(
                                new DrgNode(TYPE, Optional.empty()),
                                new DrgNode(PLAN, Optional.empty()),
                                new DrgNode(a, Optional.of("A"))),
                        List.of(scope(PLAN, a), DrgEdge.impacts(PLAN, tactic("impacted"), -1.0))),
                List.of()))));
        assertThat(walk(query, 2)).singleElement().satisfies(e -> {
            assertThat(e.label()).contains("A");
            assertThat(e.impacts()).isEmpty();
        });
    }

    @Test
    void theOfferingsLoadFindingsArePassedThroughInLoadOrder() {
        Diagnostic first = new Diagnostic(Diagnostic.Code.PACK_LEVEL_MISSING, "org pack qa has no pack.yaml");
        Diagnostic second =
                new Diagnostic(Diagnostic.Code.PACK_LEVEL_MISSING, "pack.yaml of internal has no pack_level");
        CharterPack clean = new CharterPack(
                "built-in",
                PackLevel.BUILT_IN,
                Optional.empty(),
                new DoctrineGraph(
                        List.of(new DrgNode(TYPE, Optional.empty()), new DrgNode(PLAN, Optional.empty())), List.of()),
                List.of());
        CharterPack qa = new CharterPack("qa", PackLevel.ORG, Optional.empty(), DoctrineGraph.empty(), List.of(first));
        CharterPack internal =
                new CharterPack("internal", PackLevel.ORG, Optional.empty(), DoctrineGraph.empty(), List.of(second));
        ActionContextQuery query = queryOver(() -> new CharterOffering(List.of(clean, qa, internal)));
        assertThat(query.answer(TYPE_NAME, "plan", 2).diagnostics()).containsExactly(first, second);
        assertThat(over(scope(PLAN, tactic("a"))).answer(TYPE_NAME, "plan", 2).diagnostics())
                .isEmpty();
    }

    @Test
    void aPackThatCannotLoadRefusesTheReadWithItsCode() {
        ActionContextQuery broken = queryOver(() -> {
            throw new CharterRefusal(
                    RefusalCode.DRG_SCHEMA_ERROR, "built-in/broken.graph.yaml: unknown key 'surprise'");
        });
        assertThatThrownBy(() -> broken.answer(TYPE_NAME, "plan", 1))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.DRG_SCHEMA_ERROR));
    }

    @Test
    void theAnswerCarriesTheDigestOfTheInputsItWasLoadedFrom() {
        assertThat(over(scope(PLAN, tactic("a"))).answer(TYPE_NAME, "plan", 2).sourceFingerprint())
                .isEqualTo(SOURCES);
    }

    @Test
    void unknownNamesAreRefused() {
        ActionContextQuery query = over(scope(PLAN, tactic("a")));
        assertThatThrownBy(() -> query.answer("gardening", "plan", 1))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND));
        assertThatThrownBy(() -> query.answer(TYPE_NAME, "deploy", 1))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.ACTION_NOT_FOUND));
    }

    @Test
    void theAnswerCarriesTheFingerprintOfTheGovernanceItWasComputedFrom() {
        ActionContextQuery query = over(scope(PLAN, tactic("a")));
        ActionContext context = query.answer(TYPE_NAME, "plan", 1);
        assertThat(context.governanceFingerprint()).matches("sha256:[0-9a-f]{64}");
        assertThat(query.answer(TYPE_NAME, "plan", 1).governanceFingerprint())
                .isEqualTo(context.governanceFingerprint());
    }

    @Test
    void followingViaFromAnyEntryOfADenseRandomGraphReachesTheActionInExactlyItsHops() {
        for (long seed = 1; seed <= 8; seed++) {
            Random random = new Random(seed);
            List<NodeKind> kinds =
                    List.of(NodeKind.TACTIC, NodeKind.DIRECTIVE, NodeKind.STYLEGUIDE, NodeKind.GLOSSARY_PACK);
            List<Urn> pool = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                pool.add(new Urn(kinds.get(i % kinds.size()), "n" + i));
            }
            List<DrgEdge> edges = new ArrayList<>();
            Relation[] relations = {Relation.SCOPE, Relation.REQUIRES, Relation.SUGGESTS, Relation.VOCABULARY};
            for (int i = 0; i < 160; i++) {
                edges.add(DrgEdge.of(
                        pool.get(random.nextInt(pool.size())),
                        pool.get(random.nextInt(pool.size())),
                        relations[random.nextInt(relations.length)]));
            }
            for (int i = 0; i < 6; i++) {
                edges.add(scope(PLAN, pool.get(random.nextInt(pool.size()))));
            }
            ActionContextQuery query = over(edges.toArray(DrgEdge[]::new));
            for (int depth = ActionContextQuery.MIN_DEPTH; depth <= ActionContextQuery.MAX_DEPTH; depth++) {
                assertViaInvariant(walk(query, depth), edges, "seed " + seed + " depth " + depth);
            }
        }
    }

    private static void assertViaInvariant(List<GuidanceEntry> guidance, List<DrgEdge> edges, String context) {
        Map<Urn, GuidanceEntry> byUrn = new HashMap<>();
        guidance.forEach(e -> byUrn.put(e.urn(), e));
        assertThat(byUrn).as("%s: each urn once", context).hasSameSizeAs(guidance);
        assertThat(urns(guidance)).as("%s: sorted by urn", context).isSorted();
        for (GuidanceEntry entry : guidance) {
            assertThat(edges)
                    .as("%s: %s <- %s is an edge of the graph", context, entry.urn(), entry.via())
                    .anyMatch(e -> e.source().equals(entry.via())
                            && e.target().equals(entry.urn())
                            && e.relation() == entry.relation());
            int steps = 0;
            GuidanceEntry current = entry;
            while (!current.via().equals(PLAN)) {
                GuidanceEntry parent = byUrn.get(current.via());
                assertThat(parent)
                        .as("%s: the via %s of %s is an entry of the answer", context, current.via(), current.urn())
                        .isNotNull();
                current = parent;
                steps++;
                assertThat(steps)
                        .as("%s: the via chain of %s ends", context, entry.urn())
                        .isLessThanOrEqualTo(guidance.size());
            }
            assertThat(current.relation())
                    .as("%s: only a scope entry hangs off the action", context)
                    .isEqualTo(Relation.SCOPE);
            assertThat(steps + 1)
                    .as("%s: edges from %s to the action", context, entry.urn())
                    .isEqualTo(entry.hops());
            assertThat(entry.urn()).isNotIn(PLAN, TYPE);
        }
    }
}
