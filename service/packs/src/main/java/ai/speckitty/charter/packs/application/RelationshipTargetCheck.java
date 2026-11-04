package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;

/**
 * The check a relationship registration makes of its {@code to} before it writes anything: the compose must be able to
 * resolve it for the pack the edge is registered into.
 */
@FunctionalInterface
public interface RelationshipTargetCheck {

    /**
     * Judges one target for the pack it is registered from.
     *
     * @param source the pack the relationship is registered into, whose artifact the edge is authored from
     * @param target the {@code to} of the relationship, as written, bare or pack-qualified
     * @throws CharterRefusal with {@code PACK_REFERENCE_PACK_MISSING} when a qualified target names a pack outside the
     *     source pack's {@code depends_on} closure, with {@code PACK_REFERENCE_UNQUALIFIED} when a bare target only
     *     another pack holds, with {@code PACK_REFERENCE_ARTIFACT_MISSING} when a qualified target names an artifact
     *     the named pack does not hold, or with a composition refusal when the configured packs do not compose
     */
    void require(PackIdentifier source, PackQualifiedReference target);
}
