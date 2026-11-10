package ai.speckitty.charter.missions.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.missions.application.MissionTypeHolding;
import ai.speckitty.charter.missions.application.StoredFile;
import ai.speckitty.charter.missions.domain.MissionStep;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.missions.domain.MissionTypeIdentifier;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.MissionTypeHoldingRead;
import ai.speckitty.charter.packs.domain.ContractStep;
import ai.speckitty.charter.packs.domain.MissionStepContract;
import ai.speckitty.charter.packs.domain.MissionStepContractIdentifier;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCompanionFiles;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.domain.TemplateIdentifier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The adapter over the packs slice's composite read (#99): the mission type the store bound is narrowed to the missions
 * domain's {@link MissionType}, a skeleton is named by its path under the type's {@code templates/}, a step contract by
 * its id and action, a step's files keep their bytes, and the fingerprint is the read's. Any other artifact held as the
 * mission type is a bug, never a refusal.
 */
class PacksMissionTypesTest {

    private static final PackIdentifier PACK = new PackIdentifier("main");
    private static final String FINGERPRINT = "sha256:" + "c".repeat(64);

    @Test
    void theHoldingIsTheMissionsSlicesOwnAtTheReadsFingerprint() {
        MissionType type = MissionType.builder()
                .id(new MissionTypeIdentifier("software-dev"))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Software Development")
                .description("Build software")
                .step(new MissionStep(
                        "specify",
                        "Specification",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        List.of()))
                .build();
        Pack pack = Pack.builder()
                .id(PACK)
                .descriptor(descriptor())
                .artifact(type)
                .artifact(Template.builder()
                        .id(new TemplateIdentifier("software-dev/divio/x.md"))
                        .packId(PACK)
                        .content(Payload.of(new byte[] {(byte) 0xff, 1}))
                        .missionType("software-dev")
                        .build())
                .artifact(MissionStepContract.builder()
                        .id(new MissionStepContractIdentifier("specify-contract"))
                        .packId(PACK)
                        .schemaVersion("1.0")
                        .action("specify")
                        .mission("software-dev")
                        .step(new ContractStep(
                                "first",
                                "The first step",
                                Optional.empty(),
                                List.of(),
                                Optional.empty(),
                                Optional.empty()))
                        .build())
                .build();
        PackCompanionFiles files = (id, held) -> Map.of(
                "specify",
                new PackCompanionFiles.StepFiles(
                        Optional.of(Payload.of("# Specify\n".getBytes(StandardCharsets.UTF_8))), Optional.empty()));

        MissionTypeHolding holding = new PacksMissionTypes(read(pack, files)).holding(PACK, "software-dev");

        assertThat(holding.type()).isSameAs(type);
        assertThat(holding.skeletons())
                .containsExactly(new MissionTypeHolding.StoredSkeleton(
                        "divio/x.md", StoredFile.of(new byte[] {(byte) 0xff, 1})));
        assertThat(holding.stepContracts())
                .containsExactly(new MissionTypeHolding.BoundContract("specify-contract", "specify"));
        assertThat(holding.stepFiles().get("specify").prompt())
                .contains(StoredFile.of("# Specify\n".getBytes(StandardCharsets.UTF_8)));
        assertThat(holding.stepFiles().get("specify").guidelines()).isEmpty();
        assertThat(holding.fingerprint()).isEqualTo(FINGERPRINT);
    }

    @Test
    void aMissionTypeTheStoreDidNotBindToTheMissionsDomainIsABug() {
        CharterArtifact stub = new CharterArtifact() {
            @Override
            public ArtifactKind kind() {
                return ArtifactKind.MISSION_TYPE;
            }

            @Override
            public PackIdentifier packId() {
                return PACK;
            }

            @Override
            public String localId() {
                return "software-dev";
            }

            @Override
            public Optional<String> schemaVersion() {
                return Optional.empty();
            }

            @Override
            public String label() {
                return "Software Development";
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }
        };

        assertThatThrownBy(() -> PacksMissionTypes.narrowed(stub)).isInstanceOf(IllegalStateException.class);
    }

    private static MissionTypeHoldingRead read(Pack pack, PackCompanionFiles files) {
        PackDescriptorRepository descriptors = new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier id) {
                return pack.descriptor();
            }

            @Override
            public Optional<PackDescriptor> find(PackIdentifier id) {
                return Optional.of(pack.descriptor());
            }

            @Override
            public void save(PackIdentifier id, PackDescriptor descriptor) {
                throw new UnsupportedOperationException();
            }
        };
        return new MissionTypeHoldingRead(id -> pack, descriptors, new ConsistentRead(() -> FINGERPRINT), files);
    }

    private static PackDescriptor descriptor() {
        return new PackDescriptor("main", "1.0.0", Optional.empty(), "main", Optional.empty(), Optional.empty());
    }
}
