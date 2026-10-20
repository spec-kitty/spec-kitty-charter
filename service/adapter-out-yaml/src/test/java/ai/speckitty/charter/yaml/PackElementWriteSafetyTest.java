package ai.speckitty.charter.yaml;

import static ai.speckitty.charter.yaml.PackFixtures.ALPHA_FILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.PackLocator;
import ai.speckitty.charter.domain.Tactic;
import ai.speckitty.charter.domain.TacticStep;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** NFR-004: a write is atomic and stays inside the pack. */
class PackElementWriteSafetyTest {

    private static Tactic renamedAlpha(String name) {
        return new Tactic(
                "alpha-tactic",
                "1.0",
                name,
                List.of(new TacticStep("Only step", Optional.empty(), List.of(), List.of())),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of());
    }

    private static List<String> fileNames(Path directory) throws IOException {
        try (Stream<Path> list = Files.list(directory)) {
            return list.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void aFailureBetweenTheTempWriteAndTheRenameLeavesTheTargetUnchangedAndNoTemp(@TempDir Path host)
            throws IOException {
        Path pack = PackFixtures.copyOf("pack-a", host);
        Path target = pack.resolve(ALPHA_FILE);
        byte[] before = Files.readAllBytes(target);
        List<String> namesBefore = fileNames(pack);
        FilesystemPackElementRepository failing = new FilesystemPackElementRepository((temp, to) -> {
            assertThat(temp).as("the temp file is written before the rename").exists();
            throw new IOException("injected failure");
        });

        assertThatThrownBy(() -> failing.write(new PackLocator(pack.toString()), renamedAlpha("Never written")))
                .isInstanceOf(CharterRefusal.class);

        assertThat(Files.readAllBytes(target)).isEqualTo(before);
        assertThat(fileNames(pack)).isEqualTo(namesBefore);
    }

    @Test
    void aTargetThatEscapesThePackIsRefusedWhileAnInRootTargetIsWritten(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-a", host);
        PackLocator location = new PackLocator(pack.toString());
        Path outside = Files.writeString(host.resolve("outside.tactic.yaml"), "id: escape-tactic\n");
        Files.createSymbolicLink(pack.resolve("escape.tactic.yaml"), outside);
        FilesystemPackElementRepository repository = new FilesystemPackElementRepository();

        Tactic escaping = new Tactic(
                "escape-tactic",
                "1.0",
                "Escape",
                List.of(new TacticStep("Only step", Optional.empty(), List.of(), List.of())),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of());
        assertThatThrownBy(() -> repository.write(location, escaping)).isInstanceOf(CharterRefusal.class);
        assertThat(Files.readString(outside)).isEqualTo("id: escape-tactic\n");

        // The same pack, in root: accepted.
        repository.write(location, renamedAlpha("Written in root"));
        assertThat(Files.readString(pack.resolve(ALPHA_FILE))).contains("Written in root");
    }
}
