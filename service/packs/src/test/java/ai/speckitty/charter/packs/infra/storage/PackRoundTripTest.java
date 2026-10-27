package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.PackFixtures.ALPHA_FILE;
import static ai.speckitty.charter.packs.infra.storage.PackFixtures.BETA_FILE;
import static ai.speckitty.charter.packs.infra.storage.PackFixtures.BROKEN_FILE;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * NFR-004 / FR-012: the semantic round-trip of a pack at the persistence-shape level. The fidelity assertion is the
 * value equality of the DSOs, checked against fixture literals (so a field the reader drops fails) and across a write
 * to a fresh directory (so a field the writer drops fails). Distinct from the byte-level {@code OpenPacksRoundTripTest}.
 */
class PackRoundTripTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private static final String ALPHA_ID = "alpha-tactic";
    private static final String BETA_ID = "beta-tactic";

    private static final PackDescriptorDso PACK_A_DESCRIPTOR = new PackDescriptorDso(
            "01M5DEH8ROUNDTRIPFIXTURE0A",
            "1.2.3",
            Optional.empty(),
            "roundtrip-a",
            Optional.of("org"),
            Optional.of(">=1.0"),
            Optional.of("org-charter.yaml"),
            Optional.of("drg/fragment.yaml"),
            List.of());

    private static final TacticDso ALPHA = new TacticDso(
            ALPHA_ID,
            "1.0",
            "Alpha tactic",
            List.of(
                    new TacticDso.Step(
                            "First step",
                            Optional.of("Do the first thing."),
                            List.of("first example", "second example"),
                            List.of(new TacticDso.Reference(
                                    "Nested reference", "directive", "nested-directive", "during the first step"))),
                    new TacticDso.Step("Second step", Optional.empty(), List.of(), List.of())),
            Optional.of("Prove that every field survives."),
            Optional.empty(),
            List.of("drifts silently", "drops a field"),
            List.of(),
            List.of(
                    new TacticDso.Reference("Gamma", "tactic", "gamma-tactic", "always"),
                    new TacticDso.Reference("Delta", "styleguide", "delta-style", "when styling"),
                    new TacticDso.Reference("Gamma again", "tactic", "gamma-tactic", "on a second look")));

    private static final TacticDso BETA = new TacticDso(
            BETA_ID,
            "1.0",
            "Beta tactic",
            List.of(new TacticDso.Step("Only step", Optional.empty(), List.of(), List.of())),
            Optional.empty(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of());

    private static FilesystemPackStore storeOver(Path root) {
        return new FilesystemPackStore(root, new YamlSerializationStrategy());
    }

    private static TacticDso tactic(FilesystemPackStore.PackRead read, String id) {
        return read.tactics().stream()
                .filter(t -> t.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no tactic " + id + " in " + read.tactics()));
    }

    /**
     * Writes everything {@code original} holds into a fresh pack directory that has none of its content: a
     * placeholder {@code pack.yaml} and an id-only placeholder per tactic file (a write replaces by id only).
     */
    private static FilesystemPackStore.PackRead roundTrip(FilesystemPackStore.PackRead original, Path fresh)
            throws IOException {
        Path pack = Files.createDirectories(fresh.resolve(PACK.value()));
        Files.writeString(pack.resolve("pack.yaml"), "pack_id: placeholder\n");
        for (TacticDso tactic : original.tactics()) {
            Files.writeString(pack.resolve(tactic.id() + ".tactic.yaml"), "id: " + tactic.id() + "\n");
        }
        FilesystemPackStore store = storeOver(fresh);
        store.writeDescriptor(PACK, original.descriptor());
        original.tactics().forEach(t -> store.writeTactic(PACK, t));
        return store.read(PACK);
    }

    /** The literal oracle: read values are the fixture's, so a field the reader drops fails here. */
    @Test
    void aFixturePackReadsAsTheFixtureLiterals(@TempDir Path host) throws IOException {
        PackFixtures.copyOf("pack-a", host);

        FilesystemPackStore.PackRead read = storeOver(host).read(PACK);

        assertThat(read.descriptor()).isEqualTo(PACK_A_DESCRIPTOR);
        assertThat(read.tactics()).containsExactly(ALPHA, BETA);
    }

    /** The oracle bites: dropping one nested reference from the literal makes it differ from what was read. */
    @Test
    void theLiteralOracleDistinguishesADroppedNestedReference(@TempDir Path host) throws IOException {
        PackFixtures.copyOf("pack-a", host);
        TacticDso readAlpha = tactic(storeOver(host).read(PACK), ALPHA_ID);
        List<TacticDso.Step> withoutNested = List.of(
                new TacticDso.Step(
                        "First step",
                        Optional.of("Do the first thing."),
                        List.of("first example", "second example"),
                        List.of()),
                readAlpha.steps().get(1));
        TacticDso droppedField = new TacticDso(
                readAlpha.id(),
                readAlpha.schemaVersion().orElseThrow(),
                readAlpha.name(),
                withoutNested,
                readAlpha.purpose(),
                readAlpha.notes(),
                readAlpha.failureModes(),
                readAlpha.appliesToLanguages(),
                readAlpha.references());

        assertThat(droppedField).isNotEqualTo(readAlpha);
        assertThat(readAlpha).isEqualTo(ALPHA);
    }

    /** FR-012: the descriptor and every valid tactic survive a write to a fresh directory, order and duplicates too. */
    @Test
    void aWrittenPackRefetchesEqualToTheOriginal(@TempDir Path host) throws IOException {
        PackFixtures.copyOf("pack-a", host.resolve("source"));
        FilesystemPackStore.PackRead original =
                storeOver(host.resolve("source")).read(PACK);

        FilesystemPackStore.PackRead refetched = roundTrip(original, host.resolve("fresh"));

        assertThat(refetched.tactics()).isEqualTo(original.tactics());
        assertThat(refetched.descriptor()).isEqualTo(original.descriptor());
        // active_charter and pack_drg would be deleted by a from-scratch writer that did not model them.
        assertThat(refetched.descriptor().activeCharter()).contains("org-charter.yaml");
        assertThat(refetched.descriptor().packDrg()).contains("drg/fragment.yaml");
        // The repeated reference id and the reference order of the alpha tactic.
        assertThat(tactic(refetched, ALPHA_ID).references())
                .extracting(TacticDso.Reference::name)
                .containsExactly("Gamma", "Delta", "Gamma again");
        // Present optionals stay present, absent ones stay absent.
        assertThat(tactic(refetched, ALPHA_ID).purpose()).contains("Prove that every field survives.");
        assertThat(tactic(refetched, ALPHA_ID).notes()).isEmpty();
    }

    /** Mutation control for the test above: a mutated DSO refetches differently, so the write path is load-bearing. */
    @Test
    void aMutatedTacticWrittenToAFreshPackRefetchesDifferently(@TempDir Path host) throws IOException {
        PackFixtures.copyOf("pack-a", host.resolve("source"));
        FilesystemPackStore.PackRead original =
                storeOver(host.resolve("source")).read(PACK);
        TacticDso alpha = tactic(original, ALPHA_ID);
        TacticDso mutated = new TacticDso(
                alpha.id(),
                alpha.schemaVersion().orElseThrow(),
                "Alpha tactic, renamed",
                alpha.steps(),
                alpha.purpose(),
                alpha.notes(),
                alpha.failureModes(),
                alpha.appliesToLanguages(),
                alpha.references());
        FilesystemPackStore.PackRead withMutation = new FilesystemPackStore.PackRead(
                original.descriptor(),
                original.tactics().stream()
                        .map(t -> t.id().equals(ALPHA_ID) ? mutated : t)
                        .toList(),
                original.violations());

        FilesystemPackStore.PackRead refetched = roundTrip(withMutation, host.resolve("fresh"));

        assertThat(refetched.tactics()).isNotEqualTo(original.tactics());
        assertThat(tactic(refetched, ALPHA_ID).name()).isEqualTo("Alpha tactic, renamed");
        assertThat(tactic(refetched, BETA_ID)).isEqualTo(BETA);
    }

    /** The same control on the descriptor: a changed level refetches changed. */
    @Test
    void aMutatedDescriptorWrittenToAFreshPackRefetchesDifferently(@TempDir Path host) throws IOException {
        PackFixtures.copyOf("pack-a", host.resolve("source"));
        FilesystemPackStore.PackRead original =
                storeOver(host.resolve("source")).read(PACK);
        PackDescriptorDso d = original.descriptor();
        PackDescriptorDso mutated = new PackDescriptorDso(
                d.packId(),
                d.packVersion(),
                d.parentPack(),
                d.name(),
                Optional.of("project"),
                d.requiresCore(),
                d.activeCharter(),
                d.packDrg(),
                d.undeclaredKeys());

        FilesystemPackStore.PackRead refetched =
                roundTrip(new FilesystemPackStore.PackRead(mutated, List.of(), List.of()), host.resolve("fresh"));

        assertThat(refetched.descriptor()).isNotEqualTo(original.descriptor());
        assertThat(refetched.descriptor().packLevel()).contains("project");
    }

    /** The pinned exception: undeclared keys are captured on read and dropped on write-back. */
    @Test
    void anUndeclaredDescriptorKeyIsCapturedOnReadButDroppedOnWriteBack(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-undeclared", host);
        PackIdentifier id = new PackIdentifier(pack.getFileName().toString());
        FilesystemPackStore store = storeOver(host);

        PackDescriptorDso read = store.read(id).descriptor();
        assertThat(read.undeclaredKeys()).containsExactly("experimental_note");

        store.writeDescriptor(id, read);
        PackDescriptorDso refetched = store.read(id).descriptor();

        assertThat(refetched.undeclaredKeys()).isEmpty();
        assertThat(refetched.packId()).isEqualTo(read.packId());
        assertThat(refetched.name()).isEqualTo(read.name());
    }

    /** Partial tolerance: the broken file is reported, and the valid elements are still returned (the control). */
    @Test
    void aBrokenTacticIsReportedWhileTheValidElementsAreStillReturned(@TempDir Path host) throws IOException {
        PackFixtures.copyOf("pack-a", host);

        FilesystemPackStore.PackRead read = storeOver(host).read(PACK);

        assertThat(read.tactics()).extracting(TacticDso::id).containsExactly(ALPHA_ID, BETA_ID);
        assertThat(read.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(BROKEN_FILE);
            assertThat(violation.kind()).isEqualTo("schema");
        });
        assertThat(Files.exists(host.resolve("pack-a").resolve(ALPHA_FILE))).isTrue();
        assertThat(Files.exists(host.resolve("pack-a").resolve(BETA_FILE))).isTrue();
    }
}
