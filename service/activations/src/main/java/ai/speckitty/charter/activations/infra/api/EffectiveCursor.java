package ai.speckitty.charter.activations.infra.api;

import ai.speckitty.charter.activations.application.EffectiveReadService;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.kernel.domain.ArtifactKind;

/** The page cursor of the effective artifact list: the position of the last artifact of a page and the tag of its set. */
final class EffectiveCursor {

    private EffectiveCursor() {}

    /**
     * The cursor of a position.
     *
     * @param position the last artifact of a page
     * @param setTag the tag of the set the page belongs to
     * @return the cursor
     */
    static String encode(ArtifactRef position, String setTag) {
        return "";
    }

    /**
     * The position a cursor names.
     *
     * @param cursor the cursor, as the request carries it
     * @return where the page continues
     */
    static EffectiveReadService.After decode(String cursor) {
        return new EffectiveReadService.After(new ArtifactRef(ArtifactKind.DIRECTIVE, ""), "", cursor);
    }
}
