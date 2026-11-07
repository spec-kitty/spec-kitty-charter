package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.RegistrationApi;
import ai.speckitty.charter.api.model.PackRegistration;
import ai.speckitty.charter.api.model.PackRegistrationStatus;
import ai.speckitty.charter.api.model.PackSource;
import ai.speckitty.charter.kernel.transport.ReservedOperation;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The inbound REST adapter of whole-pack registration (ADR-0065): {@code PUT /packs/{packId}}, {@code POST /packs},
 * {@code GET /packs/{packId}}, {@code GET /pack-registrations/{registrationId}} and the two {@code DELETE}s of an
 * artifact and of a relationship. All six are reserved (ADR-0020 D8): the type carries {@link ReservedOperation}, so the
 * composition root's {@code ReservedOperationInterceptor} answers each {@code 501} before the request is bound, the
 * archive body of the {@code PUT} and the {@code PackSource} of the {@code POST} included. Each method throws the same
 * answer in case one is reached. The class reads and stores nothing.
 *
 * <p>An operation is built by moving the type-level mark onto the operations that stay reserved and removing it from
 * the one that is built, together with its {@code x-reserved} and its 501 pin in {@code registration/reserved-registration.feature}.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
@ReservedOperation
public class PackRegistrationController implements RegistrationApi {

    /** For Spring. */
    public PackRegistrationController() {}

    @Override
    public ResponseEntity<Void> deletePackArtifact(
            String packId, String kind, String artifactId, @Nullable String ifMatch) {
        throw notBuilt();
    }

    @Override
    public ResponseEntity<Void> deletePackRelationship(
            String packId, String from, String to, @Nullable String ifMatch) {
        throw notBuilt();
    }

    @Override
    public ResponseEntity<PackRegistration> getPack(String packId) {
        throw notBuilt();
    }

    @Override
    public ResponseEntity<PackRegistrationStatus> getPackRegistration(String registrationId) {
        throw notBuilt();
    }

    @Override
    public ResponseEntity<PackRegistrationStatus> registerPackFromArchive(
            String packId, Resource body, @Nullable String ifMatch, @Nullable String ifNoneMatch) {
        throw notBuilt();
    }

    @Override
    public ResponseEntity<PackRegistrationStatus> registerPackFromSource(
            PackSource packSource, @Nullable String ifMatch, @Nullable String ifNoneMatch) {
        throw notBuilt();
    }

    private static ResponseStatusException notBuilt() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
