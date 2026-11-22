package ai.speckitty.charter.packdoctor;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The checker is pack-doctor, outright and with no alias (FR-007, C-002, C-004; plan D2 and D11; occurrence map of
 * mission pack-doctor-coverage-01M7YMQK). No tracked file outside {@link #KEPT} names the old tool, its module, its
 * package or its classes, and the command line takes a command word: {@code validate} checks as the old tool did,
 * {@code remediate} is not available yet, and the old flat form, options with no command word, is a usage fault.
 *
 * <p>The scan reads the tracked files ({@code git ls-files}) of the repository root the build hands over as
 * {@code charter.test.repo-root}; the build declares their candidate lines as an input, so a new mention reruns it. It
 * is tagged {@code surface} and runs in {@code surfaceTest}, inside {@code check}, never in {@code test} or PIT, whose
 * minions get no repository root (#461).
 */
class NoPackCheckSurfaceTest {

    private static final Path ROOT = Path.of(System.getProperty("charter.test.repo-root", ""));

    /** The command line's working directory: its example pack and its temporary directory. */
    @TempDir
    Path temp;

    /**
     * The old tool's names: its hyphenated, snake and upper-case names, its package, and any identifier that holds
     * {@code PackCheck} inside a longer name, a camel-case compound included ({@code PackCheckMain},
     * {@code theTableOfPackCheckHolds}). Three identifiers stay: the bare {@code PackCheck} of pack-validation (validate
     * mode over one pack), and its tests {@code PackCheckTest} and {@code PackCheckReadoutTest}, a domain term
     * (occurrence map: do_not_change).
     */
    private static final Pattern OLD_NAME = Pattern.compile(
            "(?i:pack[-_]check)|packcheck|packCheck|\\wPackCheck|PackCheck(?!\\b|Test\\b|ReadoutTest\\b)\\w");

    /** A path or path prefix the scan leaves out, and why. */
    private record Kept(String path, String reason) {
        boolean covers(String file) {
            return path.endsWith("/") ? file.startsWith(path) : file.equals(path);
        }
    }

    /** The one allowlist: history (C-004) and the docs another work package renames. Each entry says why. */
    static final List<Kept> KEPT = List.of(
            new Kept("kitty-specs/", "mission records are history (C-004)"),
            new Kept("kitty-ops/", "Op records are history (C-004)"),
            new Kept("docs/changelog/", "released changelog entries are history (C-004)"),
            new Kept("docs/plans/engineering-notes/", "dated assessments are history (C-004)"),
            new Kept("contracts/charter/CHANGELOG.md", "released contract history; the rename has its own fragment"),
            new Kept(
                    "contracts/charter/changes/2026-11-21-pack-doctor.md",
                    "the change fragment that records the rename names what was renamed; it joins CHANGELOG.md"),
            new Kept(".kittify/evidence/", "evidence records are history (C-004)"),
            new Kept(
                    "contracts/packs/conformance/classifier-cases.yaml",
                    "names spec-kitty-open-packs' own pack-check usage, that repository's history"),
            new Kept(
                    "docs/adr/",
                    "ADR bodies keep their historical wording; WP08 amends ADR-0075 decision 9 and records the rename"),
            new Kept(
                    "docs/architecture/architectural-patterns-applied.md",
                    "WP08 (T033) renames its scope rows and then removes this entry"),
            new Kept(
                    "service/pack-doctor/src/test/java/ai/speckitty/charter/packdoctor/NoPackCheckSurfaceTest.java",
                    "this test names what it forbids"));

    /**
     * Names that keep the old text inside a live file, and why: a file name is permanent once linked, so a link to it
     * is no mention of the tool.
     */
    private static final List<String> KEPT_NAMES = List.of(
            // ADR-0075's file name: ADR file names never change (docs/adr/README.md); WP08 amends its decision 9.
            "0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check",
            // The unreleased change fragment keeps its dated file name; its text names pack-doctor.
            "2026-11-20-pack-check-skipped-reasons",
            // This test's own name, which the mission's tasks name (WP02 T006, WP08 T033) and the build cites.
            "NoPackCheckSurfaceTest");

    private static String withoutKeptNames(String text) {
        String stripped = text;
        for (String name : KEPT_NAMES) {
            stripped = stripped.replace(name, "");
        }
        return stripped;
    }

    @Nested
    @Tag("surface")
    class TheLiveSurfaces {

        @Test
        void noTrackedFileOutsideTheAllowlistNamesTheOldTool() {
            List<String> findings = new ArrayList<>();
            for (String file : trackedFiles()) {
                if (KEPT.stream().anyMatch(kept -> kept.covers(file))) {
                    continue;
                }
                if (OLD_NAME.matcher(withoutKeptNames(file)).find()) {
                    findings.add(file + ": its path");
                }
                text(ROOT.resolve(file)).ifPresent(text -> {
                    List<String> lines = text.lines().toList();
                    for (int i = 0; i < lines.size(); i++) {
                        Matcher matcher = OLD_NAME.matcher(withoutKeptNames(lines.get(i)));
                        if (matcher.find()) {
                            findings.add(file + ":" + (i + 1) + ": " + matcher.group());
                        }
                    }
                });
            }

            assertThat(findings)
                    .as("live surfaces that still name the old tool; rename them, or allowlist history in KEPT")
                    .isEmpty();
        }

        @Test
        void theScanFindsEachOldName() {
            assertThat(List.of(
                            "java -jar pack-check-1.jar",
                            "Pack-check reports",
                            "ai.speckitty.charter.packcheck",
                            ":pack-check:packCheckJar",
                            "COMPOSED_BY_THE_PACK_CHECK_MAIN",
                            "PackCheckMain",
                            "PackCheckReport.yaml",
                            "PackCheckLicences",
                            "fooOfPackCheckBar",
                            "theStatusTableOfPackCheckHoldsExactlyTheseCodes",
                            "aPackCheck",
                            "PackCheckReadoutTests"))
                    .allSatisfy(text ->
                            assertThat(OLD_NAME.matcher(text).find()).as(text).isTrue());
        }

        @Test
        void theDomainTermAndTheKeptFileNamesPass() {
            assertThat(List.of(
                            "PackCheck.validate(root)",
                            "class PackCheckTest",
                            "PackCheckReadoutTest.java",
                            "NoPackCheckSurfaceTest",
                            "/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check/",
                            "pack-doctor validate"))
                    .allSatisfy(text -> assertThat(
                                    OLD_NAME.matcher(withoutKeptNames(text)).find())
                            .as(text)
                            .isFalse());
        }

        @Test
        void everyAllowlistEntryIsTrackedAndSaysWhy() {
            List<String> tracked = trackedFiles();
            assertThat(KEPT).allSatisfy(kept -> {
                assertThat(kept.reason()).as(kept.path()).isNotBlank();
                assertThat(tracked.stream().anyMatch(kept::covers))
                        .as("%s covers a tracked file", kept.path())
                        .isTrue();
            });
        }
    }

    @Nested
    class TheCommandLine {

        /** What one run answered. */
        private record Run(int exit, String out, String err) {}

        private Run run(String... args) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int exit = PackDoctorMain.run(
                    args,
                    new PrintStream(out, true, StandardCharsets.UTF_8),
                    new PrintStream(err, true, StandardCharsets.UTF_8),
                    temp);
            return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        }

        private Path examplePack() {
            Path packs = temp.resolve("packs");
            ExamplePacks.copyOf(ExamplePacks.BUILT_IN, packs);
            return packs.resolve(ExamplePacks.BUILT_IN);
        }

        @Test
        void validateChecksThePacks() {
            Run run = run("validate", "--pack", examplePack().toString());

            assertThat(run.exit()).as(run.err()).isZero();
            assertThat(run.out()).contains("\"problems\" : [ ]");
        }

        @Test
        void remediateIsNotAvailable() {
            Run run = run("remediate");

            assertThat(run.exit()).isEqualTo(2);
            assertThat(run.out()).isEmpty();
            assertThat(run.err()).startsWith("pack-doctor: ").contains("not available");
        }

        @Test
        void theOldFlatFormIsNoAlias() {
            Run run = run("--pack", examplePack().toString());

            assertThat(run.exit()).isEqualTo(2);
            assertThat(run.out()).isEmpty();
            assertThat(run.err()).startsWith("pack-doctor: ").contains("usage: pack-doctor validate");
        }

        @Test
        void aConfigRootWithNoCommandIsAUsageFault() {
            Run run = run("--config-root", "x");

            assertThat(run.exit()).isEqualTo(2);
            assertThat(run.err()).contains("usage: pack-doctor validate");
        }
    }

    // --- reading ---------------------------------------------------------------------------------------------------

    private static List<String> trackedFiles() {
        if (!Files.exists(ROOT.resolve(".git"))) {
            throw new IllegalStateException("no git checkout at " + ROOT + ": the scan reads the tracked files");
        }
        try {
            Process process = new ProcessBuilder("git", "-C", ROOT.toString(), "ls-files", "-z")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("git ls-files failed with exit " + exit);
            }
            return List.of(output.split("\0")).stream()
                    .filter(name -> !name.isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** The file's text, or nothing for a binary file (not UTF-8) or a tracked file deleted in the working tree. */
    private static Optional<String> text(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Files.readAllBytes(file)))
                    .toString());
        } catch (CharacterCodingException binary) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
