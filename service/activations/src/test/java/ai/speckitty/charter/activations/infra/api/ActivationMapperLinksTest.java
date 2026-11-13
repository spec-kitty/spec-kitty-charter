package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.activations.application.ActivationService;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.api.model.Activation;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * An activation is discoverable by links (#409, ruling {@code DM-01M78RKM6Q23PA9QAC80QJKQFF}): every activation answer
 * links its reads, its held assets and skills, the offering and its writes, each write with its method and each
 * template marked templated.
 */
class ActivationMapperLinksTest {

    private static final String SELF = "/api/v1/activations/a-1";

    private final ActivationMapper mapper = new ActivationMapper("/api/v1");
    private final StoredActivation stored = StoredActivation.of(
            new ActivationId("a-1"), ActivationDocument.builder().build().document());

    @Test
    void anActivationLinksItsReadsTheOfferingAndItsWritesInTheContractOrder() {
        Map<String, Link> links = mapper.toActivation(stored).getLinks();

        assertThat(links.entrySet())
                .extracting(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getHref(),
                        entry -> entry.getValue().getMethod(),
                        entry -> entry.getValue().getTemplated())
                .containsExactly(
                        tuple("self", SELF, null, null),
                        tuple("artifacts", SELF + "/artifacts{?kind,state}", null, true),
                        tuple("offering", "/api/v1/offering", null, null),
                        tuple("edit", SELF, "PUT", null),
                        tuple("delete", SELF, "DELETE", null),
                        tuple("select", SELF + "/selections/{kind}/{artifactId}", "PUT", true),
                        tuple("exclude", SELF + "/exclusions/{kind}/{artifactId}", "PUT", true),
                        tuple("apply-preset", SELF + "/presets/{presetId}", "PUT", true),
                        tuple("packs", SELF + "/packs", null, null),
                        tuple("graph", SELF + "/graph", null, null),
                        tuple("skipped", SELF + "/skipped", null, null),
                        tuple(
                                "effective-context",
                                SELF + "/mission-types/{missionType}/actions/{action}/context{?depth}",
                                null,
                                true),
                        tuple("assets", SELF + "/artifacts/asset", null, null),
                        tuple("asset", SELF + "/artifacts/asset/{artifactId}", null, true),
                        tuple("skills", SELF + "/artifacts/skill", null, null));
    }

    @Test
    void theActivationCarriesNoLinkForAnArtifactAlreadySelectedOrExcluded() {
        assertThat(mapper.toActivation(stored).getLinks()).doesNotContainKeys("unselect", "include");
    }

    @Test
    void theReadAndTheReplaceAnswerTheSameLinksAsTheCreate() {
        Map<String, Link> created = mapper.toActivation(stored).getLinks();
        ArtifactRef denied = new ArtifactRef(ArtifactKind.DIRECTIVE, "D");

        assertThat(mapper.toActivation(stored, List.of(denied)).getLinks()).isEqualTo(created);
        assertThat(mapper.toReplaced(new ActivationService.Written(stored, false, List.of(), List.of()))
                        .getLinks())
                .isEqualTo(created);
    }

    @Test
    void everyLinkStartsAtTheBasePathWhateverItsTrailingSlash() {
        Activation slashed = new ActivationMapper("/api/v1/").toActivation(stored);

        assertThat(slashed.getLinks()).isEqualTo(mapper.toActivation(stored).getLinks());
        assertThat(slashed.getLinks().values())
                .allSatisfy(link ->
                        assertThat(link.getHref()).startsWith("/api/v1/").doesNotContain("//"));
    }
}
