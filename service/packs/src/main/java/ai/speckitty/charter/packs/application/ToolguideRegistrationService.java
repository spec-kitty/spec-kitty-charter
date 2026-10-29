package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import java.net.URI;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Registers a toolguide into a pack (FR-015, US3), the write half of the toolguide slice. It copies
 * {@link DirectiveRegistrationService}'s discipline: every write is <strong>functional</strong> (ADR-0026) — there is
 * no CRUD, no {@code DELETE}/{@code PATCH}; a registration goes <em>load &rarr; mutate &rarr; revalidate the whole
 * {@link Pack} &rarr; commit atomically</em>, so the aggregate invariants (belongs-to-pack, no-duplicate-id = FR-008)
 * gate the write, and a refusal changes zero stored files (NFR-002, the {@link PackWriteStore}'s stage-and-swap
 * enforces it).
 *
 * <p>Unlike a directive, a toolguide originates no relationships ({@link Toolguide#relationships()} is empty), so the
 * registration body carries none: there is no body-relationship door and no dual-write ownership rule to enforce here.
 * The commit leaves the pack's standalone relationship shard untouched, so a toolguide write never erases a
 * cross-authored edge another door registered.
 *
 * <p>The guide arrives as a {@link GuideSource}: its Markdown content, or a URI (FR-010). A URI is fetched through the
 * {@link GuideFetcher} port <em>before</em> the pack's write lock is taken, never while it is held (NFR-005), on every
 * registration that names it (a PUT with the same URI fetches again). Fetched text and inline content are read by the
 * one {@link GuideFormat}, so both obey the same rules (FR-011); a refused fetch or an invalid guide is a client error
 * ({@link RefusalCode#VALIDATION_FAILED}) and nothing is stored.
 *
 * <p>The refusals that need no guide come first, before anything is fetched: an unknown pack, a stale
 * {@code If-Match}, a POST that names an existing id and a write the policy refuses never cost an outbound fetch.
 * That check takes the pack's write lock only for itself and lets it go before the fetch; the same checks run again
 * under the lock of the commit, where they are authoritative, since the pack may change during the fetch.
 */
@Service
public final class ToolguideRegistrationService {

    private final PackRepository packs;
    private final PackWriteStore writeStore;
    private final WritePolicyGate policy;
    private final GuideFetcher fetcher;
    private final GuideFormat format;

    /**
     * A service over the injected ports.
     *
     * @param packs the port that loads the pack aggregate to mutate and revalidate
     * @param writeStore the write-path seam that commits the aggregate atomically and holds the relationship shard
     * @param policy the mutability gate consulted before a write (permissive by default; WP06 supplies ADR-0012)
     * @param fetcher the port that fetches a guide a registration names by URI
     * @param format the port that reads a guide's Markdown, inline or fetched
     */
    @Autowired
    public ToolguideRegistrationService(
            PackRepository packs,
            PackWriteStore writeStore,
            WritePolicyGate policy,
            GuideFetcher fetcher,
            GuideFormat format) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.writeStore = Objects.requireNonNull(writeStore, "writeStore");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.format = Objects.requireNonNull(format, "format");
    }

    /** Where a registration's guide comes from: exactly one of its content or a URI the service fetches (FR-010). */
    public sealed interface GuideSource {

        /**
         * The guide's Markdown, sent in the registration.
         *
         * @param markdown the Markdown; an H1, when present, equals the toolguide's title
         */
        record Content(String markdown) implements GuideSource {
            /**
             * Checks the field.
             *
             * @param markdown the Markdown
             */
            public Content {
                Objects.requireNonNull(markdown, "markdown");
            }
        }

        /**
         * A URI the guide is fetched from, once per registration.
         *
         * @param uri the guide's location
         */
        record Location(URI uri) implements GuideSource {
            /**
             * Checks the field.
             *
             * @param uri the guide's location
             */
            public Location {
                Objects.requireNonNull(uri, "uri");
            }
        }
    }

    /**
     * The outcome of a registration: the new revision (the {@code ETag}), whether it created rather than replaced, and
     * the registered toolguide's URN (the {@code Location} target of a create).
     *
     * @param revision the revision of the committed state, for the {@code ETag}
     * @param created {@code true} when the write created a net-new toolguide, {@code false} when it replaced one
     * @param target the registered toolguide's URN
     * @param toolguide the registered toolguide, with the guide that was stored
     */
    public record Registered(PackRevision revision, boolean created, Urn target, Toolguide toolguide) {}

    /**
     * Registers a toolguide into the pack (POST create or PUT create-or-replace). The refusals that need no guide are
     * checked first, without the lock. The guide is then resolved, outside the pack's write lock: a
     * {@link GuideSource.Location} is fetched, and the content is read against {@code title}. Only then is the
     * toolguide assembled and the lock taken, under which every check runs again.
     *
     * @param pack the pack to register into
     * @param id the toolguide's id, which the assembled toolguide must carry
     * @param title the toolguide's title, which a guide's H1, when present, must equal
     * @param guide where the guide comes from
     * @param toolguide assembles the toolguide around the resolved guide; it refuses a breach of a toolguide invariant
     *     with {@link RefusalCode#VALIDATION_FAILED}
     * @param replace {@code true} for PUT (idempotent create-or-replace), {@code false} for POST (create; a duplicate
     *     id is a conflict)
     * @param ifMatch the {@code If-Match} precondition, or {@code null} when none was supplied (optional for an
     *     artifact write)
     * @return the registration outcome
     * @throws CharterRefusal {@link RefusalCode#PACK_NOT_FOUND} (404) when the pack is unknown,
     *     {@link RefusalCode#REGISTRATION_CONFLICT} (409) when a POST names an existing id or the guide's file is
     *     claimed by another artifact, {@link RefusalCode#VALIDATION_FAILED} (422) when the guide's fetch is refused,
     *     the guide is not valid, or the mutated aggregate fails revalidation, or the policy's
     *     {@link RefusalCode#GOVERNANCE_POLICY_VIOLATION} (409)
     * @throws PreconditionException {@link PreconditionException.Kind#STALE} (412) when {@code ifMatch} is stale
     */
    public Registered registerToolguide(
            PackIdentifier pack,
            ToolguideIdentifier id,
            String title,
            GuideSource guide,
            Function<Guide, Toolguide> toolguide,
            boolean replace,
            @Nullable String ifMatch) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(guide, "guide");
        Objects.requireNonNull(toolguide, "toolguide");
        // The cheap refusals first (S4): a fetch is an outbound call, never spent on a write that is refused anyway.
        // They run under the lock, which answers an unknown pack as not found, but the lock is let go before the fetch.
        writeStore.exclusively(pack, () -> admitted(pack, id, replace, ifMatch));
        // Resolved before the lock (NFR-005): a slow or hostile guide host never holds a pack's writers up.
        Toolguide assembled = Objects.requireNonNull(toolguide.apply(read(title, markdownOf(guide))), "toolguide");
        if (!assembled.id().equals(id)) {
            throw new IllegalArgumentException(
                    "the assembled toolguide " + assembled.id().value() + " is not " + id.value());
        }
        return commit(pack, assembled, replace, ifMatch);
    }

    private Registered commit(PackIdentifier pack, Toolguide toolguide, boolean replace, @Nullable String ifMatch) {
        // Held from load to swap, so the precondition, the merge over the current shard and the commit see one
        // state: a concurrent writer cannot slip between them (a lost update).
        return writeStore.exclusively(pack, () -> {
            Pack current = admitted(pack, toolguide.id(), replace, ifMatch);
            boolean exists = exists(current, toolguide.id());
            Pack mutated = revalidated(current, toolguide);
            PackRevision revision =
                    writeStore.commit(pack, ifMatch, PackWriteStore.Change.artifact(mutated, toolguide));
            return new Registered(revision, !exists, toolguide.urn(), toolguide);
        });
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /**
     * The checks that need no guide: the pack exists, the {@code If-Match} is current, a POST names a new id, and the
     * policy allows the write. Run once before the fetch and again under the lock.
     *
     * @return the pack as loaded
     */
    private Pack admitted(PackIdentifier pack, ToolguideIdentifier id, boolean replace, @Nullable String ifMatch) {
        Pack current = packs.load(pack);
        precondition(pack, ifMatch);
        boolean exists = exists(current, id);
        if (!replace && exists) {
            throw new CharterRefusal(
                    RefusalCode.REGISTRATION_CONFLICT,
                    "Pack " + pack.value() + " already has a toolguide " + id.value() + ".");
        }
        policy.authorize(
                current,
                exists ? WritePolicyGate.Operation.REPLACE : WritePolicyGate.Operation.REGISTER,
                new Urn(NodeKind.of(ArtifactKind.TOOLGUIDE), id.value()));
        return current;
    }

    private static boolean exists(Pack pack, ToolguideIdentifier id) {
        return pack.toolguides().stream().anyMatch(t -> t.id().equals(id));
    }

    /** The guide's Markdown: the content as sent, or the text the URI answers (fetched here, never under the lock). */
    private String markdownOf(GuideSource guide) {
        return switch (guide) {
            case GuideSource.Content content -> content.markdown();
            case GuideSource.Location location -> fetched(location.uri());
        };
    }

    /**
     * Fetches the guide. A refusal is the client's to fix (a host to allowlist, a URI to correct, or the content to
     * send instead), so it is a validation refusal; its message names the reason and the host, never the path or query.
     */
    private String fetched(URI uri) {
        try {
            return fetcher.fetch(uri);
        } catch (GuideFetchRefusal refused) {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED,
                    sentence("the guide could not be fetched: " + refused.getMessage()),
                    refused);
        }
    }

    private Guide read(String title, String markdown) {
        try {
            return format.read(title, markdown);
        } catch (IllegalArgumentException invalid) {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED, sentence(String.valueOf(invalid.getMessage())), invalid);
        }
    }

    /** The detail as a sentence: capitalized, ending in a full stop. */
    private static String sentence(String detail) {
        String capitalized = detail.isEmpty() ? detail : Character.toUpperCase(detail.charAt(0)) + detail.substring(1);
        return capitalized.endsWith(".") ? capitalized : capitalized + ".";
    }

    private void precondition(PackIdentifier pack, @Nullable String ifMatch) {
        if (ifMatch == null) {
            return;
        }
        if (!PackRevision.matches(ifMatch, writeStore.currentRevision(pack))) {
            throw new PreconditionException(
                    PreconditionException.Kind.STALE, "The If-Match precondition is stale; read the pack again.");
        }
    }

    /**
     * Rebuilds the whole aggregate with {@code toolguide} added (or replacing the one of the same id) and revalidates
     * it: {@link Pack#with} re-runs the aggregate invariants (belongs-to-pack, no-duplicate-id = FR-008),
     * so a breach is a validation refusal (422) rather than a 500, and the write never reaches the commit.
     */
    private static Pack revalidated(Pack current, Toolguide toolguide) {
        try {
            return current.with(toolguide);
        } catch (ConstraintBreachException breach) {
            throw new CharterRefusal(RefusalCode.VALIDATION_FAILED, String.valueOf(breach.getMessage()), breach);
        }
    }
}
