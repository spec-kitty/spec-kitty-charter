package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The shared walk the pack tests use to read what is on disk without the store: files by suffix, the steps a mission
 * type declares, and the guard that a glob over several packs found files in each. It is tied to no pack source; the
 * pack tests point it at the example packs (ADR-0070).
 */
public final class PackFiles {

    private PackFiles() {}

    /**
     * The files under {@code directory} whose name ends in {@code suffix}, as {@code /}-separated paths relative to
     * {@code directory}, sorted; empty when the directory is absent.
     */
    public static List<String> filesEndingWith(Path directory, String suffix) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(Files::isRegularFile)
                    .map(file -> directory.relativize(file).toString().replace('\\', '/'))
                    .filter(path -> path.endsWith(suffix))
                    .sorted()
                    .toList();
        }
    }

    /**
     * Fails unless {@code files} holds at least one file of each named example pack, so a pack whose files a glob over
     * all packs should have found cannot silently drop out.
     */
    public static void assertEachPackHolds(String what, List<Path> files, String... packs) {
        for (String pack : packs) {
            Path directory = ExamplePacks.pack(pack);
            if (files.stream().noneMatch(file -> file.startsWith(directory))) {
                throw new AssertionError("The example pack '" + pack + "' holds no " + what);
            }
        }
    }

    /** The step ids a mission-type file declares: the sequence, then the on-demand steps, each in file order. */
    record DeclaredSteps(List<String> sequence, List<String> onDemand) {}

    /** What the mission-type file declares under {@code steps} and {@code on_demand_steps}, read as YAML without the store. */
    static DeclaredSteps declaredSteps(Path missionTypeFile) throws IOException {
        Map<?, ?> document = (Map<?, ?>) Yaml.load(Files.readString(missionTypeFile, StandardCharsets.UTF_8));
        return new DeclaredSteps(stepIds(document, "steps"), stepIds(document, "on_demand_steps"));
    }

    private static List<String> stepIds(Map<?, ?> document, String key) {
        List<String> ids = new ArrayList<>();
        if (document.get(key) instanceof List<?> steps) {
            for (Object step : steps) {
                if (step instanceof Map<?, ?> fields) {
                    ids.add(String.valueOf(fields.get("id")));
                }
            }
        }
        return ids;
    }
}
