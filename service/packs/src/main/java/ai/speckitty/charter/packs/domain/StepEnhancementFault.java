package ai.speckitty.charter.packs.domain;

import java.util.Objects;

/**
 * The rules {@link StepEnhancementRules} judges, one member each. The declaration order is the tie-break order of the
 * answer: faults at one file come out in this order (research P9).
 *
 * <p>There is no member for an id that differs from its file stem, nor for a slot id that breaks
 * {@code <namespace>:<name>}, nor for {@code prompt_fragments: []}: each is a schema violation the store refuses first
 * (operator ruling A1; FR-005), so the file never reaches these rules. A caller maps every member onto one wire
 * code; this type names none.
 */
public enum StepEnhancementFault {
    /** A fragment's body is blank (FR-004). */
    FRAGMENT_BLANK,
    /** A fragment carries frontmatter (FR-004). */
    FRAGMENT_FRONTMATTER,
    /** A fragment holds a slot marker candidate of any id, anywhere, fences included (FR-004, d7). */
    FRAGMENT_HAS_MARKER,
    /** A slot id occurs twice in one enhancement (FR-005). */
    SLOT_REPEATED,
    /** An entry's companion does not exist beside the enhancement (FR-005). */
    COMPANION_MISSING,
    /** A companion is named by a second entry, of the same enhancement or another (FR-005, D11). */
    COMPANION_SHARED,
    /** A fragment under an {@code enhancements/} directory is named by no entry (D11). */
    COMPANION_ORPHAN,
    /** The enhancement has not exactly one {@code enhances} edge (D9, FR-006). */
    EDGE_COUNT,
    /** The {@code enhances} edge's target is not an {@code action:<type>/<step>} (D9, FR-006). */
    EDGE_TARGET_NOT_STEP,
    /** The enhancement is the source of an {@code overrides} edge, whatever its target (R-OVR, FR-006). */
    OVERRIDES_EDGE,
    /** The file's {@code <type>} directory differs from the edge's {@code <type>} (C-003, FR-006). */
    DIRECTORY_MISMATCH,
    /** The id repeats across type directories of the pack; judged at the second file in path order (D8, FR-006). */
    STEM_REPEATED,
    /** The enhancement has no {@code prompt_fragments} entry (D12, FR-006). */
    NO_FRAGMENTS;

    /**
     * One fault found.
     *
     * @param rule the rule that found it
     * @param path the pack-relative path of the file it belongs to
     * @param reason a sentence for the reader; it names pack-relative paths only
     */
    public record Finding(StepEnhancementFault rule, String path, String reason) {
        /**
         * Checks the parts.
         *
         * @param rule the rule
         * @param path the file path
         * @param reason the sentence
         */
        public Finding {
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
