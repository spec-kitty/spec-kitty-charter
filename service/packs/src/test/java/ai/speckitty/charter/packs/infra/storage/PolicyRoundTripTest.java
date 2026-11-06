package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The mutability policy file through the whole-pack store (ADR-0063): a pack whose descriptor names {@code policy.yaml}
 * is written with the file, so the stored pack keeps its locks and stays readable; a named file that is missing or
 * invalid refuses the write and leaves no target. A pack without the key is the control: its round trip is unchanged.
 */
@DisplayName("Policy round trip (ADR-0063)")
class PolicyRoundTripTest {

    private static final String PACK_YAML =
            "pack_id: 01M60H1A0000000000000000PQ\npack_version: 0.1.0\nname: qa\npack_level: project\n";
    private static final String NAMES_POLICY = "mutability_policy: policy.yaml\n";
    private static final String POLICY =
            "schema_version: 1\nkind_defaults: {tactic: extendable}\nartifacts: {directive:X: locked}\n";

    private final WholePackStore store = new WholePackStore(new YamlSerializationStrategy());

    private static Path pack(Path host, String descriptor, Optional<String> policy) throws IOException {
        Path pack = Files.createDirectories(host.resolve("source").resolve("qa"));
        Files.writeString(pack.resolve("pack.yaml"), descriptor);
        if (policy.isPresent()) {
            Files.writeString(pack.resolve("policy.yaml"), policy.get());
        }
        return pack;
    }

    private static MutabilityPolicySource policyOf(Path pack) {
        PackLocation location = new PackLocation("qa", PackLevel.PROJECT, pack);
        return new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy())
                .policyOf(new PackIdentifier("qa"));
    }

    @Test
    @DisplayName("a named policy file is modelled, written after pack.yaml and reloads with the same levels")
    void aNamedPolicyFileRoundTripsWithItsLevels(@TempDir Path host) throws IOException {
        Path source = pack(host, PACK_YAML + NAMES_POLICY, Optional.of(POLICY));
        WholePackStore.LoadedPack loaded = store.load(source);

        assertThat(loaded.pack().violations()).isEmpty();
        assertThat(loaded.unmodelled()).doesNotContain("policy.yaml");
        assertThat(loaded.policy()).contains(POLICY);

        Path target = host.resolve("written").resolve("qa");
        store.write(loaded, target);

        assertThat(Files.readString(target.resolve("policy.yaml"))).isEqualTo(POLICY);
        assertThat(policyOf(target)).isEqualTo(policyOf(source));
        assertThat(policyOf(target).levelOf(new Urn(NodeKind.DIRECTIVE, "X"))).isEqualTo(MutabilityLevel.LOCKED);
        WholePackStore.LoadedPack reloaded = store.load(target);
        assertThat(reloaded.pack().descriptor()).isEqualTo(loaded.pack().descriptor());
        assertThat(reloaded.policy()).isEqualTo(loaded.policy());
        assertThat(reloaded.unmodelled()).isEqualTo(loaded.unmodelled());
    }

    @Test
    @DisplayName("control: a pack without the key round-trips unchanged, a stray policy.yaml stays unmodelled")
    void aPackWithoutTheKeyRoundTripsUnchanged(@TempDir Path host) throws IOException {
        Path source = pack(host, PACK_YAML, Optional.empty());
        WholePackStore.LoadedPack loaded = store.load(source);
        assertThat(loaded.policy()).isEmpty();

        Path target = host.resolve("written").resolve("qa");
        store.write(loaded, target);

        assertThat(target.resolve("policy.yaml")).doesNotExist();
        assertThat(store.load(target).pack().descriptor())
                .isEqualTo(loaded.pack().descriptor());
        assertThat(policyOf(target)).isEqualTo(MutabilityPolicySource.empty());

        Files.writeString(source.resolve("policy.yaml"), POLICY);
        WholePackStore.LoadedPack stray = store.load(source);
        assertThat(stray.policy())
                .as("a file the descriptor does not name is no policy")
                .isEmpty();
        assertThat(stray.unmodelled()).contains("policy.yaml");
    }

    @Test
    @DisplayName("a named policy file that is missing or invalid refuses the write and leaves no target")
    void aMissingOrInvalidNamedPolicyRefusesTheWrite(@TempDir Path host) throws IOException {
        for (Optional<String> policy :
                List.of(Optional.<String>empty(), Optional.of("schema_version: 1\npins: []\n"))) {
            Path root = Files.createDirectories(host.resolve(policy.isPresent() ? "invalid" : "missing"));
            WholePackStore.LoadedPack loaded = store.load(pack(root, PACK_YAML + NAMES_POLICY, policy));

            assertThat(loaded.pack().violations()).singleElement().satisfies(violation -> {
                assertThat(violation.file()).isEqualTo("policy.yaml");
                assertThat(violation.reason()).contains(policy.isPresent() ? "pins" : "is missing");
            });
            Path target = root.resolve("written").resolve("qa");
            assertThatThrownBy(() -> store.write(loaded, target))
                    .isInstanceOfSatisfying(
                            CharterRefusal.class,
                            refused -> assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED));
            assertThat(target).doesNotExist();
        }
    }

    @Test
    @DisplayName("a loaded pack that names a policy file but does not carry it is refused, naming the file")
    void aLoadedPackThatDropsItsPolicyIsRefused(@TempDir Path host) throws IOException {
        WholePackStore.LoadedPack loaded = store.load(pack(host, PACK_YAML + NAMES_POLICY, Optional.of(POLICY)));
        WholePackStore.LoadedPack dropped = new WholePackStore.LoadedPack(
                loaded.pack(), loaded.layout(), loaded.graphs(), loaded.unmodelled(), loaded.manifest());
        Path target = host.resolve("written").resolve("qa");

        assertThatThrownBy(() -> store.write(dropped, target)).isInstanceOfSatisfying(CharterRefusal.class, refused -> {
            assertThat(refused.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
            assertThat(refused.getMessage()).contains("mutability_policy policy.yaml");
        });
        assertThat(target).doesNotExist();
    }
}
