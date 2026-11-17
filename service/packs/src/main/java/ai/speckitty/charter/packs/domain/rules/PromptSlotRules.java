package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.markdown.PromptSlots;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The content rules of a mission type's step prompts (#554, FR-001, T037; FR-007 of ADR-0066): each slot marker is
 * well formed, and a slot holds no text. Each is a kernel rule over the slot parse the one read gives of each step
 * prompt ({@link PackContents#promptSlots()}); it reads no file and knows no surface. A prompt the codec refused is a
 * read fault and has no parse, so it is judged by no slot rule.
 *
 * <p>A slot that holds text is the one condition with two severities (ADR-0066 version gate): a
 * {@link PackCodes#PROMPT_SLOT_NOT_EMPTY} violation when the descriptor declares {@code schema_version} {@value
 * #VERSIONED_SCHEMA}, and the {@link PackCodes#PROMPT_SLOT_NOT_EMPTY_WARNING} warning when it declares none (or could not
 * be read). The warning has this one source: the reader states none (C-9).
 *
 * <p>The judgement of one prompt is a plain function ({@link #faults(PromptSlots.Parse, boolean)}): the rule states each
 * of its faults as a finding, and the store half, which the composing read answers from (S7), records the same faults
 * on the aggregate.
 */
public final class PromptSlotRules {

    /** The descriptor {@code schema_version} that makes a slot that holds text a refusal (ADR-0066 version gate). */
    static final String VERSIONED_SCHEMA = "1.0";

    /** What the warning of an unversioned descriptor adds to the reason. */
    static final String UNVERSIONED_WARNING = "; the descriptor declares no schema_version, so this is a warning";

    /**
     * Each fault of each step prompt's slots, in the read's order of the prompts and then the parse's: a marker that is
     * malformed, repeated, nested or unpaired is one {@link PackCodes#PROMPT_SLOT_MALFORMED} whatever the descriptor's
     * version; a slot that holds text is one {@link PackCodes#PROMPT_SLOT_NOT_EMPTY} under a versioned descriptor, else
     * one {@link PackCodes#PROMPT_SLOT_NOT_EMPTY_WARNING}. Each is at the prompt file, naming the line of the canonical
     * text (and the slot).
     */
    public static final Rule<PackContents> SLOTS = PromptSlotRules::faults;

    /** Every prompt slot rule, in this class's order. */
    public static final ModularRuleset<PackContents> RULES =
            ModularRuleset.<PackContents>emptyRules().must(SLOTS);

    private PromptSlotRules() {}

    static List<Reason> faults(PackContents contents) {
        boolean versioned = versioned(contents);
        List<Reason> findings = new ArrayList<>();
        for (PackContents.PromptSlotFacts prompt : contents.promptSlots()) {
            for (Fault fault : faults(prompt.parse(), versioned)) {
                findings.add(finding(prompt.file(), fault));
            }
        }
        return findings;
    }

    /** Whether the descriptor the read read declares the versioned schema. */
    static boolean versioned(PackContents contents) {
        return contents.descriptor() instanceof FileRead.Read<PackDescriptor> read
                && versioned(read.value().schemaVersion());
    }

    /**
     * Whether a descriptor's {@code schema_version} is the versioned one, {@value #VERSIONED_SCHEMA}.
     *
     * @param schemaVersion the descriptor's {@code schema_version}; empty for an unversioned descriptor
     * @return whether a slot that holds text is a violation
     */
    public static boolean versioned(Optional<String> schemaVersion) {
        return schemaVersion.filter(VERSIONED_SCHEMA::equals).isPresent();
    }

    /**
     * One fault of a step prompt's slots: the code the fault's kind and the descriptor's version pick, and what is wrong.
     *
     * @param code the code, which owns the severity
     * @param reason what is wrong, naming the line of the marker (and the slot); it does not name the file
     */
    public record Fault(ReasonCode code, String reason) {
        /**
         * Checks the fields.
         *
         * @param code the code
         * @param reason the reason
         */
        public Fault {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Judges the slots of one step prompt (FR-007): each fault of its parse, in the parse's order, as the code the
     * descriptor's version picks.
     *
     * @param parse the parse of the prompt's canonical text
     * @param versioned whether the descriptor declares the versioned schema ({@link #versioned(Optional)})
     * @return one fault per fault of the parse
     */
    public static List<Fault> faults(PromptSlots.Parse parse, boolean versioned) {
        List<Fault> faults = new ArrayList<>();
        for (PromptSlots.Fault fault : parse.faults()) {
            String where = "line " + fault.line() + " of the canonical text: ";
            faults.add(
                    switch (fault.kind()) {
                        case MALFORMED -> new Fault(PackCodes.PROMPT_SLOT_MALFORMED, where + fault.reason());
                        case NOT_EMPTY -> {
                            String slot = fault.slot()
                                    .map(id -> "slot " + id + " holds text")
                                    .orElse(fault.reason());
                            ReasonCode code = versioned
                                    ? PackCodes.PROMPT_SLOT_NOT_EMPTY
                                    : PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING;
                            yield new Fault(code, where + slot + (versioned ? "" : UNVERSIONED_WARNING));
                        }
                    });
        }
        return faults;
    }

    /**
     * One fault as a finding at the prompt file: {@code <file>: <reason>}.
     *
     * @param file the prompt's pack-relative path
     * @param fault the fault
     * @return the finding
     */
    public static Reason finding(String file, Fault fault) {
        return Findings.at(fault.code(), file, List.of(), file + ": " + fault.reason());
    }
}
