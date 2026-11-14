package ai.speckitty.charter.artifacts.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.GlossaryContextList;
import ai.speckitty.charter.api.model.GlossaryTerm;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.artifacts.application.GlossaryReads;
import ai.speckitty.charter.artifacts.application.GlossaryView;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The wire form of a glossary context, its summary and a term: every field of the view, field by field, a list the
 * file does not state left out, the term count computed, and the links of each read.
 */
class GlossaryBodiesTest {

    private static final PackIdentifier QA = new PackIdentifier("qa");

    private static final GlossaryView.Term MISSION = new GlossaryView.Term(
            "mission",
            "mission",
            "A unit of planned work.",
            "active",
            List.of("The mission ships it."),
            List.of("feature"),
            List.of("epic"),
            List.of("term:core/lane"),
            "term:core/mission");
    private static final GlossaryView.Term LANE = new GlossaryView.Term(
            "lane", "lane", "A worktree.", "deprecated", List.of(), List.of(), List.of(), List.of(), "term:core/lane");

    private static final GlossaryView CORE = new GlossaryView(
            "core",
            QA,
            "1.0",
            "Core",
            "The core vocabulary.",
            List.of("spec-kitty", "workflow"),
            List.of("kitty-specs/**"),
            List.of(),
            List.of("spec-kitty"),
            List.of(MISSION, LANE));
    private static final GlossaryView BARE = new GlossaryView(
            "bare", QA, "1.0", "Bare", "No lists.", List.of(), List.of(), List.of(), List.of(), List.of(LANE));

    /**
     * The service's own omission of the glossary DTOs' absent members ({@link GlossaryJson}), with the envelope's
     * omission of its other kind members and a link's as the packs slice and the web transport apply them.
     */
    private static final JsonMapper JSON = JsonMapper.builder()
            .addModule(new GlossaryJson().glossaryJsonModule())
            .addMixIn(Artifact.class, OmitNulls.class)
            .addMixIn(Link.class, OmitNulls.class)
            .build();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private abstract static class OmitNulls {}

    private final GlossaryBodies bodies = new GlossaryBodies("/api/v1/");

    /** The wire form as Jackson writes it, member order aside, equals the expected JSON exactly. */
    private static void assertJson(Object wire, String expected) {
        JsonNode written = JSON.valueToTree(wire);
        assertThat(written.equals(JSON.readTree(expected))).as("%s", written).isTrue();
    }

    @Test
    void theEnvelopeIsKindGlossaryWithEveryFieldEveryTermAndItsThreeLinks() {
        assertJson(bodies.artifact(CORE), """
                {
                  "kind": "glossary",
                  "id": "core",
                  "packId": "qa",
                  "_links": {
                    "self": {"href": "/api/v1/packs/qa/artifacts/glossary/core"},
                    "collection": {"href": "/api/v1/packs/qa/artifacts/glossary"},
                    "term": {"href": "/api/v1/packs/qa/artifacts/glossary/core/terms/{termId}", "templated": true}
                  },
                  "glossary": {
                    "schemaVersion": "1.0",
                    "id": "core",
                    "name": "Core",
                    "description": "The core vocabulary.",
                    "tags": ["spec-kitty", "workflow"],
                    "appliesTo": {"paths": ["kitty-specs/**"], "stacks": ["spec-kitty"]},
                    "terms": [
                      {"id": "mission", "name": "mission", "definition": "A unit of planned work.",
                       "status": "active", "examples": ["The mission ships it."], "aliases": ["feature"],
                       "avoid": ["epic"], "seeAlso": ["term:core/lane"]},
                      {"id": "lane", "name": "lane", "definition": "A worktree.", "status": "deprecated"}
                    ]
                  }
                }
                """);
    }

    @Test
    void aContextThatStatesNoListAnswersNeitherTagsNorAppliesTo() {
        assertJson(bodies.context(BARE), """
                {
                  "schemaVersion": "1.0",
                  "id": "bare",
                  "name": "Bare",
                  "description": "No lists.",
                  "terms": [{"id": "lane", "name": "lane", "definition": "A worktree.", "status": "deprecated"}]
                }
                """);
    }

    @Test
    void anAppliesToWithOnlyLanguagesAnswersOnlyThem() {
        GlossaryView languages = new GlossaryView(
                "l", QA, "1.0", "L", "D", List.of(), List.of(), List.of("java"), List.of(), List.of(LANE));

        assertJson(bodies.context(languages).getAppliesTo(), """
                {"languages": ["java"]}
                """);
    }

    @Test
    void theListSummarisesEachContextWithItsTermCountInTheGivenOrderAndLinksItself() {
        GlossaryContextList list = bodies.list("qa", List.of(BARE, CORE));

        assertJson(list, """
                {
                  "packId": "qa",
                  "items": [
                    {"id": "bare", "name": "Bare", "description": "No lists.", "termCount": 1,
                     "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/glossary/bare"}}},
                    {"id": "core", "name": "Core", "description": "The core vocabulary.",
                     "tags": ["spec-kitty", "workflow"], "termCount": 2,
                     "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/glossary/core"}}}
                  ],
                  "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/glossary"}}
                }
                """);
    }

    @Test
    void aTermAnswersEveryFieldItsUrnItsContextAndLinksItselfAndItsContext() {
        GlossaryTerm wire = bodies.term(new GlossaryReads.TermRead(MISSION, "core", QA, "r", "f"));

        assertJson(wire, """
                {
                  "id": "mission",
                  "name": "mission",
                  "definition": "A unit of planned work.",
                  "status": "active",
                  "examples": ["The mission ships it."],
                  "aliases": ["feature"],
                  "avoid": ["epic"],
                  "seeAlso": ["term:core/lane"],
                  "urn": "term:core/mission",
                  "contextId": "core",
                  "_links": {
                    "self": {"href": "/api/v1/packs/qa/artifacts/glossary/core/terms/mission"},
                    "up": {"href": "/api/v1/packs/qa/artifacts/glossary/core"}
                  }
                }
                """);
    }

    @Test
    void aTermThatStatesNoOptionalMemberAnswersNone() {
        assertJson(bodies.term(new GlossaryReads.TermRead(LANE, "core", QA, "r", "f")), """
                {
                  "id": "lane",
                  "name": "lane",
                  "definition": "A worktree.",
                  "status": "deprecated",
                  "urn": "term:core/lane",
                  "contextId": "core",
                  "_links": {
                    "self": {"href": "/api/v1/packs/qa/artifacts/glossary/core/terms/lane"},
                    "up": {"href": "/api/v1/packs/qa/artifacts/glossary/core"}
                  }
                }
                """);
    }

    @Test
    void anIdIsEncodedAsOnePathSegmentAndTheTemplateIsLeftForTheClient() {
        GlossaryView odd = new GlossaryView(
                "a b",
                new PackIdentifier("q/a"),
                "1.0",
                "N",
                "D",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(LANE));
        Artifact envelope = new GlossaryBodies("/api/v1").artifact(odd);

        assertThat(envelope.getLinks().get("self").getHref()).isEqualTo("/api/v1/packs/q%2Fa/artifacts/glossary/a%20b");
        assertThat(envelope.getLinks().get("self").getTemplated()).isNull();
        assertThat(envelope.getLinks().get("term").getHref())
                .isEqualTo("/api/v1/packs/q%2Fa/artifacts/glossary/a%20b/terms/{termId}");
        assertThat(envelope.getLinks().get("term").getTemplated()).isTrue();
    }
}
