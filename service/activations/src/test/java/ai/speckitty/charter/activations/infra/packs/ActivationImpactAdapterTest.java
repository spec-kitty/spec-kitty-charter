package ai.speckitty.charter.activations.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.infra.storage.FileActivationRepository;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The adapter that tells the registration impact check which packs the stored activations root at (IC-15, FR-025): each
 * stored activation's {@code packs} list, in the order written, and an empty optional for one that lists none.
 */
class ActivationImpactAdapterTest {

    private static ActivationDocument listing(PackRef... packs) {
        return ActivationDocument.builder()
                .packs(List.of(packs))
                .selected(ArtifactKind.TACTIC, List.of("t-one"))
                .build()
                .document();
    }

    private static ActivationDocument listingNone() {
        return ActivationDocument.builder()
                .selected(ArtifactKind.TACTIC, List.of("t-one"))
                .build()
                .document();
    }

    @Test
    void noStoredActivationHasNoRootSet() {
        assertThat(new ActivationImpactAdapter(new InMemoryActivationRepository()).activationRoots())
                .isEmpty();
    }

    @Test
    void eachStoredActivationGivesItsPacksInTheOrderWrittenAndOneWithNoneGivesEmpty() {
        InMemoryActivationRepository repository = new InMemoryActivationRepository();
        repository.create(new ActivationId("one"), listing(PackRef.of("beta"), PackRef.pinned("alpha", "1.2")));
        repository.create(new ActivationId("two"), listingNone());

        assertThat(new ActivationImpactAdapter(repository).activationRoots())
                .containsExactlyInAnyOrder(Optional.of(List.of("beta", "alpha")), Optional.empty());
    }

    @Test
    void theFileStoreIsListedToo(@TempDir Path root) {
        FileActivationRepository repository = new FileActivationRepository(root.resolve("activations"));
        repository.create(new ActivationId("file-one"), listing(PackRef.of("main")));

        assertThat(new ActivationImpactAdapter(repository).activationRoots())
                .containsExactly(Optional.of(List.of("main")));
    }
}
