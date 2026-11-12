package ai.speckitty.charter.profiles.infra.api;

import static ai.speckitty.charter.profiles.application.ProfileFixtures.active;
import static ai.speckitty.charter.profiles.application.ProfileFixtures.effectiveRead;
import static ai.speckitty.charter.profiles.application.ProfileFixtures.effectiveRequires;
import static ai.speckitty.charter.profiles.application.ProfileFixtures.urn;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.EffectiveAgentProfileRead;
import ai.speckitty.charter.api.model.EffectiveGovernanceEntry;
import ai.speckitty.charter.api.model.GovernanceEdgeWords;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.profiles.application.EdgeWords;
import ai.speckitty.charter.profiles.application.EffectiveProfileRead;
import ai.speckitty.charter.profiles.application.GovernanceEntry;
import ai.speckitty.charter.profiles.application.ProfileFixtures;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The wire form of a profile read under an activation: the facts of the winner, the body of the profile, the entries
 * with the facts of their own winners and the links, as the service configures its JSON ({@link ProfileJson}).
 */
class EffectiveProfileDtosTest {

    private static final JsonMapper JSON = JsonMapper.builder()
            .addModule(new ProfileJson().profileJsonModule())
            .build();
    private final EffectiveProfileDtos dtos = new EffectiveProfileDtos("/api/v1");

    private static EffectiveProfileRead.Facts facts(
            String urn,
            String pack,
            PackLevel level,
            String state,
            boolean required,
            String policy,
            String reason,
            List<String> satisfiedBy,
            Optional<EdgeWords> suggestion) {
        return new EffectiveProfileRead.Facts(
                urn(urn),
                new PackIdentifier(pack),
                level,
                state,
                required,
                policy,
                reason,
                satisfiedBy.stream().map(ProfileFixtures::urn).toList(),
                suggestion);
    }

    @Test
    void theWinnerIsTheMembersOfTheReadAndTheBodyIsTheProfileAsThePackStoresIt() {
        EffectiveAgentProfileRead wire = dtos.read(
                new EffectiveProfileRead(
                        ProfileFixtures.leanProfile("house", "P"),
                        "a-1",
                        "sha256:rev",
                        "sha256:closure",
                        "sha256:source",
                        facts(
                                "agent_profile:P",
                                "house",
                                PackLevel.PROJECT,
                                "inactive",
                                true,
                                "extendable",
                                "not_selected",
                                List.of(),
                                Optional.empty()),
                        List.of(),
                        List.of()),
                OptionalInt.empty());

        assertThat(wire.getActivationId()).isEqualTo("a-1");
        assertThat(wire.getRevision()).isEqualTo("sha256:rev");
        assertThat(wire.getGovernanceFingerprint()).isEqualTo("sha256:closure");
        assertThat(wire.getKind()).isEqualTo(EffectiveAgentProfileRead.KindEnum.AGENT_PROFILE);
        assertThat(wire.getArtifactId()).isEqualTo("P");
        assertThat(wire.getPackId()).isEqualTo("house");
        assertThat(wire.getLevel()).isEqualTo(EffectiveAgentProfileRead.LevelEnum.PROJECT);
        assertThat(wire.getState()).isEqualTo(EffectiveAgentProfileRead.StateEnum.INACTIVE);
        assertThat(wire.getRequired()).isTrue();
        assertThat(wire.getPolicy()).isEqualTo(EffectiveAgentProfileRead.PolicyEnum.EXTENDABLE);
        assertThat(wire.getReason()).isEqualTo(EffectiveAgentProfileRead.ReasonEnum.NOT_SELECTED);
        assertThat(wire.getAgentProfile().getProfileId()).isEqualTo("P");
        assertThat(wire.getAgentProfile().getName()).isEqualTo("Name of P");
        assertThat(wire.getGovernance()).isEmpty();
    }

    @Test
    void theLinksAreSelfTheWinnersPackScopedReadAndTheActivation() {
        EffectiveAgentProfileRead wire = dtos.read(effectiveRead(), OptionalInt.empty());

        assertThat(wire.getLinks().keySet()).containsExactly("self", "artifact", "up");
        assertThat(wire.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/agent_profile/P");
        assertThat(wire.getLinks().get("artifact").getHref()).isEqualTo("/api/v1/packs/main/artifacts/agent_profile/P");
        assertThat(wire.getLinks().get("up").getHref()).isEqualTo("/api/v1/activations/a-1");
    }

    @Test
    void selfRepeatsTheDepthOnlyWhenTheRequestNamedIt() {
        assertThat(dtos.read(effectiveRead(), OptionalInt.of(0))
                        .getLinks()
                        .get("self")
                        .getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/agent_profile/P?depth=0");
        assertThat(dtos.read(effectiveRead(), OptionalInt.of(1))
                        .getLinks()
                        .get("self")
                        .getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/agent_profile/P?depth=1");
    }

    @Test
    void segmentsArePercentEncodedAndATrailingSlashOfTheBasePathIsIgnored() {
        EffectiveAgentProfileRead wire = new EffectiveProfileDtos("/charter/")
                .read(
                        new EffectiveProfileRead(
                                ProfileFixtures.leanProfile("a b", "P Q"),
                                "act 1",
                                "r",
                                "g",
                                "s",
                                active("agent_profile:P Q", "a b"),
                                List.of(),
                                List.of()),
                        OptionalInt.empty());

        assertThat(wire.getLinks().get("self").getHref())
                .isEqualTo("/charter/activations/act%201/artifacts/agent_profile/P%20Q");
        assertThat(wire.getLinks().get("artifact").getHref())
                .isEqualTo("/charter/packs/a%20b/artifacts/agent_profile/P%20Q");
        assertThat(wire.getLinks().get("up").getHref()).isEqualTo("/charter/activations/act%201");
    }

    @Test
    void anEntryHasTheFactsOfItsWinnerTheStepOfTheWalkAndALinkToTheWinnersPack() {
        EffectiveAgentProfileRead wire = dtos.read(
                effectiveRead(new EffectiveProfileRead.Entry(
                        facts(
                                "directive:D",
                                "house",
                                PackLevel.PROJECT,
                                "active",
                                true,
                                "locked",
                                "required",
                                List.of(),
                                Optional.empty()),
                        GovernanceEntry.Step.REQUIRES_OF_SUGGESTED,
                        urn("directive:S"),
                        2,
                        EdgeWords.NONE)),
                OptionalInt.empty());

        EffectiveGovernanceEntry entry = wire.getGovernance().getFirst();
        assertThat(entry.getKind()).isEqualTo(EffectiveGovernanceEntry.KindEnum.DIRECTIVE);
        assertThat(entry.getArtifactId()).isEqualTo("D");
        assertThat(entry.getPackId()).isEqualTo("house");
        assertThat(entry.getLevel()).isEqualTo(EffectiveGovernanceEntry.LevelEnum.PROJECT);
        assertThat(entry.getState()).isEqualTo(EffectiveGovernanceEntry.StateEnum.ACTIVE);
        assertThat(entry.getRequired()).isTrue();
        assertThat(entry.getPolicy()).isEqualTo(EffectiveGovernanceEntry.PolicyEnum.LOCKED);
        assertThat(entry.getReason()).isEqualTo(EffectiveGovernanceEntry.ReasonEnum.REQUIRED);
        assertThat(entry.getStep()).isEqualTo(EffectiveGovernanceEntry.StepEnum.REQUIRES_OF_SUGGESTED);
        assertThat(entry.getRelation()).isEqualTo(EffectiveGovernanceEntry.RelationEnum.REQUIRES);
        assertThat(entry.getVia()).isEqualTo("directive:S");
        assertThat(entry.getHops()).isEqualTo(2);
        assertThat(entry.getEdge())
                .as("an edge that states neither is an empty object")
                .isNotNull();
        assertThat(entry.getEdge().getWhen()).isNull();
        assertThat(entry.getEdge().getReason()).isNull();
        assertThat(entry.getSatisfiedBy()).isNull();
        assertThat(entry.getLinks().keySet()).containsExactly("effective-artifact", "artifact");
        assertThat(entry.getLinks().get("effective-artifact").getHref())
                .as("the entry's winner read under the activation")
                .isEqualTo("/api/v1/activations/a-1/artifacts/directive/D");
        assertThat(entry.getLinks().get("artifact").getHref()).isEqualTo("/api/v1/packs/house/artifacts/directive/D");
    }

    @Test
    void anEntryOfAKindWithNoPackScopedReadIsListedWithoutALink() {
        EffectiveAgentProfileRead wire =
                dtos.read(effectiveRead(effectiveRequires("procedure:Q", "main")), OptionalInt.empty());

        EffectiveGovernanceEntry entry = wire.getGovernance().getFirst();
        assertThat(entry.getKind()).isEqualTo(EffectiveGovernanceEntry.KindEnum.PROCEDURE);
        assertThat(entry.getLinks()).isEmpty();
    }

    /** An org directive the profile P suggests, not selected, with the words of the edge. */
    private static EffectiveProfileRead.Entry suggestedOfP(String directive, EdgeWords words) {
        return new EffectiveProfileRead.Entry(
                facts(
                        directive,
                        "main",
                        PackLevel.ORG,
                        "inactive",
                        false,
                        "open",
                        "not_selected",
                        List.of(),
                        Optional.empty()),
                GovernanceEntry.Step.SUGGESTS,
                urn("agent_profile:P"),
                1,
                words);
    }

    @Test
    void theWordsOfTheEdgeAndASatisfiedByAreCarriedWhereTheyAreStated() {
        EffectiveAgentProfileRead wire = dtos.read(
                effectiveRead(
                        suggestedOfP("directive:D", new EdgeWords(Optional.of("w"), Optional.of("r"))),
                        new EffectiveProfileRead.Entry(
                                facts(
                                        "directive:E",
                                        "main",
                                        PackLevel.ORG,
                                        "inactive",
                                        true,
                                        "open",
                                        "overridden",
                                        List.of("directive:F", "directive:A"),
                                        Optional.empty()),
                                GovernanceEntry.Step.REQUIRES,
                                urn("agent_profile:P"),
                                1,
                                EdgeWords.NONE),
                        suggestedOfP("directive:G", new EdgeWords(Optional.empty(), Optional.of("only why")))),
                OptionalInt.empty());

        EffectiveGovernanceEntry suggested = wire.getGovernance().get(0);
        assertThat(suggested.getEdge()).isInstanceOf(GovernanceEdgeWords.class);
        assertThat(suggested.getEdge().getWhen()).isEqualTo("w");
        assertThat(suggested.getEdge().getReason()).isEqualTo("r");
        assertThat(suggested.getRelation()).isEqualTo(EffectiveGovernanceEntry.RelationEnum.SUGGESTS);
        EffectiveGovernanceEntry satisfied = wire.getGovernance().get(1);
        assertThat(satisfied.getSatisfiedBy())
                .as("every overrider, in the read's order (#439), never sorted again")
                .extracting(reference -> reference.getKind().getValue() + ":" + reference.getArtifactId())
                .containsExactly("directive:F", "directive:A");
        EffectiveGovernanceEntry partial = wire.getGovernance().get(2);
        assertThat(partial.getEdge().getWhen()).isNull();
        assertThat(partial.getEdge().getReason()).isEqualTo("only why");
    }

    @Test
    void theWinnersOwnSatisfiedByAndSuggestionAreCarried() {
        EffectiveAgentProfileRead wire = dtos.read(
                new EffectiveProfileRead(
                        ProfileFixtures.leanProfile("main", "P"),
                        "a-1",
                        "r",
                        "g",
                        "s",
                        facts(
                                "agent_profile:P",
                                "main",
                                PackLevel.ORG,
                                "inactive",
                                true,
                                "open",
                                "overridden",
                                List.of("agent_profile:Q"),
                                Optional.of(new EdgeWords(Optional.of("w"), Optional.of("r")))),
                        List.of(),
                        List.of()),
                OptionalInt.empty());

        assertThat(wire.getSatisfiedBy())
                .extracting(reference -> reference.getKind().getValue() + ":" + reference.getArtifactId())
                .containsExactly("agent_profile:Q");
        assertThat(wire.getSuggestion().getWhen()).isEqualTo("w");
        assertThat(wire.getSuggestion().getReason()).isEqualTo("r");
    }

    @Test
    void theJsonOmitsTheAbsentMembersAndWritesTheEmptyLinksOfAnEntryWithoutALink() throws Exception {
        JsonNode written = JSON.valueToTree(dtos.read(
                effectiveRead(effectiveRequires("directive:D", "main"), effectiveRequires("procedure:Q", "main")),
                OptionalInt.empty()));

        assertThat(written.has("satisfiedBy")).isFalse();
        assertThat(written.has("suggestion")).isFalse();
        JsonNode first = written.get("governance").get(0);
        assertThat(first.has("satisfiedBy")).isFalse();
        assertThat(first.has("suggestion")).isFalse();
        assertThat(first.get("edge").isObject())
                .as("always present, never null or absent")
                .isTrue();
        assertThat(first.get("edge").size()).isZero();
        assertThat(first.get("_links").get("artifact").get("href").asString())
                .isEqualTo("/api/v1/packs/main/artifacts/directive/D");
        assertThat(first.get("_links").get("effective-artifact").get("href").asString())
                .isEqualTo("/api/v1/activations/a-1/artifacts/directive/D");
        assertThat(written.get("governance").get(1).get("_links").isObject()).isTrue();
        assertThat(written.get("governance").get(1).get("_links").size()).isZero();
        assertThat(written.get("agentProfile").get("model").isNull()).isTrue();
        assertThat(ArtifactKind.AGENT_PROFILE.token())
                .isEqualTo(written.get("kind").asString());
    }

    @Test
    void theEdgeOfAnEntryInForceIsWrittenWithTheWordsItStatesAndNoOtherWhateverTheRelation() throws Exception {
        JsonNode written = JSON.valueToTree(dtos.read(
                effectiveRead(
                        new EffectiveProfileRead.Entry(
                                facts(
                                        "directive:D",
                                        "main",
                                        PackLevel.ORG,
                                        "active",
                                        false,
                                        "open",
                                        "selected",
                                        List.of(),
                                        Optional.empty()),
                                GovernanceEntry.Step.REQUIRES,
                                urn("agent_profile:P"),
                                1,
                                new EdgeWords(Optional.empty(), Optional.of("only why"))),
                        new EffectiveProfileRead.Entry(
                                facts(
                                        "directive:E",
                                        "main",
                                        PackLevel.ORG,
                                        "inactive",
                                        false,
                                        "open",
                                        "not_selected",
                                        List.of(),
                                        Optional.empty()),
                                GovernanceEntry.Step.SUGGESTS,
                                urn("agent_profile:P"),
                                1,
                                EdgeWords.NONE)),
                OptionalInt.empty()));

        JsonNode required = written.get("governance").get(0);
        assertThat(required.get("state").asString()).isEqualTo("active");
        assertThat(required.get("edge").has("when")).isFalse();
        assertThat(required.get("edge").get("reason").asString()).isEqualTo("only why");
        JsonNode suggested = written.get("governance").get(1);
        assertThat(suggested.get("state").asString()).isEqualTo("inactive");
        assertThat(suggested.get("edge").isObject())
                .as("the same {} as an entry in force whose edge states neither")
                .isTrue();
        assertThat(suggested.get("edge").size()).isZero();
        assertThat(suggested.has("suggestion")).isFalse();
    }
}
