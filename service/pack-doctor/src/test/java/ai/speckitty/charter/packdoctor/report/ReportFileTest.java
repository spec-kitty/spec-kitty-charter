package ai.speckitty.charter.packdoctor.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.packdoctor.PackDoctorMain;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code pack-doctor validate --report <file> [--format yaml|xml]} (FR-008, rulings R-i and R-n, T027), run in process:
 * the file holds standard output's tree, in YAML unless {@code --format xml}; standard output is the same with it as
 * without; a usage fault or a report that cannot be written exits {@code 2} with nothing on standard output; and writing
 * the report never changes the exit code of a run.
 */
class ReportFileTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path temp;

    private record Run(int exit, String out, String err) {
        JsonNode report() {
            return JSON.readTree(out);
        }
    }

    private static Run run(String... options) {
        String[] args = Stream.concat(Stream.of("validate"), Stream.of(options)).toArray(String[]::new);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = PackDoctorMain.run(
                args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private Path builtIn() {
        return ExamplePacks.copyOf(ExamplePacks.BUILT_IN, temp.resolve("packs"));
    }

    private static JsonNode yaml(Path file) throws IOException {
        return JSON.valueToTree(new Load(LoadSettings.builder().build()).loadFromString(Files.readString(file)));
    }

    /** The usage fault's line, then the usage, and nothing on standard output. */
    private static void assertUsageFault(Run run, String message) {
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.out()).isEmpty();
        assertThat(run.err()).startsWith("pack-doctor: " + message + System.lineSeparator() + "usage: ");
    }

    @Nested
    class TheFile {

        @Test
        void isYamlByDefaultAndHoldsStandardOutputsTree() throws IOException {
            Path pack = builtIn();
            Path report = temp.resolve("report.yaml");

            Run without = run("--pack", pack.toString());
            Run with = run("--pack", pack.toString(), "--report", report.toString());

            assertThat(with.exit()).isZero().isEqualTo(without.exit());
            assertThat(with.out()).isEqualTo(without.out());
            assertThat(with.err()).isEqualTo(without.err());
            assertThat(Files.readString(report)).startsWith("tool:\n  name: pack-doctor\n");
            assertThat(yaml(report)).isEqualTo(with.report());
        }

        @Test
        void isYamlWithFormatYaml() throws IOException {
            Path pack = builtIn();
            Path report = temp.resolve("report.yaml");

            Run run = run("--pack", pack.toString(), "--report", report.toString(), "--format", "yaml");

            assertThat(run.exit()).isZero();
            assertThat(yaml(report)).isEqualTo(run.report());
        }

        @Test
        void isXmlWithFormatXmlGivenBeforeOrAfterTheReport() throws IOException {
            Path pack = builtIn();
            Path after = temp.resolve("after.xml");
            Path before = temp.resolve("before.xml");

            Run run = run("--pack", pack.toString(), "--report", after.toString(), "--format", "xml");
            run("--format", "xml", "--report", before.toString(), "--pack", pack.toString());

            assertThat(run.exit()).isZero();
            assertThat(Files.readString(after))
                    .startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<pack-doctor-report>\n");
            assertThat(XmlReport.read(Files.readString(after))).isEqualTo(run.report());
            assertThat(XmlReport.read(Files.readString(before)).get("inputs").get("packs"))
                    .isEqualTo(run.report().get("inputs").get("packs"));
        }

        /**
         * Ruling R-ac: a pack directory named with a character XML 1.0 cannot carry is pack content, which never makes
         * the run exit 2: the XML escapes it and reads back as standard output's tree.
         */
        @Test
        void isXmlEvenWhenAPathHoldsACharacterXmlCannotCarry() throws IOException {
            Path pack;
            try {
                pack = Files.move(builtIn(), temp.resolve("packs").resolve("built\u0001in"));
            } catch (IOException | InvalidPathException noSuchName) {
                assumeTrue(false, "the file system cannot name a directory with a control character");
                return;
            }
            Path report = temp.resolve("report.xml");

            Run without = run("--pack", pack + ":built-in");
            Run run = run("--pack", pack + ":built-in", "--report", report.toString(), "--format", "xml");

            assertThat(run.exit()).as(run.err()).isEqualTo(without.exit()).isNotEqualTo(2);
            assertThat(run.out()).isEqualTo(without.out());
            assertThat(Files.readString(report)).contains("built\\u0001in").doesNotContain("\u0001");
            assertThat(XmlReport.read(Files.readString(report))).isEqualTo(run.report());
        }

        @Test
        void isReplacedWhenItExists() throws IOException {
            Path pack = builtIn();
            Path report = Files.writeString(temp.resolve("report.yaml"), "an older and much longer file\n".repeat(200));

            Run run = run("--pack", pack.toString(), "--report", report.toString());

            assertThat(yaml(report)).isEqualTo(run.report());
        }

        @Test
        void leavesTheExitCodeOfAFailingRunAsItIs() throws IOException {
            Path project = ExamplePacks.copyOf(ExamplePacks.PROJECT, temp.resolve("packs"));
            Path report = temp.resolve("report.xml");

            Run without = run("--pack", project + ":project");
            Run with = run("--pack", project + ":project", "--report", report.toString(), "--format", "xml");

            assertThat(with.exit()).isEqualTo(1).isEqualTo(without.exit());
            assertThat(with.out()).isEqualTo(without.out());
            JsonNode written = XmlReport.read(Files.readString(report));
            assertThat(written).isEqualTo(with.report());
            assertThat(written.get("summary").get("exit_code").asInt()).isEqualTo(1);
        }
    }

    @Nested
    class AUsageFault {

        @Test
        void formatWithoutReport() {
            assertUsageFault(run("--pack", builtIn().toString(), "--format", "xml"), "--format needs --report");
        }

        @Test
        void anUnknownFormat() {
            Path report = temp.resolve("report.json");

            assertUsageFault(
                    run("--pack", builtIn().toString(), "--report", report.toString(), "--format", "json"),
                    "unknown format json for --format; the formats are yaml, xml");
            assertThat(report).doesNotExist();
        }

        @Test
        void aFormatInUpperCaseIsUnknown() {
            assertUsageFault(
                    run(
                            "--pack",
                            builtIn().toString(),
                            "--report",
                            temp.resolve("r").toString(),
                            "--format",
                            "XML"),
                    "unknown format XML for --format; the formats are yaml, xml");
        }

        @Test
        void reportTwice() {
            assertUsageFault(
                    run("--pack", builtIn().toString(), "--report", "a", "--report", "b"), "--report is given twice");
        }

        @Test
        void formatTwice() {
            assertUsageFault(
                    run("--pack", builtIn().toString(), "--report", "a", "--format", "xml", "--format", "yaml"),
                    "--format is given twice");
        }

        @Test
        void reportWithNoValue() {
            assertUsageFault(run("--pack", builtIn().toString(), "--report"), "--report needs a value");
            assertUsageFault(run("--report", "--pack", builtIn().toString()), "--report needs a value");
        }

        @Test
        void formatWithNoValue() {
            assertUsageFault(
                    run("--pack", builtIn().toString(), "--report", "a", "--format"), "--format needs a value");
        }

        /** Squad B7: a report written inside a pack it checks would change the pack; it is refused, the pack untouched. */
        @Test
        void aReportInsideAPackItChecks() throws IOException {
            Path pack = builtIn();
            byte[] descriptor = Files.readAllBytes(pack.resolve("pack.yaml"));

            for (Path report : List.of(pack.resolve("pack.yaml"), pack.resolve("report.yaml"), pack)) {
                assertUsageFault(
                        run("--pack", pack.toString(), "--report", report.toString()),
                        "--report " + report + " lies inside the pack " + pack
                                + "; write the report outside every pack it checks");
            }
            assertThat(Files.readAllBytes(pack.resolve("pack.yaml"))).isEqualTo(descriptor);
            assertThat(pack.resolve("report.yaml")).doesNotExist();
        }

        @Test
        void aReportReachedThroughALinkIntoAPackItChecks() throws IOException {
            Path pack = builtIn();
            Path link = temp.resolve("into-the-pack");
            try {
                Files.createSymbolicLink(link, pack);
            } catch (IOException | UnsupportedOperationException noLinks) {
                assumeTrue(false, "the file system cannot make a symbolic link");
                return;
            }
            Path report = link.resolve("report.yaml");

            assertUsageFault(
                    run("--pack", pack.toString(), "--report", report.toString()),
                    "--report " + report + " lies inside the pack " + pack
                            + "; write the report outside every pack it checks");
            assertThat(pack.resolve("report.yaml")).doesNotExist();
        }

        /** Fold review LOW: a report path that is a dangling link into a pack is judged where it leads. */
        @Test
        void aReportThatIsADanglingLinkIntoAPackItChecks() throws IOException {
            Path pack = builtIn();
            Path report = temp.resolve("report.yaml");
            try {
                Files.createSymbolicLink(report, pack.resolve("r.yaml"));
            } catch (IOException | UnsupportedOperationException noLinks) {
                assumeTrue(false, "the file system cannot make a symbolic link");
                return;
            }

            assertUsageFault(
                    run("--pack", pack.toString(), "--report", report.toString()),
                    "--report " + report + " lies inside the pack " + pack
                            + "; write the report outside every pack it checks");
            assertThat(pack.resolve("r.yaml")).doesNotExist();
        }

        @Test
        void theUsageNamesBothOptions() {
            Run run = run("--help");

            assertThat(run.out())
                    .contains("[--report <file> [--format yaml|xml]]")
                    .contains("--report <file>")
                    .contains("--format yaml|xml");
        }
    }

    @Nested
    class AReportThatCannotBeWritten {

        @Test
        void becauseItIsADirectoryExitsTwoWithNothingOnStandardOutput() throws IOException {
            Path directory = Files.createDirectories(temp.resolve("a-directory"));

            Run run = run("--pack", builtIn().toString(), "--report", directory.toString());

            assertThat(run.exit()).isEqualTo(2);
            assertThat(run.out()).isEmpty();
            assertThat(run.err()).startsWith("pack-doctor: cannot write the report " + directory + ": ");
        }

        /**
         * Squad B8: standard output that cannot be written exits 2, so the report file, which names the exit code, is
         * never written for that run; a file already there is left as it was, and no staged copy stays beside it.
         */
        @Test
        void becauseStandardOutputCannotBeWrittenLeavesTheFileAsItWas() throws IOException {
            Path report = Files.writeString(temp.resolve("report.yaml"), "an earlier run's report\n");
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            PrintStream closed = new PrintStream(
                    new OutputStream() {
                        @Override
                        public void write(int b) throws IOException {
                            throw new IOException("closed");
                        }
                    },
                    true,
                    StandardCharsets.UTF_8);

            int exit = PackDoctorMain.run(
                    new String[] {"validate", "--pack", builtIn().toString(), "--report", report.toString()},
                    closed,
                    new PrintStream(err, true, StandardCharsets.UTF_8));

            assertThat(exit).isEqualTo(2);
            assertThat(err.toString(StandardCharsets.UTF_8)).contains("cannot write to standard output");
            assertThat(Files.readString(report)).isEqualTo("an earlier run's report\n");
            try (Stream<Path> entries = Files.list(temp)) {
                assertThat(entries.map(entry -> entry.getFileName().toString()))
                        .containsExactlyInAnyOrder("packs", "report.yaml");
            }
        }

        @Test
        void becauseItsDirectoryDoesNotExist() {
            Path report = temp.resolve("missing").resolve("report.yaml");

            Run run = run("--pack", builtIn().toString(), "--report", report.toString(), "--format", "yaml");

            assertThat(run.exit()).isEqualTo(2);
            assertThat(run.out()).isEmpty();
            assertThat(run.err()).startsWith("pack-doctor: cannot write the report " + report + ": ");
            assertThat(report).doesNotExist();
        }
    }
}
