package ai.speckitty.charter.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.PackLevel;
import ai.speckitty.charter.domain.RefusalCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

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
}
