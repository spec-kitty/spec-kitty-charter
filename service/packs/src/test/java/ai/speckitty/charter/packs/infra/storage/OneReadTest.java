package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * One read per surface call (#554, S1, T028, NFR-004): counting seams on the tree walk ({@link PackFileSet#walks()})
 * and on the store's whole read ({@link FilesystemPackStore#reads()}) see one walk and one read of the staged tree per
 * registration load, the census, the validate facts and the assembly input all from that walk, and the composition
 * facts built from that read without reading again. The second read the registration made through
 * {@code contentsOf} and a single-pack {@link DomainRepositoryService} is gone.
 */
@DisplayName("one read of a pack per surface call (#554, T028)")
class OneReadTest {

    private static final YamlSerializationStrategy STRATEGY = new YamlSerializationStrategy();

    @Test
    @DisplayName("a registration's load reads the staged tree once, its composition facts included")
    void theRegistrationLoadReadsOnce(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(ExamplePacks.ORG, host);
        long walks = PackFileSet.walks();
        long runs = StoreRead.runs();
        long before = FilesystemPackStore.reads();

        WholePackWriteStore.Contents contents = StagedReads.contents(new WholePackStore(STRATEGY), pack);

        assertThat(PackFileSet.walks() - walks)
                .as("one walk of the staged tree")
                .isOne();
        assertThat(StoreRead.runs() - runs).as("one run of the store half").isOne();
        assertThat(FilesystemPackStore.reads() - before).isOne();
        assertThat(contents.facts().defined()).isNotEmpty();
    }

    @Test
    @DisplayName("a stored pack's load walks and reads it once, its composition facts included")
    void theStoredLoadReadsOnce(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        long walks = PackFileSet.walks();
        long before = FilesystemPackStore.reads();

        new WholePackStore(STRATEGY).loadStored(pack);

        assertThat(PackFileSet.walks() - walks).isOne();
        assertThat(FilesystemPackStore.reads() - before).isOne();
    }

    @Test
    @DisplayName("the composing read walks and reads the pack once, through the one reader's StoreRead half")
    void theComposingReadReadsOnce(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        DomainRepositoryService service = new DomainRepositoryService(
                () -> List.of(new PackLocation(ExamplePacks.BUILT_IN, ExamplePacks.level(ExamplePacks.BUILT_IN), pack)),
                STRATEGY);
        long walks = PackFileSet.walks();
        long before = FilesystemPackStore.reads();
        long runs = StoreRead.runs();

        PackCompositionFacts facts = service.compositionFacts(new PackIdentifier(ExamplePacks.BUILT_IN));

        assertThat(PackFileSet.walks() - walks).isOne();
        assertThat(FilesystemPackStore.reads() - before).isOne();
        assertThat(StoreRead.runs() - runs)
                .as("the composing read runs the one reader's StoreRead half (operator decision 2026-11-17)")
                .isOne();
        assertThat(facts.defined()).isNotEmpty();
    }

    @Test
    @DisplayName("the pack under resolution walks and reads the pack once, through the one reader's StoreRead half")
    void thePackUnderResolutionReadsOnce(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        ConfiguredPack configured = new ConfiguredPack(
                new PackIdentifier(ExamplePacks.BUILT_IN), ExamplePacks.level(ExamplePacks.BUILT_IN));
        long walks = PackFileSet.walks();
        long before = FilesystemPackStore.reads();
        long runs = StoreRead.runs();

        CompositionFactsBuilder.underResolution(configured, pack, STRATEGY);

        assertThat(PackFileSet.walks() - walks).isOne();
        assertThat(FilesystemPackStore.reads() - before).isOne();
        assertThat(StoreRead.runs() - runs).isOne();
    }

    @Test
    @DisplayName("the one reader walks the tree once: the validate facts, the census and the assembly input share it")
    void theOneReaderWalksOnce(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        long walks = PackFileSet.walks();
        long before = FilesystemPackStore.reads();

        new FileSystemPackInspector()
                .readTree(new ai.speckitty.charter.packs.domain.PackRoot(
                        "built-in", java.util.Optional.empty(), pack.toString()));

        assertThat(PackFileSet.walks() - walks).isOne();
        assertThat(FilesystemPackStore.reads() - before).isOne();
    }

    @Test
    @DisplayName("the composition facts are built from a read already made, and read nothing")
    void theFactsBuilderReadsNothing(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        FilesystemPackStore store = new FilesystemPackStore(pack.getParent(), STRATEGY);
        PackIdentifier key = new PackIdentifier(String.valueOf(pack.getFileName()));
        FilesystemPackStore.GraphRead graphs = store.readGraphs(key);
        FilesystemPackStore.PackRead read = store.read(key, graphs);
        Pack assembled = StoreRead.assembly(key, read, new HashMap<>(), graphs, new ArrayList<>())
                .build();
        long before = FilesystemPackStore.reads();

        CompositionFactsBuilder.build(key, assembled, read, graphs.documents(), MutabilityPolicySource.empty());

        assertThat(FilesystemPackStore.reads() - before).isZero();
    }
}
