package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The ids of a stored document its closure no longer declares (ADR-0059 amendment A5, operator ruling of 2026-11-06):
 * a pack upgrade removed them. Every per-artifact and preset write drops them first, from {@code selected},
 * {@code deactivated} and {@code missionTypes}, and reports them in {@code ActivationChange.dropped}, instead of
 * refusing the write until a full replace. {@code packs}, {@code activatedKinds} and {@code languages} are left as they
 * are: an unknown pack or a pinned version is still judged by {@link DocumentRules}. A create or a replace is not
 * dropped from: an unknown id in a body the client sent is refused.
 */
public final class StaleIds {

    private StaleIds() {}

    /**
     * A document without its stale ids, and the ids dropped.
     *
     * @param document the document less every stale id; a list it empties stays, as {@code []}, and a deny list it
     *     empties is dropped, as the per-artifact writes treat them
     * @param dropped the ids dropped, sorted
     */
    public record Dropped(ActivationDocument document, List<ArtifactRef> dropped) {

        /**
         * Checks and copies the fields.
         *
         * @param document the document
         * @param dropped the dropped ids
         */
        public Dropped {
            Objects.requireNonNull(document, "document");
            dropped = List.copyOf(dropped);
        }
    }

    /**
     * Drops the ids the closure does not hold from a stored document.
     *
     * @param stored the document as stored
     * @param snapshot the closure the document names, resolved
     * @return the document without the stale ids, and those ids
     */
    public static Dropped drop(ActivationDocument stored, ClosureSnapshot snapshot) {
        List<ArtifactRef> dropped = new ArrayList<>();
        ActivationDocument document = stored;
        for (ArtifactRef ref : ActivationChange.refs(stored.selected())) {
            if (!snapshot.held(ref.kind()).contains(ref.id())) {
                document = document.withoutSelected(ref.kind(), ref.id());
                dropped.add(ref);
            }
        }
        for (ArtifactRef ref : ActivationChange.refs(stored.deactivated())) {
            if (!snapshot.held(ref.kind()).contains(ref.id())) {
                document = document.withoutDeny(ref.kind(), ref.id());
                dropped.add(ref);
            }
        }
        for (String id : stored.missionTypes().orElseGet(TreeSet::new)) {
            if (!snapshot.missionTypes().contains(id)) {
                document = document.withoutSelected(ArtifactKind.MISSION_TYPE, id);
                dropped.add(new ArtifactRef(ArtifactKind.MISSION_TYPE, id));
            }
        }
        return new Dropped(document, List.copyOf(new TreeSet<>(dropped)));
    }
}
