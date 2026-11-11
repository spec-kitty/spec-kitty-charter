package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * NFR-001: a pack that holds no {@code enhancements/} directory keeps the source fingerprint it had before the
 * step-enhancement binding existed. The invariant is computed live: for every example pack (ADR-0070) that holds no
 * {@code enhancements/} directory, the digest with every kind bound ({@code digestOfPack}) equals the digest of the
 * files that remain when the {@link ArtifactKind#STEP_ENHANCEMENT} binding is left out. A pack that holds step
 * enhancements is not covered; there the binding must move the digest, which the test also asserts, so a binding that
 * digested nothing could not pass.
 *
 * <p>No digest of the example packs is frozen here, so a change to an example pack needs no recapture. This check is
 * relative: both sides go through the same digest code, so it cannot see a change to the hash, the encoding or the
 * order of the kinds. {@link PackDigestFixtureTest} guards those with a fixed fixture pack and a frozen fingerprint.
 * If this goes red, the binding changed the digest of a pack without step enhancements: fix the code (append, never
 * insert).
 */
@DisplayName("Packs without step enhancements keep their fingerprint (NFR-001)")
class PackDigestBaselineTest {

    private static final Set<ArtifactKind> WITHOUT_STEP_ENHANCEMENTS = withoutStepEnhancements();

    private static Set<ArtifactKind> withoutStepEnhancements() {
        Set<ArtifactKind> kinds = EnumSet.copyOf(KindBindings.kinds());
        kinds.remove(ArtifactKind.STEP_ENHANCEMENT);
        return kinds;
    }

    @Test
    void everyPackWithoutEnhancementsKeepsItsDigest() throws IOException {
        List<String> covered = new ArrayList<>();
        for (String name : ExamplePacks.names()) {
            PackLocation pack = new PackLocation(name, ExamplePacks.level(name), ExamplePacks.pack(name));
            List<String> withoutBinding = PackFileSet.digested(pack.root(), WITHOUT_STEP_ENHANCEMENTS);
            if (holdsEnhancements(pack.root())) {
                assertThat(PackFileSet.digested(pack.root()))
                        .as("%s: the binding digests the step enhancements the pack holds", pack.name())
                        .containsAll(withoutBinding)
                        .hasSizeGreaterThan(withoutBinding.size());
            } else {
                covered.add(pack.name());
                assertThat(FileSystemPackSources.digestOfPack(pack.root(), pack.name()))
                        .as("%s: binding step enhancements moved the digest of a pack that has none", pack.name())
                        .isEqualTo(FileSystemPackSources.digest(List.of(pack), root -> withoutBinding));
            }
        }

        assertThat(covered)
                .as("every example pack but the two that hold step enhancements is covered")
                .containsExactly(ExamplePacks.PROJECT);
    }

    @Test
    @DisplayName("failing control: one changed byte in the project example moves its digest")
    void aChangedFileMovesTheDigest(@TempDir Path scratch) throws IOException {
        String unchanged =
                FileSystemPackSources.digestOfPack(ExamplePacks.pack(ExamplePacks.PROJECT), ExamplePacks.PROJECT);
        Path copy = ExamplePacks.copyOf(ExamplePacks.PROJECT, scratch);
        Path directive = copy.resolve("directives/PROJECT_001.directive.yaml");
        Files.writeString(
                directive, Files.readString(directive, StandardCharsets.UTF_8) + "\n", StandardCharsets.UTF_8);

        assertThat(FileSystemPackSources.digestOfPack(copy, ExamplePacks.PROJECT))
                .isNotEqualTo(unchanged);
        assertThat(FileSystemPackSources.digestOfPack(
                        ExamplePacks.copyOf(ExamplePacks.PROJECT, scratch.resolve("again")), ExamplePacks.PROJECT))
                .as("an unchanged copy digests alike: the digest is of the content, not the location")
                .isEqualTo(unchanged);
    }

    private static boolean holdsEnhancements(Path pack) throws IOException {
        try (Stream<Path> walk = Files.walk(pack)) {
            return walk.anyMatch(path ->
                    Files.isDirectory(path) && path.getFileName().toString().equals("enhancements"));
        }
    }
}
