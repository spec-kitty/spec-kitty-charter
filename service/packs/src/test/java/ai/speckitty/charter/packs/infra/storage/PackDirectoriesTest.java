package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.infra.storage.PackDirectories.Resolution;
import ai.speckitty.charter.packs.infra.storage.PackDirectories.State;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How a lock-free reader resolves a pack entry (#490): the live directory, or while a replace is between its renames the
 * one aside copy; nothing for zero or several copies, for a create's empty reservation, or for a copy beside a pack the
 * service may not write. And the identity-checked re-read around a read.
 */
class PackDirectoriesTest {

    @TempDir
    Path host;

    private Path packs;

    /** Locations that let the service write everything, as a fixed set does. */
    private final Locations writable = List::of;

    @BeforeEach
    void aPackDomain() throws IOException {
        packs = Files.createDirectories(host.resolve("config/packs"));
    }

    private static Path pack(Path directory) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(PackFileSet.PACK_YAML), "pack_id: x\n");
        return directory;
    }

    @Test
    void aLiveDirectoryIsReadInPlace() throws IOException {
        Path live = pack(packs.resolve("qa"));

        Resolution resolution = PackDirectories.resolve(live, writable);

        assertThat(resolution.state()).isEqualTo(State.LIVE);
        assertThat(resolution.directory()).isEqualTo(live);
        assertThat(resolution.identity()).isNotNull();
        assertThat(resolution.sameAs(PackDirectories.resolve(live, writable))).isTrue();
    }

    @Test
    void aMissingLiveDirectoryWithOneAsideCopyReadsTheCopy() throws IOException {
        Path aside = pack(packs.resolve("qa.pack-old-1"));

        Resolution resolution = PackDirectories.resolve(packs.resolve("qa"), writable);

        assertThat(resolution.state()).isEqualTo(State.ASIDE);
        assertThat(resolution.directory()).isEqualTo(aside.toRealPath());
    }

    @Test
    void twoAsideCopiesAreNotGuessedBetween() throws IOException {
        pack(packs.resolve("qa.pack-old-1"));
        pack(packs.resolve("qa.pack-old-2"));

        assertThat(PackDirectories.resolve(packs.resolve("qa"), writable).state())
                .isEqualTo(State.MISSING);
    }

    @Test
    void noLiveDirectoryAndNoAsideCopyIsMissing() {
        Resolution resolution = PackDirectories.resolve(packs.resolve("qa"), writable);

        assertThat(resolution.state()).isEqualTo(State.MISSING);
        assertThat(resolution.present()).isFalse();
        assertThat(resolution.directory()).isEqualTo(packs.resolve("qa"));
    }

    @Test
    void anEmptyDirectoryIsACreatesReservationAndReadsAsNoPack() throws IOException {
        Path reserved = Files.createDirectories(packs.resolve("qa"));

        assertThat(PackDirectories.resolve(reserved, writable).state()).isEqualTo(State.RESERVED);
        assertThatThrownBy(() -> PackDirectories.read(reserved, "qa", writable, directory -> "read"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    @Test
    void aDanglingLinkReadsTheAsideCopyBesideItsTarget() throws IOException {
        Path vendor = Files.createDirectories(packs.resolve(".vendor"));
        Path target = pack(vendor.resolve("real-qa")).toRealPath();
        Path link = SymbolicLinks.create(packs.resolve("qa"), target);
        Path aside = Files.move(target, vendor.resolve("real-qa.pack-old-1"));

        Resolution resolution = PackDirectories.resolve(link, writable);

        assertThat(resolution.state()).isEqualTo(State.ASIDE);
        assertThat(resolution.directory()).isEqualTo(aside.toRealPath());
    }

    @Test
    void anAsideCopyBesideAReadOnlyLinkedTargetIsNeverRead() throws IOException {
        Path checkout = Files.createDirectories(host.resolve("checkout"));
        Path target = pack(checkout.resolve("real-qa")).toRealPath();
        Path link = SymbolicLinks.create(packs.resolve("qa"), target);
        Files.move(target, checkout.resolve("real-qa.pack-old-1"));
        Path outside = checkout.toRealPath();
        Locations readOnly = new Locations() {
            @Override
            public List<PackLocation> all() {
                return List.of();
            }

            @Override
            public Optional<String> whyReadOnly(Path realDirectory) {
                return realDirectory.startsWith(outside) ? Optional.of("is read-only") : Optional.empty();
            }
        };

        assertThat(PackDirectories.resolve(link, readOnly).state()).isEqualTo(State.MISSING);
    }

    @Test
    void aFailureOverADirectoryThatHeldStillIsThrownAsItIsAndNotRetried() throws IOException {
        Path live = pack(packs.resolve("qa"));
        AtomicInteger runs = new AtomicInteger();
        CharterRefusal unreadable = new CharterRefusal(RefusalCode.PACK_UNREADABLE, "broken");

        assertThatThrownBy(() -> PackDirectories.read(live, "qa", writable, directory -> {
                    runs.incrementAndGet();
                    throw unreadable;
                }))
                .isSameAs(unreadable);
        assertThat(runs).as("an unchanged identity is never retried").hasValue(1);
    }

    @Test
    void aReadOverADirectoryAReplaceMovedRunsAgainOverTheNewOne() throws IOException {
        Path live = pack(packs.resolve("qa"));
        AtomicInteger runs = new AtomicInteger();

        String answer = PackDirectories.read(live, "qa", writable, directory -> {
            if (runs.incrementAndGet() == 1) {
                replace(live); // a whole swap under the first read
                return "torn";
            }
            return "whole";
        });

        assertThat(answer).isEqualTo("whole");
        assertThat(runs).hasValue(2);
    }

    @Test
    void aReadAReplaceMovesEveryTimeRefusesStaleInputAtTheCapNeverPackUnreadable() throws IOException {
        Path live = pack(packs.resolve("qa"));
        AtomicInteger runs = new AtomicInteger();

        assertThatThrownBy(() -> PackDirectories.read(live, "qa", writable, directory -> {
                    runs.incrementAndGet();
                    replace(live);
                    throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "a file vanished");
                }))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT);
                    assertThat(refusal.getCause()).isInstanceOf(CharterRefusal.class);
                });
        assertThat(runs).hasValue(PackDirectories.MAX_PROGRESS);
    }

    /** A whole swap of {@code live}: a new directory takes its place, the old one is gone. */
    private static void replace(Path live) {
        try {
            Path staged = pack(live.resolveSibling(".pack-commit-" + System.nanoTime()));
            Path aside = live.resolveSibling(live.getFileName() + ".pack-old-x");
            Files.move(live, aside);
            Files.move(staged, live);
            PackCommitStore.deleteTree(aside);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
