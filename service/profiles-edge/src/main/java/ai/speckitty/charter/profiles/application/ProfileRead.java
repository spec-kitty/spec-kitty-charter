package ai.speckitty.charter.profiles.application;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import java.util.List;
import java.util.Objects;

/**
 * What the pack-scoped read of one agent profile answers: the stored profile, the revision of its pack, the digest the
 * read verified, the profile's governance and the kinds a client can follow to a pack-scoped read.
 *
 * @param profile the stored profile
 * @param revision the pack revision the profile was read at: the {@code ETag} token, without quotes
 * @param sourceFingerprint the whole-source digest the one verified read agreed on, the answer's
 *     {@code Charter-Source-Fingerprint}; it is never the closure's own digest
 * @param governance the governance entries, in URN order
 * @param servedKinds the kinds the generic artifact route serves, so that the REST adapter links only the entries it can
 *     link, without naming the packs slice that knows them
 */
public record ProfileRead(
        AgentProfile profile,
        String revision,
        String sourceFingerprint,
        List<GovernanceEntry> governance,
        List<ArtifactKind> servedKinds) {

    /**
     * Checks and copies the fields.
     *
     * @param profile the profile
     * @param revision the pack revision
     * @param sourceFingerprint the verified digest
     * @param governance the entries
     * @param servedKinds the kinds of the generic route
     */
    public ProfileRead {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        governance = List.copyOf(Objects.requireNonNull(governance, "governance"));
        servedKinds = List.copyOf(Objects.requireNonNull(servedKinds, "servedKinds"));
    }

    /**
     * Whether an entry has a pack-scoped read to link to: its artifact is held by a pack and is of a kind the generic
     * artifact route serves.
     *
     * @param entry an entry of this answer
     * @return true for a held entry of a served kind
     */
    public boolean linksArtifact(GovernanceEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return entry.held()
                && entry.urn()
                        .kind()
                        .artifactKind()
                        .filter(servedKinds::contains)
                        .isPresent();
    }
}
