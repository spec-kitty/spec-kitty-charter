package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.DirectiveReference;
import ai.speckitty.charter.packs.domain.DirectiveRepository;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.ParadigmReference;
import ai.speckitty.charter.packs.domain.ParadigmRepository;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.StyleguideAntiPattern;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.StyleguidePattern;
import ai.speckitty.charter.packs.domain.StyleguideRepository;
import ai.speckitty.charter.packs.domain.StyleguideScope;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.TacticReference;
import ai.speckitty.charter.packs.domain.TacticRepository;
import ai.speckitty.charter.packs.domain.TacticStep;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.domain.ToolguideRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
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

    // ---- DirectiveRepository / StyleguideRepository / ParadigmRepository -----------------------------------------

    @Override
    public void save(Directive directive) {
        Resolved pack = resolve(directive.packId());
        pack.store.writeDirective(pack.key, toDso(directive));
    }

    @Override
    public Optional<Directive> load(PackIdentifier pack, DirectiveIdentifier directive) {
        Resolved resolved = resolve(pack);
        return resolved.store
                .readDirective(resolved.key, directive.value())
                .flatMap(dso -> orEmpty(() -> buildDirective(pack, dso)));
    }

    @Override
    public void save(Styleguide styleguide) {
        Resolved pack = resolve(styleguide.packId());
        pack.store.writeStyleguide(pack.key, toDso(styleguide));
    }

    @Override
    public Optional<Styleguide> load(PackIdentifier pack, StyleguideIdentifier styleguide) {
        Resolved resolved = resolve(pack);
        return resolved.store
                .readStyleguide(resolved.key, styleguide.value())
                .flatMap(dso -> orEmpty(() -> buildStyleguide(pack, dso)));
    }

    @Override
    public void save(Toolguide toolguide) {
        Resolved pack = resolve(toolguide.packId());
        pack.store.writeToolguide(pack.key, toDso(toolguide));
    }

    @Override
    public Optional<Toolguide> load(PackIdentifier pack, ToolguideIdentifier toolguide) {
        Resolved resolved = resolve(pack);
        return resolved.store
                .readToolguide(resolved.key, toolguide.value())
                .flatMap(dso -> orEmpty(() -> buildToolguide(pack, dso)));
    }

    @Override
    public void save(Paradigm paradigm) {
        Resolved pack = resolve(paradigm.packId());
        pack.store.writeParadigm(pack.key, toDso(paradigm));
    }

    @Override
    public Optional<Paradigm> load(PackIdentifier pack, ParadigmIdentifier paradigm) {
        Resolved resolved = resolve(pack);
        return resolved.store
                .readParadigm(resolved.key, paradigm.value())
                .flatMap(dso -> orEmpty(() -> buildParadigm(pack, dso)));
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

    /**
     * Writes one artefact into {@code store} under {@code key}, through the shared DSO mappers: a known id replaces
     * its file, an unknown id creates {@code <id>.<kind>.yaml}. The {@link PackCommitStore} calls it to stage the one
     * artefact a registration added or replaced before the atomic directory swap, leaving the descriptor and every
     * sibling byte-identical; the mapping stays in one place so the commit path and the per-child saves cannot drift.
     *
     * @param store the store to write through (for a commit, one rooted at the staging directory's parent)
     * @param key the directory-name key the store addresses the pack by
     * @param artifact the artefact to write
     * @throws IllegalArgumentException when {@code artifact} is of a kind this store does not persist
     */
    static void writeArtifact(FilesystemPackStore store, PackIdentifier key, CharterArtifact artifact) {
        switch (artifact) {
            case Tactic tactic -> store.writeTactic(key, toDso(tactic));
            case Directive directive -> store.writeDirective(key, toDso(directive));
            case Styleguide styleguide -> store.writeStyleguide(key, toDso(styleguide));
            case Toolguide toolguide -> store.writeToolguide(key, toDso(toolguide));
            case Paradigm paradigm -> store.writeParadigm(key, toDso(paradigm));
            default -> throw new IllegalArgumentException("cannot persist a " + artifact.kind() + " artefact");
        }
    }

    private Pack assemble(PackIdentifier pack) {
        Resolved resolved = resolve(pack);
        FilesystemPackStore.PackRead read = resolved.store.read(resolved.key);
        Pack.Builder builder = Pack.builder().id(pack).descriptor(toDescriptor(read.descriptor()));
        read.violations().forEach(builder::violation);
        collect(read.tactics(), TacticDso::id, dso -> builder.tactic(buildTactic(pack, dso)), builder);
        collect(read.directives(), DirectiveDso::id, dso -> builder.directive(buildDirective(pack, dso)), builder);
        collect(read.styleguides(), StyleguideDso::id, dso -> builder.styleguide(buildStyleguide(pack, dso)), builder);
        collect(read.toolguides(), ToolguideDso::id, dso -> builder.toolguide(buildToolguide(pack, dso)), builder);
        collect(read.paradigms(), ParadigmDso::id, dso -> builder.paradigm(buildParadigm(pack, dso)), builder);
        return builder.build();
    }

    /**
     * Adds each readable DSO to the aggregate, failing closed: a domain-invalid element is recorded as a violation
     * rather than aborting assembly.
     */
    private static <D> void collect(List<D> dsos, Function<D, String> fileOf, Consumer<D> add, Pack.Builder builder) {
        for (D dso : dsos) {
            try {
                add.accept(dso);
            } catch (ConstraintBreachException | IllegalArgumentException breach) {
                builder.violation(
                        new PackElementViolation(fileOf.apply(dso), "domain", String.valueOf(breach.getMessage())));
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

    private static <T> Optional<T> orEmpty(java.util.function.Supplier<T> build) {
        try {
            return Optional.of(build.get());
        } catch (ConstraintBreachException | IllegalArgumentException breach) {
            return Optional.empty();
        }
    }

    private static Directive buildDirective(PackIdentifier pack, DirectiveDso dso) {
        Directive.Builder builder = Directive.builder()
                .id(new DirectiveIdentifier(dso.id()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .title(dso.title())
                .intent(dso.intent())
                .enforcement(Enforcement.fromToken(dso.enforcement())
                        .orElseThrow(() -> new IllegalArgumentException("unknown enforcement: " + dso.enforcement())))
                .scope(dso.scope().orElse(null));
        dso.procedures().forEach(builder::procedure);
        dso.integrityRules().forEach(builder::integrityRule);
        dso.validationCriteria().forEach(builder::validationCriterion);
        dso.explicitAllowances().forEach(builder::explicitAllowance);
        dso.references()
                .forEach(ref ->
                        builder.reference(new DirectiveReference(ref.type(), ref.id(), ref.when(), ref.reason())));
        return builder.build();
    }

    private static DirectiveDso toDso(Directive directive) {
        return new DirectiveDso(
                directive.id().value(),
                directive.schemaVersion().orElseThrow(),
                directive.title(),
                directive.intent(),
                directive.enforcement().token(),
                directive.scope(),
                directive.procedures(),
                directive.integrityRules(),
                directive.validationCriteria(),
                directive.explicitAllowances(),
                directive.references().stream()
                        .map(ref -> new DirectiveDso.Reference(ref.type(), ref.id(), ref.when(), ref.reason()))
                        .toList());
    }

    private static Styleguide buildStyleguide(PackIdentifier pack, StyleguideDso dso) {
        Styleguide.Builder builder = Styleguide.builder()
                .id(new StyleguideIdentifier(dso.id()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .title(dso.title())
                .scope(StyleguideScope.fromToken(dso.scope())
                        .orElseThrow(() -> new IllegalArgumentException("unknown scope: " + dso.scope())))
                .tooling(dso.tooling().orElse(null))
                .qualityTest(dso.qualityTest().orElse(null))
                .structuralLintConfig(dso.structuralLintConfig().orElse(null));
        dso.principles().forEach(builder::principle);
        dso.patterns()
                .forEach(p -> builder.pattern(
                        new StyleguidePattern(p.name(), p.description(), p.badExample(), p.goodExample())));
        dso.antiPatterns()
                .forEach(a -> builder.antiPattern(
                        new StyleguideAntiPattern(a.name(), a.description(), a.badExample(), a.goodExample())));
        dso.appliesToLanguages().forEach(builder::appliesToLanguage);
        dso.references().forEach(builder::reference);
        return builder.build();
    }

    private static StyleguideDso toDso(Styleguide styleguide) {
        return new StyleguideDso(
                styleguide.id().value(),
                styleguide.schemaVersion().orElseThrow(),
                styleguide.title(),
                styleguide.scope().token(),
                styleguide.principles(),
                styleguide.patterns().stream()
                        .map(p -> new StyleguideDso.Pattern(p.name(), p.description(), p.badExample(), p.goodExample()))
                        .toList(),
                styleguide.antiPatterns().stream()
                        .map(a -> new StyleguideDso.AntiPattern(
                                a.name(), a.description(), a.badExample(), a.goodExample()))
                        .toList(),
                styleguide.tooling(),
                styleguide.qualityTest(),
                styleguide.structuralLintConfig(),
                styleguide.appliesToLanguages(),
                styleguide.references());
    }

    private static Toolguide buildToolguide(PackIdentifier pack, ToolguideDso dso) {
        Toolguide.Builder builder = Toolguide.builder()
                .id(new ToolguideIdentifier(dso.id()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .tool(dso.tool())
                .title(dso.title())
                .guidePath(dso.guidePath())
                .summary(dso.summary())
                .lastUpdated(dso.lastUpdated().orElse(null));
        dso.commands().forEach(builder::command);
        dso.appliesToLanguages().forEach(builder::appliesToLanguage);
        dso.references().forEach(builder::reference);
        return builder.build();
    }

    private static ToolguideDso toDso(Toolguide toolguide) {
        return new ToolguideDso(
                toolguide.id().value(),
                toolguide.schemaVersion().orElseThrow(),
                toolguide.tool(),
                toolguide.title(),
                toolguide.guidePath(),
                toolguide.summary(),
                toolguide.commands(),
                toolguide.appliesToLanguages(),
                toolguide.lastUpdated(),
                toolguide.references());
    }

    private static Paradigm buildParadigm(PackIdentifier pack, ParadigmDso dso) {
        Paradigm.Builder builder = Paradigm.builder()
                .id(new ParadigmIdentifier(dso.id()))
                .packId(pack)
                .schemaVersion(dso.schemaVersion())
                .name(dso.name())
                .summary(dso.summary());
        dso.directiveRefs().forEach(builder::directiveRef);
        dso.references().forEach(ref -> builder.reference(new ParadigmReference(ref.type(), ref.id())));
        return builder.build();
    }

    private static ParadigmDso toDso(Paradigm paradigm) {
        return new ParadigmDso(
                paradigm.id().value(),
                paradigm.schemaVersion().orElseThrow(),
                paradigm.name(),
                paradigm.summary(),
                paradigm.directiveRefs(),
                paradigm.references().stream()
                        .map(ref -> new ParadigmDso.Reference(ref.type(), ref.id()))
                        .toList());
    }

    private static TacticReference toEntity(TacticDso.Reference ref) {
        return new TacticReference(ref.name(), ref.type(), ref.id(), ref.when());
    }

    private static TacticDso toDso(Tactic tactic) {
        return new TacticDso(
                tactic.id().value(),
                tactic.schemaVersion().orElseThrow(),
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
