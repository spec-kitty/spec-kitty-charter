package ai.speckitty.charter.missions.infra.packs;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.missions.application.HeldArtifact;
import ai.speckitty.charter.missions.application.MissionTypeHolding;
import ai.speckitty.charter.missions.application.MissionTypeNarrowing;
import ai.speckitty.charter.missions.application.PackMissionTypes;
import ai.speckitty.charter.missions.application.PackRead;
import ai.speckitty.charter.missions.application.StoredFile;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.packs.application.ArtifactReadService;
import ai.speckitty.charter.packs.application.MissionTypeHoldingRead;
import ai.speckitty.charter.packs.application.Revised;
import ai.speckitty.charter.packs.domain.Payload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * The one bean of {@link PackMissionTypes}: it asks the packs slice's composite read
 * ({@link MissionTypeHoldingRead}) and turns its answer into the missions slice's own. The packs rings hold a mission
 * type as a kernel artifact; the pack store binds it to {@link MissionType}, so any other artifact here is a bug, never a
 * refusal.
 */
@Component
public final class PacksMissionTypes implements PackMissionTypes {

    private final MissionTypeHoldingRead reads;

    /**
     * An adapter over the packs slice's composite read.
     *
     * @param reads the composite read of one mission type
     */
    public PacksMissionTypes(MissionTypeHoldingRead reads) {
        this.reads = Objects.requireNonNull(reads, "reads");
    }

    @Override
    public MissionTypeHolding holding(PackIdentifier pack, String missionTypeId) {
        return holdingOf(reads.holding(pack, missionTypeId), pack, missionTypeId);
    }

    @Override
    public PackRead<MissionTypeHolding> revised(PackIdentifier pack, String missionTypeId) {
        Revised<MissionTypeHoldingRead.Holding> held = reads.revised(pack, missionTypeId);
        MissionTypeHolding holding = holdingOf(held.value(), pack, missionTypeId);
        return new PackRead<>(holding, holding.fingerprint(), held.revision().token());
    }

    private static MissionTypeHolding holdingOf(
            MissionTypeHoldingRead.Holding held, PackIdentifier pack, String missionTypeId) {
        String read = "The read of the mission type " + missionTypeId + " in pack " + pack.value();
        MissionType type = MissionTypeNarrowing.narrowed(held.missionType(), read);
        Map<String, MissionTypeHolding.StepFiles> files = new LinkedHashMap<>();
        held.stepFiles()
                .forEach((step, stored) -> files.put(
                        step, new MissionTypeHolding.StepFiles(stored(stored.prompt()), stored(stored.guidelines()))));
        return new MissionTypeHolding(
                type,
                held.skeletons().stream()
                        .map(template -> MissionTypeHolding.StoredSkeleton.ofTemplate(
                                template.localId(),
                                type.localId(),
                                StoredFile.of(template.content().bytes()),
                                read))
                        .toList(),
                files,
                held.fingerprint(),
                ownCandidates(type, held.packName()));
    }

    /**
     * The candidates of the type's substeps that the pack holds, of a kind the pack-scoped artifact read serves, each by
     * its kind and the candidate as written: one written bare or with the pack's own name, which the pack's load has
     * resolved in the pack (FR-005). One written with another pack's name is resolved in a closure, which a
     * pack-scoped read does not have, so it is not held here, even when the pack holds an artifact of the same id.
     */
    private static Set<HeldArtifact> ownCandidates(MissionType type, String packName) {
        List<ArtifactKind> listed = ArtifactReadService.listed();
        return Stream.concat(type.steps().stream(), type.onDemandSteps().stream())
                .flatMap(step -> step.substeps().stream())
                .flatMap(substep -> substep.delegatesTo().stream())
                .filter(delegation -> listed.contains(delegation.kind()))
                .flatMap(delegation -> delegation.candidates().stream()
                        .filter(candidate ->
                                candidate.packName().map(packName::equals).orElse(true))
                        .map(candidate -> new HeldArtifact(delegation.kind(), candidate.toString())))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Optional<StoredFile> stored(Optional<Payload> payload) {
        return payload.map(bytes -> StoredFile.of(bytes.bytes()));
    }
}
