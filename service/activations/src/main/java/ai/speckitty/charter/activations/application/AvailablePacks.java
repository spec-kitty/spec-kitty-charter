package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import java.util.List;
import java.util.Optional;

/**
 * The one port for every question the activations slice asks the packs slice: which packs, artifacts, mission types,
 * locks and presets a closure holds. Only the adapter in {@code activations.infra.packs} implements it, so the slice
 * names {@code packs} in one place. A request calls it once (NFR-007), so one answer is consistent with itself: a write that
 * walks the closure keeps one {@link PacksSession} for the whole request.
 */
public interface AvailablePacks {

    /**
     * One verified read of a composition closure, kept for the whole request: the snapshot and every walk of the
     * cascade answer from it (NFR-007).
     *
     * @param packIds the configured ids of the packs the closure is rooted at, in the document's order; empty for every
     *     configured pack (an activation without {@code packs})
     * @return the session; its snapshot is {@linkplain ClosureSnapshot#unresolved unresolved}, naming the ids, when a
     *     root names no configured pack
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with the refusal of the compose, unchanged, when the
     *     closure cannot be read ({@code pack_unreadable}), does not compose (a composition code) or changed during the
     *     read ({@code stale_input})
     */
    PacksSession session(Optional<List<String>> packIds);

    /**
     * One verified read of a composition closure, for a use case that walks nothing: the snapshot of a
     * {@linkplain #session session}.
     *
     * @param packIds the configured ids of the packs the closure is rooted at; empty for every configured pack
     * @return the closure; {@linkplain ClosureSnapshot#unresolved unresolved}, naming the ids, when a root names no
     *     configured pack
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal as {@link #session} does
     */
    default ClosureSnapshot snapshot(Optional<List<String>> packIds) {
        return session(packIds).snapshot();
    }
}
