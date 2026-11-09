package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.RegistrationImpact;
import ai.speckitty.charter.packs.application.StagedPackViews;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The packs as the impact check composes them ({@link StagedPackViewFactory} hands them out): the stored packs, read through the ports once each and kept for the
 * length of one check, with the pack being written standing in the place of the stored one (or beside them, when it is
 * new). It implements the three ports a compose reads through, so {@code OfferingReadService} and
 * {@code ClosureQueries} run over it unchanged and no compose rule is copied.
 *
 * <p>The check runs under the registry lock and the pack's own, which every pack write takes, so what is read once is
 * what every closure sees; and a closure's compose reads each pack's files once, not once per closure that holds it.
 * Reads that fail are not kept: the first failure ends the check.
 */
final class StagedPacks implements PackCatalogRepository, PackDescriptorRepository, PackArtifactRepository {

    /** One descriptor read: the descriptor, or the refusal that reading it gave. */
    private record DescriptorRead(Optional<PackDescriptor> descriptor, Optional<CharterRefusal> refusal) {}

    /** What the stored packs gave, shared by the view of the packs as they are and the view with the write in place. */
    private static final class Stored {
        private final PackCatalogRepository catalog;
        private final PackDescriptorRepository descriptors;
        private final PackArtifactRepository artifacts;
        private final Map<PackIdentifier, DescriptorRead> descriptorReads = new HashMap<>();
        private final Map<PackIdentifier, PackCompositionFacts> factReads = new HashMap<>();
        private Optional<List<ConfiguredPack>> configured = Optional.empty();

        Stored(PackCatalogRepository catalog, PackDescriptorRepository descriptors, PackArtifactRepository artifacts) {
            this.catalog = catalog;
            this.descriptors = descriptors;
            this.artifacts = artifacts;
        }

        List<ConfiguredPack> configured() {
            if (configured.isEmpty()) {
                configured = Optional.of(List.copyOf(catalog.configured()));
            }
            return configured.get();
        }

        DescriptorRead descriptor(PackIdentifier pack) {
            return descriptorReads.computeIfAbsent(pack, this::readDescriptor);
        }

        private DescriptorRead readDescriptor(PackIdentifier pack) {
            try {
                return new DescriptorRead(descriptors.find(pack), Optional.empty());
            } catch (CharterRefusal refusal) {
                if (refusal.code() != RefusalCode.PACK_UNREADABLE) {
                    throw refusal;
                }
                return new DescriptorRead(Optional.empty(), Optional.of(refusal));
            }
        }

        PackCompositionFacts facts(PackIdentifier pack) {
            PackCompositionFacts kept = factReads.get(pack);
            if (kept == null) {
                kept = artifacts.compositionFacts(pack);
                factReads.put(pack, kept);
            }
            return kept;
        }
    }

    private final Stored stored;
    private final Optional<RegistrationImpact.Replacement> replacement;

    private StagedPacks(Stored stored, Optional<RegistrationImpact.Replacement> replacement) {
        this.stored = stored;
        this.replacement = replacement;
    }

    /**
     * The stored packs as they are.
     *
     * @param catalog the configured packs
     * @param descriptors their descriptors
     * @param artifacts what the compose reads of each
     * @return the view
     */
    static StagedPacks stored(
            PackCatalogRepository catalog, PackDescriptorRepository descriptors, PackArtifactRepository artifacts) {
        return new StagedPacks(
                new Stored(
                        Objects.requireNonNull(catalog, "catalog"),
                        Objects.requireNonNull(descriptors, "descriptors"),
                        Objects.requireNonNull(artifacts, "artifacts")),
                Optional.empty());
    }

    /**
     * The same stored packs, the written pack in the place of the stored one of its name, or beside them.
     *
     * @param written the pack as it will be
     * @return the view, which shares what this one has read
     */
    StagedPacks with(RegistrationImpact.Replacement written) {
        return new StagedPacks(stored, Optional.of(Objects.requireNonNull(written, "written")));
    }

    /**
     * The three ports of this view, for a compose to read through.
     *
     * @return the view
     */
    StagedPackViews.View view() {
        return new StagedPackViews.View(this, this, this);
    }

    @Override
    public List<ConfiguredPack> configured() {
        List<ConfiguredPack> packs = new ArrayList<>(stored.configured());
        if (replacement.isEmpty()) {
            return packs;
        }
        ConfiguredPack written = replacement.get().configured();
        for (int index = 0; index < packs.size(); index++) {
            if (packs.get(index).id().equals(written.id())) {
                packs.set(index, written);
                return packs;
            }
        }
        packs.add(written);
        return packs;
    }

    @Override
    public Optional<PackDescriptor> find(PackIdentifier pack) {
        if (isWritten(pack)) {
            return Optional.of(replacement.orElseThrow().descriptor());
        }
        DescriptorRead read = stored.descriptor(pack);
        if (read.refusal().isPresent()) {
            throw read.refusal().get();
        }
        return read.descriptor();
    }

    @Override
    public PackDescriptor get(PackIdentifier pack) {
        return find(pack)
                .orElseThrow(() ->
                        new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found."));
    }

    @Override
    public void save(PackIdentifier pack, PackDescriptor descriptor) {
        throw new UnsupportedOperationException("the impact check reads packs and writes none");
    }

    @Override
    public PackCompositionFacts compositionFacts(PackIdentifier pack) {
        return isWritten(pack) ? replacement.orElseThrow().facts() : stored.facts(pack);
    }

    private boolean isWritten(PackIdentifier pack) {
        return replacement
                .filter(written -> written.configured().id().equals(pack))
                .isPresent();
    }
}
