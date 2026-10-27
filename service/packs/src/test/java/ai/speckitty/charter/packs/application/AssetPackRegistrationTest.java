package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.infra.storage.CommitStorePackWriteStore;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.PackCommitStore;
import ai.speckitty.charter.packs.infra.storage.StorageRootLocations;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Acceptance check 11 (post-plan P3, FR-012): a registration into a pack that holds asset files keeps them. It runs
 * through the real {@link DirectiveRegistrationService} over the real {@link DomainRepositoryService} and commit store
 * on a temp pack holding a sidecar with its payload, a sidecar-less payload and a top-level template; the same
 * fixture's hand-built pack without the asset file is the negative control.
 */
class AssetPackRegistrationTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    /** A PNG signature, a NUL, CR/LF pairs, bytes that are no UTF-8, and no final newline. */
    private static final byte[] LOGO = {
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, '\r', '\n', (byte) 0xff, (byte) 0xfe, 'e', 'n', 'd'
    };

    /** The fixture pack's files, relative to the pack directory. */
    private static final Map<String, byte[]> FILES = files();

    private static Map<String, byte[]> files() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(
                "pack.yaml",
                utf8("pack_id: 01M5DEH8ASSETREGISTRATION0\npack_version: 0.1.0\nname: pack-a\npack_level: org\n"));
        files.put("assets/debrief/logo.png.asset.yaml", utf8("id: logo\nmime: image/png\npath: debrief/logo.png\n"));
        files.put("assets/debrief/logo.png", LOGO);
        files.put("assets/lint.config.yaml", utf8("rules:\r\n  - a\r\n"));
        files.put("templates/writing/minutes.md", utf8("# Minutes\n\n- {{date}}\n"));
        return Map.copyOf(files);
    }

    @Test
    void aDirectiveRegisteredIntoAPackHoldingAssetFilesKeepsThemAndAdvancesTheRevision(@TempDir Path host)
            throws IOException {
        Path packDir = fixture(host);
        Wiring wiring = new Wiring(host);
        Map<String, String> payloadDigests =
                digests(packDir, "assets/debrief/logo.png", "assets/lint.config.yaml", "templates/writing/minutes.md");
        PackRevision before = wiring.writeStore.currentRevision(PACK);

        DirectiveRegistrationService.Registered registered =
                wiring.directives.registerDirective(PACK, directive("GUARD"), List.of(), false, before.token());

        assertThat(registered.created()).isTrue();
        assertThat(registered.revision()).as("the revision advanced").isNotEqualTo(before);
        assertThat(registered.revision()).isEqualTo(wiring.writeStore.currentRevision(PACK));
        Pack reloaded = wiring.repository.load(PACK);
        assertThat(reloaded.violations()).isEmpty();
        assertThat(reloaded.directives()).extracting(d -> d.id().value()).containsExactly("GUARD");
        assertThat(reloaded.assetFiles())
                .extracting(AssetFile::path)
                .containsExactly("debrief/logo.png", "lint.config.yaml");
        assertThat(reloaded.assetFiles().getFirst().payload().bytes()).isEqualTo(LOGO);
        assertThat(reloaded.assets()).extracting(Asset::localId).containsExactly("logo");
        assertThat(reloaded.templates()).extracting(Template::localId).containsExactly("writing/minutes.md");
        assertThat(digests(packDir, payloadDigests.keySet().toArray(String[]::new)))
                .as("the payload bytes on disk are unchanged")
                .isEqualTo(payloadDigests);
    }

    /**
     * The negative control on the same fixture: the loaded pack is sound (it adds a directive), while a hand-built
     * pack holding the same artifacts but not the asset file breaches, which is what a registration that dropped the
     * asset files would hit.
     */
    @Test
    void aHandBuiltPackWithoutTheAssetFileBreachesWhileTheLoadedPackAcceptsADirective(@TempDir Path host)
            throws IOException {
        fixture(host);
        Pack loaded = new Wiring(host).repository.load(PACK);

        assertThat(loaded.with(directive("GUARD")).assetFiles()).isEqualTo(loaded.assetFiles());

        Pack.Builder withoutAssetFile = Pack.builder().id(loaded.id()).descriptor(loaded.descriptor());
        loaded.artifacts().forEach(withoutAssetFile::artifact);
        loaded.assetFiles().stream()
                .filter(file -> !file.path().equals("debrief/logo.png"))
                .forEach(withoutAssetFile::assetFile);
        assertThatThrownBy(withoutAssetFile::build)
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessageContaining("debrief/logo.png");
    }

    // ---- fixture and wiring --------------------------------------------------------------------------------------

    private static Path fixture(Path host) throws IOException {
        Path packDir = host.resolve(PACK.value());
        for (Map.Entry<String, byte[]> file : FILES.entrySet()) {
            Path target = packDir.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, file.getValue());
        }
        return packDir;
    }

    /** The production wiring over the packs under {@code host}, with the real ADR-0012 gate (every target open). */
    private static final class Wiring {
        private final DomainRepositoryService repository;
        private final CommitStorePackWriteStore writeStore;
        private final DirectiveRegistrationService directives;

        Wiring(Path host) {
            StorageRootLocations locations = new StorageRootLocations(host);
            YamlSerializationStrategy strategy = new YamlSerializationStrategy();
            this.repository = new DomainRepositoryService(locations, strategy);
            this.writeStore =
                    new CommitStorePackWriteStore(locations, strategy, new PackCommitStore(locations, strategy));
            this.directives = new DirectiveRegistrationService(repository, writeStore, new PackMutabilityPolicy());
        }
    }

    private static Directive directive(String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title(id)
                .intent("Intent of " + id)
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    private static Map<String, String> digests(Path packDir, String... files) throws IOException {
        Map<String, String> digests = new LinkedHashMap<>();
        for (String file : files) {
            digests.put(file, sha256(Files.readAllBytes(packDir.resolve(file))));
        }
        return digests;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
