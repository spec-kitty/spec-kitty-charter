package ai.speckitty.charter.packvalidation.roundtrip;

import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;

/**
 * The production round trip of one pack, as the pack-set check asks for it (#478, FR-004, T012): read, domain, write,
 * read back, judging nothing. The validation edge cannot name the flow ({@code packs.infra.storage.PackRoundTrip}), so a
 * composition root supplies it: pack-check's main runs it into a temporary target it deletes after, and a test runs it
 * the same way. {@link WholePackRoundTripRule} judges what it returns.
 */
@FunctionalInterface
public interface RoundTripping {

    /**
     * Round-trips one pack.
     *
     * @param root the pack to round-trip
     * @return what the flow observed
     */
    PackRoundTripResult run(PackRoot root);
}
