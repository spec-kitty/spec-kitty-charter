package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A pack skill of a {@link Pack}: a thin, parameterised entry point, identified by its {@link SkillIdentifier}. A
 * prompt skill holds its Markdown body, the text of the file its {@code body_path} names (ADR-0039); a wrapper skill
 * holds the command it expands to. Two skills are equal when they have the same identity, whatever their other fields
 * hold.
 *
 * <p>Every field is kept as the file writes it: nothing is defaulted and nothing is forced. Build one with
 * {@link #builder()}.
 */
public final class Skill implements CharterArtifact {

    private static final ModularRuleset<Skill> RULES = ModularRuleset.<Skill>emptyRules()
            .must(Constraints.of(
                    "A skill needs a schema version that is not blank.", skill -> !skill.schemaVersion.isBlank()))
            .must(Constraints.of("A skill needs a title that is not blank.", skill -> !skill.title.isBlank()))
            .must(Constraints.of(
                    "A skill needs a description that is not blank.", skill -> !skill.description.isBlank()))
            .must(Constraints.of(
                    "A prompt skill needs a body path and its body, and no expansion.",
                    skill -> skill.form != SkillForm.PROMPT
                            || (skill.bodyPath.isPresent() && skill.body.isPresent() && skill.expandsTo.isEmpty())))
            .must(Constraints.of(
                    "A wrapper skill needs an expansion, and no body path and no body.",
                    skill -> skill.form != SkillForm.WRAPPER
                            || (skill.expandsTo.isPresent() && skill.bodyPath.isEmpty() && skill.body.isEmpty())));

    private final SkillIdentifier id;
    private final PackIdentifier packId;
    private final String schemaVersion;
    private final String title;
    private final String description;
    private final List<String> triggers;
    private final SkillForm form;
    private final Optional<String> bodyPath;
    private final Optional<String> body;
    private final Optional<SkillExpansion> expandsTo;
    private final List<SkillParameter> parameters;
    private final Optional<SkillInvocation> invocation;
    private final List<String> tools;
    private final Optional<String> version;
    private final List<String> maintainers;
    private final Optional<String> overrides;
    private final Optional<String> enhances;

    private Skill(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.packId = Objects.requireNonNull(builder.packId, "packId");
        this.schemaVersion = Objects.requireNonNull(builder.schemaVersion, "schemaVersion");
        this.title = Objects.requireNonNull(builder.title, "title");
        this.description = Objects.requireNonNull(builder.description, "description");
        this.triggers = List.copyOf(builder.triggers);
        this.form = Objects.requireNonNull(builder.form, "form");
        this.bodyPath = Objects.requireNonNull(builder.bodyPath, "bodyPath");
        this.body = Objects.requireNonNull(builder.body, "body");
        this.expandsTo = Objects.requireNonNull(builder.expandsTo, "expandsTo");
        this.parameters = List.copyOf(builder.parameters);
        this.invocation = Objects.requireNonNull(builder.invocation, "invocation");
        this.tools = List.copyOf(builder.tools);
        this.version = Objects.requireNonNull(builder.version, "version");
        this.maintainers = List.copyOf(builder.maintainers);
        this.overrides = Objects.requireNonNull(builder.overrides, "overrides");
        this.enhances = Objects.requireNonNull(builder.enhances, "enhances");
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
     * The skill's identity.
     *
     * @return the identifier
     */
    public SkillIdentifier id() {
        return id;
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.SKILL;
    }

    /**
     * The pack that owns this skill.
     *
     * @return the pack's identifier
     */
    @Override
    public PackIdentifier packId() {
        return packId;
    }

    @Override
    public String localId() {
        return id.value();
    }

    /**
     * The skill's {@code schema_version}, as text.
     *
     * @return the schema version
     */
    @Override
    public Optional<String> schemaVersion() {
        return Optional.of(schemaVersion);
    }

    /**
     * The skill's label, which is its {@code title}.
     *
     * @return the title
     */
    @Override
    public String label() {
        return title;
    }

    /**
     * The skill's {@code description}.
     *
     * @return the description, always present
     */
    @Override
    public Optional<String> description() {
        return Optional.of(description);
    }

    /**
     * The skill's {@code title}.
     *
     * @return the title
     */
    public String title() {
        return title;
    }

    /**
     * The phrases that trigger the skill, in authored order.
     *
     * @return an immutable list; empty when none is written
     */
    public List<String> triggers() {
        return triggers;
    }

    /**
     * The skill's form.
     *
     * @return prompt or wrapper
     */
    public SkillForm form() {
        return form;
    }

    /**
     * The {@code body_path} as written, relative to the skill's file; present exactly for a prompt skill.
     *
     * @return the body path
     */
    public Optional<String> bodyPath() {
        return bodyPath;
    }

    /**
     * The prompt body, the text of the file {@code body_path} names, exactly as read; present exactly for a prompt
     * skill.
     *
     * @return the body
     */
    public Optional<String> body() {
        return body;
    }

    /**
     * What a wrapper skill expands to; present exactly for a wrapper skill.
     *
     * @return the expansion
     */
    public Optional<SkillExpansion> expandsTo() {
        return expandsTo;
    }

    /**
     * The declared parameters, in authored order.
     *
     * @return an immutable list; empty when none is written
     */
    public List<SkillParameter> parameters() {
        return parameters;
    }

    /**
     * Who may invoke the skill, as written.
     *
     * @return the invocation, when written
     */
    public Optional<SkillInvocation> invocation() {
        return invocation;
    }

    /**
     * The tools the skill may use, in authored order.
     *
     * @return an immutable list; empty when none is written (no default is filled in)
     */
    public List<String> tools() {
        return tools;
    }

    /**
     * The skill's {@code version}, as text.
     *
     * @return the version, when written
     */
    public Optional<String> version() {
        return version;
    }

    /**
     * The skill's maintainers, in authored order.
     *
     * @return an immutable list; empty when none is written
     */
    public List<String> maintainers() {
        return maintainers;
    }

    /**
     * The skill this one replaces.
     *
     * @return its id, when written
     */
    public Optional<String> overrides() {
        return overrides;
    }

    /**
     * The skill this one narrows.
     *
     * @return its id, when written
     */
    public Optional<String> enhances() {
        return enhances;
    }

    /**
     * A skill mints no relationships: its links to procedures and directives are edges of the pack's fragment, and
     * {@code overrides}/{@code enhances} only name another skill.
     *
     * @return an empty list
     */
    @Override
    public List<Relationship> relationships() {
        return List.of();
    }

    /** Entity identity: equal when the identifier is equal, whatever the other fields hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof Skill that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Skill[" + id.value() + " of pack " + packId.value() + "]";
    }

    /** Fluent builder for a {@link Skill}; {@link #build()} validates. */
    public static final class Builder {
        private @Nullable SkillIdentifier id;
        private @Nullable PackIdentifier packId;
        private @Nullable String schemaVersion;
        private @Nullable String title;
        private @Nullable String description;
        private final List<String> triggers = new ArrayList<>();
        private @Nullable SkillForm form;
        private Optional<String> bodyPath = Optional.empty();
        private Optional<String> body = Optional.empty();
        private Optional<SkillExpansion> expandsTo = Optional.empty();
        private final List<SkillParameter> parameters = new ArrayList<>();
        private Optional<SkillInvocation> invocation = Optional.empty();
        private final List<String> tools = new ArrayList<>();
        private Optional<String> version = Optional.empty();
        private final List<String> maintainers = new ArrayList<>();
        private Optional<String> overrides = Optional.empty();
        private Optional<String> enhances = Optional.empty();

        private Builder() {}

        /**
         * Sets the skill's identity.
         *
         * @param value the identifier
         * @return this builder
         */
        public Builder id(SkillIdentifier value) {
            this.id = value;
            return this;
        }

        /**
         * Sets the owning pack.
         *
         * @param value the pack's identifier
         * @return this builder
         */
        public Builder packId(PackIdentifier value) {
            this.packId = value;
            return this;
        }

        /**
         * Sets the schema version text.
         *
         * @param value the schema version
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
         * Appends trigger phrases.
         *
         * @param values the phrases, in order
         * @return this builder
         */
        public Builder triggers(List<String> values) {
            this.triggers.addAll(values);
            return this;
        }

        /**
         * Sets the form.
         *
         * @param value the form
         * @return this builder
         */
        public Builder form(SkillForm value) {
            this.form = value;
            return this;
        }

        /**
         * Sets the body path, as written.
         *
         * @param value the body path, possibly empty
         * @return this builder
         */
        public Builder bodyPath(Optional<String> value) {
            this.bodyPath = value;
            return this;
        }

        /**
         * Sets the body text.
         *
         * @param value the body, possibly empty
         * @return this builder
         */
        public Builder body(Optional<String> value) {
            this.body = value;
            return this;
        }

        /**
         * Sets the expansion.
         *
         * @param value the expansion, possibly empty
         * @return this builder
         */
        public Builder expandsTo(Optional<SkillExpansion> value) {
            this.expandsTo = value;
            return this;
        }

        /**
         * Appends parameters.
         *
         * @param values the parameters, in order
         * @return this builder
         */
        public Builder parameters(List<SkillParameter> values) {
            this.parameters.addAll(values);
            return this;
        }

        /**
         * Sets the invocation.
         *
         * @param value the invocation, possibly empty
         * @return this builder
         */
        public Builder invocation(Optional<SkillInvocation> value) {
            this.invocation = value;
            return this;
        }

        /**
         * Appends tools.
         *
         * @param values the tools, in order
         * @return this builder
         */
        public Builder tools(List<String> values) {
            this.tools.addAll(values);
            return this;
        }

        /**
         * Sets the version text.
         *
         * @param value the version, possibly empty
         * @return this builder
         */
        public Builder version(Optional<String> value) {
            this.version = value;
            return this;
        }

        /**
         * Appends maintainers.
         *
         * @param values the maintainers, in order
         * @return this builder
         */
        public Builder maintainers(List<String> values) {
            this.maintainers.addAll(values);
            return this;
        }

        /**
         * Sets the skill this one replaces.
         *
         * @param value its id, possibly empty
         * @return this builder
         */
        public Builder overrides(Optional<String> value) {
            this.overrides = value;
            return this;
        }

        /**
         * Sets the skill this one narrows.
         *
         * @param value its id, possibly empty
         * @return this builder
         */
        public Builder enhances(Optional<String> value) {
            this.enhances = value;
            return this;
        }

        /**
         * Builds the skill.
         *
         * @return the validated skill
         * @throws NullPointerException when identity, pack, schema version, title, description or form was never set
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
         */
        public Skill build() {
            return new Skill(this);
        }
    }
}
