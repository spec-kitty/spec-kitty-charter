package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.AntiPattern;
import ai.speckitty.charter.packs.domain.AntiPatternSeverity;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The anti-pattern's binding (ADR-0052 decision 8, as amended by ADR-0053 decision 30; #154): a file reads into the DSO
 * and the entity and writes back byte for byte in schema key order; the stem and placement rule applies.
 */
@DisplayName("An anti-pattern round-trips from its file (#154)")
class AntiPatternBindingTest {

    private static final String PACK = "anti-pattern";
    private static final PackIdentifier PACK_ID = new PackIdentifier(PACK);
    private static final PackDirectory PACK_DIRECTORY = PackDirectory.of(PACK_ID);
    private static final String FILE = "anti_patterns/clever-over-direct.anti-pattern.yaml";

    /** Every field, in schema key order, as the canonical writer emits it. */
    private static final String FULL = """
            schema_version: '1.0'
            id: clever-over-direct
            title: Clever over direct
            description: Using a clever language feature when a direct, familiar idiom would be easier to review.
            severity: smell
            bad_example: result = next((v for v in xs if pred(v)), None)
            good_example: write the loop that finds the first match
            """;

    private static final String DESCRIPTOR = """
            pack_id: 01M7Q0R0ANT1PATTERN0000000
            pack_version: 1.0.0
            name: anti-pattern
            pack_level: org
            """;

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    @TempDir
    Path host;

    private Path pack(String file, String text) throws IOException {
        Path pack = host.resolve(PACK);
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.yaml"), DESCRIPTOR);
        Path at = pack.resolve(file);
        Files.createDirectories(at.getParent());
        Files.writeString(at, text);
        return pack;
    }

    private AntiPatternDso dso() {
        return new FilesystemPackStore(host, strategy)
                .read(PACK_DIRECTORY, List.of(AntiPatternBinding.INSTANCE))
                .dsos(AntiPatternBinding.INSTANCE)
                .getFirst();
    }

    private Pack load(Path pack) {
        return new DomainRepositoryService(() -> List.of(new PackLocation(PACK, PackLevel.ORG, pack)), strategy)
                .load(PACK_ID);
    }

    private static List<AntiPattern> antiPatterns(Pack pack) {
        return pack.artifacts(ArtifactKind.ANTI_PATTERN, AntiPattern.class);
    }

    @Test
    @DisplayName("the file reads into the DSO and the entity, every field carried")
    void theFileReadsIntoTheDomain() throws IOException {
        Pack loaded = load(pack(FILE, FULL));

        assertThat(loaded.violations()).isEmpty();
        assertThat(loaded.antiPatterns()).singleElement().satisfies(antiPattern -> {
            assertThat(antiPattern.localId()).isEqualTo("clever-over-direct");
            assertThat(antiPattern.packId()).isEqualTo(PACK_ID);
            assertThat(antiPattern.title()).isEqualTo("Clever over direct");
            assertThat(antiPattern.description())
                    .contains("Using a clever language feature when a direct, familiar"
                            + " idiom would be easier to review.");
            assertThat(antiPattern.severity()).isEqualTo(AntiPatternSeverity.SMELL);
            assertThat(antiPattern.badExample()).isEqualTo("result = next((v for v in xs if pred(v)), None)");
            assertThat(antiPattern.goodExample()).contains("write the loop that finds the first match");
        });
    }

    @Test
    @DisplayName("the file writes back byte for byte, in schema key order, through the production writer")
    void theFileWritesBackByteForByte() throws IOException {
        Path pack = pack(FILE, FULL);
        AntiPatternDso dso = dso();
        AntiPattern antiPattern = AntiPatternBinding.INSTANCE.toEntity(PACK_ID, dso);
        assertThat(AntiPatternBinding.INSTANCE.toDso(antiPattern))
                .as("DSO fidelity")
                .isEqualTo(dso);

        Files.delete(pack.resolve(FILE));
        new FilesystemPackStore(host, strategy).write(PACK_DIRECTORY, AntiPatternBinding.INSTANCE, dso);

        assertThat(pack.resolve(FILE)).hasContent(FULL);
    }

    @Test
    @DisplayName("an absent good_example stays absent: the read holds none and the write emits no key")
    void anAbsentGoodExampleStaysAbsent() throws IOException {
        String withoutGood = FULL.replace("good_example: write the loop that finds the first match\n", "");
        Path pack = pack(FILE, withoutGood);
        AntiPatternDso dso = dso();
        assertThat(dso.goodExample()).isEmpty();
        assertThat(AntiPatternBinding.INSTANCE.toEntity(PACK_ID, dso).goodExample())
                .isEmpty();

        Files.delete(pack.resolve(FILE));
        new FilesystemPackStore(host, strategy).write(PACK_DIRECTORY, AntiPatternBinding.INSTANCE, dso);

        assertThat(pack.resolve(FILE)).hasContent(withoutGood);
    }

    @Test
    @DisplayName("multi-line examples and every severity survive the writer and the reader")
    void multiLineTextAndEverySeverityRoundTrip() throws IOException {
        for (AntiPatternSeverity severity : AntiPatternSeverity.values()) {
            AntiPatternDso written = new AntiPatternDso(
                    "1.0",
                    "clever-over-direct",
                    "Clever over direct",
                    "Using a clever feature.\n",
                    severity.token(),
                    "result = next((v for v in xs if pred(v)), None)\n",
                    Optional.of("result = None\nfor v in xs:\n    if pred(v):\n        result = v\n        break\n"));
            Path pack = pack(FILE, "placeholder\n");
            Files.delete(pack.resolve(FILE));
            new FilesystemPackStore(host, strategy).write(PACK_DIRECTORY, AntiPatternBinding.INSTANCE, written);

            assertThat(dso()).as(severity.token()).isEqualTo(written);
            assertThat(AntiPatternBinding.INSTANCE.toEntity(PACK_ID, written).severity())
                    .isEqualTo(severity);
        }
    }

    @Test
    @DisplayName("an unknown severity token is refused: the schema at load, the binding for a DSO that bypassed it")
    void anUnknownSeverityIsRefused() throws IOException {
        Pack loaded = load(pack(FILE, FULL.replace("severity: smell", "severity: minor")));

        assertThat(loaded.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.file()).isEqualTo(FILE);
            assertThat(violation.kind()).isEqualTo(PackCodes.ARTIFACT_SCHEMA_ERROR);
        });
        assertThat(antiPatterns(loaded)).isEmpty();

        AntiPatternDso minor =
                new AntiPatternDso("1.0", "clever-over-direct", "t", "d", "minor", "b", Optional.empty());
        assertThatThrownBy(() -> AntiPatternBinding.INSTANCE.toEntity(PACK_ID, minor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minor");
    }

    @Test
    @DisplayName("an anti-pattern's file is anti_patterns/<id>.anti-pattern.yaml, its id the file stem, spelled"
            + " anti-pattern, with no companion")
    void theFileNameIsTheId() {
        assertThat(AntiPatternBinding.INSTANCE.kind()).isEqualTo(ArtifactKind.ANTI_PATTERN);
        assertThat(AntiPatternBinding.INSTANCE.fileName("clever-over-direct")).isEqualTo(FILE);
        assertThat(AntiPatternBinding.INSTANCE.fileToken()).isEqualTo("anti-pattern");
        assertThat(AntiPatternBinding.INSTANCE.schemaName()).isEqualTo("anti-pattern");
        assertThat(AntiPatternBinding.INSTANCE.idIsFileStem()).isTrue();
        assertThat(AntiPatternBinding.INSTANCE.placedAtItsFileName()).isTrue();
        assertThat(AntiPatternBinding.INSTANCE.hasCompanions()).isFalse();
    }

    @Test
    @DisplayName("the stem rule refuses an id that is not the file stem, and the placement rule a file outside"
            + " anti_patterns/ (FileStems)")
    void theStemAndPlacementRulesApply() {
        assertThat(FileStems.mismatch(AntiPatternBinding.INSTANCE, FILE, "clever-over-direct"))
                .isEmpty();
        assertThat(FileStems.mismatch(AntiPatternBinding.INSTANCE, FILE, "other"))
                .contains("anti_pattern id 'other' is not its file stem 'clever-over-direct'");
        assertThat(FileStems.mismatch(
                        AntiPatternBinding.INSTANCE,
                        "styleguides/clever-over-direct.anti-pattern.yaml",
                        "clever-over-direct"))
                .contains("anti_pattern 'clever-over-direct' is not at its place '" + FILE + "'");
    }

    @Test
    @DisplayName("a load refuses a misnamed and a misplaced file, holding neither")
    void aLoadRefusesAMisnamedAndAMisplacedFile() throws IOException {
        Pack misnamed = load(pack(FILE, FULL.replace("id: clever-over-direct", "id: other")));
        assertThat(misnamed.violations())
                .extracting(PackElementViolation::file, PackElementViolation::kind, PackElementViolation::reason)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        FILE,
                        PackCodes.ARTIFACT_SCHEMA_ERROR,
                        "anti_pattern id 'other' is not its file stem 'clever-over-direct'"));
        assertThat(antiPatterns(misnamed)).isEmpty();

        Files.delete(host.resolve(PACK).resolve(FILE));
        String misplacedFile = "clever-over-direct.anti-pattern.yaml";
        Pack misplaced = load(pack(misplacedFile, FULL));
        assertThat(misplaced.violations())
                .extracting(PackElementViolation::file, PackElementViolation::reason)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        misplacedFile, "anti_pattern 'clever-over-direct' is not at its place '" + FILE + "'"));
        assertThat(antiPatterns(misplaced)).isEmpty();
    }
}
