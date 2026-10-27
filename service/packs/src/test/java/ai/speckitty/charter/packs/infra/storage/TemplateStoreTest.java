package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Template;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Templates through {@link FilesystemPackStore} and {@link TemplateBinding} (US2, FR-007, FR-013): a file under the
 * pack's top-level {@code templates/} is read as bytes with its path as its id and written back byte for byte; a
 * mission template is not one.
 */
@DisplayName("Templates in the per-pack store")
class TemplateStoreTest {

    private static final PackIdentifier SOURCE = new PackIdentifier("source");
    private static final PackIdentifier TARGET = new PackIdentifier("target");
    private static final String MINUTES = "writing/meeting-minutes.md";

    /** Frontmatter, CR/LF pairs, a NUL, a byte that is no UTF-8 and no final newline. */
    private static final byte[] TEMPLATE =
            "---\r\ntitle: Minutes\r\n---\r\n# {{ meeting }}\u0000\r\nend".getBytes(StandardCharsets.UTF_8);

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    private static Path pack(Path host, String name) throws IOException {
        Path pack = PackFixtures.copyOf("pack-descriptor-only", host);
        Path renamed = host.resolve(name);
        Files.move(pack, renamed);
        return renamed;
    }

    private static void write(Path pack, String relative, byte[] bytes) throws IOException {
        Path file = pack.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    /** US2 scenario 1: the template is read with its path under templates/ as its id, and written byte for byte. */
    @Test
    void aNestedTemplateIsReadByItsPathAndWrittenByteForByte(@TempDir Path host) throws IOException {
        Path source = pack(host, "source");
        write(source, "templates/" + MINUTES, TEMPLATE);
        write(source, "templates/empty.md", new byte[0]);
        write(source, "templates/latin1.txt", new byte[] {(byte) 0xe9, 't', (byte) 0xe9});
        Path target = pack(host, "target");
        FilesystemPackStore store = new FilesystemPackStore(host, strategy);

        FilesystemPackStore.PackRead read = store.read(SOURCE);
        assertThat(read.violations()).isEmpty();
        assertThat(read.templates())
                .extracting(FilesystemPackStore.TemplateFile::path)
                .containsExactly("empty.md", "latin1.txt", MINUTES);
        for (FilesystemPackStore.TemplateFile file : read.templates()) {
            Template template = TemplateBinding.INSTANCE.toEntity(SOURCE, file.path(), file.content());
            DomainRepositoryService.writeArtifact(store, TARGET, template);
        }

        Template minutes = TemplateBinding.INSTANCE.toEntity(SOURCE, MINUTES, Payload.of(TEMPLATE));
        assertThat(minutes.urn()).isEqualTo(new Urn(NodeKind.TEMPLATE, MINUTES));
        assertThat(TemplateBinding.INSTANCE.pathOf(minutes)).isEqualTo("templates/" + MINUTES);
        for (String file : List.of("templates/" + MINUTES, "templates/empty.md", "templates/latin1.txt")) {
            assertThat(AssetStoreTest.sha256(target.resolve(file)))
                    .as(file)
                    .isEqualTo(AssetStoreTest.sha256(source.resolve(file)));
        }
        assertThat(Files.size(target.resolve("templates/empty.md"))).isZero();
    }

    /** FR-013 with FR-007's positive case on the same pack: a mission template is not a template of the pack. */
    @Test
    void aMissionTemplateIsNotReadAsATemplate(@TempDir Path host) throws IOException {
        Path source = pack(host, "source");
        write(source, "missions/plan/templates/spec.md", TEMPLATE);
        write(source, "templates/top.md", TEMPLATE);

        FilesystemPackStore.PackRead read = new FilesystemPackStore(host, strategy).read(SOURCE);

        assertThat(read.templates())
                .extracting(FilesystemPackStore.TemplateFile::path)
                .containsExactly("top.md");
    }

    /** A template with no recorded place is written at {@code templates/<id>}; a recorded place wins. */
    @Test
    void anUnplacedTemplateGoesToTheTemplatesDirectory() {
        Template template = TemplateBinding.INSTANCE.toEntity(SOURCE, MINUTES, Payload.of(TEMPLATE));

        assertThat(PackLayout.canonical().pathOf(template)).isEqualTo("templates/" + MINUTES);
        assertThat(new PackLayout(Map.of(template.urn(), "templates/elsewhere.md")).pathOf(template))
                .isEqualTo("templates/elsewhere.md");
        assertThat(TemplateBinding.INSTANCE.payloadOf(template)).isEqualTo(Payload.of(TEMPLATE));
    }

    /**
     * F8: a template reached through a link that leaves the pack is a violation and is not read; a link to a file
     * inside the pack, beside it, loads.
     */
    @Test
    void aTemplateLinkThatLeavesThePackIsAViolationWhileOneInsideLoads(@TempDir Path host) throws IOException {
        Path source = pack(host, "source");
        Path outside = host.resolve("outside.md");
        Files.write(outside, TEMPLATE);
        write(source, "templates/real.md", TEMPLATE);
        assumeTrue(
                AssetStoreTest.link(source.resolve("templates/leak.md"), outside),
                "the file system cannot create symbolic links");
        AssetStoreTest.link(source.resolve("templates/inside.md"), source.resolve("templates/real.md"));

        FilesystemPackStore.PackRead read = new FilesystemPackStore(host, strategy).read(SOURCE);

        assertThat(read.templates())
                .extracting(FilesystemPackStore.TemplateFile::path)
                .containsExactly("inside.md", "real.md");
        assertThat(read.violations())
                .containsExactly(
                        new PackElementViolation("templates/leak.md", "unreadable", "the file leaves the pack"));
    }

    /**
     * F7: one file, one owner. A template whose id another rule would classify (a tactic's file name, the excluded
     * plan-field declaration) is refused on save, naming the path and the rule, and nothing is written; a template
     * beside it with an id only the template rule classifies is written.
     */
    @Test
    void aTemplateWhosePathAnotherRuleClassifiesIsRefusedOnSave(@TempDir Path host) throws IOException {
        Path target = pack(host, "target");
        FilesystemPackStore store = new FilesystemPackStore(host, strategy);
        Payload content = Payload.of(TEMPLATE);

        DomainRepositoryService.writeArtifact(
                store, TARGET, TemplateBinding.INSTANCE.toEntity(TARGET, "x.md", content));
        assertThatThrownBy(() -> DomainRepositoryService.writeArtifact(
                        store, TARGET, TemplateBinding.INSTANCE.toEntity(TARGET, "x.tactic.yaml", content)))
                .isInstanceOf(CharterRefusal.class)
                .hasMessage("payload templates/x.tactic.yaml is a pack file of kind tactic, not a template");
        assertThatThrownBy(() -> DomainRepositoryService.writeArtifact(
                        store,
                        TARGET,
                        TemplateBinding.INSTANCE.toEntity(TARGET, "plan-field-declaration.yaml", content)))
                .isInstanceOf(CharterRefusal.class)
                .hasMessage("payload templates/plan-field-declaration.yaml is excluded by"
                        + " **/templates/plan-field-declaration.yaml, not a template");

        assertThat(Files.readAllBytes(target.resolve("templates/x.md"))).isEqualTo(TEMPLATE);
        assertThat(target.resolve("templates/x.tactic.yaml")).doesNotExist();
        assertThat(target.resolve("templates/plan-field-declaration.yaml")).doesNotExist();
    }
}
