package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackWriteGeneration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The directory a lock-free reader reads for a pack entry, through the window of a replace (#490, ADR-0042 decision 8).
 *
 * <p>A replace ({@link PackCommitStore}) is two renames: the live directory to {@code <name>.pack-old-<unique>}, then
 * the staged copy into its place; the aside copy is then hidden and deleted. Between the renames there is no live
 * directory, only the aside copy, which holds the last committed state. A reader takes no lock (NFR-001), so it reads
 * that aside copy. {@link #resolve} answers the entry's own directory when it exists; when it is missing, the missing
 * live directory ({@link PackCommitStore#missingLiveDirectory}, which follows a dangling link to its target) and its
 * aside copies: exactly one is read, zero or several are absent, and the caller refuses as before. An aside copy beside
 * a directory the service may not write (a pack linked from outside the configuration root) was not left by the
 * service and is never read. An entry that is itself an empty directory (not a link) is the reservation of a create in
 * flight, or the residue of one a crash interrupted, and reads as no pack: a create reserves its entry with one
 * {@code mkdir} in the pack domain, never through a link.
 *
 * <p>Because an aside copy is hidden right after the second rename, and a whole read can span both renames, a read
 * runs inside {@link #read}: the pack's count of moves is taken ({@link PackWriteGeneration#movesOf}), the directory is
 * resolved, its identity taken (its path, its {@code fileKey} and creation time), the read run, the directory resolved
 * again and the count taken again. The read is accepted only when both resolutions name the same directory with the
 * same identity and the count did not move; otherwise a swap moved under it, and the read runs again. The count is what
 * catches a swap whose second rename failed: it moves the aside copy back, the same directory with the same identity,
 * and a read that ran between the renames found no pack (squad D-1). Only an attempt whose identity or count changed
 * counts, so a pack that is unreadable for any other reason refuses as before and is never retried; after
 * {@link #MAX_PROGRESS} such attempts the read refuses {@code STALE_INPUT}, which the caller may retry, never
 * {@code PACK_UNREADABLE}.
 */
final class PackDirectories {

    /** The most attempts whose directory changed under them before a read gives up with {@code STALE_INPUT}. */
    static final int MAX_PROGRESS = 8;

    private PackDirectories() {}

    /** What a pack entry resolves to for a reader. */
    enum State {
        /** The entry's own directory exists and holds a pack. */
        LIVE,
        /** The live directory is missing and one aside copy sits beside it: the last committed state. */
        ASIDE,
        /** The entry is itself an empty directory, not a link: a create's reservation; no pack. */
        RESERVED,
        /** Neither: no live directory and zero or several aside copies, or no directory at all. */
        MISSING
    }

    /**
     * A resolution of a pack entry.
     *
     * @param state what the entry resolved to
     * @param directory the directory to read: the entry for {@link State#LIVE}, {@link State#RESERVED} and
     *     {@link State#MISSING}, the aside copy for {@link State#ASIDE}
     * @param identity the directory's identity, or null when nothing is there to read
     */
    record Resolution(State state, Path directory, @Nullable Identity identity) {

        /**
         * Whether the resolution names a directory a reader reads.
         *
         * @return true for a live directory or an aside copy
         */
        boolean present() {
            return state == State.LIVE || state == State.ASIDE;
        }

        /** Whether {@code other} names the same directory with the same identity: no swap ran between the two. */
        boolean sameAs(Resolution other) {
            return state == other.state
                    && directory.equals(other.directory)
                    && Objects.equals(identity, other.identity);
        }
    }

    /**
     * The identity of a directory: its {@code fileKey} (device and inode where the platform has one) and its creation
     * time, so an inode a successful later swap reuses is still another directory. Not its modification time: a file
     * saved in place inside the pack (an operator's edit) changes that, and is no swap. Where the platform keeps no birth
     * time (tmpfs, some overlayfs), {@code creationTime()} answers the modification time instead, so there a file created
     * in the pack counts as progress and the read runs again; it is never accepted wrongly. The identity does not catch
     * a swap rolled back: its second rename failed and the aside copy, the same directory, was moved back. The pack's
     * count of moves catches that ({@link #read}); correctness rests on the {@code fileKey} and that count.
     *
     * @param fileKey the file key, or null where the platform has none
     * @param created the creation time (the birth time where the platform keeps one)
     */
    record Identity(@Nullable Object fileKey, FileTime created) {

        static Identity of(BasicFileAttributes attributes) {
            return new Identity(attributes.fileKey(), attributes.creationTime());
        }
    }

    /**
     * Resolves a pack entry for a lock-free read.
     *
     * @param entry the pack's entry, as it is configured (a link's own path, not its target's)
     * @param locations the configured locations, which say whether the service may write a directory: an aside copy
     *     beside one it may not was not left by the service
     * @return the resolution
     */
    static Resolution resolve(Path entry, Locations locations) {
        Path at = entry.toAbsolutePath().normalize();
        Optional<BasicFileAttributes> live = attributes(at, true);
        if (live.isPresent()) {
            if (!live.get().isDirectory()) {
                return new Resolution(State.MISSING, at, null);
            }
            if (!Files.isSymbolicLink(at) && isReservation(at)) {
                return new Resolution(State.RESERVED, at, null);
            }
            return new Resolution(State.LIVE, at, Identity.of(live.get()));
        }
        return aside(at, locations).orElseGet(() -> new Resolution(State.MISSING, at, null));
    }

    /** The one aside copy beside the missing live directory of {@code entry}, when the service left it. */
    private static Optional<Resolution> aside(Path entry, Locations locations) {
        Optional<Path> missing = PackCommitStore.missingLiveDirectory(entry);
        if (missing.isEmpty() || locations.whyReadOnly(missing.get()).isPresent()) {
            return Optional.empty();
        }
        List<Path> asides;
        try {
            asides = PackCommitStore.asidesOf(missing.get());
        } catch (CharterRefusal unlistable) {
            return Optional.empty();
        }
        if (asides.size() != 1) {
            return Optional.empty();
        }
        Path aside = asides.getFirst();
        return attributes(aside, false)
                .filter(BasicFileAttributes::isDirectory)
                .map(attributes -> new Resolution(State.ASIDE, aside, Identity.of(attributes)));
    }

    /**
     * Runs a lock-free read of a pack inside an identity-checked re-read: {@code body} is given the directory
     * {@link #resolve} answers (the entry itself when nothing is there, so the read refuses as before), and its answer
     * is kept only when the directory resolves to the same one, with the same identity, after it, and no swap of the
     * pack moved its directory in between ({@link PackWriteGeneration#movesOf}). Otherwise it runs again; a failure of {@code body} is thrown only when the directory did not change under it.
     *
     * @param entry the pack's entry, as it is configured
     * @param liveName the name the pack is configured under, which a refusal names; never the aside copy's
     * @param locations the configured locations
     * @param body the read; it must not write
     * @param <T> what the read answers
     * @return what {@code body} answered over a directory no swap moved under it
     * @throws CharterRefusal {@code PACK_NOT_FOUND} when the entry is a create's empty reservation;
     *     {@code STALE_INPUT} when {@value #MAX_PROGRESS} attempts in a row each met a changed directory; what
     *     {@code body} threw when the directory did not change under it
     */
    static <T> T read(Path entry, String liveName, Locations locations, Function<Path, T> body) {
        return read(entry, liveName, locations, PackWriteGeneration.shared(), body);
    }

    /**
     * As {@link #read(Path, String, Locations, Function)}, over a write generation of the caller's own; a test passes the
     * one its commit store records moves in.
     *
     * @param entry the pack's entry, as it is configured
     * @param liveName the name the pack is configured under: the name every swap of it records its moves under
     * @param locations the configured locations
     * @param generation the write generation the pack's swaps record their moves in
     * @param body the read; it must not write
     * @param <T> what the read answers
     * @return what {@code body} answered over a directory no swap moved under it
     */
    static <T> T read(
            Path entry, String liveName, Locations locations, PackWriteGeneration generation, Function<Path, T> body) {
        @Nullable RuntimeException last = null;
        for (int progress = 0; progress < MAX_PROGRESS; progress++) {
            long moves = generation.movesOf(liveName);
            Resolution before = resolve(entry, locations);
            if (before.state() == State.RESERVED) {
                // A create in flight, not yet acknowledged: there is no pack of that name yet (squad D7).
                throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + liveName + " was not found.");
            }
            Attempt<T> attempt = Attempt.of(body, before.directory());
            // The count is taken after the second resolution: a swap rolled back between them moved it (squad D-1).
            if (before.sameAs(resolve(entry, locations)) && generation.movesOf(liveName) == moves) {
                return attempt.answerOrThrow(); // the directory held still: a failure is the pack's, not a swap's
            }
            last = attempt.failure(); // the directory moved under the read: that is progress, so read again
        }
        String why = "pack " + liveName + " was replaced " + MAX_PROGRESS + " times while it was read; read it again";
        throw last == null
                ? new CharterRefusal(RefusalCode.STALE_INPUT, why)
                : new CharterRefusal(RefusalCode.STALE_INPUT, why, last);
    }

    /** One run of a read: its answer, or what it threw. */
    private record Attempt<T>(@Nullable T answer, @Nullable RuntimeException failure) {

        static <T> Attempt<T> of(Function<Path, T> body, Path directory) {
            try {
                return new Attempt<>(body.apply(directory), null);
            } catch (RuntimeException thrown) {
                return new Attempt<>(null, thrown);
            }
        }

        T answerOrThrow() {
            if (failure != null) {
                throw failure;
            }
            return Objects.requireNonNull(answer, "a read answers a value");
        }
    }

    /** Whether a directory is a create's reservation: empty, so it has no {@code pack.yaml} either. */
    private static boolean isReservation(Path directory) {
        if (Files.exists(directory.resolve(PackFileSet.PACK_YAML), LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (Stream<Path> children = Files.list(directory)) {
            return children.findAny().isEmpty();
        } catch (IOException unlistable) {
            return false; // the read lists it and refuses as before
        }
    }

    private static Optional<BasicFileAttributes> attributes(Path path, boolean followLinks) {
        try {
            return Optional.of(
                    followLinks
                            ? Files.readAttributes(path, BasicFileAttributes.class)
                            : Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
        } catch (IOException gone) {
            return Optional.empty();
        }
    }
}
