package ai.speckitty.charter.packvalidation.roundtrip;

import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;

/**
 * The production round trip of one pack, as the pack-set check asks for it (#478, FR-004, T012): read, domain, write,
 * read back, judging nothing. The validation edge cannot name the flow ({@code packs.infra.storage.PackRoundTrip}), so a
 * composition root supplies it: pack-check's main runs it into a temporary target it deletes after, and a test runs it
 * the same way. {@link WholePackRoundTripRule} judges what it returns.
 *
 * <p>The boundary of its failures (#478, M2): a refusal of the pack itself, a {@link
 * ai.speckitty.charter.kernel.domain.CharterRefusal} such as a {@code pack.yaml} that cannot be read, is the pack's
 * result, and the pack-set check records it for that pack. Only a failure outside the pack read, such as an {@link
 * java.io.UncheckedIOException} from the temporary target, propagates out of the check.
 */
@FunctionalInterface
public interface RoundTripping {

    /**
     * Round-trips one pack.
     *
     * @param root the pack to round-trip
     * @return what the flow observed
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal when the flow refuses the pack
     * @throws java.io.UncheckedIOException when the round trip fails outside the pack read
     */
    PackRoundTripResult run(PackRoot root);
}
