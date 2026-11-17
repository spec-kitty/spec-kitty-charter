package ai.speckitty.charter.packs.examples;

import ai.speckitty.charter.kernel.domain.PackLevel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The packs every pack-judging surface's golden snapshot records (#554, IC-01, NFR-002): each planted pack that holds
 * files, then the example packs. The registration and the compose read are recorded by {@code :packs}'
 * {@code SurfaceGoldenTest}, validate mode by {@code :pack-validation}'s {@code ValidateGoldenTest}, over the same
 * subjects.
 */
public final class GoldenSubjects {

    /** The name a planted pack is staged and stored under, as the planted tests name it. */
    public static final String PLANT_NAME = "qa";

    /** The suffix of a plant judged again with a descriptor, when its own pack has none. */
    public static final String WITH_DESCRIPTOR = "+pack.yaml";

    private static final String PLANTED = "planted/";

    private static final String EXAMPLE = "example-packs/";

    private static final String PACK_YAML = "pack.yaml";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private GoldenSubjects() {}

    /**
     * A pack the surfaces judge.
     *
     * @param key the golden file's key: {@code planted/<plant>} or {@code example-packs/<name>}
     * @param name the name the pack is judged, staged and stored under; its directory's name too
     * @param configuredLevel the level validate mode is told; empty for a bare path
     * @param storedLevel the level the compose read stores it at (a stored pack always has one)
     * @param links the symbolic links the plant's {@code expected.json} asks for: link (pack-relative), then target
     * @param copy writes a fresh copy of the pack at the path given
     */
    public record Subject(
            String key,
            String name,
            Optional<PackLevel> configuredLevel,
            PackLevel storedLevel,
            Map<String, String> links,
            Function<Path, Path> copy) {

        /**
         * Whether the pack plants symbolic links, which a file system without them cannot hold.
         *
         * @return true when it does
         */
        public boolean plantsLinks() {
            return !links.isEmpty();
        }

        @Override
        public String toString() {
            return key;
        }
    }

    /**
     * Every subject: each planted pack that holds files (a plant proven by a named test holds none), then the three
     * example packs. A plant whose pack has no {@code pack.yaml} is judged a second time, as
     * {@code planted/<plant>}{@value #WITH_DESCRIPTOR}, with the clean control's descriptor beside its files: that
     * entry shows what the surfaces answer for the plant's own fault.
     *
     * @return the subjects, in the golden file's order
     */
    public static List<Subject> subjects() {
        List<Subject> subjects = new ArrayList<>();
        for (String plant : PlantedPacks.names()) {
            Path files = PlantedPacks.plant(plant).resolve("files");
            if (Files.isDirectory(files)) {
                Subject planted = planted(plant);
                subjects.add(planted);
                if (!Files.exists(files.resolve(PACK_YAML))) {
                    subjects.add(withDescriptor(planted));
                }
            }
        }
        for (String name : ExamplePacks.names()) {
            PackLevel level = ExamplePacks.level(name);
            subjects.add(new Subject(
                    EXAMPLE + name,
                    name,
                    Optional.of(level),
                    level,
                    Map.of(),
                    host -> ExamplePacks.copyOf(name, host)));
        }
        return List.copyOf(subjects);
    }

    /**
     * A fresh copy of the subject under {@code host}, named as the subject is judged, with its symbolic links.
     *
     * @param subject the pack
     * @param host an empty directory the copy is made in
     * @return the copy's directory
     * @throws IOException when the copy or a link cannot be made
     */
    public static Path copy(Subject subject, Path host) throws IOException {
        Files.createDirectories(host);
        Path pack = subject.copy().apply(host);
        Path named = pack.resolveSibling(subject.name());
        if (!named.equals(pack)) {
            Files.move(pack, named);
        }
        for (Map.Entry<String, String> link : subject.links().entrySet()) {
            Files.createSymbolicLink(named.resolve(link.getKey()), Path.of(link.getValue()));
        }
        return named;
    }

    /** The plant with the clean control's descriptor written beside its files, at the level it is stored at. */
    private static Subject withDescriptor(Subject planted) {
        String descriptor = "pack_id: 01ARWG13C000000000000000FG\npack_version: 1.0.0\nname: " + PLANT_NAME
                + "\npack_level: " + planted.storedLevel().token() + "\n";
        return new Subject(
                planted.key() + WITH_DESCRIPTOR,
                planted.name(),
                planted.configuredLevel(),
                planted.storedLevel(),
                planted.links(),
                host -> {
                    Path pack = planted.copy().apply(host);
                    try {
                        Files.writeString(pack.resolve(PACK_YAML), descriptor, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return pack;
                });
    }

    private static Subject planted(String plant) {
        JsonNode expected =
                JSON.readTree(PlantedPacks.plant(plant).resolve("expected.json").toFile());
        Optional<PackLevel> level = Optional.ofNullable(expected.get("configuredLevel"))
                .map(node -> PackLevel.fromToken(node.asString()).orElseThrow());
        Map<String, String> links = new LinkedHashMap<>();
        JsonNode symlinks = expected.get("symlinks");
        if (symlinks != null) {
            symlinks.forEach(link ->
                    links.put(link.get("link").asString(), link.get("target").asString()));
        }
        // A plant with no configured level is a bare path to validate mode; stored, it needs a level, and org is the
        // level the planted tests store a plant at.
        return new Subject(
                PLANTED + plant,
                PLANT_NAME,
                level,
                level.orElse(PackLevel.ORG),
                Collections.unmodifiableMap(links),
                host -> PlantedPacks.copyOf(plant, host));
    }
}
