package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The one link builder: hrefs under the configured base path, encoded segments, templates and methods. */
class HypermediaTest {

    private final Hypermedia hypermedia = new Hypermedia("/api/v1");

    @Test
    void anHrefIsTheBasePathFollowedByThePath() {
        assertThat(hypermedia.link("packs", "qa", "artifacts").href()).isEqualTo("/api/v1/packs/qa/artifacts");
        assertThat(new Hypermedia("/charter/v2/").link("info").href()).isEqualTo("/charter/v2/info");
    }

    @Test
    void theRootIsTheBasePathWithATrailingSlash() {
        assertThat(hypermedia.link().href()).isEqualTo("/api/v1/");
    }

    @Test
    void eachSegmentIsPercentEncoded() {
        assertThat(hypermedia
                        .link("packs", "my pack", "artifacts", "directive", "a/b")
                        .href())
                .isEqualTo("/api/v1/packs/my%20pack/artifacts/directive/a%2Fb");
    }

    @Test
    void aTemplatedLinkCarriesTemplatedTrueAndKeepsItsExpression() {
        Hypermedia.LinkValue link = hypermedia.templated("{?kind}", "packs", "my pack", "artifacts");

        assertThat(link.href()).isEqualTo("/api/v1/packs/my%20pack/artifacts{?kind}");
        assertThat(link.templated()).isTrue();
        assertThat(hypermedia.link("info").templated()).isNull();
    }

    @Test
    void aTemplateIsRefusedWhenItIsNotAnRfc6570Expression() {
        assertThatThrownBy(() -> hypermedia.templated("/x", "packs"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RFC 6570");
    }

    @Test
    void theMethodIsOmittedWhenItIsGet() {
        assertThat(hypermedia.link("info").method()).isNull();
        assertThat(hypermedia.link(Hypermedia.Method.GET, "info").method()).isNull();
        assertThat(hypermedia
                        .link(Hypermedia.Method.PUT, "packs", "qa", "relationships")
                        .method())
                .isEqualTo("PUT");
        assertThat(hypermedia.link(Hypermedia.Method.POST, "packs").method()).isEqualTo("POST");
    }

    @Test
    void theLinksMapIsKeyedByWireKeyInRelationOrder() {
        Map<Relation, Hypermedia.LinkValue> links = new EnumMap<>(Relation.class);
        links.put(Relation.SERVICE_DESC, hypermedia.link("openapi.yaml"));
        links.put(Relation.SELF, hypermedia.link("info"));

        assertThat(Hypermedia.links(links))
                .containsExactly(
                        Map.entry("self", hypermedia.link("info")),
                        Map.entry("service-desc", hypermedia.link("openapi.yaml")));
    }

    @Test
    void everyRelationHasItsWireKey() {
        assertThat(Relation.values())
                .extracting(Relation::wireKey)
                .containsExactly(
                        "self", "collection", "info", "offering", "artifacts", "service-desc", "first", "next");
    }
}
