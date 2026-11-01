package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
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

    /** The kinds this service serves, in the list order. Other kernel kinds are not served yet. */
    public static final List<ArtifactKind> SERVED = List.of(
            ArtifactKind.TACTIC,
            ArtifactKind.DIRECTIVE,
            ArtifactKind.STYLEGUIDE,
            ArtifactKind.PARADIGM,
            ArtifactKind.TOOLGUIDE);

    private static final String STALE_LIST = "The inputs changed while the artifacts were read; read them again.";
    private static final String STALE_ONE = "The inputs changed while the artifact was read; read it again.";
    private static final Map<ArtifactKind, RefusalCode> ABSENT = Map.of(
            ArtifactKind.TACTIC, RefusalCode.TACTIC_NOT_FOUND,
            ArtifactKind.DIRECTIVE, RefusalCode.DIRECTIVE_NOT_FOUND,
            ArtifactKind.STYLEGUIDE, RefusalCode.STYLEGUIDE_NOT_FOUND,
            ArtifactKind.PARADIGM, RefusalCode.PARADIGM_NOT_FOUND,
            ArtifactKind.TOOLGUIDE, RefusalCode.TOOLGUIDE_NOT_FOUND);

    private final PackRepository packs;
    private final PackCatalogRepository catalog;
    private final PackDescriptorRepository descriptors;
    private final ConsistentRead read;
    private final TacticReadService tactics;
    private final DirectiveReadService directives;
    private final StyleguideReadService styleguides;
    private final ParadigmReadService paradigms;
    private final ToolguideReadService toolguides;

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
     * @param catalog the port that lists the configured packs, which tells an unreadable pack from an unknown one
     * @param descriptors the port that reads a pack's descriptor, judged before an item is read
     * @param read the verifier that digests the inputs before and after each read
     * @param tactics the tactic reads an item of that kind delegates to
     * @param directives the directive reads an item of that kind delegates to
     * @param styleguides the styleguide reads an item of that kind delegates to
     * @param paradigms the paradigm reads an item of that kind delegates to
     * @param toolguides the toolguide reads an item of that kind delegates to
     */
    @Autowired
    public ArtifactReadService(
            PackRepository packs,
            PackCatalogRepository catalog,
            PackDescriptorRepository descriptors,
            ConsistentRead read,
            TacticReadService tactics,
            DirectiveReadService directives,
            StyleguideReadService styleguides,
            ParadigmReadService paradigms,
            ToolguideReadService toolguides) {
        this.packs = Objects.requireNonNull(packs, "packs");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.read = Objects.requireNonNull(read, "read");
        this.tactics = Objects.requireNonNull(tactics, "tactics");
        this.directives = Objects.requireNonNull(directives, "directives");
        this.styleguides = Objects.requireNonNull(styleguides, "styleguides");
        this.paradigms = Objects.requireNonNull(paradigms, "paradigms");
        this.toolguides = Objects.requireNonNull(toolguides, "toolguides");
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
        List<ArtifactKind> kinds = filter.map(List::of).orElse(SERVED);
        if (!SERVED.containsAll(kinds)) {
            throw new IllegalArgumentException("not a served kind: " + kinds);
        }
        return read.read(STALE_LIST, () -> page(load(pack), kinds, after, size));
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
            default -> throw new IllegalStateException("a served kind without a read: " + kind);
        };
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
    private ConsistentRead.Verified<Item> refusedAfterThePack(PackIdentifier pack, CharterRefusal refusal) {
        return read.read(STALE_ONE, () -> {
            load(pack);
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
     * The pack aggregate. A refusal to read it is {@link RefusalCode#PACK_NOT_FOUND} when no configured pack has the
     * identifier, and stays {@link RefusalCode#PACK_UNREADABLE} for a configured pack (one without a readable
     * {@code pack.yaml} among them).
     */
    private Pack load(PackIdentifier pack) {
        try {
            return packs.load(pack);
        } catch (CharterRefusal refused) {
            throw judged(pack, refused);
        }
    }

    /**
     * Refuses a pack the item cannot read as a canonical pack, before the kind's read, which reads one file: its
     * descriptor must be there and readable. It runs inside the item's outer verified read, between the same two
     * digests as the kind's read, so a {@code pack.yaml} that changes after it is looked up refuses the item as stale.
     */
    private void judgeDescriptor(PackIdentifier pack) {
        Optional<PackDescriptor> descriptor;
        try {
            descriptor = descriptors.find(pack);
        } catch (CharterRefusal refused) {
            throw judged(pack, refused);
        }
        if (descriptor.isEmpty()) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + pack.value() + " has no pack.yaml");
        }
    }

    /** An unreadable pack is not found when no configured pack has the identifier; every other refusal stays. */
    private CharterRefusal judged(PackIdentifier pack, CharterRefusal refused) {
        if (refused.code() != RefusalCode.PACK_UNREADABLE || isConfigured(pack)) {
            return refused;
        }
        return new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.", refused);
    }

    private boolean isConfigured(PackIdentifier pack) {
        return catalog.configured().stream()
                .anyMatch(configured -> configured.id().equals(pack));
    }
}
