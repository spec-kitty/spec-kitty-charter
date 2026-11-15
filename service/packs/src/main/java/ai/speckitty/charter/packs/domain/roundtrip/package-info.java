/**
 * What the whole-pack round trip observed (ADR-0036; ADR-0075 decision 8, as amended by operator ruling 2 of
 * 2026-11-15): every file of a pack read into the domain, written to an empty directory and read back, with both sides
 * read by the pack readers' own settings. The flow that produces it is storage ({@code infra.storage.PackRoundTrip});
 * the verdict over it, equality of meaning against an expected baseline, is {@code pack-validation}'s. No rule reads a
 * file again: it judges these readings.
 */
@NullMarked
package ai.speckitty.charter.packs.domain.roundtrip;

import org.jspecify.annotations.NullMarked;
