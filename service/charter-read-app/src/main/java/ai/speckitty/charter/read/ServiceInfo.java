package ai.speckitty.charter.read;

/**
 * What the service says about itself: which worktree it serves and the files it answers from.
 *
 * @param worktree the opaque id of the worktree the service serves, never a host path
 * @param sourceFingerprint the digest of the pack files the offering is loaded from
 *     ({@link ai.speckitty.charter.domain.PackSources#fingerprint()})
 */
public record ServiceInfo(String worktree, String sourceFingerprint) {}
