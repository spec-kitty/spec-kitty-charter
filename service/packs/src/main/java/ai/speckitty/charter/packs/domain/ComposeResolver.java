package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import java.util.Optional;

/**
 * A domain-owned port over the packs of one compose: it names the pack a reference means and binds a
 * {@link ComponentResolver} to one pack. A walk across packs asks it where a reference points, so every reference
 * resolves to {@code (holding pack, bare URN)} before anything is compared, keyed or visited (ADR-0048 decision 10:
 * a pack is provenance, not identity).
 *
 * <p>An implementation answers from a compose resolved once, for the read it serves, and decides "which pack does this
 * reference mean" by the compose's one rule ({@link PackCompositionResolver#holderOf}), never by a rule of its own.
 *
 * <p>{@link ai.speckitty.charter.packs.application.GraphWalk} takes it; the one implementation is the
 * {@link ai.speckitty.charter.packs.application.EdgeIndex}.
 */
public interface ComposeResolver {

    /**
     * The pack that holds what a qualified reference names, judged by the compose's closure rule.
     *
     * @param reference the reference as the artifact writes it
     * @param referringPack the pack whose artifact writes it
     * @return the holding pack's identity; empty when the compose holds none
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal when the compose's reference rule refuses the
     *     reference, for example a qualifier naming a pack outside the referring pack's {@code depends_on} closure
     */
    Optional<PackIdentifier> holdingPack(PackQualifiedReference reference, PackIdentifier referringPack);

    /**
     * The resolver of one pack's own artifacts.
     *
     * @param pack the pack whose read paths the resolver uses
     * @return a resolver that reads a bare URN from {@code pack}
     */
    ComponentResolver inPack(PackIdentifier pack);
}
