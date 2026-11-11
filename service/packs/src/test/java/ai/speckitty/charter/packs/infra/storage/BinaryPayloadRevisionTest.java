package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.packs.application.DirectiveRegistrationService;
import ai.speckitty.charter.packs.application.PackMutabilityPolicy;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Acceptance check 11 (post-plan P4): the revision and {@code If-Match} work over a pack whose asset files are binary.
 * A copy of the built-in example pack (ADR-0070; a PNG at {@code assets/logo.png}) sits in a temp storage root behind
 * the production locations, revision store and registration service.
 */
class BinaryPayloadRevisionTest {

    private static final PackIdentifier PACK = new PackIdentifier(ExamplePacks.BUILT_IN);
    private static final String LOGO = "logo.png";

    @Test
    void theRevisionOfAPackWithBinaryPayloadsComputesAndIfMatchFollowsAPayloadByte(@TempDir Path host)
            throws IOException {
        Path packDir = copyOfBuiltInExample(host);
        StorageRootLocations locations = new StorageRootLocations(host);
        YamlSerializationStrategy strategy = new YamlSerializationStrategy();
        DomainRepositoryService repository = new DomainRepositoryService(locations, strategy);
        CommitStorePackWriteStore writeStore =
                new CommitStorePackWriteStore(locations, strategy, new PackCommitStore(locations, strategy));
        DirectiveRegistrationService directives =
                new DirectiveRegistrationService(repository, writeStore, new PackMutabilityPolicy());
        List<String> binaries = repository.load(PACK).assetFiles().stream()
                .filter(file -> !isUtf8(file.payload().bytes()))
                .map(AssetFile::path)
                .toList();
        assertThat(binaries)
                .as("the copy holds binary payloads, so the revision is read over non-UTF-8 bytes")
                .containsExactly(LOGO);
        assertThat(PackFileSet.digested(packDir)).contains("assets/" + LOGO);

        PackRevision current = writeStore.currentRevision(PACK); // no PACK_UNREADABLE
        assertThat(current.token()).matches("sha256:[0-9a-f]{64}");
        assertThatCode(() -> new FileSystemPackSources(locations::all).fingerprint())
                .doesNotThrowAnyException();

        PackRevision registered = directives
                .registerDirective(PACK, directive("BINARY_PROBE_ONE"), false, current.token())
                .revision();
        assertThat(registered).isNotEqualTo(current).isEqualTo(writeStore.currentRevision(PACK));

        PackRevision preEdit = directives
                .registerDirective(PACK, directive("BINARY_PROBE_TWO"), false, registered.token())
                .revision();
        assertThat(preEdit)
                .as("If-Match with the reported revision passed")
                .isEqualTo(writeStore.currentRevision(PACK));

        Path logo = packDir.resolve(PackFileSet.ASSETS + LOGO);
        byte[] edited = Files.readAllBytes(logo);
        edited[edited.length / 2] ^= 0x01;
        Files.write(logo, edited);

        assertThatThrownBy(() ->
                        directives.registerDirective(PACK, directive("BINARY_PROBE_THREE"), false, preEdit.token()))
                .isInstanceOfSatisfying(
                        PreconditionException.class,
                        e -> assertThat(e.kind()).isEqualTo(PreconditionException.Kind.STALE));
        assertThat(repository.load(PACK).directives())
                .extracting(d -> d.id().value())
                .doesNotContain("BINARY_PROBE_THREE");

        PackRevision fresh = writeStore.currentRevision(PACK);
        assertThat(fresh).as("one payload byte moved the revision").isNotEqualTo(preEdit);
        assertThatCode(() -> directives.registerDirective(PACK, directive("BINARY_PROBE_THREE"), false, fresh.token()))
                .doesNotThrowAnyException();
        assertThat(Files.readAllBytes(logo))
                .as("the commit kept the edited payload")
                .isEqualTo(edited);
    }

    /** Copies the built-in example pack, the pack with a binary payload; it depends on no other pack. */
    private static Path copyOfBuiltInExample(Path host) {
        return ExamplePacks.copyOf(ExamplePacks.BUILT_IN, host);
    }

    private static boolean isUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
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
}
