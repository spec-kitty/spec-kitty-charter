package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.missions.domain.GuardEntry;
import ai.speckitty.charter.missions.domain.Lifecycle;
import ai.speckitty.charter.missions.domain.LifecycleTransition;
import ai.speckitty.charter.missions.domain.MissionType;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.Guard;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.ReferenceFacts;
import ai.speckitty.charter.packs.domain.rules.ReferenceClosure;
import java.util.List;
import java.util.Optional;

/**
 * What the reference closure ({@link ReferenceClosure}) judges of an artifact, as the packs domain can name it (#355): a
 * lifecycle and a mission type are the missions slice's values, which the packs domain does not name
 * ({@code SLICES_DO_NOT_CROSS}), so the storage edge gives the rule their guard entries and output templates. It decides
 * nothing; the rule does.
 */
final class ReferenceSubjects {

    private ReferenceSubjects() {}

    /**
     * The subject of an artifact the closure judges.
     *
     * @param artifact any artifact
     * @return its subject; empty for a kind the closure does not judge
     */
    static Optional<ReferenceFacts.Subject> of(CharterArtifact artifact) {
        return switch (artifact) {
            case Asset asset -> Optional.of(new ReferenceFacts.AssetSubject(asset));
            case Guard guard -> Optional.of(new ReferenceFacts.GuardSubject(guard));
            case Lifecycle lifecycle -> Optional.of(lifecycle(lifecycle));
            case MissionType type -> Optional.of(missionType(type));
            default -> Optional.empty();
        };
    }

    /**
     * The subjects of an aggregate's artifacts, in its order.
     *
     * @param pack the aggregate
     * @return the subjects
     */
    static List<ReferenceFacts.Subject> of(Pack pack) {
        return pack.artifacts().stream()
                .flatMap(artifact -> of(artifact).stream())
                .toList();
    }

    /**
     * The closure over an aggregate: its artifacts, its asset payloads and its mission types' own templates.
     *
     * @param pack the aggregate
     * @return the closure
     */
    static ReferenceClosure closure(Pack pack) {
        return ReferenceClosure.of(
                of(pack),
                pack.assetFiles().stream()
                        .map(ai.speckitty.charter.packs.domain.AssetFile::path)
                        .collect(java.util.stream.Collectors.toSet()),
                ReferenceClosure.namable(pack.templates()),
                EmbeddedSchemas.PORT);
    }

    /**
     * The finding a write refuses an aggregate with, when one of its artifacts does not close: the first mission type's,
     * then the first other artifact's, in the aggregate's order.
     *
     * @param pack the aggregate as it will be written
     * @return the finding; empty when every artifact closes
     */
    static Optional<ReferenceClosure.Finding> firstRefusal(Pack pack) {
        List<ReferenceClosure.Finding> findings = closure(pack).findings(of(pack));
        return findings.stream()
                .filter(finding -> finding.subject() instanceof ReferenceFacts.MissionTypeView)
                .findFirst()
                .or(() -> findings.stream().findFirst());
    }

    private static ReferenceFacts.LifecycleView lifecycle(Lifecycle lifecycle) {
        return new ReferenceFacts.LifecycleView(
                lifecycle.urn(),
                lifecycle.localId(),
                lifecycle.transitions().stream()
                        .map(ReferenceSubjects::transition)
                        .toList());
    }

    private static ReferenceFacts.TransitionView transition(LifecycleTransition transition) {
        return new ReferenceFacts.TransitionView(
                transition.describe(),
                transition.guards().stream().map(ReferenceSubjects::entry).toList());
    }

    private static ReferenceFacts.GuardEntryView entry(GuardEntry entry) {
        return new ReferenceFacts.GuardEntryView(
                entry.ref().crossPack(), entry.ref().id(), entry.ref().spelling(), entry.config());
    }

    private static ReferenceFacts.MissionTypeView missionType(MissionType type) {
        return new ReferenceFacts.MissionTypeView(
                type.urn(),
                type.localId(),
                type.templateReferences().stream()
                        .map(reference -> new ReferenceFacts.OutputTemplate(
                                reference.step(), reference.templatePath(), reference.templateId(type.id())))
                        .toList());
    }
}
