package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Validate mode (FR-010) over a fake reading: the root it reads and the verdict it returns. */
class PackValidationTest {

    private static final FileRead<RawGraph> EMPTY_FRAGMENT = new FileRead.Read<>(
            "drg/fragment.yaml", new RawGraph("drg/fragment.yaml", RawGraph.Shape.FRAGMENT, List.of(), List.of()));

    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            "01ARWG13C00000000000001ABC",
            "1.0.0",
            Optional.empty(),
            "qa",
            Optional.of(PackLevel.ORG),
            Optional.empty());

    /** A reading that records the root it was asked for and answers with a fixed readout. */
    private static final class FakeReading implements PackReading {
        private final PackReadout answer;
        private final List<PackRoot> roots = new ArrayList<>();

        FakeReading(PackReadout answer) {
            this.answer = answer;
        }

        @Override
        public PackReadout read(PackRoot root) {
            roots.add(root);
            return answer;
        }
    }

    private static PackReadout facts(
            PackRoot root, List<MissionTypeFacts> types, List<FileRead.Unreadable<?>> unreadable) {
        PackContents contents = new PackContents(
                root,
                new FileRead.Read<>("pack.yaml", DESCRIPTOR),
                List.of(),
                EMPTY_FRAGMENT,
                List.of(),
                List.of(),
                StepEnhancementFacts.none(),
                List.of(),
                types);
        return Readouts.of(
                contents,
                List.of(new ClassifiedFile("pack.yaml", Optional.of("pack-descriptor"), Optional.empty())),
                unreadable,
                List.of());
    }

    /** A mission type whose one step {@code s} declares the substeps with these ids; no step id, so no step link. */
    private static MissionTypeFacts type(String file, String... substepIds) {
        List<MissionTypeFacts.SubstepFacts> substeps = new ArrayList<>();
        for (int i = 0; i < substepIds.length; i++) {
            substeps.add(new MissionTypeFacts.SubstepFacts("steps", 0, "s", i, substepIds[i], Optional.empty()));
        }
        return new MissionTypeFacts(file, "rx", List.of(), substeps);
    }

    private static PackRoot root(Optional<PackLevel> level) {
        return new PackRoot("qa", level, "loc");
    }

    @Test
    void aValidationReadsTheWholePackAndReturnsTheVerdictOfWhatWasRead() {
        PackRoot root = root(Optional.of(PackLevel.ORG));
        List<MissionTypeFacts> types = List.of(type("rx.mission-type.yaml", "a", "b", "a"));
        FakeReading inspector = new FakeReading(facts(root, types, List.of()));

        PackVerdict verdict = new PackValidation(inspector).validate(root);

        assertThat(inspector.roots).containsExactly(root);
        assertThat(verdict.files()).extracting(ClassifiedFile::file).containsExactly("pack.yaml");
        assertThat(verdict.findings())
                .containsExactly(new PackFinding(
                        PackCodes.DUPLICATE_STEP_ID,
                        "rx.mission-type.yaml",
                        List.of("steps", "0", "substeps", "2", "id"),
                        "rx.mission-type.yaml: substep id 'a' is declared more than once in the step 's'"));
    }

    @Test
    void theVerdictIsExactlyWhatThePolicyJudgesOfTheSameFacts() {
        PackRoot root = root(Optional.of(PackLevel.ORG));
        PackReadout contents = facts(
                root,
                List.of(type("rx.mission-type.yaml", "x", "x")),
                List.of(new FileRead.Unreadable<Object>(
                        "tactics/a.tactic.yaml",
                        new ParseFailure("tactics/a.tactic.yaml", "while parsing"),
                        List.of())));

        assertThat(new PackValidation(new FakeReading(contents)).validate(root)).isEqualTo(PackPolicy.judge(contents));
    }

    @Test
    void aFileThatDoesNotParseOrBreaksItsSchemaIsAFindingOfTheCodeOfItsKind() {
        PackRoot root = root(Optional.of(PackLevel.ORG));
        SchemaViolation missing = new SchemaViolation(
                "tactics/b.tactic.yaml", List.of(), "required", Optional.of("id"), Optional.empty(), Map.of());
        PackReadout contents = facts(
                root,
                List.of(),
                List.of(
                        new FileRead.Unreadable<Object>(
                                "tactics/a.tactic.yaml",
                                new ParseFailure("tactics/a.tactic.yaml", "while parsing a block mapping"),
                                List.of()),
                        new FileRead.Unreadable<Object>("tactics/b.tactic.yaml", null, List.of(missing)),
                        new FileRead.Unreadable<Object>(
                                "org-charter.yaml",
                                ParseFailure.refused(
                                        "org-charter.yaml", "org enforcement is a preset (#431)", StorageCodes.RETIRED),
                                List.of())));

        List<PackFinding> findings =
                new PackValidation(new FakeReading(contents)).validate(root).findings();

        assertThat(findings)
                .extracting(PackFinding::code, PackFinding::file, PackFinding::severity)
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple(
                                StorageCodes.UNREADABLE, "tactics/a.tactic.yaml", Severity.VIOLATION),
                        org.assertj.core.api.Assertions.tuple(
                                PackFindingCode.ARTIFACT_SCHEMA_ERROR, "tactics/b.tactic.yaml", Severity.VIOLATION),
                        org.assertj.core.api.Assertions.tuple(
                                StorageCodes.RETIRED, "org-charter.yaml", Severity.VIOLATION));
        assertThat(findings)
                .extracting(PackFinding::detail)
                .contains(
                        "tactics/a.tactic.yaml: while parsing a block mapping",
                        "tactics/b.tactic.yaml: missing 'id'",
                        "org-charter.yaml: org enforcement is a preset (#431)");
    }

    @Test
    void aBarePathIsJudgedWithoutAConfiguredLevel() {
        PackRoot bare = root(Optional.empty());
        PackReadout contents = Readouts.of(new PackContents(
                bare,
                new FileRead.Absent<>("pack.yaml"),
                List.of(),
                new FileRead.Absent<>("drg/fragment.yaml"),
                List.of(),
                List.of(),
                StepEnhancementFacts.none(),
                List.of(),
                List.of()));

        assertThat(new PackValidation(new FakeReading(contents)).validate(bare).findings())
                .as("no level to compare with, so neither pack_level_missing nor org_fragment_missing")
                .isEmpty();
    }

    @Test
    void aReadingIsRequired() {
        assertThatThrownBy(() -> new PackValidation(null)).isInstanceOf(NullPointerException.class);
    }
}
