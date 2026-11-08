package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The fixed reasons of {@code pack_source_refused} (NFR-012) are one set in three places: {@link SourceRefusal}, the
 * description of the code in {@code CharterRefusalCode.yaml} and the description of {@code registerPackFromSource} in
 * {@code paths/packs.yaml}. A reason a client can be answered with and the contract does not list, or the reverse, is a
 * drift this test refuses.
 */
class SourceRefusalContractTest {

    private static final List<String> REASONS = List.of(
            "host_not_allowed",
            "address_not_global",
            "url_refused",
            "redirect_refused",
            "wrong_media_type",
            "too_large",
            "digest_mismatch",
            "name_mismatch",
            "source_malformed",
            "path_refused",
            "bad_ref",
            "commit_mismatch");

    private static String read(String relative) throws IOException {
        Path root = Path.of(System.getProperty("charter.repositoryRoot"));
        return Files.readString(root.resolve(relative), StandardCharsets.UTF_8).replaceAll("\\s+", " ");
    }

    @Test
    void theEnumHoldsExactlyTheFixedReasons() {
        assertThat(Stream.of(SourceRefusal.values()).map(SourceRefusal::wire)).containsExactlyInAnyOrderElementsOf(REASONS);
    }

    @Test
    void theRefusalCodeDescriptionListsEveryReason() throws IOException {
        String description = read("contracts/charter/schemas/CharterRefusalCode.yaml");

        assertThat(REASONS).allSatisfy(reason -> assertThat(description).contains(reason));
    }

    @Test
    void theOperationDescriptionListsEveryReason() throws IOException {
        String description = read("contracts/charter/paths/packs.yaml");

        assertThat(REASONS).allSatisfy(reason -> assertThat(description).contains(reason));
    }
}
