package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.testfixtures.Fifos;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A special file inside a pack root (a FIFO, a socket, a device: neither a regular file nor a directory) is never read,
 * and every surface refuses it (operator ruling R-ad), as a link is refused (D1, D6): the walk lists it apart, the
 * storage rules state one {@code unreadable} at it, and the store half states it too, so the composing read of a
 * configured pack refuses the pack. Each test makes its FIFO at test time and skips where the operating system cannot.
 */
@Timeout(120)
@DisplayName("a special file inside a pack is never read and is refused on every surface (R-ad)")
class SpecialEntriesTest {

    private static final String PACK = "qa";

    private static final String DESCRIPTOR =
            "pack_id: 01ARWG13C00000000000001ABC\npack_version: 1.0.0\nname: qa\npack_level: org\n";

    /** The one wording of a special file's refusal, after its path. */
    private static final String SPECIAL =
            "is not a regular file or a directory; a pack holds only regular files and directories";

    private static String directive(String id, String title) {
        return "schema_version: \"1.0\"\nid: " + id + "\ntitle: " + title + "\nintent: Intent of " + id
                + "\nenforcement: required\n";
    }

    /** A valid org pack named {@value #PACK} holding one directive, in {@code host/packs/qa}. */
    private static Path pack(Path host) throws IOException {
        Path pack = Files.createDirectories(host.resolve("packs").resolve(PACK));
        write(pack.resolve("pack.yaml"), DESCRIPTOR);
        write(pack.resolve("directives/A.directive.yaml"), directive("A", "A"));
        return pack;
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static void fifo(Path at) throws IOException {
        Files.deleteIfExists(at);
        assumeTrue(Fifos.make(at), "the operating system cannot make a FIFO here");
    }

    private static PackRoot root(Path pack) {
        return new PackRoot(PACK, Optional.empty(), pack.toString());
    }

    private static List<Reason> violations(Path pack) {
        return new FileSystemPackInspector()
                .read(root(pack)).rationale().details().stream()
                        .filter(reason -> reason.isFailing(Severity.VIOLATION))
                        .toList();
    }

    @Test
    @DisplayName("the walk lists a FIFO apart from the regular files and the links")
    void theWalkListsAFifoApart(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        fifo(pack.resolve("directives/B.directive.yaml"));

        PackFileSet.Walk walk = PackFileSet.walk(pack, PACK);

        assertThat(walk.regular()).containsExactly("directives/A.directive.yaml", "pack.yaml");
        assertThat(walk.links()).isEmpty();
        assertThat(walk.special()).containsExactly("directives/B.directive.yaml");
    }

    @Test
    @DisplayName("the whole-pack gate counts a FIFO once, as a special file, and the round trip never opens it")
    void theGateCountsAFifoAsASpecialFile(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        fifo(pack.resolve("NOTES.fifo"));

        assertThat(WholePackCensus.of(pack))
                .containsEntry("NOTES.fifo", WholePackCensus.SPECIAL)
                .containsEntry("pack.yaml", "pack-descriptor");
    }

    @Test
    @DisplayName("the verdict states one unreadable per special file, at it, in the one wording, and no other fault")
    void oneViolationPerSpecialFile(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        fifo(pack.resolve("NOTES.fifo"));
        fifo(pack.resolve("directives/B.directive.yaml"));

        assertThat(violations(pack))
                .extracting(reason ->
                        reason.code().value() + " " + reason.location().file() + " " + reason.detail())
                .containsExactly(
                        "unreadable NOTES.fifo NOTES.fifo: " + SPECIAL,
                        "unreadable directives/B.directive.yaml directives/B.directive.yaml: " + SPECIAL);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"pack.yaml", "pack-manifest.yaml", "drg/fragment.yaml", "README.md"})
    @DisplayName("at a special file's path only the special file is stated, not what it makes missing or irregular")
    void atASpecialFilesPathOnlyItIsStated(String at, @TempDir Path host) throws IOException {
        Path pack = pack(host);
        fifo(pack.resolve(at));

        assertThat(violations(pack))
                .filteredOn(reason -> reason.location().file().equals(at))
                .extracting(reason -> reason.code().value() + " " + reason.detail())
                .containsExactly("unreadable " + at + ": " + SPECIAL);
    }

    /**
     * Tier R N3: a FIFO in place of a mission type's step prompt is one fault, at the prompt, as A9 states an
     * anti-pattern file refused for its own fault once: the mission type that names the prompt is not refused beside it
     * for a missing companion.
     */
    @Test
    @DisplayName("a special file in place of a step prompt is stated once, at the prompt, not also at the mission type")
    void aSpecialStepPromptIsOneFaultAtThePrompt(@TempDir Path host) throws IOException {
        Path pack = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host.resolve("packs"));
        String prompt = "missions/software-dev/steps/charter.prompt.md";
        fifo(pack.resolve(prompt));

        List<Reason> violations = new FileSystemPackInspector()
                        .read(new PackRoot(ExamplePacks.BUILT_IN, Optional.empty(), pack.toString()))
                        .rationale()
                        .details()
                        .stream()
                        .filter(reason -> reason.isFailing(Severity.VIOLATION))
                        .toList();

        assertThat(violations)
                .extracting(reason ->
                        reason.code().value() + " " + reason.location().file() + " " + reason.detail())
                .containsExactly("unreadable " + prompt + " " + prompt + ": " + SPECIAL);
    }

    @Test
    @DisplayName("the same tree without its FIFO passes")
    void theSameTreeWithoutTheFifoPasses(@TempDir Path host) throws IOException {
        assertThat(violations(pack(host))).isEmpty();
    }

    @Test
    @DisplayName("the composing read of a configured pack holding a FIFO refuses it, naming the file once")
    void theConfiguredLoadRefusesAPackHoldingAFifo(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        fifo(pack.resolve("NOTES.fifo"));

        CharterRefusal refused = catchThrowableOfType(
                CharterRefusal.class,
                () -> new DomainRepositoryService(
                                new StorageRootLocations(host.resolve("packs")), new YamlSerializationStrategy())
                        .compositionFacts(new PackIdentifier(PACK)));

        assertThat(refused).as("the composing read refuses the pack").isNotNull();
        assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(refused.getMessage()).isEqualTo("the file NOTES.fifo of qa cannot be read: " + SPECIAL);
    }

    @Test
    @DisplayName("a configured pack whose pack.yaml is a FIFO is refused naming it, not as having no pack.yaml")
    void aSpecialDescriptorIsNamedAsSuch(@TempDir Path host) throws IOException {
        Path pack = pack(host);
        fifo(pack.resolve("pack.yaml"));

        CharterRefusal refused = catchThrowableOfType(
                CharterRefusal.class,
                () -> new DomainRepositoryService(
                                new StorageRootLocations(host.resolve("packs")), new YamlSerializationStrategy())
                        .compositionFacts(new PackIdentifier(PACK)));

        assertThat(refused).isNotNull();
        assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
        assertThat(refused.getMessage()).isEqualTo("pack qa: pack.yaml: " + SPECIAL);
    }

    @Test
    @DisplayName("the same configured pack without the FIFO composes")
    void theSamePackWithoutTheFifoComposes(@TempDir Path host) throws IOException {
        pack(host);

        assertThatCode(() -> new DomainRepositoryService(
                                new StorageRootLocations(host.resolve("packs")), new YamlSerializationStrategy())
                        .compositionFacts(new PackIdentifier(PACK)))
                .doesNotThrowAnyException();
    }
}
