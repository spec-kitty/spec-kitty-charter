package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * What one sub-resource write changed (ADR-0020 D6, the contract's {@code ActivationChange}): the document after it,
 * and the references it changed. {@link #of} is the one definition of {@code applied} and {@code cleared}, shared by
 * select, unselect, deactivate, reactivate and preset:
 *
 * <ul>
 *   <li>{@code applied}: every id the write added to or removed from {@code selected}, {@code deactivated} or
 *       {@code missionTypes}. A kind ({@code activatedKinds}) is never an entry.
 *   <li>{@code cleared}: every id the write removed from {@code deactivated}.
 * </ul>
 *
 * <p>A no-op has both empty, and its document keeps its revision.
 *
 * @param after the document after the write
 * @param applied the ids whose state the write changed, sorted
 * @param cleared the deny entries the write removed, sorted
 * @param skipped what the cascade left out
 */
public record ActivationChange(
        ActivationDocument after, List<ArtifactRef> applied, List<ArtifactRef> cleared, SkippedReport skipped) {

    /**
     * Checks the fields and sorts the lists.
     *
     * @param after the document after the write
     * @param applied the changed ids
     * @param cleared the cleared deny entries
     * @param skipped the skipped report
     */
    public ActivationChange {
        Objects.requireNonNull(after, "after");
        applied = List.copyOf(new TreeSet<>(applied));
        cleared = List.copyOf(new TreeSet<>(cleared));
        Objects.requireNonNull(skipped, "skipped");
    }

    /**
     * The change between two documents.
     *
     * @param before the document before the write
     * @param after the document after it
     * @param skipped what the cascade left out
     * @return the change
     */
    public static ActivationChange of(ActivationDocument before, ActivationDocument after, SkippedReport skipped) {
        Set<ArtifactRef> applied = new TreeSet<>();
        applied.addAll(changed(refs(before.selected()), refs(after.selected())));
        applied.addAll(changed(refs(before.deactivated()), refs(after.deactivated())));
        applied.addAll(changed(missionTypes(before), missionTypes(after)));
        Set<ArtifactRef> cleared = new TreeSet<>(refs(before.deactivated()));
        cleared.removeAll(refs(after.deactivated()));
        return new ActivationChange(after, List.copyOf(applied), List.copyOf(cleared), skipped);
    }

    static SortedSet<ArtifactRef> refs(Map<ArtifactKind, SortedSet<String>> lists) {
        SortedSet<ArtifactRef> refs = new TreeSet<>();
        lists.forEach((kind, ids) -> ids.forEach(id -> refs.add(new ArtifactRef(kind, id))));
        return refs;
    }

    private static Set<ArtifactRef> missionTypes(ActivationDocument document) {
        Set<ArtifactRef> refs = new TreeSet<>();
        document.missionTypes()
                .ifPresent(ids -> ids.forEach(id -> refs.add(new ArtifactRef(ArtifactKind.MISSION_TYPE, id))));
        return refs;
    }

    /** The symmetric difference: what is in one set and not the other. */
    private static Set<ArtifactRef> changed(Set<ArtifactRef> before, Set<ArtifactRef> after) {
        Set<ArtifactRef> changed = new TreeSet<>(before);
        changed.addAll(after);
        Set<ArtifactRef> kept = new TreeSet<>(before);
        kept.retainAll(after);
        changed.removeAll(kept);
        return changed;
    }
}
