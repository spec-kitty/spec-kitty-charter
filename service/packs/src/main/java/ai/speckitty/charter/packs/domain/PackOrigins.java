package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.Origin;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * How the packs domain names the element a reason is about (#614, ADR-0082 decision 4, operator ruling R-o): the kernel's
 * {@link Origin} is opaque, and this is the one place its identifier is spelled for a pack. It is a pack id, or a pack
 * id and a URN written through {@link PackQualifiedReference}, as a pack-qualified reference writes them
 * ({@code acme::directive:DIR-1}). A check fills it with the element it already knows; it never invents one, and a reason
 * about no element is {@link Origin#NONE}.
 */
public final class PackOrigins {

    private PackOrigins() {}

    /**
     * A pack as an element.
     *
     * @param pack the pack's configured identifier
     * @return its origin: the pack id
     */
    public static Origin of(PackIdentifier pack) {
        return new Origin(Objects.requireNonNull(pack, "pack").value());
    }

    /**
     * An artifact a check judges in a pack, or that a pack names.
     *
     * @param pack the pack's configured identifier
     * @param artifact the artifact's URN
     * @return its origin: the pack id and the URN, pack-qualified
     */
    public static Origin of(PackIdentifier pack, Urn artifact) {
        Objects.requireNonNull(artifact, "artifact");
        return new Origin(new PackQualifiedReference(Optional.of(of(pack).id()), artifact.toString()).toString());
    }

    /**
     * The pack that broke a rule first: the first of the packs a fault names.
     *
     * @param packs the packs a fault names, the one that broke the rule first
     * @return the first pack's origin; {@link Origin#NONE} when the fault names no pack
     */
    public static Origin firstOf(List<RefusalPack> packs) {
        return packs.isEmpty() ? Origin.NONE : of(packs.getFirst().packId());
    }
}
