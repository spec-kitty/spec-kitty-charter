package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
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
 *   <li>Three states: {@code activatedKinds}, {@code missionTypes}, {@code languages} and each {@code selected} kind
 *       are absent, {@code []} or a set, and absent and {@code []} stay distinct in the canonical form (FR-008). For
 *       {@code activatedKinds} both mean that no kind is in force (ADR-0053 decision 10).
 *   <li>Allow lists and deny lists are sorted by code point and deduplicated; {@code packs} keeps its written (merge)
 *       order, each pack once. Absent {@code packs} means every configured pack.
 *   <li>A deny list is never empty: the builder refuses an empty one with a violation, and {@link #withoutDeny} drops
 *       a list it empties. An empty {@code selected} or {@code deactivated} object is left out of the canonical form.
 *   <li>The kinds are {@link #kinds()}: the activatable kinds less {@code mission_type}, which {@code missionTypes}
 *       holds.
 * </ul>
 *
 * <p>The document is built by {@link Builder}, the one place shape violations are made, so the inbound adapter and
 * {@link DocumentRules} agree on them. Whether the ids name what the closure holds is {@link DocumentRules}' question.
 */
public final class ActivationDocument {

    /** The one document shape version this service reads and writes. */
    public static final int SCHEMA_VERSION = 1;

    /** Lists sort by Unicode code point, as the contract's canonical form says. */
    public static final Comparator<String> CODE_POINT_ORDER = ActivationDocument::compareCodePoints;

    private static final Set<ArtifactKind> KINDS =
            Collections.unmodifiableSet(EnumSet.copyOf(Arrays.stream(ArtifactKind.values())
                    .filter(ArtifactKind::activatable)
                    .filter(k -> k != ArtifactKind.MISSION_TYPE)
                    .toList()));

    private final int schemaVersion;
    private final Optional<List<PackRef>> packs;
    private final Optional<SortedSet<ArtifactKind>> activatedKinds;
    private final Optional<SortedSet<String>> missionTypes;
    private final Optional<SortedSet<String>> languages;
    private final Map<ArtifactKind, SortedSet<String>> selected;
    private final Map<ArtifactKind, SortedSet<String>> deactivated;

    private ActivationDocument(Builder builder) {
        this.schemaVersion = builder.schemaVersion;
        this.packs = builder.packs.map(List::copyOf);
        this.activatedKinds = builder.activatedKinds.map(k -> Collections.unmodifiableSortedSet(new TreeSet<>(k)));
        this.missionTypes = builder.missionTypes.map(ActivationDocument::frozen);
        this.languages = builder.languages.map(ActivationDocument::frozen);
        this.selected = frozenLists(builder.selected);
        this.deactivated = frozenLists(builder.deactivated);
    }

    /**
     * The kinds a document selects, deactivates or activates: {@link ArtifactKind#activatable()} less
     * {@code mission_type} (the contract's {@code ActivatableKind}).
     *
     * @return the kinds, unmodifiable
     */
    public static Set<ArtifactKind> kinds() {
        return KINDS;
    }

    /**
     * A builder of the minimal document, {@code {"schemaVersion":1}}.
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
     * The kinds in force.
     *
     * @return the kinds, in kind order; empty when absent, which means none, as {@code []} does
     */
    public Optional<SortedSet<ArtifactKind>> activatedKinds() {
        return activatedKinds;
    }

    /**
     * Whether a kind is in force: only when {@code activatedKinds} names it (absent means none).
     *
     * @param kind the kind
     * @return {@code true} when the document activates the kind
     */
    public boolean inForce(ArtifactKind kind) {
        return activatedKinds.map(k -> k.contains(kind)).orElse(false);
    }

    /**
     * The mission types in force, by id.
     *
     * @return the ids; empty when absent, which means none, as {@code []} does
     */
    public Optional<SortedSet<String>> missionTypes() {
        return missionTypes;
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
     * The allow lists: a kind present holds its list, {@code []} included; a kind missing is absent.
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
     * @return the ids; empty when the kind's list is absent
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
     * @param kind a kind of {@link #kinds()}
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
     * @param kind a kind of {@link #kinds()}
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
     * The canonical form as a value tree for {@link Jcs}: members in the schema's order, absent members left out,
     * {@code []} kept, and an empty {@code selected} or {@code deactivated} object left out.
     *
     * @return the tree, unmodifiable
     */
    public Map<String, Object> toCanonicalTree() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("schemaVersion", schemaVersion);
        packs.ifPresent(
                p -> tree.put("packs", p.stream().map(PackRef::toCanonicalTree).toList()));
        activatedKinds.ifPresent(k -> tree.put("activatedKinds", tokens(k)));
        missionTypes.ifPresent(m -> tree.put("missionTypes", List.copyOf(m)));
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
        builder.activatedKinds = activatedKinds.map(k -> new TreeSet<>(k));
        builder.missionTypes = missionTypes.map(List::copyOf);
        builder.languages = languages.map(List::copyOf);
        selected.forEach((kind, ids) -> builder.selected.put(kind, new TreeSet<>(ids)));
        deactivated.forEach((kind, ids) -> builder.deactivated.put(kind, new TreeSet<>(ids)));
        return builder;
    }

    private static List<String> tokens(Collection<ArtifactKind> kinds) {
        return kinds.stream().map(ArtifactKind::token).sorted(CODE_POINT_ORDER).toList();
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
     * A document built, and the shape violations met on the way (ADR-0020 D10). The document is always built: a part
     * with a violation is left out of it.
     *
     * @param document the document
     * @param violations the shape violations, in the order met; empty when the shape is valid
     */
    public record Built(ActivationDocument document, List<RefusalViolation> violations) {

        /**
         * Copies the violations.
         *
         * @param document the document
         * @param violations the shape violations
         */
        public Built {
            Objects.requireNonNull(document, "document");
            violations = List.copyOf(violations);
        }
    }

    /**
     * Builds a document, one method per member, collecting shape violations rather than throwing on them:
     * {@code unknown_kind} for a kind outside {@link #kinds()}, {@code duplicate_pack} for a pack named twice and
     * {@code empty_deny_list} for a deny list written empty. Shapes the contract's schema already refuses
     * ({@code schemaVersion} other than 1, an empty {@code packs} list) are programming errors here and throw.
     * Pointers index the list as written.
     */
    public static final class Builder {

        private int schemaVersion = SCHEMA_VERSION;
        private Optional<List<PackRef>> packs = Optional.empty();
        private Optional<SortedSet<ArtifactKind>> activatedKinds = Optional.empty();
        private Optional<List<String>> missionTypes = Optional.empty();
        private Optional<List<String>> languages = Optional.empty();
        private final Map<ArtifactKind, SortedSet<String>> selected = new EnumMap<>(ArtifactKind.class);
        private final Map<ArtifactKind, SortedSet<String>> deactivated = new EnumMap<>(ArtifactKind.class);
        private final List<RefusalViolation> violations = new ArrayList<>();

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
                throw new IllegalArgumentException("The activation document shape version is 1, not " + version + ".");
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
         * The kinds in force; a kind outside {@link #kinds()} is an {@code unknown_kind} violation and left out.
         *
         * @param written the kinds as written
         * @return this builder
         */
        public Builder activatedKinds(List<ArtifactKind> written) {
            SortedSet<ArtifactKind> kept = new TreeSet<>();
            for (int i = 0; i < written.size(); i++) {
                ArtifactKind kind = written.get(i);
                if (knownKind(kind, "/activatedKinds/" + i)) {
                    kept.add(kind);
                }
            }
            this.activatedKinds = Optional.of(kept);
            return this;
        }

        /**
         * The mission types in force.
         *
         * @param ids the mission type ids as written
         * @return this builder
         */
        public Builder missionTypes(Collection<String> ids) {
            this.missionTypes = Optional.of(List.copyOf(ids));
            return this;
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
         * One kind's allow list; a kind outside {@link #kinds()} is an {@code unknown_kind} violation and left out.
         *
         * @param kind the kind
         * @param ids the ids as written; empty for {@code []}
         * @return this builder
         */
        public Builder selected(ArtifactKind kind, Collection<String> ids) {
            if (knownKind(kind, "/selected/" + kind.token())) {
                selected.put(kind, frozen(ids));
            }
            return this;
        }

        /**
         * One kind's deny list; an empty one is an {@code empty_deny_list} violation, and a kind outside
         * {@link #kinds()} an {@code unknown_kind} one; either is left out.
         *
         * @param kind the kind
         * @param ids the ids as written
         * @return this builder
         */
        public Builder deactivated(ArtifactKind kind, Collection<String> ids) {
            String pointer = "/deactivated/" + kind.token();
            if (knownKind(kind, pointer) && nonEmptyDenyList(ids, pointer)) {
                deactivated.put(kind, frozen(ids));
            }
            return this;
        }

        private boolean knownKind(ArtifactKind kind, String pointer) {
            if (KINDS.contains(kind)) {
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
            return new Built(new ActivationDocument(this), violations);
        }
    }
}
