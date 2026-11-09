package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * What one sub-resource write changed (ADR-0020 D6, the contract's {@code ActivationChange}): the document after it,
 * and the references it changed. {@link #of} is the one definition of {@code applied} and {@code cleared}, shared by
 * select, unselect, deactivate, reactivate and preset:
 *
 * <ul>
 *   <li>{@code applied}: every id the write added to or removed from {@code selected} or {@code deactivated}, of every
 *       kind.
 *   <li>{@code cleared}: every id the write removed from {@code deactivated}.
 * </ul>
 *
 * <p>{@code dropped} is apart from both: the ids the write removed from the stored document before it computed anything,
 * because the activation's closure no longer declares them (ADR-0059 amendment A5, {@link StaleIds}). They are
 * measured against the stored document, {@code applied} and {@code cleared} against the document without them.
 * {@code droppedPins} is apart as well: the version pins the write dropped from {@code packs} because no pack of the
 * closure holds the pinned version (B1, decision DM-01M6RN5Y406SB4KA13QYMQ2P8J, {@link StaleIds}), each as it was.
 *
 * <p>A no-op has all four empty, and its document keeps its revision.
 *
 * @param after the document after the write
 * @param applied the ids whose state the write changed, sorted
 * @param cleared the deny entries the write removed, sorted
 * @param skipped what the cascade left out
 * @param dropped the stale ids the write dropped from the stored document, sorted
 * @param droppedPins the version pins the write dropped from the stored document, in its pack order
 * @param warnings what the write applied that a read of the stored document will not put in force (FR-032), sorted
 */
public record ActivationChange(
        ActivationDocument after,
        List<ArtifactRef> applied,
        List<ArtifactRef> cleared,
        SkippedReport skipped,
        List<ArtifactRef> dropped,
        List<PackRef> droppedPins,
        List<Warning> warnings) {

    /** Why a read will not put an applied artifact in force (the contract's {@code ActivationWarning} codes). */
    public enum Code {
        /** The artifact is an enhancer none of whose targets is in force. */
        ENHANCED_TARGET_NOT_IN_FORCE,
        /** An artifact in force overrides the artifact. */
        OVERRIDDEN;

        /**
         * The contract's spelling.
         *
         * @return the lower-case token
         */
        public String token() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * An artifact the write applied that a read will not put in force.
     *
     * @param code why
     * @param artifact the artifact
     */
    public record Warning(Code code, ArtifactRef artifact) implements Comparable<Warning> {

        /**
         * Checks the fields.
         *
         * @param code why
         * @param artifact the artifact
         */
        public Warning {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(artifact, "artifact");
        }

        @Override
        public int compareTo(Warning other) {
            int byArtifact = artifact.compareTo(other.artifact);
            return byArtifact != 0 ? byArtifact : code.compareTo(other.code);
        }
    }

    /**
     * Checks the fields and sorts the lists.
     *
     * @param after the document after the write
     * @param applied the changed ids
     * @param cleared the cleared deny entries
     * @param skipped the skipped report
     * @param dropped the dropped stale ids
     * @param droppedPins the dropped version pins
     * @param warnings the warnings of the write
     */
    public ActivationChange {
        Objects.requireNonNull(after, "after");
        applied = List.copyOf(new TreeSet<>(applied));
        cleared = List.copyOf(new TreeSet<>(cleared));
        Objects.requireNonNull(skipped, "skipped");
        dropped = List.copyOf(new TreeSet<>(dropped));
        droppedPins = List.copyOf(droppedPins);
        warnings = List.copyOf(new TreeSet<>(warnings));
    }

    /**
     * A change that reports no warning.
     *
     * @param after the document after the write
     * @param applied the changed ids
     * @param cleared the cleared deny entries
     * @param skipped the skipped report
     * @param dropped the dropped stale ids
     * @param droppedPins the dropped version pins
     */
    public ActivationChange(
            ActivationDocument after,
            List<ArtifactRef> applied,
            List<ArtifactRef> cleared,
            SkippedReport skipped,
            List<ArtifactRef> dropped,
            List<PackRef> droppedPins) {
        this(after, applied, cleared, skipped, dropped, droppedPins, List.of());
    }

    /**
     * A change that dropped nothing.
     *
     * @param after the document after the write
     * @param applied the changed ids
     * @param cleared the cleared deny entries
     * @param skipped the skipped report
     */
    public ActivationChange(
            ActivationDocument after, List<ArtifactRef> applied, List<ArtifactRef> cleared, SkippedReport skipped) {
        this(after, applied, cleared, skipped, List.of(), List.of());
    }

    /**
     * The same change, reporting what the write dropped from the stored document before it computed it: the stale ids
     * and the unheld pins.
     *
     * @param drops what {@link StaleIds#drop} dropped
     * @return the change
     */
    public ActivationChange withDropped(StaleIds.Dropped drops) {
        return new ActivationChange(after, applied, cleared, skipped, drops.dropped(), drops.droppedPins(), warnings);
    }

    /**
     * The same change with the warnings the in-force resolution of the document as stored gives: each artifact the write
     * put in ({@code selected} gained it: a selection, or an item of a preset) that a read leaves out of force for want of
     * a target in force (an enhancer), or because an artifact in force overrides it. A removal, a deny entry and any other
     * reason is no warning: it is what the write asked for.
     *
     * @param answered what the in-force resolution answered for {@code after}
     * @param before the document the write started from
     * @return the change with its warnings
     */
    public ActivationChange withWarnings(MergedSnapshot.InForceAnswer answered, ActivationDocument before) {
        Set<ArtifactRef> added = new TreeSet<>(refs(after.selected()));
        added.removeAll(refs(before.selected()));
        List<Warning> found = applied.stream()
                .filter(added::contains)
                .flatMap(ref -> warningOf(ref, answered.states().get(ref)).stream())
                .toList();
        return new ActivationChange(after, applied, cleared, skipped, dropped, droppedPins, found);
    }

    private static Optional<Warning> warningOf(ArtifactRef ref, MergedSnapshot.@Nullable ArtifactState state) {
        if (state == null) {
            return Optional.empty();
        }
        return switch (state.reason()) {
            case ENHANCED_TARGET_NOT_IN_FORCE -> Optional.of(new Warning(Code.ENHANCED_TARGET_NOT_IN_FORCE, ref));
            case OVERRIDDEN -> Optional.of(new Warning(Code.OVERRIDDEN, ref));
            default -> Optional.empty();
        };
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
        Set<ArtifactRef> cleared = new TreeSet<>(refs(before.deactivated()));
        cleared.removeAll(refs(after.deactivated()));
        return new ActivationChange(after, List.copyOf(applied), List.copyOf(cleared), skipped);
    }

    static SortedSet<ArtifactRef> refs(Map<ArtifactKind, SortedSet<String>> lists) {
        SortedSet<ArtifactRef> refs = new TreeSet<>();
        lists.forEach((kind, ids) -> ids.forEach(id -> refs.add(new ArtifactRef(kind, id))));
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
