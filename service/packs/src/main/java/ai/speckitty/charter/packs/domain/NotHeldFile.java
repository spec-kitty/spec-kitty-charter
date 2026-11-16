package ai.speckitty.charter.packs.domain;

import java.util.List;
import java.util.Objects;

/**
 * A file of a pack that is not pack content, so not held (#554, operator ruling Q1, open case 6): an {@code exclusions}
 * rule names it, or it is Markdown only the {@code ignored} fallback takes (#451), or no pack file kind models it. Both
 * validate mode and a registration report it as one {@code file_not_held} warning and judge nothing in it. A retired
 * file is never one: it is refused by name.
 *
 * @param path the pack-relative path, {@code /}-separated
 * @param kind why the file is not held, decided where the file is classified
 * @param reason the reviewable reason of the rule that excludes it, or {@value #UNMODELLED_REASON}
 */
public record NotHeldFile(String path, Kind kind, String reason) {

    /** The reason of a file no pack file kind models (operator ruling Q1, #554). */
    public static final String UNMODELLED_REASON = "no pack file kind models this file";

    /** Why a file is not held. */
    public enum Kind {
        /** An {@code exclusions} rule or the {@code ignored} fallback takes the file, with the rule's reason. */
        EXCLUDED,
        /** No pack file kind models the file. */
        UNMODELLED
    }

    /**
     * Checks the fields: an unmodelled file carries {@value #UNMODELLED_REASON}.
     *
     * @param path the pack-relative path
     * @param kind why the file is not held
     * @param reason the rule's reason
     */
    public NotHeldFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reason, "reason");
        if (kind == Kind.UNMODELLED && !reason.equals(UNMODELLED_REASON)) {
            throw new IllegalArgumentException("an unmodelled file's reason is '" + UNMODELLED_REASON + "': " + path);
        }
    }

    /**
     * A file an {@code exclusions} rule or the {@code ignored} fallback takes.
     *
     * @param path the pack-relative path
     * @param reason the rule's reason
     * @return the file
     */
    public static NotHeldFile excluded(String path, String reason) {
        return new NotHeldFile(path, Kind.EXCLUDED, reason);
    }

    /**
     * A file no pack file kind models: warned about, then ignored (operator ruling Q1, #554).
     *
     * @param path the pack-relative path
     * @return the file, with {@value #UNMODELLED_REASON} as its reason
     */
    public static NotHeldFile unmodelled(String path) {
        return new NotHeldFile(path, Kind.UNMODELLED, UNMODELLED_REASON);
    }

    /**
     * The {@code file_not_held} warning for this file (FR-021): the file named and the reason given.
     *
     * @return the warning
     */
    public PackFinding finding() {
        return new PackFinding(PackFindingCode.FILE_NOT_HELD, path, List.of(), path + ": not held (" + reason + ")");
    }
}
