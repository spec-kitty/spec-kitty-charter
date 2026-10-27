package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Map;
import java.util.Objects;

/**
 * Where each artifact of a pack sits, as pack-relative paths (ADR-0037 decision 5). A file's place is how a pack is
 * authored, not what the artifact means, so it is kept here beside the aggregate instead of on the entities. An
 * artifact with no recorded place goes where a registration creates one: its kind's
 * {@link KindBinding#fileName file name} directly in the pack directory.
 */
public final class PackLayout {

    private final Map<Urn, String> paths;

    /**
     * A layout over recorded places.
     *
     * @param paths each artifact's pack-relative, {@code /}-separated path, by URN
     */
    PackLayout(Map<Urn, String> paths) {
        this.paths = Map.copyOf(paths);
    }

    /**
     * The layout that records nothing: every artifact goes to its canonical place.
     *
     * @return an empty layout
     */
    public static PackLayout canonical() {
        return new PackLayout(Map.of());
    }

    /**
     * Where an artifact is written.
     *
     * @param artifact the artifact
     * @return its recorded path, or its kind's file name in the pack directory when none is recorded
     * @throws IllegalArgumentException when no place is recorded and the kind is not stored
     */
    public String pathOf(CharterArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact");
        String recorded = paths.get(artifact.urn());
        if (recorded != null) {
            return recorded;
        }
        return KindBindings.of(artifact.kind())
                .orElseThrow(() -> new IllegalArgumentException("cannot persist a " + artifact.kind() + " artefact"))
                .fileName(artifact.localId());
    }

    /**
     * The recorded places.
     *
     * @return an immutable map from URN to pack-relative path
     */
    public Map<Urn, String> paths() {
        return paths;
    }
}
