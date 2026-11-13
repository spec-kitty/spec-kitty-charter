package ai.speckitty.charter.artifacts.domain.badfixture;

/** A repository port as a domain ring of the artifacts edge would declare it, for the known-bad that implements it. */
public interface ArtifactsStandInRepository {

    /**
     * A read the port would offer.
     *
     * @return nothing of note
     */
    String read();
}
