package ai.speckitty.charter.packs.golden;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.application.WholePackWriteStore;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackValidation;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.examples.PlantedPacks;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packs.infra.storage.PackLocation;
import ai.speckitty.charter.packs.infra.storage.WholePackStore;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What every pack-judging surface answers for one pack, as one stable, ordered value (#554, IC-01, NFR-002): the
 * characterization the work packages after WP01 are compared with. Three surfaces:
 *
 * <ul>
 *   <li>{@code validate}: validate mode, {@code new PackValidation(new FileSystemPackInspector()).validate(root)}; every
 *       finding as code, severity, file, place and detail, in the verdict's order;
 *   <li>{@code registration}: the staged load {@link WholePackStore#loadStaged}, as {@code PackRegistrationService} and
 *       {@code PackSourceStagers} call it (with the store production builds, {@code new
 *       WholePackStore(strategy)}); on a refusal its code, detail and violations (pointer, code, detail) in order, on
 *       success the diagnostics (code, detail) the registration answers, in order;
 *   <li>{@code composeRead}: the compose's read of the pack as a stored pack, {@link
 *       DomainRepositoryService#compositionFacts}; the refusal's code and detail ({@code pack_unreadable}, the first
 *       violation as {@code readable} words it), or {@code readable}.
 * </ul>
 *
 * <p>Each surface reads its own fresh copy of the pack, so none sees what another did. The only normalisation is the
 * temporary directory the copies live in, which becomes {@value #SCRATCH} wherever a text names it: nothing else of a
 * detail is touched.
 */
final class SurfaceAnswers {

    /** What the temporary directory the copies live in becomes in a recorded text. */
    static final String SCRATCH = "<scratch>";

    /** A recorded compose read that refused nothing. */
    static final String READABLE = "readable";

    /** The name a planted pack is staged and stored under, as the planted tests name it. */
    static final String PLANT_NAME = "qa";

    /** One golden file entry per planted pack, then one per example pack. */
    private static final String PLANTED = "planted/";

    private static final String EXAMPLE = "example-packs/";

    /** The suffix of a plant judged again with a descriptor, when its own pack has none. */
    static final String WITH_DESCRIPTOR = "+pack.yaml";

    private static final String PACK_YAML = "pack.yaml";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SurfaceAnswers() {}

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
    record Subject(
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
        boolean plantsLinks() {
            return !links.isEmpty();
        }

        @Override
        public String toString() {
            return key;
        }
    }

    /**
     * Every subject: each planted pack that holds files (a plant proven by a named test holds none), then the three
     * example packs. A plant whose pack has no {@code pack.yaml} answers the registration and the compose read with
     * that alone, so it is judged a second time, as {@code planted/<plant>}{@value #WITH_DESCRIPTOR}, with the clean
     * control's descriptor beside its files: that entry shows what the two surfaces answer for the plant's own fault.
     *
     * @return the subjects, in the golden file's order
     */
    static List<Subject> subjects() {
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

    /**
     * What every surface answers for the subject.
     *
     * @param subject the pack
     * @param scratch an empty directory the copies are made in
     * @return {@code validate}, {@code registration} and {@code composeRead}, in that order
     * @throws IOException when a copy or a link cannot be made
     */
    static Map<String, Object> of(Subject subject, Path scratch) throws IOException {
        Normaliser normaliser = new Normaliser(scratch);
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("validate", validate(subject, copy(subject, scratch.resolve("validate")), normaliser));
        answers.put("registration", registration(copy(subject, scratch.resolve("registration")), normaliser));
        answers.put("composeRead", composeRead(subject, copy(subject, scratch.resolve("compose-read")), normaliser));
        return answers;
    }

    private static Path copy(Subject subject, Path host) throws IOException {
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

    private static Object validate(Subject subject, Path pack, Normaliser normaliser) {
        try {
            List<Map<String, Object>> findings = new ArrayList<>();
            for (PackFinding finding : new PackValidation(new FileSystemPackInspector())
                    .validate(new PackRoot(subject.name(), subject.configuredLevel(), pack.toString()))
                    .findings()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", finding.code().name().toLowerCase(Locale.ROOT));
                row.put("severity", finding.severity().name().toLowerCase(Locale.ROOT));
                row.put("file", normaliser.apply(finding.file()));
                row.put("place", List.copyOf(finding.place()));
                row.put("detail", normaliser.apply(finding.detail()));
                findings.add(row);
            }
            return Map.of("findings", findings);
        } catch (RuntimeException thrown) {
            return threw(thrown, normaliser);
        }
    }

    private static Object registration(Path pack, Normaliser normaliser) {
        try {
            WholePackWriteStore.Contents contents =
                    new WholePackStore(new YamlSerializationStrategy()).loadStaged(new StagedPack(pack));
            List<Map<String, Object>> diagnostics = new ArrayList<>();
            contents.warnings().stream().map(PackFinding::toDiagnostic).forEach(diagnostic -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("code", diagnostic.code().token());
                row.put("detail", normaliser.apply(diagnostic.detail()));
                diagnostics.add(row);
            });
            return Map.of("accepted", Map.of("diagnostics", diagnostics));
        } catch (CharterRefusal refusal) {
            Map<String, Object> refused = refused(refusal, normaliser);
            List<Map<String, Object>> violations = new ArrayList<>();
            for (RefusalViolation violation : refusal.violations()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("pointer", normaliser.apply(violation.pointer()));
                row.put("code", violation.code());
                row.put("detail", normaliser.apply(violation.detail()));
                violations.add(row);
            }
            refused.put("violations", violations);
            return Map.of("refused", refused);
        } catch (RuntimeException thrown) {
            return threw(thrown, normaliser);
        }
    }

    private static Object composeRead(Subject subject, Path pack, Normaliser normaliser) {
        PackLocation location = new PackLocation(subject.name(), subject.storedLevel(), pack);
        try {
            new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy())
                    .compositionFacts(new PackIdentifier(subject.name()));
            return READABLE;
        } catch (CharterRefusal refusal) {
            return Map.of("refused", refused(refusal, normaliser));
        } catch (RuntimeException thrown) {
            return threw(thrown, normaliser);
        }
    }

    private static Map<String, Object> refused(CharterRefusal refusal, Normaliser normaliser) {
        Map<String, Object> refused = new LinkedHashMap<>();
        refused.put("code", refusal.code().token());
        refused.put("detail", normaliser.apply(String.valueOf(refusal.getMessage())));
        return refused;
    }

    /** A surface that threw something other than a refusal: recorded as it is, so a change of it shows. */
    private static Map<String, Object> threw(RuntimeException thrown, Normaliser normaliser) {
        Map<String, Object> threw = new LinkedHashMap<>();
        threw.put("type", thrown.getClass().getName());
        threw.put("message", normaliser.apply(String.valueOf(thrown.getMessage())));
        return Map.of("threw", threw);
    }

    /** Replaces the scratch directory, as written and as its real path, with {@value #SCRATCH}. */
    private static final class Normaliser implements Function<String, String> {

        private final List<String> spellings = new ArrayList<>();

        Normaliser(Path scratch) {
            try {
                spellings.add(scratch.toRealPath().toString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            spellings.add(scratch.toAbsolutePath().normalize().toString());
        }

        @Override
        public String apply(String text) {
            String normalised = text;
            for (String spelling : spellings) {
                normalised = normalised.replace(spelling, SCRATCH);
            }
            return normalised;
        }
    }
}
