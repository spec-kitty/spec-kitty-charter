package ai.speckitty.charter.deployable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * The edges the graph files of the example packs (ADR-0070) write, read from the YAML alone: every {@code *.graph.yaml} at
 * a pack's root, and the fragment its {@code pack.yaml} names as {@code pack_drg}.
 */
final class PackGraphEdges {

    private PackGraphEdges() {}

    /**
     * The words of every edge the graph files of the given packs write, keyed {@code "<source> <relation> <target>"} (a
     * pack qualifier on an end is dropped): what the {@code edge} of an entry must be, by the contract's rule that an
     * entry carries the words of the edge it was reached through. An edge that states neither word has the empty map.
     * More than one edge may share a key across the packs, so each key holds the set of the words written for it.
     */
    static Map<String, Set<Map<String, String>>> words(Path packsDirectory, List<String> packs) throws IOException {
        Map<String, Set<Map<String, String>>> words = new HashMap<>();
        for (String pack : packs) {
            for (Path file : graphFiles(packsDirectory.resolve(pack))) {
                Map<String, Object> yaml = yaml(file);
                Object edges = yaml.get("edges");
                for (Object edge : edges == null ? List.of() : (List<?>) edges) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> e = (Map<String, Object>) edge;
                    Map<String, String> stated = new LinkedHashMap<>();
                    if (e.get("when") != null) {
                        stated.put("when", String.valueOf(e.get("when")));
                    }
                    if (e.get("reason") != null) {
                        stated.put("reason", String.valueOf(e.get("reason")));
                    }
                    String key =
                            unqualified(e.get("source")) + " " + e.get("relation") + " " + unqualified(e.get("target"));
                    words.computeIfAbsent(key, ignored -> new HashSet<>()).add(stated);
                }
            }
        }
        return words;
    }

    private static List<Path> graphFiles(Path pack) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> list = Files.list(pack)) {
            list.filter(f -> f.getFileName().toString().endsWith(".graph.yaml"))
                    .sorted()
                    .forEach(files::add);
        }
        Object fragment = yaml(pack.resolve("pack.yaml")).get("pack_drg");
        if (fragment != null) {
            files.add(pack.resolve(String.valueOf(fragment)));
        }
        return files;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(Path file) throws IOException {
        return (Map<String, Object>) new Load(LoadSettings.builder().build()).loadFromString(Files.readString(file));
    }

    private static String unqualified(Object urn) {
        return String.valueOf(urn).replaceFirst("^[^:]*::", "");
    }
}
