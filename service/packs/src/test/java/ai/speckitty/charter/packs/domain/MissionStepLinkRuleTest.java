package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.KindRef;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link MissionStepLinkRule#isOwnStepLink}: which {@code requires} edges are a mission type's own step links, the
 * definition the relationship writes and the removal of the type call (FR-011, operator ruling
 * DM-01M7CF9PA1V2DS0P7ZZVANJ1WZ).
 */
@DisplayName("a mission type's own step link: mission_type:T requires action:T/<step>, as the pack writes it")
class MissionStepLinkRuleTest {

    private static final String PACK = "main";
    private static final Urn TYPE = Urn.missionType("t");

    @Test
    @DisplayName("the type's requires edge to one of its actions, its source bare or with the pack's own name")
    void theTypesEdgeToItsActionIsAnOwnLink() {
        assertThat(MissionStepLinkRule.isOwnStepLink(
                        PACK, TYPE, Relationship.requiresStep(KindRef.missionType("t"), KindRef.action("t", "s"))))
                .as("bare")
                .isTrue();
        assertThat(MissionStepLinkRule.isOwnStepLink(
                        PACK,
                        TYPE,
                        Relationship.requiresStep(KindRef.missionType("t").inPack(PACK), KindRef.action("t", "s"))))
                .as("written with the pack's own name")
                .isTrue();
    }

    @Test
    @DisplayName("an edge written at another pack's type is no own link, even when it names the type's id")
    void anotherPacksQualifierIsNoOwnLink() {
        assertThat(MissionStepLinkRule.isOwnStepLink(
                        PACK,
                        TYPE,
                        Relationship.requiresStep(KindRef.missionType("t").inPack("base"), KindRef.action("t", "s"))))
                .isFalse();
    }

    @Test
    @DisplayName("an edge to another type's action, or to an action qualified by a pack, is no own link")
    void anEdgeToAnotherActionIsNoOwnLink() {
        assertThat(MissionStepLinkRule.isOwnStepLink(
                        PACK, TYPE, Relationship.requiresStep(KindRef.missionType("t"), KindRef.action("u", "s"))))
                .as("another type's action")
                .isFalse();
        assertThat(MissionStepLinkRule.isOwnStepLink(
                        PACK,
                        TYPE,
                        Relationship.requiresStep(
                                KindRef.missionType("t"),
                                KindRef.action("t", "s").inPack(PACK))))
                .as("a qualified target")
                .isFalse();
        assertThat(MissionStepLinkRule.isOwnStepLink(
                        PACK,
                        Urn.missionType("u"),
                        Relationship.requiresStep(KindRef.missionType("t"), KindRef.action("t", "s"))))
                .as("the edge of another type")
                .isFalse();
    }
}
