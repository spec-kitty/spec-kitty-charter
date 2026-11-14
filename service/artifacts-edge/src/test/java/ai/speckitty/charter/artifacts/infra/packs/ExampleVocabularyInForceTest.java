package ai.speckitty.charter.artifacts.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Outcome;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.artifacts.application.ActiveTerm;
import ai.speckitty.charter.artifacts.application.ContextiveProjection;
import ai.speckitty.charter.artifacts.application.ContextsInForce;
import ai.speckitty.charter.artifacts.application.TabooProjection;
import ai.speckitty.charter.artifacts.application.VocabularyReads;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.application.ClosureQueries;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ForeignArtifactRead;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.ProfileGovernance;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.PackLocation;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * NFR-005 end to end over charter's example vocabulary (ADR-0070): the three vocabulary reads of an activation that
 * holds the built-in example pack's {@code spec-kitty-core} in force compose the application ring, the production
 * {@link PacksVocabulary} adapter and the packs slice's own read over the real store, and each reads the winning pack
 * exactly once, however many terms the context holds. The positive control is the size: all 139 terms of the file are
 * served, and the projections are of those 139 terms. The activations port is the one stub: it names the context in
 * force and the source fingerprint, as one activation session would.
 */
class ExampleVocabularyInForceTest {

    private static final String FINGERPRINT = "sha256:" + "a".repeat(64);
    private static final String TAG = "sha256:" + "e".repeat(64);
    private static final int TERMS = 139;
    private static final PackIdentifier BUILT_IN = new PackIdentifier(ExamplePacks.BUILT_IN);
    private static final Held KITTY = new Held("spec-kitty-core", BUILT_IN, PackLevel.BUILT_IN, "locked");

    /** The packs slice's read over the real store of the three example packs, counted. */
    private final ForeignArtifactRead packs = spy(examplePacks());

    private final ActivationArtifacts activations = mock(ActivationArtifacts.class);

    private static ForeignArtifactRead examplePacks() {
        List<PackLocation> locations = ExamplePacks.names().stream()
                .map(name -> new PackLocation(name, ExamplePacks.level(name), ExamplePacks.pack(name)))
                .toList();
        DomainRepositoryService store = new DomainRepositoryService(() -> locations, new YamlSerializationStrategy());
        ConsistentRead read = new ConsistentRead(() -> FINGERPRINT);
        OfferingReadService offering = new OfferingReadService(store, store, store, read);
        ClosureQueries queries = new ClosureQueries(offering, store, read, store, store);
        return new ForeignArtifactRead(
                store,
                store,
                store,
                read,
                pack -> PackRevision.compute(List.of(pack.value())),
                new ProfileGovernance(queries));
    }

    private VocabularyReads reads() {
        when(activations.contextsInForce(any(), any(), isNull()))
                .thenReturn(new Outcome.Found<>(
                        new ContextsInForce(
                                new Scope("a-1", "sha256:rev", "sha256:closure", FINGERPRINT), List.of(KITTY)),
                        TAG));
        return new VocabularyReads(activations, new PacksVocabulary(packs));
    }

    private static <T> T found(Outcome<T> outcome) {
        assertThat(outcome).isInstanceOf(Outcome.Found.class);
        return ((Outcome.Found<T>) outcome).value();
    }

    private void readTheWinningPackOnce() {
        verify(packs, times(1)).held(BUILT_IN, ArtifactKind.GLOSSARY);
        verifyNoMoreInteractions(packs);
    }

    @Test
    void theTermsAreAll139TermsOfTheExampleVocabularyFromOneReadOfTheWinningPack() {
        List<ActiveTerm> terms = found(reads().terms("a-1", null)).terms();

        readTheWinningPackOnce();
        assertThat(terms).as("the positive control: every term of the file").hasSize(TERMS);
        assertThat(terms)
                .allSatisfy(term -> {
                    assertThat(term.contextId()).isEqualTo("spec-kitty-core");
                    assertThat(term.packId()).isEqualTo(BUILT_IN);
                    assertThat(term.urn()).isEqualTo("term:spec-kitty-core/" + term.termId());
                })
                .extracting(ActiveTerm::termId)
                .isSorted()
                .doesNotHaveDuplicates();
        assertThat(terms)
                .extracting(ActiveTerm::status)
                .as("every status is served")
                .contains("active", "draft", "deprecated");
    }

    @Test
    void theContextiveDocumentHoldsThe139TermsFromOneReadOfTheWinningPack() {
        ContextiveProjection.Document document =
                found(reads().contextive("a-1", null)).document();

        readTheWinningPackOnce();
        assertThat(document.contexts()).singleElement().satisfies(context -> {
            assertThat(context.name()).isEqualTo("Spec Kitty core");
            assertThat(context.paths()).contains(List.of("kitty-specs/**", ".kittify/**"));
            assertThat(context.terms()).hasSize(TERMS);
        });
    }

    @Test
    void theTabooListIsOfThe139TermsFromOneReadOfTheWinningPack() {
        List<TabooProjection.Entry> entries = found(reads().taboos("a-1", null)).entries();

        readTheWinningPackOnce();
        assertThat(entries)
                .isNotEmpty()
                .allSatisfy(entry -> assertThat(entry.term()).startsWith("term:spec-kitty-core/"));
    }
}
