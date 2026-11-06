package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * NFR-005 and SC-003: a failure injected between the temp write and the move leaves the old document byte-identical
 * and no partial file, in 100 of 100 runs.
 */
class FileActivationRepositoryFailureTest {

    private static final int RUNS = 100;
    private static final long SEED = 0x5EED_0007L;

    @TempDir
    Path root;

    /** A random document of the seeded generator. */
    private static ActivationDocument randomDocument(Random random) {
        List<PackRef> packs = new ArrayList<>();
        int count = 1 + random.nextInt(4);
        for (int i = 0; i < count; i++) {
            packs.add(PackRef.pinned("pack-" + i + "-" + random.nextInt(1000), "1." + random.nextInt(50)));
        }
        List<ArtifactKind> kinds = List.copyOf(ActivationDocument.kinds());
        return ActivationDocument.builder()
                .packs(packs)
                .activatedKinds(List.of(kinds.get(random.nextInt(kinds.size()))))
                .languages(List.of("lang-" + random.nextInt(100), "é-" + random.nextInt(100)))
                .deactivated(ArtifactKind.TACTIC, List.of("tactic-" + random.nextInt(10_000)))
                .build()
                .document();
    }

    private Path file(ActivationId id) {
        return root.resolve(ActivationDirectoryName.encode(id)).resolve("activation.json");
    }

    private List<Path> filesOf(ActivationId id) throws IOException {
        try (Stream<Path> listed = Files.list(file(id).getParent())) {
            return listed.toList();
        }
    }

    @Test
    void aMoveThatThrowsAnIoExceptionLeavesTheOldDocumentAndNoTempFile() throws IOException {
        FileActivationRepository.Durability failing = (temp, target) -> {
            throw new IOException("injected: the disk is gone");
        };
        everyRunKeepsTheOldDocument(failing);
    }

    @Test
    void aMoveThatThrowsARuntimeExceptionLeavesTheOldDocumentAndNoTempFile() throws IOException {
        FileActivationRepository.Durability failing = (temp, target) -> {
            throw new IllegalStateException("injected: the move blew up");
        };
        everyRunKeepsTheOldDocument(failing);
    }

    private void everyRunKeepsTheOldDocument(FileActivationRepository.Durability failing) throws IOException {
        Random random = new Random(SEED);
        for (int run = 0; run < RUNS; run++) {
            ActivationId id = new ActivationId("run-" + run);
            ActivationDocument old = randomDocument(random);
            new FileActivationRepository(root).create(id, old);
            byte[] before = Files.readAllBytes(file(id));
            FileActivationRepository broken = new FileActivationRepository(root, failing);

            assertThatThrownBy(() -> broken.replace(id, Optional.empty(), randomDocument(random)))
                    .isInstanceOf(ActivationStorageException.class)
                    .hasMessageContaining(id.value());

            assertThat(Files.readAllBytes(file(id))).isEqualTo(before);
            assertThat(broken.find(id)).contains(StoredActivation.of(id, old));
            assertThat(filesOf(id)).containsExactly(file(id));
        }
    }

    @Test
    void aFailedFirstWriteLeavesNoDocumentAndNoTempFile() throws IOException {
        ActivationId id = new ActivationId("never-written");
        FileActivationRepository broken = new FileActivationRepository(root, (temp, target) -> {
            throw new IOException("injected");
        });

        assertThatThrownBy(() -> broken.create(id, randomDocument(new Random(SEED))))
                .isInstanceOf(ActivationStorageException.class);

        assertThat(broken.find(id)).isEmpty();
        assertThat(filesOf(id)).isEmpty();
    }

    @Test
    void aMoveDeniedOnceIsRetriedAndSucceeds() {
        AtomicInteger attempts = new AtomicInteger();
        FileActivationRepository flaky = new FileActivationRepository(root, (temp, target) -> {
            if (attempts.incrementAndGet() == 1) {
                throw new AccessDeniedException(target.toString(), null, "injected: a reader holds it open");
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        });
        ActivationId id = new ActivationId("denied-once");
        ActivationDocument document = randomDocument(new Random(SEED));

        assertThat(flaky.create(id, document)).isPresent();

        assertThat(attempts).hasValue(2);
        assertThat(flaky.find(id)).map(StoredActivation::document).contains(document);
    }

    @Test
    void aMoveDeniedEveryTimeGivesUpAfterThreeAttempts() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        FileActivationRepository denied = new FileActivationRepository(root, (temp, target) -> {
            attempts.incrementAndGet();
            throw new AccessDeniedException(target.toString());
        });
        ActivationId id = new ActivationId("always-denied");

        assertThatThrownBy(() -> denied.create(id, randomDocument(new Random(SEED))))
                .isInstanceOf(ActivationStorageException.class);

        assertThat(attempts).hasValue(3);
        assertThat(filesOf(id)).isEmpty();
    }
}
