package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackDocumentText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The pack document read (#451) over a real pack directory, through the one resolution
 * {@link DomainRepositoryService} gives every port: the held text, the H1, and the canonical body without frontmatter
 * and title. A README the store refuses is refused here as {@code pack_unreadable}, never normalized.
 */
@DisplayName("Pack document files (#451)")
class PackDocumentFilesTest {

    private static final PackIdentifier PACK = new PackIdentifier("docs");

    private static final String WITH_FRONTMATTER = """
            ---
            audience: pack authors
            ---

            # A Documented Pack

            What the pack is for.

            ## Contents

            Directives and tactics.
            """;

    @TempDir
    Path root;

    @Test
    void aDocumentWithFrontmatterAndTitleServesTheHeldTextTheTitleAndABodyWithoutEither() throws IOException {
        PackDocumentText document = documentOf(WITH_FRONTMATTER).orElseThrow();

        assertThat(document.markdown()).isEqualTo(WITH_FRONTMATTER);
        assertThat(document.title()).contains("A Documented Pack");
        assertThat(document.body()).isEqualTo("""
                        What the pack is for.

                        ## Contents

                        Directives and tactics.
                        """);
    }

    @Test
    void theFrontmatterIsOnlyInTheHeldText() throws IOException {
        PackDocumentText document = documentOf(WITH_FRONTMATTER).orElseThrow();

        assertThat(document.markdown()).contains("audience: pack authors");
        assertThat(document.body()).doesNotContain("audience").doesNotContain("---");
    }

    @Test
    void aDocumentWithNoH1HasNoTitleAndItsWholeTextAsBody() throws IOException {
        PackDocumentText document =
                documentOf("Just words.\n\n## Part\n\nMore.\n").orElseThrow();

        assertThat(document.title()).isEmpty();
        assertThat(document.body()).isEqualTo("Just words.\n\n## Part\n\nMore.\n");
    }

    @Test
    void aTitleOnlyDocumentHasAnEmptyBody() throws IOException {
        PackDocumentText document = documentOf("# Only a title\n").orElseThrow();

        assertThat(document.title()).contains("Only a title");
        assertThat(document.body()).isEmpty();
    }

    @Test
    void anH1FollowedByAThematicBreakIsReadAndItsBodyKeepsTheBreak() throws IOException {
        PackDocumentText document = documentOf("# T\n\n---\n\nx\n").orElseThrow();

        assertThat(document.markdown()).isEqualTo("# T\n\n---\n\nx\n");
        assertThat(document.title()).contains("T");
        assertThat(document.body()).isEqualTo("---\n\nx\n");
    }

    @Test
    void frontmatterWithNoH1AndABodyThatOpensWithABreakIsRead() throws IOException {
        String text = "---\na: 1\n---\n\n---\n\nx\n";
        PackDocumentText document = documentOf(text).orElseThrow();

        assertThat(document.markdown()).isEqualTo(text);
        assertThat(document.title()).isEmpty();
        assertThat(document.body()).isEqualTo("---\n\nx\n");
    }

    @Test
    void frontmatterWithNoH1HasTheTextBelowItAsBody() throws IOException {
        PackDocumentText document =
                documentOf("---\na: 1\n---\n\nWords.\n\n## Part\n\nMore.\n").orElseThrow();

        assertThat(document.title()).isEmpty();
        assertThat(document.body()).isEqualTo("Words.\n\n## Part\n\nMore.\n");
    }

    @Test
    void anEmptyReadmeHasNoTitleAndNoBody() throws IOException {
        PackDocumentText document = documentOf("").orElseThrow();

        assertThat(document.title()).isEmpty();
        assertThat(document.body()).isEmpty();
    }

    @Test
    void aPackWithoutAReadmeHoldsNoDocument() throws IOException {
        DomainRepositoryService repository = repository(null);

        assertThat(repository.document(PACK)).isEmpty();
        assertThat(repository.holdsDocument(PACK)).isFalse();
    }

    @Test
    void aPackWithAReadmeHoldsADocument() throws IOException {
        assertThat(repository("# T\n").holdsDocument(PACK)).isTrue();
    }

    @Test
    void anUnknownPackIsNotFound() throws IOException {
        DomainRepositoryService repository = repository("# T\n");

        assertThatThrownBy(() -> repository.document(new PackIdentifier("nobody")))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND));
    }

    @Test
    void aReadmeWithACarriageReturnIsRefusedNotNormalized() throws IOException {
        DomainRepositoryService repository = repository("# T\r\n\r\nWords.\r\n");

        assertThatThrownBy(() -> repository.document(PACK)).isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refusal.getMessage()).contains("docs/README.md");
        });
    }

    @Test
    void aReadmeThatIsNotUtf8IsRefused() throws IOException {
        Path pack = pack();
        Files.write(pack.resolve("README.md"), new byte[] {'#', ' ', (byte) 0xC3, (byte) 0x28, '\n'});

        assertThatThrownBy(() -> repositoryOver().document(PACK))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    private Optional<PackDocumentText> documentOf(String readme) throws IOException {
        return repository(readme).document(PACK);
    }

    private DomainRepositoryService repository(String readme) throws IOException {
        Path pack = pack();
        if (readme != null) {
            Files.writeString(pack.resolve("README.md"), readme, StandardCharsets.UTF_8);
        }
        return repositoryOver();
    }

    private DomainRepositoryService repositoryOver() {
        return new DomainRepositoryService(
                () -> List.of(new PackLocation("docs", PackLevel.ORG, root.resolve("docs"))),
                new YamlSerializationStrategy());
    }

    private Path pack() throws IOException {
        Path pack = Files.createDirectories(root.resolve("docs"));
        Files.writeString(
                pack.resolve("pack.yaml"),
                "pack_id: 01M65MBWPACKD0CVMENT000000\npack_version: 0.1.0\nname: docs\npack_level: org\n");
        return pack;
    }
}
