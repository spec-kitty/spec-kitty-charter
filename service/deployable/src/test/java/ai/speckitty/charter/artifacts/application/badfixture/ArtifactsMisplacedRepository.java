package ai.speckitty.charter.artifacts.application.badfixture;

/** A repository port in the artifacts application ring: the known-bad of the rule that a port lives in a domain ring. */
public interface ArtifactsMisplacedRepository {

    /**
     * A read the port would offer.
     *
     * @return nothing of note
     */
    String read();
}
