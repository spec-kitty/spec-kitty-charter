package ai.speckitty.charter.profiles.domain.resolution;

import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.ArtifactReference;
import ai.speckitty.charter.profiles.domain.Collaboration;
import ai.speckitty.charter.profiles.domain.DirectiveReference;
import ai.speckitty.charter.profiles.domain.Exclusion;
import ai.speckitty.charter.profiles.domain.ModeDefault;
import ai.speckitty.charter.profiles.domain.SelfReviewStep;
import ai.speckitty.charter.profiles.domain.Specialization;
import ai.speckitty.charter.profiles.domain.SpecializationContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The one table of an agent profile's top-level fields, as the schema names them, that both folds read: whether the
 * field is an identity field (ruling D5: never merged, never inherited), its value as a plain shape for the loss check,
 * and how a builder takes it from a profile. A field of {@link AgentProfile} that this table does not list fails
 * {@code ProfileFieldTableTest}, so a new schema field cannot silently skip the folds.
 *
 * <p>A shape is a {@link Map} for an object (its declared sub-fields by schema name), a {@link List} for a list, and
 * the value itself for a scalar; an absent value, an empty list and an object with nothing declared are {@code null}, so
 * {@code null} is exactly "the profile does not declare the field".
 *
 * @param yamlName the field's name in the schema
 * @param javaField the name of the {@link AgentProfile} field that holds it
 * @param identity whether the field is the profile's own: never merged by an enhancer, never inherited (ruling D5)
 * @param shape the field's value as a shape; {@code null} when the profile does not declare it
 * @param copy sets the field on a builder from a profile
 */
record ProfileField(
        String yamlName,
        String javaField,
        boolean identity,
        Function<AgentProfile, @Nullable Object> shape,
        BiConsumer<AgentProfile.Builder, AgentProfile> copy) {

    /** {@code profile-id}. */
    static final ProfileField PROFILE_ID =
            new ProfileField("profile-id", "id", true, profile -> profile.id().value(), (to, from) -> to.id(from.id()));

    /** {@code schema-version}. */
    static final ProfileField SCHEMA_VERSION = new ProfileField(
            "schema-version",
            "schemaVersion",
            false,
            profile -> scalar(profile.schemaVersion()),
            (to, from) -> to.schemaVersion(from.schemaVersion()));

    /** {@code name}. */
    static final ProfileField NAME =
            new ProfileField("name", "name", true, AgentProfile::name, (to, from) -> to.name(from.name()));

    /** {@code description}. */
    static final ProfileField DESCRIPTION = new ProfileField(
            "description",
            "description",
            false,
            profile -> scalar(profile.description()),
            (to, from) -> to.description(from.description()));

    /** {@code role}. */
    static final ProfileField ROLE =
            new ProfileField("role", "role", true, AgentProfile::role, (to, from) -> to.role(from.role()));

    /** {@code alt-roles}. */
    static final ProfileField ALT_ROLES = new ProfileField(
            "alt-roles",
            "altRoles",
            true,
            profile -> list(profile.altRoles()),
            (to, from) -> from.altRoles().forEach(to::altRole));

    /** {@code avatar-image}. */
    static final ProfileField AVATAR_IMAGE = new ProfileField(
            "avatar-image",
            "avatarImage",
            false,
            profile -> scalar(profile.avatarImage()),
            (to, from) -> to.avatarImage(from.avatarImage()));

    /** {@code capabilities}. */
    static final ProfileField CAPABILITIES = new ProfileField(
            "capabilities",
            "capabilities",
            false,
            profile -> list(profile.capabilities()),
            (to, from) -> from.capabilities().forEach(to::capability));

    /** {@code routing-priority}. */
    static final ProfileField ROUTING_PRIORITY = new ProfileField(
            "routing-priority",
            "routingPriority",
            true,
            profile -> scalar(profile.routingPriority()),
            (to, from) -> to.routingPriority(from.routingPriority()));

    /** {@code max-concurrent-tasks}. */
    static final ProfileField MAX_CONCURRENT_TASKS = new ProfileField(
            "max-concurrent-tasks",
            "maxConcurrentTasks",
            false,
            profile -> scalar(profile.maxConcurrentTasks()),
            (to, from) -> to.maxConcurrentTasks(from.maxConcurrentTasks()));

    /** {@code model}. */
    static final ProfileField MODEL = new ProfileField(
            "model", "model", false, profile -> scalar(profile.model()), (to, from) -> to.model(from.model()));

    /** {@code effort}. */
    static final ProfileField EFFORT = new ProfileField(
            "effort", "effort", false, profile -> scalar(profile.effort()), (to, from) -> to.effort(from.effort()));

    /** {@code purpose}. */
    static final ProfileField PURPOSE = new ProfileField(
            "purpose", "purpose", true, AgentProfile::purpose, (to, from) -> to.purpose(from.purpose()));

    /** {@code specialization}: required, so every profile declares it. */
    static final ProfileField SPECIALIZATION = new ProfileField(
            "specialization",
            "specialization",
            false,
            profile -> specialization(profile.specialization()),
            (to, from) -> to.specialization(from.specialization()));

    /** {@code collaboration}. */
    static final ProfileField COLLABORATION = new ProfileField(
            "collaboration",
            "collaboration",
            false,
            profile -> collaboration(profile.collaboration()),
            (to, from) -> to.collaboration(from.collaboration()));

    /** {@code mode-defaults}. */
    static final ProfileField MODE_DEFAULTS = new ProfileField(
            "mode-defaults",
            "modeDefaults",
            false,
            profile -> list(profile.modeDefaults().stream()
                    .map(ProfileField::modeDefault)
                    .toList()),
            (to, from) -> from.modeDefaults().forEach(to::modeDefault));

    /** {@code initialization-declaration}. */
    static final ProfileField INITIALIZATION_DECLARATION = new ProfileField(
            "initialization-declaration",
            "initializationDeclaration",
            false,
            profile -> scalar(profile.initializationDeclaration()),
            (to, from) -> to.initializationDeclaration(from.initializationDeclaration()));

    /** {@code performer}. */
    static final ProfileField PERFORMER = new ProfileField(
            "performer",
            "performer",
            true,
            profile -> scalar(profile.performer()),
            (to, from) -> to.performer(from.performer()));

    /** {@code specialization-context}. */
    static final ProfileField SPECIALIZATION_CONTEXT = new ProfileField(
            "specialization-context",
            "specializationContext",
            false,
            profile -> specializationContext(profile.specializationContext()),
            (to, from) -> to.specializationContext(from.specializationContext()));

    /** {@code applies_to_languages}. */
    static final ProfileField APPLIES_TO_LANGUAGES = new ProfileField(
            "applies_to_languages",
            "appliesToLanguages",
            false,
            profile -> list(profile.appliesToLanguages()),
            (to, from) -> from.appliesToLanguages().forEach(to::appliesToLanguage));

    /** {@code directive-references}. */
    static final ProfileField DIRECTIVE_REFERENCES = new ProfileField(
            "directive-references",
            "directiveReferences",
            false,
            profile -> list(profile.directiveReferences().stream()
                    .map(ProfileField::directiveReference)
                    .toList()),
            (to, from) -> from.directiveReferences().forEach(to::directiveReference));

    /** {@code tactic-references}. */
    static final ProfileField TACTIC_REFERENCES = new ProfileField(
            "tactic-references",
            "tacticReferences",
            false,
            profile -> references(profile.tacticReferences()),
            (to, from) -> from.tacticReferences().forEach(to::tacticReference));

    /** {@code toolguide-references}. */
    static final ProfileField TOOLGUIDE_REFERENCES = new ProfileField(
            "toolguide-references",
            "toolguideReferences",
            false,
            profile -> references(profile.toolguideReferences()),
            (to, from) -> from.toolguideReferences().forEach(to::toolguideReference));

    /** {@code styleguide-references}. */
    static final ProfileField STYLEGUIDE_REFERENCES = new ProfileField(
            "styleguide-references",
            "styleguideReferences",
            false,
            profile -> references(profile.styleguideReferences()),
            (to, from) -> from.styleguideReferences().forEach(to::styleguideReference));

    /** {@code self-review-protocol}: its steps. */
    static final ProfileField SELF_REVIEW_PROTOCOL = new ProfileField(
            "self-review-protocol",
            "selfReviewSteps",
            false,
            profile -> list(profile.selfReviewSteps().stream()
                    .map(ProfileField::selfReviewStep)
                    .toList()),
            (to, from) -> from.selfReviewSteps().forEach(to::selfReviewStep));

    /** {@code excluding}. */
    static final ProfileField EXCLUDING = new ProfileField(
            "excluding",
            "excluding",
            false,
            profile -> profile.excluding().map(ProfileField::exclusion).orElse(null),
            (to, from) -> to.excluding(from.excluding()));

    /** Every row, in schema order. */
    private static final List<ProfileField> ROWS = List.of(
            PROFILE_ID,
            SCHEMA_VERSION,
            NAME,
            DESCRIPTION,
            ROLE,
            ALT_ROLES,
            AVATAR_IMAGE,
            CAPABILITIES,
            ROUTING_PRIORITY,
            MAX_CONCURRENT_TASKS,
            MODEL,
            EFFORT,
            PURPOSE,
            SPECIALIZATION,
            COLLABORATION,
            MODE_DEFAULTS,
            INITIALIZATION_DECLARATION,
            PERFORMER,
            SPECIALIZATION_CONTEXT,
            APPLIES_TO_LANGUAGES,
            DIRECTIVE_REFERENCES,
            TACTIC_REFERENCES,
            TOOLGUIDE_REFERENCES,
            STYLEGUIDE_REFERENCES,
            SELF_REVIEW_PROTOCOL,
            EXCLUDING);

    /**
     * Every row, in schema order.
     *
     * @return the rows
     */
    static List<ProfileField> values() {
        return ROWS;
    }

    /** The field's value as a shape; {@code null} when the profile does not declare it. */
    @Nullable Object shapeOf(AgentProfile profile) {
        return shape.apply(profile);
    }

    /** Whether the profile declares the field. */
    boolean declaredBy(AgentProfile profile) {
        return shapeOf(profile) != null;
    }

    /** Sets the field on the builder from the profile. */
    void copy(AgentProfile.Builder to, AgentProfile from) {
        copy.accept(to, from);
    }

    /**
     * Builds a profile whose identity, pack and identity fields are {@code identity}'s and whose every other field is
     * taken from the profile {@code sources} names for it ({@code identity} when it names none).
     */
    static AgentProfile assemble(AgentProfile identity, Map<ProfileField, AgentProfile> sources) {
        AgentProfile.Builder builder = AgentProfile.builder().packId(identity.packId());
        for (ProfileField field : values()) {
            field.copy(builder, field.identity ? identity : sources.getOrDefault(field, identity));
        }
        return builder.build();
    }

    /**
     * Whether {@code replacement} loses content of {@code base} (ruling D5): a list item of {@code base} that
     * {@code replacement} does not hold, in any order; a sub-field of an object missing or losing content, recursively;
     * a scalar changed. An absent {@code base} loses nothing.
     */
    static boolean loses(@Nullable Object base, @Nullable Object replacement) {
        if (base == null) {
            return false;
        }
        if (replacement == null) {
            return true;
        }
        if (base instanceof Map<?, ?> baseObject) {
            if (!(replacement instanceof Map<?, ?> object)) {
                return true;
            }
            return baseObject.entrySet().stream().anyMatch(sub -> loses(sub.getValue(), object.get(sub.getKey())));
        }
        if (base instanceof List<?> baseList) {
            if (!(replacement instanceof List<?> items)) {
                return true;
            }
            return baseList.stream().anyMatch(item -> !items.contains(item));
        }
        return !base.equals(replacement);
    }

    // ---- shapes -----------------------------------------------------------------------------------------------------

    private static @Nullable Object scalar(Optional<?> value) {
        return value.orElse(null);
    }

    private static @Nullable List<?> list(List<?> items) {
        return items.isEmpty() ? null : items;
    }

    private static @Nullable Map<String, Object> object(Map<String, @Nullable Object> fields) {
        Map<String, Object> declared = new LinkedHashMap<>();
        fields.forEach((name, value) -> {
            if (value != null) {
                declared.put(name, value);
            }
        });
        return declared.isEmpty() ? null : declared;
    }

    private static Map<String, @Nullable Object> fields(@Nullable Object... namesAndValues) {
        Map<String, @Nullable Object> fields = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            fields.put(String.valueOf(namesAndValues[i]), namesAndValues[i + 1]);
        }
        return fields;
    }

    private static @Nullable Object specialization(Specialization value) {
        return object(fields(
                "primary-focus", value.primaryFocus(),
                "secondary-awareness", scalar(value.secondaryAwareness()),
                "avoidance-boundary", scalar(value.avoidanceBoundary()),
                "success-definition", scalar(value.successDefinition())));
    }

    private static @Nullable Object collaboration(Collaboration value) {
        return object(fields(
                "handoff-to", list(value.handoffTo()),
                "handoff-from", list(value.handoffFrom()),
                "works-with", list(value.worksWith()),
                "output-artifacts", list(value.outputArtifacts()),
                "operating-procedures", list(value.operatingProcedures()),
                "canonical-verbs", list(value.canonicalVerbs())));
    }

    private static @Nullable Object specializationContext(SpecializationContext value) {
        return object(fields(
                "languages", list(value.languages()),
                "frameworks", list(value.frameworks()),
                "file-patterns", list(value.filePatterns()),
                "domain-keywords", list(value.domainKeywords()),
                "writing-style", list(value.writingStyle()),
                "complexity-preference", list(value.complexityPreference())));
    }

    private static @Nullable Object modeDefault(ModeDefault value) {
        return object(fields("mode", value.mode(), "description", value.description(), "use-case", value.useCase()));
    }

    private static @Nullable Object directiveReference(DirectiveReference value) {
        return object(fields("code", value.code(), "name", value.name(), "rationale", value.rationale()));
    }

    private static @Nullable List<?> references(List<ArtifactReference> references) {
        return list(references.stream()
                .map(reference -> object(fields("id", reference.id(), "rationale", reference.rationale())))
                .toList());
    }

    private static @Nullable Object selfReviewStep(SelfReviewStep value) {
        return object(fields("name", value.name(), "command", scalar(value.command()), "gate", value.gate()));
    }

    private static @Nullable Object exclusion(Exclusion value) {
        return switch (value) {
            case Exclusion.WholeFields whole -> list(whole.fields());
            case Exclusion.FieldValues values -> {
                Map<String, @Nullable Object> byField = new LinkedHashMap<>();
                values.values().forEach((field, removed) -> byField.put(field, list(removed)));
                yield object(byField);
            }
        };
    }
}
