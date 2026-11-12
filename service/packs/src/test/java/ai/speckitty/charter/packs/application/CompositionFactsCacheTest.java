package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.markdown.SlotId;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PromptFacts;
import ai.speckitty.charter.packs.infra.storage.CommitStorePackWriteStore;
import ai.speckitty.charter.packs.infra.storage.PackCommitStore;
import ai.speckitty.charter.packs.infra.storage.StorageRootLocations;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The facts cache of the effective reads (#416, decision DM-01M6XT2FKK7YPV1GCESFW1ANZ7): facts are kept per pack under
 * the pack's write generation and its revision, both read with the pack's configured name, which is the name every
 * write brackets its swap with (#418 R5). A read never fills the cache from a generation that was odd or moved, nor from
 * a holder of the registry lock, and a failure is never kept.
 */
class CompositionFactsCacheTest {

    private static final PackIdentifier ALPHA = new PackIdentifier("alpha");

    private final Map<String, String> content = new HashMap<>();
    private final PackWriteGeneration generation = new PackWriteGeneration();
    private final RegistryWriteLock lock = new RegistryWriteLock(Duration.ofMillis(200));
    private final AtomicInteger loads = new AtomicInteger();
    private final PackRevisions revisions = pack -> PackRevision.compute(List.of(content.get(pack.value())));

    private PackCompositionFacts load(PackIdentifier pack) {
        loads.incrementAndGet();
        return factsOf(content.get(pack.value()));
    }

    private static PackCompositionFacts factsOf(String text) {
        return new PackCompositionFacts(List.of(), List.of("directive:" + text), List.of());
    }

    private CompositionFactsCache cache(int capacity) {
        return new CompositionFactsCache(revisions, generation, lock, capacity);
    }

    private CompositionFactsCache cache() {
        return cache(8);
    }

    @Test
    void aSecondReadOfAnUnchangedPackIsServedFromTheCache() {
        content.put("alpha", "one");
        CompositionFactsCache cache = cache();

        PackCompositionFacts first = cache.facts(ALPHA, this::load);
        PackCompositionFacts second = cache.facts(ALPHA, this::load);

        assertThat(first).isEqualTo(factsOf("one"));
        assertThat(second).isEqualTo(first);
        assertThat(loads).hasValue(1);
    }

    @Test
    void noReadAfterAWriteIsStale() {
        content.put("alpha", "one");
        CompositionFactsCache cache = cache();
        cache.facts(ALPHA, this::load);

        generation.bracket("alpha", () -> content.put("alpha", "two"));

        assertThat(cache.facts(ALPHA, this::load)).isEqualTo(factsOf("two"));
        assertThat(loads).hasValue(2);
    }

    @Test
    void aSwapThatRestoresTheSameRevisionStillReloads() {
        content.put("alpha", "one");
        CompositionFactsCache cache = cache();
        cache.facts(ALPHA, this::load);

        generation.bracket("alpha", () -> content.put("alpha", "two"));
        generation.bracket("alpha", () -> content.put("alpha", "one"));

        assertThat(cache.facts(ALPHA, this::load)).isEqualTo(factsOf("one"));
        assertThat(loads)
                .as("the generation moved, so the revision alone is not trusted")
                .hasValue(2);
    }

    @Test
    void anOutOfBandRevisionChangeMissesTheCache() {
        content.put("alpha", "one");
        CompositionFactsCache cache = cache();
        cache.facts(ALPHA, this::load);

        content.put("alpha", "two");

        assertThat(cache.facts(ALPHA, this::load)).isEqualTo(factsOf("two"));
        assertThat(loads)
                .as("no write bracketed the change, and the revision caught it")
                .hasValue(2);
    }

    @Test
    void anOddGenerationIsNeitherServedFromNorFillsTheCache() {
        content.put("alpha", "one");
        CompositionFactsCache cache = cache();
        cache.facts(ALPHA, this::load);
        generation.begin("alpha");

        cache.facts(ALPHA, this::load);
        assertThat(loads).as("the entry is not served while a swap runs").hasValue(2);
        generation.end("alpha");

        cache.facts(ALPHA, this::load);
        assertThat(loads).as("the generation moved past the entry").hasValue(3);
        cache.facts(ALPHA, this::load);
        assertThat(loads).as("and the read of the quiet generation fills it").hasValue(3);
    }

    @Test
    void aReadDuringWhichTheGenerationMovesDoesNotFillTheCache() {
        content.put("alpha", "one");
        CompositionFactsCache cache = cache();

        PackCompositionFacts read = cache.facts(ALPHA, pack -> {
            PackCompositionFacts facts = load(pack);
            generation.bracket("alpha", () -> content.put("alpha", "two"));
            return facts;
        });

        assertThat(read).isEqualTo(factsOf("one"));
        assertThat(cache.facts(ALPHA, this::load)).isEqualTo(factsOf("two"));
        assertThat(loads).hasValue(2);
    }

    @Test
    void aReadDuringWhichTheRevisionChangesOutOfBandDoesNotFillTheCache() {
        content.put("alpha", "one");
        CompositionFactsCache cache = cache();

        cache.facts(ALPHA, pack -> {
            PackCompositionFacts facts = load(pack);
            content.put("alpha", "two");
            return facts;
        });

        assertThat(cache.facts(ALPHA, this::load)).isEqualTo(factsOf("two"));
        assertThat(loads).hasValue(2);
    }

    @Test
    void aHolderOfTheRegistryLockNeitherReadsFromNorFillsTheCache() {
        content.put("alpha", "one");
        CompositionFactsCache cache = cache();
        cache.facts(ALPHA, this::load);

        lock.exclusively(() -> {
            content.put("alpha", "staged");
            assertThat(cache.facts(ALPHA, this::load))
                    .as("a staged session reads the store, not the entry")
                    .isEqualTo(factsOf("staged"));
            return null;
        });
        content.put("alpha", "one");

        assertThat(cache.facts(ALPHA, this::load)).isEqualTo(factsOf("one"));
        assertThat(loads)
                .as("the staged read left the entry of the committed state as it was: it filled nothing")
                .hasValue(2);
    }

    @Test
    void aFailureIsNotKeptAndKeepsItsCodeAndDetail() {
        content.put("alpha", "one");
        CompositionFactsCache cache = cache();
        AtomicInteger attempts = new AtomicInteger();

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> cache.facts(ALPHA, pack -> {
                        attempts.incrementAndGet();
                        throw new CharterRefusal(
                                RefusalCode.PACK_UNREADABLE, "a graph document of alpha cannot be read");
                    }))
                    .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                        assertThat(refusal.getMessage()).isEqualTo("a graph document of alpha cannot be read");
                    });
        }

        assertThat(attempts).as("the second read tried again").hasValue(2);
        assertThat(cache.facts(ALPHA, this::load)).isEqualTo(factsOf("one"));
    }

    @Test
    void aRevisionThatCannotBeReadLeavesTheLoadsOwnRefusalTheAnswer() {
        PackRevisions broken = pack -> {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "the revision of alpha cannot be read");
        };
        CompositionFactsCache cache = new CompositionFactsCache(broken, generation, lock, 8);

        assertThatThrownBy(() -> cache.facts(ALPHA, pack -> {
                    throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "a graph document of alpha cannot be read");
                }))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal ->
                                assertThat(refusal.getMessage()).isEqualTo("a graph document of alpha cannot be read"));
    }

    @Test
    void aWriteIsSeenUnderTheConfiguredNameItWasBracketedWith() {
        content.put("alpha", "one");
        CompositionFactsCache cache = cache();
        cache.facts(ALPHA, this::load);

        generation.bracket("some-other-pack", () -> null);
        cache.facts(ALPHA, this::load);
        assertThat(loads).as("another pack's swap does not touch this entry").hasValue(1);

        generation.bracket("alpha", () -> null);
        cache.facts(ALPHA, this::load);
        assertThat(loads).as("a swap bracketed with the configured name does").hasValue(2);
    }

    @Test
    void moreDistinctPacksThanTheCapReadCorrectlyAndTheCacheStaysWithinIt() {
        CompositionFactsCache cache = cache(3);
        for (int pack = 0; pack < 10; pack++) {
            content.put("pack-" + pack, "v" + pack);
        }

        for (int round = 0; round < 2; round++) {
            for (int pack = 0; pack < 10; pack++) {
                PackIdentifier id = new PackIdentifier("pack-" + pack);
                assertThat(cache.facts(id, this::load)).isEqualTo(factsOf("v" + pack));
            }
        }

        assertThat(cache.size()).isLessThanOrEqualTo(3);
        assertThat(loads)
                .as("a bound smaller than the working set only costs reloads")
                .hasValueGreaterThan(10);
    }

    @Test
    void theMostRecentlyUsedPackStaysWhenTheCapIsReached() {
        CompositionFactsCache cache = cache(2);
        content.put("a", "va");
        content.put("b", "vb");
        content.put("c", "vc");
        cache.facts(new PackIdentifier("a"), this::load);
        cache.facts(new PackIdentifier("b"), this::load);
        cache.facts(new PackIdentifier("a"), this::load);
        cache.facts(new PackIdentifier("c"), this::load);
        int before = loads.get();

        cache.facts(new PackIdentifier("a"), this::load);

        assertThat(loads).as("a was used after b, so c's arrival evicted b").hasValue(before);
    }

    /** Facts whose prompt member is all the text says: the slot the plan step declares and the fill's text. */
    private static PackCompositionFacts promptFactsOf(String text) {
        SlotId reasons = new SlotId("spdd:reasons-block");
        return new PackCompositionFacts(
                List.of(),
                List.of("mission_type:software-dev"),
                List.of(),
                MutabilityPolicySource.empty(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                new PromptFacts(
                        Map.of(
                                Urn.action("software-dev", "plan"),
                                List.of(new PromptFacts.DeclaredSlot(reasons, false))),
                        Map.of(
                                Urn.parse("step_enhancement:reasons").orElseThrow(),
                                List.of(new PromptFacts.Fill(reasons, text)))));
    }

    @Test
    void thePromptFactsAreKeptWithTheOtherFactsAndReadOncePerState() {
        content.put("alpha", "one");
        CompositionFactsCache cache = cache();
        Function<PackIdentifier, PackCompositionFacts> readPrompts = pack -> {
            loads.incrementAndGet();
            return promptFactsOf(content.get(pack.value()));
        };

        PackCompositionFacts first = cache.facts(ALPHA, readPrompts);
        PackCompositionFacts second = cache.facts(ALPHA, readPrompts);

        assertThat(second.prompts()).isEqualTo(promptFactsOf("one").prompts());
        assertThat(second.prompts()).as("served from the entry, not rebuilt").isSameAs(first.prompts());
        assertThat(loads).hasValue(1);

        generation.bracket("alpha", () -> content.put("alpha", "two"));

        assertThat(cache.facts(ALPHA, readPrompts).prompts())
                .as("a write moves the prompt facts with the rest")
                .isEqualTo(promptFactsOf("two").prompts());
        assertThat(loads).hasValue(2);
    }

    @Test
    void theCapIsAtLeastOne() {
        assertThatThrownBy(() -> cache(0)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- the offering's compose reads through it -----------------------------------------------------------------

    private OfferingReadService offeringOver(CompositionFactsCache cache) {
        PackDescriptor descriptor =
                new PackDescriptor("id-alpha", "1.0.0", Optional.empty(), "alpha", Optional.empty(), Optional.empty());
        PackCatalogRepository catalog = () -> List.of(new ConfiguredPack(ALPHA, PackLevel.ORG));
        PackDescriptorRepository descriptors = new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier pack) {
                return descriptor;
            }

            @Override
            public Optional<PackDescriptor> find(PackIdentifier pack) {
                return Optional.of(descriptor);
            }

            @Override
            public void save(PackIdentifier pack, PackDescriptor ignored) {
                throw new UnsupportedOperationException("the compose never writes");
            }
        };
        return new OfferingReadService(catalog, descriptors, this::load, new ConsistentRead(() -> "digest"), cache);
    }

    @Test
    void theComposeReadsAPacksFactsOncePerGenerationAndRevision() {
        content.put("alpha", "one");
        OfferingReadService offering = offeringOver(cache());

        offering.composedWithFacts();
        OfferingReadService.ComposedClosure again = offering.composedWithFacts();
        assertThat(loads)
                .as("the second compose recomposes nothing that did not change")
                .hasValue(1);
        assertThat(again.facts().get("alpha")).isEqualTo(factsOf("one"));

        generation.bracket("alpha", () -> content.put("alpha", "two"));
        assertThat(offering.composedWithFacts().facts().get("alpha")).isEqualTo(factsOf("two"));
        assertThat(loads).hasValue(2);
    }

    @Test
    void aRenameOnlyOutOfBandChangeOfTheRealPackFilesMissesTheCache(@TempDir Path host) throws IOException {
        Path pack = Files.createDirectories(host.resolve("alpha").resolve("directives"));
        Files.writeString(
                host.resolve("alpha").resolve("pack.yaml"),
                "pack_id: 01M5DEH8ASSETREG1STRAT10N0\npack_version: 0.1.0\nname: pack-a\npack_level: org\n",
                StandardCharsets.UTF_8);
        Path before = pack.resolve("A.directive.yaml");
        Files.writeString(before, "id: A\n", StandardCharsets.UTF_8);
        StorageRootLocations locations = new StorageRootLocations(host);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        CommitStorePackWriteStore store = new CommitStorePackWriteStore(
                locations, strategy, new PackCommitStore(locations, strategy), lock, generation);
        CompositionFactsCache cache = new CompositionFactsCache(store, generation, lock, 8);
        List<String> seen = new ArrayList<>();
        Function<PackIdentifier, PackCompositionFacts> readFiles = id -> {
            loads.incrementAndGet();
            seen.clear();
            try (Stream<Path> files = Files.list(pack)) {
                files.forEach(file -> seen.add("directive:" + file.getFileName()));
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            return new PackCompositionFacts(List.of(), List.copyOf(seen), List.of());
        };
        cache.facts(ALPHA, readFiles);

        Files.move(before, pack.resolve("B.directive.yaml"));

        assertThat(cache.facts(ALPHA, readFiles).defined())
                .as("the same bytes under another path are another pack: no write bracketed it, so the key must see it")
                .containsExactly("directive:B.directive.yaml");
        assertThat(loads).hasValue(2);
    }
}
