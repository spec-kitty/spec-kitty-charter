package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.PackDocument;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.StyleguideScope;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.domain.TemplateIdentifier;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.Specialization;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The per-file writes of {@link DomainRepositoryService} keep a pack linked from outside the configuration root
 * read-only, as {@link PackCommitStore} does (#287, ADR-0038 as amended 2026-10-31): each is refused with the commit
 * store's {@code PACK_UNREADABLE} message before anything is written. Read and written through the production
 * {@link StorageRootLocations}.
 */
@DisplayName("Per-file saves into a pack linked from outside the configuration root are refused (#287)")
class ReadOnlyPackSaveTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private static final String READ_ONLY =
            "pack pack-a is linked from outside the configuration root; it is read-only through the service";

    @TempDir
    Path host;

    private final YamlSerializationStrategy strategy = new YamlSerializationStrategy();

    /** One per-file write of the repository service. */
    @FunctionalInterface
    interface Write {
        void into(DomainRepositoryService service);
    }

    static Stream<Arguments> everyWrite() {
        return Stream.of(
                write("save(Tactic)", service -> service.save(tacticOf(service))),
                write(
                        "save(Directive)",
                        service -> service.save(Directive.builder()
                                .id(new DirectiveIdentifier("DIRECTIVE_900"))
                                .packId(PACK)
                                .schemaVersion("1.0")
                                .title("Close the class")
                                .intent("Fix the class of defect.")
                                .enforcement(Enforcement.REQUIRED)
                                .build())),
                write(
                        "save(Styleguide)",
                        service -> service.save(Styleguide.builder()
                                .id(new StyleguideIdentifier("house-style"))
                                .packId(PACK)
                                .schemaVersion("1.0")
                                .title("House style")
                                .scope(StyleguideScope.CODE)
                                .principle("Prefer clarity")
                                .build())),
                write(
                        "save(Toolguide)",
                        service -> service.save(Toolguide.builder()
                                .id(new ToolguideIdentifier("ripgrep"))
                                .packId(PACK)
                                .schemaVersion("1.0")
                                .tool("ripgrep")
                                .title("Use ripgrep")
                                .guide(Guide.of("How to use the tool."))
                                .summary("How to search.")
                                .build())),
                write(
                        "save(Paradigm)",
                        service -> service.save(Paradigm.builder()
                                .id(new ParadigmIdentifier("tdd"))
                                .packId(PACK)
                                .schemaVersion("1.0")
                                .name("TDD")
                                .summary("Tests first.")
                                .build())),
                write(
                        "save(AgentProfile)",
                        service -> service.save(AgentProfile.builder()
                                .id(new AgentProfileIdentifier("implementer"))
                                .packId(PACK)
                                .name("implementer")
                                .role("implementer")
                                .purpose("Implements.")
                                .specialization(new Specialization(
                                        "Focus", Optional.empty(), Optional.empty(), Optional.empty()))
                                .build())),
                write("saveArtifact", service -> service.saveArtifact(PACK, tacticOf(service))),
                write(
                        "saveArtifact(Template)",
                        service -> service.saveArtifact(
                                PACK,
                                Template.builder()
                                        .id(new TemplateIdentifier("writing/plan.md"))
                                        .packId(PACK)
                                        .content(Payload.of(new byte[] {'#'}))
                                        .build())),
                write("save(PackIdentifier, PackDescriptor)", service -> service.save(PACK, service.get(PACK))),
                write(
                        "writeAssetFile",
                        service -> service.writeAssetFile(PACK, new AssetFile("logo.png", Payload.of(new byte[] {1})))),
                write(
                        "writePreset",
                        service -> service.writePreset(
                                PACK, new Preset("house-rules", "The house rules.", List.of(), Map.of(), List.of()))),
                write(
                        "writePackDocument",
                        service -> service.writePackDocument(
                                PACK, new PackDocument(Optional.empty(), Optional.empty(), "", List.of()))));
    }

    private static Arguments write(String name, Write write) {
        return Arguments.of(name, write);
    }

    private static Tactic tacticOf(DomainRepositoryService service) {
        return service.load(PACK).tactics().getFirst();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyWrite")
    void aWriteIntoAPackLinkedFromOutsideTheConfigRootIsRefusedAndChangesNothing(String name, Write write)
            throws IOException {
        Path packs = Files.createDirectories(host.resolve("config/packs"));
        Path target = linkedPack(packs, Files.createDirectories(host.resolve("checkout")));
        DomainRepositoryService service = new DomainRepositoryService(new StorageRootLocations(packs), strategy);
        Map<String, String> before = snapshot(target);

        assertThatThrownBy(() -> write.into(service))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessage(READ_ONLY);

        assertThat(snapshot(target)).as("the target is unchanged").isEqualTo(before);
    }

    @Test
    @DisplayName("a write into a pack of a domain linked from outside the configuration root is refused (#285)")
    void aWriteIntoAPackOfADomainLinkedFromOutsideTheConfigRootIsRefusedAndChangesNothing() throws IOException {
        // One representative write: the read-only refusal is decided in writable() before any port's write dispatch,
        // so every port is equivalent here; the per-port coverage lives in the sibling parameterized test above, and
        // the distinct behaviour this guards — a linked packs/ domain making its packs read-only (#285) — is proven
        // once. The commit surface of the same scenario is in LinkedPackCommitTest.
        Path config = Files.createDirectories(host.resolve("config"));
        Path shared = Files.createDirectories(host.resolve("shared-packs"));
        Path real = PackFixtures.copyOf("pack-a", shared).toRealPath();
        Path packs = SymbolicLinks.create(config.resolve("packs"), shared);
        DomainRepositoryService service = new DomainRepositoryService(new StorageRootLocations(packs), strategy);
        Map<String, String> before = snapshot(real);

        assertThatThrownBy(() -> service.writeAssetFile(PACK, new AssetFile("logo.png", Payload.of(new byte[] {1}))))
                .as("the whole packs/ is linked from outside (#285)")
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                .hasMessage(READ_ONLY);

        assertThat(snapshot(real)).as("the pack is unchanged").isEqualTo(before);
    }

    @Test
    @DisplayName("a pack linked from inside the configuration root is still written through its real target")
    void aPackLinkedFromInsideTheConfigRootIsWrittenThroughItsTarget() throws IOException {
        Path packs = Files.createDirectories(host.resolve("config/packs"));
        // A hidden directory of the pack domain is no pack itself, so a link into it overlaps none.
        Path target = linkedPack(packs, Files.createDirectories(packs.resolve(".vendor")));
        DomainRepositoryService service = new DomainRepositoryService(new StorageRootLocations(packs), strategy);

        service.writeAssetFile(PACK, new AssetFile("logo.png", Payload.of(new byte[] {1})));

        assertThat(Files.isSymbolicLink(packs.resolve("pack-a"))).isTrue();
        assertThat(target.resolve("assets/logo.png")).hasBinaryContent(new byte[] {1});
    }

    /** Links {@code packs/pack-a} to a copy of the pack, under another name, in {@code checkout}; the real target. */
    private static Path linkedPack(Path packs, Path checkout) throws IOException {
        Path target = Files.move(PackFixtures.copyOf("pack-a", checkout), checkout.resolve("real-pack-a"))
                .toRealPath();
        SymbolicLinks.create(packs.resolve("pack-a"), target);
        return target;
    }

    private static Map<String, String> snapshot(Path directory) {
        Map<String, String> out = new TreeMap<>();
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                out.put(directory.relativize(file).toString(), Files.readString(file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }
}
