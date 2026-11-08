package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The activation document (the contract's {@code ActivationConfig}, ADR-0020 D4): the state a client authors for one
 * active charter, held in its canonical form (FR-005). Immutable; equal by content.
 *
 * <ul>
 *   <li>The document is {@code ⟨packs, selected, deactivated, languages⟩} at {@code schemaVersion} 2 (one kind model,
 *       IC-20): its kinds are the {@linkplain ArtifactKind#activatable() activatable} ones, {@code mission_type}
 *       included, which is selected and denied like any other kind. There is no kind gate and no separate mission type
 *       set.
 *   <li>An allow list absent and an allow list {@code []} both mean none of the kind (rule W2, ADR-0053 decision 10),
 *       and they are one document: an emptied or empty allow list is left out of the canonical form, so a client that
 *       sends {@code []} for a kind it read as absent keeps the revision (FR-042, decision
 *       {@code DM-01M6RN62PPCMSH6M54D2HXYZEQ}). {@code languages} keeps absent (no filter) and {@code []} (no language)
 *       apart.
 *   <li>Allow lists and deny lists are sorted by code point and deduplicated; {@code packs} keeps its written (merge)
 *       order, each pack once. Absent {@code packs} means every configured pack.
 *   <li>A deny list is never empty: the builder refuses an empty one with a violation, and {@link #withoutDeny} drops
 *       a list it empties. An empty {@code selected} or {@code deactivated} object is left out of the canonical form.
 * </ul>
 *
 * <p>The document is built by {@link Builder}, the one place shape violations are made, so the inbound adapter and
 * {@link DocumentRules} agree on them. Whether the ids name what the closure holds is {@link DocumentRules}' question.
 */
public final class ActivationDocument {

    /** The document shape version this service writes; a stored version 1 document is upgraded on read. */
    public static final int SCHEMA_VERSION = 2;

    /** Lists sort by Unicode code point, as the contract's canonical form says. */
    public static final Comparator<String> CODE_POINT_ORDER = ActivationDocument::compareCodePoints;

    private final int schemaVersion;
    private final Optional<List<PackRef>> packs;
    private final Optional<SortedSet<String>> languages;
    private final Map<ArtifactKind, SortedSet<String>> selected;
    private final Map<ArtifactKind, SortedSet<String>> deactivated;

    private ActivationDocument(Builder builder) {
        this.schemaVersion = builder.schemaVersion;
        this.packs = builder.packs.map(List::copyOf);
        this.languages = builder.languages.map(ActivationDocument::frozen);
        this.selected = frozenLists(withoutEmpty(builder.selected));
        this.deactivated = frozenLists(builder.deactivated);
    }

    /**
     * A builder of the minimal document, {@code {"schemaVersion":2}}.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The document shape version.
     *
     * @return {@link #SCHEMA_VERSION}
     */
    public int schemaVersion() {
        return schemaVersion;
    }

    /**
     * The packs the activation builds on, in merge order.
     *
     * @return the packs, never empty when present; empty for every configured pack, which the application resolves
     */
    public Optional<List<PackRef>> packs() {
        return packs;
    }

    /**
     * The project's languages.
     *
     * @return the tokens; empty when absent, which means no language filter
     */
    public Optional<SortedSet<String>> languages() {
        return languages;
    }

    /**
     * The allow lists: a kind present holds its ids, never none; a kind missing selects none of the kind.
     *
     * @return the allow lists by kind, unmodifiable
     */
    public Map<ArtifactKind, SortedSet<String>> selected() {
        return selected;
    }

    /**
     * One kind's allow list.
     *
     * @param kind the kind
     * @return the ids, never none; empty when the document selects none of the kind
     */
    public Optional<SortedSet<String>> selected(ArtifactKind kind) {
        return Optional.ofNullable(selected.get(kind));
    }

    /**
     * The deny lists, never empty.
     *
     * @return the deny lists by kind, unmodifiable
     */
    public Map<ArtifactKind, SortedSet<String>> deactivated() {
        return deactivated;
    }

    /**
     * One kind's deny list.
     *
     * @param kind the kind
     * @return the ids; empty when the document denies nothing of the kind
     */
    public SortedSet<String> deactivated(ArtifactKind kind) {
        return deactivated.getOrDefault(kind, Collections.emptySortedSet());
    }

    /**
     * The document with one more deny entry.
     *
     * @param kind an activatable kind
     * @param id the artifact's declared id
     * @return the new document
     */
    public ActivationDocument withDeny(ArtifactKind kind, String id) {
        Builder builder = toBuilder();
        builder.deactivated
                .computeIfAbsent(kind, k -> new TreeSet<>(CODE_POINT_ORDER))
                .add(id);
        return new ActivationDocument(builder);
    }

    /**
     * The document without one deny entry; a deny list it empties is dropped (reactivate).
     *
     * @param kind the kind
     * @param id the artifact's declared id
     * @return the new document
     */
    public ActivationDocument withoutDeny(ArtifactKind kind, String id) {
        Builder builder = toBuilder();
        Optional.ofNullable(builder.deactivated.get(kind)).ifPresent(ids -> ids.remove(id));
        builder.deactivated.values().removeIf(Set::isEmpty);
        return new ActivationDocument(builder);
    }

    /**
     * The document with one more allow entry; an absent allow list becomes the list of that one id.
     *
     * @param kind an activatable kind
     * @param id the artifact's declared id
     * @return the new document
     */
    public ActivationDocument withSelected(ArtifactKind kind, String id) {
        Builder builder = toBuilder();
        builder.selected
                .computeIfAbsent(kind, k -> new TreeSet<>(CODE_POINT_ORDER))
                .add(id);
        return new ActivationDocument(builder);
    }

    /**
     * The document without one allow entry (unselect): the id leaves its kind's allow list, and a list it empties is
     * left out, as an absent one is (none of the kind either way).
     *
     * @param kind an activatable kind
     * @param id the artifact's declared id
     * @return the new document
     */
    public ActivationDocument withoutSelected(ArtifactKind kind, String id) {
        Builder builder = toBuilder();
        Optional.ofNullable(builder.selected.get(kind)).ifPresent(ids -> ids.remove(id));
        return new ActivationDocument(builder);
    }

    /**
     * The document with its allow and deny lists replaced, each id as given; used to store every spelling of an id as
     * the declared one ({@link DocumentRules#normalize}).
     *
     * @param newSelected the allow lists
     * @param newDeactivated the deny lists
     * @return the new document
     */
    public ActivationDocument withLists(
            Map<ArtifactKind, ? extends Collection<String>> newSelected,
            Map<ArtifactKind, ? extends Collection<String>> newDeactivated) {
        Builder builder = toBuilder();
        builder.selected.clear();
        builder.deactivated.clear();
        newSelected.forEach((kind, ids) -> builder.selected.put(kind, frozenToTree(ids)));
        newDeactivated.forEach((kind, ids) -> builder.deactivated.put(kind, frozenToTree(ids)));
        return new ActivationDocument(builder);
    }

    private static SortedSet<String> frozenToTree(Collection<String> ids) {
        TreeSet<String> sorted = new TreeSet<>(CODE_POINT_ORDER);
        sorted.addAll(ids);
        return sorted;
    }

    /**
     * The canonical form as a value tree for {@link Jcs}: members in the schema's order, absent members left out, an
     * empty allow list and an empty {@code selected} or {@code deactivated} object left out, and {@code []}
     * {@code languages} kept.
     *
     * @return the tree, unmodifiable
     */
    public Map<String, Object> toCanonicalTree() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("schemaVersion", schemaVersion);
        packs.ifPresent(
                p -> tree.put("packs", p.stream().map(PackRef::toCanonicalTree).toList()));
        putKindLists(tree, "selected", selected);
        putKindLists(tree, "deactivated", deactivated);
        languages.ifPresent(l -> tree.put("languages", List.copyOf(l)));
        return Collections.unmodifiableMap(tree);
    }

    /**
     * The revision: {@code sha256:} over the RFC 8785 serialization of {@link #toCanonicalTree()}.
     *
     * @return the revision
     */
    public Revision revision() {
        return Revision.of(Jcs.canonical(toCanonicalTree()));
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof ActivationDocument that && toCanonicalTree().equals(that.toCanonicalTree());
    }

    @Override
    public int hashCode() {
        return toCanonicalTree().hashCode();
    }

    /** The canonical JSON text. */
    @Override
    public String toString() {
        return new String(Jcs.canonical(toCanonicalTree()), StandardCharsets.UTF_8);
    }

    private Builder toBuilder() {
        Builder builder = new Builder();
        builder.packs = packs;
        builder.languages = languages.map(List::copyOf);
        selected.forEach((kind, ids) -> builder.selected.put(kind, new TreeSet<>(ids)));
        deactivated.forEach((kind, ids) -> builder.deactivated.put(kind, new TreeSet<>(ids)));
        return builder;
    }

    private static void putKindLists(
            Map<String, Object> tree, String member, Map<ArtifactKind, SortedSet<String>> lists) {
        if (lists.isEmpty()) {
            return;
        }
        Map<String, Object> byToken = new LinkedHashMap<>();
        lists.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ArtifactKind::token, CODE_POINT_ORDER)))
                .forEach(list -> byToken.put(list.getKey().token(), List.copyOf(list.getValue())));
        tree.put(member, byToken);
    }

    private static SortedSet<String> frozen(Collection<String> ids) {
        TreeSet<String> sorted = new TreeSet<>(CODE_POINT_ORDER);
        sorted.addAll(ids);
        return Collections.unmodifiableSortedSet(sorted);
    }

    private static Map<ArtifactKind, SortedSet<String>> withoutEmpty(Map<ArtifactKind, SortedSet<String>> lists) {
        Map<ArtifactKind, SortedSet<String>> kept = new EnumMap<>(ArtifactKind.class);
        lists.forEach((kind, ids) -> {
            if (!ids.isEmpty()) {
                kept.put(kind, ids);
            }
        });
        return kept;
    }

    private static Map<ArtifactKind, SortedSet<String>> frozenLists(Map<ArtifactKind, SortedSet<String>> lists) {
        Map<ArtifactKind, SortedSet<String>> copy = new EnumMap<>(ArtifactKind.class);
        lists.forEach((kind, ids) -> copy.put(kind, frozen(ids)));
        return Collections.unmodifiableMap(copy);
    }

    private static int compareCodePoints(String left, String right) {
        int i = 0;
        int j = 0;
        while (i < left.length() && j < right.length()) {
            int a = left.codePointAt(i);
            int b = right.codePointAt(j);
            if (a != b) {
                return Integer.compare(a, b);
            }
            i += Character.charCount(a);
            j += Character.charCount(b);
        }
        return Integer.compare(left.length() - i, right.length() - j);
    }

    /**
     * A document built, the shape violations met on the way (ADR-0020 D10), and the lists as the request body wrote them,
     * so that every violation points into the body (RFC 6901). The document is always built: a part with a shape
     * violation is left out of it, and out of the written form.
     *
     * @param document the document
     * @param violations the shape violations, in the order met; empty when the shape is valid
     * @param written the lists as written, which {@link DocumentRules} indexes
     */
    public record Built(ActivationDocument document, List<RefusalViolation> violations, Written written) {

        /**
         * Copies the violations.
         *
         * @param document the document
         * @param violations the shape violations
         * @param written the lists as written
         */
        public Built {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(written, "written");
            violations = List.copyOf(violations);
        }

        /**
         * A document with no written form of its own, such as one a per-artifact write derives: its canonical lists
         * stand in for the written ones, so a pointer indexes the canonical document.
         *
         * @param document the document
         * @param violations the shape violations
         */
        public Built(ActivationDocument document, List<RefusalViolation> violations) {
            this(document, violations, Written.canonical(document));
        }
    }

    /**
     * The lists of a request body in the order written, duplicates included, for the members whose ids
     * {@link DocumentRules} checks against the closure.
     *
     * @param packs the packs as written, a repeated pack included; empty when absent
     * @param selected the allow lists as written, per kind the document keeps
     * @param deactivated the deny lists as written, per kind the document keeps
     */
    public record Written(
            List<PackRef> packs,
            Map<ArtifactKind, List<String>> selected,
            Map<ArtifactKind, List<String>> deactivated) {

        /**
         * Copies every list.
         *
         * @param packs the packs
         * @param selected the allow lists
         * @param deactivated the deny lists
         */
        public Written {
            packs = List.copyOf(packs);
            selected = copyOfLists(selected);
            deactivated = copyOfLists(deactivated);
        }

        /**
         * A document's canonical lists, standing in for a written form it does not have.
         *
         * @param document the document
         * @return its canonical lists
         */
        public static Written canonical(ActivationDocument document) {
            return new Written(
                    document.packs().orElse(List.of()),
                    copyOfLists(document.selected()),
                    copyOfLists(document.deactivated()));
        }

        private static Map<ArtifactKind, List<String>> copyOfLists(
                Map<ArtifactKind, ? extends Collection<String>> lists) {
            Map<ArtifactKind, List<String>> copy = new EnumMap<>(ArtifactKind.class);
            lists.forEach((kind, ids) -> copy.put(kind, List.copyOf(ids)));
            return Collections.unmodifiableMap(copy);
        }
    }

    /**
     * Builds a document, one method per member, collecting shape violations rather than throwing on them:
     * {@code unknown_kind} for a kind that is not {@linkplain ArtifactKind#activatable() activatable},
     * {@code duplicate_pack} for a pack named twice and {@code empty_deny_list} for a deny list written empty. Shapes
     * the contract's schema already refuses ({@code schemaVersion} other than 2, an empty {@code packs} list) are
     * programming errors here and throw.
     * Every pointer indexes the body as written; {@link Built#written()} keeps the written lists for
     * {@link DocumentRules}.
     */
    public static final class Builder {

        private int schemaVersion = SCHEMA_VERSION;
        private Optional<List<PackRef>> packs = Optional.empty();
        private Optional<List<String>> languages = Optional.empty();
        private final Map<ArtifactKind, SortedSet<String>> selected = new EnumMap<>(ArtifactKind.class);
        private final Map<ArtifactKind, SortedSet<String>> deactivated = new EnumMap<>(ArtifactKind.class);
        private final List<RefusalViolation> violations = new ArrayList<>();
        private List<PackRef> writtenPacks = List.of();
        private final Map<ArtifactKind, List<String>> writtenSelected = new EnumMap<>(ArtifactKind.class);
        private final Map<ArtifactKind, List<String>> writtenDeactivated = new EnumMap<>(ArtifactKind.class);

        private Builder() {}

        /**
         * The document shape version.
         *
         * @param version the version
         * @return this builder
         * @throws IllegalArgumentException when the version is not {@link #SCHEMA_VERSION}
         */
        public Builder schemaVersion(int version) {
            if (version != SCHEMA_VERSION) {
                throw new IllegalArgumentException(
                        "The activation document shape version is " + SCHEMA_VERSION + ", not " + version + ".");
            }
            this.schemaVersion = version;
            return this;
        }

        /**
         * The packs, in merge order; a pack named again is a {@code duplicate_pack} violation and left out.
         *
         * @param written the packs as written, not empty
         * @return this builder
         * @throws IllegalArgumentException when the list is empty
         */
        public Builder packs(List<PackRef> written) {
            if (written.isEmpty()) {
                throw new IllegalArgumentException("An activation's packs list is absent or not empty.");
            }
            List<PackRef> kept = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < written.size(); i++) {
                keepOnce(written.get(i), i, seen, kept);
            }
            this.packs = Optional.of(kept);
            this.writtenPacks = List.copyOf(written);
            return this;
        }

        private void keepOnce(PackRef pack, int index, Set<String> seen, List<PackRef> kept) {
            if (seen.add(pack.packId())) {
                kept.add(pack);
                return;
            }
            violations.add(new RefusalViolation(
                    "/packs/" + index + "/packId",
                    "duplicate_pack",
                    "The pack '" + pack.packId() + "' is named more than once."));
        }

        /**
         * The project's languages.
         *
         * @param tokens the language tokens as written
         * @return this builder
         */
        public Builder languages(Collection<String> tokens) {
            this.languages = Optional.of(List.copyOf(tokens));
            return this;
        }

        /**
         * One kind's allow list; a kind that is not activatable is an {@code unknown_kind} violation and left out. An
         * empty list is none of the kind, as an absent one is, and is left out of the document (it stays in the
         * written form, which only indexes ids).
         *
         * @param kind the kind
         * @param ids the ids as written; empty for {@code []}
         * @return this builder
         */
        public Builder selected(ArtifactKind kind, Collection<String> ids) {
            if (knownKind(kind, "/selected/" + kind.token())) {
                selected.put(kind, frozen(ids));
                writtenSelected.put(kind, List.copyOf(ids));
            }
            return this;
        }

        /**
         * One kind's deny list; an empty one is an {@code empty_deny_list} violation, and a kind that is not
         * activatable an {@code unknown_kind} one; either is left out.
         *
         * @param kind the kind
         * @param ids the ids as written
         * @return this builder
         */
        public Builder deactivated(ArtifactKind kind, Collection<String> ids) {
            String pointer = "/deactivated/" + kind.token();
            if (knownKind(kind, pointer) && nonEmptyDenyList(ids, pointer)) {
                deactivated.put(kind, frozen(ids));
                writtenDeactivated.put(kind, List.copyOf(ids));
            }
            return this;
        }

        private boolean knownKind(ArtifactKind kind, String pointer) {
            if (kind.activatable()) {
                return true;
            }
            violations.add(new RefusalViolation(
                    pointer, "unknown_kind", "An activation names no '" + kind.token() + "' artifacts."));
            return false;
        }

        private boolean nonEmptyDenyList(Collection<String> ids, String pointer) {
            if (!ids.isEmpty()) {
                return true;
            }
            violations.add(new RefusalViolation(
                    pointer, "empty_deny_list", "A deny list is never empty: leave the kind out instead."));
            return false;
        }

        /**
         * The document, and the shape violations met.
         *
         * @return the built document
         */
        public Built build() {
            Written written = new Written(writtenPacks, writtenSelected, writtenDeactivated);
            return new Built(new ActivationDocument(this), violations, written);
        }
    }
}
