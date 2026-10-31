package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * A mission type of a pack (ADR-0044): its steps in order, the steps invoked on demand, the artifacts it expects and
 * the guidance it references. It is identified by its {@link MissionTypeIdentifier}; two mission types are equal when
 * they have the same identity, whatever their other fields hold.
 *
 * <p>It owns its steps: the order of {@link #steps()} is the run order, and the order of {@link #onDemandSteps()} is
 * kept as authored but is not a run order. Step ids are unique across both. Build one with {@link #builder()}.
 */
public final class MissionType implements CharterArtifact {

    private static final ModularRuleset<MissionType> RULES = ModularRuleset.<MissionType>emptyRules()
            .must(Constraints.of("A mission type needs at least one step.", type -> !type.steps.isEmpty()))
            .must(Constraints.of("A mission type needs a title that is not blank.", type -> !type.title.isBlank()))
            .must(Constraints.of(
                    "A mission type needs a description that is not blank.", type -> !type.description.isBlank()))
            .must(Constraints.of(
                    "A mission type needs a schema version that is not blank.", type -> !type.schemaVersion.isBlank()))
            .must(Constraints.of(
                    "A mission type's content is not blank.",
                    type -> type.content.map(text -> !text.isBlank()).orElse(true)))
            .must(Constraints.of(
                    "A step id stands once across a mission type's steps and on-demand steps.",
                    MissionType::stepIdsAreUnique));
    private static final ModularRuleset<List<String>> KEY_RULES = ModularRuleset.<List<String>>emptyRules()
            .must(Constraints.of(
                    "An artifact key stands once in a mission type's expected outputs.", MissionStep::unique));

    private final MissionTypeIdentifier id;
    private final PackIdentifier packId;
    private final String schemaVersion;
    private final String title;
    private final String description;
    private final Optional<String> content;
    private final boolean requiresSubstantiveSpec;
    private final Optional<PathConventions> pathConventions;
    private final List<ExpectedArtifact> expectedOutputArtifacts;
    private final List<MissionReference> references;
    private final List<MissionStep> steps;
    private final List<MissionStep> onDemandSteps;

    private MissionType(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.packId = Objects.requireNonNull(builder.packId, "packId");
        this.schemaVersion = Objects.requireNonNull(builder.schemaVersion, "schemaVersion");
        this.title = Objects.requireNonNull(builder.title, "title");
        this.description = Objects.requireNonNull(builder.description, "description");
        this.content = Optional.ofNullable(builder.content);
        this.requiresSubstantiveSpec = builder.requiresSubstantiveSpec;
        this.pathConventions = Optional.ofNullable(builder.pathConventions);
        this.expectedOutputArtifacts = List.copyOf(builder.expectedOutputArtifacts);
        this.references = List.copyOf(builder.references);
        this.steps = List.copyOf(builder.steps);
        this.onDemandSteps = List.copyOf(builder.onDemandSteps);
        KEY_RULES.guard(expectedOutputArtifacts.stream()
                .map(ExpectedArtifact::artifactKey)
                .toList());
        RULES.guard(this);
    }

    /**
     * Starts a builder.
     *
     * @return an empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The mission type's identity.
     *
     * @return the identifier
     */
    public MissionTypeIdentifier id() {
        return id;
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.MISSION_TYPE;
    }

    @Override
    public String localId() {
        return id.value();
    }

    @Override
    public PackIdentifier packId() {
        return packId;
    }

    @Override
    public Optional<String> schemaVersion() {
        return Optional.of(schemaVersion);
    }

    /**
     * The mission type's {@code title}.
     *
     * @return the title
     */
    @Override
    public String label() {
        return title;
    }

    /**
     * The mission type's {@code description}; always present for a mission type.
     *
     * @return the description
     */
    @Override
    public Optional<String> description() {
        return Optional.of(description);
    }

    /**
     * A mission type mints no relationship: always empty, even when it or its steps carry references. The references
     * are kept but not turned into edges until #189 rules on edge authority; the graph's {@code scope} edges carry
     * their meaning meanwhile (ADR-0044 decision 3).
     *
     * @return an empty list
     */
    @Override
    public List<Relationship> relationships() {
        return List.of();
    }

    /**
     * The mission type's {@code title}.
     *
     * @return the title
     */
    public String title() {
        return title;
    }

    /**
     * The standing context given to the agent of every step.
     *
     * @return the content; empty when the type has none
     */
    public Optional<String> content() {
        return content;
    }

    /**
     * Whether a mission of this type requires a substantive specification.
     *
     * @return {@code true} when required
     */
    public boolean requiresSubstantiveSpec() {
        return requiresSubstantiveSpec;
    }

    /**
     * Where a mission of this type keeps its work.
     *
     * @return the conventions; empty when the type names none
     */
    public Optional<PathConventions> pathConventions() {
        return pathConventions;
    }

    /**
     * The mission's own postconditions: artifacts of the whole mission, not tied to a step.
     *
     * @return the expected outputs, in authored order
     */
    public List<ExpectedArtifact> expectedOutputArtifacts() {
        return expectedOutputArtifacts;
    }

    /**
     * The type's recipe ingredients: the guidance it references.
     *
     * @return the references, in authored order
     */
    public List<MissionReference> references() {
        return references;
    }

    /**
     * The sequence: the steps in run order.
     *
     * @return the steps; never empty
     */
    public List<MissionStep> steps() {
        return steps;
    }

    /**
     * The steps invoked by an explicit command, outside the sequence.
     *
     * @return the on-demand steps, in authored order; their order is not a run order
     */
    public List<MissionStep> onDemandSteps() {
        return onDemandSteps;
    }

    /**
     * A step by id, from the sequence or the on-demand steps.
     *
     * @param stepId the step's id
     * @return the step; empty when the type has no step of that id
     */
    public Optional<MissionStep> step(String stepId) {
        return Stream.concat(steps.stream(), onDemandSteps.stream())
                .filter(step -> step.id().equals(stepId))
                .findFirst();
    }

    /**
     * Every output template the type names (#277): the type's own outputs first, then each step's in the sequence,
     * then each on-demand step's, each in authored order, the order the stored shape names them.
     *
     * @return the references, with the step that names each
     */
    public List<TemplateReference> templateReferences() {
        List<TemplateReference> references = new ArrayList<>();
        expectedOutputArtifacts.forEach(artifact -> artifact.templatePath()
                .ifPresent(path -> references.add(new TemplateReference(Optional.empty(), path))));
        Stream.concat(steps.stream(), onDemandSteps.stream())
                .forEach(step -> step.expectedOutputArtifacts()
                        .forEach(artifact -> artifact.templatePath()
                                .ifPresent(
                                        path -> references.add(new TemplateReference(Optional.of(step.id()), path)))));
        return List.copyOf(references);
    }

    /**
     * The output templates that name no template of the pack (#277, ADR-0044 decision 7 as amended): a path resolves
     * when the pack holds a template whose id is {@link TemplateReference#templateId}, the path kept as written.
     *
     * @param templateIds the ids of the templates the pack holds
     * @return the unresolved references, in {@link #templateReferences} order; empty when every one resolves
     */
    public List<UnresolvedTemplate> unresolvedTemplates(Set<String> templateIds) {
        return templateReferences().stream()
                .filter(reference -> !templateIds.contains(reference.templateId(id)))
                .map(reference -> new UnresolvedTemplate(id, reference))
                .toList();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof MissionType type && id.equals(type.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "MissionType[" + id.value() + "]";
    }

    private static boolean stepIdsAreUnique(MissionType type) {
        Set<String> ids = new HashSet<>();
        return Stream.concat(type.steps.stream(), type.onDemandSteps.stream()).allMatch(step -> ids.add(step.id()));
    }

    /** Builds a {@link MissionType}; every field but the optional ones must be set before {@link #build()}. */
    public static final class Builder {
        private @Nullable MissionTypeIdentifier id;
        private @Nullable PackIdentifier packId;
        private @Nullable String schemaVersion;
        private @Nullable String title;
        private @Nullable String description;
        private @Nullable String content;
        private boolean requiresSubstantiveSpec;
        private @Nullable PathConventions pathConventions;
        private final List<ExpectedArtifact> expectedOutputArtifacts = new ArrayList<>();
        private final List<MissionReference> references = new ArrayList<>();
        private final List<MissionStep> steps = new ArrayList<>();
        private final List<MissionStep> onDemandSteps = new ArrayList<>();

        private Builder() {}

        /**
         * Sets the identity.
         *
         * @param value the identifier
         * @return this builder
         */
        public Builder id(MissionTypeIdentifier value) {
            this.id = value;
            return this;
        }

        /**
         * Sets the owning pack.
         *
         * @param value the pack's identity
         * @return this builder
         */
        public Builder packId(PackIdentifier value) {
            this.packId = value;
            return this;
        }

        /**
         * Sets the schema version.
         *
         * @param value the {@code schema_version} text
         * @return this builder
         */
        public Builder schemaVersion(String value) {
            this.schemaVersion = value;
            return this;
        }

        /**
         * Sets the title.
         *
         * @param value the title
         * @return this builder
         */
        public Builder title(String value) {
            this.title = value;
            return this;
        }

        /**
         * Sets the description.
         *
         * @param value the description
         * @return this builder
         */
        public Builder description(String value) {
            this.description = value;
            return this;
        }

        /**
         * Sets the standing content; {@code null} for none.
         *
         * @param value the content
         * @return this builder
         */
        public Builder content(@Nullable String value) {
            this.content = value;
            return this;
        }

        /**
         * Sets whether a substantive specification is required.
         *
         * @param value {@code true} when required
         * @return this builder
         */
        public Builder requiresSubstantiveSpec(boolean value) {
            this.requiresSubstantiveSpec = value;
            return this;
        }

        /**
         * Sets the path conventions; {@code null} for none.
         *
         * @param value the conventions
         * @return this builder
         */
        public Builder pathConventions(@Nullable PathConventions value) {
            this.pathConventions = value;
            return this;
        }

        /**
         * Appends one of the mission's own expected outputs.
         *
         * @param value the artifact
         * @return this builder
         */
        public Builder expectedOutputArtifact(ExpectedArtifact value) {
            this.expectedOutputArtifacts.add(Objects.requireNonNull(value, "expectedOutputArtifact"));
            return this;
        }

        /**
         * Appends a type-level reference.
         *
         * @param value the reference
         * @return this builder
         */
        public Builder reference(MissionReference value) {
            this.references.add(Objects.requireNonNull(value, "reference"));
            return this;
        }

        /**
         * Appends a step to the sequence.
         *
         * @param value the step
         * @return this builder
         */
        public Builder step(MissionStep value) {
            this.steps.add(Objects.requireNonNull(value, "step"));
            return this;
        }

        /**
         * Appends an on-demand step.
         *
         * @param value the step
         * @return this builder
         */
        public Builder onDemandStep(MissionStep value) {
            this.onDemandSteps.add(Objects.requireNonNull(value, "onDemandStep"));
            return this;
        }

        /**
         * Builds the mission type.
         *
         * @return the mission type
         * @throws NullPointerException when a required field is unset
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
         */
        public MissionType build() {
            return new MissionType(this);
        }
    }
}
