package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackEdge;
import ai.speckitty.charter.packs.domain.PackEdgeRepository;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The generic artifact reads: one paged list of a pack's artifacts of the served kinds, and one artifact by kind and
 * id. They are the only artifact reads the API serves; the per-kind routes are gone. The item delegates to the
 * per-kind read service of its kind ({@link TacticReadService} and the rest), which the generic reads compose (#169
 * folds the kinds into a registry later).
 *
 * <p>The list is one read verified by {@link ConsistentRead}: the pack aggregate is loaded between two digests, so a
 * page and its fingerprint name the same inputs. The pack is judged before the kind: an identifier that names no
 * configured pack is refused {@link RefusalCode#PACK_NOT_FOUND}, then a kind outside {@link #SERVED} is refused
 * {@link RefusalCode#KIND_NOT_FOUND}, inside the same kind of verified read.
 *
 * <p>A configured pack without a readable {@code pack.yaml} is not a readable canonical pack (operator ruling of
 * 2026-10-31): the list and the item of every kind refuse it {@link RefusalCode#PACK_UNREADABLE}, never
 * {@code PACK_NOT_FOUND}, since it is configured. The item judges the descriptor before it delegates to the kind's read,
 * which reads one file and would otherwise answer the artifact of a pack that cannot be read as a whole. The check and
 * the kind's read run inside one outer verified read, and the answer carries the kind's fingerprint only when it equals
 * the outer digests: all four digests agree, so the 200 or the refusal is the one the fingerprinted inputs give. A
 * change during it is {@link RefusalCode#STALE_INPUT}, with the kind's own detail.
 */
@Service
public final class ArtifactReadService {

    /**
     * The kinds this service serves, in the list order; {@code agent_profile} is last. Other kernel kinds are not served
     * yet. An agent profile is listed, referenced and removed here, but its body is rendered by the profiles slice's
     * own controller, so the generic item read answers it as an {@link Item.Foreign}.
     */
    public static final List<ArtifactKind> SERVED = List.of(
            ArtifactKind.TACTIC,
            ArtifactKind.DIRECTIVE,
            ArtifactKind.STYLEGUIDE,
            ArtifactKind.PARADIGM,
            ArtifactKind.TOOLGUIDE,
            ArtifactKind.AGENT_PROFILE);

    /**
     * The kinds a client can list and follow from a list: the list, its {@code ?kind=} filter, the positions of a page,
     * a reference's {@code artifact} link and the removal of an artifact. Every gate on the listable kinds reads this
     * accessor and not {@link #SERVED}, so that a kind can later be served without being listed, or the other way round.
     * Today it is {@link #SERVED}.
     *
     * @return the listed kinds, in the list order
     */
    public static List<ArtifactKind> listed() {
        return SERVED;
    }

    private static final String STALE_LIST = "The inputs changed while the artifacts were read; read them again.";
    private static final String STALE_ONE = "The inputs changed while the artifact was read; read it again.";
    private static final Map<ArtifactKind, RefusalCode> ABSENT = Map.of(
            ArtifactKind.TACTIC, RefusalCode.TACTIC_NOT_FOUND,
            ArtifactKind.DIRECTIVE, RefusalCode.DIRECTIVE_NOT_FOUND,
            ArtifactKind.STYLEGUIDE, RefusalCode.STYLEGUIDE_NOT_FOUND,
            ArtifactKind.PARADIGM, RefusalCode.PARADIGM_NOT_FOUND,
            ArtifactKind.TOOLGUIDE, RefusalCode.TOOLGUIDE_NOT_FOUND,
            // Kinds served from now on use artifact_not_found; the five above keep their own codes (contract rule).
            ArtifactKind.AGENT_PROFILE, RefusalCode.ARTIFACT_NOT_FOUND);

    /**
     * The code a request for an artifact of a served kind that the pack does not hold is refused with.
     *
     * @param kind a served kind
     * @return its not-found code, for example {@code directive_not_found}
     * @throws IllegalArgumentException when the kind is not served
     */
    public static RefusalCode absentCode(ArtifactKind kind) {
        RefusalCode code = ABSENT.get(kind);
        if (code == null) {
            throw new IllegalArgumentException("not a served kind: " + kind);
        }
        return code;
    }

    private final PackRepository packs;
    private final PackDescriptorRepository descriptors;
    private final ConsistentRead read;
    private final TacticReadService tactics;
    private final DirectiveReadService directives;
    private final StyleguideReadService styleguides;
    private final ParadigmReadService paradigms;
    private final ToolguideReadService toolguides;
    private final OfferingReadService offerings;
    private final CompositeComponentResolver components;
    private final PackEdgeRepository graphs;
    private final PackRevisions revisions;

    /**
     * One artifact as the list names it.
     *
     * @param position its kind and id, which is also its place in the list
     * @param title the name of a tactic or a paradigm, the title of a directive, a styleguide or a toolguide
     */
    public record Summary(ArtifactPosition position, String title) {

        /**
         * A summary.
         *
         * @param position its kind and id
         * @param title its human-readable name
         */
        public Summary {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(title, "title");
        }
    }

    /**
     * One page of the list.
     *
     * @param items the artifacts of the page, in the list order
     * @param hasMore whether the list holds more artifacts after the last of {@code items}
     */
    public record Page(List<Summary> items, boolean hasMore) {

        /**
         * A page, its items copied.
         *
         * @param items the artifacts of the page
         * @param hasMore whether more follow
         */
        public Page {
            items = List.copyOf(items);
        }
    }

    /** One artifact read by kind, typed by its kind, with the pack revision the writable kinds carry for an ETag. */
    public sealed interface Item {

        /**
         * The artifact read.
         *
         * @return the domain entity
         */
        CharterArtifact artifact();

        /**
         * The pack revision the artifact was read at.
         *
         * @return the revision of a writable kind; empty for a tactic, which has no write
         */
        Optional<PackRevision> revision();

        /**
         * A tactic.
         *
         * @param artifact the tactic
         */
        record OfTactic(Tactic artifact) implements Item {
            @Override
            public Optional<PackRevision> revision() {
                return Optional.empty();
            }
        }

        /**
         * A directive and its revision.
         *
         * @param artifact the directive
         * @param at the revision it was read at
         */
        record OfDirective(Directive artifact, PackRevision at) implements Item {
            @Override
            public Optional<PackRevision> revision() {
                return Optional.of(at);
            }
        }

        /**
         * A styleguide and its revision.
         *
         * @param artifact the styleguide
         * @param at the revision it was read at
         */
        record OfStyleguide(Styleguide artifact, PackRevision at) implements Item {
            @Override
            public Optional<PackRevision> revision() {
                return Optional.of(at);
            }
        }

        /**
         * A paradigm and its revision.
         *
         * @param artifact the paradigm
         * @param at the revision it was read at
         */
        record OfParadigm(Paradigm artifact, PackRevision at) implements Item {
            @Override
            public Optional<PackRevision> revision() {
                return Optional.of(at);
            }
        }

        /**
         * An artifact of a kind another slice's controller renders, and the revision of its pack.
         *
         * @param artifact the artifact
         * @param at the revision it was read at
         */
        record Foreign(CharterArtifact artifact, PackRevision at) implements Item {
            @Override
            public Optional<PackRevision> revision() {
                return Optional.of(at);
            }
        }

        /**
         * A toolguide and its revision.
         *
         * @param artifact the toolguide
         * @param at the revision it was read at
         */
        record OfToolguide(Toolguide artifact, PackRevision at) implements Item {
            @Override
            public Optional<PackRevision> revision() {
                return Optional.of(at);
            }
        }
    }

    /**
     * A service over the pack port and the per-kind reads.
     *
     * @param packs the port that loads a pack aggregate, for the list and the pack check of an unserved kind
     * @param descriptors the port that reads a pack's descriptor, judged before an item is read
     * @param read the verifier that digests the inputs before and after each read
     * @param tactics the tactic reads an item of that kind delegates to
     * @param directives the directive reads an item of that kind delegates to
     * @param styleguides the styleguide reads an item of that kind delegates to
     * @param paradigms the paradigm reads an item of that kind delegates to
     * @param toolguides the toolguide reads an item of that kind delegates to
     * @param offerings the offering read whose compose a reference is resolved against, resolved once per read
     * @param components the resolver that resolves a reference a read is given, against the compose closure
     * @param graphs the port that reads a pack's graph-document edges, the references an artifact writes
     * @param revisions the pack revision an artifact of another slice's kind is read at
     */
    @Autowired
    public ArtifactReadService(
            PackRepository packs,
            PackDescriptorRepository descriptors,
            ConsistentRead read,
            TacticReadService tactics,
            DirectiveReadService directives,
            StyleguideReadService styleguides,
            ParadigmReadService paradigms,
            ToolguideReadService toolguides,
            OfferingReadService offerings,
            CompositeComponentResolver components,
            PackEdgeRepository graphs,
            PackRevisions revisions) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.read = Objects.requireNonNull(read, "read");
        this.tactics = Objects.requireNonNull(tactics, "tactics");
        this.directives = Objects.requireNonNull(directives, "directives");
        this.styleguides = Objects.requireNonNull(styleguides, "styleguides");
        this.paradigms = Objects.requireNonNull(paradigms, "paradigms");
        this.toolguides = Objects.requireNonNull(toolguides, "toolguides");
        this.offerings = Objects.requireNonNull(offerings, "offerings");
        this.components = Objects.requireNonNull(components, "components");
        this.graphs = Objects.requireNonNull(graphs, "graphs");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
    }

    /**
     * The served kind a token names.
     *
     * @param token a kind token, for example {@code directive}
     * @return the kind; empty when the token names no kind, or one this service does not serve
     */
    public static Optional<ArtifactKind> servedKind(String token) {
        return ArtifactKind.fromToken(token).filter(SERVED::contains);
    }

    /**
     * The listed kind a token names.
     *
     * @param token a kind token, for example {@code directive}
     * @return the kind; empty when the token names no kind, or one the list does not hold
     */
    public static Optional<ArtifactKind> listedKind(String token) {
        return ArtifactKind.fromToken(token).filter(kind -> listed().contains(kind));
    }

    /**
     * One page of a pack's artifacts of the served kinds, with the digest of the inputs it was read from.
     *
     * @param pack the pack's identity
     * @param filter the one served kind to list; empty for all of them
     * @param after the position the page starts after; empty for the first page
     * @param size the most artifacts the page holds, at least 1
     * @return the page and the verified fingerprint
     * @throws IllegalArgumentException when {@code size} is below 1 or {@code filter} is not a served kind
     * @throws CharterRefusal with {@link RefusalCode#PACK_UNREADABLE} when any configured pack or pack file cannot be
     *     read, this pack included when it has no readable {@code pack.yaml}, with {@link RefusalCode#PACK_NOT_FOUND}
     *     when no configured pack has this identifier, or with
     *     {@link RefusalCode#STALE_INPUT} when the inputs changed while the page was read
     */
    public ConsistentRead.Verified<Page> list(
            PackIdentifier pack, Optional<ArtifactKind> filter, Optional<ArtifactPosition> after, int size) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(after, "after");
        if (size < 1) {
            throw new IllegalArgumentException("a page holds at least one artifact, not " + size);
        }
        List<ArtifactKind> kinds = filter.map(List::of).orElse(listed());
        if (!listed().containsAll(kinds)) {
            throw new IllegalArgumentException("not a served kind: " + kinds);
        }
        return read.read(STALE_LIST, () -> page(packs.load(pack), kinds, after, size));
    }

    /**
     * One artifact of a pack by kind and id, with the digest of the inputs it was read from.
     *
     * @param pack the pack's identity
     * @param kindToken the kind's token, as the request names it
     * @param id the artifact's id within its kind
     * @return the artifact typed by its kind, and the verified fingerprint
     * @throws CharterRefusal with {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has this identifier,
     *     whatever the kind; with {@link RefusalCode#PACK_UNREADABLE} when the configured pack has no readable
     *     {@code pack.yaml}, whatever the kind; with {@link RefusalCode#KIND_NOT_FOUND} when the token names no served kind; with the
     *     not-found code of the kind when the pack has no such artifact; with {@link RefusalCode#PACK_UNREADABLE} or
     *     {@link RefusalCode#STALE_INPUT} as every verified read refuses
     */
    public ConsistentRead.Verified<Item> item(PackIdentifier pack, String kindToken, String id) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(kindToken, "kindToken");
        Objects.requireNonNull(id, "id");
        Optional<ArtifactKind> kind = servedKind(kindToken);
        if (kind.isEmpty()) {
            return refusedAfterThePack(
                    pack,
                    new CharterRefusal(
                            RefusalCode.KIND_NOT_FOUND, "The service serves no artifact kind " + kindToken + "."));
        }
        ArtifactKind served = kind.get();
        String stale = "The inputs changed while the " + served.token() + " was read; read it again.";
        ConsistentRead.Verified<ConsistentRead.Verified<Item>> judged = read.read(stale, () -> {
            judgeDescriptor(pack);
            return servedItem(pack, served, id);
        });
        if (!judged.fingerprint().equals(judged.value().fingerprint())) {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, stale);
        }
        return judged.value();
    }

    /**
     * One reference an artifact writes, resolved through the compose.
     *
     * @param written the reference as the artifact writes it, bare or pack-qualified
     * @param relation the relation of its edge, or of the relation the artifact derives
     * @param holder the pack of the compose that holds what it names; empty when no pack holds it
     * @param urn the bare URN it resolves to
     */
    public record Reference(
            PackQualifiedReference written,
            RelationType relation,
            Optional<PackCompositionResolver.PackUnderResolution> holder,
            Urn urn) {}

    /**
     * The references one artifact writes, each resolved through the compose, in the order it writes them and without
     * repeats, with the digest of the inputs they were read from. A composed read: the compose is resolved once,
     * inside the read, so a compose the resolver cannot serve refuses it with the composition code the offering is
     * refused with, and every hop resolves against that one closure. A reference naming no URN of a known kind is not
     * listed.
     *
     * @param pack the referring pack's identity
     * @param kindToken the referring artifact's kind token, as the request names it
     * @param id the referring artifact's id
     * @return the references and the verified fingerprint
     * @throws CharterRefusal as {@link #item(PackIdentifier, String, String)} refuses for the pack, the kind and the
     *     artifact; with a composition code as the offering refuses a compose the resolver cannot serve
     */
    public ConsistentRead.Verified<List<Reference>> references(PackIdentifier pack, String kindToken, String id) {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(kindToken, "kindToken");
        Objects.requireNonNull(id, "id");
        Optional<ArtifactKind> kind = listedKind(kindToken);
        if (kind.isEmpty()) {
            return refusedAfterThePack(
                    pack,
                    new CharterRefusal(
                            RefusalCode.KIND_NOT_FOUND, "The service serves no artifact kind " + kindToken + "."));
        }
        String stale = "The inputs changed while the references were read; read them again.";
        ConsistentRead.Verified<ConsistentRead.Verified<List<Reference>>> judged = read.read(stale, () -> {
            String packName = judgeDescriptor(pack).name();
            // The compose is resolved once, inside this read, before the artifact: a broken compose refuses the read.
            CompositeComponentResolver.Composed composed = components.over(offerings.composed());
            ConsistentRead.Verified<Item> referrer = servedItem(pack, kind.get(), id);
            return new ConsistentRead.Verified<>(
                    resolved(composed, pack, packName, referrer.value().artifact(), graphs.edges(pack)),
                    referrer.fingerprint());
        });
        if (!judged.fingerprint().equals(judged.value().fingerprint())) {
            throw new CharterRefusal(RefusalCode.STALE_INPUT, stale);
        }
        return new ConsistentRead.Verified<>(judged.value().value(), judged.fingerprint());
    }

    /**
     * The references an artifact writes (ADR-0064): the edges of its pack's graph documents whose source is the
     * artifact, written bare or qualified with the pack's own name, in document order, then its derived relations. Each
     * carries its relation. A target is listed once per relation: the same artifact written bare and qualified with
     * its holder's name is one reference, and the first spelling is kept.
     */
    private static List<Reference> resolved(
            CompositeComponentResolver.Composed composed,
            PackIdentifier pack,
            String packName,
            CharterArtifact referrer,
            List<PackEdge> edges) {
        // The schemas refuse a malformed endpoint when a pack is read, so the grammar's parser cannot refuse one here.
        PackQualifiedReference self =
                new PackQualifiedReference(Optional.empty(), referrer.urn().toString());
        Map<String, Reference> references = new LinkedHashMap<>();
        for (PackEdge edge : edges) {
            if (PackPolicy.joinsItself(packName, self, PackQualifiedReference.parse(edge.source()))) {
                add(references, composed, pack, PackQualifiedReference.parse(edge.target()), edge.relation());
            }
        }
        for (Relationship derived : referrer.derivedRelationships()) {
            add(references, composed, pack, derived.toReference(), derived.type());
        }
        return List.copyOf(references.values());
    }

    private static void add(
            Map<String, Reference> references,
            CompositeComponentResolver.Composed composed,
            PackIdentifier pack,
            PackQualifiedReference written,
            RelationType relation) {
        written.asUrn().ifPresent(urn -> {
            Optional<PackCompositionResolver.PackUnderResolution> holder = composed.holder(written, pack);
            String held = holder.map(h -> h.configured().id().value()).orElse("");
            references.putIfAbsent(
                    held + " " + urn + " " + relation.name(), new Reference(written, relation, holder, urn));
        });
    }

    /** The kind's own verified read of one artifact, typed by its kind. */
    private ConsistentRead.Verified<Item> servedItem(PackIdentifier pack, ArtifactKind kind, String id) {
        return switch (kind) {
            case TACTIC ->
                readAs(
                        pack,
                        ArtifactKind.TACTIC,
                        id,
                        TacticIdentifier::new,
                        tactic -> as(tactics.tactic(pack, tactic), Item.OfTactic::new));
            case DIRECTIVE ->
                readAs(
                        pack,
                        ArtifactKind.DIRECTIVE,
                        id,
                        DirectiveIdentifier::new,
                        directive -> as(
                                directives.directiveWithRevision(pack, directive),
                                revised -> new Item.OfDirective(revised.value(), revised.revision())));
            case STYLEGUIDE ->
                readAs(
                        pack,
                        ArtifactKind.STYLEGUIDE,
                        id,
                        StyleguideIdentifier::new,
                        styleguide -> as(
                                styleguides.styleguideWithRevision(pack, styleguide),
                                revised -> new Item.OfStyleguide(revised.value(), revised.revision())));
            case PARADIGM ->
                readAs(
                        pack,
                        ArtifactKind.PARADIGM,
                        id,
                        ParadigmIdentifier::new,
                        paradigm -> as(
                                paradigms.paradigmWithRevision(pack, paradigm),
                                revised -> new Item.OfParadigm(revised.value(), revised.revision())));
            case TOOLGUIDE ->
                readAs(
                        pack,
                        ArtifactKind.TOOLGUIDE,
                        id,
                        ToolguideIdentifier::new,
                        toolguide -> as(
                                toolguides.toolguideWithRevision(pack, toolguide),
                                revised -> new Item.OfToolguide(revised.value(), revised.revision())));
            case AGENT_PROFILE -> foreignItem(pack, kind, id);
            default -> throw new IllegalStateException("a served kind without a read: " + kind);
        };
    }

    /**
     * The read of an artifact of a kind another slice renders: the stored entity and the revision of its pack, taken in
     * one verified read. The id is not parsed as an identifier of the kind: an id the pack does not hold, malformed or
     * not, is the kind's not-found.
     */
    private ConsistentRead.Verified<Item> foreignItem(PackIdentifier pack, ArtifactKind kind, String id) {
        return read.read("The inputs changed while the " + kind.token() + " was read; read it again.", () -> {
            CharterArtifact artifact = packs.load(pack).artifacts(kind).stream()
                    .filter(candidate -> candidate.localId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new CharterRefusal(
                            absentCode(kind), "Pack " + pack.value() + " has no " + kind.token() + " " + id + "."));
            return new Item.Foreign(artifact, revisions.currentRevision(pack));
        });
    }

    /**
     * Reads one artifact through its kind's read once {@code id} is an identifier of that kind. An id the kind's
     * identifier rules refuse (a lower-case directive id) names no artifact of the kind: it is the kind's not-found,
     * after the pack is judged, never a server error.
     */
    private <I> ConsistentRead.Verified<Item> readAs(
            PackIdentifier pack,
            ArtifactKind kind,
            String id,
            Function<String, I> identifier,
            Function<I, ConsistentRead.Verified<Item>> kindRead) {
        I parsed;
        try {
            parsed = identifier.apply(id);
        } catch (ConstraintBreachException malformed) {
            return refusedAfterThePack(
                    pack,
                    new CharterRefusal(
                            Objects.requireNonNull(ABSENT.get(kind), "the not-found code of a served kind"),
                            "Pack " + pack.value() + " has no " + kind.token() + " " + id + ".",
                            malformed));
        }
        return kindRead.apply(parsed);
    }

    /**
     * A refusal taken inside one verified read that first loads the pack, so an unknown pack is
     * {@link RefusalCode#PACK_NOT_FOUND} whatever else the request names, and changed inputs are stale.
     */
    private <T> ConsistentRead.Verified<T> refusedAfterThePack(PackIdentifier pack, CharterRefusal refusal) {
        return read.read(STALE_ONE, () -> {
            packs.load(pack);
            throw refusal;
        });
    }

    private static <T> ConsistentRead.Verified<Item> as(ConsistentRead.Verified<T> verified, Function<T, Item> typed) {
        return new ConsistentRead.Verified<>(typed.apply(verified.value()), verified.fingerprint());
    }

    private static Page page(Pack pack, List<ArtifactKind> kinds, Optional<ArtifactPosition> after, int size) {
        List<Summary> rest = kinds.stream()
                .flatMap(kind -> pack.artifacts(kind).stream())
                .map(artifact ->
                        new Summary(new ArtifactPosition(artifact.kind(), artifact.localId()), artifact.label()))
                .filter(summary -> after.map(anchor -> ArtifactPosition.ORDER.compare(summary.position(), anchor) > 0)
                        .orElse(true))
                .sorted((left, right) -> ArtifactPosition.ORDER.compare(left.position(), right.position()))
                .toList();
        return new Page(rest.subList(0, Math.min(size, rest.size())), rest.size() > size);
    }

    /**
     * Refuses a pack the item cannot read as a canonical pack, before the kind's read, which reads one file: its
     * descriptor must be there and readable. It runs inside the item's outer verified read, between the same two
     * digests as the kind's read, so a {@code pack.yaml} that changes after it is looked up refuses the item as stale.
     * The storage refuses an identifier no configured pack answers to {@link RefusalCode#PACK_NOT_FOUND} itself, and a
     * configured pack it cannot read {@link RefusalCode#PACK_UNREADABLE}; both pass through.
     */
    private PackDescriptor judgeDescriptor(PackIdentifier pack) {
        return descriptors
                .find(pack)
                .orElseThrow(() ->
                        new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + pack.value() + " has no pack.yaml"));
    }
}
