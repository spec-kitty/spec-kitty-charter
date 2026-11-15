/**
 * The effective profile's two folds (rulings D5, D6 and D7 of ADR-0078): {@link
 * ai.speckitty.charter.profiles.domain.resolution.ProfileEnhancementMerge} merges a profile with its in-force enhancers,
 * refusing a merge that loses base content ({@link
 * ai.speckitty.charter.profiles.domain.resolution.ProfileEnhancementDropsField}), and {@link
 * ai.speckitty.charter.profiles.domain.resolution.ProfileInheritance} folds a {@code specializes_from} chain root to
 * leaf by per-field replacement. Both read the one table of the schema's top-level fields, {@link
 * ai.speckitty.charter.profiles.domain.resolution.ProfileField}. Plain Java only: which enhancers are in force and the
 * order of the chain are the callers' to decide.
 */
@NullMarked
package ai.speckitty.charter.profiles.domain.resolution;

import org.jspecify.annotations.NullMarked;
