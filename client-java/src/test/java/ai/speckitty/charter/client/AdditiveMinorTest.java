package ai.speckitty.charter.client;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.client.model.OfferingPack;
import ai.speckitty.charter.client.model.PackLevel;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * A client generated today reads what a later, additive 0.x minor may answer (#217): an enum value it does not know and
 * a member it does not know. The fuller forward-compatibility suite is #221's.
 */
class AdditiveMinorTest {

    private static final String PACK = """
            {"packId": "acme", "descriptorPackId": "acme", "level": "%s", "name": "Acme", "version": "1.0.0",
             "dependsOn": [], "_links": {"artifacts": {"href": "/api/v1/packs/acme/artifacts"}}%s}""";

    @Test
    void aKnownAnswerDecodesAsDeclared() throws IOException {
        OfferingPack pack = OfferingPack.fromJson(PACK.formatted("org", ""));

        assertThat(pack.getLevel()).isEqualTo(PackLevel.ORG);
        assertThat(pack.getAdditionalProperties()).isNullOrEmpty();
    }

    @Test
    void anEnumValueThisClientDoesNotKnowDecodesToUnknown() throws IOException {
        OfferingPack pack = OfferingPack.fromJson(PACK.formatted("galaxy", ""));

        assertThat(pack.getLevel()).isEqualTo(PackLevel.UNKNOWN_DEFAULT_OPEN_API);
        assertThat(pack.getPackId()).isEqualTo("acme");
    }

    @Test
    void aMemberThisClientDoesNotKnowIsKeptAndTheKnownMembersDecode() throws IOException {
        OfferingPack pack = OfferingPack.fromJson(PACK.formatted("org", ", \"addedLater\": \"x\""));

        assertThat(pack.getName()).isEqualTo("Acme");
        assertThat(pack.getLinks()).containsKey("artifacts");
        assertThat(pack.getAdditionalProperty("addedLater")).isEqualTo("x");
    }
}
