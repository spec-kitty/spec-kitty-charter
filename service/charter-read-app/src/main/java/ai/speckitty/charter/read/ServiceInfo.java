package ai.speckitty.charter.read;

/**
 * What the service says about itself: which worktree it serves and the inputs it answers from.
 *
 * @param worktree the opaque id of the worktree the service serves, never a host path
 * @param sourceFingerprint the digest of the inputs the answers depend on, as the service verified it: read twice,
 *     with the same value both times ({@link ai.speckitty.charter.domain.PackSources#fingerprint()})
 */
public record ServiceInfo(String worktree, String sourceFingerprint) {}
