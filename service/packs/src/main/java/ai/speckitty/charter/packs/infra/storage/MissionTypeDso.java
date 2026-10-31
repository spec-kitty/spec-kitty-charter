package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of a mission type: the {@code mission-type} schema's fields (ADR-0044), each step with its
 * prompt and guidelines held as Markdown documents once the store has read the companions.
 *
 * @param id the {@code id}, the file stem
 * @param schemaVersion the {@code schema_version}
 * @param title the {@code title}
 * @param description the {@code description}
 * @param content the {@code content}, when present
 * @param requiresSubstantiveSpec whether {@code requires_substantive_spec: true} is written
 * @param pathConventions the {@code path_conventions} in written order, empty when absent
 * @param expectedOutputArtifacts the type's {@code expected_output_artifacts}
 * @param references the type's {@code references}
 * @param steps the {@code steps}, in order
 * @param onDemandSteps the {@code on_demand_steps}, in written order
 */
record MissionTypeDso(
        String id,
        String schemaVersion,
        String title,
        String description,
        Optional<String> content,
        boolean requiresSubstantiveSpec,
        Map<String, String> pathConventions,
        List<Artifact> expectedOutputArtifacts,
        List<Reference> references,
        List<Step> steps,
        List<Step> onDemandSteps) {

    /**
     * Checks that every component is present and copies the collections.
     *
     * @param id the id
     * @param schemaVersion the schema version
     * @param title the title
     * @param description the description
     * @param content the content, possibly empty
     * @param requiresSubstantiveSpec the flag
     * @param pathConventions the conventions, possibly empty
     * @param expectedOutputArtifacts the type's outputs
     * @param references the type's references
     * @param steps the steps
     * @param onDemandSteps the on-demand steps
     */
    MissionTypeDso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(content, "content");
        pathConventions = Collections.unmodifiableMap(new LinkedHashMap<>(pathConventions));
        expectedOutputArtifacts = List.copyOf(expectedOutputArtifacts);
        references = List.copyOf(references);
        steps = List.copyOf(steps);
        onDemandSteps = List.copyOf(onDemandSteps);
    }

    /**
     * The shape with its steps replaced, as the store fills in their companions.
     *
     * @param newSteps the steps
     * @param newOnDemandSteps the on-demand steps
     * @return the shape
     */
    MissionTypeDso withSteps(List<Step> newSteps, List<Step> newOnDemandSteps) {
        return new MissionTypeDso(
                id,
                schemaVersion,
                title,
                description,
                content,
                requiresSubstantiveSpec,
                pathConventions,
                expectedOutputArtifacts,
                references,
                newSteps,
                newOnDemandSteps);
    }

    /**
     * A step sub-item.
     *
     * @param id the step's {@code id}
     * @param title the {@code title}
     * @param description the {@code description}, when present
     * @param role the {@code role}, when present
     * @param promptPath the {@code prompt_path}, when present
     * @param guidelinesPath the {@code guidelines_path}, when present
     * @param expectedInputArtifacts the {@code expected_input_artifacts}
     * @param expectedOutputArtifacts the {@code expected_output_artifacts}
     * @param references the {@code references}
     * @param prompt the prompt document, once read
     * @param guidelines the guidelines document, once read
     */
    record Step(
            String id,
            String title,
            Optional<String> description,
            Optional<String> role,
            Optional<String> promptPath,
            Optional<String> guidelinesPath,
            List<Artifact> expectedInputArtifacts,
            List<Artifact> expectedOutputArtifacts,
            List<Reference> references,
            Optional<MarkdownDocument> prompt,
            Optional<MarkdownDocument> guidelines) {

        /**
         * Checks that every component is present and copies the lists.
         *
         * @param id the id
         * @param title the title
         * @param description the description, possibly empty
         * @param role the role, possibly empty
         * @param promptPath the prompt path, possibly empty
         * @param guidelinesPath the guidelines path, possibly empty
         * @param expectedInputArtifacts the inputs
         * @param expectedOutputArtifacts the outputs
         * @param references the references
         * @param prompt the prompt document, possibly empty
         * @param guidelines the guidelines document, possibly empty
         */
        Step {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(promptPath, "promptPath");
            Objects.requireNonNull(guidelinesPath, "guidelinesPath");
            expectedInputArtifacts = List.copyOf(expectedInputArtifacts);
            expectedOutputArtifacts = List.copyOf(expectedOutputArtifacts);
            references = List.copyOf(references);
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(guidelines, "guidelines");
        }

        /**
         * The step with its companion documents.
         *
         * @param newPrompt the prompt document, possibly empty
         * @param newGuidelines the guidelines document, possibly empty
         * @return the step
         */
        Step withDocuments(Optional<MarkdownDocument> newPrompt, Optional<MarkdownDocument> newGuidelines) {
            return new Step(
                    id,
                    title,
                    description,
                    role,
                    promptPath,
                    guidelinesPath,
                    expectedInputArtifacts,
                    expectedOutputArtifacts,
                    references,
                    newPrompt,
                    newGuidelines);
        }
    }

    /**
     * An expected artifact entry: an input has no class and no template.
     *
     * @param artifactKey the {@code artifact_key}
     * @param artifactClass the {@code artifact_class} of an output; empty for an input
     * @param pathPattern the {@code path_pattern}
     * @param blocking whether {@code blocking: true} is written
     * @param templatePath the {@code template_path} of an output, when present
     */
    record Artifact(
            String artifactKey,
            Optional<String> artifactClass,
            String pathPattern,
            boolean blocking,
            Optional<String> templatePath) {

        /**
         * Checks that every component is present.
         *
         * @param artifactKey the key
         * @param artifactClass the class, possibly empty
         * @param pathPattern the workspace glob
         * @param blocking the flag
         * @param templatePath the template path, possibly empty
         */
        Artifact {
            Objects.requireNonNull(artifactKey, "artifactKey");
            Objects.requireNonNull(artifactClass, "artifactClass");
            Objects.requireNonNull(pathPattern, "pathPattern");
            Objects.requireNonNull(templatePath, "templatePath");
        }
    }

    /**
     * A reference entry.
     *
     * @param type the {@code type} token
     * @param id the {@code id}
     * @param when the {@code when}, when present
     * @param reason the {@code reason}, when present
     */
    record Reference(String type, String id, Optional<String> when, Optional<String> reason) {

        /**
         * Checks that every component is present.
         *
         * @param type the kind token
         * @param id the id
         * @param when the when text, possibly empty
         * @param reason the reason text, possibly empty
         */
        Reference {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(when, "when");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
