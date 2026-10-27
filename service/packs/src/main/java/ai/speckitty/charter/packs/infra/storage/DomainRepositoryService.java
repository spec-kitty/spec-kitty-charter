package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.DirectiveRepository;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.ParadigmRepository;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.StyleguideRepository;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.TacticRepository;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.domain.ToolguideRepository;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The edge concretization of the pack persistence ports: one service that realizes {@link TacticRepository},
 * {@link PackDescriptorRepository} and {@link PackRepository} over the filesystem.
 * The Spring stereotype lives only here; the ports in the domain stay plain interfaces, and a database-backed
 * sibling would carry its own stereotype.
 *
 * <p>It serves every configured pack through an explicit {@link PackIdentifier}. The identifier is resolved to a pack
 * directory through the configured {@link Locations}: a location whose configured name equals the identifier wins,
 * otherwise the location whose {@code pack.yaml} declares that {@code pack_id}. A directory name is therefore never
 * assumed to be the id. An identifier that no location resolves is refused as an unreadable pack.
 *
 * <p>The persistence shapes ({@link TacticDso}, {@link PackDescriptorDso}) stay inside this package. Each kind is
 * mapped to and from its domain entity by its {@link KindBinding}, and assembled and committed through the
 * {@link KindBindings} registry (ADR-0036); only domain types leave the service.
 */
@Service
public final class DomainRepositoryService
        implements TacticRepository,
                DirectiveRepository,
                StyleguideRepository,
                ToolguideRepository,
                ParadigmRepository,
                PackDescriptorRepository,
                PackRepository {

    /** The seam that supplies the configured pack locations, read on every call so a reconfiguration is seen. */
    @FunctionalInterface
    public interface Locations {
        /**
         * The configured packs.
         *
         * @return every configured pack location
         */
        List<PackLocation> all();
    }

    private final Locations locations;
    private final SerializationStrategy strategy;

    /**
     * A service over the injected format.
     *
     * @param locations the configured pack locations
     * @param strategy the serialization format
     */
    @Autowired
    public DomainRepositoryService(Locations locations, SerializationStrategy strategy) {
        this.locations = Objects.requireNonNull(locations, "locations");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    // ---- the per-kind repositories, all through the kind registry ----------------------------------------------

    @Override
    public void save(Tactic tactic) {
        saveArtifact(tactic);
    }

    @Override
    public Optional<Tactic> load(PackIdentifier pack, TacticIdentifier tactic) {
        return loadOne(pack, TacticBinding.INSTANCE, tactic.value());
    }

    @Override
    public void save(Directive directive) {
        saveArtifact(directive);
    }

    @Override
    public Optional<Directive> load(PackIdentifier pack, DirectiveIdentifier directive) {
        return loadOne(pack, DirectiveBinding.INSTANCE, directive.value());
    }

    @Override
    public void save(Styleguide styleguide) {
        saveArtifact(styleguide);
    }

    @Override
    public Optional<Styleguide> load(PackIdentifier pack, StyleguideIdentifier styleguide) {
        return loadOne(pack, StyleguideBinding.INSTANCE, styleguide.value());
    }

    @Override
    public void save(Toolguide toolguide) {
        saveArtifact(toolguide);
    }

    @Override
    public Optional<Toolguide> load(PackIdentifier pack, ToolguideIdentifier toolguide) {
        return loadOne(pack, ToolguideBinding.INSTANCE, toolguide.value());
    }

    @Override
    public void save(Paradigm paradigm) {
        saveArtifact(paradigm);
    }

    @Override
    public Optional<Paradigm> load(PackIdentifier pack, ParadigmIdentifier paradigm) {
        return loadOne(pack, ParadigmBinding.INSTANCE, paradigm.value());
    }

    private void saveArtifact(CharterArtifact artifact) {
        Resolved pack = resolve(artifact.packId());
        writeArtifact(pack.store, pack.key, artifact);
    }

    private <D, E extends CharterArtifact> Optional<E> loadOne(
            PackIdentifier pack, KindBinding<D, E> kind, String elementId) {
        Resolved resolved = resolve(pack);
        return resolved.store.readOne(resolved.key, kind, elementId).flatMap(dso -> entity(pack, kind, dso));
    }

    // ---- PackDescriptorRepository --------------------------------------------------------------------------------

    @Override
    public PackDescriptor get(PackIdentifier pack) {
        Resolved resolved = resolve(pack);
        return resolved.store.readDescriptor(resolved.key).toDescriptor();
    }

    @Override
    public void save(PackIdentifier pack, PackDescriptor descriptor) {
        Resolved resolved = resolve(pack);
        resolved.store.writeDescriptor(resolved.key, PackDescriptorDso.of(descriptor));
    }

    // ---- PackRepository --------------------------------------------------------------------

    @Override
    public Pack load(PackIdentifier pack) {
        return assemble(pack);
    }

    @Override
    public List<Tactic> tacticsOf(PackIdentifier pack) {
        Resolved resolved = resolve(pack);
        return resolved.store.readAll(resolved.key, TacticBinding.INSTANCE).stream()
                .flatMap(dso -> entity(pack, TacticBinding.INSTANCE, dso).stream())
                .toList();
    }

    /**
     * Writes one artefact into {@code store} under {@code key}, through its kind's binding: a known id replaces its
     * file, an unknown id creates {@code <id>.<kind>.yaml}. The {@link PackCommitStore} calls it to stage the one
     * artefact a registration added or replaced before the atomic directory swap, leaving the descriptor and every
     * sibling byte-identical; the mapping stays in the binding so the commit path and the per-child saves cannot drift.
     *
     * @param store the store to write through (for a commit, one rooted at the staging directory's parent)
     * @param key the directory-name key the store addresses the pack by
     * @param artifact the artefact to write
     * @throws IllegalArgumentException when {@code artifact} is of a kind this store does not persist
     */
    static void writeArtifact(FilesystemPackStore store, PackIdentifier key, CharterArtifact artifact) {
        KindBinding<?, ?> kind = KindBindings.of(artifact.kind())
                .orElseThrow(() -> new IllegalArgumentException("cannot persist a " + artifact.kind() + " artefact"));
        write(store, key, kind, artifact);
    }

    private static <D> void write(
            FilesystemPackStore store, PackIdentifier key, KindBinding<D, ?> kind, CharterArtifact artifact) {
        store.write(key, kind, kind.dsoOf(artifact));
    }

    private Pack assemble(PackIdentifier pack) {
        Resolved resolved = resolve(pack);
        FilesystemPackStore.PackRead read = resolved.store.read(resolved.key);
        Pack.Builder builder = Pack.builder().id(pack).descriptor(read.descriptor().toDescriptor());
        read.violations().forEach(builder::violation);
        for (KindBinding<?, ?> kind : KindBindings.all()) {
            collect(pack, kind, read, builder, new HashMap<>());
        }
        return builder.build();
    }

    /**
     * Adds each readable element of one kind to the aggregate, failing closed: a domain-invalid element is recorded as
     * a violation, named by its id, rather than aborting assembly. Each added element's file is put in {@code places}
     * under its URN.
     */
    static <D, E extends CharterArtifact> void collect(
            PackIdentifier pack,
            KindBinding<D, E> kind,
            FilesystemPackStore.PackRead read,
            Pack.Builder builder,
            Map<Urn, String> places) {
        for (FilesystemPackStore.Stored<D> stored : read.elements(kind)) {
            try {
                E entity = kind.toEntity(pack, stored.dso());
                Urn urn = entity.urn();
                builder.artifact(entity);
                places.put(urn, stored.file());
            } catch (ConstraintBreachException | IllegalArgumentException breach) {
                builder.violation(new PackElementViolation(
                        kind.idOf(stored.dso()), "domain", String.valueOf(breach.getMessage())));
            }
        }
    }

    // ---- identifier to directory ---------------------------------------------------------------------------------

    /** A pack's store and the directory-name key the store addresses it by. */
    private record Resolved(FilesystemPackStore store, PackIdentifier key) {}

    private Resolved resolve(PackIdentifier pack) {
        List<PackLocation> configured = locations.all();
        for (PackLocation location : configured) {
            if (location.name().equals(pack.value())) {
                return over(location);
            }
        }
        for (PackLocation location : configured) {
            Resolved candidate = over(location);
            if (declaresId(candidate, pack)) {
                return candidate;
            }
        }
        throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + pack.value() + " is not a configured pack");
    }

    /** The store rooted at the pack directory's parent, addressed by the directory's own name. */
    private Resolved over(PackLocation location) {
        Path directory = location.root().toAbsolutePath().normalize();
        Path parent = directory.getParent();
        if (parent == null || directory.getFileName() == null) {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack location has no parent directory");
        }
        return new Resolved(
                new FilesystemPackStore(parent, strategy),
                new PackIdentifier(directory.getFileName().toString()));
    }

    private static boolean declaresId(Resolved candidate, PackIdentifier pack) {
        try {
            return candidate.store.readDescriptor(candidate.key).packId().equals(pack.value());
        } catch (CharterRefusal unreadable) {
            return false;
        }
    }

    // ---- DSO to entity -------------------------------------------------------------------------------------------

    /** The entity of a readable element, or empty when it breaks a domain invariant. */
    private static <D, E extends CharterArtifact> Optional<E> entity(PackIdentifier pack, KindBinding<D, E> kind, D dso) {
        try {
            return Optional.of(kind.toEntity(pack, dso));
        } catch (ConstraintBreachException | IllegalArgumentException breach) {
            return Optional.empty();
        }
    }
}
