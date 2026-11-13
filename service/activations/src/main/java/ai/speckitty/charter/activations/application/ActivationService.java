package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.ActivationRepository.CasResult;
import ai.speckitty.charter.activations.domain.ActivationRepository.DeleteResult;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.DocumentRules;
import ai.speckitty.charter.activations.domain.EffectiveSet;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.activations.domain.StaleIds;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The activation register's use cases (ADR-0020 D3-D6, ADR-0059 D4-D5): create, register (put), read, delete, and the
 * closure read. A write is checked against its closure before it is stored, so nothing is stored on a {@code 409} or
 * a {@code 422}; a read serves the stored document as stored, without a look at the packs (FR-017). A create judges
 * every id, pin and deny entry of its body; a put judges its body against the document it replaces (A4, B1,
 * {@link DocumentRules#checkReplace}), so a GET then PUT of the same body never fails without a pack change.
 *
 * <p>Each write and each closure read takes exactly one {@link AvailablePacks#snapshot} (NFR-007); a document read
 * and a delete take none. The headers arrive verbatim: an {@code If-Match} is compared strongly and an
 * {@code If-None-Match} weakly ({@link EntityTags}), and a missing document never matches an {@code If-Match}
 * (RFC 9110 &sect;13.1.1), so it answers {@code 412}.
 */
@Service
public class ActivationService {

    /** How often a put without a validator is judged again when other writers keep coming in between. */
    private static final int LATEST_ATTEMPTS = 8;

    private static final ActivationDocument NOTHING_STORED =
            ActivationDocument.builder().build().document();

    private final ActivationRepository repository;
    private final AvailablePacks packs;
    private final ActivationIdMint mint;
    private final WriteCoordination coordination;

    /**
     * The use cases over the register and the packs.
     *
     * @param repository the activation register
     * @param packs the packs, asked once per write and per closure read
     * @param mint the id mint of {@link #create}
     */
    public ActivationService(ActivationRepository repository, AvailablePacks packs, ActivationIdMint mint) {
        this(repository, packs, mint, WriteCoordination.NONE);
    }

    /**
     * A service over its ports, whose writes exclude a pack registration.
     *
     * @param repository the activation register
     * @param packs the packs
     * @param mint the id source
     * @param coordination what makes a write exclude a pack registration
     */
    @Autowired
    public ActivationService(
            ActivationRepository repository,
            AvailablePacks packs,
            ActivationIdMint mint,
            WriteCoordination coordination) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.packs = Objects.requireNonNull(packs, "packs");
        this.mint = Objects.requireNonNull(mint, "mint");
        this.coordination = Objects.requireNonNull(coordination, "coordination");
    }

    /**
     * The outcome of a put.
     *
     * @param stored the activation as now stored
     * @param created {@code true} when the write created it ({@code 201}), {@code false} when it replaced one
     * @param dropped the ids of the body the packs no longer hold that the replaced document named, dropped (A4)
     * @param droppedPins the pins of the body to a version no pack holds, dropped, each as written (B1)
     */
    public record Written(
            StoredActivation stored, boolean created, List<ArtifactRef> dropped, List<PackRef> droppedPins) {

        /**
         * Checks and copies the fields.
         *
         * @param stored the activation as now stored
         * @param created whether the write created it
         * @param dropped the dropped ids
         * @param droppedPins the dropped pins
         */
        public Written {
            Objects.requireNonNull(stored, "stored");
            dropped = List.copyOf(dropped);
            droppedPins = List.copyOf(droppedPins);
        }

        private Written(CasResult.Written written, StaleIds.Dropped drops) {
            this(written.stored(), written.created(), drops.dropped(), drops.droppedPins());
        }
    }

    /**
     * The outcome of a conditional read.
     *
     * @param <T> the answer read
     */
    public sealed interface Read<T> {

        /**
         * The client holds the current answer ({@code 304}).
         *
         * @param <T> the answer read
         * @param etag the current entity tag, unquoted
         */
        record NotModified<T>(String etag) implements Read<T> {}

        /**
         * The current answer.
         *
         * @param <T> the answer read
         * @param value the answer
         * @param etag its entity tag, unquoted
         */
        record Found<T>(T value, String etag) implements Read<T> {}
    }

    /**
     * Stores a document under an id the service mints (ADR-0020 D3).
     *
     * @param built the document as the request built it, with its shape violations
     * @return the stored activation
     * @throws CharterRefusal with {@code activation_invalid}, {@code artifact_required} or {@code artifact_locked} when
     *     the document breaks its closure, or with the refusal of the compose
     */
    public StoredActivation create(ActivationDocument.Built built) {
        return coordination.exclusively(() -> {
            ActivationDocument document = checked(built);
            ActivationId id = mint.mint();
            return repository
                    .create(id, document)
                    .orElseThrow(() -> new IllegalStateException("The minted id " + id + " is already taken."));
        });
    }

    /**
     * Registers a document under the client's id (ADR-0020 D6): {@code If-None-Match: *} creates only,
     * {@code If-Match} makes the write conditional, and without either the last writer wins.
     *
     * <p>The preconditions are evaluated first (RFC 9110 &sect;13.2.2): a malformed pair is a {@code 400}, and a stale
     * {@code If-Match}, or {@code If-None-Match: *} on a stored id, is a {@code 412} whatever the body holds, before any
     * snapshot of the packs is taken. Only a write whose precondition holds has its document judged. The register's
     * compare-and-set judges the precondition again, so a writer that comes in between still answers {@code 412}.
     *
     * <p>The body is judged against the document it replaces ({@link DocumentRules#checkReplace}, A4 and B1): the floor
     * on the deny entries it adds, and an id the packs no longer hold that the replaced document named, or a pin to a
     * version no pack holds, dropped and reported instead of refused. The document judged against is the one the
     * write replaces: the matched one under {@code If-Match}, none under {@code If-None-Match: *}, and without a
     * validator the one read just before the write, by a compare-and-set at its revision that is judged again from a
     * fresh read when another writer came in between, so the last writer still wins.
     *
     * @param id the activation's id
     * @param built the document as the request built it
     * @param ifMatch the {@code If-Match} header, verbatim; null when absent
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the stored activation, created or replaced
     * @throws MalformedPreconditionException when both headers are sent, or an {@code If-None-Match} other than
     *     {@code *}
     * @throws PreconditionException ({@code STALE}) when a precondition does not hold
     * @throws CharterRefusal when the document breaks its closure
     */
    public Written put(
            ActivationId id, ActivationDocument.Built built, @Nullable String ifMatch, @Nullable String ifNoneMatch) {
        return coordination.exclusively(() -> {
            Expected expected = expectedOf(id, preconditionOf(ifMatch, ifNoneMatch));
            PacksSession session = packs.session(packIdsOf(built.document()));
            ActivationDocument.Built normalized = DocumentRules.normalize(built, session.snapshot());
            return switch (expected) {
                case Expected.Absent() -> created(id, normalized, session).orElseThrow(() -> stale(id));
                case Expected.At(StoredActivation matched) ->
                    replaced(matched, normalized, session).orElseThrow(() -> stale(null));
                case Expected.Latest() -> latest(id, normalized, session);
            };
        });
    }

    /** The write without a validator: the last writer wins, judged against the document it replaces. */
    private Written latest(ActivationId id, ActivationDocument.Built normalized, PacksSession session) {
        for (int attempt = 0; attempt < LATEST_ATTEMPTS; attempt++) {
            Optional<StoredActivation> current = repository.find(id);
            Optional<Written> written = current.isPresent()
                    ? replaced(current.orElseThrow(), normalized, session)
                    : created(id, normalized, session);
            if (written.isPresent()) {
                return written.orElseThrow();
            }
        }
        throw stale(id);
    }

    /** Creates the document when the id is free; empty when another writer took it first. */
    private Optional<Written> created(ActivationId id, ActivationDocument.Built normalized, PacksSession session) {
        StaleIds.Dropped drops = judged(normalized, NOTHING_STORED, session);
        return repository
                .create(id, drops.document())
                .map(stored -> new Written(new CasResult.Written(stored, true), drops));
    }

    /** Replaces a stored document at its revision; empty when another writer came in between. */
    private Optional<Written> replaced(
            StoredActivation current, ActivationDocument.Built normalized, PacksSession session) {
        StaleIds.Dropped drops = judged(
                DocumentRules.normalize(normalized, session.snapshot(), current.document()),
                current.document(),
                session);
        return switch (repository.replace(current.id(), Optional.of(current.revision()), drops.document())) {
            case CasResult.Written written -> Optional.of(new Written(written, drops));
            case CasResult.Stale _, CasResult.Missing _ -> Optional.empty();
        };
    }

    /**
     * The body judged against the document it replaces, less what the packs no longer satisfy (A4, B1). A deny entry the
     * body adds on a required artifact is judged by the session's in-force resolution (IC-13), and the document as it
     * will be stored by the slot check of its closure (C38, {@link PacksSession#requireComposable}).
     */
    private static StaleIds.Dropped judged(
            ActivationDocument.Built normalized, ActivationDocument replaced, PacksSession session) {
        ClosureSnapshot snapshot = session.snapshot();
        DocumentRules.checkReplace(normalized, replaced, snapshot, session::inForce)
                .ifPresent(refusal -> {
                    throw refusal;
                });
        StaleIds.Dropped drops = StaleIds.drop(normalized.document(), snapshot);
        session.requireComposable(EffectiveSet.queryOf(drops.document()));
        return drops;
    }

    /**
     * Deletes a stored document (ADR-0020 D6).
     *
     * @param id the activation's id
     * @param ifMatch the {@code If-Match} header, verbatim; null when absent
     * @throws CharterRefusal with {@code activation_not_found} when nothing is stored under the id
     * @throws PreconditionException ({@code STALE}) when the {@code If-Match} does not name the current revision, or the
     *     document was removed by another request after the {@code If-Match} matched it
     */
    public void delete(ActivationId id, @Nullable String ifMatch) {
        coordination.exclusively(() -> {
            Optional<Revision> expected = ifMatch == null ? Optional.empty() : matched(id, ifMatch);
            DeleteResult result = repository.delete(id, expected);
            switch (result) {
                case DeleteResult.Deleted() -> {}
                // With If-Match, a document another deleter removed since this request matched it is no stored
                // revision:
                // 412.
                case DeleteResult.Missing() -> throw ifMatch == null ? notFound(id) : stale(id);
                case DeleteResult.Stale _ -> throw stale(id);
            }
        });
    }

    /**
     * The closure read (#360): the packs the stored document builds on, from one snapshot. Its entity tag is
     * {@code sha256:} over the revision and the snapshot's fingerprint, so it changes when either does.
     *
     * @param id the activation's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the closure, or {@code NotModified} when the header matches its entity tag
     * @throws CharterRefusal with {@code activation_not_found} when nothing is stored under the id, with
     *     {@code pack_unreadable} when a pack the document names is no longer configured, or with the refusal of the
     *     compose. The unconfigured pack is a 500 {@code pack_unreadable}, not the 404 {@code pack_not_found} a
     *     request naming an unknown pack gets: the request names only the activation, and the pack is named by the
     *     stored document, which is server-side state the configuration change made stale.
     */
    public Read<ClosureSnapshot> packs(ActivationId id, @Nullable String ifNoneMatch) {
        StoredActivation stored = found(id);
        ClosureSnapshot snapshot = packs.snapshot(packIdsOf(stored.document()));
        if (!snapshot.resolved()) {
            throw new CharterRefusal(
                    RefusalCode.PACK_UNREADABLE,
                    "The activation builds on " + String.join(", ", snapshot.unknownPacks())
                            + ", which no pack is configured as any more.");
        }
        String etag = closureTag(stored.revision(), snapshot.fingerprint());
        return EntityTags.noneMatch(ifNoneMatch, etag)
                ? new Read.NotModified<>(etag)
                : new Read.Found<>(snapshot, etag);
    }

    /**
     * A create's document, when it may be stored: one snapshot of the closure it names, and every rule over it, the slot
     * check of C38 last ({@link PacksSession#requireComposable}).
     */
    private ActivationDocument checked(ActivationDocument.Built built) {
        PacksSession session = packs.session(packIdsOf(built.document()));
        ClosureSnapshot snapshot = session.snapshot();
        ActivationDocument.Built normalized = DocumentRules.normalize(built, snapshot);
        DocumentRules.check(normalized, snapshot, session::inForce).ifPresent(refusal -> {
            throw refusal;
        });
        session.requireComposable(EffectiveSet.queryOf(normalized.document()));
        return normalized.document();
    }

    private static Optional<List<String>> packIdsOf(ActivationDocument document) {
        return document.packs().map(list -> list.stream().map(PackRef::packId).toList());
    }

    /** What a write's headers ask for, judged before anything is read. */
    private sealed interface Precondition {
        record CreateOnly() implements Precondition {}

        record Matching(String value) implements Precondition {}

        record None() implements Precondition {}
    }

    /**
     * What the compare-and-set expects, judged against the register now: nothing stored, the stored activation the
     * {@code If-Match} matched, or whatever is stored when the write is made.
     */
    private sealed interface Expected {
        record Absent() implements Expected {}

        record At(StoredActivation matched) implements Expected {}

        record Latest() implements Expected {}
    }

    /**
     * The precondition judged against what is stored now, before the body is: a create-only write on a stored id, or
     * an {@code If-Match} that names no stored revision, is {@code 412} here.
     */
    private Expected expectedOf(ActivationId id, Precondition precondition) {
        return switch (precondition) {
            case Precondition.CreateOnly() -> {
                if (repository.find(id).isPresent()) {
                    throw stale(id);
                }
                yield new Expected.Absent();
            }
            case Precondition.Matching(String value) -> new Expected.At(matchedActivation(id, value));
            case Precondition.None() -> new Expected.Latest();
        };
    }

    private static Precondition preconditionOf(@Nullable String ifMatch, @Nullable String ifNoneMatch) {
        if (ifMatch != null && ifNoneMatch != null) {
            throw MalformedPreconditionException.bothSent();
        }
        if (ifNoneMatch != null && !EntityTags.isWildcard(ifNoneMatch)) {
            throw MalformedPreconditionException.notAWildcard(ifNoneMatch);
        }
        if (ifNoneMatch != null) {
            return new Precondition.CreateOnly();
        }
        return ifMatch == null ? new Precondition.None() : new Precondition.Matching(ifMatch);
    }

    /**
     * The revision an {@code If-Match} names, as the compare-and-set expects it: the stored revision when the header
     * matches it ({@code *} matches any stored one); the write then fails only when another writer comes in between.
     */
    private Optional<Revision> matched(ActivationId id, String ifMatch) {
        return Optional.of(matchedActivation(id, ifMatch).revision());
    }

    private StoredActivation matchedActivation(ActivationId id, String ifMatch) {
        StoredActivation current = repository.find(id).orElseThrow(() -> stale(id));
        if (!IfMatchTags.names(ifMatch, current, packs)) {
            throw stale(id);
        }
        return current;
    }

    private StoredActivation found(ActivationId id) {
        return repository.find(id).orElseThrow(() -> notFound(id));
    }

    private static CharterRefusal notFound(ActivationId id) {
        return new CharterRefusal(RefusalCode.ACTIVATION_NOT_FOUND, "No activation is stored as '" + id + "'.");
    }

    private static PreconditionException stale(@Nullable ActivationId id) {
        String which = id == null ? "The activation" : "The activation '" + id + "'";
        return new PreconditionException(
                PreconditionException.Kind.STALE,
                which + " is not at the revision the request names, or does not exist; read it again.");
    }

    /** The closure read's entity tag, unquoted: {@code sha256:} over the revision and the fingerprint. */
    static String closureTag(Revision revision, String fingerprint) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((revision.token() + "\n" + fingerprint).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides SHA-256.", e);
        }
    }
}
