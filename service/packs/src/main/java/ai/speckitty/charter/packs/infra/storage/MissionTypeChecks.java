package ai.speckitty.charter.packs.infra.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The checks the store's load makes of a schema-valid mission-type file beyond its schema, made in validate mode too so
 * the two refuse the same files (#194, ADR-0044): a step id stands once across {@code steps} and
 * {@code on_demand_steps}; a step's {@code prompt_path} and {@code guidelines_path} are the paths derived from its id and
 * name files that are there; every {@code template_path} names a template of the pack
 * ({@link FilesystemPackStore#missingNamedFile}). Read over the schema-valid document as loaded YAML, before any binding.
 */
final class MissionTypeChecks {

    private static final String SCHEMA = "mission-type";

    private MissionTypeChecks() {}

    /**
     * The first refusal the load would make of a schema-valid mission-type file, worded after its origin.
     *
     * @param directory the pack directory
     * @param file the file, pack-relative
     * @param kind the file's schema kind; any other kind than {@code mission-type} has none
     * @param document the file's schema-valid document
     * @param table the table that classifies a named template
     * @return the refusal, as {@code names <path> ...} or {@code step '<id>' ...}; empty when the load reads the file
     */
    static Optional<String> refusal(Path directory, String file, String kind, Object document, PackFileKind table) {
        if (!SCHEMA.equals(kind) || !(document instanceof Map<?, ?> type)) {
            return Optional.empty();
        }
        List<Map<?, ?>> steps = steps(type);
        Optional<String> repeated = repeatedStep(steps);
        if (repeated.isPresent()) {
            return repeated;
        }
        Path base = Objects.requireNonNull(directory.resolve(file).getParent(), "a mission-type file has a parent");
        for (Map<?, ?> step : steps) {
            Optional<String> companion = companionProblem(base, step);
            if (companion.isPresent()) {
                return companion;
            }
        }
        return FilesystemPackStore.missingNamedFile(directory, file, namedTemplates(type, steps), table)
                .map(why -> "names " + why);
    }

    private static List<Map<?, ?>> steps(Map<?, ?> type) {
        List<Map<?, ?>> out = new ArrayList<>();
        for (String list : List.of("steps", "on_demand_steps")) {
            if (type.get(list) instanceof List<?> items) {
                items.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> (Map<?, ?>) item)
                        .forEach(out::add);
            }
        }
        return out;
    }

    private static Optional<String> repeatedStep(List<Map<?, ?>> steps) {
        Set<Object> seen = new HashSet<>();
        for (Map<?, ?> step : steps) {
            if (!seen.add(step.get("id"))) {
                return Optional.of("step '" + step.get("id") + "' stands twice across steps and on_demand_steps");
            }
        }
        return Optional.empty();
    }

    private static Optional<String> companionProblem(Path base, Map<?, ?> step) {
        Object id = step.get("id");
        for (String suffix : List.of("prompt", "guidelines")) {
            if (!(step.get(suffix + "_path") instanceof String named)) {
                continue;
            }
            String derived = "steps/" + id + "." + suffix + ".md";
            if (!named.equals(derived)) {
                return Optional.of("step '" + id + "' names " + named + ", not its derived path " + derived);
            }
            if (!Files.isRegularFile(base.resolve(named))) {
                return Optional.of("step '" + id + "' names " + named + ": missing");
            }
        }
        return Optional.empty();
    }

    private static List<KindBinding.NamedFile> namedTemplates(Map<?, ?> type, List<Map<?, ?>> steps) {
        List<KindBinding.NamedFile> out = new ArrayList<>(templatesOf(type, "the type"));
        for (Map<?, ?> step : steps) {
            out.addAll(templatesOf(step, "step '" + step.get("id") + "'"));
        }
        return out;
    }

    private static List<KindBinding.NamedFile> templatesOf(Map<?, ?> holder, String namedBy) {
        List<KindBinding.NamedFile> out = new ArrayList<>();
        if (holder.get("expected_output_artifacts") instanceof List<?> outputs) {
            for (Object output : outputs) {
                if (output instanceof Map<?, ?> artifact && artifact.get("template_path") instanceof String path) {
                    out.add(new KindBinding.NamedFile(path, namedBy));
                }
            }
        }
        return out;
    }
}
