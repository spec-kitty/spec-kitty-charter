package ai.speckitty.charter.activations.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * What of a document its closure no longer satisfies, and the document without it:
 *
 * <ul>
 *   <li>the ids it no longer declares (ADR-0059 amendment A5, operator ruling of 2026-11-06; a pack upgrade removed
 *       them), from {@code selected} and {@code deactivated}, of every kind;
 *   <li>the version pins no pack of the closure holds (B1, decision DM-01M6RN5Y406SB4KA13QYMQ2P8J): the pack stays in
 *       {@code packs}, at the version the service holds, and the pin is named as it was.
 * </ul>
 *
 * <p>Every per-artifact and preset write drops them from the stored document first, and a replace from its body (A4,
 * decision DM-01M6RA7XC5HC8915GZT2R3H83W, after {@link DocumentRules#checkReplace} has refused an id the stored
 * document never named); each reports them in {@code dropped} and {@code droppedPins}, instead of refusing the write.
 * {@code languages} and an unknown pack are left as they are: an unknown pack is still judged by {@link DocumentRules}.
 * A create drops nothing: an unknown id or an unheld pin in its body is refused.
 */
public final class StaleIds {

    private StaleIds() {}

    /**
     * A document without its stale ids and unheld pins, and what was dropped.
     *
     * @param document the document less every stale id and unheld pin; an allow list or a deny list it empties is
     *     dropped, as the per-artifact writes treat them
     * @param dropped the ids dropped, sorted
     * @param droppedPins the pins dropped, each as it was, in the document's pack order
     */
    public record Dropped(ActivationDocument document, List<ArtifactRef> dropped, List<PackRef> droppedPins) {

        /**
         * Checks and copies the fields.
         *
         * @param document the document
         * @param dropped the dropped ids
         * @param droppedPins the dropped pins
         */
        public Dropped {
            Objects.requireNonNull(document, "document");
            dropped = List.copyOf(dropped);
            droppedPins = List.copyOf(droppedPins);
        }
    }

    /**
     * Drops the ids the closure does not hold, and the pins to a version it does not hold, from a document.
     *
     * @param stored the document
     * @param snapshot the closure the document names, resolved
     * @return the document without them, and what was dropped
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
        List<PackRef> unheld = stored.packs().orElseGet(List::of).stream()
                .filter(pack -> !held(pack, snapshot))
                .toList();
        return new Dropped(
                unheld.isEmpty() ? document : withoutPins(document, unheld),
                List.copyOf(new TreeSet<>(dropped)),
                unheld);
    }

    /** Whether the closure holds the pack at its pinned version; an unpinned or unknown pack is not dropped here. */
    private static boolean held(PackRef pack, ClosureSnapshot snapshot) {
        Optional<String> heldVersion = snapshot.pack(pack.packId()).map(ClosureSnapshot.PackEntry::version);
        return pack.version().isEmpty()
                || heldVersion.isEmpty()
                || pack.version().equals(heldVersion);
    }

    /** The document with each unheld pin's pack at the version the service holds, in the same place. */
    private static ActivationDocument withoutPins(ActivationDocument document, List<PackRef> unheld) {
        List<PackRef> packs = document.packs().orElseThrow().stream()
                .map(pack -> unheld.contains(pack) ? PackRef.of(pack.packId()) : pack)
                .toList();
        ActivationDocument.Builder builder = ActivationDocument.builder().packs(packs);
        document.languages().ifPresent(builder::languages);
        document.selected().forEach(builder::selected);
        document.deactivated().forEach(builder::deactivated);
        return builder.build().document();
    }
}
