package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
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
 * One read per surface call (#554, S1, T028, NFR-004): a counting seam on the store's whole read
 * ({@link FilesystemPackStore#reads()}) sees one read of the staged tree per registration load and one per composing
 * read, and the composition facts are built from that read without reading again. The second read the registration
 * made through {@code contentsOf} and a single-pack {@link DomainRepositoryService} is gone.
 */
@DisplayName("one read of a pack per surface call (#554, T028)")
class OneReadTest {

    private static final YamlSerializationStrategy STRATEGY = new YamlSerializationStrategy();

    @Test
    @DisplayName("a registration's load reads the staged tree once, its composition facts included")
    void theRegistrationLoadReadsOnce(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(ExamplePacks.ORG, host);
        long before = FilesystemPackStore.reads();

        WholePackWriteStore.Contents contents = new WholePackStore(STRATEGY).loadStaged(new StagedPack(pack));

        assertThat(FilesystemPackStore.reads() - before).isOne();
        assertThat(contents.facts().defined()).isNotEmpty();
    }

    @Test
    @DisplayName("a stored pack's load reads it once, its composition facts included")
    void theStoredLoadReadsOnce(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        long before = FilesystemPackStore.reads();

        new WholePackStore(STRATEGY).loadStored(pack);

        assertThat(FilesystemPackStore.reads() - before).isOne();
    }

    @Test
    @DisplayName("the composing read reads the pack once")
    void theComposingReadReadsOnce(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        DomainRepositoryService service = new DomainRepositoryService(
                () -> List.of(new PackLocation(ExamplePacks.BUILT_IN, ExamplePacks.level(ExamplePacks.BUILT_IN), pack)),
                STRATEGY);
        long before = FilesystemPackStore.reads();

        PackCompositionFacts facts = service.compositionFacts(new PackIdentifier(ExamplePacks.BUILT_IN));

        assertThat(FilesystemPackStore.reads() - before).isOne();
        assertThat(facts.defined()).isNotEmpty();
    }

    @Test
    @DisplayName("the composition facts are built from a read already made, and read nothing")
    void theFactsBuilderReadsNothing(@TempDir Path host) {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
        FilesystemPackStore store = new FilesystemPackStore(pack.getParent(), STRATEGY);
        PackIdentifier key = new PackIdentifier(String.valueOf(pack.getFileName()));
        FilesystemPackStore.GraphRead graphs = store.readGraphs(key);
        FilesystemPackStore.PackRead read = store.read(key, graphs);
        Pack assembled = LoadChecks.assembly(key, read, new HashMap<>(), graphs, new ArrayList<>())
                .build();
        long before = FilesystemPackStore.reads();

        CompositionFactsBuilder.build(key, assembled, read, graphs.documents(), MutabilityPolicySource.empty());

        assertThat(FilesystemPackStore.reads() - before).isZero();
    }
}
