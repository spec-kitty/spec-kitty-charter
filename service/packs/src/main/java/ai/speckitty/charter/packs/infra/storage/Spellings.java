package ai.speckitty.charter.packs.infra.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * How a pack-relative path is spelled on disk, read from the directory listings segment by segment and never by asking
 * the file system to open a name. A case-insensitive file system opens {@code A.md} for {@code a.md}; a case-sensitive
 * one does not. The names a listing holds are the same on both, so a verdict made from them is the same on both
 * (#252, #300). Case is folded as a claim folds it ({@link Companions.Inside#claim()}): {@link Locale#ROOT} lower
 * case. Names that differ in a way that fold leaves apart (Unicode normalization, say) are different names here.
 */
final class Spellings {

    private Spellings() {}

    /** How the pack spells a path. */
    sealed interface Spelling permits Exact, OtherCase, Absent {}

    /** Every segment is listed as written. */
    record Exact() implements Spelling {}

    /**
     * The path is listed only in another case, as a case-insensitive file system would open it.
     *
     * @param onDisk the pack-relative path as the listings spell it
     */
    record OtherCase(String onDisk) implements Spelling {}

    /** No entry stands for the path, in any case. */
    record Absent() implements Spelling {}

    /**
     * How the pack spells {@code relative}: each segment as written when its directory lists it so, otherwise the one
     * entry a case-insensitive file system would open for it (the first by name, if the pack holds several), as such a
     * file system resolves it. An exact entry wins, as it does there.
     *
     * @param directory the pack directory
     * @param relative the {@code /}-separated path under it, normalized
     * @return {@link Exact}, {@link OtherCase} with the on-disk spelling of the whole path, or {@link Absent} when a
     *     segment is listed in no case
     */
    static Spelling of(Path directory, String relative) {
        Path at = directory;
        List<String> onDisk = new ArrayList<>();
        boolean respelled = false;
        for (String segment : relative.split("/", -1)) {
            List<String> names = names(at);
            String entry;
            if (names.contains(segment)) {
                entry = segment;
            } else {
                Optional<String> other = otherCase(names, segment);
                if (other.isEmpty()) {
                    return new Absent();
                }
                entry = other.get();
                respelled = true;
            }
            onDisk.add(entry);
            at = at.resolve(entry);
        }
        return respelled ? new OtherCase(String.join("/", onDisk)) : new Exact();
    }

    /**
     * Whether writing {@code relative} would meet a file or directory of the pack in another case: the first segment
     * whose directory lists another entry that folds to it, as the pack-relative path of that entry, whether or not
     * the segment is listed as written too. A case-insensitive file system holds one entry for the two names, so the
     * write would land on it, or in it.
     *
     * @param directory the pack directory
     * @param relative the {@code /}-separated path under it, normalized
     * @return the on-disk path, up to and including the entry in another case; empty when no listed entry folds to a
     *     segment it is not
     */
    static Optional<String> collision(Path directory, String relative) {
        Path at = directory;
        List<String> onDisk = new ArrayList<>();
        for (String segment : relative.split("/", -1)) {
            List<String> names = names(at);
            Optional<String> other = otherCase(names, segment);
            if (other.isPresent()) {
                onDisk.add(other.get());
                return Optional.of(String.join("/", onDisk));
            }
            if (!names.contains(segment)) {
                return Optional.empty();
            }
            onDisk.add(segment);
            at = at.resolve(segment);
        }
        return Optional.empty();
    }

    private static Optional<String> otherCase(List<String> names, String segment) {
        String folded = segment.toLowerCase(Locale.ROOT);
        return names.stream()
                .filter(name ->
                        !name.equals(segment) && name.toLowerCase(Locale.ROOT).equals(folded))
                .sorted()
                .findFirst();
    }

    /**
     * The names a directory lists; none when it is not a directory or cannot be listed. Then nothing is spelled there,
     * and the caller's own file access meets the same failure: a write cannot land, and a named file reads as missing.
     */
    private static List<String> names(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> String.valueOf(entry.getFileName())).toList();
        } catch (IOException | UncheckedIOException e) {
            return List.of();
        }
    }
}
