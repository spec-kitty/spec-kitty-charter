/**
 * The whole-pack round trip's verdict (ADR-0036; ADR-0075 decision 8, as amended by operator ruling 2 of 2026-11-15): a
 * rule over the {@link ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult} the production flow returns
 * ({@code packs.infra.storage.PackRoundTrip}). It holds equality of meaning ({@link
 * ai.speckitty.charter.packvalidation.roundtrip.MeaningComparator}), the per-file observation ({@link
 * ai.speckitty.charter.packvalidation.roundtrip.RoundTripObservation}), the expected baseline ({@link
 * ai.speckitty.charter.packvalidation.roundtrip.WholePackExpectation}), the report and the rule itself ({@link
 * ai.speckitty.charter.packvalidation.roundtrip.WholePackRoundTripRule}). It reads no pack file: the flow read both sides.
 */
@NullMarked
package ai.speckitty.charter.packvalidation.roundtrip;

import org.jspecify.annotations.NullMarked;
