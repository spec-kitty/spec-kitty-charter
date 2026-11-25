package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.Optional;

/**
 * Reads the agent profile of an id from the pack that holds the version in force: what the own-closure rules read of a
 * profile ({@link PackRulesets#ownClosure}). The caller reads the written pack's profile from the write, every other
 * pack's from the store.
 */
@FunctionalInterface
public interface ProfileReader {

    /**
     * The profile a pack holds.
     *
     * @param holder the pack that holds the winning version
     * @param id the profile's id
     * @return the entity, when the pack holds and can read it
     */
    Optional<CharterArtifact> read(PackIdentifier holder, String id);
}
