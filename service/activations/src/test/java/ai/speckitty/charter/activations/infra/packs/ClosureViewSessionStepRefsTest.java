package ai.speckitty.charter.activations.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.application.PacksSession;
import ai.speckitty.charter.activations.domain.ActionContext;
import ai.speckitty.charter.activations.domain.ActionContext.AppliesThrough;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.DocumentRules;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport;
import ai.speckitty.charter.activations.domain.GraphSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ActionContextWalk;
import ai.speckitty.charter.packs.application.ActionContextWalk.Pass;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.EdgeIndex;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.InForceStates;
import ai.speckitty.charter.packs.domain.InForceStates.ArtifactState;
import ai.speckitty.charter.packs.domain.InForceStates.Reason;
import ai.speckitty.charter.packs.domain.InForceStates.State;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.RequiredFloor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every part of the skipped report and of the merged closure that {@link ClosureViewSession} copies through
 * {@code activatable(}, stated for a step of a mission type (#429, T163, D4(a)): one case per call site, each with an
 * artifact of an activatable kind as the control in the same answer. A step ({@code action:<missionType>/<step>}) is a
 * node of the graph and no artifact an activation selects, so the report and the merged closure carry none of it,
 * except the one part whose type is a node: the targets of an enhancer without a target in force, which name the step
 * they enhance.
 *
 * <p>Beside them: the effective graph lists the layer override of a held step and no override of a template, an asset,
 * a guard or a lifecycle (T138, T140); and {@code requiredNotInForce} never carries a step, by construction and by an
 * explicit, documented drop (D16).
 */
class ClosureViewSessionStepRefsTest {

    private static final Urn STEP = Urn.action("M", "s");
    private static final Urn D = urn("directive:D");
    private static final ArtifactRef D_REF = ref("directive:D");

    private static Urn urn(String text) {
        return Urn.parse(text).orElseThrow();
    }

    private static ArtifactRef ref(String text) {
        return ArtifactRef.of(urn(text)).orElseThrow();
    }

    // ---- the states of the in-force resolution: one case per call site ----

    private static ArtifactState active(Urn urn) {
        return new ArtifactState(
                urn, State.ACTIVE, Reason.SELECTED, false, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static ArtifactState state(
            Urn urn, Optional<Urn> satisfiedBy, Optional<Urn> overriddenBy, boolean required) {
        return new ArtifactState(
                urn, State.INACTIVE, Reason.NOT_SELECTED, required, satisfiedBy, overriddenBy, Optional.empty());
    }

    private static InForceStates states(Map<Urn, ArtifactState> byUrn) {
        return new InForceStates(byUrn, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    private static Map<Urn, ArtifactState> byUrn(ArtifactState... states) {
        Map<Urn, ArtifactState> byUrn = new LinkedHashMap<>();
        Arrays.stream(states).forEach(state -> byUrn.put(state.urn(), state));
        return byUrn;
    }

    @Test
    void aStateOfAStepIsNotCarriedAndTheStateOfAnArtifactIs() {
        InForceAnswer answer = ClosureViewSession.inForceOf(states(byUrn(active(D), active(STEP))));

        assertThat(answer.states()).containsOnlyKeys(D_REF);
    }

    @Test
    void aStepThatSatisfiesARequirementIsNotNamedAndAnArtifactIs() {
        Urn required = urn("directive:R");
        Urn required2 = urn("directive:R2");
        InForceAnswer answer = ClosureViewSession.inForceOf(states(byUrn(
                state(required, Optional.of(STEP), Optional.empty(), true),
                state(required2, Optional.of(D), Optional.empty(), true))));

        assertThat(answer.states().get(ref("directive:R")).satisfiedBy())
                .as("a step satisfies nothing")
                .isEmpty();
        assertThat(answer.states().get(ref("directive:R2")).satisfiedBy()).contains(D_REF);
    }

    @Test
    void aTemplateThatOverridesARequiredDirectiveIsNoSatisfierOnTheWriteWhileTheReadShowsItOverridden() {
        // R-4: D16 drops a node that is no activatable artifact: given a resolution that reports the required directive
        // T as overridden by a template, the read names no overrider, and the write (IC-13) finds no satisfier, so a
        // deactivate of T is 409 artifact_required. Since FR-014 (#447, option 1) the packs refuse an overrides edge
        // from a template at registration, on the relationship write and at load, so no stored pack yields this
        // resolution; the test pins only the projection of D16.
        Urn template = urn("template:tpl");
        Urn required = urn("directive:T");
        InForceStates hidden = states(byUrn(new ArtifactState(
                required,
                State.INACTIVE,
                Reason.OVERRIDDEN,
                true,
                Optional.of(template),
                Optional.of(template),
                Optional.empty())));

        MergedSnapshot.ArtifactState read =
                ClosureViewSession.inForceOf(hidden).states().get(ref("directive:T"));
        assertThat(read.state()).isEqualTo(ai.speckitty.charter.activations.domain.EffectiveSet.State.INACTIVE);
        assertThat(read.satisfiedBy())
                .as("a template is no artifact an activation selects")
                .isEmpty();
        assertThat(read.overriddenBy()).isEmpty();

        ActivationDocument denied = ActivationDocument.builder()
                .packs(List.of(PackRef.of("main")))
                .deactivated(ArtifactKind.DIRECTIVE, List.of("T"))
                .build()
                .document();
        var closure = ClosureSnapshots.snapshot()
                .pack("main", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "T")
                .requiredBy("directive:T", "PID-main")
                .build();
        assertThat(DocumentRules.check(denied, closure, query -> ClosureViewSession.inForceOf(hidden)))
                .hasValueSatisfying(refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED));
    }

    @Test
    void aStepThatHidesAnArtifactIsNotNamedAndAnArtifactIs() {
        Urn hidden = urn("directive:H");
        Urn hidden2 = urn("directive:H2");
        InForceAnswer answer = ClosureViewSession.inForceOf(states(byUrn(
                state(hidden, Optional.empty(), Optional.of(STEP), false),
                state(hidden2, Optional.empty(), Optional.of(D), false))));

        assertThat(answer.states().get(ref("directive:H")).overriddenBy())
                .as("a step overrides nothing")
                .isEmpty();
        assertThat(answer.states().get(ref("directive:H2")).overriddenBy()).contains(D_REF);
    }

    @Test
    void aDenyEntryOnAStepIsNotCarriedAndOneOnAnArtifactIs() {
        InForceStates denied = new InForceStates(
                Map.of(),
                List.of(),
                List.of(
                        new InForceStates.DenyNotApplied(STEP, InForceStates.DenyReason.LOCKED),
                        new InForceStates.DenyNotApplied(D, InForceStates.DenyReason.REQUIRED)),
                List.of(),
                List.of(),
                List.of(),
                Map.of());

        assertThat(ClosureViewSession.inForceOf(denied).denyNotApplied())
                .containsExactly(
                        new EffectiveSkippedReport.DenyNotApplied(D_REF, EffectiveSkippedReport.DenyReason.REQUIRED));
    }

    @Test
    void anOverrideNotAppliedOnAStepOrBySomethingThatIsNoneIsNotCarriedAndOneBetweenArtifactsIs() {
        Urn other = urn("directive:O");
        InForceStates kept = new InForceStates(
                Map.of(),
                List.of(),
                List.of(),
                List.of(
                        new InForceStates.OverrideNotApplied(STEP, D),
                        new InForceStates.OverrideNotApplied(D, STEP),
                        new InForceStates.OverrideNotApplied(D, other)),
                List.of(),
                List.of(),
                Map.of());

        assertThat(ClosureViewSession.inForceOf(kept).overrideNotApplied())
                .containsExactly(new EffectiveSkippedReport.OverrideNotApplied(D_REF, ref("directive:O")));
    }

    @Test
    void aStaleIdAndAMustHaveItemThatAreStepsAreNotListedAndTheArtifactsAre() {
        InForceStates lists = new InForceStates(
                Map.of(),
                List.of(STEP, D),
                List.of(),
                List.of(),
                List.of(STEP, urn("directive:N")),
                List.of(),
                Map.of());

        InForceAnswer answer = ClosureViewSession.inForceOf(lists);

        assertThat(answer.staleIds()).containsExactly(D_REF);
        assertThat(answer.requiredNotHeld()).containsExactly(ref("directive:N"));
    }

    @Test
    void aRequiredStepThatIsNotInForceIsDroppedAndARequiredArtifactIsKept() {
        // D16: no preset or pack lock can name a step (the policy and preset schemas exclude `action`), so the required
        // floor holds artifacts an activation can select, and requiredNotInForce never carries a step. Were one fed in,
        // it would be dropped here, explicitly, and not carried as a node the report has no way to say is required.
        InForceStates floor = new InForceStates(
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new InForceStates.RequiredNotInForce(
                                STEP, InForceStates.PruningReason.MISSION_TYPE_NOT_IN_FORCE),
                        new InForceStates.RequiredNotInForce(
                                urn("skill:K"), InForceStates.PruningReason.ENHANCED_TARGET_NOT_IN_FORCE)),
                Map.of());

        assertThat(ClosureViewSession.inForceOf(floor).requiredNotInForce())
                .containsExactly(new EffectiveSkippedReport.RequiredNotInForce(
                        ref("skill:K"), EffectiveSkippedReport.PruningReason.ENHANCED_TARGET_NOT_IN_FORCE));
    }

    @Test
    void thePolicyAndPresetSchemasCannotNameAStepSoTheFloorHoldsNone() throws Exception {
        // The reason the drop above is a guard and not a loss: the files that make an artifact required or locked take
        // artifact kinds only. This pins both schemas, from the contract the service ships, against `action`.
        JsonMapper json = JsonMapper.builder().build();
        JsonNode policy = json.readTree(schema("policy"));
        JsonNode preset = json.readTree(schema("preset"));

        Set<String> policyKinds = new java.util.TreeSet<>(
                policy.get("properties").get("kind_defaults").get("properties").propertyNames());
        Set<String> presetKeys =
                new java.util.TreeSet<>(preset.get("properties").propertyNames());
        String artifactKeys = policy.get("properties")
                .get("artifacts")
                .get("patternProperties")
                .propertyNames()
                .iterator()
                .next();

        assertThat(policyKinds)
                .as("the kinds kind_defaults names")
                .contains("directive", "mission_type", "step_enhancement")
                .doesNotContain("action", "actions");
        assertThat(artifactKeys
                        .substring(artifactKeys.indexOf('(') + 1, artifactKeys.indexOf(')'))
                        .split("\\|"))
                .as("the kinds the artifact keys of policy.yaml take")
                .contains("directive", "mission_type", "step_enhancement")
                .doesNotContain("action");
        assertThat(presetKeys)
                .as("the keys of a preset")
                .contains("activated_directives", "activated_mission_types", "activated_step_enhancements")
                .doesNotContain("activated_action", "activated_actions");
    }

    private static String schema(String name) {
        String resource = "/packs/schemas/" + name + ".schema.json";
        try (InputStream in = ClosureViewSessionStepRefsTest.class.getResourceAsStream(resource)) {
            assertThat(in).as(resource + " is on the classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    // ---- the targets of an enhancer without a target in force: the one part that names a step ----

    @Test
    void theTargetsOfAnEnhancerNameTheStepsItEnhancesAndTheArtifactsAlike() {
        Urn enhancer = urn("skill:E");
        InForceStates enhanced = new InForceStates(
                Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(enhancer, List.of(STEP, D)));

        assertThat(ClosureViewSession.inForceOf(enhanced).enhancedTargets())
                .containsEntry(
                        ref("skill:E"),
                        List.of(new NodeRef(NodeKind.ACTION, "M/s"), new NodeRef(NodeKind.DIRECTIVE, "D")));
    }

    @Test
    void anEnhancerKeyedByAStepIsNotCarried() {
        InForceStates enhanced = new InForceStates(
                Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(STEP, List.of(D)));

        assertThat(ClosureViewSession.inForceOf(enhanced).enhancedTargets())
                .as("an enhancer is an artifact; a step enhances nothing")
                .isEmpty();
    }

    @Test
    void aStepEnhancementOfAStepNoPackHoldsNamesThatStepInTheReportOfTheRealResolution() {
        // The resolution's own branch for an unheld step (rule E6 as amended by OD-6), below the compose: a step
        // enhancer
        // selected next to its mission type, whose one target step no pack holds, is not in force, and the report names
        // the step as a node of kind action. The shape is not production-shaped any more: C37 (ADR-0066 decision 8,
        // WP04) refuses it at compose, so no composed closure reaches this branch. The closure here is built directly,
        // without the compose, to pin what the resolution answers if it ever meets one. A two-target enhancer is no
        // stand-in: rule E6 as built drops its unheld action targets and it never reaches this branch.
        Closure closure = new Closure()
                .pack(
                        "alpha",
                        PackLevel.ORG,
                        List.of(),
                        List.of("mission_type:M", "action:M/s", "step_enhancement:x"),
                        relationship("step_enhancement:x", RelationType.ENHANCES, "action:M/missing"));
        PacksSession session = closure.session();

        InForceAnswer answer = session.inForce(new InForceQuery(
                Set.of(ref("step_enhancement:x"), ref("mission_type:M")), Set.of(), Optional.empty(), Map.of()));

        assertThat(answer.states().get(ref("step_enhancement:x")).state())
                .isEqualTo(ai.speckitty.charter.activations.domain.EffectiveSet.State.INACTIVE);
        assertThat(answer.states().get(ref("mission_type:M")).state())
                .as("the control: the mission type selected beside it is in force")
                .isEqualTo(ai.speckitty.charter.activations.domain.EffectiveSet.State.ACTIVE);
        assertThat(answer.enhancedTargets())
                .containsEntry(ref("step_enhancement:x"), List.of(new NodeRef(NodeKind.ACTION, "M/missing")));
    }

    // ---- the action context: each pass of the walk is the appliesThrough of its entry (FR-009) ----

    @Test
    void theContextMapsEachPassOfTheWalkToTheAppliesThroughOfItsEntryAndOfItsCandidate() {
        Urn action = Urn.action("M", "s");
        ActionContextWalk.Context walked = new ActionContextWalk.Context(
                action,
                true,
                List.of(
                        entry("directive:A", Pass.ACTION, Optional.empty()),
                        entry("directive:T", Pass.MISSION_TYPE, Optional.of(urn("directive:T"))),
                        entry("directive:P", Pass.PROJECT, Optional.of(urn("directive:P")))),
                List.of(
                        new ActionContextWalk.SuggestionCandidate(
                                urn("tactic:s1"),
                                urn("directive:A"),
                                2,
                                Optional.empty(),
                                Optional.empty(),
                                Pass.ACTION),
                        new ActionContextWalk.SuggestionCandidate(
                                urn("tactic:s3"),
                                urn("directive:P"),
                                2,
                                Optional.of("when"),
                                Optional.of("why"),
                                Pass.PROJECT)),
                List.of(),
                List.of());

        ActionContext context = ClosureViewSession.contextOf(walked);

        assertThat(context.entries())
                .extracting(entry -> entry.ref().urn(), ActionContext.Reached::appliesThrough)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("directive:A", AppliesThrough.ACTION),
                        org.assertj.core.groups.Tuple.tuple("directive:T", AppliesThrough.MISSION_TYPE),
                        org.assertj.core.groups.Tuple.tuple("directive:P", AppliesThrough.PROJECT));
        assertThat(context.suggestionCandidates())
                .extracting(candidate -> candidate.ref().urn(), ActionContext.SuggestionCandidate::appliesThrough)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("tactic:s1", AppliesThrough.ACTION),
                        org.assertj.core.groups.Tuple.tuple("tactic:s3", AppliesThrough.PROJECT));
    }

    @Test
    void everyPassOfTheWalkHasAnAppliesThroughOfTheSameName() {
        assertThat(Arrays.stream(Pass.values()).map(ClosureViewSession::appliesThroughOf))
                .extracting(AppliesThrough::name)
                .containsExactlyElementsOf(
                        Arrays.stream(Pass.values()).map(Pass::name).toList());
    }

    private static ActionContextWalk.Entry entry(String urn, Pass pass, Optional<Urn> root) {
        return new ActionContextWalk.Entry(
                urn(urn),
                ActionContextWalk.Step.SCOPE,
                Urn.action("M", "s"),
                1,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                pass,
                root);
    }

    // ---- the merged closure: winners, languages, edges and layer overrides ----

    private static Closure steps() {
        Relationship scopes = relationship("action:M/s", RelationType.SCOPE, "directive:D");
        Relationship enhancesStep = relationship("tactic:t", RelationType.ENHANCES, "action:M/s");
        Relationship requires = relationship("directive:D", RelationType.REQUIRES, "tactic:t");
        return new Closure()
                .pack(
                        "beta",
                        PackLevel.BUILT_IN,
                        List.of(),
                        List.of("mission_type:M", "action:M/s", "directive:D", "tactic:t"),
                        scopes,
                        enhancesStep,
                        requires);
    }

    @Test
    void aStepIsNoWinnerOfTheMergedSnapshotAndAnArtifactIs() {
        MergedSnapshot merged = steps().session().merged();

        assertThat(merged.winners().keySet()).containsExactlyInAnyOrder(ref("mission_type:M"), D_REF, ref("tactic:t"));
    }

    @Test
    void aStepHasNoLanguagesInTheMergedSnapshotAndAnArtifactHas() {
        Closure closure = new Closure()
                .languages(Map.of("action:M/s", List.of("java"), "directive:D", List.of("java")))
                .pack("beta", PackLevel.BUILT_IN, List.of(), List.of("mission_type:M", "action:M/s", "directive:D"));

        MergedSnapshot merged = closure.session().merged();

        assertThat(merged.languages()).containsOnlyKeys(D_REF);
    }

    @Test
    void anEdgeFromAStepAndAnEdgeToAStepAreNotAnEdgeOfTheMergedSnapshotAndOneBetweenArtifactsIs() {
        MergedSnapshot merged = steps().session().merged();

        assertThat(merged.edges())
                .extracting(MergedSnapshot.Edge::source, MergedSnapshot.Edge::target)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(D_REF, ref("tactic:t")));
    }

    private static Closure layered() {
        List<String> held = List.of(
                "mission_type:M",
                "action:M/s",
                "directive:D",
                "template:tpl",
                "asset:logo",
                "guard:g",
                "lifecycle:l",
                "directive:Unselected");
        return new Closure()
                .pack("beta", PackLevel.BUILT_IN, List.of(), held)
                .pack("alpha", PackLevel.ORG, List.of("beta"), held);
    }

    private static InForceQuery selecting(String... urns) {
        return new InForceQuery(
                Arrays.stream(urns)
                        .map(ClosureViewSessionStepRefsTest::ref)
                        .collect(java.util.stream.Collectors.toSet()),
                Set.of(),
                Optional.empty(),
                Map.of());
    }

    @Test
    void aStepsLayerOverrideIsNotInTheMergedSnapshotWhichHoldsArtifactsOnly() {
        MergedSnapshot merged = layered().session().merged();

        assertThat(merged.layerOverrides())
                .extracting(MergedSnapshot.LayerOverride::ref)
                .as("the merged snapshot lists the artifacts an activation can select; the graph lists the steps")
                .containsExactlyInAnyOrder(
                        NodeRef.of(ref("mission_type:M")), NodeRef.of(D_REF), NodeRef.of(ref("directive:Unselected")));
    }

    // ---- the effective graph: a held step's override is listed, the others' are not ----

    @Test
    void theGraphListsTheOverrideOfAHeldStepAndOfAnArtifactInForce() {
        GraphSnapshot graph = layered().session().graph(selecting("mission_type:M", "directive:D"));

        assertThat(graph.layerOverrides())
                .extracting(MergedSnapshot.LayerOverride::ref)
                .containsExactly(
                        new NodeRef(NodeKind.ACTION, "M/s"),
                        new NodeRef(NodeKind.DIRECTIVE, "D"),
                        new NodeRef(NodeKind.MISSION_TYPE, "M"));
        assertThat(graph.layerOverrides().getFirst().winner())
                .isEqualTo(new MergedSnapshot.LayerOverride.Side("alpha", PackLevel.ORG));
        assertThat(graph.layerOverrides().getFirst().loser())
                .isEqualTo(new MergedSnapshot.LayerOverride.Side("beta", PackLevel.BUILT_IN));
    }

    // ---- one resolution per request (NFR-003): the adapter's own cache, not a fake's count ----

    @Test
    void theInForceStatesOfOneRequestAreResolvedOnceForTheSetTheGraphAndTheWalk() {
        ClosureViewSession session = (ClosureViewSession) steps().session();
        InForceQuery a = selecting("mission_type:M", "directive:D");
        InForceQuery b = selecting("directive:D");
        InForceStates first = session.statesOf(a);

        session.inForce(a);
        session.graph(selecting("directive:D", "mission_type:M"));
        session.actionContext("M", "s", 0, a);
        assertThat(session.statesOf(a))
                .as("an equal request is answered by the states resolved first, not by a second resolution")
                .isSameAs(first);

        // The cache holds one request, so a read of another request that goes through it replaces the entry: asking for
        // `a` again then resolves anew. A read that resolved on its own, bypassing the cache, would leave `a` cached.
        session.graph(b);
        InForceStates afterGraph = session.statesOf(a);
        assertThat(afterGraph).as("graph() resolved b through the cache").isNotSameAs(first);
        session.actionContext("M", "s", 0, b);
        assertThat(session.statesOf(a))
                .as("actionContext() resolved b through the cache")
                .isNotSameAs(afterGraph);
    }

    @Test
    void aDifferentRequestOfTheSameSessionResolvesAgain() {
        ClosureViewSession session = (ClosureViewSession) steps().session();
        InForceStates first = session.statesOf(selecting("mission_type:M", "directive:D"));

        InForceStates other = session.statesOf(selecting("directive:D"));

        assertThat(other).as("the control: the cache is keyed by the request").isNotSameAs(first);
        assertThat(session.statesOf(selecting("directive:D"))).isSameAs(other);
    }

    // ---- the names of an action context, judged alone ----

    @Test
    void theNamesOfAnActionAreJudgedAgainstTheMergedClosureAndNothingElse() {
        PacksSession session = steps().session();

        assertThatThrownBy(() -> session.requireAction("missing", "s"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND));
        assertThatThrownBy(() -> session.requireAction("M", "missing"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.ACTION_NOT_FOUND));
        assertThatCode(() -> session.requireAction("M", "s"))
                .as("the control: a held step of a held mission type, in force or not")
                .doesNotThrowAnyException();
    }

    // ---- the fixture: hand-built packs, merged for real ----

    private static Relationship relationship(String from, RelationType type, String to) {
        return new Relationship(urn(from), urn(to), type, Optional.empty(), Optional.empty());
    }

    /** A closure of hand-built packs, merged for real: the facts each pack holds, in merge order. */
    private static final class Closure {

        private final List<PackUnderResolution> packs = new ArrayList<>();
        private final Map<String, PackCompositionFacts> facts = new LinkedHashMap<>();
        private final Map<String, MutabilityPolicySource> policies = new LinkedHashMap<>();
        private Map<String, List<String>> languages = Map.of();

        Closure languages(Map<String, List<String>> declared) {
            this.languages = declared;
            return this;
        }

        Closure pack(String name, PackLevel level, List<String> dependsOn, List<String> held, Relationship... edges) {
            String id = "id-" + name;
            PackDescriptor descriptor = new PackDescriptor(
                    id,
                    "1.0.0",
                    Optional.empty(),
                    name,
                    Optional.empty(),
                    Optional.empty(),
                    PackPaths.none(),
                    dependsOn.stream()
                            .map(other -> new PackDependency("id-" + other, ">=0"))
                            .toList());
            packs.add(new PackUnderResolution(
                    new ConfiguredPack(new PackIdentifier(name), level), descriptor, List.of(), held));
            MutabilityPolicySource policy = MutabilityPolicySource.empty();
            facts.put(
                    name,
                    new PackCompositionFacts(
                            List.of(), held, List.of(), policy, List.of(), Arrays.asList(edges), languages));
            policies.put(id, policy);
            return this;
        }

        PacksSession session() {
            ClosureView view = new ClosureView(
                    List.of(),
                    Map.<ArtifactKind, Set<String>>of(),
                    Map.of(),
                    new RequiredFloor(Map.of()),
                    new PerPackPolicy(policies),
                    packs.stream().map(pack -> pack.descriptor().packId()).toList(),
                    List.of(),
                    Map.of(),
                    "sha256:merged",
                    EdgeIndex.of(packs, facts),
                    new ClosureView.MergeInputs(packs, facts),
                    "sha256:merged");
            return new ClosureViewSession(view, PacksAvailability.snapshotOf(view));
        }
    }
}
