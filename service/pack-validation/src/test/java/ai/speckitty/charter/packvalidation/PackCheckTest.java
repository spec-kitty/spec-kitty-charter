package ai.speckitty.charter.packvalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Rationale;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.ClassifiedFile;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackReading;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackVerdict;
import ai.speckitty.charter.packs.domain.StepEnhancementFacts;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packvalidation.placement.Placement;
import ai.speckitty.charter.packvalidation.placement.PlacementCodes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Validate mode, the validation edge (#554, FR-005, S6): it reads the pack once through the port, appends placement's
 * findings to the readout's, and returns them in precedence order with the classified files.
 */
class PackCheckTest {

    private static final String PACK_YAML = "pack.yaml";

    private static final FileRead<RawGraph> EMPTY_FRAGMENT = new FileRead.Read<>(
            "drg/fragment.yaml", new RawGraph("drg/fragment.yaml", RawGraph.Shape.FRAGMENT, List.of(), List.of()));

    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            "01ARWG13C00000000000001ABC",
            "1.0.0",
            Optional.empty(),
            "qa",
            Optional.of(PackLevel.ORG),
            Optional.empty());

    private static final List<ClassifiedFile> FILES =
            List.of(new ClassifiedFile(PACK_YAML, Optional.of("pack-descriptor"), Optional.empty()));

    /** A reading that records the roots it was asked for and answers with a fixed readout. */
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

    private static PackRoot root(Optional<PackLevel> level) {
        return new PackRoot("qa", level, "loc");
    }

    private static PackReadout readout(
            PackRoot root, FileRead<PackDescriptor> descriptor, FileRead<RawGraph> fragment, List<Reason> reasons) {
        PackContents contents = new PackContents(
                root,
                descriptor,
                List.of(),
                fragment,
                List.of(),
                List.of(),
                StepEnhancementFacts.none(),
                List.of(),
                List.of(),
                List.of());
        // Hand-written facts carry no assembly input: the one reader answers such a read pack_unreadable.
        return new PackReadout(
                contents, FILES, List.of(), new Rationale(reasons), PackReadout.Unreadable.noAssembly(root.name()));
    }

    private static Reason failing(ReasonCode code, String file, String pointer, String detail) {
        return new Reason(code.value(), Evaluation.FAIL, code, Location.in(file).at(pointer), detail);
    }

    @Test
    void aCheckReadsTheRootOnceAndAnswersTheReadoutsFindingsWithItsFiles() {
        PackRoot root = root(Optional.of(PackLevel.ORG));
        Reason repeated = failing(
                PackCodes.DUPLICATE_STEP_ID,
                "rx.mission-type.yaml",
                "/steps/0/substeps/2/id",
                "rx.mission-type.yaml: substep id 'a' is declared more than once in the step 's'");
        FakeReading reading = new FakeReading(
                readout(root, new FileRead.Read<>(PACK_YAML, DESCRIPTOR), EMPTY_FRAGMENT, List.of(repeated)));

        PackVerdict verdict = new PackCheck(reading).validate(root);

        assertThat(reading.roots).containsExactly(root);
        assertThat(verdict.files()).isEqualTo(FILES);
        assertThat(verdict.findings()).containsExactly(repeated);
    }

    @Test
    void placementIsAppendedToTheReadoutsFindingsAndTheWholeIsInPrecedenceOrder() {
        PackRoot root = root(Optional.of(PackLevel.ORG));
        Reason tactic = failing(PackCodes.DUPLICATE_ARTIFACT_ID, "tactics/b.tactic.yaml", "", "tactics/b.tactic.yaml");
        Reason descriptor = failing(PackCodes.DRG_SCHEMA_ERROR, PACK_YAML, "/name", "pack.yaml of qa");
        FakeReading reading = new FakeReading(readout(
                root,
                new FileRead.Read<>(PACK_YAML, DESCRIPTOR),
                new FileRead.Absent<>("drg/fragment.yaml"),
                List.of(tactic, descriptor)));

        List<Reason> findings = new PackCheck(reading).validate(root).findings();

        assertThat(findings)
                .as("pack.yaml first, then the pack-wide placement group, then every other file")
                .extracting(Reason::code)
                .containsExactly(
                        PackCodes.DRG_SCHEMA_ERROR,
                        PlacementCodes.ORG_FRAGMENT_MISSING,
                        PackCodes.DUPLICATE_ARTIFACT_ID);
        assertThat(findings.get(1).detail()).isEqualTo("org pack qa has no drg/fragment.yaml");
        assertThat(findings.get(1).location()).isEqualTo(Location.in(PACK_YAML));
    }

    @Test
    void theLevelRulesRunBeforeTheOrgFragmentsWithinThePlacementGroup() {
        PackRoot root = root(Optional.of(PackLevel.ORG));
        FakeReading reading = new FakeReading(
                readout(root, new FileRead.Absent<>(PACK_YAML), new FileRead.Absent<>("drg/fragment.yaml"), List.of()));

        assertThat(new PackCheck(reading).validate(root).findings())
                .extracting(Reason::code, Reason::detail)
                .containsExactly(
                        tuple(
                                PlacementCodes.PACK_LEVEL_MISSING,
                                "org pack qa has no pack.yaml; the configured level org is used"),
                        tuple(PlacementCodes.ORG_FRAGMENT_MISSING, "org pack qa has no drg/fragment.yaml"));
    }

    @Test
    void theVerdictHoldsTheFailingReasonsWarningsIncludedAndNoPassingOne() {
        PackRoot root = root(Optional.of(PackLevel.ORG));
        ReasonCode notice = new ReasonCode("some_notice", Severity.NOTICE);
        Reason warning = failing(PackCodes.PROMPT_SLOT_NOT_EMPTY_WARNING, "steps/a.md", "", "steps/a.md: slot");
        Reason noted = failing(notice, "steps/a.md", "", "noted");
        FakeReading reading = new FakeReading(readout(
                root,
                new FileRead.Read<>(PACK_YAML, DESCRIPTOR),
                EMPTY_FRAGMENT,
                List.of(Reason.passed("a rule that held"), warning, noted)));

        assertThat(new PackCheck(reading).validate(root).findings()).containsExactly(warning, noted);
    }

    @Test
    void aBarePathIsJudgedWithoutAConfiguredLevel() {
        PackRoot bare = root(Optional.empty());
        FakeReading reading = new FakeReading(
                readout(bare, new FileRead.Absent<>(PACK_YAML), new FileRead.Absent<>("drg/fragment.yaml"), List.of()));

        assertThat(new PackCheck(reading).validate(bare).findings())
                .as("no level to compare with, so neither pack_level_missing nor org_fragment_missing")
                .isEmpty();
    }

    @Test
    void aReadingIsRequired() {
        assertThatThrownBy(() -> new PackCheck(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void thePlacementCompositionIsTheLevelRulesThenTheOrgFragments() {
        PackRoot root = root(Optional.of(PackLevel.PROJECT));
        PackReadout read = readout(
                root,
                new FileRead.Read<>(PACK_YAML, DESCRIPTOR),
                new FileRead.Absent<>("drg/fragment.yaml"),
                List.of());

        assertThat(PackCheck.placement().apply(new Placement(read, root.configuredLevel())))
                .as("a project pack that declares org: the level mismatch; it is no org pack, so no fragment finding")
                .extracting(Reason::code)
                .containsExactly(PlacementCodes.PACK_LEVEL_MISMATCH);
    }

    // ---- over the one reader -------------------------------------------------------------------------------------

    static List<String> examplePacks() {
        return ExamplePacks.names();
    }

    @ParameterizedTest
    @MethodSource("examplePacks")
    void everyExamplePackIsReadInFullAndHasNoViolation(String name) {
        PackRoot root = new PackRoot(
                name,
                Optional.of(ExamplePacks.level(name)),
                ExamplePacks.pack(name).toString());

        PackVerdict verdict = new PackCheck(new FileSystemPackInspector()).validate(root);

        assertThat(verdict.files()).isNotEmpty();
        assertThat(verdict.findings()).noneMatch(finding -> finding.severity() == Severity.VIOLATION);
    }

    @Test
    void aPackWithNoDescriptorIsRefusedAtPackYamlAsTheRegistrationRefusesIt(@TempDir Path dir) throws IOException {
        Path pack = Files.createDirectories(dir.resolve("qa"));
        Files.writeString(pack.resolve("tactic.graph.yaml"), "nodes: []\nedges: []\n", StandardCharsets.UTF_8);

        List<Reason> findings = new PackCheck(new FileSystemPackInspector())
                .validate(new PackRoot("qa", Optional.empty(), pack.toString()))
                .findings();

        assertThat(findings)
                .as("R2 row 24: validate mode gains the registration's answer by construction")
                .filteredOn(finding -> finding.location().file().equals(PACK_YAML))
                .extracting(finding -> finding.code().value(), Reason::detail)
                .containsExactly(tuple("pack_unreadable", "pack qa has no pack.yaml"));
    }

    private static final String MANIFEST = "pack-manifest.yaml";

    /** Validate mode's findings on a copy of the project example pack, changed first. */
    private static List<Reason> validateProject(Path dir, Consumer<Path> change) {
        Path pack = ExamplePacks.copyOf(ExamplePacks.PROJECT, dir);
        change.accept(pack);
        return new PackCheck(new FileSystemPackInspector())
                .validate(new PackRoot(
                        ExamplePacks.PROJECT, Optional.of(ExamplePacks.level(ExamplePacks.PROJECT)), pack.toString()))
                .findings();
    }

    @Test
    void aManifestThatLeavesOutAHeldFileIsAMembershipFaultAsTheRegistrationAnswersIt(@TempDir Path dir) {
        List<Reason> findings = validateProject(
                dir,
                pack -> rewrite(
                        pack.resolve(MANIFEST),
                        text -> text.replaceFirst(
                                "- kind: skill\\n  id: project-triage\\n  path: skills/project-triage.skill.yaml\\n"
                                        + "  content_hash: [0-9a-f]+\\n",
                                "")));

        assertThat(findings)
                .as("R2 row 29: validate mode gains manifest membership by construction")
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file(),
                        Reason::detail)
                .containsExactly(tuple(
                        "manifest_membership",
                        MANIFEST,
                        MANIFEST + ": leaves out constituent skill:project-triage at skills/project-triage.skill.yaml,"
                                + " which the pack holds"));
    }

    @Test
    void aManifestThatIsNoRegularFileIsUnreadableAsTheRegistrationAnswersIt(@TempDir Path dir) {
        List<Reason> findings = validateProject(dir, pack -> {
            try {
                Files.delete(pack.resolve(MANIFEST));
                Files.createDirectories(pack.resolve(MANIFEST));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        assertThat(findings)
                .as("R2 row 30: validate mode gains the manifest's regularity by construction")
                .extracting(
                        finding -> finding.code().value(),
                        finding -> finding.location().file(),
                        Reason::detail)
                .containsExactly(tuple("unreadable", MANIFEST, MANIFEST + ": the manifest is not a regular file"));
    }

    private static void rewrite(Path file, UnaryOperator<String> change) {
        try {
            Files.writeString(
                    file, change.apply(Files.readString(file, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
