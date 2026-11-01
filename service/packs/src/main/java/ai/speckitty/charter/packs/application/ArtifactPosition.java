package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.Comparator;
import java.util.Objects;

/**
 * Where an artifact stands in a pack's generic list: its served kind and its id. A page cursor names one, and the
 * next page holds the artifacts strictly after it, whether or not an artifact still stands there.
 *
 * @param kind a served kind ({@link ArtifactReadService#SERVED})
 * @param id the artifact's id within its kind
 */
public record ArtifactPosition(ArtifactKind kind, String id) {

    /**
     * The list order: by the served-kind order ({@link ArtifactReadService#SERVED}), then by id in code-point order.
     * Code points, not UTF-16 units: {@link String#compareTo} would put a supplementary character before
     * {@code U+FB01}.
     */
    public static final Comparator<ArtifactPosition> ORDER = Comparator.<ArtifactPosition>comparingInt(
                    position -> ArtifactReadService.SERVED.indexOf(position.kind()))
            .thenComparing(ArtifactPosition::id, ArtifactPosition::compareCodePoints);

    /**
     * A position of a served kind.
     *
     * @param kind a served kind
     * @param id the artifact's id within its kind
     * @throws IllegalArgumentException when {@code kind} is not served
     */
    public ArtifactPosition {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        if (!ArtifactReadService.SERVED.contains(kind)) {
            throw new IllegalArgumentException("not a served kind: " + kind.token());
        }
    }

    private static int compareCodePoints(String left, String right) {
        int i = 0;
        int j = 0;
        while (i < left.length() && j < right.length()) {
            int l = left.codePointAt(i);
            int r = right.codePointAt(j);
            if (l != r) {
                return Integer.compare(l, r);
            }
            i += Character.charCount(l);
            j += Character.charCount(r);
        }
        return Boolean.compare(i < left.length(), j < right.length());
    }
}
