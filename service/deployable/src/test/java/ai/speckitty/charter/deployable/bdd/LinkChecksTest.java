package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * The link checks the scenario hook runs on every 2xx JSON answer (R-20), over the shipped contract and the relation
 * table of the hypermedia guide. Each planted body breaks one check and must be reported.
 */
class LinkChecksTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LinkChecks CHECKS = new LinkChecks(new ContractConformance(), RelationTable.keys());
    private static final String ITEM = "/packs/qa/artifacts/tactic/rehearse";
    private static final String ITEM_LINKS = """
            "_links": {
              "self": {"href": "/api/v1/packs/qa/artifacts/tactic/rehearse"},
              "collection": {"href": "/api/v1/packs/qa/artifacts?kind=tactic"}
            }""";

    private static List<String> check(
            String method, String path, @Nullable String query, int status, @Nullable String location, String body) {
        List<String> violations =
                CHECKS.violations(new LinkChecks.Answer(method, path, query, status, location, JSON.readTree(body)));
        // The planted failures are logged, so the red run of each shows what the check reports.
        violations.forEach(violation -> System.out.println("LinkChecks: " + violation));
        return violations;
    }

    private static List<String> get(String path, @Nullable String query, String body) {
        return check("get", path, query, 200, null, body);
    }

    @Test
    void aSelfThatIsTheRootOnAPackPathIsReported() {
        assertThat(get(ITEM, null, """
                        {"_links": {"self": {"href": "/api/v1/"}}}"""))
                .anySatisfy(violation -> assertThat(violation)
                        .contains("self")
                        .contains("/api/v1/")
                        .contains("/api/v1" + ITEM));
    }

    @Test
    void aLinkToAPathNoOperationDefinesIsReported() {
        assertThat(get(ITEM, null, """
                        {"_links": {
                          "self": {"href": "/api/v1/packs/qa/artifacts/tactic/rehearse"},
                          "up": {"href": "/api/v1/packs/{packId}", "templated": true}
                        }}"""))
                .anySatisfy(violation ->
                        assertThat(violation).contains("/api/v1/packs/{packId}").contains("no GET operation"));
    }

    @Test
    void anItemWithItsSelfAndCollectionPasses() {
        assertThat(get(ITEM, null, "{" + ITEM_LINKS + "}")).isEmpty();
    }

    @Test
    void theRootWithItsTemplatedArtifactsLinkPasses() {
        assertThat(get("/", null, """
                        {"_links": {
                          "self": {"href": "/api/v1/"},
                          "info": {"href": "/api/v1/info"},
                          "offering": {"href": "/api/v1/offering"},
                          "artifacts": {"href": "/api/v1/packs/{packId}/artifacts{?kind}", "templated": true},
                          "service-desc": {"href": "/api/v1/openapi.yaml"}
                        }}""")).isEmpty();
    }

    @Test
    void theSelfOfAListPageKeepsTheQueryOfTheRequest() {
        String page = """
                {"items": [], "page": {"hasNextPage": false, "nextPageCursor": null, "pageSize": 1},
                 "_links": {"self": {"href": "/api/v1/packs/qa/artifacts?kind=tactic&pageSize=1"},
                            "first": {"href": "/api/v1/packs/qa/artifacts?kind=tactic&pageSize=1"}}}""";
        assertThat(get("/packs/qa/artifacts", "kind=tactic&pageSize=1", page)).isEmpty();
        assertThat(get("/packs/qa/artifacts", "kind=directive&pageSize=1", page))
                .anySatisfy(violation -> assertThat(violation).contains("self"));
    }

    @Test
    void aNestedLinkIsResolvedAndItsRelationChecked() {
        String page = """
                {"items": [{"kind": "tactic", "id": "x", "title": "X",
                            "_links": {"self": {"href": "/api/v1/packs/qa/nowhere/x"}, "bogus": {"href": "/api/v1/info"}}}],
                 "page": {"hasNextPage": false, "nextPageCursor": null, "pageSize": 50},
                 "_links": {"self": {"href": "/api/v1/packs/qa/artifacts"}, "first": {"href": "/api/v1/packs/qa/artifacts"}}}""";
        assertThat(get("/packs/qa/artifacts", null, page))
                .anySatisfy(violation -> assertThat(violation).contains("/api/v1/packs/qa/nowhere/x"))
                .anySatisfy(violation -> assertThat(violation).contains("bogus").contains("relation table"));
    }

    @Test
    void anAnswerWhoseUriHasNoGetCarriesNoSelf() {
        String registered = """
                {"relationship": {"from": "directive:A", "to": "procedure:p", "type": "requires"},
                 "_links": {"artifacts": {"href": "/api/v1/packs/qa/artifacts"}}}""";
        assertThat(check("put", "/packs/qa/relationships", null, 201, null, registered))
                .isEmpty();
        assertThat(check(
                        "put",
                        "/packs/qa/relationships",
                        null,
                        201,
                        null,
                        registered.replace(
                                "\"artifacts\":",
                                "\"self\": {\"href\": \"/api/v1/packs/qa/relationships\"}, \"artifacts\":")))
                .anySatisfy(
                        violation -> assertThat(violation).contains("no GET").contains("self"));
    }

    @Test
    void theLocationOfA201IsItsSelf() {
        String created = "{\"kind\": \"directive\", \"id\": \"GUARD\", \"packId\": \"qa\", \"_links\": {"
                + "\"self\": {\"href\": \"/api/v1/packs/qa/artifacts/directive/GUARD\"},"
                + " \"collection\": {\"href\": \"/api/v1/packs/qa/artifacts?kind=directive\"}}}";
        assertThat(check(
                        "post",
                        "/packs/qa/artifacts/directive",
                        null,
                        201,
                        "http://localhost/api/v1/packs/qa/artifacts/directive/GUARD",
                        created))
                .isEmpty();
        assertThat(check(
                        "post",
                        "/packs/qa/artifacts/directive",
                        null,
                        201,
                        "http://localhost/api/v1/packs/qa/artifacts/directive/OTHER",
                        created))
                .anySatisfy(violation -> assertThat(violation).contains("Location"));
    }

    @Test
    void aLinkWithAMethodIsResolvedAgainstThatMethod() {
        String body = "{"
                + ITEM_LINKS.replace(
                        "\"collection\"",
                        "\"edit\": {\"href\": \"/api/v1/packs/qa/artifacts/tactic/rehearse\", \"method\": \"PUT\"},\n  \"collection\"")
                + "}";
        assertThat(get(ITEM, null, body))
                .anySatisfy(violation -> assertThat(violation).contains("no PUT operation"));
    }

    @Test
    void aTemplatedFlagMustMatchTheHrefAndAnExpressionTheCheckCannotReadIsReported() {
        String untemplated = "{\"_links\": {\"self\": {\"href\": \"/api/v1/\"},"
                + " \"artifacts\": {\"href\": \"/api/v1/packs/{packId}/artifacts{?kind}\"}}}";
        assertThat(get("/", null, untemplated))
                .anySatisfy(violation -> assertThat(violation).contains("templated"));
        String reserved = "{\"_links\": {\"self\": {\"href\": \"/api/v1/\"},"
                + " \"artifacts\": {\"href\": \"/api/v1/packs/{+packId}/artifacts\", \"templated\": true}}}";
        assertThat(get("/", null, reserved))
                .anySatisfy(violation -> assertThat(violation).contains("{+packId}"));
    }

    @Test
    void theRelationTableIsReadFromTheCopiedGuide() {
        assertThat(RelationTable.keys())
                .contains("self", "collection", "first", "next", "service-desc", "select", "unselect")
                .doesNotContain("Relation");
        assertThat(
                        Set.copyOf(
                                RelationTable.keys(
                                        "## Relation names\n\n| Relation | On |\n|---|---|\n| `a`, `b` | x |\n\n## Next\n\n| `c` | y |\n")))
                .containsExactlyInAnyOrder("a", "b");
    }
}
