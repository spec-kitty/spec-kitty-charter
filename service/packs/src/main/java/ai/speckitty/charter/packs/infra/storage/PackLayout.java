package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Map;
import java.util.Objects;

/**
 * Where each artifact of a pack sits, as pack-relative paths (ADR-0036 decision 5). A file's place is how a pack is
 * authored, not what the artifact means, so it is kept here beside the aggregate instead of on the entities. An
 * artifact with no recorded place goes to {@code <plural>/<id>.<kind>.yaml}.
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
     * @return its recorded path, or {@code <plural>/<id>.<kind>.yaml} when none is recorded
     */
    public String pathOf(CharterArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact");
        String recorded = paths.get(artifact.urn());
        return recorded != null
                ? recorded
                : artifact.kind().plural() + "/" + artifact.localId() + "." + artifact.kind().token() + ".yaml";
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
