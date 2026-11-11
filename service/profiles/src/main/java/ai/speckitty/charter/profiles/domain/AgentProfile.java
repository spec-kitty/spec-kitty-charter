package ai.speckitty.charter.profiles.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RoleBearer;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * An agent profile of a pack: an agent's identity, specialization, collaboration and the charter artifacts it applies,
 * identified by its {@link AgentProfileIdentifier}. Two profiles are equal when they have the same identity, whatever
 * their other fields hold.
 *
 * <p>It holds every field the schema declares. A field with a schema default is optional and empty when the profile
 * does not declare it, so a profile is written back as it was authored and no default is invented; the default it
 * stands for is named on each accessor. Lists keep their authored order and duplicates. Lineage is not a field: it is a
 * {@code specializes_from} edge of the pack's graph, and resolving {@code enhances}, {@code overrides} or
 * {@link #excluding()} is not done here (ADR-0032). Build one with {@link #builder()}.
 */
public final class AgentProfile implements CharterArtifact, RoleBearer {

    private static final Pattern SCHEMA_VERSION = Pattern.compile("^(1\\.0|2)$");
    private static final Pattern ROLE = Pattern.compile("^[a-z][a-z0-9-]*+$");

    /** The rule text of an alt-role that repeats the role or an earlier alt-role (ADR-0069 decision 1). */
    public static final String ALT_ROLES_DISTINCT =
            "An agent profile's alt-roles must be distinct and must not repeat its role.";

    /** The rule text of an alt-role that is the reserved role {@value RoleBearer#GENERIC} (ADR-0068 decision 4). */
    public static final String ALT_ROLES_NOT_GENERIC =
            "An agent profile's alt-roles must not contain the reserved role generic.";

    /** The reserved role of a fallback profile (ADR-0068 decision 4); it never earns a role fit. */
    public static final String GENERIC_ROLE = RoleBearer.GENERIC;

    /** The longest role token; the cap of an identifier (#446). */
    public static final int ROLE_MAX = 64;

    private static final ModularRuleset<AgentProfile> RULES = ModularRuleset.<AgentProfile>emptyRules()
            .must(Constraints.of(
                    "An agent profile's schema version must be 1.0 or 2.",
                    profile -> profile.schemaVersion
                            .map(version -> SCHEMA_VERSION.matcher(version).matches())
                            .orElse(true)))
            .must(Constraints.of(
                    "An agent profile's role and alt-roles must be lowercase letters, digits and hyphens, starting"
                            + " with a letter, at most " + AgentProfile.ROLE_MAX + " characters.",
                    profile -> java.util.stream.Stream.concat(
                                    java.util.stream.Stream.of(profile.role), profile.altRoles.stream())
                            .allMatch(AgentProfile::wellFormedRole)))
            .must(Constraints.of(ALT_ROLES_NOT_GENERIC, profile -> !profile.altRoles.contains(GENERIC_ROLE)))
            .must(Constraints.of(
                    ALT_ROLES_DISTINCT,
                    profile ->
                            firstRepeatedAltRole(profile.role, profile.altRoles).isEmpty()))
            .must(Constraints.of(
                    "An agent profile's routing priority must be between 0 and 100.",
                    profile -> profile.routingPriority
                            .map(priority -> priority >= 0 && priority <= 100)
                            .orElse(true)))
            .must(Constraints.of(
                    "An agent profile's maximum of concurrent tasks must be above 0.",
                    profile -> profile.maxConcurrentTasks.map(max -> max > 0).orElse(true)));

    private final AgentProfileIdentifier id;
    private final PackIdentifier packId;
    private final Optional<String> schemaVersion;
    private final String name;
    private final Optional<String> description;
    private final String role;
    private final List<String> altRoles;
    private final Optional<String> avatarImage;
    private final List<String> capabilities;
    private final Optional<Integer> routingPriority;
    private final Optional<Integer> maxConcurrentTasks;
    private final Optional<String> model;
    private final Optional<String> effort;
    private final String purpose;
    private final Specialization specialization;
    private final Collaboration collaboration;
    private final List<ModeDefault> modeDefaults;
    private final Optional<String> initializationDeclaration;
    private final Optional<Boolean> sentinel;
    private final SpecializationContext specializationContext;
    private final List<String> appliesToLanguages;
    private final List<DirectiveReference> directiveReferences;
    private final List<ArtifactReference> tacticReferences;
    private final List<ArtifactReference> toolguideReferences;
    private final List<ArtifactReference> styleguideReferences;
    private final List<SelfReviewStep> selfReviewSteps;
    private final Optional<Exclusion> excluding;

    private AgentProfile(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.packId = Objects.requireNonNull(builder.packId, "packId");
        this.schemaVersion = builder.schemaVersion;
        this.name = Objects.requireNonNull(builder.name, "name");
        this.description = builder.description;
        this.role = Objects.requireNonNull(builder.role, "role");
        this.altRoles = List.copyOf(builder.altRoles);
        this.avatarImage = builder.avatarImage;
        this.capabilities = List.copyOf(builder.capabilities);
        this.routingPriority = builder.routingPriority;
        this.maxConcurrentTasks = builder.maxConcurrentTasks;
        this.model = builder.model;
        this.effort = builder.effort;
        this.purpose = Objects.requireNonNull(builder.purpose, "purpose");
        this.specialization = Objects.requireNonNull(builder.specialization, "specialization");
        this.collaboration = builder.collaboration;
        this.modeDefaults = List.copyOf(builder.modeDefaults);
        this.initializationDeclaration = builder.initializationDeclaration;
        this.sentinel = builder.sentinel;
        this.specializationContext = builder.specializationContext;
        this.appliesToLanguages = List.copyOf(builder.appliesToLanguages);
        this.directiveReferences = List.copyOf(builder.directiveReferences);
        this.tacticReferences = List.copyOf(builder.tacticReferences);
        this.toolguideReferences = List.copyOf(builder.toolguideReferences);
        this.styleguideReferences = List.copyOf(builder.styleguideReferences);
        this.selfReviewSteps = List.copyOf(builder.selfReviewSteps);
        this.excluding = builder.excluding;
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
     * The profile's identity.
     *
     * @return the identifier
     */
    public AgentProfileIdentifier id() {
        return id;
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.AGENT_PROFILE;
    }

    @Override
    public PackIdentifier packId() {
        return packId;
    }

    @Override
    public String localId() {
        return id.value();
    }

    /**
     * The profile's {@code schema-version}, as text, when declared; the schema reads an absent one as {@code 1.0}.
     *
     * @return the declared schema version, or empty
     */
    @Override
    public Optional<String> schemaVersion() {
        return schemaVersion;
    }

    /**
     * The profile's {@code name}.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    @Override
    public String label() {
        return name;
    }

    /**
     * The profile's {@code description}, when declared; the schema reads an absent one as the empty text.
     *
     * @return the declared description, or empty
     */
    @Override
    public Optional<String> description() {
        return description;
    }

    /**
     * The profile's primary role, its {@code role} (ADR-0069).
     *
     * @return the primary role
     */
    @Override
    public String role() {
        return role;
    }

    /**
     * The profile's {@code alt-roles}, in authored order (ADR-0069).
     *
     * @return an immutable list, empty when it has none
     */
    @Override
    public List<String> altRoles() {
        return altRoles;
    }

    /**
     * How the profile fits a role a step asks for (ADR-0069 decision 4): {@link RoleFit#PRIMARY} for its role,
     * {@link RoleFit#ALT} for one of its alt-roles, otherwise {@link RoleFit#NONE}. A profile whose role is the reserved
     * {@value #GENERIC_ROLE} fits no role, and no profile fits the role {@value #GENERIC_ROLE}: it is served only as a
     * fallback (ADR-0068 decision 4).
     *
     * @param asked the role asked for
     * @return the fit
     */
    public RoleFit roleFit(String asked) {
        Objects.requireNonNull(asked, "asked");
        if (GENERIC_ROLE.equals(role) || GENERIC_ROLE.equals(asked)) {
            return RoleFit.NONE;
        }
        if (role.equals(asked)) {
            return RoleFit.PRIMARY;
        }
        return altRoles.contains(asked) ? RoleFit.ALT : RoleFit.NONE;
    }

    /**
     * The index of the first alt-role that repeats the role or an earlier alt-role (ADR-0069 decision 3): the one rule
     * of the role fields a schema cannot state, shared by the entity and the pack reader.
     *
     * @param role the role
     * @param altRoles the alt-roles, in order
     * @return the index of the first repeat, or empty
     */
    public static java.util.OptionalInt firstRepeatedAltRole(String role, List<String> altRoles) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        seen.add(role);
        for (int i = 0; i < altRoles.size(); i++) {
            if (!seen.add(altRoles.get(i))) {
                return java.util.OptionalInt.of(i);
            }
        }
        return java.util.OptionalInt.empty();
    }

    private static boolean wellFormedRole(String role) {
        return role.length() <= ROLE_MAX && ROLE.matcher(role).matches();
    }

    /**
     * The {@code avatar-image}, when declared.
     *
     * @return the image reference, or empty
     */
    public Optional<String> avatarImage() {
        return avatarImage;
    }

    /**
     * The {@code capabilities}, in authored order.
     *
     * @return an immutable list
     */
    public List<String> capabilities() {
        return capabilities;
    }

    /**
     * The {@code routing-priority}, when declared; the schema reads an absent one as 50.
     *
     * @return the declared priority, 0 to 100, or empty
     */
    public Optional<Integer> routingPriority() {
        return routingPriority;
    }

    /**
     * The {@code max-concurrent-tasks}, when declared; the schema reads an absent one as 5.
     *
     * @return the declared maximum, above 0, or empty
     */
    public Optional<Integer> maxConcurrentTasks() {
        return maxConcurrentTasks;
    }

    /**
     * The {@code model}, when declared.
     *
     * @return the model, or empty
     */
    public Optional<String> model() {
        return model;
    }

    /**
     * The {@code effort}, when declared.
     *
     * @return the effort, or empty
     */
    public Optional<String> effort() {
        return effort;
    }

    /**
     * The profile's {@code purpose}.
     *
     * @return the purpose
     */
    public String purpose() {
        return purpose;
    }

    /**
     * The profile's {@code specialization}.
     *
     * @return the specialization
     */
    public Specialization specialization() {
        return specialization;
    }

    /**
     * The profile's {@code collaboration}; {@link Collaboration#NONE} when it declares none.
     *
     * @return the collaboration
     */
    public Collaboration collaboration() {
        return collaboration;
    }

    /**
     * The {@code mode-defaults}, in authored order.
     *
     * @return an immutable list
     */
    public List<ModeDefault> modeDefaults() {
        return modeDefaults;
    }

    /**
     * The {@code initialization-declaration}, when declared; the schema reads an absent one as the empty text.
     *
     * @return the declaration, or empty
     */
    public Optional<String> initializationDeclaration() {
        return initializationDeclaration;
    }

    /**
     * The {@code sentinel} flag, when declared; the schema reads an absent one as {@code false}.
     *
     * @return the declared flag, or empty
     */
    public Optional<Boolean> sentinel() {
        return sentinel;
    }

    /**
     * The {@code specialization-context}; {@link SpecializationContext#NONE} when it declares none.
     *
     * @return the context
     */
    public SpecializationContext specializationContext() {
        return specializationContext;
    }

    /**
     * The {@code applies_to_languages}, in authored order.
     *
     * @return an immutable list
     */
    public List<String> appliesToLanguages() {
        return appliesToLanguages;
    }

    /**
     * The {@code directive-references}, in authored order.
     *
     * @return an immutable list
     */
    public List<DirectiveReference> directiveReferences() {
        return directiveReferences;
    }

    /**
     * The {@code tactic-references}, in authored order.
     *
     * @return an immutable list
     */
    public List<ArtifactReference> tacticReferences() {
        return tacticReferences;
    }

    /**
     * The {@code toolguide-references}, in authored order.
     *
     * @return an immutable list
     */
    public List<ArtifactReference> toolguideReferences() {
        return toolguideReferences;
    }

    /**
     * The {@code styleguide-references}, in authored order.
     *
     * @return an immutable list
     */
    public List<ArtifactReference> styleguideReferences() {
        return styleguideReferences;
    }

    /**
     * The steps of the {@code self-review-protocol}, in authored order.
     *
     * @return an immutable list; empty when the profile declares no protocol
     */
    public List<SelfReviewStep> selfReviewSteps() {
        return selfReviewSteps;
    }

    /**
     * The {@code excluding} declaration, as authored, when declared.
     *
     * @return the exclusion, or empty
     */
    public Optional<Exclusion> excluding() {
        return excluding;
    }

    /** Entity identity: equal when the identifier is equal, whatever the other fields hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof AgentProfile that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "AgentProfile[" + id.value() + " of pack " + packId.value() + "]";
    }

    /** Fluent builder for an {@link AgentProfile}; {@link #build()} validates. */
    public static final class Builder {
        private @Nullable AgentProfileIdentifier id;
        private @Nullable PackIdentifier packId;
        private Optional<String> schemaVersion = Optional.empty();
        private @Nullable String name;
        private Optional<String> description = Optional.empty();
        private @Nullable String role;
        private final List<String> altRoles = new ArrayList<>();
        private Optional<String> avatarImage = Optional.empty();
        private final List<String> capabilities = new ArrayList<>();
        private Optional<Integer> routingPriority = Optional.empty();
        private Optional<Integer> maxConcurrentTasks = Optional.empty();
        private Optional<String> model = Optional.empty();
        private Optional<String> effort = Optional.empty();
        private @Nullable String purpose;
        private @Nullable Specialization specialization;
        private Collaboration collaboration = Collaboration.NONE;
        private final List<ModeDefault> modeDefaults = new ArrayList<>();
        private Optional<String> initializationDeclaration = Optional.empty();
        private Optional<Boolean> sentinel = Optional.empty();
        private SpecializationContext specializationContext = SpecializationContext.NONE;
        private final List<String> appliesToLanguages = new ArrayList<>();
        private final List<DirectiveReference> directiveReferences = new ArrayList<>();
        private final List<ArtifactReference> tacticReferences = new ArrayList<>();
        private final List<ArtifactReference> toolguideReferences = new ArrayList<>();
        private final List<ArtifactReference> styleguideReferences = new ArrayList<>();
        private final List<SelfReviewStep> selfReviewSteps = new ArrayList<>();
        private Optional<Exclusion> excluding = Optional.empty();

        private Builder() {}

        /**
         * Sets the profile's identity.
         *
         * @param value the identifier
         * @return this builder
         */
        public Builder id(AgentProfileIdentifier value) {
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
         * Sets the declared schema version.
         *
         * @param value the schema version, or empty when not declared
         * @return this builder
         */
        public Builder schemaVersion(Optional<String> value) {
            this.schemaVersion = Objects.requireNonNull(value, "schemaVersion");
            return this;
        }

        /**
         * Sets the name.
         *
         * @param value the name
         * @return this builder
         */
        public Builder name(String value) {
            this.name = value;
            return this;
        }

        /**
         * Sets the declared description.
         *
         * @param value the description, or empty when not declared
         * @return this builder
         */
        public Builder description(Optional<String> value) {
            this.description = Objects.requireNonNull(value, "description");
            return this;
        }

        /**
         * Sets the primary {@code role} (ADR-0069).
         *
         * @param value the role
         * @return this builder
         */
        public Builder role(String value) {
            this.role = Objects.requireNonNull(value, "role");
            return this;
        }

        /**
         * Appends one {@code alt-roles} entry (ADR-0069).
         *
         * @param value the alt-role
         * @return this builder
         */
        public Builder altRole(String value) {
            this.altRoles.add(Objects.requireNonNull(value, "altRole"));
            return this;
        }

        /**
         * Sets the declared avatar image.
         *
         * @param value the image reference, or empty
         * @return this builder
         */
        public Builder avatarImage(Optional<String> value) {
            this.avatarImage = Objects.requireNonNull(value, "avatarImage");
            return this;
        }

        /**
         * Appends a capability.
         *
         * @param value the capability
         * @return this builder
         */
        public Builder capability(String value) {
            this.capabilities.add(Objects.requireNonNull(value, "capability"));
            return this;
        }

        /**
         * Sets the declared routing priority.
         *
         * @param value the priority, or empty when not declared
         * @return this builder
         */
        public Builder routingPriority(Optional<Integer> value) {
            this.routingPriority = Objects.requireNonNull(value, "routingPriority");
            return this;
        }

        /**
         * Sets the declared maximum of concurrent tasks.
         *
         * @param value the maximum, or empty when not declared
         * @return this builder
         */
        public Builder maxConcurrentTasks(Optional<Integer> value) {
            this.maxConcurrentTasks = Objects.requireNonNull(value, "maxConcurrentTasks");
            return this;
        }

        /**
         * Sets the declared model.
         *
         * @param value the model, or empty
         * @return this builder
         */
        public Builder model(Optional<String> value) {
            this.model = Objects.requireNonNull(value, "model");
            return this;
        }

        /**
         * Sets the declared effort.
         *
         * @param value the effort, or empty
         * @return this builder
         */
        public Builder effort(Optional<String> value) {
            this.effort = Objects.requireNonNull(value, "effort");
            return this;
        }

        /**
         * Sets the purpose.
         *
         * @param value the purpose
         * @return this builder
         */
        public Builder purpose(String value) {
            this.purpose = value;
            return this;
        }

        /**
         * Sets the specialization.
         *
         * @param value the specialization
         * @return this builder
         */
        public Builder specialization(Specialization value) {
            this.specialization = value;
            return this;
        }

        /**
         * Sets the collaboration.
         *
         * @param value the collaboration; {@link Collaboration#NONE} when none is declared
         * @return this builder
         */
        public Builder collaboration(Collaboration value) {
            this.collaboration = Objects.requireNonNull(value, "collaboration");
            return this;
        }

        /**
         * Appends a mode default.
         *
         * @param value the mode default
         * @return this builder
         */
        public Builder modeDefault(ModeDefault value) {
            this.modeDefaults.add(Objects.requireNonNull(value, "modeDefault"));
            return this;
        }

        /**
         * Sets the declared initialization declaration.
         *
         * @param value the declaration, or empty when not declared
         * @return this builder
         */
        public Builder initializationDeclaration(Optional<String> value) {
            this.initializationDeclaration = Objects.requireNonNull(value, "initializationDeclaration");
            return this;
        }

        /**
         * Sets the declared sentinel flag.
         *
         * @param value the flag, or empty when not declared
         * @return this builder
         */
        public Builder sentinel(Optional<Boolean> value) {
            this.sentinel = Objects.requireNonNull(value, "sentinel");
            return this;
        }

        /**
         * Sets the specialization context.
         *
         * @param value the context; {@link SpecializationContext#NONE} when none is declared
         * @return this builder
         */
        public Builder specializationContext(SpecializationContext value) {
            this.specializationContext = Objects.requireNonNull(value, "specializationContext");
            return this;
        }

        /**
         * Appends a language of {@code applies_to_languages}.
         *
         * @param value the language
         * @return this builder
         */
        public Builder appliesToLanguage(String value) {
            this.appliesToLanguages.add(Objects.requireNonNull(value, "appliesToLanguage"));
            return this;
        }

        /**
         * Appends a directive reference.
         *
         * @param value the reference
         * @return this builder
         */
        public Builder directiveReference(DirectiveReference value) {
            this.directiveReferences.add(Objects.requireNonNull(value, "directiveReference"));
            return this;
        }

        /**
         * Appends a tactic reference.
         *
         * @param value the reference
         * @return this builder
         */
        public Builder tacticReference(ArtifactReference value) {
            this.tacticReferences.add(Objects.requireNonNull(value, "tacticReference"));
            return this;
        }

        /**
         * Appends a toolguide reference.
         *
         * @param value the reference
         * @return this builder
         */
        public Builder toolguideReference(ArtifactReference value) {
            this.toolguideReferences.add(Objects.requireNonNull(value, "toolguideReference"));
            return this;
        }

        /**
         * Appends a styleguide reference.
         *
         * @param value the reference
         * @return this builder
         */
        public Builder styleguideReference(ArtifactReference value) {
            this.styleguideReferences.add(Objects.requireNonNull(value, "styleguideReference"));
            return this;
        }

        /**
         * Appends a self-review step.
         *
         * @param value the step
         * @return this builder
         */
        public Builder selfReviewStep(SelfReviewStep value) {
            this.selfReviewSteps.add(Objects.requireNonNull(value, "selfReviewStep"));
            return this;
        }

        /**
         * Sets the declared exclusion.
         *
         * @param value the exclusion, or empty when not declared
         * @return this builder
         */
        public Builder excluding(Optional<Exclusion> value) {
            this.excluding = Objects.requireNonNull(value, "excluding");
            return this;
        }

        /**
         * Builds the profile.
         *
         * @return the validated profile
         * @throws NullPointerException when identity, pack, name, purpose or specialization was never set
         * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
         */
        public AgentProfile build() {
            return new AgentProfile(this);
        }
    }
}
