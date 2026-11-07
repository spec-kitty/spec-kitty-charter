package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Which requests are registration or DELETE attempts that NFR-010 audits, and which are not. */
class RegistrationPathsTest {

    private final RegistrationPaths paths = new RegistrationPaths("/api/v1");

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "PUT, /api/v1/packs/acme, registerPackFromArchive, archive, acme",
        "POST, /api/v1/packs, registerPackFromSource, '', ''",
        "DELETE, /api/v1/packs/acme/artifacts/directive/A, deletePackArtifact, '', acme",
        "DELETE, /api/v1/packs/acme/relationships, deletePackRelationship, '', acme",
        "PUT, /api/v1/packs/Not-A-Grammar-Id, registerPackFromArchive, archive, Not-A-Grammar-Id",
    })
    void aRegistrationOrDeleteAttemptIsClassified(
            String method, String uri, String operation, String sourceKind, String packId) {
        assertThat(paths.classify(method, uri)).hasValue(new RegistrationPaths.Attempt(operation, sourceKind, packId));
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET, /api/v1/packs/acme",
        "GET, /api/v1/pack-registrations/01M6TQ62HXYF362GAFS2BZ3PGF",
        "PUT, /api/v1/packs/acme/artifacts/directive/A",
        "PUT, /api/v1/packs/acme/relationships",
        "POST, /api/v1/packs/acme",
        "DELETE, /api/v1/packs/acme",
        "PUT, /other/packs/acme",
        "PUT, /api/v1/packs",
        "PUT, /api/v1/packs/acme/",
    })
    void anyOtherRequestIsNotAnAttempt(String method, String uri) {
        assertThat(paths.classify(method, uri)).isEmpty();
    }

    @Test
    void aBasePathWithATrailingSlashIsTheSameBasePath() {
        assertThat(new RegistrationPaths("/api/v1/").classify("PUT", "/api/v1/packs/acme"))
                .isPresent();
    }
}
