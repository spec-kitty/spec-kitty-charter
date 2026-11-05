package ai.speckitty.charter.activations.application;

/**
 * The one port for every question the activations slice asks the packs slice: which packs, artifacts, mission types,
 * locks and presets a closure holds. Only the adapter in {@code activations.infra.packs} implements it, so the slice
 * names {@code packs} in one place. A request calls it once (NFR-007), so one answer is consistent with itself.
 *
 * <p>It declares no method yet: its answer is an activation-domain value that the closure read adds with the method
 * (WP09), so no placeholder signature leaks into its callers before then.
 */
public interface AvailablePacks {}
