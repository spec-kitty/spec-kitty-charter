package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.list;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The YAML form of the agent profile. Its keys are hyphenated as the schema spells them ({@code profile-id}), except
 * {@code applies_to_languages}. Integers and the {@code sentinel} flag are read and written as their YAML types, never
 * as text, and an undeclared field is not written, so no schema default is invented.
 */
enum AgentProfileYaml implements YamlFormat<AgentProfileDso> {
    /** The format. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.AGENT_PROFILE;
    }

    @Override
    public Class<AgentProfileDso> dsoType() {
        return AgentProfileDso.class;
    }

    @Override
    public AgentProfileDso read(Map<?, ?> root) {
        return new AgentProfileDso(
                required(root, "profile-id"),
                required(root, "name"),
                optional(root, "description"),
                optional(root, "schema-version"),
                strings(root, "roles"),
                optional(root, "avatar-image"),
                strings(root, "capabilities"),
                integer(root, "routing-priority"),
                integer(root, "max-concurrent-tasks"),
                optional(root, "model"),
                optional(root, "effort"),
                required(root, "purpose"),
                specialization(mapping(root, "specialization")),
                collaboration(mapping(root, "collaboration")),
                modeDefaults(root),
                optional(root, "initialization-declaration"),
                root.get("sentinel") instanceof Boolean flag ? Optional.of(flag) : Optional.empty(),
                specializationContext(mapping(root, "specialization-context")),
                strings(root, "applies_to_languages"),
                directiveRefs(root),
                artifactRefs(root, "tactic-references"),
                artifactRefs(root, "toolguide-references"),
                artifactRefs(root, "styleguide-references"),
                selfReviewSteps(mapping(root, "self-review-protocol")),
                excluding(root.get("excluding")));
    }

    @Override
    public String write(AgentProfileDso profile) {
        YamlDumpEmitter out = new YamlDumpEmitter()
                .scalar("profile-id", profile.profileId())
                .scalar("name", profile.name())
                .scalar("description", profile.description())
                .scalar("schema-version", profile.schemaVersion())
                .strings("roles", profile.roles())
                .scalar("avatar-image", profile.avatarImage())
                .strings("capabilities", profile.capabilities());
        profile.routingPriority().ifPresent(value -> out.present("routing-priority", value));
        profile.maxConcurrentTasks().ifPresent(value -> out.present("max-concurrent-tasks", value));
        out.scalar("model", profile.model())
                .scalar("effort", profile.effort())
                .scalar("purpose", profile.purpose())
                .present("specialization", specialization(profile.specialization()));
        Map<String, Object> collaboration = collaboration(profile.collaboration());
        if (!collaboration.isEmpty()) {
            out.present("collaboration", collaboration);
        }
        out.mappings("mode-defaults", modeDefaults(profile.modeDefaults()))
                .scalar("initialization-declaration", profile.initializationDeclaration());
        profile.sentinel().ifPresent(value -> out.present("sentinel", value));
        Map<String, Object> context = specializationContext(profile.specializationContext());
        if (!context.isEmpty()) {
            out.present("specialization-context", context);
        }
        out.strings("applies_to_languages", profile.appliesToLanguages())
                .mappings("directive-references", directiveRefs(profile.directiveReferences()))
                .mappings("tactic-references", artifactRefs(profile.tacticReferences()))
                .mappings("toolguide-references", artifactRefs(profile.toolguideReferences()))
                .mappings("styleguide-references", artifactRefs(profile.styleguideReferences()));
        List<Map<String, Object>> steps = selfReviewSteps(profile.selfReviewSteps());
        if (!steps.isEmpty()) {
            Map<String, Object> protocol = YamlDumpEmitter.mapping();
            protocol.put("steps", steps);
            out.present("self-review-protocol", protocol);
        }
        profile.excluding().ifPresent(excluding -> out.present("excluding", excluding(excluding)));
        return out.dump();
    }

    // ---- reading -------------------------------------------------------------------------------------------------

    /**
     * An integer field; the schema guarantees an integral number when it is present. A value beyond {@code long} reads
     * as {@link Long#MAX_VALUE}, never wrapped, so the binding refuses it rather than store another number.
     */
    private static Optional<Long> integer(Map<?, ?> owner, String key) {
        return switch (owner.get(key)) {
            case BigInteger big -> Optional.of(big.bitLength() < Long.SIZE ? big.longValue() : Long.MAX_VALUE);
            case Number number -> Optional.of(number.longValue());
            case null, default -> Optional.empty();
        };
    }

    /** A declared object field; absent and null read as the empty mapping. */
    private static Map<?, ?> mapping(Map<?, ?> owner, String key) {
        return owner.get(key) instanceof Map<?, ?> map ? map : Map.of();
    }

    private static AgentProfileDso.Specialization specialization(Map<?, ?> specialization) {
        return new AgentProfileDso.Specialization(
                required(specialization, "primary-focus"),
                optional(specialization, "secondary-awareness"),
                optional(specialization, "avoidance-boundary"),
                optional(specialization, "success-definition"));
    }

    private static AgentProfileDso.Collaboration collaboration(Map<?, ?> collaboration) {
        return new AgentProfileDso.Collaboration(
                strings(collaboration, "handoff-to"),
                strings(collaboration, "handoff-from"),
                strings(collaboration, "works-with"),
                strings(collaboration, "output-artifacts"),
                strings(collaboration, "operating-procedures"),
                strings(collaboration, "canonical-verbs"));
    }

    private static List<AgentProfileDso.ModeDefault> modeDefaults(Map<?, ?> root) {
        List<AgentProfileDso.ModeDefault> modes = new ArrayList<>();
        for (Object entry : list(root, "mode-defaults")) {
            if (entry instanceof Map<?, ?> m) {
                modes.add(new AgentProfileDso.ModeDefault(
                        required(m, "mode"), required(m, "description"), required(m, "use-case")));
            }
        }
        return modes;
    }

    private static AgentProfileDso.SpecializationContext specializationContext(Map<?, ?> context) {
        return new AgentProfileDso.SpecializationContext(
                strings(context, "languages"),
                strings(context, "frameworks"),
                strings(context, "file-patterns"),
                strings(context, "domain-keywords"),
                strings(context, "writing-style"),
                strings(context, "complexity-preference"));
    }

    private static List<AgentProfileDso.DirectiveRef> directiveRefs(Map<?, ?> root) {
        List<AgentProfileDso.DirectiveRef> refs = new ArrayList<>();
        for (Object entry : list(root, "directive-references")) {
            if (entry instanceof Map<?, ?> r) {
                refs.add(new AgentProfileDso.DirectiveRef(
                        required(r, "code"), required(r, "name"), required(r, "rationale")));
            }
        }
        return refs;
    }

    private static List<AgentProfileDso.ArtifactRef> artifactRefs(Map<?, ?> root, String key) {
        List<AgentProfileDso.ArtifactRef> refs = new ArrayList<>();
        for (Object entry : list(root, key)) {
            if (entry instanceof Map<?, ?> r) {
                refs.add(new AgentProfileDso.ArtifactRef(required(r, "id"), required(r, "rationale")));
            }
        }
        return refs;
    }

    private static List<AgentProfileDso.SelfReviewStep> selfReviewSteps(Map<?, ?> protocol) {
        List<AgentProfileDso.SelfReviewStep> steps = new ArrayList<>();
        for (Object entry : list(protocol, "steps")) {
            if (entry instanceof Map<?, ?> s) {
                steps.add(new AgentProfileDso.SelfReviewStep(
                        required(s, "name"), optional(s, "command"), required(s, "gate")));
            }
        }
        return steps;
    }

    /** The {@code excluding} in the form it was authored; null and absent are undeclared. */
    private static Optional<AgentProfileDso.Excluding> excluding(@Nullable Object value) {
        if (value instanceof List<?> fields) {
            List<String> names = new ArrayList<>();
            fields.forEach(field -> YamlFields.text(field).ifPresent(names::add));
            return Optional.of(new AgentProfileDso.Excluding.Fields(names));
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, List<String>> values = new LinkedHashMap<>();
            map.forEach((field, removed) -> {
                List<String> items = new ArrayList<>();
                if (removed instanceof List<?> list) {
                    list.forEach(item -> YamlFields.text(item).ifPresent(items::add));
                }
                values.put(String.valueOf(field), items);
            });
            return Optional.of(new AgentProfileDso.Excluding.Values(values));
        }
        return Optional.empty();
    }

    // ---- writing -------------------------------------------------------------------------------------------------

    private static Map<String, Object> specialization(AgentProfileDso.Specialization specialization) {
        Map<String, Object> out = YamlDumpEmitter.mapping();
        out.put("primary-focus", specialization.primaryFocus());
        YamlDumpEmitter.scalarInto(out, "secondary-awareness", specialization.secondaryAwareness());
        YamlDumpEmitter.scalarInto(out, "avoidance-boundary", specialization.avoidanceBoundary());
        YamlDumpEmitter.scalarInto(out, "success-definition", specialization.successDefinition());
        return out;
    }

    private static Map<String, Object> collaboration(AgentProfileDso.Collaboration collaboration) {
        Map<String, Object> out = YamlDumpEmitter.mapping();
        YamlDumpEmitter.stringsInto(out, "handoff-to", collaboration.handoffTo());
        YamlDumpEmitter.stringsInto(out, "handoff-from", collaboration.handoffFrom());
        YamlDumpEmitter.stringsInto(out, "works-with", collaboration.worksWith());
        YamlDumpEmitter.stringsInto(out, "output-artifacts", collaboration.outputArtifacts());
        YamlDumpEmitter.stringsInto(out, "operating-procedures", collaboration.operatingProcedures());
        YamlDumpEmitter.stringsInto(out, "canonical-verbs", collaboration.canonicalVerbs());
        return out;
    }

    private static List<Map<String, Object>> modeDefaults(List<AgentProfileDso.ModeDefault> modes) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentProfileDso.ModeDefault mode : modes) {
            Map<String, Object> entry = YamlDumpEmitter.mapping();
            entry.put("mode", mode.mode());
            entry.put("description", mode.description());
            entry.put("use-case", mode.useCase());
            out.add(entry);
        }
        return out;
    }

    private static Map<String, Object> specializationContext(AgentProfileDso.SpecializationContext context) {
        Map<String, Object> out = YamlDumpEmitter.mapping();
        YamlDumpEmitter.stringsInto(out, "languages", context.languages());
        YamlDumpEmitter.stringsInto(out, "frameworks", context.frameworks());
        YamlDumpEmitter.stringsInto(out, "file-patterns", context.filePatterns());
        YamlDumpEmitter.stringsInto(out, "domain-keywords", context.domainKeywords());
        YamlDumpEmitter.stringsInto(out, "writing-style", context.writingStyle());
        YamlDumpEmitter.stringsInto(out, "complexity-preference", context.complexityPreference());
        return out;
    }

    private static List<Map<String, Object>> directiveRefs(List<AgentProfileDso.DirectiveRef> refs) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentProfileDso.DirectiveRef ref : refs) {
            Map<String, Object> entry = YamlDumpEmitter.mapping();
            entry.put("code", ref.code());
            entry.put("name", ref.name());
            entry.put("rationale", ref.rationale());
            out.add(entry);
        }
        return out;
    }

    private static List<Map<String, Object>> artifactRefs(List<AgentProfileDso.ArtifactRef> refs) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentProfileDso.ArtifactRef ref : refs) {
            Map<String, Object> entry = YamlDumpEmitter.mapping();
            entry.put("id", ref.id());
            entry.put("rationale", ref.rationale());
            out.add(entry);
        }
        return out;
    }

    private static List<Map<String, Object>> selfReviewSteps(List<AgentProfileDso.SelfReviewStep> steps) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentProfileDso.SelfReviewStep step : steps) {
            Map<String, Object> entry = YamlDumpEmitter.mapping();
            entry.put("name", step.name());
            YamlDumpEmitter.scalarInto(entry, "command", step.command());
            entry.put("gate", step.gate());
            out.add(entry);
        }
        return out;
    }

    /** The {@code excluding} as authored: the list form, or the map form with every entry, an empty one included. */
    private static Object excluding(AgentProfileDso.Excluding excluding) {
        return switch (excluding) {
            case AgentProfileDso.Excluding.Fields fields -> new ArrayList<Object>(fields.fields());
            case AgentProfileDso.Excluding.Values values -> {
                Map<String, Object> out = YamlDumpEmitter.mapping();
                values.values().forEach((field, removed) -> out.put(field, new ArrayList<Object>(removed)));
                yield out;
            }
        };
    }
}
