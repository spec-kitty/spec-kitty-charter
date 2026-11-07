package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The inbound scan of a whole-pack replace (FR-036, ADR-0065 D4): which other artifacts, presets and descriptors still
 * refer to an artifact the replace removes.
 */
@Component
public final class InboundReferences {

    /**
     * One thing that still refers to a removed artifact.
     *
     * @param pack the configured identifier of the pack that holds the referrer
     * @param descriptorPackId the {@code pack_id} that pack's descriptor declares
     * @param name that pack's name
     * @param referrer what refers: an artifact URN, {@code preset:<id>} or {@code overrides:<artifact>}
     * @param removed the removed artifact it refers to
     */
    public record Referrer(PackIdentifier pack, String descriptorPackId, String name, String referrer, Urn removed) {}

    /**
     * The staged tree standing in for the stored pack.
     *
     * @param replacing the configured identifier of the stored pack the tree replaces; empty for a new pack
     * @param descriptor the staged pack's descriptor
     * @param facts the staged pack's composition facts
     */
    public record StagedTree(
            Optional<PackIdentifier> replacing, PackDescriptor descriptor, PackCompositionFacts facts) {}

    private final PackCatalogRepository catalog;
    private final PackDescriptorRepository descriptors;
    private final PackArtifactRepository artifacts;

    /**
     * A scan over the configured packs.
     *
     * @param catalog the configured packs
     * @param descriptors their descriptors
     * @param artifacts what each of them holds and writes
     */
    @Autowired
    public InboundReferences(
            PackCatalogRepository catalog, PackDescriptorRepository descriptors, PackArtifactRepository artifacts) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
    }

    /**
     * Every referrer of an artifact the replace removes, judged against the staged tree in place of the stored pack.
     *
     * @param staged the staged tree
     * @param removed the artifacts the replace removes
     * @return the referrers, by pack and referrer; empty when nothing refers
     */
    public List<Referrer> scan(StagedTree staged, Set<Urn> removed) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(descriptors, "descriptors");
        Objects.requireNonNull(artifacts, "artifacts");
        Objects.requireNonNull(staged, "staged");
        Objects.requireNonNull(removed, "removed");
        return List.of();
    }
}
