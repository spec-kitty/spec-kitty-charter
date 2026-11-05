package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.kernel.domain.RequiringPack;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;

/**
 * Checks an activation document against the closure it names (FR-006, FR-007, FR-010 body half, FR-017 write half), as
 * pure functions over an {@link ClosureSnapshot}.
 *
 * <p>A {@code 422 activation_invalid} names every violation, in JSON Pointer order (segment by segment, an index
 * compared as a number). A pointer into a list indexes the canonical list the service stores and answers (sorted and
 * deduplicated), but for the builder's shape violations, which index the list as written. The codes:
 *
 * <ul>
 *   <li>{@code unknown_artifact}: a {@code selected} or {@code deactivated} id the closure does not hold;
 *   <li>{@code unknown_mission_type}: a {@code missionTypes} id the closure does not hold;
 *   <li>{@code unknown_pack}: a {@code packs} id no configured pack has;
 *   <li>{@code version_not_held}: a pinned {@code packs} version other than the one held (research R2, an open review
 *       item, decided here alone);
 *   <li>{@code duplicate_pack}, {@code unknown_kind} and {@code empty_deny_list}: the shape violations of
 *       {@link ActivationDocument.Builder}.
 * </ul>
 *
 * <p>Only a document with no violation meets the floor (ADR-0012 D7): a deny entry on an artifact a pack locks is a
 * {@code 409 artifact_locked} naming each locking pack, and otherwise one on an artifact a pack requires is a
 * {@code 409 artifact_required} naming each requiring pack. Locked wins, since locked implies required.
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
        List<RefusalViolation> violations = new ArrayList<>(built.violations());
        violations.addAll(packViolations(built.document(), snapshot));
        violations.addAll(idViolations(built.document(), snapshot));
        violations.addAll(denyViolations(built.document(), snapshot));
        if (violations.isEmpty()) {
            return floorRefusal(built.document(), snapshot);
        }
        violations.sort(Comparator.comparing(RefusalViolation::pointer, POINTER_ORDER));
        return Optional.of(CharterRefusal.withViolations(
                RefusalCode.ACTIVATION_INVALID,
                "The activation document names " + violations.size() + " thing(s) its packs do not hold.",
                violations));
    }

    private static List<RefusalViolation> packViolations(ActivationDocument document, ClosureSnapshot snapshot) {
        List<PackRef> packs = document.packs().orElse(List.of());
        List<RefusalViolation> violations = new ArrayList<>();
        for (int i = 0; i < packs.size(); i++) {
            packViolation(packs.get(i), "/packs/" + i, snapshot).ifPresent(violations::add);
        }
        return violations;
    }

    private static Optional<RefusalViolation> packViolation(PackRef pack, String pointer, ClosureSnapshot snapshot) {
        Optional<ClosureSnapshot.PackEntry> entry = snapshot.pack(pack.packId());
        if (entry.isEmpty()) {
            return Optional.of(new RefusalViolation(
                    pointer + "/packId", "unknown_pack", "No pack is configured as '" + pack.packId() + "'."));
        }
        String held = entry.orElseThrow().version();
        return pack.version()
                .filter(pinned -> !pinned.equals(held))
                .map(pinned -> new RefusalViolation(
                        pointer + "/version",
                        "version_not_held",
                        "The pack '" + pack.packId() + "' is held at " + held + ", not " + pinned + "."));
    }

    private static List<RefusalViolation> idViolations(ActivationDocument document, ClosureSnapshot snapshot) {
        List<RefusalViolation> violations = new ArrayList<>(unknown(
                "/missionTypes",
                document.missionTypes().map(List::copyOf).orElse(List.of()),
                snapshot.missionTypes(),
                "unknown_mission_type",
                "mission type"));
        for (Map.Entry<ArtifactKind, SortedSet<String>> list :
                document.selected().entrySet()) {
            violations.addAll(unknownArtifacts("/selected", list, snapshot));
        }
        return violations;
    }

    private static List<RefusalViolation> denyViolations(ActivationDocument document, ClosureSnapshot snapshot) {
        List<RefusalViolation> violations = new ArrayList<>();
        for (Map.Entry<ArtifactKind, SortedSet<String>> list :
                document.deactivated().entrySet()) {
            violations.addAll(unknownArtifacts("/deactivated", list, snapshot));
        }
        return violations;
    }

    private static List<RefusalViolation> unknownArtifacts(
            String member, Map.Entry<ArtifactKind, SortedSet<String>> list, ClosureSnapshot snapshot) {
        ArtifactKind kind = list.getKey();
        return unknown(
                member + "/" + kind.token(), list.getValue(), snapshot.held(kind), "unknown_artifact", kind.token());
    }

    private static List<RefusalViolation> unknown(
            String pointer, Collection<String> ids, Set<String> held, String code, String noun) {
        List<String> listed = List.copyOf(ids);
        List<RefusalViolation> violations = new ArrayList<>();
        for (int i = 0; i < listed.size(); i++) {
            String id = listed.get(i);
            if (!held.contains(id)) {
                violations.add(new RefusalViolation(
                        pointer + "/" + i, code, "The activation's packs hold no " + noun + " '" + id + "'."));
            }
        }
        return violations;
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
            return Optional.of(floor(RefusalCode.ARTIFACT_LOCKED, "locks", locked, locking));
        }
        List<String> required = denied.stream()
                .filter(urn -> !snapshot.requiringPacks(urn).isEmpty())
                .toList();
        Set<String> requiring = required.stream()
                .flatMap(urn -> snapshot.requiringPacks(urn).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return required.isEmpty()
                ? Optional.empty()
                : Optional.of(floor(RefusalCode.ARTIFACT_REQUIRED, "requires", required, requiring));
    }

    private static List<String> deniedUrns(ActivationDocument document) {
        List<String> urns = new ArrayList<>();
        document.deactivated()
                .forEach((kind, ids) -> ids.forEach(id -> urns.add(new Urn(NodeKind.of(kind), id).toString())));
        return urns;
    }

    private static CharterRefusal floor(RefusalCode code, String verb, List<String> urns, Set<String> packs) {
        List<RequiringPack> requiredBy =
                packs.stream().map(pack -> new RequiringPack(pack, List.of())).toList();
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
