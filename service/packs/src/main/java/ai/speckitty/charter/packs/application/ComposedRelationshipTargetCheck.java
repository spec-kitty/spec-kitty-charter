package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Judges a registered relationship's {@code to} by the compose's one rule for which pack a reference means
 * ({@link CompositeComponentResolver.Composed#holder}), the rule the offering and the reference reads apply to what a
 * pack writes, so a registration cannot write an edge that the next read of the offering would refuse.
 */
@Service
public final class ComposedRelationshipTargetCheck implements RelationshipTargetCheck {

    private final OfferingReadService offerings;

    /**
     * A check over the compose.
     *
     * @param offerings the offering read that resolves the compose
     */
    @Autowired
    public ComposedRelationshipTargetCheck(OfferingReadService offerings) {
        this.offerings = Objects.requireNonNull(offerings, "offerings");
    }

    @Override
    public void require(PackIdentifier source, PackQualifiedReference target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        CompositeComponentResolver.holderIn(offerings.composed(), target, source);
    }
}
