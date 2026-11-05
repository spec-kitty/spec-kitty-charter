package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.ActivationRepository.CasResult;
import ai.speckitty.charter.activations.domain.ActivationRepository.DeleteResult;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.DocumentRules;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.Revision;
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
 * a {@code 422}; a read serves the stored document as stored, without a look at the packs (FR-017).
 *
 * <p>Each write and each closure read takes exactly one {@link AvailablePacks#snapshot} (NFR-007); a document read
 * and a delete take none. The headers arrive verbatim: an {@code If-Match} is compared strongly and an
 * {@code If-None-Match} weakly ({@link EntityTags}), and a missing document never matches an {@code If-Match}
 * (RFC 9110 &sect;13.1.1), so it answers {@code 412}.
 */
@Service
public class ActivationService {

    private final ActivationRepository repository;
    private final AvailablePacks packs;
    private final ActivationIdMint mint;

    /**
     * The use cases over the register and the packs.
     *
     * @param repository the activation register
     * @param packs the packs, asked once per write and per closure read
     * @param mint the id mint of {@link #create}
     */
    @Autowired
    public ActivationService(ActivationRepository repository, AvailablePacks packs, ActivationIdMint mint) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.packs = Objects.requireNonNull(packs, "packs");
        this.mint = Objects.requireNonNull(mint, "mint");
    }

    /**
     * The outcome of a write.
     *
     * @param stored the activation as now stored
     * @param created {@code true} when the write created it ({@code 201}), {@code false} when it replaced one
     */
    public record Written(StoredActivation stored, boolean created) {}

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
        ActivationDocument document = checked(built);
        ActivationId id = mint.mint();
        return repository
                .create(id, document)
                .orElseThrow(() -> new IllegalStateException("The minted id " + id + " is already taken."));
    }

    /**
     * Registers a document under the client's id (ADR-0020 D6): {@code If-None-Match: *} creates only,
     * {@code If-Match} makes the write conditional, and without either the last writer wins.
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
        Precondition precondition = preconditionOf(ifMatch, ifNoneMatch);
        ActivationDocument document = checked(built);
        return switch (precondition) {
            case Precondition.CreateOnly() ->
                new Written(repository.create(id, document).orElseThrow(() -> stale(id)), true);
            case Precondition.Matching(String value) -> written(repository.replace(id, matched(id, value), document));
            case Precondition.None() -> written(repository.replace(id, Optional.empty(), document));
        };
    }

    /**
     * Reads a stored document, as stored (ADR-0059 D5).
     *
     * @param id the activation's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the activation, or {@code NotModified} when the header matches its revision
     * @throws CharterRefusal with {@code activation_not_found} when nothing is stored under the id
     */
    public Read<StoredActivation> get(ActivationId id, @Nullable String ifNoneMatch) {
        StoredActivation stored = found(id);
        String etag = stored.revision().token();
        return EntityTags.noneMatch(ifNoneMatch, etag) ? new Read.NotModified<>(etag) : new Read.Found<>(stored, etag);
    }

    /**
     * Deletes a stored document (ADR-0020 D6).
     *
     * @param id the activation's id
     * @param ifMatch the {@code If-Match} header, verbatim; null when absent
     * @throws CharterRefusal with {@code activation_not_found} when nothing is stored under the id
     * @throws PreconditionException ({@code STALE}) when the {@code If-Match} does not name the current revision
     */
    public void delete(ActivationId id, @Nullable String ifMatch) {
        Optional<Revision> expected = ifMatch == null ? Optional.empty() : matched(id, ifMatch);
        DeleteResult result = repository.delete(id, expected);
        switch (result) {
            case DeleteResult.Deleted() -> {}
            case DeleteResult.Missing() -> throw notFound(id);
            case DeleteResult.Stale _ -> throw stale(id);
        }
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
     *     compose
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

    /** The document, when it may be stored: one snapshot of the closure it names, and the rules over it. */
    private ActivationDocument checked(ActivationDocument.Built built) {
        ClosureSnapshot snapshot = packs.snapshot(packIdsOf(built.document()));
        DocumentRules.check(built, snapshot).ifPresent(refusal -> {
            throw refusal;
        });
        return built.document();
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

    private static Precondition preconditionOf(@Nullable String ifMatch, @Nullable String ifNoneMatch) {
        if (ifMatch != null && ifNoneMatch != null) {
            throw new MalformedPreconditionException("A write carries If-Match or If-None-Match, not both.");
        }
        if (ifNoneMatch != null && !EntityTags.isWildcard(ifNoneMatch)) {
            throw new MalformedPreconditionException("A write's If-None-Match is *, to create only; nothing else.");
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
        Revision current = repository.find(id).orElseThrow(() -> stale(id)).revision();
        if (!EntityTags.matches(ifMatch, current.token())) {
            throw stale(id);
        }
        return Optional.of(current);
    }

    private Written written(CasResult result) {
        return switch (result) {
            case CasResult.Written(StoredActivation stored, boolean created) -> new Written(stored, created);
            case CasResult.Stale _, CasResult.Missing _ -> throw stale(null);
        };
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
