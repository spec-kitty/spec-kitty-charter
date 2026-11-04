package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.ResolvedEdge;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The offering (ADR-0020 D1): the packs the service is configured with, in the compose's resolved order (dependencies
 * before the packs that need them, then the merge order across levels), each with its configured level and the name
 * and version its descriptor gives. Merging is not applied yet (#10, #11): the offering lists the packs, not a merged
 * result.
 *
 * <p>It is one read verified by {@link ConsistentRead}: the catalog and every descriptor are read between two
 * digests, so the list and its fingerprint name the same inputs. A pack whose descriptor cannot be read refuses the
 * whole offering; it is never partial. A configured pack without a {@code pack.yaml} is no readable canonical pack
 * either (operator ruling of 2026-10-31): it refuses the offering as unreadable, as an invalid one does.
 */
@Service
public final class OfferingReadService {

    private static final String STALE = "The inputs changed while the offering was read; read it again.";

    private final PackCatalogRepository catalog;
    private final PackDescriptorRepository descriptors;
    private final PackArtifactRepository refinements;
    private final ConsistentRead read;

    /**
     * One configured pack as the offering names it.
     *
     * @param id the configured identifier of the pack
     * @param descriptorPackId the {@code pack_id} its descriptor declares, the identity a dependency edge names
     * @param level the level the pack is configured at
     * @param name the name its descriptor gives
     * @param version the version its descriptor gives
     * @param dependencies the dependency edges its descriptor declared, each with the range and the satisfying version
     *     the compose resolved (FR-020); empty when it declares none
     */
    public record OfferingEntry(
            PackIdentifier id,
            String descriptorPackId,
            PackLevel level,
            String name,
            String version,
            List<ResolvedEdge> dependencies) {

        /**
         * An entry.
         *
         * @param id the configured identifier
         * @param descriptorPackId the descriptor's {@code pack_id}
         * @param level the configured level
         * @param name the descriptor's name
         * @param version the descriptor's version
         * @param dependencies the resolved edges the descriptor declares
         */
        public OfferingEntry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(descriptorPackId, "descriptorPackId");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(dependencies, "dependencies");
            dependencies = List.copyOf(dependencies);
        }
    }

    /**
     * A service over the catalog, the descriptors and the refinements.
     *
     * @param catalog the port that lists the configured packs
     * @param descriptors the port that looks each pack's descriptor up
     * @param refinements the port that reads the artifacts each pack's graphs say it changes
     * @param read the verifier that digests the inputs before and after the read
     */
    @Autowired
    public OfferingReadService(
            PackCatalogRepository catalog,
            PackDescriptorRepository descriptors,
            PackArtifactRepository refinements,
            ConsistentRead read) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
        this.refinements = Objects.requireNonNull(refinements, "refinements");
        this.read = Objects.requireNonNull(read, "read");
    }

    /**
     * The configured packs in the merge order, with the digest of the inputs they were read from.
     *
     * @return the entries and the verified fingerprint
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when an input cannot be read, a configured pack's directory
     *     is missing or it has no readable descriptor (none, or one that cannot be read), or with {@code STALE_INPUT}
     *     when the inputs changed while the offering was read
     */
    public ConsistentRead.Verified<List<OfferingEntry>> offering() {
        return read.read(STALE, this::entries);
    }

    /**
     * The offering, in the compose's resolved order: dependencies before the packs that need them, and the merge
     * order across levels. A graph the resolver refuses refuses the offering, so a broken compose is never served
     * partially — two siblings changing one artifact among them, unless a pack of the compose settles it with an
     * {@code overrides} edge (FR-012), and so is a graph reference that breaks the pack-qualification rule (FR-015,
     * FR-017). Each entry carries the dependency edges its descriptor declared, with the range
     * and the satisfying version the compose resolved (FR-020).
     *
     * @return the entries
     */
    private List<OfferingEntry> entries() {
        List<PackUnderResolution> closure = composed();
        Map<String, String> heldVersions = new HashMap<>();
        for (PackUnderResolution pack : closure) {
            heldVersions.put(pack.descriptor().packId(), pack.descriptor().packVersion());
        }
        return closure.stream()
                .map(pack -> new OfferingEntry(
                        pack.configured().id(),
                        pack.descriptor().packId(),
                        pack.configured().level(),
                        pack.descriptor().name(),
                        pack.descriptor().packVersion(),
                        dependenciesOf(pack, heldVersions)))
                .toList();
    }

    /**
     * The edges one pack's descriptor declared, each paired with the target's held version from the closure. The
     * resolver already refused a compose whose declared target is absent or whose range the held version misses, so
     * every declared edge resolves here.
     *
     * @param pack the pack under resolution
     * @param heldVersions the closure's {@code pack_id} to held {@code pack_version}
     * @return the resolved edges, in the descriptor's declared order
     */
    private static List<ResolvedEdge> dependenciesOf(PackUnderResolution pack, Map<String, String> heldVersions) {
        return pack.descriptor().dependencies().stream()
                .map(dependency -> new ResolvedEdge(
                        pack.descriptor().packId(),
                        dependency.packId(),
                        dependency.range(),
                        Objects.requireNonNull(
                                heldVersions.get(dependency.packId()), "the resolver guarantees the target is held")))
                .toList();
    }

    /**
     * The compose, resolved now and not verified: for a read that already runs inside its own {@link ConsistentRead},
     * so it resolves the compose once for itself and never nests a second verified read per reference or per hop.
     *
     * @return the compose's closure, dependencies first, each pack once
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when an input cannot be read or a configured pack has no readable
     *     descriptor, or with a composition refusal when the graph cannot be served
     */
    public List<PackUnderResolution> composed() {
        try {
            Map<String, PackCompositionFacts> facts = new HashMap<>();
            List<PackUnderResolution> closure = PackCompositionResolver.resolve(catalog.configured().stream()
                    .map(pack -> {
                        PackDescriptor descriptor = descriptorOf(pack);
                        PackCompositionFacts read = refinements.compositionFacts(pack.id());
                        facts.put(pack.id().value(), read);
                        return new PackUnderResolution(pack, descriptor, read.changed(), read.defined());
                    })
                    .toList());
            for (PackUnderResolution pack : closure) {
                PackCompositionResolver.checkReferences(
                        closure,
                        pack.descriptor().packId(),
                        Objects.requireNonNull(facts.get(pack.configured().id().value()), "read above")
                                .references()
                                .stream()
                                .map(written -> PackCompositionResolver.resolveReference(
                                        closure, pack.configured().id(), PackQualifiedReference.parse(written)))
                                .toList());
            }
            return closure;
        } catch (PackCompositionResolver.Refusal refusal) {
            throw new CharterRefusal(refusal.failure().code(), refusal.detail(), refusal.packs());
        }
    }

    /**
     * The pack's descriptor, judged by the descriptor rules validate mode runs: a {@code requires_core} or a
     * {@code depends_on} range that is no PEP 440 specifier set makes the descriptor unreadable here too, so a
     * malformed range answers {@code pack_unreadable} on the offering as it does on the load path, and never reaches
     * the resolver.
     */
    private PackDescriptor descriptorOf(ConfiguredPack pack) {
        PackDescriptor descriptor = descriptors
                .find(pack.id())
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.PACK_UNREADABLE, "pack " + pack.id().value() + " has no pack.yaml"));
        PackPolicy.descriptorFindings(pack.id().value(), descriptor).stream()
                .findFirst()
                .ifPresent(finding -> {
                    throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, finding.detail());
                });
        return descriptor;
    }
}
