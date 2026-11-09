package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.application.EffectiveReadService.After;
import ai.speckitty.charter.activations.application.EffectiveReadService.Page;
import ai.speckitty.charter.activations.application.EffectiveReadService.PageQuery;
import ai.speckitty.charter.activations.application.EffectiveReadService.Report;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
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
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.activations.domain.SkippedReport.SkippedDeactivated;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.api.model.Activation;
import ai.speckitty.charter.api.model.ActivationSkippedReport;
import ai.speckitty.charter.api.model.ArtifactReference;
import ai.speckitty.charter.api.model.EffectiveArtifactList;
import ai.speckitty.charter.api.model.NodeReference;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

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
    private final EffectiveReadMapper mapper = new EffectiveReadMapper(activations);
    private final StoredActivation stored = StoredActivation.of(
            new ActivationId("a-1"), ActivationDocument.builder().build().document());

    private static EffectiveArtifact item(
            ArtifactRef ref, Optional<ArtifactRef> satisfiedBy, Optional<Suggestion> suggestion) {
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
                                Optional.of(B),
                                Optional.of(new Suggestion(Optional.of("on release"), Optional.of("it helps")))),
                        item(T, Optional.empty(), Optional.empty())));

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
        assertThat(first.getSatisfiedBy()).isEqualTo(new ArtifactReference(ArtifactReference.KindEnum.DIRECTIVE, "B"));
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
                                item(
                                        A,
                                        Optional.empty(),
                                        Optional.of(new Suggestion(Optional.empty(), Optional.of("why")))),
                                item(
                                        B,
                                        Optional.empty(),
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
                        List.of(item(A, Optional.empty(), Optional.empty()))),
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
                        List.of(
                                item(A, Optional.empty(), Optional.empty()),
                                item(B, Optional.empty(), Optional.empty()))),
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
                        List.of(item(A, Optional.empty(), Optional.empty()))),
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
                            ErrorResponseException.class,
                            refused -> assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    // ---- the skipped report

    @Test
    void theSkippedReportMapsEveryBucketAndTheWriteBucketsStayEmpty() {
        EffectiveSkippedReport skipped = new EffectiveSkippedReport(
                List.of(A),
                List.of(new DenyNotApplied(B, DenyReason.LOCKED)),
                List.of(new EnhancerWithoutTarget(T, List.of(NodeRef.of(B), NodeRef.of(A)))),
                List.of(new OverrideNotApplied(A, B)),
                List.of(new ArtifactRef(ArtifactKind.DIRECTIVE, "GONE")),
                List.of(new RequiredNotInForce(T, PruningReason.ENHANCED_TARGET_NOT_IN_FORCE)),
                List.of(new PinNotHeld("main", "9.9.9")),
                List.of(B),
                List.of(new ArtifactRef(ArtifactKind.TACTIC, "step")),
                List.of(new SkippedDeactivated(A, false, List.of())),
                List.of(new Overridden(T, A)));

        ActivationSkippedReport wire = mapper.toSkipped(new Report(stored, "sha256:source", "sha256:closure", skipped));

        assertThat(wire.getActivationId()).isEqualTo("a-1");
        assertThat(wire.getRevision()).isEqualTo(stored.revision().token());
        assertThat(wire.getGovernanceFingerprint()).isEqualTo("sha256:closure");
        ai.speckitty.charter.api.model.SkippedReport report = wire.getReport();
        assertThat(report.getStaleIds())
                .containsExactly(new ArtifactReference(ArtifactReference.KindEnum.DIRECTIVE, "A"));
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
                    .isEqualTo(new ArtifactReference(ArtifactReference.KindEnum.DIRECTIVE, "B"));
        });
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
                    .isEqualTo(new ArtifactReference(ArtifactReference.KindEnum.DIRECTIVE, "A"));
        });
        assertThat(report.getSkippedByScope()).isEmpty();
        assertThat(report.getUnreachedBehindDeactivated()).isEmpty();
        assertThat(report.getSkippedShared()).isEmpty();
        assertThat(wire.getLinks()).containsOnlyKeys("self");
        assertThat(wire.getLinks().get("self").getHref()).isEqualTo("/api/v1/activations/a-1/skipped");
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
}
