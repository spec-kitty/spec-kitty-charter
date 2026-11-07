package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.RegistrationApi;
import ai.speckitty.charter.api.model.PackRegistration;
import ai.speckitty.charter.api.model.PackRegistrationStatus;
import ai.speckitty.charter.api.model.PackSource;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.ReservedOperation;
import ai.speckitty.charter.packs.application.ArchiveStaging;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackSummaryQuery;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * The inbound REST adapter of whole-pack registration (ADR-0065): {@code PUT /packs/{packId}} accepts an archive and
 * answers {@code 202} with the status the client polls, {@code GET /pack-registrations/{registrationId}} reads that
 * status, and {@code GET /packs/{packId}} reads the stored pack's summary (FR-024, FR-039). The checks that come before
 * the body is read (the flags, the packId grammar, the headers, the declared length, the queue) are
 * {@link RegistrationAdmission}'s, the body's size and media type {@link ArchiveBodyAdvice}'s, and the {@code 401} the
 * guard's; this class spools the judged body into the slot the admission took, hands the job its stager, and answers.
 * Everything after the {@code 202} is the job's. {@code POST /packs} and the two {@code DELETE}s are still reserved
 * (ADR-0020 D8): each is marked, so the composition root's {@code ReservedOperationInterceptor} answers {@code 501}
 * before the request is bound.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class PackRegistrationController implements RegistrationApi {

    private static final String ARCHIVE_FILE = "archive";

    private final RegistrationJobs jobs;
    private final PackSummaryQuery summaries;
    private final PackArchiveReader archives;
    private final PackRegistrationMapper mapper;
    private final PackSourceIntake intake;

    /**
     * A controller over the registration jobs and the stored packs, with no source of a registration built.
     *
     * @param jobs the registration jobs
     * @param summaries the stored-pack read
     * @param archives the archive extractor
     * @param mapper the contract shapes
     */
    PackRegistrationController(
            RegistrationJobs jobs,
            PackSummaryQuery summaries,
            PackArchiveReader archives,
            PackRegistrationMapper mapper) {
        this(jobs, summaries, archives, mapper, PackSourceIntake.none());
    }

    /**
     * A controller over the registration jobs, the stored packs and the sources a registration may name.
     *
     * @param jobs the registration jobs
     * @param summaries the stored-pack read
     * @param archives the archive extractor
     * @param mapper the contract shapes
     * @param intake what takes a registration from a source
     */
    @Autowired
    PackRegistrationController(
            RegistrationJobs jobs,
            PackSummaryQuery summaries,
            PackArchiveReader archives,
            PackRegistrationMapper mapper,
            PackSourceIntake intake) {
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.summaries = Objects.requireNonNull(summaries, "summaries");
        this.archives = Objects.requireNonNull(archives, "archives");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.intake = Objects.requireNonNull(intake, "intake");
    }

    @Override
    @ReservedOperation
    public ResponseEntity<Void> deletePackArtifact(
            String packId, String kind, String artifactId, @Nullable String ifMatch) {
        throw notBuilt();
    }

    @Override
    @ReservedOperation
    public ResponseEntity<Void> deletePackRelationship(
            String packId, String from, String to, @Nullable String ifMatch) {
        throw notBuilt();
    }

    @Override
    public ResponseEntity<PackRegistration> getPack(String packId) {
        var verified = summaries.summary(packId);
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .eTag(verified.value().revision().token())
                .body(mapper.summary(verified.value()));
    }

    @Override
    public ResponseEntity<PackRegistrationStatus> getPackRegistration(String registrationId) {
        RegistrationJobs.Status status = jobs.status(registrationId)
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.REGISTRATION_NOT_FOUND,
                        "No registration " + registrationId + " is held; it ended more than an hour ago, was evicted or"
                                + " never existed."));
        return ResponseEntity.ok(mapper.status(status));
    }

    @Override
    public ResponseEntity<PackRegistrationStatus> registerPackFromArchive(
            String packId, Resource body, @Nullable String ifMatch, @Nullable String ifNoneMatch) {
        HttpServletRequest request = currentRequest();
        RegistrationJobs.Reservation reservation = RegistrationAdmission.reservationOf(request);
        ArchiveBody archive = archiveOf(body);
        Path file = reservation.directory().resolve(ARCHIVE_FILE);
        try (InputStream bytes = archive.getInputStream()) {
            Files.copy(bytes, file);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot spool the archive of a registration", failure);
        }
        reservation.start(ArchiveStaging.of(archives, archive.format(), file));
        AuditContext.of(request).handedToJob();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create(mapper.statusPath(reservation.id())))
                .body(mapper.status(reservation.status()));
    }

    @Override
    @ReservedOperation
    public ResponseEntity<PackRegistrationStatus> registerPackFromSource(
            PackSource packSource, @Nullable String ifMatch, @Nullable String ifNoneMatch) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "not built yet: " + intake.worktree());
    }

    private static ArchiveBody archiveOf(Resource body) {
        if (body instanceof ArchiveBody archive) {
            return archive;
        }
        throw new IllegalStateException("the archive body was not judged by the archive guard");
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.currentRequestAttributes() instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest();
        }
        throw new IllegalStateException("no servlet request is being handled");
    }

    private static ResponseStatusException notBuilt() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
