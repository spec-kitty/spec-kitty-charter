package ai.speckitty.charter.packs.domain;

import java.util.List;
import java.util.Objects;

/**
 * A file of a pack that is not pack content, so not held (#554, operator ruling Q1, open case 6): an {@code exclusions}
 * rule names it, or it is Markdown only the {@code ignored} fallback takes (#451), or no pack file kind models it, or a
 * rule maps it but only as the file the descriptor names, and the descriptor names none (open case 9). Both
 * validate mode and a registration report it as one {@code file_not_held} warning and judge nothing in it. A retired
 * file is never one: it is refused by name.
 *
 * @param path the pack-relative path, {@code /}-separated
 * @param kind why the file is not held, decided where the file is classified
 * @param reason the reviewable reason of the rule that excludes it, {@value #UNMODELLED_REASON}, or
 *     {@value #UNNAMED_POLICY_REASON}
 */
public record NotHeldFile(String path, Kind kind, String reason) {

    /** The reason of a file no pack file kind models (operator ruling Q1, #554). */
    public static final String UNMODELLED_REASON = "no pack file kind models this file";

    /**
     * The reason of a {@code policy.yaml} the descriptor's {@code mutability_policy} does not name (#554, open case 9):
     * the load reads the policy file only as the one the descriptor names (ADR-0063), so an unnamed one affects nothing.
     */
    public static final String UNNAMED_POLICY_REASON = "the descriptor's mutability_policy does not name it";

    /** Why a file is not held. */
    public enum Kind {
        /** An {@code exclusions} rule or the {@code ignored} fallback takes the file, with the rule's reason. */
        EXCLUDED,
        /** No pack file kind models the file. */
        UNMODELLED,
        /**
         * A rule maps the file, but the load reads it only when the descriptor names it, and the descriptor does not: a
         * {@code policy.yaml} no {@code mutability_policy} names (#554, open case 9).
         */
        UNNAMED
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
     * A {@code policy.yaml} the descriptor's {@code mutability_policy} does not name (#554, open case 9).
     *
     * @param path the pack-relative path
     * @return the file, with {@value #UNNAMED_POLICY_REASON} as its reason
     */
    public static NotHeldFile unnamedPolicy(String path) {
        return new NotHeldFile(path, Kind.UNNAMED, UNNAMED_POLICY_REASON);
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
