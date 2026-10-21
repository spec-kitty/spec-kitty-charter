package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.CharterPack;
import ai.speckitty.charter.packs.domain.Diagnostic;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackFinding;
import ai.speckitty.charter.packs.domain.PackFindingCode;
import ai.speckitty.charter.packs.domain.PackInspector.Scope;
import ai.speckitty.charter.packs.domain.PackPolicy;
import ai.speckitty.charter.packs.domain.PackRoot;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * I2 across scopes (research R10): a load of a pack and the full validation of it, restricted to the files a load
 * reads, must tell the same story, for every open-packs pack, every planted pack and every load fixture. The load
 * refuses with the first blocking finding of the restricted validation, code and text, or it loads with exactly the
 * restricted warnings as its diagnostics. The full validation may only add findings outside the load path.
 */
class ScopeParityTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final FileSystemPackInspector INSPECTOR = new FileSystemPackInspector();

    /**
     * Compares the load of one pack with its full validation.
     *
     * @param restrictToLoadPath whether the full validation is restricted to the load path; {@code false} is the
     *     test-only variant that proves the restriction is what the parity rests on
     * @return what differs, or empty when the two agree
     */
    static Optional<String> mismatch(PackLocation location, boolean restrictToLoadPath) {
        PackRoot root = new PackRoot(
                location.name(), Optional.of(location.level()), location.root().toString());
        PackContents loadContents = INSPECTOR.inspect(root, Scope.LOAD_PATH);
        PackContents full = INSPECTOR.inspect(root, Scope.FULL);
        List<String> loadPath = PackPolicy.loadPath(full);
        List<PackFinding> judged = PackPolicy.judge(full).findings();
        List<PackFinding> restricted =
                judged.stream().filter(f -> loadPath.contains(f.file())).toList();
        List<PackFinding> compared = restrictToLoadPath ? restricted : judged;

        if (!restricted.equals(PackPolicy.judge(loadContents).findings())) {
            return Optional.of("the load-path read and the full read judge the load path differently: "
                    + PackPolicy.judge(loadContents).findings() + " versus " + restricted);
        }
        Optional<PackFinding> firstBlocking = compared.stream()
                .filter(f -> f.severity() == PackFindingCode.Severity.BLOCKING)
                .findFirst();
        try {
            CharterPack loaded = FileSystemCharterPackRepository.loadPack(location);
            if (firstBlocking.isPresent()) {
                return Optional.of("the load succeeded but the validation blocks with " + firstBlocking.get());
            }
            List<String> diagnostics =
                    loaded.diagnostics().stream().map(ScopeParityTest::describe).toList();
            List<String> warnings =
                    compared.stream().map(f -> describe(f.toDiagnostic())).toList();
            return diagnostics.equals(warnings)
                    ? Optional.empty()
                    : Optional.of("diagnostics " + diagnostics + " differ from the warnings " + warnings);
        } catch (CharterRefusal refusal) {
            if (firstBlocking.isEmpty()) {
                return Optional.of("the load refused with " + refusal.getMessage() + " but the validation does not");
            }
            PackFinding expected = firstBlocking.get();
            boolean same = refusal.code() == expected.code().refusal()
                    && refusal.getMessage().equals(expected.detail());
            return same
                    ? Optional.empty()
                    : Optional.of("the load refused with " + refusal.code().token() + ": " + refusal.getMessage()
                            + " but the validation's first blocking finding is " + expected);
        }
    }

    private static String describe(Diagnostic diagnostic) {
        return diagnostic.code().token() + ": " + diagnostic.detail();
    }

    // ---- planted packs and load fixtures -------------------------------------------------------------------------

    static List<String> plants() throws IOException {
        return PlantedFilesTest.plantsWithFiles();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("plants")
    void aPlantedPackLoadsAsItsRestrictedValidationSays(String id, @TempDir Path tmp) throws IOException {
        JsonNode load = PlantedFilesTest.expected(id).path("load");
        PackLevel level = load.has("level")
                ? PackLevel.fromToken(load.get("level").asString()).orElseThrow()
                : PlantedFilesTest.configuredLevel(id).orElse(PackLevel.BUILT_IN);
        Path pack = PlantedFilesTest.materialize(id, tmp);

        assertThat(mismatch(new PackLocation("qa", level, pack), true)).as(id).isEmpty();
    }

    static List<String> loadFixtures() throws IOException {
        return LoadOutcomeTest.cases();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("loadFixtures")
    void aLoadFixtureLoadsAsItsRestrictedValidationSays(String name) throws IOException, URISyntaxException {
        Path dir = Path.of(ScopeParityTest.class.getResource("/load-fixtures").toURI())
                .resolve(name);
        JsonNode expected = JSON.readTree(Files.readString(dir.resolve("expected.json"), StandardCharsets.UTF_8));
        PackLevel level = PackLevel.fromToken(expected.get("pack").get("level").asString())
                .orElseThrow();
        PackLocation location =
                new PackLocation(expected.get("pack").get("name").asString(), level, dir.resolve("files"));

        assertThat(mismatch(location, true)).as(name).isEmpty();
    }

    // ---- the parity bites ----------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("findingsOutsideTheLoadPath")
    void withoutTheLoadPathRestrictionAPackWhoseOnlyFindingIsOutsideItFailsTheParity(String id, @TempDir Path tmp)
            throws IOException {
        Path pack = PlantedFilesTest.materialize(id, tmp);
        PackLocation location = new PackLocation("qa", PackLevel.BUILT_IN, pack);

        assertThat(mismatch(location, true))
                .as("restricted: the load never reads the file")
                .isEmpty();
        assertThat(mismatch(location, false))
                .as("unrestricted: the validation blocks on a file the load does not read")
                .isPresent()
                .get()
                .asString()
                .contains("the load succeeded but the validation blocks");
    }

    static Stream<String> findingsOutsideTheLoadPath() {
        return Stream.of("duplicate-step-id", "duplicate-artifact-id", "asset-path-escape-dot-dot", "charter-invalid");
    }

    // ---- the open-packs corpus -----------------------------------------------------------------------------------

    @Tag("open-packs")
    @Test
    void everyOpenPacksPackLoadsAsItsRestrictedValidationSays() throws IOException {
        OpenPacksCorpus.assertPinned();
        List<String> mismatches = new ArrayList<>();
        List<PackLocation> packs = OpenPacksCorpus.packs();
        for (PackLocation pack : packs) {
            mismatch(pack, true).ifPresent(m -> mismatches.add(pack.name() + ": " + m));
        }
        assertThat(packs).as("the packs of the corpus").hasSize(OpenPacksCorpus.DIAGNOSTICS.size());
        assertThat(mismatches).isEmpty();
    }
}
