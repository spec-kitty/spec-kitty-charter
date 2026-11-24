package ai.speckitty.charter.packdoctor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packdoctor.Arguments.Help;
import ai.speckitty.charter.packdoctor.Arguments.Outcome;
import ai.speckitty.charter.packdoctor.Arguments.Parsed;
import ai.speckitty.charter.packdoctor.Arguments.Remediate;
import ai.speckitty.charter.packdoctor.Arguments.UsageFault;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packvalidation.set.PackSetEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link Arguments#parse}: the command word (FR-007), and {@link Arguments#validate}: the grammar of {@code validate}, the
 * happy paths and one test per usage fault (#478, T014).
 */
class ArgumentsTest {

    @TempDir
    Path temp;

    private Path pack(String name) throws IOException {
        return Files.createDirectories(temp.resolve(name));
    }

    private static Parsed parsed(Outcome outcome) {
        assertThat(outcome).isInstanceOf(Parsed.class);
        return (Parsed) outcome;
    }

    private static String fault(Outcome outcome) {
        assertThat(outcome).isInstanceOf(UsageFault.class);
        return ((UsageFault) outcome).message();
    }

    @Nested
    class TheCommandWord {

        @Test
        void validateTakesTheOptionsAfterIt() throws IOException {
            Path a = pack("a");

            Parsed parsed = parsed(Arguments.parse(new String[] {"validate", "--pack", a.toString(), "--round-trip"}));

            assertThat(parsed.roundTrip()).isTrue();
            assertThat(parsed.packs())
                    .extracting(entry -> entry.root().locator())
                    .containsExactly(a.toString());
        }

        @Test
        void validateFullAsksForTheReferencesPhase() throws IOException {
            Path a = pack("a");

            Parsed full = parsed(Arguments.parse(new String[] {"validate", "--pack", a.toString(), "--full"}));
            Parsed plain = parsed(Arguments.parse(new String[] {"validate", "--pack", a.toString()}));

            assertThat(full.full()).isTrue();
            assertThat(full.roundTrip()).isFalse();
            assertThat(plain.full()).isFalse();
            assertThat(parsed(Arguments.validate(new String[] {"--full", "--round-trip", "--pack", a.toString()})))
                    .satisfies(both -> {
                        assertThat(both.full()).isTrue();
                        assertThat(both.roundTrip()).isTrue();
                    });
            assertThat(Arguments.USAGE).contains("[--full]").contains("--full ");
        }

        @Test
        void validateHelpAsksForTheUsage() {
            assertThat(Arguments.parse(new String[] {"validate", "--help"})).isInstanceOf(Help.class);
        }

        @Test
        void helpAloneAsksForTheUsage() {
            assertThat(Arguments.parse(new String[] {"--help"})).isInstanceOf(Help.class);
        }

        @Test
        void remediateTakesPackDirectoriesAndChecksThemWithTheRoundTrip() throws IOException {
            Path a = pack("a");
            Path b = pack("b");

            Outcome outcome = Arguments.parse(new String[] {"remediate", a.toString(), b + ":org=org-b"});

            assertThat(outcome).isInstanceOf(Remediate.class);
            Parsed validation = ((Remediate) outcome).validation();
            assertThat(validation.roundTrip()).isTrue();
            assertThat(validation.report()).isEmpty();
            assertThat(validation.packs())
                    .extracting(
                            entry -> entry.root().name(), entry -> entry.root().locator())
                    .containsExactly(tuple("a", a.toString()), tuple("org-b", b.toString()));
        }

        @Test
        void remediateWithNoPackIsAUsageFault() {
            assertThat(fault(Arguments.parse(new String[] {"remediate"})))
                    .isEqualTo("nothing to remediate: give one or more <dir>[:<level>][=<id>]");
        }

        @Test
        void remediateTakesNoOption() throws IOException {
            assertThat(fault(
                            Arguments.parse(new String[] {"remediate", pack("a").toString(), "--report", "r.yaml"})))
                    .isEqualTo("unknown option --report");
        }

        @Test
        void remediateRefusesTheSameDirectoryTwice() throws IOException {
            Path a = pack("a");

            assertThat(fault(Arguments.parse(new String[] {"remediate", a.toString(), a.toString()})))
                    .contains("are the same directory");
        }

        /**
         * Squad B6: the swap would move the working directory away, so a pack that is or holds it is refused before
         * anything is written; run {@code remediate} from outside the pack.
         */
        @Test
        void remediateRefusesAPackThatIsOrHoldsTheWorkingDirectory() {
            Path working = Path.of("").toAbsolutePath();
            for (String value :
                    List.of(".", "..", working.toString(), working.getParent().toString())) {
                assertThat(fault(Arguments.parse(new String[] {"remediate", value})))
                        .as(value)
                        .isEqualTo("remediate " + value + " holds the working directory, which the swap would move;"
                                + " run remediate from outside the pack");
            }
        }

        /** Squad B9: remediate's faults name its own words, never {@code --pack}, which it does not take. */
        @Test
        void remediateFaultsNameRemediateNotThePackOption() {
            Path missing = temp.resolve("missing");

            assertThat(fault(Arguments.parse(new String[] {"remediate", missing.toString()})))
                    .isEqualTo("remediate " + missing + " is not a directory");
            assertThat(fault(Arguments.parse(new String[] {"remediate", "=org-a"})))
                    .isEqualTo("remediate =org-a names no directory: its directory part is empty;"
                            + " write remediate <dir>[:<level>][=<id>]");
            assertThat(fault(Arguments.parse(new String[] {"remediate", missing + ":galaxy"})))
                    .startsWith("unknown level galaxy in remediate " + missing + ":galaxy; the levels are ");
            assertThat(fault(Arguments.parse(new String[] {"remediate", missing + "=Not An Id"})))
                    .startsWith("remediate " + missing + "=Not An Id: Not An Id after the last = is not a pack id")
                    .contains("write remediate <dir>[:<level>]=<id>")
                    .doesNotContain("--pack");
        }

        @Test
        void remediateHelpAsksForTheUsage() {
            assertThat(Arguments.parse(new String[] {"remediate", "--help"})).isInstanceOf(Help.class);
        }

        @Test
        void noCommandIsAUsageFault() {
            assertThat(fault(Arguments.parse(new String[] {}))).isEqualTo("no command: give validate or remediate");
        }

        @Test
        void theOldFlatFormIsAUsageFault() {
            assertThat(fault(Arguments.parse(new String[] {"--config-root", "x"})))
                    .isEqualTo("no command: give validate or remediate before the options");
        }

        @Test
        void anUnknownCommandIsAUsageFault() {
            assertThat(fault(Arguments.parse(new String[] {"check", "--pack", "x"})))
                    .isEqualTo("unknown command check");
        }

        @Test
        void theUsageNamesBothCommands() {
            assertThat(Arguments.USAGE)
                    .startsWith("usage: pack-doctor validate (--config-root <dir> | --pack")
                    .contains("pack-doctor remediate <dir>[:<level>][=<id>] ...");
        }

        /** Tier R N1: under the POSIX locale Java reads file names as ASCII; the usage says to run under UTF-8. */
        @Test
        void theUsageStatesTheUtf8LocaleItNeeds() {
            assertThat(Arguments.USAGE).contains("locale: run under a UTF-8 locale (LC_ALL=C.UTF-8)");
        }
    }

    @Nested
    class TheHappyPaths {

        @Test
        void aRepeatedPackWithAndWithoutALevel() throws IOException {
            Path org = pack("org-pack");
            Path project = pack("project-pack");

            Parsed parsed =
                    parsed(Arguments.validate(new String[] {"--pack", org + ":org", "--pack", project.toString()}));

            assertThat(parsed.packs())
                    .extracting(
                            PackSetEntry::name,
                            entry -> entry.root().configuredLevel(),
                            entry -> entry.root().locator())
                    .containsExactly(
                            tuple("org-pack", Optional.of(PackLevel.ORG), org.toString()),
                            tuple("project-pack", Optional.empty(), project.toString()));
            assertThat(parsed.roundTrip()).isFalse();
        }

        @Test
        void everyLevelTokenIsALevel() throws IOException {
            Path a = pack("a");
            Path b = pack("b");
            Path c = pack("c");

            Parsed parsed = parsed(Arguments.validate(
                    new String[] {"--pack", a + ":built-in", "--pack", b + ":org", "--pack", c + ":project"}));

            assertThat(parsed.packs())
                    .extracting(entry -> entry.root().configuredLevel().orElseThrow())
                    .containsExactly(PackLevel.BUILT_IN, PackLevel.ORG, PackLevel.PROJECT);
        }

        @Test
        void theLevelIsTheTextAfterTheLastColonSoAPathWithAColonParses() throws IOException {
            // A Windows-style path: the drive's colon is not followed by a level, the last one is.
            Path windows = pack("C:\\packs\\x");

            Parsed parsed = parsed(Arguments.validate(new String[] {"--pack", windows + ":org"}));

            assertThat(parsed.packs()).singleElement().satisfies(entry -> {
                assertThat(entry.root().locator()).isEqualTo(windows.toString());
                assertThat(entry.root().configuredLevel()).contains(PackLevel.ORG);
            });
        }

        @Test
        void aPathWithAColonAndNoLevelIsThePathWhole() throws IOException {
            Path colon = pack("with:colon");

            Parsed parsed = parsed(Arguments.validate(new String[] {"--pack", colon.toString()}));

            assertThat(parsed.packs()).singleElement().satisfies(entry -> {
                assertThat(entry.root().locator()).isEqualTo(colon.toString());
                assertThat(entry.name()).isEqualTo("with:colon");
                assertThat(entry.root().configuredLevel()).isEmpty();
            });
        }

        @Test
        void aConfigRootGivesItsPacksInNameOrderAtTheirDeclaredLevel() throws IOException {
            Files.createDirectories(temp.resolve("packs/b"));
            Files.createDirectories(temp.resolve("packs/a"));

            Parsed parsed = parsed(Arguments.validate(new String[] {"--config-root", temp.toString(), "--round-trip"}));

            assertThat(parsed.packs()).extracting(PackSetEntry::name).containsExactly("a", "b");
            assertThat(parsed.packs())
                    .allSatisfy(
                            entry -> assertThat(entry.root().configuredLevel()).isEmpty());
            assertThat(parsed.roundTrip()).isTrue();
        }

        @Test
        void aValueWhoseWholePathIsADirectoryAndWhosePrefixIsNotIsTheWholePathAtNoLevel() throws IOException {
            Path colon = pack("x:org");

            Parsed parsed = parsed(Arguments.validate(new String[] {"--pack", colon.toString()}));

            assertThat(parsed.packs()).singleElement().satisfies(entry -> {
                assertThat(entry.root().locator()).isEqualTo(colon.toString());
                assertThat(entry.name()).isEqualTo("x:org");
                assertThat(entry.root().configuredLevel()).isEmpty();
            });
        }

        @Test
        void aValueWhosePrefixIsAFileIsTheWholePathAtNoLevel() throws IOException {
            Path colon = pack("y:project");
            Files.writeString(temp.resolve("y"), "a file, not a pack\n");

            Parsed parsed = parsed(Arguments.validate(new String[] {"--pack", colon.toString()}));

            assertThat(parsed.packs()).singleElement().satisfies(entry -> {
                assertThat(entry.root().locator()).isEqualTo(colon.toString());
                assertThat(entry.root().configuredLevel()).isEmpty();
            });
        }

        @Test
        void aWholePathDirectoryTakesALevelOfItsOwn() throws IOException {
            Path colon = pack("x:org");

            Parsed parsed = parsed(Arguments.validate(new String[] {"--pack", colon + ":project"}));

            assertThat(parsed.packs()).singleElement().satisfies(entry -> {
                assertThat(entry.root().locator()).isEqualTo(colon.toString());
                assertThat(entry.root().configuredLevel()).contains(PackLevel.PROJECT);
            });
        }

        @Test
        void theRoundTripMayComeFirst() throws IOException {
            Path a = pack("a");

            assertThat(parsed(Arguments.validate(new String[] {"--round-trip", "--pack", a.toString()}))
                            .roundTrip())
                    .isTrue();
        }

        @Test
        void everyArgumentAfterTheTerminatorIsAPack() throws IOException {
            Path plain = pack("plain");
            Path other = pack("other");

            Parsed parsed = parsed(Arguments.validate(
                    new String[] {"--round-trip", "--pack", plain.toString(), "--", other + ":org"}));

            assertThat(parsed.packs()).extracting(PackSetEntry::name).containsExactly("plain", "other");
            assertThat(parsed.packs().get(1).root().configuredLevel()).contains(PackLevel.ORG);
            assertThat(parsed.roundTrip()).isTrue();
        }

        @Test
        void aValueThatStartsWithTwoDashesIsAPackValueAfterTheTerminator() {
            // Not an option: the value is taken as a pack directory, which does not exist where the tests run.
            assertThat(fault(Arguments.validate(new String[] {"--", "--no-such-dir-478"})))
                    .isEqualTo("--pack --no-such-dir-478 is not a directory");
        }

        @Test
        void aConfigRootSkipsHiddenEntriesAndAsideCopies() throws IOException {
            Files.createDirectories(temp.resolve("packs/a"));
            Files.createDirectories(temp.resolve("packs/.staging"));
            Files.createDirectories(temp.resolve("packs/a.pack-old-1"));

            Parsed parsed = parsed(Arguments.validate(new String[] {"--config-root", temp.toString()}));

            assertThat(parsed.packs()).extracting(PackSetEntry::name).containsExactly("a");
        }

        @Test
        void aPackGivenAnIdIsConfiguredUnderItWhateverItsDirectoryIsCalled() throws IOException {
            Path misnamed = pack("Bad_Name");

            Parsed parsed = parsed(Arguments.validate(new String[] {"--pack", misnamed + "=" + ExamplePacks.ORG}));

            assertThat(parsed.packs())
                    .extracting(
                            PackSetEntry::name,
                            entry -> entry.root().configuredLevel(),
                            entry -> entry.root().locator())
                    .containsExactly(tuple(ExamplePacks.ORG, Optional.empty(), misnamed.toString()));
        }

        @Test
        void aLevelThenAnId() throws IOException {
            Path misnamed = pack("Bad_Name");

            Parsed parsed = parsed(Arguments.validate(new String[] {"--pack", misnamed + ":org=" + ExamplePacks.ORG}));

            assertThat(parsed.packs())
                    .extracting(
                            PackSetEntry::name,
                            entry -> entry.root().configuredLevel(),
                            entry -> entry.root().locator())
                    .containsExactly(tuple(ExamplePacks.ORG, Optional.of(PackLevel.ORG), misnamed.toString()));
        }

        @Test
        void aDirectoryWhoseNameHoldsAnEqualsSignIsThePathWholeUnderItsOwnName() throws IOException {
            // b is a pack id, but no directory a exists: the value names the directory a=b.
            Path equals = pack("a=b");

            Parsed parsed = parsed(Arguments.validate(new String[] {"--pack", equals.toString()}));

            assertThat(parsed.packs()).singleElement().satisfies(entry -> {
                assertThat(entry.root().locator()).isEqualTo(equals.toString());
                assertThat(entry.name()).isEqualTo("a=b");
                assertThat(entry.root().configuredLevel()).isEmpty();
            });
        }

        @Test
        void aDirectoryWhoseNameHoldsAnEqualsSignTakesALevel() throws IOException {
            Path equals = pack("a=b");

            Parsed parsed = parsed(Arguments.validate(new String[] {"--pack", equals + ":org"}));

            assertThat(parsed.packs()).singleElement().satisfies(entry -> {
                assertThat(entry.root().locator()).isEqualTo(equals.toString());
                assertThat(entry.name()).isEqualTo("a=b");
                assertThat(entry.root().configuredLevel()).contains(PackLevel.ORG);
            });
        }

        @Test
        void aDirectoryWhoseNameEndsInNoPackIdAfterAnEqualsSignIsThePathWhole() throws IOException {
            Path equals = pack("x=Not_An_Id");

            Parsed parsed = parsed(Arguments.validate(new String[] {"--pack", equals.toString()}));

            assertThat(parsed.packs()).singleElement().satisfies(entry -> {
                assertThat(entry.root().locator()).isEqualTo(equals.toString());
                assertThat(entry.name()).isEqualTo("x=Not_An_Id");
            });
        }

        @Test
        void aDirectoryWhoseNameHoldsAnEqualsSignTakesAnIdOfItsOwn() throws IOException {
            Path equals = pack("a=b");

            Parsed parsed =
                    parsed(Arguments.validate(new String[] {"--pack", equals + ":project=" + ExamplePacks.PROJECT}));

            assertThat(parsed.packs())
                    .extracting(
                            PackSetEntry::name,
                            entry -> entry.root().configuredLevel(),
                            entry -> entry.root().locator())
                    .containsExactly(tuple(ExamplePacks.PROJECT, Optional.of(PackLevel.PROJECT), equals.toString()));
        }

        @Test
        void helpAloneAsksForTheUsage() {
            assertThat(Arguments.validate(new String[] {"--help"})).isInstanceOf(Help.class);
        }
    }

    @Nested
    class TheUsageFaults {

        @Test
        void noInput() {
            assertThat(fault(Arguments.validate(new String[] {}))).startsWith("nothing to check");
        }

        @Test
        void onlyTheRoundTrip() {
            assertThat(fault(Arguments.validate(new String[] {"--round-trip"}))).startsWith("nothing to check");
        }

        @Test
        void bothInputs() throws IOException {
            Files.createDirectories(temp.resolve("packs/a"));

            assertThat(fault(Arguments.validate(new String[] {
                        "--config-root", temp.toString(), "--pack", pack("b").toString()
                    })))
                    .isEqualTo("give --config-root or --pack, not both");
        }

        @Test
        void theConfigRootTwice() {
            assertThat(fault(Arguments.validate(
                            new String[] {"--config-root", temp.toString(), "--config-root", temp.toString()})))
                    .isEqualTo("--config-root is given twice");
        }

        @Test
        void anUnknownOption() {
            assertThat(fault(Arguments.validate(new String[] {"--packs", "x"}))).isEqualTo("unknown option --packs");
        }

        @Test
        void helpWithAnythingElseIsAnUnknownOption() throws IOException {
            assertThat(fault(
                            Arguments.validate(new String[] {"--pack", pack("a").toString(), "--help"})))
                    .isEqualTo("unknown option --help");
        }

        @Test
        void aMissingValueAtTheEnd() {
            assertThat(fault(Arguments.validate(new String[] {"--pack"}))).isEqualTo("--pack needs a value");
        }

        @Test
        void aMissingValueBeforeAnotherOption() {
            assertThat(fault(Arguments.validate(new String[] {"--config-root", "--round-trip"})))
                    .isEqualTo("--config-root needs a value");
        }

        @Test
        void anUnknownLevel() throws IOException {
            Path a = pack("a");

            assertThat(fault(Arguments.validate(new String[] {"--pack", a + ":team"})))
                    .isEqualTo("unknown level team in --pack " + a + ":team; the levels are built-in, org, project");
        }

        @Test
        void aMissingPackDirectory() {
            Path missing = temp.resolve("missing");

            assertThat(fault(Arguments.validate(new String[] {"--pack", missing.toString()})))
                    .isEqualTo("--pack " + missing + " is not a directory");
        }

        @Test
        void aMissingPackDirectoryWithALevelIsNamedAsGiven() {
            // Neither missing:org nor missing exists: the fault names the value, not only its directory part.
            Path missing = temp.resolve("missing");

            assertThat(fault(Arguments.validate(new String[] {"--pack", missing + ":org"})))
                    .isEqualTo("--pack " + missing + ":org is not a directory, and nor is " + missing);
        }

        @Test
        void aLevelOnAFileIsNamedAsGiven() throws IOException {
            Path file = Files.writeString(temp.resolve("pack.yaml"), "name: x\n");

            assertThat(fault(Arguments.validate(new String[] {"--pack", file + ":project"})))
                    .isEqualTo("--pack " + file + ":project is not a directory, and nor is " + file);
        }

        @Test
        void aPackThatIsAFile() throws IOException {
            Path file = Files.writeString(temp.resolve("pack.yaml"), "name: x\n");

            assertThat(fault(Arguments.validate(new String[] {"--pack", file.toString()})))
                    .isEqualTo("--pack " + file + " is not a directory");
        }

        @Test
        void aPackThatIsTheFileSystemRoot() {
            String root = temp.getRoot().toString();

            assertThat(fault(Arguments.validate(new String[] {"--pack", root})))
                    .isEqualTo("--pack " + root + " names no pack directory");
        }

        @Test
        void aMissingConfigRoot() {
            Path missing = temp.resolve("missing");

            assertThat(fault(Arguments.validate(new String[] {"--config-root", missing.toString()})))
                    .isEqualTo("--config-root " + missing + " is not a directory");
        }

        @Test
        void aConfigRootWithNoPacksDirectory() {
            assertThat(fault(Arguments.validate(new String[] {"--config-root", temp.toString()})))
                    .isEqualTo("nothing to check: " + temp.resolve("packs") + " holds no pack directory");
        }

        @Test
        void aConfigRootWithAnEmptyPacksDirectory() throws IOException {
            Files.createDirectories(temp.resolve("packs"));
            Files.writeString(temp.resolve("packs/README.md"), "not a pack\n");

            assertThat(fault(Arguments.validate(new String[] {"--config-root", temp.toString()})))
                    .isEqualTo("nothing to check: " + temp.resolve("packs") + " holds no pack directory");
        }

        @Test
        void twoPacksWithOneRealPath() throws IOException {
            Path a = pack("a");
            Path again = pack("x").resolve("..").resolve("a");

            assertThat(fault(Arguments.validate(new String[] {"--pack", a.toString(), "--pack", again + ":org"})))
                    .isEqualTo(a + " and " + again + " are the same directory; give each pack once");
        }

        @Test
        void aValueThatStartsWithTwoDashesWithoutTheTerminator() throws IOException {
            pack("--dir");

            assertThat(fault(Arguments.validate(new String[] {"--pack", "--dir"})))
                    .isEqualTo("--pack needs a value");
        }

        @Test
        void onlyTheTerminator() {
            assertThat(fault(Arguments.validate(new String[] {"--"}))).startsWith("nothing to check");
        }

        @Test
        void aConfigRootAndPacksAfterTheTerminator() throws IOException {
            Files.createDirectories(temp.resolve("packs/a"));

            assertThat(fault(Arguments.validate(new String[] {
                        "--config-root", temp.toString(), "--", pack("b").toString()
                    })))
                    .isEqualTo("give --config-root or --pack, not both");
        }

        @Test
        void aPackValueThatNamesTwoDirectories() throws IOException {
            Path bare = pack("x");
            Path colon = pack("x:org");

            assertThat(fault(Arguments.validate(new String[] {"--pack", colon.toString()})))
                    .isEqualTo("--pack " + colon + " is ambiguous: " + colon + " and " + bare
                            + " are both directories; rename one, or write " + colon + ":<level>");
        }

        @Test
        void aPackInsideAnother() throws IOException {
            Path outer = pack("outer");
            Path inner = Files.createDirectories(outer.resolve("inner"));

            assertThat(fault(Arguments.validate(new String[] {"--pack", outer.toString(), "--pack", inner.toString()})))
                    .isEqualTo(outer + " and " + inner + " overlap, one inside the other; a pack holds no other pack");
        }

        @Test
        void aPackHoldingAnEarlierOne() throws IOException {
            Path outer = pack("outer");
            Path inner = Files.createDirectories(outer.resolve("inner"));

            assertThat(fault(Arguments.validate(new String[] {"--pack", inner.toString(), "--pack", outer.toString()})))
                    .isEqualTo(inner + " and " + outer + " overlap, one inside the other; a pack holds no other pack");
        }

        @Test
        void aDanglingEntryUnderTheConfigRoot() throws IOException {
            Files.createDirectories(temp.resolve("packs/a"));
            Path gone = temp.resolve("packs/gone");
            try {
                Files.createSymbolicLink(gone, temp.resolve("nowhere"));
            } catch (IOException | UnsupportedOperationException noLinks) {
                org.junit.jupiter.api.Assumptions.assumeTrue(
                        Boolean.getBoolean("charter.requireSymlinks"), "the file system cannot create links");
                throw noLinks;
            }

            assertThat(fault(Arguments.validate(new String[] {"--config-root", temp.toString()})))
                    .isEqualTo(gone + " is a link to no directory; a pack entry must resolve to a directory");
        }

        @Test
        void anEntryUnderTheConfigRootThatLinksToAFile() throws IOException {
            Files.createDirectories(temp.resolve("packs/a"));
            Path file = Files.writeString(temp.resolve("notes.md"), "not a pack\n");
            Path filed = temp.resolve("packs/filed");
            Path gone = temp.resolve("packs/gone");
            try {
                Files.createSymbolicLink(filed, file);
                Files.createSymbolicLink(gone, temp.resolve("nowhere"));
            } catch (IOException | UnsupportedOperationException noLinks) {
                org.junit.jupiter.api.Assumptions.assumeTrue(
                        Boolean.getBoolean("charter.requireSymlinks"), "the file system cannot create links");
                throw noLinks;
            }

            assertThat(fault(Arguments.validate(new String[] {"--config-root", temp.toString()})))
                    .isEqualTo(filed + ", " + gone
                            + " are links to no directory; a pack entry must resolve to a directory");
        }

        @Test
        void twoPacksWithOneName() throws IOException {
            Path first = Files.createDirectories(temp.resolve("one/" + ExamplePacks.ORG));
            Path second = Files.createDirectories(temp.resolve("two/" + ExamplePacks.ORG));

            assertThat(fault(
                            Arguments.validate(new String[] {"--pack", first.toString(), "--pack", second.toString()})))
                    .isEqualTo(first + " and " + second + " are both under the id " + ExamplePacks.ORG
                            + "; a set holds one pack of each id");
        }

        @Test
        void nothingAfterTheEqualsSign() throws IOException {
            Path dir = pack("dir");

            assertThat(fault(Arguments.validate(new String[] {"--pack", dir + "="})))
                    .isEqualTo("--pack " + dir + "=: nothing follows the last =, and " + dir
                            + "= is not a directory; write --pack <dir>[:<level>]=<id>. "
                            + PackIdentifier.GRAMMAR_DETAIL);
        }

        @Test
        void anIdThatIsNoPackId() throws IOException {
            Path dir = pack("dir");

            assertThat(fault(Arguments.validate(new String[] {"--pack", dir + "=Bad_Id"})))
                    .isEqualTo("--pack " + dir + "=Bad_Id: Bad_Id after the last = is not a pack id, and " + dir
                            + "=Bad_Id is not a directory; write --pack <dir>[:<level>]=<id>. "
                            + PackIdentifier.GRAMMAR_DETAIL);
        }

        @Test
        void anIdBeforeTheLevelIsNoPackId() throws IOException {
            // The order is <dir>[:<level>][=<id>]: org-base:org is the text after the last =, and is no pack id.
            Path dir = pack("dir");

            assertThat(fault(Arguments.validate(new String[] {"--pack", dir + "=org-base:org"})))
                    .startsWith("--pack " + dir + "=org-base:org: org-base:org after the last = is not a pack id");
        }

        @Test
        void aMissingDirectoryWithAnId() {
            Path missing = temp.resolve("missing");

            assertThat(fault(Arguments.validate(new String[] {"--pack", missing + ":org=" + ExamplePacks.ORG})))
                    .isEqualTo("--pack " + missing + ":org is not a directory, and nor is " + missing);
        }

        @Test
        void aPackValueWhoseIdReadingAndWholeReadingBothNameADirectory() throws IOException {
            Path bare = pack("a");
            Path equals = pack("a=b");

            assertThat(fault(Arguments.validate(new String[] {"--pack", equals.toString()})))
                    .isEqualTo("--pack " + equals + " is ambiguous: it names the directory " + equals
                            + " under its own name, and the directory " + bare + " under the id b; rename one, write "
                            + bare + "/=b for " + bare + " under the id b, or write " + equals + "=<id> for " + equals
                            + " under an id");
        }

        /** PR #608 review MEDIUM-B: an id reading that is itself ambiguous is a usage fault, never an exception. */
        @Test
        void aValueWhoseIdReadingIsItselfAmbiguousIsAUsageFault() throws IOException {
            pack("x");
            pack("x:org");
            Path whole = pack("x:org=" + ExamplePacks.ORG);
            Path rest = temp.resolve("x:org");

            assertThat(fault(Arguments.validate(new String[] {"--pack", whole.toString()})))
                    .isEqualTo("--pack " + whole + " is ambiguous: it names the directory " + whole
                            + " under its own name, and " + rest
                            + " is itself ambiguous; rename one of the directories");
        }

        @Test
        void anAmbiguousIdReadingWithALevelKeepsTheLevelInItsSpelling() throws IOException {
            Path bare = pack("a");
            Path whole = pack("a:org=b");

            assertThat(fault(Arguments.validate(new String[] {"--pack", whole.toString()})))
                    .isEqualTo("--pack " + whole + " is ambiguous: it names the directory " + whole
                            + " under its own name, and the directory " + bare + " under the id b; rename one, write "
                            + bare + "/:org=b for " + bare + " under the id b, or write " + whole + "=<id> for "
                            + whole + " under an id");
        }

        /** Each spelling the ambiguity fault offers parses as it says, with both directories present. */
        @Test
        void theSpellingsTheAmbiguityOffersAreUnambiguous() throws IOException {
            Path bare = pack("a");
            Path equals = pack("a=b");

            Parsed idReading = parsed(Arguments.validate(new String[] {"--pack", bare + "/=b"}));
            Parsed wholeReading = parsed(Arguments.validate(new String[] {"--pack", equals + "=" + ExamplePacks.ORG}));
            Parsed levelled = parsed(Arguments.validate(new String[] {"--pack", bare + "/:org=b"}));

            assertThat(idReading.packs())
                    .extracting(PackSetEntry::name, entry -> entry.root().configuredLevel())
                    .containsExactly(tuple("b", Optional.empty()));
            assertThat(Path.of(idReading.packs().getFirst().root().locator())).isEqualTo(bare);
            assertThat(wholeReading.packs())
                    .extracting(
                            PackSetEntry::name,
                            entry -> entry.root().configuredLevel(),
                            entry -> entry.root().locator())
                    .containsExactly(tuple(ExamplePacks.ORG, Optional.empty(), equals.toString()));
            assertThat(levelled.packs())
                    .extracting(PackSetEntry::name, entry -> entry.root().configuredLevel())
                    .containsExactly(tuple("b", Optional.of(PackLevel.ORG)));
            assertThat(Path.of(levelled.packs().getFirst().root().locator())).isEqualTo(bare);
        }

        /** L1: an empty directory part never names the working directory, which the empty path resolves to. */
        @Test
        void anEmptyDirectoryBeforeTheIdIsRefused() {
            assertThat(fault(Arguments.validate(new String[] {"--pack", "=" + ExamplePacks.ORG})))
                    .isEqualTo("--pack =" + ExamplePacks.ORG
                            + " names no directory: its directory part is empty; write --pack <dir>[:<level>][=<id>]");
        }

        @Test
        void anEmptyDirectoryBeforeTheLevelIsRefused() {
            assertThat(fault(Arguments.validate(new String[] {"--pack", ":org"})))
                    .isEqualTo("--pack :org names no directory: its directory part is empty;"
                            + " write --pack <dir>[:<level>][=<id>]");
        }

        @Test
        void anEmptyDirectoryBeforeALevelAndAnIdIsRefused() {
            assertThat(fault(Arguments.validate(new String[] {"--pack", ":org=" + ExamplePacks.ORG})))
                    .isEqualTo("--pack :org names no directory: its directory part is empty;"
                            + " write --pack <dir>[:<level>][=<id>]");
        }

        @Test
        void anEmptyPackValueIsRefused() {
            assertThat(fault(Arguments.validate(new String[] {"--pack", ""})))
                    .isEqualTo("--pack  names no directory: its directory part is empty;"
                            + " write --pack <dir>[:<level>][=<id>]");
        }

        @Test
        void anIdReadingThatIsItselfAmbiguousIsThatFault() throws IOException {
            Path bare = pack("x");
            Path colon = pack("x:org");

            assertThat(fault(Arguments.validate(new String[] {"--pack", colon + "=" + ExamplePacks.ORG})))
                    .isEqualTo("--pack " + colon + " is ambiguous: " + colon + " and " + bare
                            + " are both directories; rename one, or write " + colon + ":<level>");
        }

        @Test
        void aWholeReadingThatIsAmbiguousIsThatFault() throws IOException {
            // x=Bad:org and x=Bad are both directories; Bad:org after the = is no pack id.
            Path bare = pack("x=Bad");
            Path colon = pack("x=Bad:org");

            assertThat(fault(Arguments.validate(new String[] {"--pack", colon.toString()})))
                    .isEqualTo("--pack " + colon + " is ambiguous: " + colon + " and " + bare
                            + " are both directories; rename one, or write " + colon + ":<level>");
        }

        @Test
        void anExplicitIdThatRepeatsADefaultedOne() throws IOException {
            Path named = pack(ExamplePacks.ORG);
            Path misnamed = pack("Bad_Name");

            assertThat(fault(Arguments.validate(
                            new String[] {"--pack", named.toString(), "--pack", misnamed + "=" + ExamplePacks.ORG})))
                    .isEqualTo(named + " and " + misnamed + " are both under the id " + ExamplePacks.ORG
                            + "; a set holds one pack of each id");
        }
    }
}
