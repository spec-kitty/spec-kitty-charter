package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.domain.CharterPack;
import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.PackFinding;
import ai.speckitty.charter.domain.PackFindingCode;
import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.domain.PackRoot;
import ai.speckitty.charter.domain.PackValidation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * One planted invalid file for every change made on import and every domain rule, each failing with its code (FR-012,
 * SC-002), and each cross-checked against core's own verdict.
 *
 * <p>Each directory of {@code planted/} is one pack: its files under {@code files/} and {@code expected.json}, written
 * from the spec and never from a run of the code. {@code planted/core-verdicts.json} holds, per plant, core's raw
 * {@code charter pack validate --json} output as {@code scripts/packs/core_verdicts.py} recorded it. Agreement is never
 * read from that file: this test maps core's raw findings through the FR-016 mapping of
 * {@code contracts/pack-finding-codes.md}, compares the set of {@code (file, code, severity)} with the service's own,
 * and calls the result {@code agree} or {@code differ}. A difference must be a recorded divergence: an {@code open}
 * entry of {@code docs/conformance/divergences.yaml}. A divergence the register does not list yet is named in
 * {@link #PROPOSED}, and only until it is.
 */
class PlantedFilesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CORE_PIN = "9554e7b26afe7a61519f7744d0147346ab36f197";

    /**
     * Divergences found by a planted-file comparison that the register does not list yet. An id here is accepted only
     * while the register lacks it; once the register has it, the register's entry counts. Empty: DIV-020..025 are
     * registered.
     */
    static final Map<String, String> PROPOSED = Map.of();

    /** Core's findings that need the built-in layer or are outside this mission (contracts/pack-finding-codes.md). */
    private static final Set<String> EXCLUDED_CATEGORIES = Set.of(
            "drg_external_endpoint",
            "drg_kind_drift",
            "profile_skipped",
            "intent_conflict",
            "unknown_target",
            "same_id_collision",
            "pack_sanction",
            "drg_root_graph_missing",
            "duplicate_drg_edge",
            "drg_builtin_unavailable",
            "org_charter_unchecked");

    /** What a finding is for the comparison: place and message text are never compared (core has neither). */
    record Verdict(String file, String code, String severity) {}

    // ---- the plants ----------------------------------------------------------------------------------------------

    static Path planted() {
        try {
            return Path.of(PlantedFilesTest.class.getResource("/planted").toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<String> plants() throws IOException {
        try (Stream<Path> dirs = Files.list(planted())) {
            return dirs.filter(Files::isDirectory)
                    .map(d -> d.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    /** The plants that have files; the others are proven by a named test. */
    static List<String> plantsWithFiles() throws IOException {
        return plants().stream().filter(id -> !expected(id).has("proven_by")).toList();
    }

    static JsonNode expected(String id) {
        try {
            return JSON.readTree(
                    Files.readString(planted().resolve(id).resolve("expected.json"), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Copies a plant's files into {@code into}, makes its symlinks, and returns the pack directory. */
    static Path materialize(String id, Path into) throws IOException {
        Path pack = Files.createDirectories(into.resolve(id));
        Path files = planted().resolve(id).resolve("files");
        try (Stream<Path> walk = Files.walk(files)) {
            for (Path source : walk.toList()) {
                Path target = pack.resolve(files.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                }
            }
        }
        for (JsonNode link : expected(id).path("symlinks")) {
            Path at = pack.resolve(link.get("link").asString());
            Files.createDirectories(at.getParent());
            Files.createSymbolicLink(at, Path.of(link.get("target").asString()));
        }
        return pack;
    }

    /** The level a plant is configured at when it is validated, or empty for a bare path. */
    static Optional<PackLevel> configuredLevel(String id) {
        return Optional.ofNullable(expected(id).get("configuredLevel"))
                .flatMap(level -> PackLevel.fromToken(level.asString()));
    }

    static PackRoot rootOf(String id, Path pack) {
        return new PackRoot("qa", configuredLevel(id), pack.toString());
    }

    // ---- every plant fails as expected ---------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("plantsWithFiles")
    void validatingAPlantYieldsExactlyItsExpectedFindings(String id, @TempDir Path tmp) throws IOException {
        Path pack = materialize(id, tmp);

        List<PackFinding> findings = new PackValidation(new FileSystemPackInspector())
                .validate(rootOf(id, pack))
                .findings();

        Set<String> actual = new LinkedHashSet<>();
        findings.forEach(
                f -> actual.add(describe(f.code().name().toLowerCase(Locale.ROOT), f.severity(), f.file(), f.place())));
        Set<String> wanted = new LinkedHashSet<>();
        for (JsonNode f : expected(id).get("findings")) {
            List<String> place = new ArrayList<>();
            f.get("place").forEach(p -> place.add(p.asString()));
            wanted.add(describe(
                    f.get("code").asString(),
                    PackFindingCode.Severity.valueOf(
                            f.get("severity").asString().toUpperCase(Locale.ROOT)),
                    f.get("file").asString(),
                    place));
        }
        assertThat(actual).as("findings of %s:%n%s", id, findings).containsExactlyInAnyOrderElementsOf(wanted);
    }

    private static String describe(String code, PackFindingCode.Severity severity, String file, List<String> place) {
        return code + " " + severity + " " + file + " /" + String.join("/", place);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("plantsWithFiles")
    void loadingALoadPathPlantRefusesWithItsCodeOrLoadsWithItsDiagnostics(String id, @TempDir Path tmp)
            throws IOException {
        JsonNode load = expected(id).get("load");
        if (load == null) {
            return;
        }
        Path pack = materialize(id, tmp);
        PackLevel level = PackLevel.fromToken(load.get("level").asString()).orElseThrow();
        FileSystemCharterPackRepository repository =
                new FileSystemCharterPackRepository(() -> List.of(new PackLocation("qa", level, pack)));

        if (load.has("refusal")) {
            org.assertj.core.api.Assertions.assertThatThrownBy(repository::load)
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            r -> assertThat(r.code().token())
                                    .isEqualTo(load.get("refusal").asString()));
            return;
        }
        CharterPack loaded = repository.load().packs().get(0);
        List<String> wanted = new ArrayList<>();
        load.path("diagnostics").forEach(d -> wanted.add(d.asString()));
        assertThat(loaded.diagnostics()).extracting(d -> d.code().token()).containsExactlyElementsOf(wanted);
    }

    // ---- every change and rule has a plant -----------------------------------------------------------------------

    @Test
    void everyChangeAndEveryRuleHasAPlantOrACitedProvingTest() throws IOException {
        Set<String> covered = new LinkedHashSet<>();
        Set<String> codes = new LinkedHashSet<>();
        for (String id : plants()) {
            expected(id).get("covers").forEach(c -> covered.add(c.asString()));
            expected(id).path("findings").forEach(f -> codes.add(f.get("code").asString()));
        }
        List<String> changes = new ArrayList<>();
        for (int c = 1; c <= 16; c++) {
            changes.add("C" + c);
        }
        assertThat(covered)
                .containsAll(changes)
                .contains(
                        "R15", "P2", "P7", "R3", "R16", "FR-009", "P8", "P10", "P11", "R14", "R7", "R8", "R13", "R5",
                        "R18");
        assertThat(codes)
                .contains(
                        "requires_core_invalid",
                        "org_fragment_missing",
                        "duplicate_step_id",
                        "duplicate_artifact_id",
                        "asset_path_escape",
                        "asset_mime_invalid",
                        "pack_unreadable",
                        "drg_schema_error",
                        "artifact_schema_error",
                        "org_charter_schema_error",
                        "pack_level_missing",
                        "pack_unknown_key");
        assertThat(plants())
                .contains(
                        "asset-path-escape-through-a-symlink",
                        "asset-path-symlink-loop",
                        "asset-repeated-id-only-the-first-owner-is-checked",
                        "c12-impacts-edge-without-impacts",
                        "c12-extraneous-impacts-on-a-suggests-edge",
                        "c12-impacts-not-a-number",
                        "c12-impacts-out-of-range",
                        "c12-edge-impacts-valid",
                        "charter-valid",
                        "charter-invalid",
                        "control-unknown-pack-key-is-a-warning",
                        "control-retired-pack-key-is-a-warning",
                        "control-no-generated-by-and-no-pack-level",
                        "r15-glossary-urn-in-a-shard",
                        "r15-glossary-urn-as-an-edge-endpoint");
    }

    @Test
    void aPlantWithoutFilesNamesTheTestsThatProveItAndEachOfThemExists() throws IOException {
        Path repository = repositoryRoot();
        int proven = 0;
        for (String id : plants()) {
            JsonNode provenBy = expected(id).get("proven_by");
            assertThat(Files.isDirectory(planted().resolve(id).resolve("files")))
                    .as("%s has files or is proven by a test, not both", id)
                    .isEqualTo(provenBy == null);
            if (provenBy == null) {
                continue;
            }
            proven++;
            assertThat(provenBy.size()).as("%s cites a test", id).isPositive();
            for (JsonNode cite : provenBy) {
                assertThat(citationExists(repository, cite.asString()))
                        .as("%s cites %s", id, cite)
                        .isTrue();
            }
        }
        assertThat(proven).as("C1, C13, C14 and C15").isEqualTo(4);
    }

    /** {@code module:Class#method} names a test method in that module; {@code plant:<id>} names another plant. */
    private static boolean citationExists(Path repository, String cite) throws IOException {
        if (cite.startsWith("plant:")) {
            return Files.isDirectory(planted().resolve(cite.substring("plant:".length())));
        }
        String module = cite.substring(0, cite.indexOf(':'));
        String type = cite.substring(cite.indexOf(':') + 1, cite.indexOf('#'));
        String method = cite.substring(cite.indexOf('#') + 1);
        Path tests = repository.resolve("service").resolve(module).resolve("src/test/java");
        try (Stream<Path> files = Files.walk(tests)) {
            Optional<Path> source = files.filter(p -> p.getFileName().toString().equals(type + ".java"))
                    .findFirst();
            return source.isPresent() && Files.readString(source.get()).contains("void " + method + "(");
        }
    }

    // ---- core's verdict, recorded and derived --------------------------------------------------------------------

    private static Path repositoryRoot() {
        String vendored = System.getProperty("charter.vendoredMd");
        if (vendored == null) {
            throw new IllegalStateException("charter.vendoredMd is not set by the build");
        }
        // <root>/contracts/packs/VENDORED.md
        return Path.of(vendored).getParent().getParent().getParent();
    }

    private static List<JsonNode> records() throws IOException {
        JsonNode all = JSON.readTree(Files.readString(planted().resolve("core-verdicts.json"), StandardCharsets.UTF_8));
        List<JsonNode> records = new ArrayList<>();
        all.forEach(records::add);
        return records;
    }

    @Test
    void everyPlantHasExactlyOneCoreRecordAndEveryRecordExactlyOnePlant() throws IOException {
        List<String> recorded =
                records().stream().map(r -> r.get("plant").asString()).toList();

        assertThat(recorded).doesNotHaveDuplicates();
        assertThat(recorded).containsExactlyInAnyOrderElementsOf(plants());
        for (JsonNode record : records()) {
            String id = record.get("plant").asString();
            assertThat(record.has("proven_by"))
                    .as("%s: a record with no core run says which tests prove the plant", id)
                    .isEqualTo(expected(id).has("proven_by"));
            assertThat(record.get("core").isNull()).as(id).isEqualTo(record.has("proven_by"));
            if (!record.get("core").isNull()) {
                assertThat(record.get("core").get("sha").asString()).as(id).isEqualTo(CORE_PIN);
                assertThat(record.get("core").get("command").get(0).asString()).isEqualTo("spec-kitty");
                assertThat(record.get("core").get("raw").has("errors")).as(id).isTrue();
            }
        }
    }

    /**
     * The FR-016 mapping: core's raw findings as the set of {@code (file, code, severity)}. The code is chosen by the
     * mapping table; the severity is core's own (an error is blocking, an advisory a warning), so that a rule the
     * service is laxer about, such as an unknown {@code pack.yaml} key, differs and has to be a recorded divergence.
     */
    static Set<Verdict> mapCore(JsonNode raw) {
        Set<Verdict> mapped = new LinkedHashSet<>();
        for (String list : List.of("errors", "advisories")) {
            for (JsonNode issue : raw.get(list)) {
                mapCoreIssue(issue).ifPresent(mapped::add);
            }
        }
        return mapped;
    }

    private static Optional<Verdict> mapCoreIssue(JsonNode issue) {
        String category = issue.has("category") ? issue.get("category").asString() : "";
        if (EXCLUDED_CATEGORIES.contains(category) || category.startsWith("preset_")) {
            return Optional.empty();
        }
        String type = issue.get("artifact_type").asString();
        String file = issue.get("file").asString();
        String message = issue.get("message").asString();
        String severity = "error".equals(issue.get("severity").asString()) ? "blocking" : "warning";
        String code = switch (category) {
            case "PACK_LEVEL_MISSING" -> "pack_level_missing";
            case "PACK_LEVEL_MISMATCH" -> "pack_level_mismatch";
            case "RETIRED_PACK_FIELD" -> "pack_unknown_key";
            case "org_pack_missing" -> "org_fragment_missing";
            case "drg_dangling_edge" -> "drg_schema_error";
            case "duplicate_id" -> "duplicate_artifact_id";
            case "asset_path_escape", "asset_mime_invalid" -> category;
            default -> "";
        };
        if (code.isEmpty()) {
            code = schemaOrParseCode(type, file, message);
        }
        return Optional.of(new Verdict(file, code, severity));
    }

    /** A {@code schema_invalid}, {@code parse_error} or uncategorised error: the code follows the kind of the file. */
    private static String schemaOrParseCode(String type, String file, String message) {
        if ("pack.yaml".equals(file)) {
            if (message.contains("Extra inputs are not permitted") || message.contains("was removed")) {
                return "pack_unknown_key";
            }
            return message.contains("requires_core") ? "requires_core_invalid" : "pack_unreadable";
        }
        if ("drg".equals(type)) {
            return "drg_schema_error";
        }
        if ("org-charter".equals(type)) {
            return "org_charter_schema_error";
        }
        return message.contains("duplicate step IDs") ? "duplicate_step_id" : "artifact_schema_error";
    }

    /** The service's findings in the same terms; a repeated {@code (file, code, severity)} counts once, as core's. */
    private static Set<Verdict> ours(List<PackFinding> findings) {
        Set<Verdict> verdicts = new LinkedHashSet<>();
        findings.forEach(f -> verdicts.add(new Verdict(
                f.file(),
                f.code().name().toLowerCase(Locale.ROOT),
                f.severity().name().toLowerCase(Locale.ROOT))));
        return verdicts;
    }

    @Test
    void agreementIsDerivedFromCoresRawOutputAndEveryDifferenceIsARecordedOpenDivergence(@TempDir Path tmp)
            throws IOException {
        Map<String, String> register = openDivergences();
        List<String> problems = new ArrayList<>();
        Map<String, String> outcomes = new LinkedHashMap<>();
        for (JsonNode record : records()) {
            String id = record.get("plant").asString();
            if (record.get("core").isNull()) {
                continue;
            }
            Path pack = materialize(id, tmp);
            Set<Verdict> ours = ours(new PackValidation(new FileSystemPackInspector())
                    .validate(rootOf(id, pack))
                    .findings());
            Set<Verdict> core = mapCore(record.get("core").get("raw"));
            boolean agree = ours.equals(core);
            outcomes.put(id, agree ? "agree" : "differ");
            String registerId =
                    record.has("register_id") ? record.get("register_id").asString() : null;
            if (agree && registerId != null) {
                problems.add(id + ": agrees with core but cites " + registerId + " (a stale divergence)");
            } else if (!agree && registerId == null) {
                problems.add(id + ": differs from core with no register id; ours=" + ours + " core=" + core
                        + (blocks(core) && !blocks(ours) ? " (core blocks while we pass)" : ""));
            } else if (!agree && !register.containsKey(registerId) && !isProposed(registerId, register)) {
                problems.add(id + ": cites " + registerId + ", which is not an open divergence of the register");
            }
        }
        System.out.println("planted plants against core: " + outcomes);
        assertThat(problems).isEmpty();
        assertThat(outcomes.values()).as("both outcomes occur").contains("agree", "differ");
    }

    private static boolean blocks(Collection<Verdict> verdicts) {
        return verdicts.stream().anyMatch(v -> "blocking".equals(v.severity()));
    }

    private static boolean isProposed(String id, Map<String, String> register) {
        return PROPOSED.containsKey(id) && !register.containsKey(id);
    }

    /** The {@code open} entries of {@code docs/conformance/divergences.yaml}, by id. */
    private static Map<String, String> openDivergences() throws IOException {
        Path file = repositoryRoot().resolve("docs/conformance/divergences.yaml");
        Object parsed = Yaml.load(Files.readString(file, StandardCharsets.UTF_8));
        Map<String, String> open = new LinkedHashMap<>();
        for (Object entry : (List<?>) parsed) {
            Map<?, ?> row = (Map<?, ?>) entry;
            if ("open".equals(row.get("status"))) {
                open.put(String.valueOf(row.get("id")), String.valueOf(row.get("surface")));
            }
        }
        return open;
    }

    @Test
    void everyProposedDivergenceIsCitedByAPlantAndNoCitationNamesAnIdOutsideTheRegisterAndTheProposals()
            throws IOException {
        Map<String, String> register = openDivergences();
        Set<String> cited = new LinkedHashSet<>();
        for (JsonNode record : records()) {
            if (record.has("register_id")) {
                cited.add(record.get("register_id").asString());
            }
        }
        assertThat(cited).containsAll(PROPOSED.keySet());
        assertThat(cited)
                .allSatisfy(id -> assertThat(register.containsKey(id) || PROPOSED.containsKey(id))
                        .as(id)
                        .isTrue());
    }

    @Test
    void theMappingMapsCoresKnownShapesAndDropsWhatNeedsTheBuiltInLayer() throws IOException {
        JsonNode raw = JSON.readTree("""
                {"ok": false, "advisories": [
                  {"severity": "advisory", "artifact_type": "pack", "file": "pack.yaml", "message": "m", "category": "PACK_LEVEL_MISSING"},
                  {"severity": "advisory", "artifact_type": "directives", "file": "d.yaml", "message": "m", "category": "same_id_collision"}],
                 "errors": [
                  {"severity": "error", "artifact_type": "drg", "file": "pack.yaml", "message": "Org pack: Extra inputs are not permitted", "category": "schema_invalid"},
                  {"severity": "error", "artifact_type": "pack", "file": "pack.yaml", "message": "Extra inputs are not permitted", "category": "schema_invalid"},
                  {"severity": "error", "artifact_type": "pack", "file": "pack.yaml", "message": "field was removed", "category": "RETIRED_PACK_FIELD"},
                  {"severity": "error", "artifact_type": "pack", "file": "pack.yaml", "message": "requires_core is not valid", "category": "schema_invalid"},
                  {"severity": "error", "artifact_type": "pack", "file": "pack.yaml", "message": "x", "category": "parse_error"},
                  {"severity": "error", "artifact_type": "drg", "file": "drg/a.graph.yaml", "message": "failed to load"},
                  {"severity": "error", "artifact_type": "drg", "file": "drg", "message": "m", "category": "drg_root_graph_missing"},
                  {"severity": "error", "artifact_type": "mission_step_contracts", "file": "c.yaml", "message": "Value error, duplicate step IDs: ['a']", "category": "schema_invalid"},
                  {"severity": "error", "artifact_type": "tactics", "file": "t.yaml", "message": "x", "category": "schema_invalid"},
                  {"severity": "error", "artifact_type": "tactics", "file": "t.yaml", "message": "x", "category": "duplicate_id"},
                  {"severity": "error", "artifact_type": "org-charter", "file": "org-charter.yaml", "message": "x"}]}
                """);

        assertThat(mapCore(raw))
                .containsExactlyInAnyOrder(
                        new Verdict("pack.yaml", "pack_level_missing", "warning"),
                        new Verdict("pack.yaml", "pack_unknown_key", "blocking"),
                        new Verdict("pack.yaml", "requires_core_invalid", "blocking"),
                        new Verdict("pack.yaml", "pack_unreadable", "blocking"),
                        new Verdict("drg/a.graph.yaml", "drg_schema_error", "blocking"),
                        new Verdict("c.yaml", "duplicate_step_id", "blocking"),
                        new Verdict("t.yaml", "artifact_schema_error", "blocking"),
                        new Verdict("t.yaml", "duplicate_artifact_id", "blocking"),
                        new Verdict("org-charter.yaml", "org_charter_schema_error", "blocking"));
    }
}
