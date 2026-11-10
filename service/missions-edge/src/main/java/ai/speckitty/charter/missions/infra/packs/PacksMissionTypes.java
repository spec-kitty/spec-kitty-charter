package ai.speckitty.charter.missions.infra.packs;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.missions.application.MissionTypeHolding;
import ai.speckitty.charter.missions.application.PackMissionTypes;
import ai.speckitty.charter.missions.application.StoredFile;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.packs.application.MissionTypeHoldingRead;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Template;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The one bean of {@link PackMissionTypes}: it asks the packs slice's composite read
 * ({@link MissionTypeHoldingRead}) and turns its answer into the missions slice's own. The packs rings hold a mission
 * type as a {@link CharterArtifact}; the pack store binds it to {@link MissionType}, so any other artifact here is a
 * bug, never a refusal.
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
        MissionTypeHoldingRead.Holding held = reads.holding(pack, missionTypeId);
        MissionType type = narrowed(held.missionType());
        String prefix = type.localId() + "/";
        Map<String, MissionTypeHolding.StepFiles> files = new LinkedHashMap<>();
        held.stepFiles()
                .forEach((step, stored) -> files.put(
                        step, new MissionTypeHolding.StepFiles(stored(stored.prompt()), stored(stored.guidelines()))));
        return new MissionTypeHolding(
                type,
                held.skeletons().stream()
                        .map(template -> skeleton(template, prefix))
                        .toList(),
                held.stepContracts().stream()
                        .map(contract -> new MissionTypeHolding.BoundContract(contract.localId(), contract.action()))
                        .toList(),
                files,
                held.fingerprint());
    }

    /** The mission type the pack store bound; anything else is a bug of the store. */
    static MissionType narrowed(CharterArtifact artifact) {
        if (artifact instanceof MissionType type) {
            return type;
        }
        throw new IllegalStateException(
                "the packs read a mission type as " + artifact.getClass().getName());
    }

    /** A skeleton: its template id without the {@code <type>/} prefix, which a mission type's template id starts with. */
    private static MissionTypeHolding.StoredSkeleton skeleton(Template template, String prefix) {
        String id = template.localId();
        if (!id.startsWith(prefix)) {
            throw new IllegalStateException("a template of the type outside its directory: " + id);
        }
        return new MissionTypeHolding.StoredSkeleton(
                id.substring(prefix.length()), StoredFile.of(template.content().bytes()));
    }

    private static Optional<StoredFile> stored(Optional<Payload> payload) {
        return payload.map(bytes -> StoredFile.of(bytes.bytes()));
    }
}
