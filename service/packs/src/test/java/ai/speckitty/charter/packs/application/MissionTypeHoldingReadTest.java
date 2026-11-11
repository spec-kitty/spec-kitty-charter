package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.ContractStep;
import ai.speckitty.charter.packs.domain.MissionStepContract;
import ai.speckitty.charter.packs.domain.MissionStepContractIdentifier;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCompanionFiles;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Template;
import ai.speckitty.charter.packs.domain.TemplateIdentifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The composite read of one mission type (#99): the pack is judged first, then the type, and inside the same verified
 * read the read takes the type's skeletons, the step contracts bound to it and the stored files of its steps, so all of
 * them carry the one fingerprint the read verified.
 */
class MissionTypeHoldingReadTest {

    private static final PackIdentifier PACK = new PackIdentifier("main");
    private static final PackIdentifier BARE = new PackIdentifier("bare");
    private static final String D1 = "sha256:" + "1".repeat(64);
    private static final String D2 = "sha256:" + "2".repeat(64);

    private static final PackRevision REVISION = PackRevision.compute(List.of("main at one state"));

    private final List<String> asked = new ArrayList<>();
    private final List<String> revisionsAsked = new ArrayList<>();
    private boolean revisionUnreadable;

    private final Pack main = Pack.builder()
            .id(PACK)
            .descriptor(descriptor(PACK))
            .artifact(new MissionTypeStub("software-dev"))
            .artifact(new MissionTypeStub("documentation"))
            .artifact(template("software-dev/spec-template.md", "software-dev"))
            .artifact(template("software-dev/a~1b.md", "software-dev"))
            .artifact(template("documentation/divio/howto-template.md", "documentation"))
            .artifact(template("software-dev.md", null))
            .artifact(contract("specify", "specify", "software-dev"))
            .artifact(contract("another", "specify", "software-dev"))
            .artifact(contract("orphan", "gather", "research"))
            .build();

    private final PackCompanionFiles companions = (pack, type) -> {
        asked.add(pack.value() + " " + type.localId());
        return Map.of(
                "specify",
                new PackCompanionFiles.StepFiles(
                        Optional.of(Payload.of("# Specify\n".getBytes(StandardCharsets.UTF_8))), Optional.empty()));
    };

    @Test
    void holdsTheTypeItsSkeletonsTheContractsBoundToItAndItsStepFilesAtOneFingerprint() {
        MissionTypeHoldingRead.Holding holding = read(() -> D1).holding(PACK, "software-dev");

        assertThat(holding.missionType().localId()).isEqualTo("software-dev");
        assertThat(holding.skeletons())
                .extracting(Template::localId)
                .containsExactly("software-dev/a~1b.md", "software-dev/spec-template.md");
        assertThat(holding.stepContracts())
                .extracting(MissionStepContract::localId)
                .containsExactly("another", "specify");
        assertThat(holding.stepFiles().get("specify").prompt()).isPresent();
        assertThat(asked).containsExactly("main software-dev");
        assertThat(holding.fingerprint()).isEqualTo(D1);
    }

    @Test
    void theHoldingReadNeverComputesAPackRevision() {
        read(() -> D1).holding(PACK, "software-dev");

        assertThat(revisionsAsked)
                .as("an activation read shares this read and answers no pack revision")
                .isEmpty();
    }

    @Test
    void theRevisedReadHoldsTheSameHoldingAndThePackRevisionInsideOneVerifiedRead() {
        AtomicInteger digests = new AtomicInteger();
        MissionTypeHoldingRead reads = read(() -> {
            assertThat(revisionsAsked)
                    .as("the revision is read between the two digests")
                    .hasSize(digests.getAndIncrement());
            return D1;
        });

        Revised<MissionTypeHoldingRead.Holding> revised = reads.revised(PACK, "software-dev");

        assertThat(revised.revision()).isEqualTo(REVISION);
        assertThat(revised.value().fingerprint()).isEqualTo(D1);
        assertThat(revised.value().stepContracts())
                .extracting(MissionStepContract::localId)
                .containsExactly("another", "specify");
        assertThat(revisionsAsked).containsExactly("main");
    }

    @Test
    void theRevisedReadRefusesAsTheHoldingReadAndChangesStaleInput() {
        assertThat(codeOf(() -> read(() -> D1).revised(new PackIdentifier("nope"), "x")))
                .isEqualTo(RefusalCode.PACK_NOT_FOUND);
        assertThat(codeOf(() -> read(() -> D1).revised(PACK, "research-x")))
                .isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND);
        AtomicInteger digests = new AtomicInteger();
        assertThat(codeOf(() ->
                        read(() -> digests.incrementAndGet() == 1 ? D1 : D2).revised(PACK, "software-dev")))
                .isEqualTo(RefusalCode.STALE_INPUT);
    }

    @Test
    void aRevisionTheStorageCannotReadIsAPackThatIsNotThere() {
        revisionUnreadable = true;

        CharterRefusal refusal =
                catchThrowableOfType(CharterRefusal.class, () -> read(() -> D1).revised(PACK, "software-dev"));

        assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND);
        assertThat(refusal.getCause()).isInstanceOf(CharterRefusal.class);
    }

    @Test
    void anotherTypeHoldsOnlyItsOwnSkeletonsAndNoContract() {
        MissionTypeHoldingRead.Holding holding = read(() -> D1).holding(PACK, "documentation");

        assertThat(holding.skeletons())
                .extracting(Template::localId)
                .containsExactly("documentation/divio/howto-template.md");
        assertThat(holding.stepContracts()).isEmpty();
    }

    @Test
    void theRefusalsComeInOrderThePackThenTheType() {
        MissionTypeHoldingRead reads = read(() -> D1);

        assertThat(codeOf(() -> reads.holding(new PackIdentifier("nope"), "research-x")))
                .isEqualTo(RefusalCode.PACK_NOT_FOUND);
        assertThat(codeOf(() -> reads.holding(BARE, "research-x"))).isEqualTo(RefusalCode.PACK_UNREADABLE);
        CharterRefusal absent = catchThrowableOfType(CharterRefusal.class, () -> reads.holding(PACK, "research-x"));
        assertThat(absent.code()).isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND);
        assertThat(absent.getMessage()).contains("Pack main has no mission type research-x.");
        assertThat(asked).isEmpty();
    }

    @Test
    void inputsThatChangeWhileTheTypeIsReadAreStale() {
        AtomicInteger digests = new AtomicInteger();

        assertThat(codeOf(() ->
                        read(() -> digests.incrementAndGet() == 1 ? D1 : D2).holding(PACK, "software-dev")))
                .isEqualTo(RefusalCode.STALE_INPUT);
    }

    private MissionTypeHoldingRead read(ai.speckitty.charter.packs.domain.PackSources sources) {
        PackRepository packs = pack -> {
            if (pack.equals(PACK)) {
                return main;
            }
            if (pack.equals(BARE)) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack bare has no pack.yaml");
            }
            throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.");
        };
        PackDescriptorRepository descriptors = new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier pack) {
                return find(pack).orElseThrow();
            }

            @Override
            public Optional<PackDescriptor> find(PackIdentifier pack) {
                if (pack.equals(BARE)) {
                    return Optional.empty();
                }
                return Optional.of(packs.load(pack).descriptor());
            }

            @Override
            public void save(PackIdentifier pack, PackDescriptor descriptor) {
                throw new UnsupportedOperationException();
            }
        };
        PackRevisions revisions = pack -> {
            revisionsAsked.add(pack.value());
            if (revisionUnreadable) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack.yaml unreadable");
            }
            return REVISION;
        };
        return new MissionTypeHoldingRead(packs, descriptors, new ConsistentRead(sources), companions, revisions);
    }

    private static RefusalCode codeOf(Runnable read) {
        return catchThrowableOfType(CharterRefusal.class, read::run).code();
    }

    private static PackDescriptor descriptor(PackIdentifier pack) {
        return new PackDescriptor(
                pack.value(), "1.0.0", Optional.empty(), pack.value(), Optional.empty(), Optional.empty());
    }

    private static Template template(String id, @Nullable String missionType) {
        return Template.builder()
                .id(new TemplateIdentifier(id))
                .packId(PACK)
                .content(Payload.of(id.getBytes(StandardCharsets.UTF_8)))
                .missionType(missionType)
                .build();
    }

    private static MissionStepContract contract(String id, String action, String mission) {
        return MissionStepContract.builder()
                .id(new MissionStepContractIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .action(action)
                .mission(mission)
                .step(new ContractStep(
                        "first", "The first step", Optional.empty(), List.of(), Optional.empty(), Optional.empty()))
                .build();
    }

    /** A mission type as the packs rings hold it: an artifact of kind {@code mission_type}. */
    private record MissionTypeStub(String localId) implements CharterArtifact {

        @Override
        public ArtifactKind kind() {
            return ArtifactKind.MISSION_TYPE;
        }

        @Override
        public PackIdentifier packId() {
            return PACK;
        }

        @Override
        public Optional<String> schemaVersion() {
            return Optional.of("1.0");
        }

        @Override
        public String label() {
            return localId;
        }

        @Override
        public Optional<String> description() {
            return Optional.empty();
        }
    }
}
