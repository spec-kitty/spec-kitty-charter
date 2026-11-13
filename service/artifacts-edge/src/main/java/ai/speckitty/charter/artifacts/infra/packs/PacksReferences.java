package ai.speckitty.charter.artifacts.infra.packs;

import ai.speckitty.charter.artifacts.application.ReferenceReads;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.ArtifactReadService;
import ai.speckitty.charter.packs.application.WinnerReferences;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The {@link ReferenceReads} over the packs slice: the one class of the artifacts edge that asks it for references, and
 * only its application ring ({@link WinnerReferences}, the pack read of the references checked against a fingerprint)
 * and the domain values its answer carries. It maps each {@link ArtifactReadService.Reference} one for one, in order,
 * to the edge's own value, and the kind of a target the pack read links is the one gate the pack read links by
 * ({@link ArtifactReadService#listed()}): nothing is filtered, added or resolved again here.
 */
@Component
public final class PacksReferences implements ReferenceReads {

    private final WinnerReferences packs;

    /**
     * Reads over the packs slice's references of a winner.
     *
     * @param packs the pack read of the references, checked against a fingerprint
     */
    @Autowired
    public PacksReferences(WinnerReferences packs) {
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    @Override
    public List<Reference> references(PackIdentifier pack, ArtifactKind kind, String id, String expectedFingerprint) {
        return packs.of(pack, kind, id, expectedFingerprint).value().stream()
                .map(PacksReferences::referenceOf)
                .toList();
    }

    /**
     * The edge's value of one reference of the pack read.
     *
     * @param reference the pack read's reference
     * @return the edge's value
     */
    static Reference referenceOf(ArtifactReadService.Reference reference) {
        return new Reference(
                reference.written(),
                reference.relation(),
                reference.urn(),
                reference.holder().map(PacksReferences::holderOf),
                reference.urn().kind().artifactKind().filter(ArtifactReadService.listed()::contains));
    }

    private static Holder holderOf(PackUnderResolution holder) {
        return new Holder(
                holder.configured().id(), holder.descriptor().packId(), holder.descriptor().name());
    }
}
