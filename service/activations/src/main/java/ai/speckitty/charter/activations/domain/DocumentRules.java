package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.activations.domain.MergedSnapshot.ArtifactState;
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
 * without a written form ({@link #check(ActivationDocument, ClosureSnapshot, InForceFacts)}) is indexed by its
 * canonical lists. The codes:
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
 * {@code 409 artifact_required} naming each requiring pack, unless an active override of it satisfies the requirement
 * (IC-13, operator ruling Q9, research R12). Locked wins, since locked implies required, and no override satisfies a
 * lock. "Active" is the in-force resolution's own judgement of the document as it will be stored ({@link InForceFacts}:
 * an artifact it reports as satisfied by an artifact in force), never a rule of this class, so a deny entry accepted on
 * write is one the next read applies; an enhancement never satisfies. The facts are asked only when a write adds a
 * deny entry on a required artifact no pack locks, and a refusal of the closure's merge ({@code pack_unreadable})
 * reaches that write. Which entries a write judges: a create judges every deny entry of the body; a deactivate judges
 * the named target whether or not its deny entry is already stored; a replace and the other writes judge only the deny
 * entries they add. Removing the override later is not refused (no write judges a stored deny entry it does not add),
 * and the read lists it in {@code denyNotApplied}. Each
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
     * Checks a document, with the in-force facts that decide a deny entry on a required artifact (IC-13). This form is
     * used by tests; main code checks the {@link ActivationDocument.Built} form.
     *
     * @param document the document
     * @param snapshot the closure the document names
     * @param facts what the in-force resolution puts in force for the document as it will be stored
     * @return the refusal; empty when the document may be stored
     */
    public static Optional<CharterRefusal> check(
            ActivationDocument document, ClosureSnapshot snapshot, InForceFacts facts) {
        return check(new ActivationDocument.Built(document, List.of()), snapshot, facts);
    }

    /**
     * Checks a document just built, together with its shape violations, judging every deny entry on the floor (a
     * create). A deny entry on a required artifact is accepted when an active override of it satisfies the requirement
     * in this document, as the in-force resolution judges it ({@link InForceFacts}); one on a locked artifact never is.
     *
     * @param built the document and its shape violations
     * @param snapshot the closure the document names
     * @param facts what the in-force resolution puts in force for the document as it will be stored
     * @return the refusal; empty when the document may be stored
     */
    public static Optional<CharterRefusal> check(
            ActivationDocument.Built built, ClosureSnapshot snapshot, InForceFacts facts) {
        List<RefusalViolation> violations = violations(built, snapshot, Tolerance.NONE);
        if (violations.isEmpty()) {
            return floorRefusal(built.document(), built.document(), snapshot, facts);
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
     * is judged as {@link #check(ActivationDocument, ClosureSnapshot, InForceFacts)} judges it (FR-017), but the floor
     * only on the deny entries the write adds. A stored deny entry on an artifact a pack upgrade made required or
     * locked is tolerated, not applied (ADR-0059 D4), so it never refuses a write of another id. A deny entry the write
     * adds on a required artifact is accepted when an active override of it satisfies the requirement in {@code after},
     * the document as it will be stored (IC-13). The facts are asked only for such an entry; a stored deny entry, and a
     * write that adds none (an unselect of the override, a select with {@code includeDeactivated}), never ask.
     *
     * @param before the document as stored
     * @param after the document the write computed
     * @param snapshot the closure the document names
     * @param facts what the in-force resolution puts in force for {@code after}
     * @return the refusal; empty when the document may be stored
     */
    public static Optional<CharterRefusal> checkWrite(
            ActivationDocument before, ActivationDocument after, ClosureSnapshot snapshot, InForceFacts facts) {
        List<RefusalViolation> violations =
                violations(new ActivationDocument.Built(after, List.of()), snapshot, Tolerance.NONE);
        if (!violations.isEmpty()) {
            return Optional.of(invalid(violations));
        }
        return floorRefusal(addedDenies(before, after, snapshot), after, snapshot, facts);
    }

    /**
     * Checks the body of a full replace against the document it replaces (A4, B1): every id is judged as
     * {@link #check(ActivationDocument.Built, ClosureSnapshot, InForceFacts)} judges it, except that an id the closure
     * no longer holds is tolerated when the stored document names it (in any known spelling, in either list of its
     * kind), and a pinned version the closure does not hold is tolerated; {@link StaleIds#drop} then drops both from
     * the document stored. The floor is judged on the deny entries the body adds to the stored document only
     * ({@link #addedDenies}), as {@link #checkWrite} judges a per-artifact write. A deny entry the body adds on a
     * required artifact is accepted when an active override of it satisfies the requirement in the document as it will
     * be stored, the body less what {@link StaleIds#drop} drops (IC-13), so a body that adds the override and the deny
     * entry together is accepted.
     *
     * @param built the body as the request built it, normalized, with its shape violations
     * @param stored the document the replace replaces; the empty document when nothing is stored, so that every deny
     *     entry is added and every unknown id is refused
     * @param snapshot the closure the body names
     * @param facts what the in-force resolution puts in force for the document as it will be stored
     * @return the refusal; empty when the body, less what {@link StaleIds#drop} drops, may be stored
     */
    public static Optional<CharterRefusal> checkReplace(
            ActivationDocument.Built built, ActivationDocument stored, ClosureSnapshot snapshot, InForceFacts facts) {
        List<RefusalViolation> violations = violations(built, snapshot, Tolerance.replacing(stored));
        if (!violations.isEmpty()) {
            return Optional.of(invalid(violations));
        }
        ActivationDocument kept =
                snapshot.resolved() ? StaleIds.drop(built.document(), snapshot).document() : built.document();
        return floorRefusal(addedDenies(stored, kept, snapshot), kept, snapshot, facts);
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
            return ArtifactSpellings.resolve(kind, id, named(kind)) instanceof ArtifactSpellings.Resolution.Declared;
        }

        /** The ids the stored document names for a kind, in either list. */
        Set<String> named(ArtifactKind kind) {
            Set<String> named = new HashSet<>(stored.deactivated(kind));
            stored.selected(kind).ifPresent(named::addAll);
            return named;
        }
    }

    /**
     * The document's packs and the deny entries {@code after} holds and {@code before} does not
     * ({@code Den(D') \ Den(D)}): the deny entries a write is judged on the floor for. The stored document is not
     * re-normalized, so a stored entry is compared in the declared spelling the closure holds for it (as
     * {@link ArtifactSpellings} resolves it): a stored deny in another spelling of the same artifact is not added.
     */
    static ActivationDocument addedDenies(
            ActivationDocument before, ActivationDocument after, ClosureSnapshot snapshot) {
        ActivationDocument.Builder added = ActivationDocument.builder();
        after.packs().ifPresent(added::packs);
        after.deactivated().forEach((kind, ids) -> {
            Set<String> held = snapshot.held(kind);
            Set<String> stored = new HashSet<>();
            before.deactivated(kind).forEach(id -> stored.add(declared(kind, id, held)));
            List<String> adding = ids.stream()
                    .filter(id -> !stored.contains(declared(kind, id, held)))
                    .toList();
            if (!adding.isEmpty()) {
                added.deactivated(kind, adding);
            }
        });
        return added.build().document();
    }

    /** The declared id the closure holds for a spelled id; the spelling itself when it resolves to none. */
    private static String declared(ArtifactKind kind, String id, Set<String> held) {
        return ArtifactSpellings.resolve(kind, id, held) instanceof ArtifactSpellings.Resolution.Declared(String found)
                ? found
                : id;
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
     * as written, for {@link #check(ActivationDocument.Built, ClosureSnapshot, InForceFacts)} to refuse at its position. A closure
     * that did not compose normalizes nothing.
     *
     * @param built the document as the request built it
     * @param snapshot the closure the document names
     * @return the document with the declared ids, and its shape violations unchanged
     */
    public static ActivationDocument.Built normalize(ActivationDocument.Built built, ClosureSnapshot snapshot) {
        return normalize(built, snapshot, ActivationDocument.builder().build().document());
    }

    /**
     * {@link #normalize(ActivationDocument.Built, ClosureSnapshot)} for a replace: a spelling the closure holds no id for
     * is read against the ids the stored document names for its kind (in either list), so that a stale id the body
     * spells as a URN or in another case is written, and reported in {@code dropped}, as the stored document spells it
     * ({@code ActivationWrites.declared} reads a path id the same way).
     *
     * @param built the document as the request built it
     * @param snapshot the closure the document names
     * @param stored the document the replace replaces; the empty document for a create
     * @return the document with the declared ids, and its shape violations unchanged
     */
    public static ActivationDocument.Built normalize(
            ActivationDocument.Built built, ClosureSnapshot snapshot, ActivationDocument stored) {
        if (!snapshot.resolved()) {
            return built;
        }
        ActivationDocument.Written written = built.written();
        Map<ArtifactKind, List<String>> selected = declared(written.selected(), snapshot, stored);
        Map<ArtifactKind, List<String>> deactivated = declared(written.deactivated(), snapshot, stored);
        ActivationDocument document = built.document().withLists(selected, deactivated);
        return new ActivationDocument.Built(
                document, built.violations(), new ActivationDocument.Written(written.packs(), selected, deactivated));
    }

    private static Map<ArtifactKind, List<String>> declared(
            Map<ArtifactKind, List<String>> lists, ClosureSnapshot snapshot, ActivationDocument stored) {
        Map<ArtifactKind, List<String>> declared = new EnumMap<>(ArtifactKind.class);
        lists.forEach((kind, ids) -> {
            Set<String> storedIds = Tolerance.replacing(stored).named(kind);
            declared.put(
                    kind,
                    ids.stream()
                            .map(id -> declaredId(kind, id, snapshot.held(kind), storedIds))
                            .toList());
        });
        return declared;
    }

    /** The id as the closure declares it, else as the stored document spells it, else as written. */
    private static String declaredId(ArtifactKind kind, String id, Set<String> held, Set<String> stored) {
        ArtifactSpellings.Resolution resolution = ArtifactSpellings.resolve(kind, id, held);
        if (resolution instanceof ArtifactSpellings.Resolution.Unknown) {
            resolution = ArtifactSpellings.resolve(kind, id, stored);
        }
        return resolution instanceof ArtifactSpellings.Resolution.Declared found ? found.id() : id;
    }

    /**
     * The floor refusal for the deny entries of {@code denying}: a locked one wins, since locked implies required; a
     * required one is refused unless an active override satisfies it in {@code stored}, the document as it will be
     * stored (IC-13). The facts are asked once, and only when a deny entry is on a required artifact no pack locks.
     */
    private static Optional<CharterRefusal> floorRefusal(
            ActivationDocument denying, ActivationDocument stored, ClosureSnapshot snapshot, InForceFacts facts) {
        List<String> denied = deniedUrns(denying);
        List<String> locked = denied.stream()
                .filter(urn -> snapshot.lockingPack(urn).isPresent())
                .toList();
        if (!locked.isEmpty()) {
            Set<String> locking = locked.stream()
                    .flatMap(urn -> snapshot.lockingPack(urn).stream())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return Optional.of(floor(RefusalCode.ARTIFACT_LOCKED, "locks", locked, via(denying, snapshot, locking)));
        }
        List<String> unmet = unsatisfied(
                denied.stream()
                        .filter(urn -> !snapshot.requiringPacks(urn).isEmpty())
                        .toList(),
                stored,
                facts);
        Set<String> requiring = unmet.stream()
                .flatMap(urn -> snapshot.requiringPacks(urn).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return unmet.isEmpty()
                ? Optional.empty()
                : Optional.of(
                        floor(RefusalCode.ARTIFACT_REQUIRED, "requires", unmet, via(denying, snapshot, requiring)));
    }

    /**
     * The denied required artifacts no active override satisfies in the document as it will be stored. The in-force
     * resolution decides: an artifact it reports as satisfied by an artifact in force (E5, E9) is satisfied; one it
     * puts back in force with its deny entry not applied, or does not report, is not. An enhancement never satisfies.
     */
    private static List<String> unsatisfied(List<String> required, ActivationDocument stored, InForceFacts facts) {
        if (required.isEmpty()) {
            return required;
        }
        Map<ArtifactRef, ArtifactState> states =
                facts.inForce(EffectiveSet.queryOf(stored)).states();
        return required.stream().filter(urn -> !satisfied(urn, states)).toList();
    }

    private static boolean satisfied(String urn, Map<ArtifactRef, ArtifactState> states) {
        return Urn.parse(urn)
                .flatMap(ArtifactRef::of)
                .map(states::get)
                .filter(state -> !state.satisfiedBy().isEmpty())
                .isPresent();
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
