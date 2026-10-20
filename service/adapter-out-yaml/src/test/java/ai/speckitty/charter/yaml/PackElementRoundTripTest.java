package ai.speckitty.charter.yaml;

import static ai.speckitty.charter.yaml.PackFixtures.ALPHA_FILE;
import static ai.speckitty.charter.yaml.PackFixtures.BETA_FILE;
import static ai.speckitty.charter.yaml.PackFixtures.BROKEN_FILE;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.domain.PackDescriptor;
import ai.speckitty.charter.domain.PackElementRepository;
import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.domain.PackLocator;
import ai.speckitty.charter.domain.PackSnapshot;
import ai.speckitty.charter.domain.Tactic;
import ai.speckitty.charter.domain.TacticReference;
import ai.speckitty.charter.domain.TacticStep;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The semantic (domain-model) round-trip of a pack through {@link PackElementRepository}. Distinct from the byte-level
 * {@code OpenPacksRoundTripTest}: it is the proof of fetch to write to fetch equality.
 */
class PackElementRoundTripTest {

    private static final String BETA_ID = "beta-tactic";
    private static final String ALPHA_ID = "alpha-tactic";

    private final PackElementRepository repository = new FilesystemPackElementRepository();

    private static PackLocator at(Path pack) {
        return new PackLocator(pack.toString());
    }

    private static Tactic tactic(PackSnapshot snapshot, String id) {
        return snapshot.tactics().stream()
                .filter(t -> t.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no tactic " + id + " in " + snapshot.tactics()));
    }

    /** The pack at {@code source} fetched, written to a fresh directory that holds nothing of it, and refetched. */
    private PackSnapshot roundTrip(PackSnapshot original, Path host) throws IOException {
        Path fresh = PackFixtures.placeholderOf("pack-a", host, original.tactics(), List.of(ALPHA_FILE, BETA_FILE));
        PackLocator location = at(fresh);
        repository.write(location, original.descriptor());
        original.tactics().forEach(t -> repository.write(location, t));
        return repository.fetch(location);
    }

    /** FR-002: fetched values are the fixture's literals, so a field the reader drops fails here. */
    @Test
    void fetchReturnsTheFixtureLiterals(@TempDir Path host) throws IOException {
        PackSnapshot snapshot = repository.fetch(at(PackFixtures.copyOf("pack-a", host)));

        assertThat(snapshot.descriptor())
                .isEqualTo(new PackDescriptor(
                        "01M5DEH8ROUNDTRIPFIXTURE0A",
                        "1.2.3",
                        Optional.empty(),
                        "roundtrip-a",
                        Optional.of(PackLevel.ORG),
                        Optional.of(">=1.0"),
                        List.of(),
                        Optional.of("org-charter.yaml"),
                        Optional.of("drg/fragment.yaml")));
        assertThat(tactic(snapshot, ALPHA_ID))
                .isEqualTo(new Tactic(
                        ALPHA_ID,
                        "1.0",
                        "Alpha tactic",
                        List.of(
                                new TacticStep(
                                        "First step",
                                        Optional.of("Do the first thing."),
                                        List.of("first example", "second example"),
                                        List.of(new TacticReference(
                                                "Nested reference",
                                                "directive",
                                                "nested-directive",
                                                "during the first step"))),
                                new TacticStep("Second step", Optional.empty(), List.of(), List.of())),
                        Optional.of("Prove that every field survives."),
                        Optional.empty(),
                        List.of("drifts silently", "drops a field"),
                        List.of(),
                        List.of(
                                new TacticReference("Gamma", "tactic", "gamma-tactic", "always"),
                                new TacticReference("Delta", "styleguide", "delta-style", "when styling"),
                                new TacticReference("Gamma again", "tactic", "gamma-tactic", "on a second look"))));
        assertThat(tactic(snapshot, BETA_ID))
                .isEqualTo(new Tactic(
                        BETA_ID,
                        "1.0",
                        "Beta tactic",
                        List.of(new TacticStep("Only step", Optional.empty(), List.of(), List.of())),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        List.of()));
    }

    /** FR-005, FR-006, D8: the descriptor and every valid tactic survive a write to a fresh directory. */
    @Test
    void aWrittenPackRefetchesEqualToTheOriginal(@TempDir Path host) throws IOException {
        PackSnapshot original = repository.fetch(at(PackFixtures.copyOf("pack-a", host.resolve("source"))));

        PackSnapshot refetched = roundTrip(original, host.resolve("fresh"));

        // Equality is order- and duplicate-sensitive, so this also guards the references order and the repeated
        // reference id of the alpha tactic (the order and duplicate guard for FR-006).
        assertThat(refetched.tactics()).isEqualTo(original.tactics());
        // active_charter and pack_drg would be deleted by a from-scratch writer that did not model them (D8).
        assertThat(refetched.descriptor()).isEqualTo(original.descriptor());
        assertThat(refetched.descriptor().activeCharter()).contains("org-charter.yaml");
        assertThat(refetched.descriptor().packDrg()).contains("drg/fragment.yaml");
        // FR-006, same fixture: present optionals stay present with their value, absent ones stay absent.
        Tactic alpha = tactic(refetched, ALPHA_ID);
        assertThat(alpha.purpose()).contains("Prove that every field survives.");
        assertThat(alpha.notes()).isEmpty();
        assertThat(alpha.appliesToLanguages()).isEmpty();
    }

    /** The control: a mutation written through the port is visible on refetch, so equality above can fail. */
    @Test
    void aMutationWrittenToAFreshPackChangesWhatRefetches(@TempDir Path host) throws IOException {
        PackSnapshot original = repository.fetch(at(PackFixtures.copyOf("pack-a", host.resolve("source"))));
        Tactic alpha = tactic(original, ALPHA_ID);
        Tactic mutated = new Tactic(
                alpha.id(),
                alpha.schemaVersion(),
                "Alpha tactic, renamed",
                alpha.steps(),
                alpha.purpose(),
                alpha.notes(),
                alpha.failureModes(),
                alpha.appliesToLanguages(),
                alpha.references());
        PackSnapshot withMutation = new PackSnapshot(
                original.descriptor(),
                original.tactics().stream()
                        .map(t -> t.id().equals(ALPHA_ID) ? mutated : t)
                        .toList(),
                original.violations());

        PackSnapshot refetched = roundTrip(withMutation, host.resolve("fresh"));

        assertThat(refetched.tactics()).isNotEqualTo(original.tactics());
        assertThat(tactic(refetched, ALPHA_ID).name()).isEqualTo("Alpha tactic, renamed");
    }

    /** FR-007: a write replaces the existing tactic in place, and leaves the rest of the pack as it was. */
    @Test
    void writingAnExistingTacticReplacesItInPlace(@TempDir Path host) throws IOException {
        PackLocator location = at(PackFixtures.copyOf("pack-a", host));
        Tactic beta = tactic(repository.fetch(location), BETA_ID);
        Tactic mutated = new Tactic(
                beta.id(),
                beta.schemaVersion(),
                beta.name(),
                beta.steps(),
                Optional.of("now has a purpose"),
                beta.notes(),
                beta.failureModes(),
                beta.appliesToLanguages(),
                beta.references());

        repository.write(location, mutated);

        PackSnapshot after = repository.fetch(location);
        assertThat(tactic(after, BETA_ID)).isEqualTo(mutated);
        assertThat(after.tactics()).hasSize(2);
        assertThat(tactic(after, ALPHA_ID).name()).isEqualTo("Alpha tactic");
    }

    /** FR-009: the empty collection is empty, not null; equality cannot tell, since absent and empty collapse. */
    @Test
    void aDescriptorOnlyPackRoundTripsWithAnEmptyTacticCollection(@TempDir Path host) throws IOException {
        PackSnapshot original = repository.fetch(at(PackFixtures.copyOf("pack-descriptor-only", host.resolve("a"))));

        PackLocator fresh =
                at(PackFixtures.placeholderOf("pack-descriptor-only", host.resolve("b"), List.of(), List.of()));
        repository.write(fresh, original.descriptor());
        PackSnapshot refetched = repository.fetch(fresh);

        assertThat(original.tactics()).isNotNull().isEmpty();
        assertThat(refetched.tactics()).isNotNull().isEmpty();
        assertThat(refetched.descriptor()).isEqualTo(original.descriptor());
    }

    /** FR-008: one fetch reports the bad file and still returns the valid descriptor and tactics. */
    @Test
    void anInvalidTacticIsReportedWhileTheValidElementsAreStillReturned(@TempDir Path host) throws IOException {
        PackSnapshot snapshot = repository.fetch(at(PackFixtures.copyOf("pack-a", host)));

        assertThat(snapshot.descriptor().name()).isEqualTo("roundtrip-a");
        assertThat(snapshot.tactics()).extracting(Tactic::id).containsExactly(ALPHA_ID, BETA_ID);
        assertThat(snapshot.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(BROKEN_FILE);
            assertThat(violation.kind()).isEqualTo("schema");
        });
    }
}
