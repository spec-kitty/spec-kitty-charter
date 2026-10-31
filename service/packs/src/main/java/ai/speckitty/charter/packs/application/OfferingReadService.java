package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The offering (ADR-0020 D1): the packs the service is configured with, in the merge order, each with its configured
 * level and the name and version its descriptor gives. Merging is not applied yet (#10, #11): the offering lists the
 * packs, not a merged result.
 *
 * <p>It is one read verified by {@link ConsistentRead}: the catalog and every descriptor are read between two
 * digests, so the list and its fingerprint name the same inputs. A pack whose descriptor cannot be read refuses the
 * whole offering; it is never partial. A configured pack without a {@code pack.yaml} is no readable canonical pack
 * either (operator ruling of 2026-10-31): it refuses the offering as unreadable, as an invalid one does.
 */
@Service
public final class OfferingReadService {

    /**
     * The merge order: by configured level in {@link PackLevel}'s declaration order (built-in, org, project), then by
     * pack id in code-point order, so a supplementary character sorts after {@code U+FB01}, as no UTF-16 order does.
     */
    static final Comparator<OfferingEntry> ORDER = Comparator.comparing(OfferingEntry::level)
            .thenComparing(entry -> entry.id().value().codePoints().toArray(), Arrays::compare);

    private static final String STALE = "The inputs changed while the offering was read; read it again.";

    private final PackCatalogRepository catalog;
    private final PackDescriptorRepository descriptors;
    private final ConsistentRead read;

    /**
     * One configured pack as the offering names it.
     *
     * @param id the configured identifier of the pack
     * @param level the level the pack is configured at
     * @param name the name its descriptor gives
     * @param version the version its descriptor gives
     */
    public record OfferingEntry(PackIdentifier id, PackLevel level, String name, String version) {

        /**
         * An entry.
         *
         * @param id the configured identifier
         * @param level the configured level
         * @param name the descriptor's name
         * @param version the descriptor's version
         */
        public OfferingEntry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(version, "version");
        }
    }

    /**
     * A service over the catalog and the descriptors.
     *
     * @param catalog the port that lists the configured packs
     * @param descriptors the port that looks each pack's descriptor up
     * @param read the verifier that digests the inputs before and after the read
     */
    @Autowired
    public OfferingReadService(
            PackCatalogRepository catalog, PackDescriptorRepository descriptors, ConsistentRead read) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.descriptors = Objects.requireNonNull(descriptors, "descriptors");
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
        return read.read(
                STALE,
                () -> catalog.configured().stream()
                        .map(this::entry)
                        .sorted(ORDER)
                        .toList());
    }

    private OfferingEntry entry(ConfiguredPack pack) {
        PackDescriptor descriptor = descriptors
                .find(pack.id())
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.PACK_UNREADABLE, "pack " + pack.id().value() + " has no pack.yaml"));
        return new OfferingEntry(pack.id(), pack.level(), descriptor.name(), descriptor.packVersion());
    }
}
