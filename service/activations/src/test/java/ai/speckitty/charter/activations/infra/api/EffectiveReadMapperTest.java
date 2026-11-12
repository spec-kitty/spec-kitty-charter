package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.activations.application.EffectiveReadService.After;
import ai.speckitty.charter.activations.application.EffectiveReadService.ContextRead;
import ai.speckitty.charter.activations.application.EffectiveReadService.GraphRead;
import ai.speckitty.charter.activations.application.EffectiveReadService.Page;
import ai.speckitty.charter.activations.application.EffectiveReadService.PageQuery;
import ai.speckitty.charter.activations.application.EffectiveReadService.Report;
import ai.speckitty.charter.activations.domain.ActionContext.AppliesThrough;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.EffectiveContext;
import ai.speckitty.charter.activations.domain.EffectiveSet.EffectiveArtifact;
import ai.speckitty.charter.activations.domain.EffectiveSet.Policy;
import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.EffectiveSet.Suggestion;
import ai.speckitty.charter.activations.domain.EffectiveSet.Summary;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.DenyNotApplied;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.DenyReason;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.EnhancerWithoutTarget;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.Overridden;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.OverrideNotApplied;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.PinNotHeld;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.PruningReason;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.RequiredNotInForce;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.GraphSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.activations.domain.SkippedReport.SkippedDeactivated;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.api.model.Activation;
import ai.speckitty.charter.api.model.ActivationSkippedReport;
import ai.speckitty.charter.api.model.ArtifactReference;
import ai.speckitty.charter.api.model.EffectiveActionContext;
import ai.speckitty.charter.api.model.EffectiveArtifactList;
import ai.speckitty.charter.api.model.EffectiveContextArtifact;
import ai.speckitty.charter.api.model.EffectiveGraph;
import ai.speckitty.charter.api.model.EffectiveGraphEdge;
import ai.speckitty.charter.api.model.EffectiveGraphNode;
import ai.speckitty.charter.api.model.NodeReference;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.web.InvalidParameter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The wire form of the effective reads and of the page cursor: an item in the contract's shape, the {@code summary} of
 * the whole set, the paging state and the {@code self}, {@code first} and {@code next} links of a page, the ten read
 * buckets of the skipped report, and the {@code denyNotApplied} of {@code getActivation} only when there is one.
 */
class EffectiveReadMapperTest {

    private static final ArtifactRef A = new ArtifactRef(ArtifactKind.DIRECTIVE, "A");
    private static final ArtifactRef B = new ArtifactRef(ArtifactKind.DIRECTIVE, "B");
    private static final ArtifactRef T = new ArtifactRef(ArtifactKind.TACTIC, "t");
    private static final String SET_TAG = "sha256:" + "ab".repeat(32);

    private final ActivationMapper activations = new ActivationMapper("/api/v1");
    private final EffectiveReadMapper mapper = new EffectiveReadMapper(
            activations, () -> List.of(ArtifactKind.DIRECTIVE, ArtifactKind.TACTIC, ArtifactKind.AGENT_PROFILE));
    private final StoredActivation stored = StoredActivation.of(
            new ActivationId("a-1"), ActivationDocument.builder().build().document());

    private static EffectiveArtifact item(
            ArtifactRef ref, List<ArtifactRef> satisfiedBy, Optional<Suggestion> suggestion) {
        return new EffectiveArtifact(
                ref,
                "main",
                PackLevel.ORG,
                State.INACTIVE,
                true,
                satisfiedBy,
                Policy.LOCKED,
                Reason.NOT_SELECTED,
                suggestion);
    }

    private Page page(PageQuery query, Optional<After> next, List<EffectiveArtifact> items) {
        return new Page(stored, "sha256:source", "sha256:closure", new Summary(1, 2, 3, 4, 5, 6), items, next, query);
    }

    // ---- the list

    @Test
    void anItemIsMappedWithItsProvenanceItsRequirementItsSatisfierAndItsSuggestion() {
        Page page = page(
                new PageQuery(Optional.empty(), Optional.empty(), 50, Optional.empty()),
                Optional.empty(),
                List.of(
                        item(
                                A,
                                List.of(T, B),
                                Optional.of(new Suggestion(Optional.of("on release"), Optional.of("it helps")))),
                        item(T, List.of(), Optional.empty())));

        EffectiveArtifactList wire = mapper.toList(page, true);

        assertThat(wire.getActivationId()).isEqualTo("a-1");
        assertThat(wire.getRevision()).isEqualTo(stored.revision().token());
        assertThat(wire.getGovernanceFingerprint()).isEqualTo("sha256:closure");
        assertThat(wire.getSummary().getActive()).isEqualTo(1);
        assertThat(wire.getSummary().getInactive()).isEqualTo(2);
        assertThat(wire.getSummary().getExcluded()).isEqualTo(3);
        assertThat(wire.getSummary().getRequired()).isEqualTo(4);
        assertThat(wire.getSummary().getLocked()).isEqualTo(5);
        assertThat(wire.getSummary().getSkipped()).isEqualTo(6);
        ai.speckitty.charter.api.model.EffectiveArtifact first = wire.getItems().getFirst();
        assertThat(first.getKind()).isEqualTo(ai.speckitty.charter.api.model.EffectiveArtifact.KindEnum.DIRECTIVE);
        assertThat(first.getArtifactId()).isEqualTo("A");
        assertThat(first.getPackId()).isEqualTo("main");
        assertThat(first.getLevel()).isEqualTo(ai.speckitty.charter.api.model.EffectiveArtifact.LevelEnum.ORG);
        assertThat(first.getState()).isEqualTo(ai.speckitty.charter.api.model.EffectiveArtifact.StateEnum.INACTIVE);
        assertThat(first.getRequired()).isTrue();
        assertThat(first.getPolicy()).isEqualTo(ai.speckitty.charter.api.model.EffectiveArtifact.PolicyEnum.LOCKED);
        assertThat(first.getReason())
                .isEqualTo(ai.speckitty.charter.api.model.EffectiveArtifact.ReasonEnum.NOT_SELECTED);
        assertThat(first.getSatisfiedBy())
                .as("every overrider, in the order the resolution gave (#439), never sorted again")
                .containsExactly(
                        new ArtifactReference(ArtifactReference.KindEnum.TACTIC, "t"),
                        new ArtifactReference(ArtifactReference.KindEnum.DIRECTIVE, "B"));
        assertThat(first.getSuggestion().getWhen()).isEqualTo("on release");
        assertThat(first.getSuggestion().getReason()).as("the edge's rationale").isEqualTo("it helps");
        ai.speckitty.charter.api.model.EffectiveArtifact second =
                wire.getItems().get(1);
        assertThat(second.getSatisfiedBy()).isNull();
        assertThat(second.getSuggestion()).isNull();
    }

    @Test
    void aSuggestionWithOnlyAReasonOrOnlyAWhenKeepsTheOtherMemberAbsent() {
        EffectiveArtifactList wire = mapper.toList(
                page(
                        new PageQuery(Optional.empty(), Optional.empty(), 50, Optional.empty()),
                        Optional.empty(),
                        List.of(
                                item(A, List.of(), Optional.of(new Suggestion(Optional.empty(), Optional.of("why")))),
                                item(
                                        B,
                                        List.of(),
                                        Optional.of(new Suggestion(Optional.of("when"), Optional.empty()))))),
                true);

        assertThat(wire.getItems()).hasSize(2);
        assertThat(wire.getItems().getFirst().getSuggestion().getWhen()).isNull();
        assertThat(wire.getItems().getFirst().getSuggestion().getReason()).isEqualTo("why");
        assertThat(wire.getItems().get(1).getSuggestion().getWhen()).isEqualTo("when");
        assertThat(wire.getItems().get(1).getSuggestion().getReason()).isNull();
    }

    @Test
    void theLastPageHasNoCursorAndNoNextLink() {
        EffectiveArtifactList wire = mapper.toList(
                page(
                        new PageQuery(Optional.empty(), Optional.empty(), 50, Optional.empty()),
                        Optional.empty(),
                        List.of(item(A, List.of(), Optional.empty()))),
                true);

        assertThat(wire.getPageInfo().getHasNextPage()).isFalse();
        assertThat(wire.getPageInfo().getNextPageCursor()).isNull();
        assertThat(wire.getPageInfo().getPageSize()).isEqualTo(50);
        assertThat(wire.getLinks()).containsOnlyKeys("self", "first");
        assertThat(wire.getLinks().get("self").getHref()).isEqualTo("/api/v1/activations/a-1/artifacts?pageSize=50");
        assertThat(wire.getLinks().get("first").getHref()).isEqualTo("/api/v1/activations/a-1/artifacts?pageSize=50");
    }

    @Test
    void aPageThatIsNotTheLastHasACursorOfItsLastItemAndANextLinkThatKeepsTheFiltersAndThePageSize() {
        PageQuery query =
                new PageQuery(Optional.of(ArtifactKind.DIRECTIVE), Optional.of(State.ACTIVE), 2, Optional.empty());
        EffectiveArtifactList wire = mapper.toList(
                page(
                        query,
                        Optional.of(new After(B, SET_TAG, "unused")),
                        List.of(item(A, List.of(), Optional.empty()), item(B, List.of(), Optional.empty()))),
                true);

        String cursor = wire.getPageInfo().getNextPageCursor();
        assertThat(wire.getPageInfo().getHasNextPage()).isTrue();
        assertThat(wire.getPageInfo().getPageSize()).isEqualTo(2);
        assertThat(cursor).isNotBlank();
        After decoded = EffectiveCursor.decode(cursor);
        assertThat(decoded.position()).isEqualTo(B);
        assertThat(decoded.setTag()).isEqualTo(SET_TAG);
        assertThat(decoded.cursor()).isEqualTo(cursor);
        assertThat(wire.getLinks()).containsKeys("self", "first", "next");
        assertThat(wire.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts?kind=directive&state=active&pageSize=2");
        assertThat(wire.getLinks().get("first").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts?kind=directive&state=active&pageSize=2");
        assertThat(wire.getLinks().get("next").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts?kind=directive&state=active&pageSize=2&pageCursor="
                        + cursor);
    }

    @Test
    void thePageAfterACursorLinksItselfWithTheCursorItWasAskedWith() {
        PageQuery query =
                new PageQuery(Optional.empty(), Optional.empty(), 5, Optional.of(new After(A, SET_TAG, "the-cursor")));

        EffectiveArtifactList wire = mapper.toList(page(query, Optional.empty(), List.of()), true);

        assertThat(wire.getLinks()).containsKeys("self", "first");
        assertThat(wire.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts?pageSize=5&pageCursor=the-cursor");
        assertThat(wire.getLinks().get("first").getHref()).isEqualTo("/api/v1/activations/a-1/artifacts?pageSize=5");
    }

    @Test
    void theLinksRepeatThePageSizeOnlyWhenTheRequestNamedOne() {
        PageQuery query = new PageQuery(Optional.of(ArtifactKind.DIRECTIVE), Optional.empty(), 50, Optional.empty());

        EffectiveArtifactList wire = mapper.toList(
                page(
                        query,
                        Optional.of(new After(B, SET_TAG, "unused")),
                        List.of(item(A, List.of(), Optional.empty()))),
                false);

        assertThat(wire.getPageInfo().getPageSize())
                .as("the page size applied is still reported")
                .isEqualTo(50);
        assertThat(wire.getLinks().get("self").getHref()).isEqualTo("/api/v1/activations/a-1/artifacts?kind=directive");
        assertThat(wire.getLinks().get("first").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts?kind=directive");
        assertThat(wire.getLinks().get("next").getHref())
                .startsWith("/api/v1/activations/a-1/artifacts?kind=directive&pageCursor=")
                .doesNotContain("pageSize");
    }

    // ---- the cursor

    @Test
    void aCursorCarriesThePositionAndTheSetTagIncludingAnIdWithASlash() {
        ArtifactRef withSlash = new ArtifactRef(ArtifactKind.AGENT_PROFILE, "software-dev/lead");

        String cursor = EffectiveCursor.encode(withSlash, SET_TAG);
        After decoded = EffectiveCursor.decode(cursor);

        assertThat(decoded.position()).isEqualTo(withSlash);
        assertThat(decoded.setTag()).isEqualTo(SET_TAG);
        assertThat(cursor).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void aMalformedCursorIsA400WithoutACode() {
        for (String malformed : List.of("not base64 !", "", "YQ", "c2hhMjU2OnRvby1zaG9ydAo", "Zm9vL2Jhcg")) {
            assertThatThrownBy(() -> EffectiveCursor.decode(malformed))
                    .as(malformed)
                    .isInstanceOfSatisfying(
                            InvalidParameter.class,
                            refused -> assertThat(refused.detail())
                                    .isEqualTo("The query parameter pageCursor is \"" + malformed
                                            + "\", which is not a cursor this list gave."));
        }
    }

    // ---- the skipped report

    @Test
    void theSkippedReportMapsEveryBucketAndTheWriteBucketsStayEmpty() {
        EffectiveSkippedReport skipped = new EffectiveSkippedReport(
                List.of(A),
                List.of(new DenyNotApplied(B, DenyReason.LOCKED)),
                List.of(new EnhancerWithoutTarget(T, List.of(NodeRef.of(B), NodeRef.of(A)))),
                List.of(new OverrideNotApplied(A, List.of(T, B))),
                List.of(new ArtifactRef(ArtifactKind.DIRECTIVE, "GONE")),
                List.of(new RequiredNotInForce(T, PruningReason.ENHANCED_TARGET_NOT_IN_FORCE)),
                List.of(new PinNotHeld("main", "9.9.9")),
                List.of(B),
                List.of(new ArtifactRef(ArtifactKind.TACTIC, "step")),
                List.of(new SkippedDeactivated(A, false, List.of())),
                List.of(new Overridden(T, List.of(B, A))));

        ActivationSkippedReport wire = mapper.toSkipped(new Report(stored, "sha256:source", "sha256:closure", skipped));

        assertThat(wire.getActivationId()).isEqualTo("a-1");
        assertThat(wire.getRevision()).isEqualTo(stored.revision().token());
        assertThat(wire.getGovernanceFingerprint()).isEqualTo("sha256:closure");
        ai.speckitty.charter.api.model.SkippedReport report = wire.getReport();
        assertThat(report.getStaleIds())
                .containsExactly(new ArtifactReference(ArtifactReference.KindEnum.DIRECTIVE, "A"));
        assertTheDenyEnhancerAndOverrideBuckets(report);
        assertTheRequiredPinLanguageAndDeactivatedBuckets(report);
        assertThat(report.getSkippedByScope()).isEmpty();
        assertThat(report.getUnreachedBehindDeactivated()).isEmpty();
        assertThat(report.getSkippedShared()).isEmpty();
        assertThat(wire.getLinks()).containsOnlyKeys("self");
        assertThat(wire.getLinks().get("self").getHref()).isEqualTo("/api/v1/activations/a-1/skipped");
    }

    private static void assertTheDenyEnhancerAndOverrideBuckets(ai.speckitty.charter.api.model.SkippedReport report) {
        assertThat(report.getDenyNotApplied()).singleElement().satisfies(entry -> {
            assertThat(entry.getKind()).isEqualTo(ai.speckitty.charter.api.model.DenyNotApplied.KindEnum.DIRECTIVE);
            assertThat(entry.getArtifactId()).isEqualTo("B");
            assertThat(entry.getReason()).isEqualTo(ai.speckitty.charter.api.model.DenyNotApplied.ReasonEnum.LOCKED);
        });
        assertThat(report.getEnhancedTargetNotInForce()).singleElement().satisfies(entry -> {
            assertThat(entry.getArtifactId()).isEqualTo("t");
            assertThat(entry.getTargets())
                    .containsExactly(
                            new NodeReference(NodeReference.KindEnum.DIRECTIVE, "A"),
                            new NodeReference(NodeReference.KindEnum.DIRECTIVE, "B"));
        });
        assertThat(report.getOverrideNotApplied()).singleElement().satisfies(entry -> {
            assertThat(entry.getArtifactId()).isEqualTo("A");
            assertThat(entry.getOverriddenBy())
                    .containsExactly(
                            new ArtifactReference(ArtifactReference.KindEnum.TACTIC, "t"),
                            new ArtifactReference(ArtifactReference.KindEnum.DIRECTIVE, "B"));
        });
    }

    private static void assertTheRequiredPinLanguageAndDeactivatedBuckets(
            ai.speckitty.charter.api.model.SkippedReport report) {
        assertThat(report.getRequiredNotHeld())
                .containsExactly(new ArtifactReference(ArtifactReference.KindEnum.DIRECTIVE, "GONE"));
        assertThat(report.getRequiredNotInForce()).singleElement().satisfies(entry -> {
            assertThat(entry.getArtifactId()).isEqualTo("t");
            assertThat(entry.getReason())
                    .isEqualTo(
                            ai.speckitty.charter.api.model.RequiredNotInForce.ReasonEnum.ENHANCED_TARGET_NOT_IN_FORCE);
        });
        assertThat(report.getPinNotHeld()).singleElement().satisfies(pin -> {
            assertThat(pin.getPackId()).isEqualTo("main");
            assertThat(pin.getVersion()).isEqualTo("9.9.9");
        });
        assertThat(report.getLanguageFiltered())
                .containsExactly(new ArtifactReference(ArtifactReference.KindEnum.DIRECTIVE, "B"));
        assertThat(report.getMissionTypeNotInForce())
                .containsExactly(new ArtifactReference(ArtifactReference.KindEnum.TACTIC, "step"));
        assertThat(report.getSkippedDeactivated()).singleElement().satisfies(entry -> {
            assertThat(entry.getArtifactId()).isEqualTo("A");
            assertThat(entry.getViaRequires()).isFalse();
            assertThat(entry.getRequiredBy()).isEmpty();
        });
        assertThat(report.getOverridden()).singleElement().satisfies(entry -> {
            assertThat(entry.getArtifactId()).isEqualTo("t");
            assertThat(entry.getOverriddenBy())
                    .containsExactly(
                            new ArtifactReference(ArtifactReference.KindEnum.DIRECTIVE, "B"),
                            new ArtifactReference(ArtifactReference.KindEnum.DIRECTIVE, "A"));
        });
    }

    // ---- getActivation's deny notice

    @Test
    void denyNotAppliedIsAListOfUrnsOnlyWhenThereIsOne() {
        Activation none = activations.toActivation(stored, List.of());
        Activation some = activations.toActivation(stored, List.of(T, A));

        assertThat(none.getDenyNotApplied())
                .as("present only when there is one")
                .isNull();
        assertThat(some.getDenyNotApplied()).containsExactly("tactic:t", "directive:A");
        assertThat(some.getRevision()).isEqualTo(stored.revision().token());
    }

    // ---- the item links (WP08): effective-artifact and artifact, for the kinds the pack-scoped routes serve

    @Test
    void anItemOfAServedKindLinksItsEffectiveReadAndItsWinnersPackScopedRead() {
        EffectiveArtifactList wire = mapper.toList(
                page(
                        new PageQuery(Optional.empty(), Optional.empty(), 50, Optional.empty()),
                        Optional.empty(),
                        List.of(item(A, List.of(), Optional.empty()), item(T, List.of(), Optional.empty()))),
                true);

        assertThat(wire.getItems().get(0).getLinks()).containsOnlyKeys("effective-artifact", "artifact");
        assertThat(wire.getItems().get(0).getLinks().get("effective-artifact").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/directive/A");
        assertThat(wire.getItems().get(0).getLinks().get("artifact").getHref())
                .as("the pack that holds the version in force")
                .isEqualTo("/api/v1/packs/main/artifacts/directive/A");
        assertThat(wire.getItems().get(1).getLinks().get("effective-artifact").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/tactic/t");
        assertThat(wire.getItems().get(1).getLinks().get("artifact").getHref())
                .isEqualTo("/api/v1/packs/main/artifacts/tactic/t");
    }

    @Test
    void anItemOfAKindNoPackScopedRouteServesCarriesNoLinks() {
        ArtifactRef paradigm = new ArtifactRef(ArtifactKind.PARADIGM, "p");

        EffectiveArtifactList wire = mapper.toList(
                page(
                        new PageQuery(Optional.empty(), Optional.empty(), 50, Optional.empty()),
                        Optional.empty(),
                        List.of(item(paradigm, List.of(), Optional.empty()), item(A, List.of(), Optional.empty()))),
                true);

        assertThat(wire.getItems().get(0).getLinks()).as("not served").isNull();
        assertThat(wire.getItems().get(1).getLinks()).as("the control, served").isNotNull();
    }

    @Test
    void theItemLinksPercentEncodeAnIdAndFollowTheKindsTheServiceListsToday() {
        ArtifactRef spaced = new ArtifactRef(ArtifactKind.DIRECTIVE, "a b/c");
        EffectiveReadMapper onlyTactics = new EffectiveReadMapper(activations, () -> List.of(ArtifactKind.TACTIC));

        EffectiveArtifactList wire = mapper.toList(
                page(
                        new PageQuery(Optional.empty(), Optional.empty(), 50, Optional.empty()),
                        Optional.empty(),
                        List.of(item(spaced, List.of(), Optional.empty()))),
                true);
        EffectiveArtifactList narrower = onlyTactics.toList(
                page(
                        new PageQuery(Optional.empty(), Optional.empty(), 50, Optional.empty()),
                        Optional.empty(),
                        List.of(item(spaced, List.of(), Optional.empty()))),
                true);

        assertThat(wire.getItems()
                        .getFirst()
                        .getLinks()
                        .get("effective-artifact")
                        .getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/directive/a%20b%2Fc");
        assertThat(wire.getItems().getFirst().getLinks().get("artifact").getHref())
                .isEqualTo("/api/v1/packs/main/artifacts/directive/a%20b%2Fc");
        assertThat(narrower.getItems().getFirst().getLinks())
                .as("the kinds are asked of the service each time, not fixed")
                .isNull();
    }

    @Test
    void aGraphNodeOfAServedKindLinksItsEffectiveReadAndAnActionOrAnUnservedNodeLinksNothing() {
        NodeRef step = new NodeRef(NodeKind.ACTION, "software-dev/implement");
        NodeRef missionType = new NodeRef(NodeKind.MISSION_TYPE, "software-dev");
        GraphSnapshot graph =
                new GraphSnapshot(List.of(step, missionType, NodeRef.of(A), NodeRef.of(T)), List.of(), List.of());

        EffectiveGraph wire = mapper.toGraph(new GraphRead(stored, "sha256:source", "sha256:closure", graph));

        assertThat(wire.getNodes().get(0).getLinks())
                .as("an action is no artifact")
                .isNull();
        assertThat(wire.getNodes().get(1).getLinks())
                .as("a mission type is not served")
                .isNull();
        assertThat(wire.getNodes().get(2).getLinks()).containsOnlyKeys("effective-artifact");
        assertThat(wire.getNodes().get(2).getLinks().get("effective-artifact").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/directive/A");
        assertThat(wire.getNodes().get(3).getLinks().get("effective-artifact").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/tactic/t");
    }

    @Test
    void anItemOfTheActionContextLinksLikeAnItemOfTheList() {
        EffectiveContext context = new EffectiveContext(
                List.of(new EffectiveContext.Item(item(A, List.of(), Optional.empty()), AppliesThrough.ACTION)),
                List.of(),
                EffectiveSkippedReport.empty());

        EffectiveActionContext wire = mapper.toContext(contextRead(context), true);

        assertThat(wire.getArtifacts().getFirst().getLinks()).containsOnlyKeys("effective-artifact", "artifact");
        assertThat(wire.getArtifacts()
                        .getFirst()
                        .getLinks()
                        .get("effective-artifact")
                        .getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/directive/A");
        assertThat(wire.getArtifacts().getFirst().getLinks().get("artifact").getHref())
                .isEqualTo("/api/v1/packs/main/artifacts/directive/A");
    }

    // ---- the mission kinds and the steps (#99 WP06)

    private final EffectiveReadMapper missions = new EffectiveReadMapper(
            activations,
            () -> List.of(ArtifactKind.DIRECTIVE, ArtifactKind.MISSION_TYPE, ArtifactKind.MISSION_STEP_CONTRACT));

    @Test
    void anItemOfAMissionKindLinksItsReadUnderTheActivationAndThePackRead() {
        ArtifactRef type = new ArtifactRef(ArtifactKind.MISSION_TYPE, "software-dev");
        ArtifactRef contract = new ArtifactRef(ArtifactKind.MISSION_STEP_CONTRACT, "specify");
        Page page = page(
                new PageQuery(Optional.empty(), Optional.empty(), 50, Optional.empty()),
                Optional.empty(),
                List.of(item(contract, List.of(), Optional.empty()), item(type, List.of(), Optional.empty())));

        EffectiveArtifactList wire = missions.toList(page, false);

        assertThat(wire.getItems().get(0).getLinks().get("effective-artifact").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/mission_step_contract/specify");
        assertThat(wire.getItems().get(0).getLinks().get("artifact").getHref())
                .isEqualTo("/api/v1/packs/main/artifacts/mission_step_contract/specify");
        assertThat(wire.getItems().get(1).getLinks().get("effective-artifact").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/mission_type/software-dev");
        assertThat(wire.getItems().get(1).getLinks().get("artifact").getHref())
                .isEqualTo("/api/v1/packs/main/artifacts/mission_type/software-dev");
    }

    @Test
    void aStepNodeLinksItsStepReadUnderTheActivationWhileMissionTypesAreServed() {
        NodeRef step = new NodeRef(NodeKind.ACTION, "software-dev/implement");
        NodeRef type = new NodeRef(NodeKind.MISSION_TYPE, "software-dev");
        NodeRef contract = new NodeRef(NodeKind.MISSION_STEP_CONTRACT, "implement");
        GraphSnapshot graph = new GraphSnapshot(List.of(step, contract, type), List.of(), List.of());

        EffectiveGraph wire = missions.toGraph(new GraphRead(stored, "sha256:source", "sha256:closure", graph));

        assertThat(wire.getNodes().get(0).getLinks()).containsOnlyKeys("effective-artifact");
        assertThat(wire.getNodes().get(0).getLinks().get("effective-artifact").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/implement");
        assertThat(wire.getNodes().get(1).getLinks().get("effective-artifact").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/mission_step_contract/implement");
        assertThat(wire.getNodes().get(2).getLinks()).containsOnlyKeys("effective-artifact");
        assertThat(wire.getNodes().get(2).getLinks().get("effective-artifact").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/mission_type/software-dev");
        assertThat(mapper.toGraph(new GraphRead(stored, "sha256:source", "sha256:closure", graph))
                        .getNodes()
                        .getFirst()
                        .getLinks())
                .as("a step links nothing while mission types are not served")
                .isNull();
    }

    @Test
    void theContextLinksItsStepReadUnderTheActivationAndInThePackThatHoldsTheMissionType() {
        EffectiveContext empty = new EffectiveContext(List.of(), List.of(), EffectiveSkippedReport.empty());
        EffectiveTag.ContextQuery query = new EffectiveTag.ContextQuery("software-dev", "implement", 0);
        ContextRead held =
                new ContextRead(stored, "sha256:source", "sha256:closure", query, empty, Optional.of("proj"));

        EffectiveActionContext wire = missions.toContext(held, false);

        assertThat(wire.getLinks()).containsOnlyKeys("self", "effective-artifact", "artifact");
        assertThat(wire.getLinks().get("effective-artifact").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/implement");
        assertThat(wire.getLinks().get("artifact").getHref())
                .isEqualTo("/api/v1/packs/proj/artifacts/mission_type/software-dev/steps/implement");
        assertThat(missions.toContext(contextRead(empty), false).getLinks())
                .as("no artifact link when the effective set names no holding pack")
                .containsOnlyKeys("self", "effective-artifact");
        assertThat(mapper.toContext(held, false).getLinks())
                .as("no step link while mission types are not served")
                .containsOnlyKeys("self");
    }

    // ---- the graph and the action context (IC-11)

    @Test
    void theGraphIsMappedWithItsNodesEdgesLayerOverridesAndSelfInTheOrderGiven() {
        NodeRef step = new NodeRef(NodeKind.ACTION, "software-dev/implement");
        GraphSnapshot graph = new GraphSnapshot(
                List.of(step, NodeRef.of(A), NodeRef.of(B)),
                List.of(
                        new GraphSnapshot.Edge(step, RelationType.SCOPE, NodeRef.of(A)),
                        new GraphSnapshot.Edge(NodeRef.of(A), RelationType.REQUIRES, NodeRef.of(B))),
                List.of(new MergedSnapshot.LayerOverride(
                        step,
                        new MergedSnapshot.LayerOverride.Side("proj", PackLevel.PROJECT),
                        new MergedSnapshot.LayerOverride.Side("core", PackLevel.BUILT_IN))));

        EffectiveGraph wire = mapper.toGraph(new GraphRead(stored, "sha256:source", "sha256:closure", graph));

        assertThat(wire.getActivationId()).isEqualTo("a-1");
        assertThat(wire.getRevision()).isEqualTo(stored.revision().token());
        assertThat(wire.getGovernanceFingerprint()).isEqualTo("sha256:closure");
        assertThat(wire.getNodes())
                .extracting(EffectiveGraphNode::getUrn, node -> node.getKind().getValue())
                .containsExactly(
                        tuple("action:software-dev/implement", "action"),
                        tuple("directive:A", "directive"),
                        tuple("directive:B", "directive"));
        assertThat(wire.getEdges())
                .extracting(
                        EffectiveGraphEdge::getSource,
                        edge -> edge.getRelation().getValue(),
                        EffectiveGraphEdge::getTarget)
                .containsExactly(
                        tuple("action:software-dev/implement", "scope", "directive:A"),
                        tuple("directive:A", "requires", "directive:B"));
        assertThat(wire.getLayerOverrides()).singleElement().satisfies(replaced -> {
            assertThat(replaced.getArtifact()).isEqualTo("action:software-dev/implement");
            assertThat(replaced.getWinner().getPackId()).isEqualTo("proj");
            assertThat(replaced.getWinner().getLevel().getValue()).isEqualTo("project");
            assertThat(replaced.getLoser().getPackId()).isEqualTo("core");
            assertThat(replaced.getLoser().getLevel().getValue()).isEqualTo("built-in");
        });
        assertThat(wire.getLinks()).containsOnlyKeys("self");
        assertThat(wire.getLinks().get("self").getHref()).isEqualTo("/api/v1/activations/a-1/graph");
    }

    @Test
    void anEmptyGraphIsMappedWithEmptyListsAndNotAbsentOnes() {
        EffectiveGraph wire =
                mapper.toGraph(new GraphRead(stored, "sha256:source", "sha256:closure", GraphSnapshot.empty()));

        assertThat(wire.getNodes()).isEmpty();
        assertThat(wire.getEdges()).isEmpty();
        assertThat(wire.getLayerOverrides()).isEmpty();
    }

    private ContextRead contextRead(EffectiveContext context) {
        return new ContextRead(
                stored,
                "sha256:source",
                "sha256:closure",
                new EffectiveTag.ContextQuery("software-dev", "implement", 1),
                context);
    }

    @Test
    void theContextIsMappedWithItsArtifactsSuggestionEnhancementsAndSkippedReport() {
        EffectiveContext context = new EffectiveContext(
                List.of(
                        new EffectiveContext.Item(
                                new EffectiveArtifact(
                                        A,
                                        "main",
                                        PackLevel.ORG,
                                        State.ACTIVE,
                                        false,
                                        List.of(),
                                        Policy.OPEN,
                                        Reason.SELECTED,
                                        Optional.empty()),
                                AppliesThrough.MISSION_TYPE),
                        new EffectiveContext.Item(
                                item(
                                        T,
                                        List.of(B, A),
                                        Optional.of(new Suggestion(Optional.of("when"), Optional.of("why")))),
                                AppliesThrough.PROJECT)),
                List.of(
                        new EffectiveContext.Enhancement(
                                new NodeRef(NodeKind.ACTION, "software-dev/implement"),
                                List.of(new ArtifactRef(ArtifactKind.STEP_ENHANCEMENT, "x"))),
                        new EffectiveContext.Enhancement(NodeRef.of(A), List.of(B))),
                EffectiveSkippedReport.empty());

        EffectiveActionContext wire = mapper.toContext(contextRead(context), true);

        assertThat(wire.getActivationId()).isEqualTo("a-1");
        assertThat(wire.getMissionType()).isEqualTo("software-dev");
        assertThat(wire.getAction()).isEqualTo("implement");
        assertThat(wire.getGovernanceFingerprint()).isEqualTo("sha256:closure");
        assertThat(wire.getArtifacts())
                .extracting(
                        EffectiveContextArtifact::getArtifactId,
                        item -> item.getAppliesThrough().getValue())
                .as("each item carries its own appliesThrough")
                .containsExactly(tuple("A", "mission_type"), tuple("t", "project"));
        assertThat(wire.getArtifacts().get(0).getKind().getValue()).isEqualTo("directive");
        assertThat(wire.getArtifacts().get(0).getPackId()).isEqualTo("main");
        assertThat(wire.getArtifacts().get(0).getLevel().getValue()).isEqualTo("org");
        assertThat(wire.getArtifacts().get(0).getState().getValue()).isEqualTo("active");
        assertThat(wire.getArtifacts().get(0).getPolicy().getValue()).isEqualTo("open");
        assertThat(wire.getArtifacts().get(0).getReason().getValue()).isEqualTo("selected");
        assertThat(wire.getArtifacts().get(1).getState().getValue()).isEqualTo("inactive");
        assertThat(wire.getArtifacts().get(1).getReason().getValue()).isEqualTo("not_selected");
        assertThat(wire.getArtifacts().get(0).getSuggestion()).isNull();
        assertThat(wire.getArtifacts().get(0).getSatisfiedBy())
                .as("absent, never empty")
                .isNull();
        assertThat(wire.getArtifacts().get(1).getSatisfiedBy())
                .as("every overrider, in the resolution's order (#439)")
                .containsExactly(
                        new ArtifactReference(ArtifactReference.KindEnum.DIRECTIVE, "B"),
                        new ArtifactReference(ArtifactReference.KindEnum.DIRECTIVE, "A"));
        assertThat(wire.getArtifacts().get(1).getSuggestion().getWhen()).isEqualTo("when");
        assertThat(wire.getArtifacts().get(1).getSuggestion().getReason()).isEqualTo("why");
        assertThat(wire.getEnhancements()).hasSize(2);
        assertThat(wire.getEnhancements().get(0).getTarget())
                .isEqualTo(new NodeReference(NodeReference.KindEnum.ACTION, "software-dev/implement"));
        assertThat(wire.getEnhancements().get(0).getEnhancedBy())
                .containsExactly(new ArtifactReference(ArtifactReference.KindEnum.STEP_ENHANCEMENT, "x"));
        assertThat(wire.getEnhancements().get(1).getTarget())
                .isEqualTo(new NodeReference(NodeReference.KindEnum.DIRECTIVE, "A"));
        assertThat(wire.getSkipped().getMissionTypeNotInForce()).isEmpty();
    }

    @Test
    void everyAppliesThroughIsMappedToTheValueTheContractSpells() {
        List<EffectiveContext.Item> items = java.util.Arrays.stream(AppliesThrough.values())
                .map(through -> new EffectiveContext.Item(item(A, List.of(), Optional.empty()), through))
                .toList();

        EffectiveActionContext wire = mapper.toContext(
                contextRead(new EffectiveContext(items, List.of(), EffectiveSkippedReport.empty())), true);

        assertThat(wire.getArtifacts())
                .extracting(item -> item.getAppliesThrough().getValue())
                .containsExactly("action", "mission_type", "project");
    }

    @Test
    void theContextsSelfLinkIsTheRequestThatWasMadeAndCarriesTheDepthOnlyWhenTheRequestNamedOne() {
        ContextRead read = contextRead(new EffectiveContext(List.of(), List.of(), EffectiveSkippedReport.empty()));

        assertThat(mapper.toContext(read, true).getLinks().get("self").getHref())
                .isEqualTo("/api/v1/activations/a-1/mission-types/software-dev/actions/implement/context?depth=1");
        assertThat(mapper.toContext(read, false).getLinks().get("self").getHref())
                .isEqualTo("/api/v1/activations/a-1/mission-types/software-dev/actions/implement/context");
    }

    @Test
    void aMissionTypeNotInForceIsMappedAsAnEmptyContextWhoseSkippedReportNamesTheMissionType() {
        EffectiveSkippedReport skipped = new EffectiveSkippedReport(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ArtifactRef(ArtifactKind.MISSION_TYPE, "software-dev")),
                List.of(),
                List.of());

        EffectiveActionContext wire =
                mapper.toContext(contextRead(new EffectiveContext(List.of(), List.of(), skipped)), true);

        assertThat(wire.getArtifacts()).isEmpty();
        assertThat(wire.getEnhancements()).isEmpty();
        assertThat(wire.getSkipped().getMissionTypeNotInForce())
                .containsExactly(new ArtifactReference(ArtifactReference.KindEnum.MISSION_TYPE, "software-dev"));
    }
}
