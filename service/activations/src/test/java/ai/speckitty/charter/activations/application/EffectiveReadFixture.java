package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.infra.api.StandaloneActivationMvc;
import ai.speckitty.charter.activations.infra.packs.PacksAvailability;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.application.ClosureQueries;
import ai.speckitty.charter.packs.application.CompositionFactsCache;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackWriteGeneration;
import ai.speckitty.charter.packs.application.RegistryWriteLock;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackSources;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.packs.infra.storage.PackLocation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The fixture of NFR-003, over the real read path: ten packs of two hundred directives each (two thousand artifacts), a
 * chain of thirty {@code overrides} edges in the first, one activation over all ten that selects every artifact, the real
 * {@link ClosureQueries} over a real {@link ConsistentRead} on real pack directories, the real
 * {@link CompositionFactsCache}, the real layer merge and in-force resolution, the activations slice's service, mapper
 * and controller, and MockMvc in front. Only the facts of a pack are authored in memory instead of read from files, and
 * each pack's directory holds the one file the digests read.
 *
 * <p>{@link #revise()} gives every pack a new revision, so the facts cache misses on the next read: the cache key varies.
 */
final class EffectiveReadFixture {

    static final int PACKS = 10;
    static final int ARTIFACTS_PER_PACK = 200;
    static final int ARTIFACTS = PACKS * ARTIFACTS_PER_PACK;
    static final String PATH = "/api/v1/activations/a-1/artifacts?pageSize=200";

    private static final int CHAIN = 30;

    final CompositionFactsCache cache;
    final MockMvc mvc;
    final CountingSources digests;
    private final AtomicInteger revision = new AtomicInteger();

    /** The digests the real sources took, as a read asks for them. */
    static final class CountingSources implements PackSources {

        private final PackSources real;
        private final AtomicInteger whole = new AtomicInteger();
        private final AtomicInteger closure = new AtomicInteger();

        CountingSources(PackSources real) {
            this.real = real;
        }

        @Override
        public String fingerprint() {
            whole.incrementAndGet();
            return real.fingerprint();
        }

        @Override
        public String fingerprintOf(Collection<String> packNames) {
            closure.incrementAndGet();
            return real.fingerprintOf(packNames);
        }

        int whole() {
            return whole.get();
        }

        int closure() {
            return closure.get();
        }
    }

    EffectiveReadFixture(Path root) {
        List<ConfiguredPack> configured = new ArrayList<>();
        List<PackLocation> locations = new ArrayList<>();
        for (int pack = 0; pack < PACKS; pack++) {
            configured.add(new ConfiguredPack(new PackIdentifier(name(pack)), PackLevel.ORG));
            locations.add(new PackLocation(name(pack), PackLevel.ORG, directory(root, pack)));
        }
        PackCatalogRepository catalog = () -> configured;
        PackDescriptorRepository descriptors = new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier pack) {
                return find(pack).orElseThrow();
            }

            @Override
            public Optional<PackDescriptor> find(PackIdentifier pack) {
                return Optional.of(new PackDescriptor(
                        "id-" + pack.value(),
                        "1.0.0",
                        Optional.empty(),
                        pack.value() + " pack",
                        Optional.empty(),
                        Optional.empty(),
                        PackPaths.none(),
                        List.of()));
            }

            @Override
            public void save(PackIdentifier pack, PackDescriptor descriptor) {
                throw new UnsupportedOperationException("the closure never writes");
            }
        };
        PackWriteGeneration generation = new PackWriteGeneration();
        this.digests = new CountingSources(new FileSystemPackSources(() -> locations));
        ConsistentRead read = new ConsistentRead(digests, generation);
        this.cache = new CompositionFactsCache(
                pack -> PackRevision.compute(List.of(pack.value(), Integer.toString(revision.get()))),
                generation,
                new RegistryWriteLock(Duration.ofSeconds(5)),
                CompositionFactsCache.DEFAULT_CAPACITY);
        OfferingReadService offering = new OfferingReadService(
                catalog,
                descriptors,
                pack -> factsOf(Integer.parseInt(pack.value().substring(1))),
                read,
                cache);
        PacksAvailability packs = new PacksAvailability(new ClosureQueries(offering, catalog, read));
        InMemoryActivationRepository repository = new InMemoryActivationRepository();
        repository.create(new ActivationId("a-1"), activation());
        this.mvc = StandaloneActivationMvc.over(repository, packs);
    }

    /** Gives every pack a new revision: the facts cache misses on the next read. */
    void revise() {
        revision.incrementAndGet();
    }

    private static String name(int pack) {
        return "p" + pack;
    }

    private static String id(int pack, int artifact) {
        return "D" + pack + "X" + artifact;
    }

    private static Path directory(Path root, int pack) {
        try {
            Path directory = Files.createDirectories(root.resolve(name(pack)));
            Files.writeString(directory.resolve("pack.yaml"), "name: " + name(pack) + "\n");
            return directory;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The facts of a pack: two hundred directives, and in the first a chain of overrides, each hiding the next. */
    private static PackCompositionFacts factsOf(int pack) {
        List<String> defined = IntStream.range(0, ARTIFACTS_PER_PACK)
                .mapToObj(artifact -> "directive:" + id(pack, artifact))
                .toList();
        List<Relationship> edges = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        if (pack == 0) {
            for (int link = 0; link < CHAIN; link++) {
                PackQualifiedReference from = PackQualifiedReference.parse("directive:" + id(0, link));
                PackQualifiedReference to = PackQualifiedReference.parse("directive:" + id(0, link + 1));
                edges.add(new Relationship(
                        from.asUrn().orElseThrow(),
                        from.packName(),
                        to.asUrn().orElseThrow(),
                        to.packName(),
                        RelationType.OVERRIDES,
                        Optional.empty(),
                        Optional.empty()));
                changed.add(to.asUrn().orElseThrow().toString());
            }
        }
        return new PackCompositionFacts(
                changed, defined, List.of(), MutabilityPolicySource.empty(), List.of(), edges, Map.of());
    }

    private static ActivationDocument activation() {
        ActivationDocument.Builder builder = ActivationDocument.builder()
                .packs(IntStream.range(0, PACKS)
                        .mapToObj(pack -> PackRef.of(name(pack)))
                        .toList());
        builder.selected(
                ArtifactKind.DIRECTIVE,
                IntStream.range(0, PACKS)
                        .boxed()
                        .flatMap(
                                pack -> IntStream.range(0, ARTIFACTS_PER_PACK).mapToObj(artifact -> id(pack, artifact)))
                        .toList());
        return builder.build().document();
    }
}
