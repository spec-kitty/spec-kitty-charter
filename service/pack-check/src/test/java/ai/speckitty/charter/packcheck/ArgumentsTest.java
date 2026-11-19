package ai.speckitty.charter.packcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packcheck.Arguments.Help;
import ai.speckitty.charter.packcheck.Arguments.Outcome;
import ai.speckitty.charter.packcheck.Arguments.Parsed;
import ai.speckitty.charter.packcheck.Arguments.UsageFault;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packvalidation.set.PackSetEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link Arguments#parse}: the grammar, the happy paths and one test per usage fault (#478, T014). */
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
    class TheHappyPaths {

        @Test
        void aRepeatedPackWithAndWithoutALevel() throws IOException {
            Path org = pack("org-pack");
            Path project = pack("project-pack");

            Parsed parsed =
                    parsed(Arguments.parse(new String[] {"--pack", org + ":org", "--pack", project.toString()}));

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

            Parsed parsed = parsed(Arguments.parse(
                    new String[] {"--pack", a + ":built-in", "--pack", b + ":org", "--pack", c + ":project"}));

            assertThat(parsed.packs())
                    .extracting(entry -> entry.root().configuredLevel().orElseThrow())
                    .containsExactly(PackLevel.BUILT_IN, PackLevel.ORG, PackLevel.PROJECT);
        }

        @Test
        void theLevelIsTheTextAfterTheLastColonSoAPathWithAColonParses() throws IOException {
            // A Windows-style path: the drive's colon is not followed by a level, the last one is.
            Path windows = pack("C:\\packs\\x");

            Parsed parsed = parsed(Arguments.parse(new String[] {"--pack", windows + ":org"}));

            assertThat(parsed.packs()).singleElement().satisfies(entry -> {
                assertThat(entry.root().locator()).isEqualTo(windows.toString());
                assertThat(entry.root().configuredLevel()).contains(PackLevel.ORG);
            });
        }

        @Test
        void aPathWithAColonAndNoLevelIsThePathWhole() throws IOException {
            Path colon = pack("with:colon");

            Parsed parsed = parsed(Arguments.parse(new String[] {"--pack", colon.toString()}));

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

            Parsed parsed = parsed(Arguments.parse(new String[] {"--config-root", temp.toString(), "--round-trip"}));

            assertThat(parsed.packs()).extracting(PackSetEntry::name).containsExactly("a", "b");
            assertThat(parsed.packs())
                    .allSatisfy(
                            entry -> assertThat(entry.root().configuredLevel()).isEmpty());
            assertThat(parsed.roundTrip()).isTrue();
        }

        @Test
        void aValueWhoseWholePathIsADirectoryAndWhosePrefixIsNotIsTheWholePathAtNoLevel() throws IOException {
            Path colon = pack("x:org");

            Parsed parsed = parsed(Arguments.parse(new String[] {"--pack", colon.toString()}));

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

            Parsed parsed = parsed(Arguments.parse(new String[] {"--pack", colon.toString()}));

            assertThat(parsed.packs()).singleElement().satisfies(entry -> {
                assertThat(entry.root().locator()).isEqualTo(colon.toString());
                assertThat(entry.root().configuredLevel()).isEmpty();
            });
        }

        @Test
        void aWholePathDirectoryTakesALevelOfItsOwn() throws IOException {
            Path colon = pack("x:org");

            Parsed parsed = parsed(Arguments.parse(new String[] {"--pack", colon + ":project"}));

            assertThat(parsed.packs()).singleElement().satisfies(entry -> {
                assertThat(entry.root().locator()).isEqualTo(colon.toString());
                assertThat(entry.root().configuredLevel()).contains(PackLevel.PROJECT);
            });
        }

        @Test
        void theRoundTripMayComeFirst() throws IOException {
            Path a = pack("a");

            assertThat(parsed(Arguments.parse(new String[] {"--round-trip", "--pack", a.toString()}))
                            .roundTrip())
                    .isTrue();
        }

        @Test
        void everyArgumentAfterTheTerminatorIsAPack() throws IOException {
            Path plain = pack("plain");
            Path other = pack("other");

            Parsed parsed = parsed(
                    Arguments.parse(new String[] {"--round-trip", "--pack", plain.toString(), "--", other + ":org"}));

            assertThat(parsed.packs()).extracting(PackSetEntry::name).containsExactly("plain", "other");
            assertThat(parsed.packs().get(1).root().configuredLevel()).contains(PackLevel.ORG);
            assertThat(parsed.roundTrip()).isTrue();
        }

        @Test
        void aValueThatStartsWithTwoDashesIsAPackValueAfterTheTerminator() {
            // Not an option: the value is taken as a pack directory, which does not exist where the tests run.
            assertThat(fault(Arguments.parse(new String[] {"--", "--no-such-dir-478"})))
                    .isEqualTo("--pack --no-such-dir-478 is not a directory");
        }

        @Test
        void aConfigRootSkipsHiddenEntriesAndAsideCopies() throws IOException {
            Files.createDirectories(temp.resolve("packs/a"));
            Files.createDirectories(temp.resolve("packs/.staging"));
            Files.createDirectories(temp.resolve("packs/a.pack-old-1"));

            Parsed parsed = parsed(Arguments.parse(new String[] {"--config-root", temp.toString()}));

            assertThat(parsed.packs()).extracting(PackSetEntry::name).containsExactly("a");
        }

        @Test
        void helpAloneAsksForTheUsage() {
            assertThat(Arguments.parse(new String[] {"--help"})).isInstanceOf(Help.class);
        }
    }

    @Nested
    class TheUsageFaults {

        @Test
        void noInput() {
            assertThat(fault(Arguments.parse(new String[] {}))).startsWith("nothing to check");
        }

        @Test
        void onlyTheRoundTrip() {
            assertThat(fault(Arguments.parse(new String[] {"--round-trip"}))).startsWith("nothing to check");
        }

        @Test
        void bothInputs() throws IOException {
            Files.createDirectories(temp.resolve("packs/a"));

            assertThat(fault(Arguments.parse(new String[] {
                        "--config-root", temp.toString(), "--pack", pack("b").toString()
                    })))
                    .isEqualTo("give --config-root or --pack, not both");
        }

        @Test
        void theConfigRootTwice() {
            assertThat(fault(Arguments.parse(
                            new String[] {"--config-root", temp.toString(), "--config-root", temp.toString()})))
                    .isEqualTo("--config-root is given twice");
        }

        @Test
        void anUnknownOption() {
            assertThat(fault(Arguments.parse(new String[] {"--packs", "x"}))).isEqualTo("unknown option --packs");
        }

        @Test
        void helpWithAnythingElseIsAnUnknownOption() throws IOException {
            assertThat(fault(Arguments.parse(new String[] {"--pack", pack("a").toString(), "--help"})))
                    .isEqualTo("unknown option --help");
        }

        @Test
        void aMissingValueAtTheEnd() {
            assertThat(fault(Arguments.parse(new String[] {"--pack"}))).isEqualTo("--pack needs a value");
        }

        @Test
        void aMissingValueBeforeAnotherOption() {
            assertThat(fault(Arguments.parse(new String[] {"--config-root", "--round-trip"})))
                    .isEqualTo("--config-root needs a value");
        }

        @Test
        void anUnknownLevel() throws IOException {
            Path a = pack("a");

            assertThat(fault(Arguments.parse(new String[] {"--pack", a + ":team"})))
                    .isEqualTo("unknown level team in --pack " + a + ":team; the levels are built-in, org, project");
        }

        @Test
        void aMissingPackDirectory() {
            Path missing = temp.resolve("missing");

            assertThat(fault(Arguments.parse(new String[] {"--pack", missing + ":org"})))
                    .isEqualTo("--pack " + missing + " is not a directory");
        }

        @Test
        void aPackThatIsAFile() throws IOException {
            Path file = Files.writeString(temp.resolve("pack.yaml"), "name: x\n");

            assertThat(fault(Arguments.parse(new String[] {"--pack", file.toString()})))
                    .isEqualTo("--pack " + file + " is not a directory");
        }

        @Test
        void aPackThatIsTheFileSystemRoot() {
            String root = temp.getRoot().toString();

            assertThat(fault(Arguments.parse(new String[] {"--pack", root})))
                    .isEqualTo("--pack " + root + " names no pack directory");
        }

        @Test
        void aMissingConfigRoot() {
            Path missing = temp.resolve("missing");

            assertThat(fault(Arguments.parse(new String[] {"--config-root", missing.toString()})))
                    .isEqualTo("--config-root " + missing + " is not a directory");
        }

        @Test
        void aConfigRootWithNoPacksDirectory() {
            assertThat(fault(Arguments.parse(new String[] {"--config-root", temp.toString()})))
                    .isEqualTo("nothing to check: " + temp.resolve("packs") + " holds no pack directory");
        }

        @Test
        void aConfigRootWithAnEmptyPacksDirectory() throws IOException {
            Files.createDirectories(temp.resolve("packs"));
            Files.writeString(temp.resolve("packs/README.md"), "not a pack\n");

            assertThat(fault(Arguments.parse(new String[] {"--config-root", temp.toString()})))
                    .isEqualTo("nothing to check: " + temp.resolve("packs") + " holds no pack directory");
        }

        @Test
        void twoPacksWithOneRealPath() throws IOException {
            Path a = pack("a");
            Path again = pack("x").resolve("..").resolve("a");

            assertThat(fault(Arguments.parse(new String[] {"--pack", a.toString(), "--pack", again + ":org"})))
                    .isEqualTo(a + " and " + again + " are the same directory; give each pack once");
        }

        @Test
        void aValueThatStartsWithTwoDashesWithoutTheTerminator() throws IOException {
            pack("--dir");

            assertThat(fault(Arguments.parse(new String[] {"--pack", "--dir"}))).isEqualTo("--pack needs a value");
        }

        @Test
        void onlyTheTerminator() {
            assertThat(fault(Arguments.parse(new String[] {"--"}))).startsWith("nothing to check");
        }

        @Test
        void aConfigRootAndPacksAfterTheTerminator() throws IOException {
            Files.createDirectories(temp.resolve("packs/a"));

            assertThat(fault(Arguments.parse(new String[] {
                        "--config-root", temp.toString(), "--", pack("b").toString()
                    })))
                    .isEqualTo("give --config-root or --pack, not both");
        }

        @Test
        void aPackValueThatNamesTwoDirectories() throws IOException {
            Path bare = pack("x");
            Path colon = pack("x:org");

            assertThat(fault(Arguments.parse(new String[] {"--pack", colon.toString()})))
                    .isEqualTo("--pack " + colon + " is ambiguous: " + colon + " and " + bare
                            + " are both directories; rename one, or write " + colon + ":<level>");
        }

        @Test
        void aPackInsideAnother() throws IOException {
            Path outer = pack("outer");
            Path inner = Files.createDirectories(outer.resolve("inner"));

            assertThat(fault(Arguments.parse(new String[] {"--pack", outer.toString(), "--pack", inner.toString()})))
                    .isEqualTo(outer + " and " + inner + " overlap, one inside the other; a pack holds no other pack");
        }

        @Test
        void aPackHoldingAnEarlierOne() throws IOException {
            Path outer = pack("outer");
            Path inner = Files.createDirectories(outer.resolve("inner"));

            assertThat(fault(Arguments.parse(new String[] {"--pack", inner.toString(), "--pack", outer.toString()})))
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

            assertThat(fault(Arguments.parse(new String[] {"--config-root", temp.toString()})))
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

            assertThat(fault(Arguments.parse(new String[] {"--config-root", temp.toString()})))
                    .isEqualTo(filed + ", " + gone
                            + " are links to no directory; a pack entry must resolve to a directory");
        }

        @Test
        void twoPacksWithOneName() throws IOException {
            Path first = Files.createDirectories(temp.resolve("one/" + ExamplePacks.ORG));
            Path second = Files.createDirectories(temp.resolve("two/" + ExamplePacks.ORG));

            assertThat(fault(Arguments.parse(new String[] {"--pack", first.toString(), "--pack", second.toString()})))
                    .isEqualTo(first + " and " + second + " are both named " + ExamplePacks.ORG
                            + "; a set holds one pack of each name");
        }
    }
}
