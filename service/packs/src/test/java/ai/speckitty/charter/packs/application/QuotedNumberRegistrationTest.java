package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A quoted number in a number field (#651): the registration refuses it with the one wording's detail, which says how to
 * fix it, under the code and at the pointer it always had. {@code pack-doctor validate} gives the same violation
 * ({@code QuotedNumberTest} of {@code :pack-doctor}).
 */
class QuotedNumberRegistrationTest {

    /** The example org pack's catalog, which prices one model. */
    static final String CATALOG = "routing_catalogs/cost-led.routing-catalog.yaml";

    @Test
    @DisplayName("a quoted number is refused with the fix in the detail, the code and the pointer unchanged")
    void aQuotedNumberIsRefusedWithTheFix(@TempDir Path host) throws IOException {
        Rig rig = new Rig(host);
        ExamplePacks.copyOf(ExamplePacks.BUILT_IN, rig.packs);
        Path pack = ExamplePacks.copyOf(ExamplePacks.ORG, host.resolve("upload"));
        Path catalog = pack.resolve(CATALOG);
        String text = Files.readString(catalog, StandardCharsets.UTF_8);
        assertThat(text).contains("    input_per_1m_usd: 15\n");
        Files.writeString(
                catalog, text.replace("input_per_1m_usd: 15\n", "input_per_1m_usd: \"15\"\n"), StandardCharsets.UTF_8);

        CharterRefusal refused =
                refusal(() -> rig.register(ExamplePacks.ORG, new StagedPack(pack), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
        assertThat(refused.violations())
                .containsExactly(new RefusalViolation(
                        CATALOG + "#/models/0/cost/input_per_1m_usd",
                        "artifact_schema_error",
                        CATALOG + ": 'input_per_1m_usd': \"15\" must be a number: write 15 without quotes, or run"
                                + " pack-doctor remediate"));
        assertThat(rig.packs.resolve(ExamplePacks.ORG)).as("nothing was stored").doesNotExist();
    }
}
