package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ActivationRepository;
import ai.speckitty.charter.activations.domain.ActivationRepository.CasResult;
import ai.speckitty.charter.activations.domain.ActivationRepository.DeleteResult;
import ai.speckitty.charter.activations.domain.Jcs;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.Revision;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The contract of {@link ActivationRepository} (FR-019, FR-003, NFR-004) every adapter passes: each subclass answers
 * a fresh, empty repository.
 */
abstract class ActivationRepositoryContract {

    /** The iterations of the concurrency proof: 100 of 100 runs (NFR-004). */
    static final int RUNS = 100;

    /** The concurrent writers holding one revision. */
    static final int WRITERS = 8;

    private static final ActivationId ID = new ActivationId("01HZX3K9Q4M7N2P5R8S1T6V0W3");
    private static final ActivationId OTHER = new ActivationId("other-activation");

    /** A version 1 document, as the store kept it before version 2. */
    private static final byte[] VERSION_ONE = ("{\"activatedKinds\":[\"tactic\"],\"languages\":[\"lang-1\"],"
                    + "\"missionTypes\":[\"mission-1\"],\"packs\":[{\"packId\":\"pack-1\",\"version\":\"1.1\"}],"
                    + "\"schemaVersion\":1,\"selected\":{\"tactic\":[\"tactic-1\"]}}")
            .getBytes(StandardCharsets.UTF_8);

    private ActivationRepository repository;

    /**
     * A fresh, empty repository.
     *
     * @return the repository under test
     */
    abstract ActivationRepository repository();

    @BeforeEach
    void freshRepository() {
        repository = repository();
    }

    /**
     * Stores bytes as the repository keeps them, bypassing its writes, so a version 1 document can be seeded.
     *
     * @param repository the repository {@link #repository()} answered
     * @param id the activation's id
     * @param bytes the stored form
     */
    abstract void storeBytes(ActivationRepository repository, ActivationId id, byte[] bytes);

    /**
     * The bytes the repository keeps for an id.
     *
     * @param repository the repository {@link #repository()} answered
     * @param id the activation's id
     * @return the stored form
     */
    abstract byte[] storedBytes(ActivationRepository repository, ActivationId id);

    /** A document distinct for each {@code n}. */
    static ActivationDocument document(int n) {
        return ActivationDocument.builder()
                .packs(List.of(PackRef.pinned("pack-" + n, "1." + n)))
                .selected(ArtifactKind.TACTIC, List.of("tactic-" + n))
                .selected(ArtifactKind.MISSION_TYPE, List.of("mission-" + n))
                .languages(List.of("lang-" + n))
                .build()
                .document();
    }

    @Test
    void aVersionOneDocumentReadsAsItsUpgradeAndTheReadsNeverWrite() {
        storeBytes(repository, ID, VERSION_ONE);

        StoredActivation first = repository.find(ID).orElseThrow();
        StoredActivation second = repository.find(ID).orElseThrow();

        assertThat(first).isEqualTo(StoredActivation.of(ID, document(1))).isEqualTo(second);
        assertThat(first.document().schemaVersion()).isEqualTo(2);
        assertThat(storedBytes(repository, ID)).isEqualTo(VERSION_ONE);
    }

    @Test
    void theNextWriteStoresTheUpgradeAtVersionTwo() {
        storeBytes(repository, ID, VERSION_ONE);
        Revision read = repository.find(ID).orElseThrow().revision();

        CasResult result = repository.replace(ID, Optional.of(read), document(1));

        assertThat(result).isEqualTo(new CasResult.Written(StoredActivation.of(ID, document(1)), false));
        assertThat(storedBytes(repository, ID)).isEqualTo(Jcs.canonical(document(1).toCanonicalTree()));
        assertThat(new String(storedBytes(repository, ID), StandardCharsets.UTF_8)).contains("\"schemaVersion\":2");
        assertThat(repository.find(ID)).contains(StoredActivation.of(ID, document(1)));
    }

    @Test
    void aRevisionOfTheVersionOneBytesIsStaleOnceAndWritesNothing() {
        storeBytes(repository, ID, VERSION_ONE);

        CasResult result = repository.replace(ID, Optional.of(Revision.of(VERSION_ONE)), document(2));

        assertThat(result).isEqualTo(new CasResult.Stale(document(1).revision()));
        assertThat(storedBytes(repository, ID)).isEqualTo(VERSION_ONE);
    }

    @Test
    void aVersionOneDocumentIsDeletedWithTheRevisionOfItsUpgrade() {
        storeBytes(repository, ID, VERSION_ONE);

        assertThat(repository.delete(ID, Optional.of(document(1).revision()))).isEqualTo(new DeleteResult.Deleted());
        assertThat(repository.find(ID)).isEmpty();
    }

    @Test
    void createThenFindAnswersTheSameDocumentAndRevision() {
        Optional<StoredActivation> created = repository.create(ID, document(1));

        assertThat(created)
                .contains(new StoredActivation(ID, document(1), document(1).revision()));
        assertThat(repository.find(ID)).isEqualTo(created);
    }

    @Test
    void findOfAnUnknownIdIsEmpty() {
        assertThat(repository.find(ID)).isEmpty();
    }

    @Test
    void createOnAnExistingIdIsEmptyAndLeavesTheStoredDocument() {
        repository.create(ID, document(1));

        assertThat(repository.create(ID, document(2))).isEmpty();
        assertThat(repository.find(ID)).map(StoredActivation::document).contains(document(1));
    }

    @Test
    void replaceWithTheCurrentRevisionWrites() {
        Revision current = repository.create(ID, document(1)).orElseThrow().revision();

        CasResult result = repository.replace(ID, Optional.of(current), document(2));

        assertThat(result).isEqualTo(new CasResult.Written(StoredActivation.of(ID, document(2)), false));
        assertThat(repository.find(ID)).map(StoredActivation::document).contains(document(2));
    }

    @Test
    void replaceWithAStaleRevisionAnswersTheCurrentOneAndWritesNothing() {
        Revision first = repository.create(ID, document(1)).orElseThrow().revision();
        repository.replace(ID, Optional.of(first), document(2));

        CasResult result = repository.replace(ID, Optional.of(first), document(3));

        assertThat(result).isEqualTo(new CasResult.Stale(document(2).revision()));
        assertThat(repository.find(ID)).map(StoredActivation::document).contains(document(2));
    }

    @Test
    void replaceWithoutARevisionLetsTheLastWriterWin() {
        repository.create(ID, document(1));

        CasResult result = repository.replace(ID, Optional.empty(), document(2));

        assertThat(result).isEqualTo(new CasResult.Written(StoredActivation.of(ID, document(2)), false));
        assertThat(repository.find(ID)).map(StoredActivation::document).contains(document(2));
    }

    @Test
    void replaceWithoutARevisionCreatesAMissingId() {
        CasResult result = repository.replace(ID, Optional.empty(), document(1));

        assertThat(result).isEqualTo(new CasResult.Written(StoredActivation.of(ID, document(1)), true));
        assertThat(repository.find(ID)).map(StoredActivation::document).contains(document(1));
    }

    @Test
    void replaceWithARevisionOfAMissingIdIsMissingAndWritesNothing() {
        CasResult result = repository.replace(ID, Optional.of(document(1).revision()), document(1));

        assertThat(result).isEqualTo(new CasResult.Missing());
        assertThat(repository.find(ID)).isEmpty();
    }

    @Test
    void deleteWithTheCurrentRevisionDeletesAndThenTheIdIsMissing() {
        Revision current = repository.create(ID, document(1)).orElseThrow().revision();

        assertThat(repository.delete(ID, Optional.of(current))).isEqualTo(new DeleteResult.Deleted());
        assertThat(repository.find(ID)).isEmpty();
        assertThat(repository.delete(ID, Optional.of(current))).isEqualTo(new DeleteResult.Missing());
        assertThat(repository.delete(ID, Optional.empty())).isEqualTo(new DeleteResult.Missing());
    }

    @Test
    void deleteWithAStaleRevisionAnswersTheCurrentOneAndDeletesNothing() {
        repository.create(ID, document(1));

        DeleteResult result = repository.delete(ID, Optional.of(document(9).revision()));

        assertThat(result).isEqualTo(new DeleteResult.Stale(document(1).revision()));
        assertThat(repository.find(ID)).map(StoredActivation::document).contains(document(1));
    }

    @Test
    void deleteWithoutARevisionDeletes() {
        repository.create(ID, document(1));

        assertThat(repository.delete(ID, Optional.empty())).isEqualTo(new DeleteResult.Deleted());
        assertThat(repository.find(ID)).isEmpty();
    }

    @Test
    void aDeletedIdCanBeCreatedAgain() {
        repository.create(ID, document(1));
        repository.delete(ID, Optional.empty());

        assertThat(repository.create(ID, document(2))).contains(StoredActivation.of(ID, document(2)));
    }

    @Test
    void twoIdsAreIndependent() {
        repository.create(ID, document(1));
        repository.create(OTHER, document(2));
        repository.replace(ID, Optional.empty(), document(3));
        repository.delete(OTHER, Optional.empty());

        assertThat(repository.find(ID)).map(StoredActivation::document).contains(document(3));
        assertThat(repository.find(OTHER)).isEmpty();
        assertThat(repository.create(OTHER, document(4))).isPresent();
        assertThat(repository.find(ID)).map(StoredActivation::document).contains(document(3));
    }

    /**
     * NFR-004: of 8 writers holding one {@code If-Match}, exactly one wins and 7 are answered {@code Stale}, and the
     * winner's document is the one stored, in every one of 100 runs.
     */
    @Test
    void ofEightWritersWithOneRevisionExactlyOneWinsInEveryRun() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(WRITERS);
        try {
            for (int run = 0; run < RUNS; run++) {
                oneRace(pool, new ActivationId("race-" + run));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void oneRace(ExecutorService pool, ActivationId id) throws Exception {
        Revision read = repository.create(id, document(0)).orElseThrow().revision();
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<CasResult>> writers = new ArrayList<>();
        for (int w = 1; w <= WRITERS; w++) {
            ActivationDocument mine = document(w);
            writers.add(pool.submit(() -> {
                gate.await();
                return repository.replace(id, Optional.of(read), mine);
            }));
        }
        gate.countDown();
        List<CasResult> results = new ArrayList<>();
        for (Future<CasResult> writer : writers) {
            results.add(writer.get(30, TimeUnit.SECONDS));
        }
        List<CasResult.Written> winners = results.stream()
                .filter(CasResult.Written.class::isInstance)
                .map(CasResult.Written.class::cast)
                .toList();
        assertThat(winners).as("winners of %s", id).hasSize(1);
        Revision won = winners.getFirst().stored().revision();
        assertThat(results).filteredOn(CasResult.Stale.class::isInstance).hasSize(WRITERS - 1);
        assertThat(repository.find(id)).map(StoredActivation::revision).contains(won);
    }
}
