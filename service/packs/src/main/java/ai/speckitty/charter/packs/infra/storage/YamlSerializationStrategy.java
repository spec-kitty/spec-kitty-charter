package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The YAML {@link SerializationStrategy}: it writes through {@link YamlDumpEmitter} and reads through
 * {@link ParsedYaml} and the bundled {@link PackSchemas}. Scalars are read as their text, an explicit null reads like
 * an absent key, and list order and duplicates are kept both ways.
 */
public final class YamlSerializationStrategy implements SerializationStrategy {

    private static final String TACTIC_KIND = "tactic";

    /** Constructs the YAML strategy; the storage configuration publishes it as the {@link SerializationStrategy} bean. */
    public YamlSerializationStrategy() {}

    @Override
    public String writeTactic(TacticDso tactic) {
        YamlDumpEmitter emitter = new YamlDumpEmitter()
                .scalar("id", tactic.id())
                .scalar("schema_version", tactic.schemaVersion())
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
        List<SchemaViolation> violations = PackSchemas.bundled().validate(TACTIC_KIND, file, document);
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
