package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.TreeReads;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import ai.speckitty.charter.packs.infra.storage.rules.StorageRulesets;
import ai.speckitty.charter.packs.infra.storage.rules.TreeRead;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every pack code is exercised (#554, NFR-005, T055): each {@link PackCodes} content code and each {@link StorageCodes}
 * code has at least one fixture whose one-read verdict ({@code StorageRulesets.verdict}, what {@code PackReading}
 * returns) states it, and one whose verdict does not; each {@code CLOSURE} code has a set of packs {@code
 * CompositionRules.closure()} refuses with it, and a passing twin. The fixtures are the planted and example packs
 * ({@link PlantedFixtures}), then a few small packs written here for the codes no plant reaches (a new plant would add
 * golden rows), and one hand-written fact for the aggregate's breach, which no read reaches. A code with no failing or
 * no passing fixture fails its case, naming the code. The {@code PlacementCodes} constants are enumerated beside their
 * subject, in {@code :pack-validation}'s {@code PlacementCodesCoverageTest} (ADR-0062).
 */
@DisplayName("every pack code has a failing and a passing fixture (#554, NFR-005)")
class PackCodesCoverageTest {

    /** One judged fixture: its name and the reasons its verdict states. */
    record Judged(String name, Optional<ReasonCode> standsFor, List<Reason> reasons) {
        boolean states(ReasonCode code) {
            return reasons.stream().anyMatch(reason -> reason.code().equals(code));
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final String DESCRIPTOR =
            "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n";

    /** A change written onto a pack directory. */
    @FunctionalInterface
    interface Change {
        void apply(Path pack) throws IOException;
    }

    /**
     * A small pack no plant stands for: written from the clean descriptor alone, or from a copy of an example pack,
     * then changed. Each is named for the code it stands for.
     *
     * @param name the code it stands for, and how
     * @param code the code its verdict states
     * @param example the example pack it starts from; empty for the clean descriptor alone
     * @param change what is written onto it
     */
    record Written(String name, ReasonCode code, Optional<String> example, Change change) {}

    private static final String MISSION = "missions/software-dev/";
    private static final String ENHANCEMENTS = MISSION + "enhancements/";
    private static final String FILLED =
            "# Plan\n\n<!-- spdd:reasons-block:start -->legacy<!-- spdd:reasons-block:end -->\n";

    private static List<Written> written() {
        List<Written> all = new ArrayList<>(promptPacks());
        all.addAll(graphPacks());
        all.addAll(storagePacks());
        return all;
    }

    /** The prompt slot codes, each beside the descriptor version that gates it. */
    private static List<Written> promptPacks() {
        return List.of(
                bare(
                        "prompt_slot_malformed",
                        PackCodes.PROMPT_SLOT_MALFORMED,
                        pack -> promptOf(pack, "# Plan\n\n<!-- spdd:reasons-block:start -->\n")),
                bare("prompt_slot_not_empty (violation, schema_version 1.0)", PackCodes.PROMPT_SLOT_NOT_EMPTY, pack -> {
                    write(pack, "pack.yaml", DESCRIPTOR + "schema_version: '1.0'\n");
                    promptOf(pack, FILLED);
                }),
                bare(
                        "prompt_slot_not_empty (warning, unversioned)",
                        PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING,
                        pack -> promptOf(pack, FILLED)));
    }

    /** The prompt fragment and the graph's endpoint codes. */
    private static List<Written> graphPacks() {
        return List.of(
                bare("prompt_fragment_invalid", PackCodes.PROMPT_FRAGMENT_INVALID, pack -> {
                    write(pack, "enhancements.graph.yaml", """
                            schema_version: '1.0'
                            generated_at: STATIC
                            nodes: []
                            edges:
                            - source: step_enhancement:spdd-specify
                              target: base::action:software-dev/specify
                              relation: enhances
                            """);
                    write(pack, ENHANCEMENTS + "spdd-specify.step-enhancement.yaml", """
                            schema_version: "1.0"
                            id: spdd-specify
                            prompt_fragments:
                              - slot: spdd:reasons-block
                                path: spdd-specify.prompt-fragment.md
                            """);
                    write(
                            pack,
                            ENHANCEMENTS + "spdd-specify.prompt-fragment.md",
                            "## Guidance\n\n<!-- other:slot:start -->\nText.\n");
                }),
                bare(
                        "overrides_source_not_activatable",
                        PackCodes.OVERRIDES_SOURCE_NOT_ACTIVATABLE,
                        pack -> write(pack, "template.graph.yaml", """
                        schema_version: '1.0'
                        generated_at: STATIC
                        nodes: []
                        edges:
                        - source: template:t
                          target: directive:d
                          relation: overrides
                        """)));
    }

    /** The companion, the descriptor format and the storage codes. */
    private static List<Written> storagePacks() {
        return List.of(
                bare("companion", StorageCodes.COMPANION, pack -> {
                    write(pack, "toolguides/make.toolguide.yaml", """
                            id: make
                            schema_version: '1.0'
                            tool: make
                            title: Make
                            guide_path: make.guide.md
                            summary: S.
                            commands:
                            - make
                            last_updated: '2026-07-28'
                            """);
                    write(pack, "toolguides/make.guide.md", "# Use make\n\n## Usage\n\nRun make.\n");
                }),
                bare(
                        "pack_version_invalid",
                        StorageCodes.PACK_VERSION_INVALID,
                        pack -> write(pack, "pack.yaml", DESCRIPTOR.replace("1.0.0", "not-pep440"))),
                bare(
                        "dependency_range_invalid",
                        StorageCodes.DEPENDENCY_RANGE_INVALID,
                        pack -> write(
                                pack,
                                "pack.yaml",
                                DESCRIPTOR
                                        + "depends_on:\n  - pack_id: 01ARWG13C000000000000000FG\n    range: banana\n")),
                bare(
                        "reserved_shard",
                        StorageCodes.RESERVED_SHARD,
                        pack -> write(
                                pack,
                                "registered.graph.yaml",
                                "schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges: []\n")),
                new Written(
                        "manifest_membership",
                        StorageCodes.MANIFEST_MEMBERSHIP,
                        Optional.of(ExamplePacks.PROJECT),
                        pack -> {
                            Path manifest = pack.resolve("pack-manifest.yaml");
                            Files.writeString(
                                    manifest,
                                    Files.readString(manifest, StandardCharsets.UTF_8)
                                            .replaceFirst(
                                                    "- kind: skill\\n  id: project-triage\\n  path:"
                                                            + " skills/project-triage.skill.yaml\\n  content_hash: [0-9a-f]+\\n",
                                                    ""),
                                    StandardCharsets.UTF_8);
                        }));
    }

    private static Written bare(String name, ReasonCode code, Change change) {
        return new Written(name, code, Optional.empty(), change);
    }

    /** A mission type whose one step's prompt is {@code prompt}, linked to its action. */
    private static void promptOf(Path pack, String prompt) throws IOException {
        write(pack, MISSION + "steps/plan.prompt.md", prompt);
        write(pack, MISSION + "software-dev.mission-type.yaml", """
                schema_version: "1.0"
                id: software-dev
                title: Software development
                description: Build
                steps:
                  - id: plan
                    title: plan
                    prompt_path: steps/plan.prompt.md
                """);
        write(pack, "mission_type.graph.yaml", """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - {source: 'mission_type:software-dev', target: 'action:software-dev/plan', relation: requires}
                """);
    }

    private static void write(Path pack, String relative, String text) throws IOException {
        Path file = pack.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /**
     * The aggregate's own breach ({@code validation_failed}, R2 row 26): no read reaches it, since the store half
     * withholds every invariant's breach with its own code first (WP07 review MINOR-4), so the fact is written by hand
     * onto the clean control's read, as {@code StorageRulesetsTest} writes it.
     */
    private static Judged assemblyRefused(TreeRead clean) {
        PackContents contents = clean.contents();
        PackContents refused = new PackContents(
                contents.root(),
                contents.descriptor(),
                contents.shards(),
                contents.fragment(),
                contents.artifacts(),
                contents.assets(),
                contents.stepEnhancements(),
                contents.promptSlots(),
                contents.missionTypes(),
                new PackContents.Assembly.Refused(List.of(), RefusalCode.VALIDATION_FAILED, "two tactics", false));
        return new Judged(
                "facts/validation_failed",
                Optional.of(StorageCodes.ASSEMBLY_REFUSED),
                StorageRulesets.verdict(new TreeRead(refused, clean.census(), clean.readFaults()))
                        .details());
    }

    private static volatile List<Judged> corpus;

    /** The planted and example packs, the packs written here and the hand-written fact, each judged once per JVM. */
    static List<Judged> corpus() {
        List<Judged> local = corpus;
        if (local == null) {
            synchronized (PackCodesCoverageTest.class) {
                local = corpus;
                if (local == null) {
                    local = judgeAll();
                    corpus = local;
                }
            }
        }
        return local;
    }

    private static List<Judged> judgeAll() {
        List<Judged> judged = new ArrayList<>();
        TreeRead clean = null;
        for (PlantedFixtures.Planted planted : PlantedFixtures.all()) {
            judged.add(new Judged(
                    planted.key(),
                    Optional.empty(),
                    planted.readout().rationale().details()));
            if (planted.key().equals("planted/control-a-clean-org-pack")) {
                clean = planted.tree();
            }
        }
        if (clean == null) {
            throw new IllegalStateException("the clean control is not among the plants");
        }
        Path scratch;
        try {
            scratch = Files.createTempDirectory("pack-codes-coverage");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            int n = 0;
            for (Written pack : written()) {
                Path host = scratch.resolve(Integer.toString(n++));
                Path dir;
                String name;
                if (pack.example().isPresent()) {
                    name = pack.example().get();
                    Path copy = ExamplePacks.copyOf(name, host);
                    dir = copy.resolveSibling(name);
                    if (!dir.equals(copy)) {
                        Files.move(copy, dir);
                    }
                } else {
                    name = "qa";
                    dir = Files.createDirectories(host.resolve(name));
                    write(dir, "pack.yaml", DESCRIPTOR);
                }
                pack.change().apply(dir);
                judged.add(new Judged(
                        "written/" + pack.name(),
                        Optional.of(pack.code()),
                        StorageRulesets.verdict(TreeReads.of(new PackRoot(name, Optional.empty(), dir.toString())))
                                .details()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            // The verdicts are judged; the written packs are not read again.
            deleteTree(scratch);
        }
        judged.add(assemblyRefused(clean));
        return List.copyOf(judged);
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every code a pack's verdict can state: the domain's content codes, then the storage edge's. */
    static List<ReasonCode> packCodes() {
        List<ReasonCode> codes = new ArrayList<>(PackCodes.CONTENT);
        codes.addAll(StorageCodes.ORDERED);
        return codes;
    }

    /** Every {@code public static final ReasonCode} field a class declares, read by reflection. */
    static Set<ReasonCode> declaredCodes(Class<?> owner) {
        Set<ReasonCode> codes = new LinkedHashSet<>();
        for (Field field : owner.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers)
                    && Modifier.isStatic(modifiers)
                    && Modifier.isFinal(modifiers)
                    && field.getType() == ReasonCode.class) {
                try {
                    codes.add((ReasonCode) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(field.getName(), e);
                }
            }
        }
        return codes;
    }

    @Test
    @DisplayName(
            "the enumeration is every ReasonCode constant PackCodes and StorageCodes declare, and every CLOSURE code")
    void theEnumerationIsEveryCode() {
        Set<ReasonCode> declared = new LinkedHashSet<>(declaredCodes(PackCodes.class));
        declared.addAll(declaredCodes(StorageCodes.class));

        // A constant left out of PackCodes.CONTENT or StorageCodes.ORDERED is never judged here: it fails, named.
        assertThat(packCodes()).containsExactlyInAnyOrderElementsOf(declared);
        assertThat(CLOSURE_FIXTURES.keySet()).containsExactlyInAnyOrderElementsOf(PackCodes.CLOSURE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packCodes")
    @DisplayName("each pack code is stated by a fixture's verdict, and not by another's")
    void eachPackCodeHasAFailingAndAPassingFixture(ReasonCode code) {
        List<Judged> failing =
                corpus().stream().filter(judged -> judged.states(code)).toList();
        List<Judged> passing =
                corpus().stream().filter(judged -> !judged.states(code)).toList();

        assertThat(failing).as("no fixture's verdict states %s", code).isNotEmpty();
        assertThat(passing).as("every fixture's verdict states %s", code).isNotEmpty();
    }

    static List<Judged> standing() {
        return corpus().stream()
                .filter(judged -> judged.standsFor().isPresent())
                .toList();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("standing")
    @DisplayName("each pack written here states the code it stands for")
    void eachWrittenPackStatesItsCode(Judged judged) {
        assertThat(judged.reasons())
                .extracting(Reason::code)
                .contains(judged.standsFor().orElseThrow());
    }

    // ---- CLOSURE -----------------------------------------------------------------------------------------------

    /** A set CLOSURE refuses with one code, and its twin that passes. */
    record ClosureFixture(List<PackUnderResolution> failing, List<PackUnderResolution> passing) {}

    private static final Map<ReasonCode, ClosureFixture> CLOSURE_FIXTURES = closureFixtures();

    private static Map<ReasonCode, ClosureFixture> closureFixtures() {
        PackUnderResolution base = pack("01BASE", "base", PackLevel.BUILT_IN, List.of());
        PackUnderResolution org = pack("01ORG", "org", PackLevel.ORG, List.of());
        Map<ReasonCode, ClosureFixture> fixtures = new LinkedHashMap<>();
        fixtures.put(
                code(RefusalCode.PACK_DEPENDENCY_CYCLE),
                new ClosureFixture(
                        List.of(
                                pack("01AAA", "a", PackLevel.ORG, depends("01BBB", ">=1.0")),
                                pack("01BBB", "b", PackLevel.ORG, depends("01AAA", ">=1.0"))),
                        List.of(
                                pack("01AAA", "a", PackLevel.ORG, depends("01BBB", ">=1.0")),
                                pack("01BBB", "b", PackLevel.ORG, List.of()))));
        fixtures.put(
                code(RefusalCode.PACK_DEPENDENCY_UNKNOWN),
                new ClosureFixture(
                        List.of(pack("01DEP", "dep", PackLevel.ORG, depends("01GONE", ">=1.0"))),
                        List.of(pack("01DEP", "dep", PackLevel.ORG, depends("01BASE", ">=1.0")), base)));
        fixtures.put(
                code(RefusalCode.PACK_DEPENDENCY_OUT_OF_RANGE),
                new ClosureFixture(
                        List.of(pack("01DEP", "dep", PackLevel.ORG, depends("01BASE", ">=2.0")), base),
                        List.of(pack("01DEP", "dep", PackLevel.ORG, depends("01BASE", ">=1.0")), base)));
        fixtures.put(
                code(RefusalCode.PACK_PARENT_UNRESOLVED),
                new ClosureFixture(
                        List.of(extending("01KID", "kid", PackLevel.ORG, "01GONE")),
                        List.of(extending("01KID", "kid", PackLevel.ORG, "01BASE"), base)));
        fixtures.put(
                code(RefusalCode.PACK_LEVEL_VIOLATION),
                new ClosureFixture(
                        List.of(pack("01DEP", "dep", PackLevel.BUILT_IN, depends("01ORG", ">=1.0")), org),
                        List.of(pack("01DEP", "dep", PackLevel.PROJECT, depends("01ORG", ">=1.0")), org)));
        fixtures.put(
                code(RefusalCode.PACK_NAME_CONFLICT),
                new ClosureFixture(
                        List.of(org, pack("01TWO", "org", PackLevel.ORG, List.of())),
                        List.of(org, pack("01TWO", "two", PackLevel.ORG, List.of()))));
        fixtures.put(
                code(RefusalCode.PACK_DUPLICATE_ID),
                new ClosureFixture(
                        List.of(
                                org,
                                new PackUnderResolution(
                                        configured("twin", PackLevel.ORG),
                                        descriptor("01ORG", "twin", PackLevel.ORG, List.of()))),
                        List.of(org)));
        fixtures.put(
                code(RefusalCode.PACK_SIBLING_CONFLICT),
                new ClosureFixture(
                        List.of(refining("01LEFT", "left", PackLevel.ORG), refining("01RIGHT", "right", PackLevel.ORG)),
                        List.of(
                                refining("01LEFT", "left", PackLevel.ORG),
                                refining("01RIGHT", "right", PackLevel.PROJECT))));
        return fixtures;
    }

    static Stream<ReasonCode> closureCodes() {
        return PackCodes.CLOSURE.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("closureCodes")
    @DisplayName("each CLOSURE code refuses a set of packs, and its twin passes")
    void eachClosureCodeHasAFailingAndAPassingSet(ReasonCode code) {
        ClosureFixture fixture = CLOSURE_FIXTURES.get(code);
        assertThat(fixture).as("no set of packs stands for %s", code).isNotNull();

        assertThat(CompositionRules.closure().apply(fixture.failing()))
                .extracting(Reason::code)
                .as("the failing set of %s", code)
                .contains(code);
        assertThat(CompositionRules.closure().apply(fixture.passing()))
                .extracting(Reason::code)
                .as("the passing set of %s", code)
                .doesNotContain(code);
    }

    // ---- fixtures ------------------------------------------------------------------------------------------------

    private static ReasonCode code(RefusalCode refusal) {
        return new ReasonCode(refusal.token(), Severity.VIOLATION);
    }

    private static List<PackDependency> depends(String packId, String range) {
        return List.of(new PackDependency(packId, range));
    }

    private static ConfiguredPack configured(String id, PackLevel level) {
        return new ConfiguredPack(new PackIdentifier(id), level);
    }

    private static PackDescriptor descriptor(
            String id, String name, PackLevel level, List<PackDependency> dependencies) {
        return new PackDescriptor(
                id,
                "1.0.0",
                Optional.empty(),
                name,
                Optional.of(level),
                Optional.empty(),
                PackPaths.none(),
                dependencies);
    }

    private static PackUnderResolution pack(
            String id, String name, PackLevel level, List<PackDependency> dependencies) {
        return new PackUnderResolution(configured(id, level), descriptor(id, name, level, dependencies));
    }

    private static PackUnderResolution extending(String id, String name, PackLevel level, String parent) {
        return new PackUnderResolution(
                configured(id, level),
                new PackDescriptor(id, "1.0.0", Optional.of(parent), name, Optional.of(level), Optional.empty()));
    }

    private static PackUnderResolution refining(String id, String name, PackLevel level) {
        return new PackUnderResolution(
                configured(id, level), descriptor(id, name, level, List.of()), List.of("directive:DIRECTIVE_009"));
    }
}
