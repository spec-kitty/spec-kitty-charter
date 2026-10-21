package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.TacticReference;
import ai.speckitty.charter.packs.domain.TacticRepository;
import ai.speckitty.charter.packs.domain.TacticStep;
import java.nio.file.Path;
import java.util.List;
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
 * <p>The persistence shapes ({@link TacticDso}, {@link PackDescriptorDso}) stay inside this package. They are mapped
 * to and from the domain entities by hand, and only domain types leave the service.
 */
@Service
public final class DomainRepositoryService implements TacticRepository, PackDescriptorRepository, PackRepository {

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

    // ---- TacticRepository ----------------------------------------------------------------------------------------

    @Override
    public void save(Tactic tactic) {
        Resolved pack = resolve(tactic.packId());
        pack.store.writeTactic(pack.key, toDso(tactic));
    }

    @Override
    public Optional<Tactic> load(PackIdentifier pack, TacticIdentifier tactic) {
        Resolved resolved = resolve(pack);
        return resolved.store.readTactic(resolved.key, tactic.value()).flatMap(dso -> toEntity(pack, dso));
    }

    // ---- PackDescriptorRepository --------------------------------------------------------------------------------

    @Override
    public PackDescriptor get(PackIdentifier pack) {
        Resolved resolved = resolve(pack);
        return toDescriptor(resolved.store.readDescriptor(resolved.key));
    }

    @Override
    public void save(PackIdentifier pack, PackDescriptor descriptor) {
        Resolved resolved = resolve(pack);
        resolved.store.writeDescriptor(resolved.key, toDso(descriptor));
    }

    // ---- PackRepository --------------------------------------------------------------------

    @Override
    public Pack load(PackIdentifier pack) {
        return assemble(pack);
    }

    @Override
    public List<Tactic> tacticsOf(PackIdentifier pack) {
        Resolved resolved = resolve(pack);
        return resolved.store.readTactics(resolved.key).stream()
                .flatMap(dso -> toEntity(pack, dso).stream())
                .toList();
    }

    private Pack assemble(PackIdentifier pack) {
        Resolved resolved = resolve(pack);
        FilesystemPackStore.PackRead read = resolved.store.read(resolved.key);
        Pack.Builder builder = Pack.builder().id(pack).descriptor(toDescriptor(read.descriptor()));
        read.violations().forEach(builder::violation);
        for (TacticDso dso : read.tactics()) {
            try {
                builder.tactic(buildTactic(pack, dso));
            } catch (ConstraintBreachException breach) {
                builder.violation(new PackElementViolation(dso.id(), "domain", String.valueOf(breach.getMessage())));
            }
        }
        return builder.build();
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

    // ---- DSO <-> entity mapping ----------------------------------------------------------------------------------

    private static Optional<Tactic> toEntity(PackIdentifier pack, TacticDso dso) {
        try {
            return Optional.of(buildTactic(pack, dso));
        } catch (ConstraintBreachException breach) {
            return Optional.empty();
        }
    }

    private static Tactic buildTactic(PackIdentifier pack, TacticDso dso) {
        Tactic.Builder builder = Tactic.builder()
                .id(new TacticIdentifier(dso.id()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .name(dso.name())
                .purpose(dso.purpose().orElse(null))
                .notes(dso.notes().orElse(null));
        dso.steps()
                .forEach(step -> builder.step(new TacticStep(
                        step.title(),
                        step.description(),
                        step.examples(),
                        step.references().stream()
                                .map(DomainRepositoryService::toEntity)
                                .toList())));
        dso.failureModes().forEach(builder::failureMode);
        dso.appliesToLanguages().forEach(builder::appliesToLanguage);
        dso.references().forEach(ref -> builder.reference(toEntity(ref)));
        return builder.build();
    }

    private static TacticReference toEntity(TacticDso.Reference ref) {
        return new TacticReference(ref.name(), ref.type(), ref.id(), ref.when());
    }

    private static TacticDso toDso(Tactic tactic) {
        return new TacticDso(
                tactic.id().value(),
                tactic.schemaVersion(),
                tactic.name(),
                tactic.steps().stream()
                        .map(step -> new TacticDso.Step(
                                step.title(),
                                step.description(),
                                step.examples(),
                                step.references().stream()
                                        .map(DomainRepositoryService::toDso)
                                        .toList()))
                        .toList(),
                tactic.purpose(),
                tactic.notes(),
                tactic.failureModes(),
                tactic.appliesToLanguages(),
                tactic.references().stream().map(DomainRepositoryService::toDso).toList());
    }

    private static TacticDso.Reference toDso(TacticReference ref) {
        return new TacticDso.Reference(ref.name(), ref.type(), ref.id(), ref.when());
    }

    private static PackDescriptor toDescriptor(PackDescriptorDso dso) {
        return new PackDescriptor(
                dso.packId(),
                dso.packVersion(),
                dso.parentPack(),
                dso.name(),
                dso.packLevel().flatMap(PackLevel::fromToken),
                dso.requiresCore(),
                dso.undeclaredKeys(),
                new PackPaths(dso.activeCharter(), dso.packDrg()));
    }

    private static PackDescriptorDso toDso(PackDescriptor descriptor) {
        return new PackDescriptorDso(
                descriptor.packId(),
                descriptor.packVersion(),
                descriptor.parentPack(),
                descriptor.name(),
                descriptor.declaredLevel().map(PackLevel::token),
                descriptor.requiresCore(),
                descriptor.paths().activeCharter(),
                descriptor.paths().packDrg(),
                descriptor.undeclaredKeys());
    }
}
