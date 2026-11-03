package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.Optional;

/**
 * A domain-owned port over the packs of one compose: it names the pack a qualified reference relies on and binds a
 * {@link ComponentResolver} to one pack. A walk across packs asks it where a reference points, so every reference
 * resolves to {@code (holding pack, bare URN)} before anything is compared, keyed or visited (ADR-0048 decision 10:
 * a pack is provenance, not identity).
 *
 * <p>An implementation answers from a compose resolved once, for the read it serves.
 */
public interface ComposeResolver {

    /**
     * The pack a qualified reference names.
     *
     * @param name the pack name the qualifier spells, as the pack descriptor declares it
     * @return the pack's identity; empty when the compose holds no pack of that name
     */
    Optional<PackIdentifier> packNamed(String name);

    /**
     * The resolver of one pack's own artifacts.
     *
     * @param pack the pack whose read paths the resolver uses
     * @return a resolver that reads a bare URN from {@code pack}
     */
    ComponentResolver inPack(PackIdentifier pack);
}
