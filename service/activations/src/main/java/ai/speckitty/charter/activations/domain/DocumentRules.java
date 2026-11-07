package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.RequiringPack;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks an activation document against the closure it names (FR-006, FR-007, FR-010 body half, FR-017 write half), as
 * pure functions over an {@link ClosureSnapshot}.
 *
 * <p>A {@code 422 activation_invalid} names every violation, in JSON Pointer order (segment by segment, an index
 * compared as a number). Every pointer is an RFC 6901 pointer into the request body: a list index is the position as
 * written ({@link ActivationDocument.Built#written()}), a repeated unknown id has one violation per position, and a
 * repeated pack is judged at its first position only (the later ones are {@code duplicate_pack}). A document checked
 * without a written form ({@link #check(ActivationDocument, ClosureSnapshot)}) is indexed by its canonical lists.
 * The codes:
 *
 * <ul>
 *   <li>{@code unknown_artifact}: a {@code selected} or {@code deactivated} id the closure does not hold, of any kind,
 *       {@code mission_type} included;
 *   <li>{@code unknown_pack}: a {@code packs} id no configured pack has;
 *   <li>{@code version_not_held}: a pinned {@code packs} version other than the one held, refused with a detail naming
 *       both versions (research R2; operator ruling of 2026-11-06), by a create only: every other write drops the pin
 *       (B1, decision DM-01M6RN5Y406SB4KA13QYMQ2P8J, {@link StaleIds});
 *   <li>{@code duplicate_pack}, {@code unknown_kind} and {@code empty_deny_list}: the shape violations of
 *       {@link ActivationDocument.Builder}.
 * </ul>
 *
 * <p>Only a document with no violation meets the floor (ADR-0012 D7): a deny entry on an artifact a pack locks is a
 * {@code 409 artifact_locked} naming each locking pack, and otherwise one on an artifact a pack requires is a
 * {@code 409 artifact_required} naming each requiring pack. Locked wins, since locked implies required. Each
 * {@link RequiringPack} names the pack by the id it is configured under, and its {@code via} is the extends chain
 * ({@link ClosureSnapshot#extendsChain}) from the first pack the document names, in its order, whose
 * {@code parent_pack} line reaches the requiring pack: empty when a pack the document names (or, without
 * {@code packs}, any configured pack) is the requiring pack itself, and empty as well when the requiring pack is
 * reached over {@code depends_on} only, which is no extends chain.
 *
 * <p>A snapshot that is not {@linkplain ClosureSnapshot#resolved() resolved} holds no closure: a pack the document
 * names is configured as nothing (research R4). Only those packs are judged then, each an {@code unknown_pack}
 * violation at its first position; the ids cannot be judged against a closure that does not exist.
 *
 * <p>A replace ({@link #checkReplace}) is judged against the document it replaces (A4, decision
 * DM-01M6RA7XC5HC8915GZT2R3H83W), so a GET then PUT of the same body never fails without a pack change: an id the
 * packs no longer hold is no {@code unknown_artifact} when the stored document names it, a pin to a version no pack
 * holds is no {@code version_not_held} (B1), and the floor is judged on the deny entries it adds only. Both are then
 * dropped from the document stored ({@link StaleIds}). A mission type is judged as any kind is, its deny entries
 * included (A7 Q2).
 *
 * <p>A stored document is not judged here: a read serves it unchanged.
 */
public final class DocumentRules {

    /** JSON Pointer order: segment by segment, an all-digit segment compared as a number. */
    static final Comparator<String> POINTER_ORDER = DocumentRules::comparePointers;

    private DocumentRules() {}

    /**
     * Checks a document.
     *
     * @param document the document
     * @param snapshot the closure the document names
     * @return the refusal; empty when the document may be stored
     */
    public static Optional<CharterRefusal> check(ActivationDocument document, ClosureSnapshot snapshot) {
        return check(new ActivationDocument.Built(document, List.of()), snapshot);
    }

    /**
     * Checks a document just built, together with its shape violations.
     *
     * @param built the document and its shape violations
     * @param snapshot the closure the document names
     * @return the refusal; empty when the document may be stored
     */
    public static Optional<CharterRefusal> check(ActivationDocument.Built built, ClosureSnapshot snapshot) {
        List<RefusalViolation> violations = violations(built, snapshot, Tolerance.NONE);
        if (violations.isEmpty()) {
            return floorRefusal(built.document(), snapshot);
        }
        return Optional.of(invalid(violations));
    }

    private static List<RefusalViolation> violations(
            ActivationDocument.Built built, ClosureSnapshot snapshot, Tolerance tolerance) {
        List<RefusalViolation> violations = new ArrayList<>(built.violations());
        violations.addAll(packViolations(built.written(), snapshot, tolerance));
        if (snapshot.resolved()) {
            violations.addAll(idViolations("/selected", built.written().selected(), snapshot, tolerance));
            violations.addAll(idViolations("/deactivated", built.written().deactivated(), snapshot, tolerance));
        }
        return violations;
    }

    /**
     * Checks the document a per-artifact or preset write computed (data model: "the floor is never denied"): every id
     * is judged as {@link #check(ActivationDocument, ClosureSnapshot)} judges it (FR-017), but the floor only on the
     * deny entries the write adds. A stored deny entry on an artifact a pack upgrade made required or locked is
     * tolerated, not applied (ADR-0059 D4), so it never refuses a write of another id.
     *
     * @param before the document as stored
     * @param after the document the write computed
     * @param snapshot the closure the document names
     * @return the refusal; empty when the document may be stored
     */
    public static Optional<CharterRefusal> checkWrite(
            ActivationDocument before, ActivationDocument after, ClosureSnapshot snapshot) {
        Optional<CharterRefusal> refusal = check(after, snapshot);
        if (refusal.isEmpty() || !refusal.orElseThrow().violations().isEmpty()) {
            return refusal;
        }
        return floorRefusal(addedDenies(before, after), snapshot);
    }

    /**
     * Checks the body of a full replace against the document it replaces (A4, B1): every id is judged as
     * {@link #check(ActivationDocument.Built, ClosureSnapshot)} judges it, except that an id the closure no longer
     * holds is tolerated when the stored document names it (in any known spelling, in either list of its kind), and a
     * pinned version the closure does not hold is tolerated; {@link StaleIds#drop} then drops both from the document
     * stored. The floor is judged on the deny entries the body adds to the stored document only ({@link #addedDenies}),
     * as {@link #checkWrite} judges a per-artifact write.
     *
     * @param built the body as the request built it, normalized, with its shape violations
     * @param stored the document the replace replaces; the empty document when nothing is stored, so that every deny
     *     entry is added and every unknown id is refused
     * @param snapshot the closure the body names
     * @return the refusal; empty when the body, less what {@link StaleIds#drop} drops, may be stored
     */
    public static Optional<CharterRefusal> checkReplace(
            ActivationDocument.Built built, ActivationDocument stored, ClosureSnapshot snapshot) {
        List<RefusalViolation> violations = violations(built, snapshot, Tolerance.replacing(stored));
        if (!violations.isEmpty()) {
            return Optional.of(invalid(violations));
        }
        ActivationDocument kept =
                snapshot.resolved() ? StaleIds.drop(built.document(), snapshot).document() : built.document();
        return floorRefusal(addedDenies(stored, kept), snapshot);
    }

    /**
     * What a write tolerates in the body it judges: nothing for a create; the ids the stored document names and every
     * pinned version for a replace.
     */
    private record Tolerance(ActivationDocument stored, boolean pinsDropped) {

        static final Tolerance NONE =
                new Tolerance(ActivationDocument.builder().build().document(), false);

        static Tolerance replacing(ActivationDocument stored) {
            return new Tolerance(stored, true);
        }

        /** Whether an id the closure does not hold is one the stored document still names, so a stale one. */
        boolean stale(ArtifactKind kind, String id) {
            Set<String> named = new HashSet<>(stored.deactivated(kind));
            stored.selected(kind).ifPresent(named::addAll);
            return ArtifactSpellings.resolve(kind, id, named) instanceof ArtifactSpellings.Resolution.Declared;
        }
    }

    /**
     * The document's packs and the deny entries {@code after} holds and {@code before} does not
     * ({@code Den(D') \ Den(D)}): the deny entries a write is judged on the floor for.
     */
    static ActivationDocument addedDenies(ActivationDocument before, ActivationDocument after) {
        ActivationDocument.Builder added = ActivationDocument.builder();
        after.packs().ifPresent(added::packs);
        after.deactivated().forEach((kind, ids) -> {
            List<String> adding = ids.stream()
                    .filter(id -> !before.deactivated(kind).contains(id))
                    .toList();
            if (!adding.isEmpty()) {
                added.deactivated(kind, adding);
            }
        });
        return added.build().document();
    }

    private static CharterRefusal invalid(List<RefusalViolation> violations) {
        violations.sort(Comparator.comparing(RefusalViolation::pointer, POINTER_ORDER));
        return CharterRefusal.withViolations(
                RefusalCode.ACTIVATION_INVALID,
                "The activation document names " + violations.size() + " thing(s) its packs do not hold.",
                violations);
    }

    private static List<RefusalViolation> packViolations(
            ActivationDocument.Written written, ClosureSnapshot snapshot, Tolerance tolerance) {
        List<PackRef> packs = written.packs();
        Set<String> seen = new HashSet<>();
        List<RefusalViolation> violations = new ArrayList<>();
        for (int i = 0; i < packs.size(); i++) {
            PackRef pack = packs.get(i);
            if (seen.add(pack.packId())) {
                packViolation(pack, "/packs/" + i, snapshot, tolerance).ifPresent(violations::add);
            }
        }
        return violations;
    }

    private static Optional<RefusalViolation> packViolation(
            PackRef pack, String pointer, ClosureSnapshot snapshot, Tolerance tolerance) {
        if (!snapshot.resolved() && !snapshot.unknownPacks().contains(pack.packId())) {
            return Optional.empty();
        }
        Optional<ClosureSnapshot.PackEntry> entry = snapshot.pack(pack.packId());
        if (entry.isEmpty()) {
            return Optional.of(new RefusalViolation(
                    pointer + "/packId", "unknown_pack", "No pack is configured as '" + pack.packId() + "'."));
        }
        String held = entry.orElseThrow().version();
        return pack.version()
                .filter(pinned -> !tolerance.pinsDropped() && !pinned.equals(held))
                .map(pinned -> new RefusalViolation(
                        pointer + "/version",
                        "version_not_held",
                        "The pack '" + pack.packId() + "' is held at " + held + ", not " + pinned + "."));
    }

    private static List<RefusalViolation> idViolations(
            String member, Map<ArtifactKind, List<String>> lists, ClosureSnapshot snapshot, Tolerance tolerance) {
        List<RefusalViolation> violations = new ArrayList<>();
        for (Map.Entry<ArtifactKind, List<String>> list : lists.entrySet()) {
            violations.addAll(unknownArtifacts(member, list, snapshot, tolerance));
        }
        return violations;
    }

    private static List<RefusalViolation> unknownArtifacts(
            String member, Map.Entry<ArtifactKind, List<String>> list, ClosureSnapshot snapshot, Tolerance tolerance) {
        ArtifactKind kind = list.getKey();
        String pointer = member + "/" + kind.token();
        List<RefusalViolation> violations = new ArrayList<>();
        for (int i = 0; i < list.getValue().size(); i++) {
            String id = list.getValue().get(i);
            switch (ArtifactSpellings.resolve(kind, id, snapshot.held(kind))) {
                case ArtifactSpellings.Resolution.Declared declared -> {}
                case ArtifactSpellings.Resolution.Unknown unknown when tolerance.stale(kind, id) -> {}
                case ArtifactSpellings.Resolution.Unknown unknown ->
                    violations.add(new RefusalViolation(
                            pointer + "/" + i,
                            "unknown_artifact",
                            "The activation's packs hold no " + kind.token() + " '" + id + "'."));
                case ArtifactSpellings.Resolution.Ambiguous ambiguous ->
                    violations.add(new RefusalViolation(
                            pointer + "/" + i,
                            "ambiguous_artifact",
                            "'" + id + "' matches " + String.join(", ", ambiguous.candidates())
                                    + ", which differ only by case; write the declared id."));
            }
        }
        return violations;
    }

    /**
     * Writes every known spelling of an id (the declared id, its full URN, a case variant of either) as the declared
     * id, in the allow and deny lists as written and in the document, so that every spelling gives one stored document
     * and one revision (ADR-0020 D4, operator ruling of 2026-11-06). A spelling that is unknown or ambiguous is left
     * as written, for {@link #check(ActivationDocument.Built, ClosureSnapshot)} to refuse at its position. A closure
     * that did not compose normalizes nothing.
     *
     * @param built the document as the request built it
     * @param snapshot the closure the document names
     * @return the document with the declared ids, and its shape violations unchanged
     */
    public static ActivationDocument.Built normalize(ActivationDocument.Built built, ClosureSnapshot snapshot) {
        if (!snapshot.resolved()) {
            return built;
        }
        ActivationDocument.Written written = built.written();
        Map<ArtifactKind, List<String>> selected = declared(written.selected(), snapshot);
        Map<ArtifactKind, List<String>> deactivated = declared(written.deactivated(), snapshot);
        ActivationDocument document = built.document().withLists(selected, deactivated);
        return new ActivationDocument.Built(
                document, built.violations(), new ActivationDocument.Written(written.packs(), selected, deactivated));
    }

    private static Map<ArtifactKind, List<String>> declared(
            Map<ArtifactKind, List<String>> lists, ClosureSnapshot snapshot) {
        Map<ArtifactKind, List<String>> declared = new EnumMap<>(ArtifactKind.class);
        lists.forEach((kind, ids) -> declared.put(
                kind,
                ids.stream()
                        .map(id -> switch (ArtifactSpellings.resolve(kind, id, snapshot.held(kind))) {
                            case ArtifactSpellings.Resolution.Declared found -> found.id();
                            case ArtifactSpellings.Resolution.Unknown unknown -> id;
                            case ArtifactSpellings.Resolution.Ambiguous ambiguous -> id;
                        })
                        .toList()));
        return declared;
    }

    private static Optional<CharterRefusal> floorRefusal(ActivationDocument document, ClosureSnapshot snapshot) {
        List<String> denied = deniedUrns(document);
        List<String> locked = denied.stream()
                .filter(urn -> snapshot.lockingPack(urn).isPresent())
                .toList();
        if (!locked.isEmpty()) {
            Set<String> locking = locked.stream()
                    .flatMap(urn -> snapshot.lockingPack(urn).stream())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return Optional.of(floor(RefusalCode.ARTIFACT_LOCKED, "locks", locked, via(document, snapshot, locking)));
        }
        List<String> required = denied.stream()
                .filter(urn -> !snapshot.requiringPacks(urn).isEmpty())
                .toList();
        Set<String> requiring = required.stream()
                .flatMap(urn -> snapshot.requiringPacks(urn).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return required.isEmpty()
                ? Optional.empty()
                : Optional.of(
                        floor(RefusalCode.ARTIFACT_REQUIRED, "requires", required, via(document, snapshot, requiring)));
    }

    private static List<String> deniedUrns(ActivationDocument document) {
        List<String> urns = new ArrayList<>();
        document.deactivated()
                .forEach((kind, ids) -> ids.forEach(id -> urns.add(new Urn(NodeKind.of(kind), id).toString())));
        return urns;
    }

    /** Each requiring pack with the extends chain it is reached through from the document's packs. */
    private static List<RequiringPack> via(ActivationDocument document, ClosureSnapshot snapshot, Set<String> packs) {
        List<String> roots = document.packs()
                .map(list -> list.stream().map(PackRef::packId).toList())
                .orElseGet(List::of);
        return packs.stream()
                .map(pack -> new RequiringPack(pack, chainTo(pack, roots, snapshot)))
                .toList();
    }

    private static List<String> chainTo(String pack, List<String> roots, ClosureSnapshot snapshot) {
        if (roots.contains(pack)) {
            return List.of();
        }
        return roots.stream()
                .map(root -> snapshot.extendsChain(root, pack))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(List::of);
    }

    private static CharterRefusal floor(
            RefusalCode code, String verb, List<String> urns, List<RequiringPack> requiredBy) {
        return CharterRefusal.withRequiredBy(
                code,
                "A pack " + verb + " " + String.join(", ", urns) + ", so the activation cannot deny it.",
                requiredBy);
    }

    private static int comparePointers(String left, String right) {
        String[] a = left.split("/", -1);
        String[] b = right.split("/", -1);
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int order = compareSegments(a[i], b[i]);
            if (order != 0) {
                return order;
            }
        }
        return Integer.compare(a.length, b.length);
    }

    private static int compareSegments(String left, String right) {
        if (isIndex(left) && isIndex(right)) {
            return Comparator.comparingInt(String::length)
                    .thenComparing(Comparator.naturalOrder())
                    .compare(left, right);
        }
        return left.compareTo(right);
    }

    private static boolean isIndex(String segment) {
        return !segment.isEmpty() && segment.chars().allMatch(c -> c >= '0' && c <= '9');
    }
}
