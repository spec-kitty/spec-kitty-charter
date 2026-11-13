package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ai.speckitty.charter.activations.application.FakeAvailablePacks;
import ai.speckitty.charter.activations.domain.ActionContext;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.EffectiveSet.Bucket;
import ai.speckitty.charter.activations.domain.EffectiveSet.Policy;
import ai.speckitty.charter.activations.domain.EffectiveSet.Reason;
import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.DenyNotApplied;
import ai.speckitty.charter.activations.domain.EffectiveSkippedReport.DenyReason;
import ai.speckitty.charter.activations.domain.EffectiveTag;
import ai.speckitty.charter.activations.domain.GraphSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.ArtifactState;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.NodeRef;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.transport.EntityTags;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The effective reads and {@code getActivation} as the controller serves them over HTTP: the strong {@code ETag} of each
 * representation, the {@code 304}, the {@code Charter-Source-Fingerprint} of the two verified reads, the {@code 400}s
 * without a code, the refusals of the closure as {@code 500} with their code, the cursor that pages a set, the write's
 * {@code If-Match} that takes the {@code getActivation} tag, and the warnings of a write.
 */
class EffectiveReadHttpTest {

    private static final String BASE = "/api/v1/activations/a-1";
    private static final String SOURCE = "sha256:source";
    private static final String CLOSURE = "sha256:closure";
    private static final ArtifactRef A = new ArtifactRef(ArtifactKind.DIRECTIVE, "a-1");
    private static final ArtifactRef B = new ArtifactRef(ArtifactKind.DIRECTIVE, "b-2");
    private static final ArtifactRef C = new ArtifactRef(ArtifactKind.DIRECTIVE, "c-3");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CONTEXT = "/mission-types/software-dev/actions/implement/context";

    private final InMemoryActivationRepository repository = new InMemoryActivationRepository();
    private FakeAvailablePacks packs;
    private MockMvc mvc;
    private String revisionTag;

    @BeforeEach
    void setUp() throws Exception {
        packs = new FakeAvailablePacks(closure(CLOSURE));
        packs.merged(merged());
        packs.resolves(query -> answer());
        mvc = StandaloneActivationMvc.over(repository, packs);
        MockHttpServletResponse created = send(put(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schemaVersion\": 2, \"packs\": [{\"packId\": \"fixture\"}],"
                        + " \"selected\": {\"directive\": [\"a-1\"]}}"));
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        revisionTag = created.getHeader("ETag");
    }

    private static ClosureSnapshot closure(String closureFingerprint) {
        return ClosureSnapshots.snapshot()
                .pack("fixture", "1.0.0")
                .held(ArtifactKind.DIRECTIVE, "a-1", "b-2", "c-3")
                .fingerprint(SOURCE)
                .closureFingerprint(closureFingerprint)
                .build();
    }

    private static MergedSnapshot merged() {
        Map<ArtifactRef, MergedSnapshot.Winner> winners = new LinkedHashMap<>();
        for (ArtifactRef ref : List.of(A, B, C)) {
            winners.put(ref, new MergedSnapshot.Winner("fixture", PackLevel.ORG, Policy.OPEN));
        }
        MergedSnapshot.Edge suggests =
                new MergedSnapshot.Edge(A, B, RelationType.SUGGESTS, Optional.of("when"), Optional.of("why"));
        return new MergedSnapshot(winners, Map.of(), List.of(suggests), List.of());
    }

    private static InForceAnswer answer() {
        Map<ArtifactRef, ArtifactState> states = new LinkedHashMap<>();
        states.put(
                A, new ArtifactState(A, State.ACTIVE, Reason.SELECTED, true, List.of(), List.of(), Optional.empty()));
        states.put(
                B,
                new ArtifactState(
                        B, State.INACTIVE, Reason.NOT_SELECTED, false, List.of(), List.of(), Optional.empty()));
        states.put(
                C,
                new ArtifactState(
                        C,
                        State.EXCLUDED,
                        Reason.DEACTIVATED,
                        false,
                        List.of(),
                        List.of(),
                        Optional.of(Bucket.DEACTIVATED)));
        return new InForceAnswer(
                states,
                List.of(),
                List.of(new DenyNotApplied(A, DenyReason.REQUIRED)),
                List.of(),
                List.of(),
                List.of(),
                Map.of());
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    private MockHttpServletResponse read(String path, String... ifNoneMatch) throws Exception {
        MockHttpServletRequestBuilder request = get(path).accept(MediaType.APPLICATION_JSON);
        if (ifNoneMatch.length > 0) {
            request.header("If-None-Match", ifNoneMatch[0]);
        }
        return send(request);
    }

    private static JsonNode body(MockHttpServletResponse response) throws Exception {
        return JSON.readTree(response.getContentAsString());
    }

    private static String revisionOf(String quotedRevision) {
        return quotedRevision.substring(1, quotedRevision.length() - 1);
    }

    private String closureTag() {
        return EntityTags.quote(EffectiveTag.activation(new Revision(revisionOf(revisionTag)), closure(CLOSURE))
                .token());
    }

    // ---- getActivation

    @Test
    void getActivationAnswersTheClosureTagNotTheRevisionAndTheDenyEntriesAndNoSourceFingerprint() throws Exception {
        MockHttpServletResponse answer = read(BASE);

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        assertThat(answer.getHeader("ETag")).isEqualTo(closureTag()).isNotEqualTo(revisionTag);
        assertThat(answer.getHeader("Charter-Source-Fingerprint")).isNull();
        JsonNode body = body(answer);
        assertThat(body.path("revision").asString()).isEqualTo(revisionOf(revisionTag));
        assertThat(body.path("denyNotApplied")).extracting(JsonNode::asString).containsExactly("directive:a-1");
    }

    @Test
    void getActivationOmitsDenyNotAppliedWhenThereIsNone() throws Exception {
        packs.resolves(query -> InForceAnswer.empty());

        assertThat(body(read(BASE)).has("denyNotApplied")).isFalse();
    }

    @Test
    void getActivationIs304OnTheClosureTagAndNot304OnTheRevision() throws Exception {
        MockHttpServletResponse matching = read(BASE, closureTag());
        MockHttpServletResponse revision = read(BASE, revisionTag);

        assertThat(matching.getStatus()).isEqualTo(304);
        assertThat(matching.getHeader("ETag")).isEqualTo(closureTag());
        assertThat(matching.getContentAsString()).isEmpty();
        assertThat(revision.getStatus()).isEqualTo(200);
    }

    // ---- the writes' If-Match

    @Test
    void aWriteTakesTheGetActivationTagAsIfMatchAndAnswersTheRevisionWithoutTheDenyNotice() throws Exception {
        MockHttpServletResponse answer = send(put(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", closureTag())
                .content("{\"schemaVersion\": 2, \"packs\": [{\"packId\": \"fixture\"}],"
                        + " \"selected\": {\"directive\": [\"a-1\", \"b-2\"]}}"));

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        assertThat(answer.getHeader("ETag"))
                .isEqualTo("\"" + body(answer).path("revision").asString() + "\"");
        assertThat(body(answer).has("denyNotApplied")).isFalse();
    }

    @Test
    void aStaleClosureTagIsA412() throws Exception {
        String stale = closureTag();
        packs.answer(closure("sha256:changed"));

        MockHttpServletResponse answer = send(put(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", stale)
                .content("{\"schemaVersion\": 2}"));

        assertThat(answer.getStatus()).isEqualTo(412);
    }

    @Test
    void aSubResourceWriteTakesTheTagAndItsAnswerCarriesTheWarnings() throws Exception {
        packs.resolves(query -> new InForceAnswer(
                Map.of(
                        B,
                        new ArtifactState(
                                B,
                                State.INACTIVE,
                                Reason.OVERRIDDEN,
                                false,
                                List.of(),
                                List.of(A),
                                Optional.of(Bucket.OVERRIDDEN))),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()));

        MockHttpServletResponse answer = send(put(BASE + "/selections/directive/b-2")
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", closureTag())
                .content("{}"));

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        JsonNode warnings = body(answer).path("warnings");
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).path("code").asString()).isEqualTo("overridden");
        assertThat(warnings.get(0).path("artifact").path("artifactId").asString())
                .isEqualTo("b-2");
    }

    // ---- listEffectiveArtifacts

    @Test
    void theListAnswersItsTagTheSourceFingerprintTheSummaryAndTheItems() throws Exception {
        MockHttpServletResponse answer = read(BASE + "/artifacts");

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        assertThat(answer.getHeader("ETag")).matches("\"sha256:[0-9a-f]{64}\"");
        assertThat(answer.getHeader("Charter-Source-Fingerprint")).isEqualTo(SOURCE);
        JsonNode body = body(answer);
        assertThat(body.path("governanceFingerprint").asString()).isEqualTo(CLOSURE);
        assertThat(body.path("summary").path("active").asInt()).isEqualTo(1);
        assertThat(body.path("items"))
                .extracting(item -> item.path("artifactId").asString())
                .containsExactly("a-1", "b-2", "c-3");
        assertThat(body.path("pageInfo").path("hasNextPage").asBoolean()).isFalse();
    }

    @Test
    void theListIs304OnItsOwnTagWithoutBodyOrFingerprintAndItsTagDiffersPerRepresentation() throws Exception {
        MockHttpServletResponse first = read(BASE + "/artifacts");
        String etag = first.getHeader("ETag");

        MockHttpServletResponse matching = read(BASE + "/artifacts", etag);
        MockHttpServletResponse active = read(BASE + "/artifacts?state=active", etag);

        assertThat(matching.getStatus()).isEqualTo(304);
        assertThat(matching.getHeader("ETag")).isEqualTo(etag);
        assertThat(matching.getContentAsString()).isEmpty();
        assertThat(matching.getHeader("Charter-Source-Fingerprint")).isNull();
        assertThat(active.getStatus()).isEqualTo(200);
        assertThat(active.getHeader("ETag")).isNotEqualTo(etag);
    }

    @Test
    void theNextLinkPagesTheSetAndTheCursorOfAnotherSetIsA400() throws Exception {
        MockHttpServletResponse first = read(BASE + "/artifacts?pageSize=2");
        JsonNode firstBody = body(first);
        String next = firstBody.path("_links").path("next").path("href").asString();
        MockHttpServletResponse second = read(next);

        assertThat(firstBody.path("items")).hasSize(2);
        assertThat(firstBody.path("pageInfo").path("hasNextPage").asBoolean()).isTrue();
        assertThat(second.getStatus()).as(second.getContentAsString()).isEqualTo(200);
        assertThat(body(second).path("items"))
                .extracting(item -> item.path("artifactId").asString())
                .containsExactly("c-3");
        assertThat(body(second).path("summary")).isEqualTo(firstBody.path("summary"));

        packs.answer(closure("sha256:changed"));
        MockHttpServletResponse foreign = read(next);
        assertThat(foreign.getStatus()).isEqualTo(400);
        assertThat(body(foreign).has("code")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"kind=bogus", "state=bogus", "pageSize=0", "pageSize=201", "pageCursor=garbage"})
    void aBadQueryIsA400WithoutACode(String query) throws Exception {
        MockHttpServletResponse answer = read(BASE + "/artifacts?" + query);

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(400);
        assertThat(body(answer).has("code")).isFalse();
    }

    @Test
    void anUnknownActivationIs404OnBothReads() throws Exception {
        for (String path : List.of("/artifacts", "/skipped", "", "/graph", CONTEXT)) {
            MockHttpServletResponse answer = read("/api/v1/activations/nope" + path);

            assertThat(answer.getStatus()).as(path).isEqualTo(404);
            assertThat(body(answer).path("code").asString()).isEqualTo("activation_not_found");
        }
    }

    @Test
    void aClosureThatCannotComposeIs500WithItsCodeOnEveryRead() throws Exception {
        packs.refuse(new CharterRefusal(RefusalCode.PACK_SIBLING_CONFLICT, "two packs contest a-1"));

        for (String path : List.of("/artifacts", "/skipped", "", "/graph", CONTEXT)) {
            MockHttpServletResponse answer = read(BASE + path);

            assertThat(answer.getStatus()).as(path).isEqualTo(500);
            assertThat(body(answer).path("code").asString()).isEqualTo("pack_sibling_conflict");
        }
    }

    // ---- getActivationSkippedReport

    @Test
    void theSkippedReportAnswersItsTagTheFingerprintsAndTheBuckets() throws Exception {
        MockHttpServletResponse answer = read(BASE + "/skipped");

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        assertThat(answer.getHeader("ETag")).matches("\"sha256:[0-9a-f]{64}\"");
        assertThat(answer.getHeader("Charter-Source-Fingerprint")).isEqualTo(SOURCE);
        JsonNode body = body(answer);
        assertThat(body.path("governanceFingerprint").asString()).isEqualTo(CLOSURE);
        assertThat(body.path("report")
                        .path("denyNotApplied")
                        .get(0)
                        .path("reason")
                        .asString())
                .isEqualTo("required");
        assertThat(body.path("report").path("skippedDeactivated")).hasSize(1);
        assertThat(body.path("report").path("skippedByScope")).isEmpty();
    }

    @Test
    void theSkippedReportIs304OnItsTagAndNotOnTheListsTag() throws Exception {
        String skipped = read(BASE + "/skipped").getHeader("ETag");
        String list = read(BASE + "/artifacts").getHeader("ETag");

        assertThat(read(BASE + "/skipped", skipped).getStatus()).isEqualTo(304);
        assertThat(read(BASE + "/skipped", list).getStatus()).isEqualTo(200);
    }

    // ---- getEffectiveGraph and getEffectiveActionContext

    private static GraphSnapshot graph() {
        return new GraphSnapshot(
                List.of(NodeRef.of(A), NodeRef.of(B)),
                List.of(new GraphSnapshot.Edge(NodeRef.of(A), RelationType.REQUIRES, NodeRef.of(B))),
                List.of());
    }

    @Test
    void theGraphAnswersItsTagTheSourceFingerprintAndTheNodesEdgesAndLayerOverrides() throws Exception {
        packs.graphs(query -> graph());

        MockHttpServletResponse answer = read(BASE + "/graph");

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        assertThat(answer.getHeader("ETag")).matches("\"sha256:[0-9a-f]{64}\"");
        assertThat(answer.getHeader("Charter-Source-Fingerprint")).isEqualTo(SOURCE);
        JsonNode body = body(answer);
        assertThat(body.path("governanceFingerprint").asString()).isEqualTo(CLOSURE);
        assertThat(body.path("nodes"))
                .extracting(node -> node.path("urn").asString())
                .containsExactly("directive:a-1", "directive:b-2");
        assertThat(body.path("edges")).hasSize(1);
        assertThat(body.path("layerOverrides").isArray()).isTrue();
        assertThat(body.path("_links").path("self").path("href").asString()).isEqualTo(BASE + "/graph");
    }

    @Test
    void theGraphIs304OnItsOwnTagWithoutBodyOrFingerprintAndNotOnTheContextsTag() throws Exception {
        packs.graphs(query -> graph());
        packs.contexts(ask -> ActionContext.notInForce());
        String graph = read(BASE + "/graph").getHeader("ETag");
        String context = read(BASE + CONTEXT).getHeader("ETag");

        MockHttpServletResponse matching = read(BASE + "/graph", graph);

        assertThat(matching.getStatus()).isEqualTo(304);
        assertThat(matching.getHeader("ETag")).isEqualTo(graph);
        assertThat(matching.getContentAsString()).isEmpty();
        assertThat(matching.getHeader("Charter-Source-Fingerprint")).isNull();
        assertThat(context).isNotEqualTo(graph);
        assertThat(read(BASE + "/graph", context).getStatus()).isEqualTo(200);
    }

    @Test
    void theContextAnswersItsEntriesItsSuggestionItsFingerprintAndASelfThatKeepsTheDepth() throws Exception {
        packs.contexts(ask -> new ActionContext(
                true,
                List.of(new ActionContext.Reached(A, List.of(), ActionContext.AppliesThrough.PROJECT)),
                List.of(new ActionContext.SuggestionCandidate(
                        B, Optional.of("when"), Optional.of("why"), ActionContext.AppliesThrough.PROJECT)),
                List.of()));

        MockHttpServletResponse answer = read(BASE + CONTEXT + "?depth=1");

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        assertThat(answer.getHeader("ETag")).matches("\"sha256:[0-9a-f]{64}\"");
        assertThat(answer.getHeader("Charter-Source-Fingerprint")).isEqualTo(SOURCE);
        JsonNode body = body(answer);
        assertThat(body.path("missionType").asString()).isEqualTo("software-dev");
        assertThat(body.path("action").asString()).isEqualTo("implement");
        assertThat(body.path("artifacts"))
                .extracting(item -> item.path("artifactId").asString())
                .containsExactly("a-1", "b-2");
        assertThat(body.path("artifacts").get(1).path("suggestion").path("when").asString())
                .isEqualTo("when");
        assertThat(body.path("artifacts"))
                .extracting(item -> item.path("appliesThrough").asString())
                .as("the entry and its suggestion carry the pass that listed them")
                .containsExactly("project", "project");
        assertThat(body.path("enhancements")).isEmpty();
        assertThat(body.path("_links").path("self").path("href").asString()).isEqualTo(BASE + CONTEXT + "?depth=1");
        assertThat(body(read(BASE + CONTEXT))
                        .path("_links")
                        .path("self")
                        .path("href")
                        .asString())
                .as("the default depth is not repeated in self")
                .isEqualTo(BASE + CONTEXT);
        assertThat(packs.contextAsks()).extracting(ask -> ask.depth()).containsExactly(1, 0);
    }

    @Test
    void theContextIs304OnItsOwnTagAndAnotherDepthIsAnotherRepresentation() throws Exception {
        packs.contexts(ask -> ActionContext.notInForce());
        String tag = read(BASE + CONTEXT).getHeader("ETag");

        assertThat(read(BASE + CONTEXT, tag).getStatus()).isEqualTo(304);
        assertThat(read(BASE + CONTEXT + "?depth=1", tag).getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"depth=2", "depth=-1", "depth=deep"})
    void aDepthOutsideTheContractIsA400WithoutACode(String query) throws Exception {
        MockHttpServletResponse answer = read(BASE + CONTEXT + "?" + query);

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(400);
        assertThat(body(answer).has("code")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"mission_type_not_found", "action_not_found"})
    void anUnknownMissionTypeOrActionIsA404WithItsCode(String code) throws Exception {
        RefusalCode refusal = RefusalCode.valueOf(code.toUpperCase(java.util.Locale.ROOT));
        packs.contexts(ask -> {
            throw new CharterRefusal(refusal, "no such name");
        });

        MockHttpServletResponse answer = read(BASE + CONTEXT);

        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(404);
        assertThat(body(answer).path("code").asString()).isEqualTo(code);
    }
}
