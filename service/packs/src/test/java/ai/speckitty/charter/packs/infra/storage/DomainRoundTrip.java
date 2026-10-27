package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.infra.storage.GraphDocumentReader.Shape;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Today's {@link WholePackRoundTrip}: read → domain → write → read back, through the public storage service only.
 *
 * <p>A {@link DomainRepositoryService} over the source directory loads the {@link Pack} aggregate (descriptor and the
 * modelled artefact kinds, with every read violation); a second one over the target directory saves the
 * descriptor and each artefact; loading the target is the read-back. Graph shards go through
 * {@link GraphDocumentReader} → {@link PackPolicy#graph} → {@link GraphShardWriter}, keeping the source's
 * {@code generated_by}. Nothing here touches {@code Pack} or {@code FilesystemPackStore} directly (#189 owns them).
 *
 * <p>A new artefact is written as {@code <id>.<file token>.yaml} at the pack root, wherever its source lived, so source
 * and written files are paired by kind and id (under the kind binding's id key: {@code profile-id} for an agent
 * profile). An artefact gate kind is the name of its kind's schema.
 */
final class DomainRoundTrip implements WholePackRoundTrip {

    static final String DESCRIPTOR = "pack-descriptor";
    static final String GRAPH_SHARD = "graph-shard";
    /** The read-back key that stands for every written file when the written pack does not load at all. */
    private static final String READ_BACK_FAILED = "";

    static final Set<String> ARTEFACT_KINDS =
            Set.of("directive", "tactic", "styleguide", "toolguide", "paradigm", "agent-profile");
    private static final Set<String> MODELLED = Stream.concat(
                    ARTEFACT_KINDS.stream(), Stream.of(DESCRIPTOR, GRAPH_SHARD))
            .collect(Collectors.toUnmodifiableSet());

    private final SerializationStrategy strategy;

    /**
     * An adapter over a format.
     *
     * @param strategy the format to read and write with; the gate uses {@link YamlSerializationStrategy}
     */
    DomainRoundTrip(SerializationStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public Set<String> modelledKinds() {
        return MODELLED;
    }

    @Override
    public Map<String, Outcome> roundTrip(Path source, Path target, Map<String, String> kindOf) {
        String name = String.valueOf(source.getFileName());
        PackIdentifier id = new PackIdentifier(name);
        createDirectories(target);
        DomainRepositoryService from = service(name, source);
        DomainRepositoryService to = service(name, target);

        SortedMap<String, Outcome> outcomes = new TreeMap<>();
        Loaded loaded = load(from, id);
        @Nullable Pack pack = loaded.pack();
        @Nullable String descriptorFailure = writeAll(from, to, id, pack);
        Map<String, List<String>> refusals = pack == null ? Map.of() : byFile(pack.violations(), "");
        @Nullable String assemblyFailure = loaded.failure();
        Loaded reloaded = descriptorFailure == null ? load(to, id) : new Loaded(null, "nothing was written");
        Map<String, List<String>> readBack = reloaded.pack() == null
                ? Map.of(READ_BACK_FAILED, List.of("read-back: the written pack does not load: " + reloaded.failure()))
                : byFile(reloaded.pack().violations(), "read-back: ");
        Map<String, String> written = writtenByKindAndId(target);
        Map<String, @Nullable String> sourceIds = sourceIds(source, kindOf);
        kindOf.forEach((path, kind) -> {
            if (DESCRIPTOR.equals(kind)) {
                outcomes.put(
                        path,
                        descriptorFailure != null
                                ? new Refused(descriptorFailure)
                                : readBackOr(PackFileSet.PACK_YAML, readBack));
            } else if (ARTEFACT_KINDS.contains(kind)) {
                outcomes.put(
                        path,
                        artefact(source, path, kind, refusals, readBack, written, kindOf, sourceIds, assemblyFailure));
            } else if (GRAPH_SHARD.equals(kind)) {
                outcomes.put(path, shard(name, source, target, path));
            }
        });
        return outcomes;
    }

    /**
     * Writes the descriptor and every artefact of {@code pack} through {@code to}; when the pack did not load, only the
     * descriptor is copied.
     *
     * @return why the descriptor could not be read or written, or null when it was written
     */
    private static @Nullable String writeAll(
            DomainRepositoryService from, DomainRepositoryService to, PackIdentifier id, @Nullable Pack pack) {
        try {
            if (pack == null) {
                to.save(id, from.get(id));
                return null;
            }
            to.save(id, pack.descriptor());
            pack.tactics().forEach(to::save);
            pack.directives().forEach(to::save);
            pack.styleguides().forEach(to::save);
            pack.toolguides().forEach(to::save);
            pack.paradigms().forEach(to::save);
            pack.artifacts(ArtifactKind.AGENT_PROFILE, AgentProfile.class).forEach(to::save);
            return null;
        } catch (CharterRefusal | ConstraintBreachException | IllegalArgumentException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    private DomainRepositoryService service(String name, Path directory) {
        return new DomainRepositoryService(
                () -> List.of(new PackLocation(name, PackLevel.BUILT_IN, directory)), strategy);
    }

    // ---- artefacts -----------------------------------------------------------------------------

    private static Outcome artefact(
            Path source,
            String path,
            String kind,
            Map<String, List<String>> refusals,
            Map<String, List<String>> readBack,
            Map<String, String> written,
            Map<String, String> kindOf,
            Map<String, @Nullable String> sourceIds,
            @Nullable String assemblyFailure) {
        List<String> refused = refusals.getOrDefault(path, List.of());
        if (!refused.isEmpty()) {
            return new Refused(String.join("; ", refused) + unknownFields(kind, read(source.resolve(path))));
        }
        @Nullable String elementId = sourceIds.get(path);
        if (elementId == null) {
            return new Refused("the source declares no id");
        }
        long sameId = kindOf.entrySet().stream()
                .filter(entry ->
                        entry.getValue().equals(kind) && !entry.getKey().equals(path))
                .filter(entry -> elementId.equals(sourceIds.get(entry.getKey())))
                .count();
        if (sameId > 0) {
            return new Refused("another " + kind + " file declares the same id '" + elementId + "'");
        }
        if (assemblyFailure != null) {
            return new Refused("the pack does not assemble: " + assemblyFailure);
        }
        @Nullable String writtenPath = written.get(kind + "/" + elementId);
        if (writtenPath == null) {
            List<String> domain = refusals.getOrDefault(elementId, List.of());
            return new Refused(
                    domain.isEmpty() ? "not written: no domain element was built" : String.join("; ", domain));
        }
        return readBackOr(writtenPath, readBack);
    }

    /** The loaded aggregate, or why it refused to assemble. */
    private record Loaded(@Nullable Pack pack, @Nullable String failure) {}

    private static Loaded load(DomainRepositoryService service, PackIdentifier id) {
        try {
            return new Loaded(service.load(id), null);
        } catch (CharterRefusal | ConstraintBreachException | IllegalArgumentException refused) {
            return new Loaded(null, String.valueOf(refused.getMessage()));
        }
    }

    /**
     * The top-level keys of a source file that its kind's schema does not declare, named so a refusal says which field
     * was unknown (the validator only reports that one was). Only top-level keys are named; a nested unknown field is
     * still refused by the reader, just not named here.
     */
    private static String unknownFields(String kind, String text) {
        if (!(Yaml.load(text) instanceof Map<?, ?> document)) {
            return "";
        }
        List<String> declared = PackSchemas.bundled().declaredProperties(kind);
        List<String> unknown = document.keySet().stream()
                .map(String::valueOf)
                .filter(key -> !declared.contains(key))
                .sorted()
                .toList();
        return unknown.isEmpty() ? "" : " (unknown field: " + String.join(", ", unknown) + ")";
    }

    /** The declared id of every source artefact file, read once. */
    private static Map<String, @Nullable String> sourceIds(Path source, Map<String, String> kindOf) {
        Map<String, @Nullable String> ids = new HashMap<>();
        kindOf.forEach((path, kind) -> {
            if (ARTEFACT_KINDS.contains(kind)) {
                ids.put(path, idOf(read(source.resolve(path)), bindingOf(kind).idKey()));
            }
        });
        return ids;
    }

    /** The written artefact files, keyed {@code <kind>/<id>}. */
    private static Map<String, String> writtenByKindAndId(Path target) {
        Map<String, String> written = new HashMap<>();
        try (Stream<Path> files = Files.list(target)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String fileName = String.valueOf(file.getFileName());
                for (String kind : ARTEFACT_KINDS) {
                    KindBinding<?, ?> binding = bindingOf(kind);
                    @Nullable String elementId = fileName.endsWith("." + binding.fileToken() + ".yaml")
                            ? idOf(read(file), binding.idKey())
                            : null;
                    if (elementId != null) {
                        written.put(kind + "/" + elementId, fileName);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return written;
    }

    private static Outcome readBackOr(String writtenPath, Map<String, List<String>> readBack) {
        List<String> problems = readBack.getOrDefault(writtenPath, readBack.getOrDefault(READ_BACK_FAILED, List.of()));
        return problems.isEmpty() ? new Written(writtenPath) : new Refused(String.join("; ", problems));
    }

    /** Violations grouped by the file they name (for a domain violation, the element id). */
    private static Map<String, List<String>> byFile(List<PackElementViolation> violations, String prefix) {
        Map<String, List<String>> byFile = new TreeMap<>();
        for (PackElementViolation violation : violations) {
            byFile.computeIfAbsent(violation.file(), file -> new ArrayList<>())
                    .add(prefix + violation.kind() + ": " + violation.reason());
        }
        return byFile;
    }

    // ---- graph shards --------------------------------------------------------------------------

    private static Outcome shard(String name, Path source, Path target, String path) {
        String text = read(source.resolve(path));
        if (!(GraphDocumentReader.read(text, Shape.SHARD, path) instanceof FileRead.Read<RawGraph> raw)) {
            return new Refused("the shard does not read");
        }
        PackPolicy.GraphBuild build = PackPolicy.graph(name, raw.value());
        if (!build.findings().isEmpty()) {
            return new Refused("the graph does not build: " + build.findings());
        }
        @Nullable String generatedBy = Yaml.load(text) instanceof Map<?, ?> document && document.get("generated_by") != null
                ? String.valueOf(document.get("generated_by"))
                : null;
        String writtenText = GraphShardWriter.write(build.graph(), generatedBy);
        write(target.resolve(path), writtenText);
        if (!(GraphDocumentReader.read(writtenText, Shape.SHARD, path) instanceof FileRead.Read<RawGraph>)) {
            return new Refused("read-back: the written shard does not read");
        }
        return new Written(path);
    }

    // ---- files ---------------------------------------------------------------------------------

    /** The binding of an artefact gate kind: the gate kind is the schema name. */
    private static KindBinding<?, ?> bindingOf(String kind) {
        return KindBindings.bySchema(kind)
                .orElseThrow(() -> new IllegalStateException("no stored kind is validated by the schema " + kind));
    }

    private static @Nullable String idOf(String text, String idKey) {
        try {
            return Yaml.load(text) instanceof Map<?, ?> map && map.get(idKey) != null
                    ? String.valueOf(map.get(idKey))
                    : null;
        } catch (RuntimeException unparsable) {
            return null;
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String text) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
