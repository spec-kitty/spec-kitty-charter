package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.domain.RefusalCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class FileSystemCharterPackRepositoryTest {

    private static final PackLocation QA = new PackLocation("qa", PackLevel.ORG, Path.of("qa"));

    @Test
    void theGraphShardsOfAListingAreItsGraphYamlFilesSortedByName() {
        assertThat(FileSystemCharterPackRepository.graphShards(
                        Stream.of(Path.of("qa/b.graph.yaml"), Path.of("qa/pack.yaml"), Path.of("qa/a.graph.yaml")),
                        QA,
                        p -> true))
                .containsExactly(Path.of("qa/a.graph.yaml"), Path.of("qa/b.graph.yaml"));
    }

    @Test
    void aListingThatFailsWhileItIsReadIsRefusedAsAnUnreadablePack() {
        IOException cause = new IOException("gone");
        Stream<Path> failing = Stream.of(Path.of("qa/a.graph.yaml")).peek(p -> {
            throw new UncheckedIOException(cause);
        });

        assertThatThrownBy(() -> FileSystemCharterPackRepository.graphShards(failing, QA, p -> true))
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessage("pack qa cannot be listed")
                .hasCause(cause);
    }

    static Stream<String> malformedShards() {
        return Stream.of(
                "nodes: [\n",
                "nodes:\n\t- a\n",
                "nodes: !!python/object/apply:os.system [rm]\n",
                "a: 1\na: 2\n",
                "%YAML 9.9\n---\nnodes: []\n",
                "nodes: *missing\n",
                "nodes: {a: b\n",
                "\"unterminated\n",
                "key: [a, b]]\n");
    }

    @ParameterizedTest
    @MethodSource("malformedShards")
    void aMalformedShardIsRefusedNamingTheShardRelativeToItsPackAndNoHostPath(String text, @TempDir Path host)
            throws IOException {
        Path pack = Files.createDirectories(host.resolve("packs").resolve("qa"));
        Files.writeString(pack.resolve("tactic.graph.yaml"), text);

        assertThatThrownBy(() -> FileSystemCharterPackRepository.loadPack(new PackLocation("qa", PackLevel.ORG, pack)))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
                    assertThat(refusal.getMessage()).startsWith("qa/tactic.graph.yaml is not valid YAML: ");
                    assertThat(refusal.getMessage())
                            .as("the parser's reason, not an empty tail")
                            .doesNotEndWith(": ");
                    assertThat(refusal.getMessage())
                            .doesNotContain(host.toString())
                            .doesNotContain(host.getParent().toString())
                            .doesNotContain(System.getProperty("user.home"))
                            .doesNotContain(Path.of("").toAbsolutePath().toString())
                            .doesNotContain("\n");
                });
    }
}
