package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.packs.domain.MissionTypeFacts;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.ArtifactFacts;
import ai.speckitty.charter.packs.domain.PackContents.AssetFacts;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawEdge;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.examples.PlantedPacks;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The content rules' fixtures (#554, T034). The planted and example packs, each read once per JVM by the one reader
 * ({@link FileSystemPackInspector#read}) into the {@link PackReadout} the rules and today's {@link PackPolicy} both
 * judge, with the findings a plant's {@code expected.json} names; and the facts a test writes by hand.
 *
 * <p>The subjects are the golden snapshot's ({@code SurfaceAnswers}): every plant that holds files, with its symbolic
 * links, judged at its configured level; a plant with no {@code pack.yaml} again as {@code <plant>+pack.yaml}, with the
 * clean control's descriptor beside its files; then the example packs.
 */
final class PlantedFixtures {

    /** The name a planted pack is read under, as the golden names it. */
    private static final String PLANT_NAME = "qa";

    private static final String PACK_YAML = "pack.yaml";

    private static final String FRAGMENT = "drg/fragment.yaml";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static volatile List<Planted> read;

    private PlantedFixtures() {}

    /**
     * One finding a plant's {@code expected.json} names.
     *
     * @param code the code's token
     * @param file the pack-relative file
     * @param place the pointer segments inside it
     */
    record Expected(String code, String file, List<String> place) {

        /**
         * What a reason states, in the terms of an {@code expected.json} finding.
         *
         * @param reason the reason
         * @return its code's token, file and place
         */
        static Expected of(Reason reason) {
            PackFinding finding = PackFinding.of(reason);
            return new Expected(finding.code().value(), finding.file(), finding.place());
        }
    }

    /**
     * One pack, as the one reader read it.
     *
     * @param key {@code planted/<plant>}, {@code planted/<plant>+pack.yaml} or {@code example-packs/<name>}
     * @param readout what the one reader gave
     * @param expected the findings the plant's {@code expected.json} names; empty for a plant judged with a descriptor
     *     of the test's own, and for an example pack
     */
    record Planted(String key, PackReadout readout, Optional<List<Expected>> expected) {

        /**
         * The facts of the read.
         *
         * @return the readout's contents
         */
        PackContents contents() {
            return readout.contents();
        }

        @Override
        public String toString() {
            return key;
        }
    }

    /**
     * Every subject, read once per JVM.
     *
     * @return the subjects, in the golden snapshot's order
     */
    static List<Planted> all() {
        List<Planted> local = read;
        if (local == null) {
            synchronized (PlantedFixtures.class) {
                local = read;
                if (local == null) {
                    local = readAll();
                    read = local;
                }
            }
        }
        return local;
    }

    /**
     * The subjects whose {@code expected.json} names a finding of one of the codes.
     *
     * @param codes the codes' tokens
     * @return those subjects
     */
    static List<Planted> expecting(Set<String> codes) {
        return all().stream()
                .filter(planted -> planted.expected()
                        .filter(expected -> expected.stream().anyMatch(finding -> codes.contains(finding.code())))
                        .isPresent())
                .toList();
    }

    /**
     * What today's validate mode ({@link PackPolicy#judge}) finds of the codes over a readout, less the read faults of
     * those codes that it reports as they came: the findings of the policy's own rules for that group, as reasons.
     *
     * @param readout the readout
     * @param codes the group's codes
     * @return the policy's findings of the group, in its precedence order
     */
    static List<Reason> policyFindings(PackReadout readout, Collection<ReasonCode> codes) {
        List<Reason> readFaults = new ArrayList<>(readout.rationale().failures());
        List<Reason> own = new ArrayList<>();
        for (PackFinding finding : PackPolicy.judge(readout).findings()) {
            if (codes.contains(finding.code()) && !readFaults.remove(finding.reason())) {
                own.add(finding.reason());
            }
        }
        return own;
    }

    /**
     * Facts a test writes by hand: an org pack named {@code qa} with a read descriptor that names it {@code qa} and an
     * empty fragment, holding what is given.
     *
     * @param artifacts the artifact facts
     * @param assets the asset facts
     * @param types the mission type facts
     * @param edges the fragment's edges
     * @return the facts
     */
    static PackContents facts(
            List<ArtifactFacts> artifacts, List<AssetFacts> assets, List<MissionTypeFacts> types, List<RawEdge> edges) {
        return facts(
                artifacts,
                assets,
                types,
                new FileRead.Read<>(FRAGMENT, new RawGraph(FRAGMENT, RawGraph.Shape.FRAGMENT, List.of(), edges)));
    }

    /**
     * Facts a test writes by hand, with the fragment as the read gave it.
     *
     * @param artifacts the artifact facts
     * @param assets the asset facts
     * @param types the mission type facts
     * @param fragment what {@code drg/fragment.yaml} gave
     * @return the facts
     */
    static PackContents facts(
            List<ArtifactFacts> artifacts,
            List<AssetFacts> assets,
            List<MissionTypeFacts> types,
            FileRead<RawGraph> fragment) {
        return new PackContents(
                new PackRoot(PLANT_NAME, Optional.of(PackLevel.ORG), "loc"),
                new FileRead.Read<>(
                        PACK_YAML,
                        new PackDescriptor(
                                "01ARWG13C00000000000001ABC",
                                "1.0.0",
                                Optional.empty(),
                                PLANT_NAME,
                                Optional.of(PackLevel.ORG),
                                Optional.empty())),
                List.of(),
                fragment,
                artifacts,
                assets,
                StepEnhancementFacts.none(),
                List.of(),
                types);
    }

    private static List<Planted> readAll() {
        try {
            Path scratch = Files.createTempDirectory("content-rules");
            boolean links = linksWork(scratch);
            FileSystemPackInspector reader = new FileSystemPackInspector();
            List<Planted> subjects = new ArrayList<>();
            int n = 0;
            for (String plant : PlantedPacks.names()) {
                Path files = PlantedPacks.plant(plant).resolve("files");
                if (!Files.isDirectory(files)) {
                    continue;
                }
                JsonNode expected = JSON.readTree(
                        PlantedPacks.plant(plant).resolve("expected.json").toFile());
                List<String[]> symlinks = new ArrayList<>();
                Optional.ofNullable(expected.get("symlinks"))
                        .ifPresent(all -> all.forEach(link -> symlinks.add(new String[] {
                            link.get("link").asString(), link.get("target").asString()
                        })));
                if (!symlinks.isEmpty() && !links) {
                    continue;
                }
                Optional<PackLevel> level = Optional.ofNullable(expected.get("configuredLevel"))
                        .map(node -> PackLevel.fromToken(node.asString()).orElseThrow());
                Path pack = plantAt(plant, scratch.resolve(Integer.toString(n++)), symlinks);
                subjects.add(new Planted(
                        "planted/" + plant,
                        reader.read(new PackRoot(PLANT_NAME, level, pack.toString())),
                        Optional.of(findings(expected))));
                if (!Files.exists(files.resolve(PACK_YAML))) {
                    Path described = plantAt(plant, scratch.resolve(Integer.toString(n++)), symlinks);
                    Files.writeString(
                            described.resolve(PACK_YAML),
                            "pack_id: 01ARWG13C000000000000000FG\npack_version: 1.0.0\nname: " + PLANT_NAME
                                    + "\npack_level: "
                                    + level.orElse(PackLevel.ORG).token() + "\n",
                            StandardCharsets.UTF_8);
                    subjects.add(new Planted(
                            "planted/" + plant + "+pack.yaml",
                            reader.read(new PackRoot(PLANT_NAME, level, described.toString())),
                            Optional.empty()));
                }
            }
            for (String name : ExamplePacks.names()) {
                Path pack = ExamplePacks.copyOf(name, scratch.resolve(Integer.toString(n++)));
                Path named = pack.resolveSibling(name);
                if (!named.equals(pack)) {
                    Files.move(pack, named);
                }
                subjects.add(new Planted(
                        "example-packs/" + name,
                        reader.read(new PackRoot(name, Optional.of(ExamplePacks.level(name)), named.toString())),
                        Optional.empty()));
            }
            return List.copyOf(subjects);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path plantAt(String plant, Path host, List<String[]> symlinks) throws IOException {
        Files.createDirectories(host);
        Path pack = PlantedPacks.copyOf(plant, host);
        for (String[] link : symlinks) {
            Files.createSymbolicLink(pack.resolve(link[0]), Path.of(link[1]));
        }
        return pack;
    }

    private static List<Expected> findings(JsonNode expected) {
        List<Expected> findings = new ArrayList<>();
        Optional.ofNullable(expected.get("findings"))
                .ifPresent(all -> all.forEach(finding -> {
                    List<String> place = new ArrayList<>();
                    finding.get("place").forEach(segment -> place.add(segment.asString()));
                    findings.add(new Expected(
                            finding.get("code").asString(), finding.get("file").asString(), place));
                }));
        return List.copyOf(findings);
    }

    /**
     * Whether the file system makes symbolic links; a plant that needs one is left out where it cannot, and the read
     * fails there when the test JVM's {@code charter.requireSymlinks} is true, as CI sets it (#286).
     */
    private static boolean linksWork(Path scratch) throws IOException {
        Path target = Files.createTempFile(scratch, "probe", ".tmp");
        Path link = scratch.resolve("probe.lnk");
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | FileSystemException noLinks) {
            if (Boolean.getBoolean("charter.requireSymlinks")) {
                throw new IllegalStateException("the file system cannot create symbolic links", noLinks);
            }
            return false;
        } finally {
            Files.deleteIfExists(link);
            Files.delete(target);
        }
    }
}
