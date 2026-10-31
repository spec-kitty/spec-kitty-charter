package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A step of a {@link MissionType}: an entity of its type, identified by its {@code id} within the type, with no file of
 * its own (ADR-0044 decision 2). It names the role that acts (ADR-0032), never an agent profile. Its expected inputs
 * and outputs close it as precondition, step, postcondition (ruling Q2).
 *
 * @param id the step's id, unique within its type
 * @param title the display label
 * @param description the step's human-facing summary, when it has one: at most {@value #DESCRIPTION_MAX_LENGTH}
 *     characters; a longer text is the prompt's {@linkplain StepDocument#description() detailed description}
 * @param role the role that acts; empty when any role may act
 * @param prompt the prompt the step's agent is given, when it has one
 * @param guidelines the step's working notes, when it has them; never with a detailed description
 * @param expectedInputArtifacts the step's preconditions, in authored order
 * @param expectedOutputArtifacts the step's postconditions, in authored order
 * @param references the step's action-scoped guidance, in authored order
 */
public record MissionStep(
        String id,
        String title,
        Optional<String> description,
        Optional<String> role,
        Optional<StepDocument> prompt,
        Optional<StepDocument> guidelines,
        List<ExpectedInput> expectedInputArtifacts,
        List<ExpectedArtifact> expectedOutputArtifacts,
        List<MissionReference> references) {

    /**
     * The longest step description, in characters (Unicode code points): a one-line summary for an overview. The
     * longest pinned one has 119 (ADR-0044, ruling Q1).
     */
    public static final int DESCRIPTION_MAX_LENGTH = 160;

    private static final Pattern ROLE = Pattern.compile("[a-z][a-z0-9-]*");

    private static final ModularRuleset<String> ID_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A mission step id is a letter-first kebab slug.",
                    id -> MissionTypeIdentifier.ID.matcher(id).matches()));
    private static final ModularRuleset<String> TEXT_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A mission step's title and description are not blank.", text -> !text.isBlank()));
    private static final ModularRuleset<String> DESCRIPTION_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A mission step's description is at most " + DESCRIPTION_MAX_LENGTH + " characters.",
                    text -> text.codePointCount(0, text.length()) <= DESCRIPTION_MAX_LENGTH));
    private static final ModularRuleset<String> ROLE_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A mission step's role is a lower-case role token.",
                    role -> ROLE.matcher(role).matches()));
    private static final ModularRuleset<StepDocument> GUIDELINES_RULES = ModularRuleset.<StepDocument>emptyRules()
            .must(Constraints.of(
                    "A mission step's guidelines carry no detailed description; only its prompt does.",
                    guidelines -> guidelines.description().isEmpty()));
    private static final ModularRuleset<List<String>> KEY_RULES = ModularRuleset.<List<String>>emptyRules()
            .must(Constraints.of("An artifact key stands once in a list of expected artifacts.", MissionStep::unique));

    /**
     * Checks every component and copies the lists.
     *
     * @param id the id
     * @param title the title
     * @param description the description, possibly empty
     * @param role the role, possibly empty
     * @param prompt the prompt, possibly empty
     * @param guidelines the guidelines, possibly empty
     * @param expectedInputArtifacts the preconditions
     * @param expectedOutputArtifacts the postconditions
     * @param references the references
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the id, a text or the role breaks
     *     its grammar, the description is longer than {@value #DESCRIPTION_MAX_LENGTH} characters, the guidelines carry
     *     a detailed description, or an artifact key stands twice in one list
     */
    public MissionStep {
        ID_RULES.guard(Objects.requireNonNull(id, "id"));
        TEXT_RULES.guard(Objects.requireNonNull(title, "title"));
        Objects.requireNonNull(description, "description").ifPresent(text -> {
            TEXT_RULES.guard(text);
            DESCRIPTION_RULES.guard(text);
        });
        Objects.requireNonNull(role, "role").ifPresent(ROLE_RULES::guard);
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(guidelines, "guidelines").ifPresent(GUIDELINES_RULES::guard);
        expectedInputArtifacts = List.copyOf(expectedInputArtifacts);
        expectedOutputArtifacts = List.copyOf(expectedOutputArtifacts);
        references = List.copyOf(references);
        KEY_RULES.guard(
                expectedInputArtifacts.stream().map(ExpectedInput::artifactKey).toList());
        KEY_RULES.guard(expectedOutputArtifacts.stream()
                .map(ExpectedArtifact::artifactKey)
                .toList());
    }

    /**
     * Whether no key stands twice in a list.
     *
     * @param keys the keys
     * @return {@code true} when every key is distinct
     */
    static boolean unique(List<String> keys) {
        Set<String> seen = new HashSet<>();
        return keys.stream().allMatch(seen::add);
    }
}
