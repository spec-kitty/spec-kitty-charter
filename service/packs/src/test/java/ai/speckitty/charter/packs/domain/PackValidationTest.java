package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.PackContents.ClassifiedFile;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.PackContents.StepContractFacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Validate mode (FR-010) over a fake inspector: the scope it asks for and the verdict it returns. */
class PackValidationTest {

    private static final FileRead<RawGraph> EMPTY_FRAGMENT = new FileRead.Read<>(
            "drg/fragment.yaml", new RawGraph("drg/fragment.yaml", RawGraph.Shape.FRAGMENT, List.of(), List.of()));

    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            "01ABC", "1.0.0", Optional.empty(), "qa", Optional.of(PackLevel.ORG), Optional.empty(), List.of());

    /** An inspector that records the root and scope it was asked for and answers with fixed facts. */
    private static final class FakeInspector implements PackInspector {
        private final PackContents answer;
        private final List<Scope> scopes = new ArrayList<>();
        private final List<PackRoot> roots = new ArrayList<>();

        FakeInspector(PackContents answer) {
            this.answer = answer;
        }

        @Override
        public PackContents inspect(PackRoot root, Scope scope) {
            roots.add(root);
            scopes.add(scope);
            return answer;
        }
    }

    private static PackContents facts(
            PackRoot root, List<StepContractFacts> steps, List<FileRead.Unreadable<?>> unreadable) {
        return new PackContents(
                root,
                new FileRead.Read<>("pack.yaml", DESCRIPTOR),
                List.of(),
                EMPTY_FRAGMENT,
                List.of(new ClassifiedFile("pack.yaml", Optional.of("pack-descriptor"), Optional.empty())),
                steps,
                List.of(),
                List.of(),
                unreadable);
    }

    private static PackRoot root(Optional<PackLevel> level) {
        return new PackRoot("qa", level, "loc");
    }

    @Test
    void aValidationReadsTheWholePackAndReturnsTheVerdictOfWhatWasRead() {
        PackRoot root = root(Optional.of(PackLevel.ORG));
        List<StepContractFacts> steps = List.of(new StepContractFacts("c.step-contract.yaml", List.of("a", "b", "a")));
        FakeInspector inspector = new FakeInspector(facts(root, steps, List.of()));

        PackVerdict verdict = new PackValidation(inspector).validate(root);

        assertThat(inspector.scopes).containsExactly(PackInspector.Scope.FULL);
        assertThat(inspector.roots).containsExactly(root);
        assertThat(verdict.files()).extracting(ClassifiedFile::file).containsExactly("pack.yaml");
        assertThat(verdict.findings())
                .containsExactly(new PackFinding(
                        PackFindingCode.DUPLICATE_STEP_ID,
                        "c.step-contract.yaml",
                        List.of("steps", "2", "id"),
                        "c.step-contract.yaml: step id 'a' is declared more than once"));
    }

    @Test
    void theVerdictIsExactlyWhatThePolicyJudgesOfTheSameFacts() {
        PackRoot root = root(Optional.of(PackLevel.ORG));
        PackContents contents = facts(
                root,
                List.of(new StepContractFacts("c.step-contract.yaml", List.of("x", "x"))),
                List.of(new FileRead.Unreadable<Object>(
                        "tactics/a.tactic.yaml",
                        new ParseFailure("tactics/a.tactic.yaml", "while parsing"),
                        List.of())));

        assertThat(new PackValidation(new FakeInspector(contents)).validate(root))
                .isEqualTo(PackPolicy.judge(contents));
    }

    @Test
    void aFileThatDoesNotParseOrBreaksItsSchemaIsAFindingOfTheCodeOfItsKind() {
        PackRoot root = root(Optional.of(PackLevel.ORG));
        SchemaViolation missing = new SchemaViolation(
                "org-charter.yaml", List.of(), "required", Optional.of("schema_version"), Optional.empty(), Map.of());
        PackContents contents = facts(
                root,
                List.of(),
                List.of(
                        new FileRead.Unreadable<Object>(
                                "tactics/a.tactic.yaml",
                                new ParseFailure("tactics/a.tactic.yaml", "while parsing a block mapping"),
                                List.of()),
                        new FileRead.Unreadable<Object>("org-charter.yaml", null, List.of(missing))));

        List<PackFinding> findings =
                new PackValidation(new FakeInspector(contents)).validate(root).findings();

        assertThat(findings)
                .extracting(PackFinding::code, PackFinding::file, PackFinding::severity)
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple(
                                PackFindingCode.ARTIFACT_SCHEMA_ERROR,
                                "tactics/a.tactic.yaml",
                                PackFindingCode.Severity.BLOCKING),
                        org.assertj.core.api.Assertions.tuple(
                                PackFindingCode.ORG_CHARTER_SCHEMA_ERROR,
                                "org-charter.yaml",
                                PackFindingCode.Severity.BLOCKING));
        assertThat(findings)
                .extracting(PackFinding::detail)
                .contains(
                        "qa/tactics/a.tactic.yaml is not valid YAML: while parsing a block mapping",
                        "qa/org-charter.yaml: missing 'schema_version'");
    }

    @Test
    void aBarePathIsJudgedWithoutAConfiguredLevel() {
        PackRoot bare = root(Optional.empty());
        PackContents contents = new PackContents(
                bare,
                new FileRead.Absent<>("pack.yaml"),
                List.of(),
                new FileRead.Absent<>("drg/fragment.yaml"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        assertThat(new PackValidation(new FakeInspector(contents))
                        .validate(bare)
                        .findings())
                .as("no level to compare with, so neither pack_level_missing nor org_fragment_missing")
                .isEmpty();
    }

    @Test
    void anInspectorIsRequired() {
        assertThatThrownBy(() -> new PackValidation(null)).isInstanceOf(NullPointerException.class);
    }
}
