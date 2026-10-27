package ai.speckitty.charter.packs.infra.storage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of an agent profile: a flat mirror of the fields of a {@code *.agent.yaml}, not the domain
 * entity. Its record equality is the round-trip fidelity assertion; lists are order- and duplicate-sensitive.
 *
 * <p>A field with a schema default is optional: empty when the file does not declare it, so a write never invents the
 * default. A declared list, or a declared object made only of lists ({@code collaboration},
 * {@code specialization-context}, {@code self-review-protocol}), reads as empty when it is absent, null or empty, which
 * the schema makes the same (ADR-0036). Integers are held as {@code long}, so a large value is never truncated on
 * read; one beyond that range is held as {@link Long#MAX_VALUE}, which the binding refuses.
 *
 * @param profileId the {@code profile-id}
 * @param name the {@code name}
 * @param description the {@code description}, when declared
 * @param schemaVersion the {@code schema-version}, when declared
 * @param roles the {@code roles}, in authored order
 * @param avatarImage the {@code avatar-image}, when declared
 * @param capabilities the {@code capabilities}, in authored order
 * @param routingPriority the {@code routing-priority}, when declared
 * @param maxConcurrentTasks the {@code max-concurrent-tasks}, when declared
 * @param model the {@code model}, when declared
 * @param effort the {@code effort}, when declared
 * @param purpose the {@code purpose}
 * @param specialization the {@code specialization}
 * @param collaboration the {@code collaboration}
 * @param modeDefaults the {@code mode-defaults}, in authored order
 * @param initializationDeclaration the {@code initialization-declaration}, when declared
 * @param sentinel the {@code sentinel}, when declared
 * @param specializationContext the {@code specialization-context}
 * @param appliesToLanguages the {@code applies_to_languages}, in authored order
 * @param directiveReferences the {@code directive-references}, in authored order
 * @param tacticReferences the {@code tactic-references}, in authored order
 * @param toolguideReferences the {@code toolguide-references}, in authored order
 * @param styleguideReferences the {@code styleguide-references}, in authored order
 * @param selfReviewSteps the {@code steps} of the {@code self-review-protocol}, in authored order
 * @param excluding the {@code excluding}, when declared
 */
record AgentProfileDso(
        String profileId,
        String name,
        Optional<String> description,
        Optional<String> schemaVersion,
        List<String> roles,
        Optional<String> avatarImage,
        List<String> capabilities,
        Optional<Long> routingPriority,
        Optional<Long> maxConcurrentTasks,
        Optional<String> model,
        Optional<String> effort,
        String purpose,
        Specialization specialization,
        Collaboration collaboration,
        List<ModeDefault> modeDefaults,
        Optional<String> initializationDeclaration,
        Optional<Boolean> sentinel,
        SpecializationContext specializationContext,
        List<String> appliesToLanguages,
        List<DirectiveRef> directiveReferences,
        List<ArtifactRef> tacticReferences,
        List<ArtifactRef> toolguideReferences,
        List<ArtifactRef> styleguideReferences,
        List<SelfReviewStep> selfReviewSteps,
        Optional<Excluding> excluding) {

    /**
     * Checks that the required fields are present and copies the lists.
     *
     * @param profileId the profile id
     * @param name the name
     * @param description the description
     * @param schemaVersion the schema version
     * @param roles the roles
     * @param avatarImage the avatar image
     * @param capabilities the capabilities
     * @param routingPriority the routing priority
     * @param maxConcurrentTasks the maximum of concurrent tasks
     * @param model the model
     * @param effort the effort
     * @param purpose the purpose
     * @param specialization the specialization
     * @param collaboration the collaboration
     * @param modeDefaults the mode defaults
     * @param initializationDeclaration the initialization declaration
     * @param sentinel the sentinel flag
     * @param specializationContext the specialization context
     * @param appliesToLanguages the languages it applies to
     * @param directiveReferences the directive references
     * @param tacticReferences the tactic references
     * @param toolguideReferences the toolguide references
     * @param styleguideReferences the styleguide references
     * @param selfReviewSteps the self-review steps
     * @param excluding the exclusion
     */
    AgentProfileDso {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        roles = List.copyOf(roles);
        Objects.requireNonNull(avatarImage, "avatarImage");
        capabilities = List.copyOf(capabilities);
        Objects.requireNonNull(routingPriority, "routingPriority");
        Objects.requireNonNull(maxConcurrentTasks, "maxConcurrentTasks");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(effort, "effort");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(specialization, "specialization");
        Objects.requireNonNull(collaboration, "collaboration");
        modeDefaults = List.copyOf(modeDefaults);
        Objects.requireNonNull(initializationDeclaration, "initializationDeclaration");
        Objects.requireNonNull(sentinel, "sentinel");
        Objects.requireNonNull(specializationContext, "specializationContext");
        appliesToLanguages = List.copyOf(appliesToLanguages);
        directiveReferences = List.copyOf(directiveReferences);
        tacticReferences = List.copyOf(tacticReferences);
        toolguideReferences = List.copyOf(toolguideReferences);
        styleguideReferences = List.copyOf(styleguideReferences);
        selfReviewSteps = List.copyOf(selfReviewSteps);
        Objects.requireNonNull(excluding, "excluding");
    }

    /**
     * The {@code specialization}, carried verbatim.
     *
     * @param primaryFocus the {@code primary-focus}
     * @param secondaryAwareness the {@code secondary-awareness}, when declared
     * @param avoidanceBoundary the {@code avoidance-boundary}, when declared
     * @param successDefinition the {@code success-definition}, when declared
     */
    record Specialization(
            String primaryFocus,
            Optional<String> secondaryAwareness,
            Optional<String> avoidanceBoundary,
            Optional<String> successDefinition) {
        /**
         * Checks that every part is present.
         *
         * @param primaryFocus the primary focus
         * @param secondaryAwareness the secondary awareness
         * @param avoidanceBoundary the avoidance boundary
         * @param successDefinition the success definition
         */
        Specialization {
            Objects.requireNonNull(primaryFocus, "primaryFocus");
            Objects.requireNonNull(secondaryAwareness, "secondaryAwareness");
            Objects.requireNonNull(avoidanceBoundary, "avoidanceBoundary");
            Objects.requireNonNull(successDefinition, "successDefinition");
        }
    }

    /**
     * The {@code collaboration}, carried verbatim; every list empty when none is declared.
     *
     * @param handoffTo the {@code handoff-to}
     * @param handoffFrom the {@code handoff-from}
     * @param worksWith the {@code works-with}
     * @param outputArtifacts the {@code output-artifacts}
     * @param operatingProcedures the {@code operating-procedures}
     * @param canonicalVerbs the {@code canonical-verbs}
     */
    record Collaboration(
            List<String> handoffTo,
            List<String> handoffFrom,
            List<String> worksWith,
            List<String> outputArtifacts,
            List<String> operatingProcedures,
            List<String> canonicalVerbs) {
        /**
         * Copies the lists.
         *
         * @param handoffTo the handoff-to roles
         * @param handoffFrom the handoff-from roles
         * @param worksWith the works-with roles
         * @param outputArtifacts the output artifacts
         * @param operatingProcedures the operating procedures
         * @param canonicalVerbs the canonical verbs
         */
        Collaboration {
            handoffTo = List.copyOf(handoffTo);
            handoffFrom = List.copyOf(handoffFrom);
            worksWith = List.copyOf(worksWith);
            outputArtifacts = List.copyOf(outputArtifacts);
            operatingProcedures = List.copyOf(operatingProcedures);
            canonicalVerbs = List.copyOf(canonicalVerbs);
        }
    }

    /**
     * One item of {@code mode-defaults}, carried verbatim.
     *
     * @param mode the {@code mode}
     * @param description the {@code description}
     * @param useCase the {@code use-case}
     */
    record ModeDefault(String mode, String description, String useCase) {
        /**
         * Checks that every field is present.
         *
         * @param mode the mode
         * @param description the description
         * @param useCase the use case
         */
        ModeDefault {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(useCase, "useCase");
        }
    }

    /**
     * The {@code specialization-context}, carried verbatim; every list empty when none is declared.
     *
     * @param languages the {@code languages}
     * @param frameworks the {@code frameworks}
     * @param filePatterns the {@code file-patterns}
     * @param domainKeywords the {@code domain-keywords}
     * @param writingStyle the {@code writing-style}
     * @param complexityPreference the {@code complexity-preference}
     */
    record SpecializationContext(
            List<String> languages,
            List<String> frameworks,
            List<String> filePatterns,
            List<String> domainKeywords,
            List<String> writingStyle,
            List<String> complexityPreference) {
        /**
         * Copies the lists.
         *
         * @param languages the languages
         * @param frameworks the frameworks
         * @param filePatterns the file patterns
         * @param domainKeywords the domain keywords
         * @param writingStyle the writing styles
         * @param complexityPreference the complexity preferences
         */
        SpecializationContext {
            languages = List.copyOf(languages);
            frameworks = List.copyOf(frameworks);
            filePatterns = List.copyOf(filePatterns);
            domainKeywords = List.copyOf(domainKeywords);
            writingStyle = List.copyOf(writingStyle);
            complexityPreference = List.copyOf(complexityPreference);
        }
    }

    /**
     * One item of {@code directive-references}, carried verbatim.
     *
     * @param code the {@code code}, as text
     * @param name the {@code name}
     * @param rationale the {@code rationale}
     */
    record DirectiveRef(String code, String name, String rationale) {
        /**
         * Checks that every field is present.
         *
         * @param code the code
         * @param name the name
         * @param rationale the rationale
         */
        DirectiveRef {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(rationale, "rationale");
        }
    }

    /**
     * One item of a tactic, toolguide or styleguide reference list, carried verbatim.
     *
     * @param id the {@code id}
     * @param rationale the {@code rationale}
     */
    record ArtifactRef(String id, String rationale) {
        /**
         * Checks that both fields are present.
         *
         * @param id the id
         * @param rationale the rationale
         */
        ArtifactRef {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(rationale, "rationale");
        }
    }

    /**
     * One step of the {@code self-review-protocol}, carried verbatim.
     *
     * @param name the {@code name}
     * @param command the {@code command}, when declared
     * @param gate the {@code gate}
     */
    record SelfReviewStep(String name, Optional<String> command, String gate) {
        /**
         * Checks that every field is present.
         *
         * @param name the name
         * @param command the command
         * @param gate the gate
         */
        SelfReviewStep {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(gate, "gate");
        }
    }

    /** The {@code excluding}, in one of its two forms. */
    sealed interface Excluding {

        /**
         * The list form.
         *
         * @param fields the field names, in authored order
         */
        record Fields(List<String> fields) implements Excluding {
            /**
             * Copies the list.
             *
             * @param fields the field names
             */
            public Fields {
                fields = List.copyOf(fields);
            }
        }

        /**
         * The map form: an open map, kept as data with its key order.
         *
         * @param values the values per field name
         */
        record Values(Map<String, List<String>> values) implements Excluding {
            /**
             * Copies the map and its lists, keeping the key order.
             *
             * @param values the values per field name
             */
            public Values {
                Map<String, List<String>> copy = new LinkedHashMap<>();
                values.forEach((field, removed) -> copy.put(field, List.copyOf(removed)));
                values = Collections.unmodifiableMap(copy);
            }
        }
    }
}
