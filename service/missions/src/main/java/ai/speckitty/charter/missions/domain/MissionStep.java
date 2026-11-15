package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.domain.IdentifierGrammar;
import ai.speckitty.charter.kernel.domain.RoleBearer;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
 * @param substeps the step's substeps, in authored order: a flat list, one level deep, empty when none is authored;
 *     their ids are unique within the step (operator ruling DM-01M7B1YH1GF15RD0G4ZPSA8KW9, FR-004)
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
        List<Substep> substeps) {

    /**
     * The longest step description, in characters (Unicode code points): a one-line summary for an overview. The
     * longest pinned one has 119 (ADR-0044, ruling Q1).
     */
    public static final int DESCRIPTION_MAX_LENGTH = 160;

    private static final ModularRuleset<String> ID_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A mission step id is a letter-first kebab slug of at most 64 characters.",
                    IdentifierGrammar::isLetterFirstSlug));
    private static final ModularRuleset<String> TEXT_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.notBlank().described("A mission step's title and description are not blank."));
    private static final ModularRuleset<String> DESCRIPTION_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A mission step's description is at most " + DESCRIPTION_MAX_LENGTH + " characters.",
                    text -> text.codePointCount(0, text.length()) <= DESCRIPTION_MAX_LENGTH));
    /** The longest role token, as a profile's roles are capped (#446). */
    public static final int ROLE_MAX_LENGTH = RoleBearer.ROLE_MAX_LENGTH;

    /** The reserved role of a fallback profile, which no step asks for (ADR-0068 decision 4). */
    private static final String GENERIC_ROLE = "generic";

    private static final ModularRuleset<String> ROLE_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A mission step's role is a letter-first kebab slug of at most " + ROLE_MAX_LENGTH + " characters.",
                    RoleBearer::isRole))
            .must(Constraints.of(
                    "A mission step never asks for the reserved role " + GENERIC_ROLE
                            + ", which marks a fallback profile (ADR-0068 decision 4).",
                    role -> !GENERIC_ROLE.equals(role)));
    private static final ModularRuleset<StepDocument> GUIDELINES_RULES = ModularRuleset.<StepDocument>emptyRules()
            .must(Constraints.of(
                    "A mission step's guidelines carry no detailed description; only its prompt does.",
                    guidelines -> guidelines.description().isEmpty()));
    private static final ModularRuleset<List<String>> KEY_RULES = ModularRuleset.<List<String>>emptyRules()
            .must(Constraints.of("An artifact key stands once in a list of expected artifacts.", MissionStep::unique));
    private static final ModularRuleset<List<String>> SUBSTEP_RULES = ModularRuleset.<List<String>>emptyRules()
            .must(Constraints.of("A substep id stands once within its step.", MissionStep::unique));

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
     * @param substeps the substeps
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the id, a text or the role breaks
     *     its grammar, the description is longer than {@value #DESCRIPTION_MAX_LENGTH} characters, the guidelines carry
     *     a detailed description, an artifact key stands twice in one list, or a substep id stands twice
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
        KEY_RULES.guard(
                expectedInputArtifacts.stream().map(ExpectedInput::artifactKey).toList());
        KEY_RULES.guard(expectedOutputArtifacts.stream()
                .map(ExpectedArtifact::artifactKey)
                .toList());
        substeps = List.copyOf(substeps);
        SUBSTEP_RULES.guard(substeps.stream().map(Substep::id).toList());
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
