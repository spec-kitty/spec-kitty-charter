package ai.speckitty.charter.profiles.domain.sharedfixture;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import java.util.List;
import java.util.Optional;

/** An artifact of another slice, implemented from the kernel alone; it has no schema version. */
public final class ProfileArtifact implements CharterArtifact {

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.AGENT_PROFILE;
    }

    @Override
    public PackIdentifier packId() {
        return new PackIdentifier("fixture");
    }

    @Override
    public String localId() {
        return "fixture-profile";
    }

    @Override
    public Optional<String> schemaVersion() {
        return Optional.empty();
    }

    @Override
    public String label() {
        return "Fixture profile";
    }

    @Override
    public Optional<String> description() {
        return Optional.empty();
    }

    @Override
    public List<Relationship> relationships() {
        return List.of();
    }
}
