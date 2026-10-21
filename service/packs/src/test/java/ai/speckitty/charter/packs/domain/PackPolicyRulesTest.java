package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackContents.ArtifactFacts;
import ai.speckitty.charter.packs.domain.PackContents.AssetFacts;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.RawGraph;
import ai.speckitty.charter.packs.domain.PackContents.StepContractFacts;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The single-pack rules a schema cannot express (FR-009): the exact texts, places, severities and precedence. */
class PackPolicyRulesTest {

    private static final String MANIFEST = "assets/logo.asset.yaml";

    /** A fragment that was read and is empty: an org pack has one, so no {@code org_fragment_missing} fires. */
    private static final FileRead<RawGraph> EMPTY_FRAGMENT = new FileRead.Read<>(
            "drg/fragment.yaml", new RawGraph("drg/fragment.yaml", RawGraph.Shape.FRAGMENT, List.of(), List.of()));

    private static PackDescriptor descriptor(Optional<String> requiresCore, List<String> undeclared) {
        return new PackDescriptor(
                "01ABC", "1.0.0", Optional.empty(), "qa", Optional.of(PackLevel.ORG), requiresCore, undeclared);
    }

    private static PackContents contents(
            FileRead<PackDescriptor> descriptor,
            List<StepContractFacts> steps,
            List<ArtifactFacts> artifacts,
            List<AssetFacts> assets) {
        return new PackContents(
                new PackRoot("qa", Optional.of(PackLevel.ORG), "loc"),
                descriptor,
                List.of(),
                EMPTY_FRAGMENT,
                List.of(),
                steps,
                artifacts,
                assets,
                List.of());
    }

    private static PackContents withDescriptor(Optional<String> requiresCore, List<String> undeclared) {
        return contents(
                new FileRead.Read<>("pack.yaml", descriptor(requiresCore, undeclared)),
                List.of(),
                List.of(),
                List.of());
    }

    private static PackContents withAssets(AssetFacts... assets) {
        return contents(
                new FileRead.Read<>("pack.yaml", descriptor(Optional.empty(), List.of())),
                List.of(),
                List.of(),
                List.of(assets));
    }

    private static List<PackFinding> judge(PackContents contents) {
        return PackPolicy.judge(contents).findings().stream()
                .filter(f -> f.code() != PackFindingCode.PACK_LEVEL_MISSING)
                .toList();
    }

    // ---- requires_core_invalid -------------------------------------------------------------------

    @Test
    void anInvalidRequiresCoreIsBlockingWithThePinnedText() {
        List<PackFinding> findings = judge(withDescriptor(Optional.of("banana"), List.of()));

        assertThat(findings)
                .containsExactly(new PackFinding(
                        PackFindingCode.REQUIRES_CORE_INVALID,
                        "pack.yaml",
                        List.of("requires_core"),
                        "pack.yaml of qa: requires_core 'banana' is not a PEP 440 version specifier"));
        assertThat(findings.get(0).severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
    }

    @Test
    void theRequiresCoreRefusalIsPackUnreadableWithTheExactText() {
        PackContents contents = withDescriptor(Optional.of("~=1"), List.of());

        assertThatThrownBy(() -> PackPolicy.load(contents)).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refusal.getMessage())
                    .isEqualTo("pack.yaml of qa: requires_core '~=1' is not a PEP 440 version specifier");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {">=4.0.0rc7,<5", "", ">=1.0", "~=1.4.5"})
    void aValidRequiresCoreGivesNoFinding(String requiresCore) {
        assertThat(judge(withDescriptor(Optional.of(requiresCore), List.of()))).isEmpty();
        assertThat(PackPolicy.load(withDescriptor(Optional.of(requiresCore), List.of())))
                .isNotNull();
    }

    @Test
    void anAbsentRequiresCoreGivesNoFinding() {
        assertThat(judge(withDescriptor(Optional.empty(), List.of()))).isEmpty();
    }

    @Test
    void anUnreadableDescriptorGivesNoRequiresCoreFinding() {
        PackContents contents = contents(
                new FileRead.Unreadable<>("pack.yaml", new PackContents.ParseFailure("pack.yaml", "x"), List.of()),
                List.of(),
                List.of(),
                List.of());
        assertThat(judge(contents)).noneMatch(f -> f.code() == PackFindingCode.REQUIRES_CORE_INVALID);
    }

    // ---- pack_unknown_key ------------------------------------------------------------------------

    @Test
    void everyUndeclaredKeyIsOneSortedWarningWithThePinnedText() {
        List<PackFinding> findings =
                judge(withDescriptor(Optional.empty(), List.of("zeta", "accompanies_doctrine_pack")));

        assertThat(findings)
                .containsExactly(
                        new PackFinding(
                                PackFindingCode.PACK_UNKNOWN_KEY,
                                "pack.yaml",
                                List.of("accompanies_doctrine_pack"),
                                "pack.yaml of qa declares unknown key 'accompanies_doctrine_pack'; it is ignored"),
                        new PackFinding(
                                PackFindingCode.PACK_UNKNOWN_KEY,
                                "pack.yaml",
                                List.of("zeta"),
                                "pack.yaml of qa declares unknown key 'zeta'; it is ignored"));
        assertThat(findings).allSatisfy(f -> assertThat(f.severity()).isEqualTo(PackFindingCode.Severity.WARNING));
    }

    @Test
    void aRetiredFieldIsJustAnUndeclaredKey() {
        assertThat(judge(withDescriptor(Optional.empty(), List.of("accompanies_doctrine_pack"))))
                .extracting(PackFinding::code)
                .containsExactly(PackFindingCode.PACK_UNKNOWN_KEY);
    }

    @Test
    void noUndeclaredKeyGivesNoWarning() {
        assertThat(judge(withDescriptor(Optional.empty(), List.of()))).isEmpty();
    }

    @Test
    void theUnknownKeyCodeIsAWarningThatProjectsOntoItsDiagnostic() {
        assertThat(PackFindingCode.PACK_UNKNOWN_KEY.severity()).isEqualTo(PackFindingCode.Severity.WARNING);
        assertThat(PackFindingCode.PACK_UNKNOWN_KEY.diagnostic()).isEqualTo(Diagnostic.Code.PACK_UNKNOWN_KEY);
        assertThat(Diagnostic.Code.PACK_UNKNOWN_KEY.token()).isEqualTo("pack_unknown_key");
    }

    @Test
    void anUndeclaredKeyLoadsWithItsDiagnosticAndItsPinnedText() {
        CharterPack pack = PackPolicy.load(withDescriptor(Optional.empty(), List.of("surprise")));

        assertThat(pack.diagnostics())
                .containsExactly(new Diagnostic(
                        Diagnostic.Code.PACK_UNKNOWN_KEY,
                        "pack.yaml of qa declares unknown key 'surprise'; it is ignored"));
    }

    @Test
    void theDescriptorFindingsComeBeforeThePackWideOnes() {
        PackContents contents = new PackContents(
                new PackRoot("qa", Optional.of(PackLevel.ORG), "loc"),
                new FileRead.Read<>(
                        "pack.yaml",
                        new PackDescriptor(
                                "01ABC",
                                "1.0.0",
                                Optional.empty(),
                                "qa",
                                Optional.of(PackLevel.PROJECT),
                                Optional.of("banana"),
                                List.of("zeta"))),
                List.of(),
                EMPTY_FRAGMENT,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        assertThat(PackPolicy.judge(contents).findings())
                .extracting(PackFinding::code)
                .containsExactly(
                        PackFindingCode.REQUIRES_CORE_INVALID,
                        PackFindingCode.PACK_UNKNOWN_KEY,
                        PackFindingCode.PACK_LEVEL_MISMATCH);
    }

    // ---- org_fragment_missing --------------------------------------------------------------------

    private static PackContents orgPack(
            Optional<PackLevel> configured, FileRead<PackDescriptor> descriptor, FileRead<RawGraph> fragment) {
        return new PackContents(
                new PackRoot("lonely", configured, "loc"),
                descriptor,
                List.of(),
                fragment,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static FileRead<PackDescriptor> declaring(Optional<PackLevel> level) {
        return new FileRead.Read<>(
                "pack.yaml",
                new PackDescriptor("01ABC", "1.0.0", Optional.empty(), "lonely", level, Optional.empty(), List.of()));
    }

    private static final FileRead<RawGraph> NO_FRAGMENT = new FileRead.Absent<>("drg/fragment.yaml");

    private static List<PackFinding> fragmentFindings(PackContents contents) {
        return PackPolicy.judge(contents).findings().stream()
                .filter(f -> f.code() == PackFindingCode.ORG_FRAGMENT_MISSING)
                .toList();
    }

    @Test
    void aConfiguredOrgPackWithoutAFragmentIsBlockingWithThePinnedText() {
        PackContents contents = orgPack(Optional.of(PackLevel.ORG), declaring(Optional.of(PackLevel.ORG)), NO_FRAGMENT);

        List<PackFinding> findings = fragmentFindings(contents);

        assertThat(findings)
                .containsExactly(new PackFinding(
                        PackFindingCode.ORG_FRAGMENT_MISSING,
                        "pack.yaml",
                        List.of(),
                        "org pack lonely has no drg/fragment.yaml"));
        assertThat(findings.get(0).severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
        assertThatThrownBy(() -> PackPolicy.load(contents)).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refusal.getMessage()).isEqualTo("org pack lonely has no drg/fragment.yaml");
        });
    }

    @Test
    void aConfiguredOrgPackWithNoDescriptorEitherIsStillRefusedForItsFragment() {
        assertThat(fragmentFindings(
                        orgPack(Optional.of(PackLevel.ORG), new FileRead.Absent<>("pack.yaml"), NO_FRAGMENT)))
                .hasSize(1);
    }

    @Test
    void aConfiguredOrgPackWithAFragmentIsFine() {
        assertThat(fragmentFindings(
                        orgPack(Optional.of(PackLevel.ORG), declaring(Optional.of(PackLevel.ORG)), EMPTY_FRAGMENT)))
                .isEmpty();
    }

    @Test
    void aConfiguredBuiltInPackNeedsNoFragment() {
        assertThat(fragmentFindings(orgPack(
                        Optional.of(PackLevel.BUILT_IN), declaring(Optional.of(PackLevel.BUILT_IN)), NO_FRAGMENT)))
                .isEmpty();
        assertThat(fragmentFindings(
                        orgPack(Optional.of(PackLevel.BUILT_IN), new FileRead.Absent<>("pack.yaml"), NO_FRAGMENT)))
                .isEmpty();
    }

    @Test
    void aBarePathIsJudgedByItsDeclaredLevel() {
        assertThat(fragmentFindings(orgPack(Optional.empty(), declaring(Optional.of(PackLevel.ORG)), NO_FRAGMENT)))
                .extracting(PackFinding::detail)
                .containsExactly("org pack lonely has no drg/fragment.yaml");
        assertThat(fragmentFindings(orgPack(Optional.empty(), declaring(Optional.of(PackLevel.BUILT_IN)), NO_FRAGMENT)))
                .isEmpty();
        assertThat(fragmentFindings(orgPack(Optional.empty(), declaring(Optional.empty()), NO_FRAGMENT)))
                .isEmpty();
        assertThat(fragmentFindings(orgPack(Optional.empty(), new FileRead.Absent<>("pack.yaml"), NO_FRAGMENT)))
                .isEmpty();
    }

    @Test
    void anUnreadableFragmentIsNotAMissingOne() {
        FileRead<RawGraph> broken = new FileRead.Unreadable<>(
                "drg/fragment.yaml", new PackContents.ParseFailure("drg/fragment.yaml", "x"), List.of());

        assertThat(fragmentFindings(orgPack(Optional.of(PackLevel.ORG), declaring(Optional.of(PackLevel.ORG)), broken)))
                .isEmpty();
    }

    @Test
    void theMissingFragmentIsPackWideSoItComesAfterTheLevelFindingAndTheFileFindings() {
        PackContents contents = orgPack(
                Optional.of(PackLevel.ORG),
                new FileRead.Read<>(
                        "pack.yaml",
                        new PackDescriptor(
                                "01ABC",
                                "1.0.0",
                                Optional.empty(),
                                "lonely",
                                Optional.of(PackLevel.PROJECT),
                                Optional.of("banana"),
                                List.of())),
                NO_FRAGMENT);

        assertThat(PackPolicy.judge(contents).findings())
                .extracting(PackFinding::code)
                .containsExactly(
                        PackFindingCode.REQUIRES_CORE_INVALID,
                        PackFindingCode.PACK_LEVEL_MISMATCH,
                        PackFindingCode.ORG_FRAGMENT_MISSING);
    }

    // ---- duplicate_step_id -----------------------------------------------------------------------

    private static final String CONTRACT = "mission_step_contracts/specify.step-contract.yaml";

    private static List<PackFinding> stepFindings(StepContractFacts... contracts) {
        return judge(contents(
                new FileRead.Read<>("pack.yaml", descriptor(Optional.empty(), List.of())),
                List.of(contracts),
                List.of(),
                List.of()));
    }

    @Test
    void aRepeatedStepIdIsBlockingAtTheSecondOccurrenceWithThePinnedText() {
        List<PackFinding> findings = stepFindings(new StepContractFacts(CONTRACT, List.of("a", "b", "a")));

        assertThat(findings)
                .containsExactly(new PackFinding(
                        PackFindingCode.DUPLICATE_STEP_ID,
                        CONTRACT,
                        List.of("steps", "2", "id"),
                        CONTRACT + ": step id 'a' is declared more than once"));
        assertThat(findings.get(0).severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
    }

    @Test
    void aTriplicatedIdGivesOneFindingAndEachDuplicatedIdItsOwn() {
        List<PackFinding> findings =
                stepFindings(new StepContractFacts(CONTRACT, List.of("a", "b", "a", "b", "a", "c")));

        assertThat(findings)
                .extracting(PackFinding::place, PackFinding::detail)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                List.of("steps", "2", "id"), CONTRACT + ": step id 'a' is declared more than once"),
                        org.assertj.core.groups.Tuple.tuple(
                                List.of("steps", "3", "id"), CONTRACT + ": step id 'b' is declared more than once"));
    }

    @Test
    void distinctStepIdsAndIdsAcrossContractsGiveNoFinding() {
        assertThat(stepFindings(
                        new StepContractFacts(CONTRACT, List.of("a", "b")),
                        new StepContractFacts("mission_step_contracts/plan.step-contract.yaml", List.of("a", "b"))))
                .isEmpty();
    }

    @Test
    void stepIdsAreComparedExactly() {
        assertThat(stepFindings(new StepContractFacts(CONTRACT, List.of("a", "A", "a "))))
                .isEmpty();
    }

    // ---- duplicate_artifact_id -------------------------------------------------------------------

    private static List<PackFinding> artifactFindings(ArtifactFacts... artifacts) {
        return judge(contents(
                new FileRead.Read<>("pack.yaml", descriptor(Optional.empty(), List.of())),
                List.of(),
                List.of(artifacts),
                List.of()));
    }

    @Test
    void theFirstFileInPathOrderOwnsTheIdAndLaterFilesAreFound() {
        List<PackFinding> findings = artifactFindings(
                new ArtifactFacts("directives/z.directive.yaml", "directive", "D1"),
                new ArtifactFacts("directives/a.directive.yaml", "directive", "D1"));

        assertThat(findings)
                .containsExactly(new PackFinding(
                        PackFindingCode.DUPLICATE_ARTIFACT_ID,
                        "directives/z.directive.yaml",
                        List.of(),
                        "directives/z.directive.yaml: directive id 'D1' is already declared in"
                                + " directives/a.directive.yaml"));
        assertThat(findings.get(0).severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
    }

    @Test
    void everyLaterDeclarationNamesTheFirstFile() {
        assertThat(artifactFindings(
                        new ArtifactFacts("t/a.yaml", "tactic", "x"),
                        new ArtifactFacts("t/b.yaml", "tactic", "x"),
                        new ArtifactFacts("t/c.yaml", "tactic", "x")))
                .extracting(PackFinding::file, PackFinding::detail)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "t/b.yaml", "t/b.yaml: tactic id 'x' is already declared in t/a.yaml"),
                        org.assertj.core.groups.Tuple.tuple(
                                "t/c.yaml", "t/c.yaml: tactic id 'x' is already declared in t/a.yaml"));
    }

    @Test
    void theSameIdInDifferentKindsIsNotADuplicate() {
        assertThat(artifactFindings(
                        new ArtifactFacts("a/x.yaml", "tactic", "x"), new ArtifactFacts("b/x.yaml", "directive", "x")))
                .isEmpty();
    }

    @Test
    void differentIdsOfOneKindAreNotADuplicate() {
        assertThat(artifactFindings(
                        new ArtifactFacts("a/x.yaml", "tactic", "x"), new ArtifactFacts("a/y.yaml", "tactic", "y")))
                .isEmpty();
    }

    // ---- duplicate_artifact_id: pathlib order ------------------------------------------------------

    private static PackFinding duplicate(String file, String owner) {
        return new PackFinding(
                PackFindingCode.DUPLICATE_ARTIFACT_ID,
                file,
                List.of(),
                file + ": styleguide id 'x' is already declared in " + owner);
    }

    @Test
    void aDirectoryOwnsTheIdOverASiblingThatExtendsItsName() {
        assertThat(artifactFindings(
                        new ArtifactFacts("styleguides/python-legacy/y.yaml", "styleguide", "x"),
                        new ArtifactFacts("styleguides/python/x.yaml", "styleguide", "x")))
                .containsExactly(duplicate("styleguides/python-legacy/y.yaml", "styleguides/python/x.yaml"));
    }

    @Test
    void aFileInADirectoryOwnsTheIdOverTheFileThatNamesTheDirectoryPlusAnExtension() {
        assertThat(artifactFindings(
                        new ArtifactFacts("styleguides/a.yaml", "styleguide", "x"),
                        new ArtifactFacts("styleguides/a/b.yaml", "styleguide", "x")))
                .containsExactly(duplicate("styleguides/a.yaml", "styleguides/a/b.yaml"));
    }

    @Test
    void theOwnerDoesNotDependOnTheInputOrder() {
        List<ArtifactFacts> facts = new ArrayList<>(List.of(
                new ArtifactFacts("styleguides/python/x.yaml", "styleguide", "x"),
                new ArtifactFacts("styleguides/python-legacy/y.yaml", "styleguide", "x"),
                new ArtifactFacts("styleguides/a.yaml", "styleguide", "x"),
                new ArtifactFacts("styleguides/a/b.yaml", "styleguide", "x")));
        for (int seed = 0; seed < 8; seed++) {
            Collections.shuffle(facts, new Random(seed));
            assertThat(artifactFindings(facts.toArray(ArtifactFacts[]::new)))
                    .extracting(PackFinding::file)
                    .containsExactly(
                            "styleguides/a.yaml", "styleguides/python/x.yaml", "styleguides/python-legacy/y.yaml");
        }
    }

    /** The order {@code sorted()} gives pathlib paths, written by {@code scripts/packs/path_corpus.py}. */
    static List<String> pythonPathOrder() throws IOException {
        try (InputStream in = PackPolicyRulesTest.class.getResourceAsStream("/paths/artifact-order.txt")) {
            assertThat(in).as("the path-order corpus").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.US_ASCII)
                    .lines()
                    .filter(line -> !line.startsWith("#") && !line.isEmpty())
                    .map(PackPolicyRulesTest::unescape)
                    .toList();
        }
    }

    /** The corpora write every non-printable or non-ASCII UTF-16 unit, and the backslash, as backslash-u-hhhh. */
    static String unescape(String text) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '\\') {
                out.append((char) Integer.parseInt(text.substring(i + 2, i + 6), 16));
                i += 6;
            } else {
                out.append(text.charAt(i));
                i++;
            }
        }
        return out.toString();
    }

    @Test
    void thePathOrderIsPythonsOnEveryPathOfTheCorpus() throws IOException {
        List<String> expected = pythonPathOrder();
        assertThat(expected).hasSizeGreaterThan(20);
        List<String> shuffled = new ArrayList<>(expected);
        Collections.shuffle(shuffled, new Random(7));

        ArtifactFacts[] facts = shuffled.stream()
                .map(p -> new ArtifactFacts(p, "styleguide", "x"))
                .toArray(ArtifactFacts[]::new);

        assertThat(artifactFindings(facts))
                .extracting(PackFinding::file, PackFinding::detail)
                .containsExactlyElementsOf(expected.subList(1, expected.size()).stream()
                        .map(p -> org.assertj.core.groups.Tuple.tuple(
                                p, duplicate(p, expected.get(0)).detail()))
                        .toList());
    }

    @Test
    void thePathOrderIsAlsoTheOrderOfTheFindingsOfDifferentFilesInOneClass() throws IOException {
        List<String> expected = pythonPathOrder();
        List<PackFinding> shuffled = new ArrayList<>(expected.stream()
                .map(p -> new PackFinding(PackFindingCode.ASSET_PATH_ESCAPE, p, List.of(), "d"))
                .toList());
        Collections.shuffle(shuffled, new Random(3));

        assertThat(shuffled.stream()
                        .sorted((a, b) -> PackPolicy.comparePaths(a.file(), b.file()))
                        .map(PackFinding::file))
                .containsExactlyElementsOf(expected);
    }

    // ---- asset_path_escape -----------------------------------------------------------------------

    private static AssetFacts asset(String path, String mime, boolean contained) {
        return new AssetFacts(MANIFEST, "logo", path, mime, contained);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/etc/passwd", "../logo.png", "a/../logo.png", "a/..", "..", "assets/../../x.png"})
    void anAbsoluteOrDotDotPathEscapes(String path) {
        List<PackFinding> findings = judge(withAssets(asset(path, "image/png", true)));

        assertThat(findings)
                .containsExactly(new PackFinding(
                        PackFindingCode.ASSET_PATH_ESCAPE,
                        MANIFEST,
                        List.of(),
                        MANIFEST + ": asset path '" + path + "' escapes the pack's assets/ root"));
        assertThat(findings.get(0).severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
    }

    @Test
    void aPathTheAdapterFoundOutsideTheRootEscapesWhateverItsSpelling() {
        assertThat(judge(withAssets(asset("link/logo.png", "image/png", false))))
                .extracting(PackFinding::code)
                .containsExactly(PackFindingCode.ASSET_PATH_ESCAPE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"logo.png", "img/logo.png", "a/..b/logo.png", "./logo.png", "a/b..c.png"})
    void aContainedRelativePathIsFine(String path) {
        assertThat(judge(withAssets(asset(path, "image/png", true)))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "C:\\x.png",
                "C:/x.png",
                "c:\\x.png",
                "1:\\x.png",
                "\\\\srv\\share\\x.png",
                "//srv/share/x.png",
                "//x.png",
                "\\\\srv",
                "a\\..\\b.png",
                "..\\b.png",
                "a\\..",
                "a/..\\b.png",
                "a\\../b.png",
                "C:..",
                "\\\\?\\C:\\x.png"
            })
    void aWindowsAbsoluteOrBackslashDotDotPathEscapesAsCoreFlagsIt(String path) {
        assertThat(judge(withAssets(asset(path, "image/png", true))))
                .extracting(PackFinding::code)
                .containsExactly(PackFindingCode.ASSET_PATH_ESCAPE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"C:x.png", "\\x.png", "a\\b.png", "a\\.\\b.png", "a\\..b\\c.png", "C:", "x:y\\z.png"})
    void aWindowsDriveRelativeOrRootRelativePathDoesNotEscapeInCoreEither(String path) {
        assertThat(judge(withAssets(asset(path, "image/png", true)))).isEmpty();
    }

    /** {@code path<TAB>escapes}, decided by core's own function under {@code python -I}. */
    @Test
    void theEscapeVerdictIsCoresOnEveryRowOfTheGeneratedCorpus() throws IOException {
        List<String> wrong = new ArrayList<>();
        int rows = 0;
        try (InputStream in = PackPolicyRulesTest.class.getResourceAsStream("/paths/asset-escape.tsv")) {
            assertThat(in).as("the asset-escape corpus").isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.US_ASCII)
                    .lines()
                    .toList()) {
                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }
                int tab = line.lastIndexOf('\t');
                String path = unescape(line.substring(0, tab));
                boolean escapes = Boolean.parseBoolean(line.substring(tab + 1));
                rows++;
                boolean found = judge(withAssets(asset(path, "image/png", true))).stream()
                        .anyMatch(f -> f.code() == PackFindingCode.ASSET_PATH_ESCAPE);
                if (found != escapes) {
                    wrong.add(line);
                }
            }
        }
        assertThat(rows).isGreaterThan(9000);
        assertThat(wrong).as("rows where the verdict differs from core's").isEmpty();
    }

    @Test
    void anEscapingAssetGetsNoMimeFindingToo() {
        assertThat(judge(withAssets(asset("../logo.png", "text/plain", true))))
                .extracting(PackFinding::code)
                .containsExactly(PackFindingCode.ASSET_PATH_ESCAPE);
    }

    @Test
    void anEmptyPathOrMimeIsLeftToTheSchema() {
        assertThat(judge(withAssets(asset("", "image/png", true), asset("logo.png", "", true))))
                .isEmpty();
    }

    // ---- asset_mime_invalid ----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"png", "/png", "image/", "/"})
    void aMimeWithoutTwoSidesIsNotWellFormed(String mime) {
        List<PackFinding> findings = judge(withAssets(asset("logo.zzz", mime, true)));
        assertThat(findings)
                .containsExactly(new PackFinding(
                        PackFindingCode.ASSET_MIME_INVALID,
                        MANIFEST,
                        List.of(),
                        MANIFEST + ": asset mime '" + mime + "' is not a well-formed 'type/subtype' value"));
        assertThat(findings.get(0).severity()).isEqualTo(PackFindingCode.Severity.BLOCKING);
    }

    @Test
    void aMimeThatDisagreesWithTheExtensionTableIsInvalidWithThePinnedText() {
        assertThat(judge(withAssets(asset("img/logo.png", "text/plain", true))))
                .containsExactly(new PackFinding(
                        PackFindingCode.ASSET_MIME_INVALID,
                        MANIFEST,
                        List.of(),
                        MANIFEST + ": asset mime 'text/plain' does not match the '.png' extension (image/png)"));
    }

    static Stream<Arguments> agreeingOrUnjudgedPaths() {
        return Stream.of(
                Arguments.of("logo.png", "image/png"),
                Arguments.of("LOGO.PNG", "image/png"),
                Arguments.of("logo.JSON", "application/json"),
                Arguments.of("logo.zzz", "application/x-anything"),
                Arguments.of("logo", "application/x-anything"),
                Arguments.of(".png", "application/x-anything"),
                Arguments.of("a.b/logo", "application/x-anything"),
                Arguments.of("a/b/c.d/", "application/x-anything"),
                Arguments.of("x.tgz", "application/x-tar"),
                Arguments.of("x.TGZ", "application/x-tar"),
                Arguments.of("x.tar.gz", "application/x-tar"),
                Arguments.of("x.json.gz", "application/json"),
                Arguments.of("x.svgz", "image/svg+xml"),
                Arguments.of("x.json.Z", "application/json"),
                Arguments.of("x.md", "text/markdown"),
                Arguments.of("logo.zzz", "a/b/c"));
    }

    @ParameterizedTest
    @MethodSource("agreeingOrUnjudgedPaths")
    void anAgreeingOrUnknownExtensionPasses(String path, String mime) {
        assertThat(judge(withAssets(asset(path, mime, true)))).isEmpty();
    }

    static Stream<Arguments> disagreeingPaths() {
        return Stream.of(
                Arguments.of("x.tgz", "application/gzip", ".tar", "application/x-tar"),
                Arguments.of("x.json.gz", "text/plain", ".json", "application/json"),
                Arguments.of("x.JPG", "image/png", ".jpg", "image/jpeg"),
                Arguments.of("dir.d/x.css", "text/plain", ".css", "text/css"),
                Arguments.of("x.json.Z", "text/plain", ".json", "application/json"));
    }

    @ParameterizedTest
    @MethodSource("disagreeingPaths")
    void aDisagreementNamesTheEffectiveExtensionAndTheExpectedType(
            String path, String mime, String extension, String expected) {
        assertThat(judge(withAssets(asset(path, mime, true))))
                .extracting(PackFinding::detail)
                .containsExactly(MANIFEST + ": asset mime '" + mime + "' does not match the '" + extension
                        + "' extension (" + expected + ")");
    }

    @Test
    void theMimeTypeIsComparedExactly() {
        assertThat(judge(withAssets(asset("logo.png", "IMAGE/PNG", true))))
                .extracting(PackFinding::code)
                .containsExactly(PackFindingCode.ASSET_MIME_INVALID);
    }

    // ---- validate-only ---------------------------------------------------------------------------

    @Test
    void theValidateOnlyRulesNeverReachTheLoadPath() {
        PackContents contents = contents(
                new FileRead.Read<>("pack.yaml", descriptor(Optional.empty(), List.of())),
                List.of(new StepContractFacts(CONTRACT, List.of("a", "a"))),
                List.of(new ArtifactFacts("t/a.yaml", "tactic", "x"), new ArtifactFacts("t/b.yaml", "tactic", "x")),
                List.of(asset("../logo.png", "text/plain", false)));

        assertThat(PackPolicy.load(contents)).isNotNull();
        assertThat(PackPolicy.judge(contents).findings())
                .extracting(PackFinding::code)
                .contains(
                        PackFindingCode.DUPLICATE_STEP_ID,
                        PackFindingCode.DUPLICATE_ARTIFACT_ID,
                        PackFindingCode.ASSET_PATH_ESCAPE);
    }
}
