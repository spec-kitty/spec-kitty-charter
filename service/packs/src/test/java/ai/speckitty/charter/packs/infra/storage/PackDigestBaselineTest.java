package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * NFR-001: a pack that holds no {@code enhancements/} directory keeps the source fingerprint it had before the
 * step-enhancement binding existed. The digests below are {@code digestOfPack} of every example pack (ADR-0070) that
 * holds no {@code enhancements/} directory: only {@code project}. {@code built-in} and {@code org-base} hold
 * {@code missions/software-dev/enhancements/}, so NFR-001 does not cover them and the test asserts they are the ones
 * left out.
 *
 * <p>The binding appends its files to the digested list and never inserts before a pack's other files, so a pack with
 * no enhancement digests as it did before the binding. If a pin goes red with the example pack unchanged, the code
 * moved a fingerprint: fix the code (append, never insert), not the constant. After an intended change to the
 * example pack, recompute the pin: run {@code ./gradlew :packs:test --tests '*PackDigestBaselineTest'} and take the
 * actual value from the failure message, which prints {@code digestOfPack} of the changed pack.
 */
@DisplayName("Packs without step enhancements keep their fingerprint (NFR-001)")
class PackDigestBaselineTest {

    private static final Map<String, String> BASELINE =
            Map.of("project", "sha256:12eb0ce7fab9fae8fc31ef8cf727f5e8266725b783133f313fd7fa26ea5f9dda");

    /** The example packs that hold an {@code enhancements/} directory, so NFR-001 does not cover them. */
    private static final List<String> WITH_ENHANCEMENTS = List.of(ExamplePacks.BUILT_IN, ExamplePacks.ORG);

    @Test
    void everyExamplePackWithoutEnhancementsKeepsItsDigest() throws IOException {
        Map<String, String> actual = new LinkedHashMap<>();
        List<String> enhanced = new ArrayList<>();
        for (String name : ExamplePacks.names()) {
            Path pack = ExamplePacks.pack(name);
            if (holdsEnhancements(pack)) {
                enhanced.add(name);
            } else {
                actual.put(name, FileSystemPackSources.digestOfPack(pack, name));
            }
        }

        assertThat(enhanced).as("packs with an enhancements/ directory").containsExactlyElementsOf(WITH_ENHANCEMENTS);
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(BASELINE);
    }

    @Test
    @DisplayName("failing control: one changed byte in the project example moves its digest off the pin")
    void aChangedFileMovesTheDigestOffThePin(@TempDir Path scratch) throws IOException {
        Path copy = ExamplePacks.copyOf(ExamplePacks.PROJECT, scratch);
        Path directive = copy.resolve("directives/PROJECT_001.directive.yaml");
        Files.writeString(
                directive, Files.readString(directive, StandardCharsets.UTF_8) + "\n", StandardCharsets.UTF_8);

        assertThat(FileSystemPackSources.digestOfPack(copy, ExamplePacks.PROJECT))
                .isNotEqualTo(BASELINE.get(ExamplePacks.PROJECT));
        assertThat(FileSystemPackSources.digestOfPack(
                        ExamplePacks.copyOf(ExamplePacks.PROJECT, scratch.resolve("again")), ExamplePacks.PROJECT))
                .as("an unchanged copy digests to the pin: the digest is of the content, not the location")
                .isEqualTo(BASELINE.get(ExamplePacks.PROJECT));
    }

    private static boolean holdsEnhancements(Path pack) throws IOException {
        try (Stream<Path> walk = Files.walk(pack)) {
            return walk.anyMatch(path ->
                    Files.isDirectory(path) && path.getFileName().toString().equals("enhancements"));
        }
    }
}
