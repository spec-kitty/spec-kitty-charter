package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.ArtifactReference;
import ai.speckitty.charter.api.model.PackDocumentRead;
import ai.speckitty.charter.api.model.PackImpact;
import ai.speckitty.charter.api.model.PackImpactProblem;
import ai.speckitty.charter.api.model.PackRegistration;
import ai.speckitty.charter.api.model.PackRegistrationNotices;
import ai.speckitty.charter.api.model.PackRegistrationProblem;
import ai.speckitty.charter.api.model.PackRegistrationStatus;
import ai.speckitty.charter.api.model.PackSourceRecord;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.PackDocumentQuery;
import ai.speckitty.charter.packs.application.PackRegistrationService.RegistrationOutcome;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackSummaryQuery;
import ai.speckitty.charter.packs.application.PackUninstallService;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.ImpactCheck;
import ai.speckitty.charter.web.RefusalHandler;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Maps the registration jobs and the stored pack summary to the contract shapes (T078): the
 * {@code PackRegistrationStatus} the {@code 202} and the status read answer, the {@code PackRegistration} of a succeeded
 * registration and of {@code GET /packs/{packId}}, and the problem of a refused one. The status carries the links the
 * relation table allows: {@code self}, and {@code pack} once the registration succeeded. A stored pack links
 * {@code self}, {@code artifacts}, {@code doc} when it holds a document, and {@code presets}.
 */
@Component
final class PackRegistrationMapper {

    private static final Logger LOG = LoggerFactory.getLogger(PackRegistrationMapper.class);
    private static final String PACKS = "packs";
    private static final String REGISTRATIONS = "pack-registrations";
    private static final String ARTIFACTS = "artifacts";
    private static final String DOC = "doc";
    private static final String PRESETS = "presets";

    private final Hypermedia links;
    private final RefusalHandler refusals;

    /**
     * A mapper over the link builder and the refusal renderer.
     *
     * @param links the link builder
     * @param refusals the renderer of refusals, which owns their statuses
     */
    @Autowired
    PackRegistrationMapper(Hypermedia links, RefusalHandler refusals) {
        this.links = Objects.requireNonNull(links, "links");
        this.refusals = Objects.requireNonNull(refusals, "refusals");
    }

    /**
     * The path of a registration's status, the {@code Location} of the {@code 202}.
     *
     * @param registrationId the registration id
     * @return the path, below the server's origin
     */
    String statusPath(String registrationId) {
        return links.link(REGISTRATIONS, registrationId).href();
    }

    PackRegistrationStatus status(RegistrationJobs.Status status) {
        Map<Relation, Hypermedia.LinkValue> own = new EnumMap<>(Relation.class);
        own.put(Relation.SELF, links.link(REGISTRATIONS, status.id()));
        PackRegistrationStatus body = new PackRegistrationStatus(
                status.id(),
                PackRegistrationStatus.StateEnum.fromValue(status.state().name().toLowerCase(Locale.ROOT)),
                status.packId(),
                at(status.acceptedAt()),
                Map.of());
        if (status.finishedAt() != null) {
            body.setFinishedAt(at(status.finishedAt()));
        }
        if (status.ending() instanceof RegistrationJobs.Succeeded done) {
            body.setResult(result(done.outcome(), done.source()));
            if (!done.outcome().dryRun()) {
                // A dry run stored nothing, so there is no pack to link to (ADR-0081 D5).
                own.put(Relation.PACK, links.link(PACKS, done.outcome().pack().value()));
            }
        } else if (status.ending() instanceof RegistrationJobs.Refused refused) {
            body.setProblem(problem(refused));
        }
        body.setLinks(Hypermedia.wire(own));
        return body;
    }

    PackRegistration result(RegistrationOutcome outcome, Map<String, String> source) {
        PackRegistration body = stored(
                outcome.pack().value(),
                outcome.descriptor(),
                outcome.level(),
                outcome.revision(),
                outcome.hasDocument());
        if (!source.isEmpty()) {
            body.setSource(sourceRecord(source));
        }
        body.setOutcome(
                PackRegistration.OutcomeEnum.fromValue(outcome.outcome().name().toLowerCase(Locale.ROOT)));
        outcome.previousVersion().ifPresent(body::setPreviousVersion);
        if (outcome.dryRun()) {
            body.setDryRun(true);
        }
        body.setNotices(notices(outcome.notices()));
        body.setDiagnostics(outcome.diagnostics().stream()
                .map(PackRegistrationMapper::diagnostic)
                .toList());
        return body;
    }

    /** A load warning on the wire: its code token and its detail, which names pack-relative paths only. */
    private static ai.speckitty.charter.api.model.Diagnostic diagnostic(Diagnostic warning) {
        return new ai.speckitty.charter.api.model.Diagnostic(
                ai.speckitty.charter.api.model.Diagnostic.CodeEnum.fromValue(
                        warning.code().token()),
                warning.detail());
    }

    /** The source as the request named it, which the fetcher recorded: its kind, and the URL or the path. */
    private static PackSourceRecord sourceRecord(Map<String, String> source) {
        PackSourceRecord record = new PackSourceRecord(PackSourceRecord.KindEnum.fromValue(source.get("kind")));
        record.setUrl(source.get("url"));
        record.setRef(source.get("ref"));
        record.setPath(source.get("path"));
        record.setCommit(source.get("commit"));
        return record;
    }

    PackRegistration summary(PackSummaryQuery.Summary summary) {
        return stored(
                summary.pack().value(),
                summary.descriptor(),
                summary.level(),
                summary.revision(),
                summary.hasDocument());
    }

    /**
     * The pack's document as the {@code application/json} form of the document read: its title and body, and the links
     * {@code self} (the document) and {@code pack} (the pack it belongs to).
     */
    PackDocumentRead document(PackDocumentQuery.Read read) {
        Map<Relation, Hypermedia.LinkValue> own = new EnumMap<>(Relation.class);
        own.put(Relation.SELF, links.link(PACKS, read.pack().value(), DOC));
        own.put(Relation.PACK, links.link(PACKS, read.pack().value()));
        PackDocumentRead body = new PackDocumentRead(read.document().body(), Hypermedia.wire(own));
        read.document().title().ifPresent(body::setTitle);
        return body;
    }

    private PackRegistration stored(
            String packId, PackDescriptor descriptor, PackLevel level, PackRevision revision, boolean hasDocument) {
        Map<Relation, Hypermedia.LinkValue> own = new EnumMap<>(Relation.class);
        own.put(Relation.SELF, links.link(PACKS, packId));
        own.put(Relation.ARTIFACTS, links.templated("{?kind}", PACKS, packId, ARTIFACTS));
        if (hasDocument) {
            own.put(Relation.DOC, links.link(PACKS, packId, DOC));
        }
        own.put(Relation.PRESETS, links.link(PACKS, packId, PRESETS));
        return new PackRegistration(
                packId,
                descriptor.packId(),
                descriptor.name(),
                descriptor.packVersion(),
                PackRegistration.LevelEnum.fromValue(level.token()),
                revision.token(),
                Hypermedia.wire(own));
    }

    private static PackRegistrationNotices notices(ImpactCheck.Notices notices) {
        return new PackRegistrationNotices()
                .floorAdded(references(notices.floorAdded()))
                .floorRemoved(references(notices.floorRemoved()))
                .locksAdded(references(notices.locksAdded()))
                .locksRemoved(references(notices.locksRemoved()))
                .activationsAffected(notices.activationsAffected());
    }

    /**
     * The artifacts as references. Notices name activatable kinds only: an entry of another kind (a template, an asset,
     * a guard, a lifecycle) is left out of the response and logged at WARN with its URN (operator ruling; widening the
     * kinds is issue #417).
     */
    private static List<ArtifactReference> references(List<String> urns) {
        return urns.stream()
                .flatMap(token -> Urn.parse(token).stream())
                .flatMap(urn -> reference(urn).stream())
                .toList();
    }

    private static java.util.Optional<ArtifactReference> reference(Urn urn) {
        java.util.Optional<ArtifactReference> reference =
                kindOf(urn).map(kind -> new ArtifactReference(kind, urn.id()));
        if (reference.isEmpty()) {
            LOG.warn("registration notice for {} left out: its kind is not an activatable kind (issue #417)", urn);
        }
        return reference;
    }

    private static java.util.Optional<ArtifactReference.KindEnum> kindOf(Urn urn) {
        try {
            return java.util.Optional.of(
                    ArtifactReference.KindEnum.fromValue(urn.kind().name().toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException unlisted) {
            return java.util.Optional.empty();
        }
    }

    /**
     * The answer of a dry run of a pack's removal (ADR-0081 D5): what the removal would do, or the refusal it would
     * answer, with a {@code self} link to the pack it judged, the resource the {@code DELETE} names.
     *
     * @param outcome what the removal's checks reached
     * @return the body
     */
    PackImpact impact(PackUninstallService.Outcome outcome) {
        PackImpact body = new PackImpact(
                outcome.pack().value(),
                PackImpact.OperationEnum.DELETE,
                outcome.refused().isPresent() ? PackImpact.OutcomeEnum.REFUSED : PackImpact.OutcomeEnum.DELETED,
                outcome.revision().token(),
                Map.of());
        outcome.version().ifPresent(body::setVersion);
        outcome.refused()
                .ifPresentOrElse(
                        refused -> body.setProblem(impactProblem(refusals.registrationProblem(refused))),
                        () -> body.setNotices(notices(outcome.notices())));
        Map<Relation, Hypermedia.LinkValue> own = new EnumMap<>(Relation.class);
        own.put(Relation.SELF, links.link(PACKS, outcome.pack().value()));
        body.setLinks(Hypermedia.wire(own));
        return body;
    }

    /** The removal's own refusal, in the shape the PackImpact carries it. */
    private static PackImpactProblem impactProblem(PackRegistrationProblem problem) {
        PackImpactProblem body = new PackImpactProblem(
                problem.getTitle(),
                problem.getStatus(),
                PackImpactProblem.CodeEnum.fromValue(Objects.requireNonNull(problem.getCode(), "code")));
        body.setDetail(problem.getDetail());
        body.setPacks(problem.getPacks());
        body.setViolations(problem.getViolations());
        return body;
    }

    private PackRegistrationProblem problem(RegistrationJobs.Refused refused) {
        return switch (refused.kind()) {
            case CODED -> refusals.registrationProblem(Objects.requireNonNull(refused.refusal()));
            case STALE -> plain(HttpStatus.PRECONDITION_FAILED, refused.detail());
            case BUSY -> plain(HttpStatus.SERVICE_UNAVAILABLE, refused.detail());
            case FAILED ->
                refusals.registrationProblem(new CharterRefusal(RefusalCode.INTERNAL_ERROR, refused.detail()));
            case UPSTREAM_FAILED -> plain(HttpStatus.BAD_GATEWAY, refused.detail());
            case UPSTREAM_TIMEOUT -> plain(HttpStatus.GATEWAY_TIMEOUT, refused.detail());
        };
    }

    private static PackRegistrationProblem plain(HttpStatus status, String detail) {
        PackRegistrationProblem problem = new PackRegistrationProblem(status.getReasonPhrase(), status.value());
        problem.setDetail(detail);
        return problem;
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
