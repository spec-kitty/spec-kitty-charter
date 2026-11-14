package ai.speckitty.charter.deployable.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The step vocabulary of the vocabulary reads of an activation (vocabulary.feature; ADR-0076 decisions 5 to 7): the
 * structure of a Contextive v1 document, which the contract describes as a string and the link and schema checks do not
 * judge, and the cross-read of SC-004, which holds the Contextive URNs and the taboo entries of one activation to the
 * terms it answers.
 */
public class VocabularySteps {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> CONTEXT_KEYS = List.of("name", "domainVisionStatement", "paths", "terms");
    private static final List<String> CONTEXT_REQUIRED = List.of("name", "domainVisionStatement", "terms");
    private static final List<String> TERM_KEYS = List.of("name", "definition", "examples", "aliases", "meta");
    private static final List<String> TERM_REQUIRED = List.of("name", "definition", "meta");
    private static final List<String> LIST_KEYS = List.of("paths", "examples", "aliases");

    private final ScenarioHttp http;
    private List<String> rememberedUrns = List.of();
    private List<Taboo> rememberedAvoid = List.of();

    /** One avoided word and the URN of the term that avoids it. */
    private record Taboo(String word, String term) {}

    /**
     * The steps over the scenario's HTTP seam.
     *
     * @param http the scenario's HTTP seam
     */
    public VocabularySteps(ScenarioHttp http) {
        this.http = http;
    }

    /**
     * The body is a Contextive v1 definitions document: a mapping of {@code contexts} alone, each context a mapping of
     * {@code name}, {@code domainVisionStatement}, {@code paths} (when present) and {@code terms}, in that order, each
     * term one of {@code name}, {@code definition}, {@code examples} and {@code aliases} (when present) and
     * {@code meta}, in that order, and each {@code meta} exactly {@code urn} then {@code status}. A list member that is
     * present is a non-empty list of strings: an absent member is left out, never written empty.
     */
    @Then("the response body is a Contextive v1 document")
    public void contextiveDocument() throws Exception {
        Map<?, ?> document = mapping(new Load(LoadSettings.builder().build()).loadFromString(yamlBody()), "document");
        assertThat(keys(document)).as("the document's keys").containsExactly("contexts");
        for (Object context : list(document.get("contexts"), "contexts")) {
            Map<?, ?> fields = mapping(context, "a context");
            assertMembers(fields, CONTEXT_KEYS, CONTEXT_REQUIRED, "a context");
            for (Object term : list(fields.get("terms"), "terms")) {
                Map<?, ?> termFields = mapping(term, "a term");
                assertMembers(termFields, TERM_KEYS, TERM_REQUIRED, "a term");
                Map<?, ?> meta = mapping(termFields.get("meta"), "meta");
                assertThat(keys(meta))
                        .as("the meta of %s", termFields.get("name"))
                        .containsExactly("urn", "status");
                assertThat(meta.values()).allSatisfy(value -> assertThat(value).isInstanceOf(String.class));
            }
        }
    }

    /** Remembers, of the terms answer, each term's URN and each of its avoided words with its URN, in order. */
    @When("I remember the URNs and the avoid entries of the terms")
    public void rememberTerms() throws Exception {
        List<String> urns = new ArrayList<>();
        List<Taboo> avoid = new ArrayList<>();
        for (JsonNode term : JSON.readTree(http.body()).path("terms")) {
            String urn = term.path("urn").asString();
            urns.add(urn);
            term.path("avoid").forEach(word -> avoid.add(new Taboo(word.asString(), urn)));
        }
        rememberedUrns = List.copyOf(urns);
        rememberedAvoid = List.copyOf(avoid);
    }

    /** SC-004: the set of the Contextive document's {@code meta.urn} values is the set of the terms' URNs. */
    @Then("the meta.urn values of the Contextive document are the remembered URNs")
    public void contextiveUrnsAreTheTerms() throws Exception {
        Map<?, ?> document = mapping(new Load(LoadSettings.builder().build()).loadFromString(yamlBody()), "document");
        List<String> urns = new ArrayList<>();
        for (Object context : list(document.get("contexts"), "contexts")) {
            for (Object term : list(mapping(context, "a context").get("terms"), "terms")) {
                urns.add((String)
                        mapping(mapping(term, "a term").get("meta"), "meta").get("urn"));
            }
        }
        assertThat(urns).as("the Contextive URNs").containsExactlyInAnyOrderElementsOf(rememberedUrns);
    }

    /**
     * SC-004: the taboo entries are the avoided words of the terms, one entry per word per term, with its URN, ordered
     * by word and then URN, each in Unicode code point order.
     */
    @Then("the taboo entries are the remembered avoid entries")
    public void taboosAreTheAvoidEntries() throws Exception {
        List<Taboo> entries = new ArrayList<>();
        for (JsonNode entry : JSON.readTree(http.body()).path("entries")) {
            entries.add(
                    new Taboo(entry.path("word").asString(), entry.path("term").asString()));
        }
        assertThat(entries).as("the taboo entries").containsExactlyInAnyOrderElementsOf(rememberedAvoid);
        assertThat(entries)
                .as("the taboo entries, by word and then URN")
                .isSortedAccordingTo(Comparator.comparing(Taboo::word, VocabularySteps::byCodePoint)
                        .thenComparing(Taboo::term, VocabularySteps::byCodePoint));
    }

    /** Unicode code point order, as the contract states it, independent of the service's own comparison. */
    private static int byCodePoint(String left, String right) {
        return Arrays.compare(left.codePoints().toArray(), right.codePoints().toArray());
    }

    private static void assertMembers(Map<?, ?> fields, List<String> allowed, List<String> required, String what) {
        List<String> keys = keys(fields);
        assertThat(keys).as("the keys of %s", what).containsAll(required);
        assertThat(allowed).as("the keys of %s, in order", what).containsSubsequence(keys);
        for (String key : keys) {
            Object value = fields.get(key);
            if (LIST_KEYS.contains(key)) {
                assertThat(list(value, key))
                        .as("%s of %s", key, what)
                        .isNotEmpty()
                        .allSatisfy(item -> assertThat(item).isInstanceOf(String.class));
            } else if (!key.equals("terms") && !key.equals("meta")) {
                assertThat(value).as("%s of %s", key, what).isInstanceOf(String.class);
            }
        }
    }

    /**
     * The YAML body, decoded as UTF-8: {@code application/yaml} defines no {@code charset} parameter (RFC 9512), so the
     * mock response would decode it as ISO-8859-1.
     */
    private String yamlBody() {
        return new String(http.response().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static List<String> keys(Map<?, ?> fields) {
        return fields.keySet().stream().map(String::valueOf).toList();
    }

    private static Map<?, ?> mapping(Object value, String what) {
        assertThat(value).as("%s is a mapping", what).isInstanceOf(Map.class);
        return (Map<?, ?>) value;
    }

    private static List<?> list(Object value, String what) {
        assertThat(value).as("%s is a list", what).isInstanceOf(List.class);
        return (List<?>) value;
    }
}
