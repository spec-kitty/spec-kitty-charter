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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Acceptance check 11 (post-plan P4): the revision and {@code If-Match} work over a pack whose asset files are binary.
 * A copy of the pinned open-packs {@code packs/internal} (OTF fonts and a PNG under {@code assets/debrief/}) sits in a temp
 * storage root behind the production locations, revision store and registration service. Runs under
 * {@code :packs:openPacksRoundTrip}, which supplies the checkout.
 */
@Tag("open-packs")
class BinaryPayloadRevisionTest {

    private static final PackIdentifier PACK = new PackIdentifier("internal");
    private static final String LOGO = "debrief/logo.png";

    @Test
    void theRevisionOfAPackWithBinaryPayloadsComputesAndIfMatchFollowsAPayloadByte(@TempDir Path host)
            throws IOException {
        Path packDir = copyOfPinnedInternalPack(host);
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
                .contains(LOGO, "debrief/fonts/FallingSky-Bold.otf");
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

    /**
     * Copies the pinned {@code internal} pack, and the {@code built-in} pack it builds on: a write is judged over every
     * closure the pack joins (IC-15), which cannot compose with the pack's dependency missing.
     */
    private static Path copyOfPinnedInternalPack(Path host) throws IOException {
        OpenPacksCorpus.assertPinned();
        copyOfPinnedPack("built-in", host);
        return copyOfPinnedPack(PACK.value(), host);
    }

    private static Path copyOfPinnedPack(String name, Path host) throws IOException {
        Path source = OpenPacksCorpus.packsDirectory().resolve(name);
        Path target = host.resolve(name);
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.sorted(Comparator.naturalOrder()).toList()) {
                Path to = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(path, to);
                }
            }
        }
        return target;
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
