package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.packs.domain.ConfigValue;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.infra.storage.PackFileSet.LoadedKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The YAML {@link SerializationStrategy}: it writes through {@link YamlDumpEmitter} and reads through
 * {@link ParsedYaml} and the bundled {@link PackSchemas}. Scalars are read as their text, an explicit null reads like
 * an absent key, and list order and duplicates are kept both ways.
 */
public final class YamlSerializationStrategy implements SerializationStrategy {

    /** Constructs the YAML strategy; the storage configuration publishes it as the {@link SerializationStrategy} bean. */
    public YamlSerializationStrategy() {}

    @Override
    public String writeTactic(TacticDso tactic) {
        YamlDumpEmitter emitter = new YamlDumpEmitter()
                .scalar("id", tactic.id())
                .scalar("schema_version", tactic.schemaVersion().orElseThrow())
                .scalar("name", tactic.name())
                .scalar("purpose", tactic.purpose());
        List<Map<String, Object>> steps = new ArrayList<>();
        for (TacticDso.Step step : tactic.steps()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("title", step.title());
            YamlDumpEmitter.scalarInto(out, "description", step.description());
            YamlDumpEmitter.stringsInto(out, "examples", step.examples());
            YamlDumpEmitter.mappingsInto(out, "references", referencesOut(step.references()));
            steps.add(out);
        }
        return emitter.mappings("steps", steps)
                .strings("failure_modes", tactic.failureModes())
                .strings("applies_to_languages", tactic.appliesToLanguages())
                .mappings("references", referencesOut(tactic.references()))
                .scalar("notes", tactic.notes())
                .dump();
    }

    @Override
    public FileRead<TacticDso> readTactic(String text, String file) {
        ParsedYaml parsed = ParsedYaml.parse(text, file);
        if (parsed.failure() != null) {
            return new FileRead.Unreadable<>(file, parsed.failure(), List.of());
        }
        Object document = parsed.document();
        List<SchemaViolation> violations = PackSchemas.bundled().validate(LoadedKind.TACTIC.token(), file, document);
        if (!violations.isEmpty()) {
            return new FileRead.Unreadable<>(file, null, violations);
        }
        if (!(document instanceof Map<?, ?> root)) {
            throw new IllegalStateException("a tactic passed the schema but is no mapping");
        }
        List<TacticDso.Step> steps = new ArrayList<>();
        for (Object step : list(root, "steps")) {
            if (step instanceof Map<?, ?> s) {
                steps.add(new TacticDso.Step(
                        required(s, "title"), optional(s, "description"), strings(s, "examples"), references(s)));
            }
        }
        return new FileRead.Read<>(
                file,
                new TacticDso(
                        required(root, "id"),
                        required(root, "schema_version"),
                        required(root, "name"),
                        steps,
                        optional(root, "purpose"),
                        optional(root, "notes"),
                        strings(root, "failure_modes"),
                        strings(root, "applies_to_languages"),
                        references(root)));
    }

    @Override
    public String writeDirective(DirectiveDso directive) {
        List<Map<String, Object>> references = new ArrayList<>();
        for (DirectiveDso.Reference reference : directive.references()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("type", reference.type());
            out.put("id", reference.id());
            YamlDumpEmitter.scalarInto(out, "when", reference.when());
            YamlDumpEmitter.scalarInto(out, "reason", reference.reason());
            references.add(out);
        }
        return new YamlDumpEmitter()
                .scalar("id", directive.id())
                .scalar("schema_version", directive.schemaVersion().orElseThrow())
                .scalar("title", directive.title())
                .scalar("intent", directive.intent())
                .scalar("enforcement", directive.enforcement())
                .scalar("scope", directive.scope())
                .strings("procedures", directive.procedures())
                .strings("integrity_rules", directive.integrityRules())
                .strings("validation_criteria", directive.validationCriteria())
                .strings("explicit_allowances", directive.explicitAllowances())
                .mappings("references", references)
                .dump();
    }

    @Override
    public FileRead<DirectiveDso> readDirective(String text, String file) {
        return readValidated(LoadedKind.DIRECTIVE.token(), text, file, root -> {
            List<DirectiveDso.Reference> references = new ArrayList<>();
            for (Object entry : list(root, "references")) {
                if (entry instanceof Map<?, ?> r) {
                    references.add(new DirectiveDso.Reference(
                            required(r, "type"), required(r, "id"), optional(r, "when"), optional(r, "reason")));
                }
            }
            return new DirectiveDso(
                    required(root, "id"),
                    required(root, "schema_version"),
                    required(root, "title"),
                    required(root, "intent"),
                    required(root, "enforcement"),
                    optional(root, "scope"),
                    strings(root, "procedures"),
                    strings(root, "integrity_rules"),
                    strings(root, "validation_criteria"),
                    strings(root, "explicit_allowances"),
                    references);
        });
    }

    @Override
    public String writeStyleguide(StyleguideDso styleguide) {
        List<Map<String, Object>> patterns = new ArrayList<>();
        for (StyleguideDso.Pattern pattern : styleguide.patterns()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("name", pattern.name());
            out.put("description", pattern.description());
            YamlDumpEmitter.scalarInto(out, "bad_example", pattern.badExample());
            YamlDumpEmitter.scalarInto(out, "good_example", pattern.goodExample());
            patterns.add(out);
        }
        List<Map<String, Object>> antiPatterns = new ArrayList<>();
        for (StyleguideDso.AntiPattern antiPattern : styleguide.antiPatterns()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("name", antiPattern.name());
            out.put("description", antiPattern.description());
            out.put("bad_example", antiPattern.badExample());
            out.put("good_example", antiPattern.goodExample());
            antiPatterns.add(out);
        }
        YamlDumpEmitter emitter = new YamlDumpEmitter()
                .scalar("id", styleguide.id())
                .scalar("schema_version", styleguide.schemaVersion().orElseThrow())
                .scalar("title", styleguide.title())
                .scalar("scope", styleguide.scope())
                .strings("principles", styleguide.principles())
                .mappings("patterns", patterns)
                .mappings("anti_patterns", antiPatterns);
        styleguide.tooling().ifPresent(tooling -> emitter.present("tooling", new LinkedHashMap<>(tooling)));
        emitter.scalar("quality_test", styleguide.qualityTest());
        styleguide
                .structuralLintConfig()
                .ifPresent(config -> emitter.present("structural_lint_config", config.toParsed()));
        return emitter.strings("applies_to_languages", styleguide.appliesToLanguages())
                .strings("references", styleguide.references())
                .dump();
    }

    @Override
    public FileRead<StyleguideDso> readStyleguide(String text, String file) {
        return readValidated(LoadedKind.STYLEGUIDE.token(), text, file, root -> {
            List<StyleguideDso.Pattern> patterns = new ArrayList<>();
            for (Object entry : list(root, "patterns")) {
                if (entry instanceof Map<?, ?> p) {
                    patterns.add(new StyleguideDso.Pattern(
                            required(p, "name"),
                            required(p, "description"),
                            optional(p, "bad_example"),
                            optional(p, "good_example")));
                }
            }
            List<StyleguideDso.AntiPattern> antiPatterns = new ArrayList<>();
            for (Object entry : list(root, "anti_patterns")) {
                if (entry instanceof Map<?, ?> a) {
                    antiPatterns.add(new StyleguideDso.AntiPattern(
                            required(a, "name"),
                            required(a, "description"),
                            required(a, "bad_example"),
                            required(a, "good_example")));
                }
            }
            Optional<Map<String, String>> tooling = Optional.empty();
            if (root.get("tooling") instanceof Map<?, ?> t) {
                Map<String, String> ordered = new LinkedHashMap<>();
                t.forEach((key, value) -> ordered.put(String.valueOf(key), String.valueOf(value)));
                tooling = Optional.of(ordered);
            }
            Optional<ConfigValue> lint = root.get("structural_lint_config") instanceof Map<?, ?> m
                    ? Optional.of(ConfigValue.of(m))
                    : Optional.empty();
            return new StyleguideDso(
                    required(root, "id"),
                    required(root, "schema_version"),
                    required(root, "title"),
                    required(root, "scope"),
                    strings(root, "principles"),
                    patterns,
                    antiPatterns,
                    tooling,
                    optional(root, "quality_test"),
                    lint,
                    strings(root, "applies_to_languages"),
                    strings(root, "references"));
        });
    }

    @Override
    public String writeParadigm(ParadigmDso paradigm) {
        List<Map<String, Object>> references = new ArrayList<>();
        for (ParadigmDso.Reference reference : paradigm.references()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("type", reference.type());
            out.put("id", reference.id());
            references.add(out);
        }
        return new YamlDumpEmitter()
                .scalar("id", paradigm.id())
                .scalar("schema_version", paradigm.schemaVersion().orElseThrow())
                .scalar("name", paradigm.name())
                .scalar("summary", paradigm.summary())
                .strings("directive_refs", paradigm.directiveRefs())
                .mappings("references", references)
                .dump();
    }

    @Override
    public FileRead<ParadigmDso> readParadigm(String text, String file) {
        return readValidated(LoadedKind.PARADIGM.token(), text, file, root -> {
            List<ParadigmDso.Reference> references = new ArrayList<>();
            for (Object entry : list(root, "references")) {
                if (entry instanceof Map<?, ?> r) {
                    references.add(new ParadigmDso.Reference(required(r, "type"), required(r, "id")));
                }
            }
            return new ParadigmDso(
                    required(root, "id"),
                    required(root, "schema_version"),
                    required(root, "name"),
                    required(root, "summary"),
                    strings(root, "directive_refs"),
                    references);
        });
    }

    @Override
    public String writeToolguide(ToolguideDso toolguide) {
        return new YamlDumpEmitter()
                .scalar("id", toolguide.id())
                .scalar("schema_version", toolguide.schemaVersion().orElseThrow())
                .scalar("tool", toolguide.tool())
                .scalar("title", toolguide.title())
                .scalar("guide_path", toolguide.guidePath())
                .scalar("summary", toolguide.summary())
                .strings("commands", toolguide.commands())
                .strings("applies_to_languages", toolguide.appliesToLanguages())
                .scalar("last_updated", toolguide.lastUpdated())
                .strings("references", toolguide.references())
                .dump();
    }

    @Override
    public FileRead<ToolguideDso> readToolguide(String text, String file) {
        return readValidated(
                LoadedKind.TOOLGUIDE.token(),
                text,
                file,
                root -> new ToolguideDso(
                        required(root, "id"),
                        required(root, "schema_version"),
                        required(root, "tool"),
                        required(root, "title"),
                        required(root, "guide_path"),
                        required(root, "summary"),
                        strings(root, "commands"),
                        strings(root, "applies_to_languages"),
                        optional(root, "last_updated"),
                        strings(root, "references")));
    }

    /** Parses, validates against the bundled schema of {@code kind}, then maps the root; never throws on content. */
    private static <T> FileRead<T> readValidated(String kind, String text, String file, Function<Map<?, ?>, T> mapper) {
        ParsedYaml parsed = ParsedYaml.parse(text, file);
        if (parsed.failure() != null) {
            return new FileRead.Unreadable<>(file, parsed.failure(), List.of());
        }
        Object document = parsed.document();
        List<SchemaViolation> violations = PackSchemas.bundled().validate(kind, file, document);
        if (!violations.isEmpty()) {
            return new FileRead.Unreadable<>(file, null, violations);
        }
        if (!(document instanceof Map<?, ?> root)) {
            throw new IllegalStateException("a " + kind + " passed the schema but is no mapping");
        }
        return new FileRead.Read<>(file, mapper.apply(root));
    }

    @Override
    public String writeDescriptor(PackDescriptorDso descriptor) {
        return new YamlDumpEmitter()
                .scalar("pack_id", descriptor.packId())
                .scalar("pack_version", descriptor.packVersion())
                .scalar("name", descriptor.name())
                .scalar("parent_pack", descriptor.parentPack())
                .scalar("pack_level", descriptor.packLevel())
                .scalar("requires_core", descriptor.requiresCore())
                .scalar("active_charter", descriptor.activeCharter())
                .scalar("pack_drg", descriptor.packDrg())
                .dump();
    }

    @Override
    public FileRead<PackDescriptorDso> readDescriptor(String text) {
        return switch (PackDescriptorReader.read(text)) {
            case FileRead.Read<PackDescriptor> read -> new FileRead.Read<>(read.file(), toDso(read.value()));
            case FileRead.Unreadable<PackDescriptor> bad ->
                new FileRead.Unreadable<>(bad.file(), bad.parse(), bad.violations());
            case FileRead.Absent<PackDescriptor> absent -> new FileRead.Absent<>(absent.file());
        };
    }

    private static PackDescriptorDso toDso(PackDescriptor d) {
        return new PackDescriptorDso(
                d.packId(),
                d.packVersion(),
                d.parentPack(),
                d.name(),
                d.declaredLevel().map(level -> level.token()),
                d.requiresCore(),
                d.paths().activeCharter(),
                d.paths().packDrg(),
                d.undeclaredKeys());
    }

    private static List<Map<String, Object>> referencesOut(List<TacticDso.Reference> references) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TacticDso.Reference reference : references) {
            Map<String, Object> entry = YamlDumpEmitter.mapping();
            entry.put("name", reference.name());
            entry.put("type", reference.type());
            entry.put("id", reference.id());
            entry.put("when", reference.when());
            out.add(entry);
        }
        return out;
    }

    private static List<TacticDso.Reference> references(Map<?, ?> owner) {
        List<TacticDso.Reference> out = new ArrayList<>();
        for (Object entry : list(owner, "references")) {
            if (entry instanceof Map<?, ?> r) {
                out.add(new TacticDso.Reference(
                        required(r, "name"), required(r, "type"), required(r, "id"), required(r, "when")));
            }
        }
        return out;
    }

    private static List<String> strings(Map<?, ?> owner, String key) {
        List<String> out = new ArrayList<>();
        for (Object value : list(owner, key)) {
            text(value).ifPresent(out::add);
        }
        return out;
    }

    private static List<?> list(Map<?, ?> owner, String key) {
        return owner.get(key) instanceof List<?> l ? l : List.of();
    }

    private static String required(Map<?, ?> owner, String key) {
        return optional(owner, key)
                .orElseThrow(() -> new IllegalStateException("'" + key + "' passed the schema but is absent or null"));
    }

    private static Optional<String> optional(Map<?, ?> owner, String key) {
        return text(owner.get(key));
    }

    /** A scalar as its text; null and anything that is no scalar are empty. */
    private static Optional<String> text(@Nullable Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(value));
    }
}
