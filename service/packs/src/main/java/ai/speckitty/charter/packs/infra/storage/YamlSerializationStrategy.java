package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The YAML {@link SerializationStrategy}: it writes through {@link YamlDumpEmitter} and reads through
 * {@link ParsedYaml} and the bundled {@link PackSchemas}, with one {@link YamlFormat} per bound kind
 * ({@link YamlFormats}). Scalars are read as their text, an explicit null reads like an absent key, and list order and
 * duplicates are kept both ways.
 */
public final class YamlSerializationStrategy implements SerializationStrategy {

    /** Constructs the YAML strategy; the storage configuration publishes it as the {@link SerializationStrategy} bean. */
    public YamlSerializationStrategy() {}

    @Override
    public <D> String write(KindBinding<D, ?> kind, D dso) {
        return YamlFormats.of(kind).write(dso);
    }

    /** Parses, validates against the bundled schema of the kind, then maps the root; never throws on content. */
    @Override
    public <D> FileRead<D> read(KindBinding<D, ?> kind, String text, String file) {
        return readValidated(kind.schemaName(), text, file, YamlFormats.of(kind)::read);
    }

    @Override
    public String writePreset(PresetDso preset) {
        return PresetYaml.write(preset);
    }

    @Override
    public FileRead<PresetDso> readPreset(String text, String file) {
        return readValidated(PresetYaml.SCHEMA, text, file, PresetYaml::read);
    }

    @Override
    public String writeManifest(PackManifestDso manifest) {
        return PackManifestYaml.write(manifest);
    }

    @Override
    public FileRead<PackManifestDso> readManifest(String text, String file) {
        return readValidated(PackManifestYaml.SCHEMA, text, file, PackManifestYaml::read);
    }

    /**
     * The one schema-validated read: parses, validates against the bundled {@code schema}, then maps the root with
     * {@code reader}; never throws on content.
     */
    private static <D> FileRead<D> readValidated(
            String schema, String text, String file, Function<Map<?, ?>, D> reader) {
        ParsedYaml parsed = ParsedYaml.parse(text, file);
        if (parsed.failure() != null) {
            return new FileRead.Unreadable<>(file, parsed.failure(), List.of());
        }
        Object document = parsed.document();
        List<SchemaViolation> violations = PackSchemas.bundled().validate(schema, file, document);
        if (!violations.isEmpty()) {
            return new FileRead.Unreadable<>(file, null, violations);
        }
        if (!(document instanceof Map<?, ?> root)) {
            throw new IllegalStateException("a " + schema + " passed the schema but is no mapping");
        }
        return new FileRead.Read<>(file, reader.apply(root));
    }

    @Override
    public String writeGraph(GraphDocument document) {
        return GraphDocumentYaml.write(document);
    }

    @Override
    public FileRead<GraphDocument> readGraph(String text, Shape shape, String file) {
        return GraphDocumentYaml.read(text, shape, file);
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
            case FileRead.Read<PackDescriptor> read ->
                new FileRead.Read<>(read.file(), PackDescriptorDso.of(read.value()));
            case FileRead.Unreadable<PackDescriptor> bad ->
                new FileRead.Unreadable<>(bad.file(), bad.parse(), bad.violations());
            case FileRead.Absent<PackDescriptor> absent -> new FileRead.Absent<>(absent.file());
        };
    }
}
