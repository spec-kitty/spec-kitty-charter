package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.RegistrationApi;
import ai.speckitty.charter.api.model.PackDocumentRead;
import ai.speckitty.charter.api.model.PackRegistration;
import ai.speckitty.charter.api.model.PackRegistrationStatus;
import ai.speckitty.charter.api.model.PackSource;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ArchiveStaging;
import ai.speckitty.charter.packs.application.OwnerOnly;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.PackSummaryQuery;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
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
 * guard's; this class spools the judged body, takes the slot, hands the job its stager, and answers.
 * {@code POST /packs} names a source instead: its kind's flag and availability, the slot and the preconditions come after
 * the JSON body is bound (the target and the kind are in it), and the fetch is the job's. Everything after the {@code 202}
 * is the job's. The two {@code DELETE}s are {@link PackRemovalController}'s: they carry the same contract tag, so the
 * generated interface is this one.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class PackRegistrationController implements RegistrationApi {

    private static final String ARCHIVE_FILE = "archive";
    private static final int SPOOL_BUFFER_BYTES = 64 * 1024;

    private final RegistrationJobs jobs;
    private final PackSummaryQuery summaries;
    private final PackArchiveReader archives;
    private final PackRegistrationMapper mapper;
    private final PackSourceIntake intake;
    private final PackRemovalController removals;
    private final PackDocumentController documents;

    /**
     * A controller over the registration jobs, the stored packs, the sources a registration may name and the removals.
     *
     * @param jobs the registration jobs
     * @param summaries the stored-pack read
     * @param archives the archive extractor
     * @param mapper the contract shapes
     * @param intake what takes a registration from a source
     * @param removals the adapter of the two {@code DELETE}s
     * @param documents the adapter of the document read, in both its forms
     */
    @Autowired
    PackRegistrationController(
            RegistrationJobs jobs,
            PackSummaryQuery summaries,
            PackArchiveReader archives,
            PackRegistrationMapper mapper,
            PackSourceIntake intake,
            PackRemovalController removals,
            PackDocumentController documents) {
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.summaries = Objects.requireNonNull(summaries, "summaries");
        this.archives = Objects.requireNonNull(archives, "archives");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.intake = Objects.requireNonNull(intake, "intake");
        this.removals = Objects.requireNonNull(removals, "removals");
        this.documents = Objects.requireNonNull(documents, "documents");
    }

    @Override
    public ResponseEntity<Void> deletePackArtifact(
            String packId, String kind, String artifactId, @Nullable String ifMatch) {
        return removals.deleteArtifact(packId, kind, artifactId, ifMatch);
    }

    @Override
    public ResponseEntity<Void> deletePackRelationship(
            String packId, String from, String to, @Nullable String ifMatch) {
        return removals.deleteRelationship(packId, from, to, ifMatch);
    }

    @Override
    public ResponseEntity<PackRegistration> getPack(String packId) {
        var verified = summaries.summary(packId);
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .eTag(verified.value().revision().token())
                .body(mapper.summary(verified.value()));
    }

    /**
     * The {@code application/json} form of the document read, and the one operation of the contract: the generated
     * interface lists {@code application/json} first, so it types this method, and {@code text/markdown} and the
     * problem type after it. Its mapping is narrowed to JSON here, so that it and {@link #getPackDocumentMarkdown} are
     * disjoint and Spring picks between them by the request's {@code Accept}: by quality, then by specificity (RFC 9110,
     * section 12.5.1), {@code text/*} to Markdown, {@code application/*}, {@code *}{@code /*} and no {@code Accept}
     * to JSON. An {@code Accept} that names neither is no match, and Spring's {@code 406} is rendered by
     * {@code ProblemHandler} as the code-less problem. The parameter annotations are the interface's.
     *
     * <p>A tie is two named types of equal quality and equal specificity ({@code text/markdown, application/json}).
     * RFC 9110 leaves it to the server, and the two mappings settle it by the order the client wrote the types in; a
     * range that covers both ({@code *}{@code /*}) is not such a tie, and the JSON form, the form the contract lists
     * first, answers it. The negotiation is Spring's mapping, not ours: none of it is written here (#451, the
     * decision of the document read's spike). {@code PackDocumentControllerTest} has each case.
     *
     * @param packId the pack identifier
     * @param ifNoneMatch the request's {@code If-None-Match}, verbatim, or null
     * @return {@code 304}, or {@code 200} with the title, the body and the links
     */
    @Override
    @GetMapping(value = RegistrationApi.PATH_GET_PACK_DOCUMENT, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PackDocumentRead> getPackDocument(String packId, @Nullable String ifNoneMatch) {
        return documents.json(packId, ifNoneMatch);
    }

    /**
     * The {@code text/markdown} form of the document read: the held {@code README.md}. It is not on the generated
     * interface, which types the operation by its first media type, JSON; a {@code String} body has no DTO to generate
     * (ADR-0004 holds for the JSON form).
     *
     * @param packId the pack identifier
     * @param ifNoneMatch the request's {@code If-None-Match}, verbatim, or null
     * @return {@code 304}, or {@code 200} with the held text
     */
    @GetMapping(value = RegistrationApi.PATH_GET_PACK_DOCUMENT, produces = "text/markdown")
    public ResponseEntity<String> getPackDocumentMarkdown(
            @PathVariable("packId") String packId,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) @Nullable String ifNoneMatch) {
        return documents.markdown(packId, ifNoneMatch);
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
        ArchiveBody archive = archiveOf(body);
        AuditContext audit = AuditContext.of(request);
        // The body is spooled before a slot is taken, so a slow upload holds none (#418, A4, ADR-0065 D8). The upload,
        // and its place among the ten that may be open, are the admission's (#418, F1).
        RegistrationJobs.Upload upload = RegistrationAdmission.admittedUpload(request);
        try {
            Path file = upload.directory().resolve(ARCHIVE_FILE);
            long bytes = spool(archive, file);
            RegistrationJobs.Reservation reservation = jobs.reserve(
                    packId,
                    RegistrationAdmission.admittedPreconditions(request),
                    RegistrationAdmission.originOf(request),
                    upload,
                    bytes);
            try {
                audit.revisionBefore(reservation.revisionBefore());
                // The status the 202 answers is read before the job can start: a worker may be running it at once.
                RegistrationJobs.Status accepted = reservation.status();
                if (!reservation.start(ArchiveStaging.of(jobs.staging(), archives, archive.format(), file))) {
                    // The deadline ended it first: the client gets the end the audit records, not a failure.
                    accepted = reservation.status();
                }
                audit.handedToJob();
                return accepted(reservation.id(), accepted);
            } catch (RuntimeException failure) {
                reservation.abandon();
                throw failure;
            }
        } finally {
            upload.discard();
        }
    }

    /**
     * Copies the body, which is never held in memory, to the job's owner-only spool file (#418, A3). A body that stops
     * before its end, because the client went away, is a {@code 400}; a file that cannot be written is the service's own
     * failure. The size limit is the body stream's: it refuses the byte after 32 MiB with the {@code 413}.
     *
     * @return the bytes spooled
     */
    private static long spool(ArchiveBody archive, Path file) {
        byte[] buffer = new byte[SPOOL_BUFFER_BYTES];
        long total = 0;
        try (InputStream bytes = archive.getInputStream();
                OutputStream spooled = OwnerOnly.newFile(file)) {
            while (true) {
                int read;
                try {
                    read = bytes.read(buffer);
                } catch (IOException broken) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The body could not be read.", broken);
                }
                if (read < 0) {
                    return total;
                }
                spooled.write(buffer, 0, read);
                total += read;
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot spool the archive of a registration", failure);
        }
    }

    @Override
    public ResponseEntity<PackRegistrationStatus> registerPackFromSource(
            PackSource packSource, @Nullable String ifMatch, @Nullable String ifNoneMatch) {
        HttpServletRequest request = currentRequest();
        PackSourceRequest source = sourceOf(packSource);
        String locator = AuditLocators.of(source);
        AuditContext audit = AuditContext.of(request);
        audit.packId(packSource.getPackId());
        audit.source(source.kind().wire(), locator);
        RegistrationJobs.Stager stager = intake.stagerFor(source, packSource.getPackId());
        requirePreconditionForBranch(source, RegistrationAdmission.admittedPreconditions(request));
        RegistrationJobs.Origin origin = new RegistrationJobs.Origin(
                intake.worktree(),
                AuditContext.credentialOf(request),
                "registerPackFromSource",
                source.kind().wire(),
                locator);
        RegistrationJobs.Reservation reservation =
                jobs.reserve(packSource.getPackId(), RegistrationAdmission.admittedPreconditions(request), origin);
        try {
            audit.revisionBefore(reservation.revisionBefore());
            // The status the 202 answers is read before the job can start: a worker may be running it at once.
            RegistrationJobs.Status accepted = reservation.status();
            if (!reservation.start(stager)) {
                // The deadline ended it first: the client gets the end the audit records, not a failure.
                accepted = reservation.status();
            }
            audit.handedToJob();
            return accepted(reservation.id(), accepted);
        } catch (RuntimeException failure) {
            reservation.abandon();
            throw failure;
        }
    }

    /**
     * A git source that names a branch and no commit registers whatever the branch points at when it is fetched, so it
     * must say what it replaces: {@code If-Match} on the target pack, or {@code If-None-Match: *} to create it. Without
     * either it is {@code 428}, answered before a slot is taken (post-plan security squad 7).
     */
    private static void requirePreconditionForBranch(PackSourceRequest source, Preconditions preconditions) {
        if (source.branchWithoutCommit() && preconditions.ifMatch() == null && !preconditions.ifNoneMatchAny()) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "A git source that names a branch and no commit needs If-Match on the target pack, or"
                            + " If-None-Match: * to create it, or a commit.");
        }
    }

    private ResponseEntity<PackRegistrationStatus> accepted(String id, RegistrationJobs.Status status) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create(mapper.statusPath(id)))
                .body(mapper.status(status));
    }

    private static PackSourceRequest sourceOf(PackSource packSource) {
        PackSourceKind kind = PackSourceKind.fromWire(packSource.getKind().getValue())
                .orElseThrow(() -> new IllegalStateException("a source kind the contract lists has no kind"));
        return new PackSourceRequest(
                kind,
                packSource.getUrl(),
                packSource.getRef(),
                packSource.getCommit(),
                packSource.getSha256(),
                packSource.getPath());
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
}
