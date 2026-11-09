package ai.speckitty.charter.activations.domain;

import ai.speckitty.charter.activations.domain.ClosureSnapshot.PresetView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Applies a preset to an activation document (ADR-0059 D6, ADR-0020 D6, FR-016), as a pure function:
 *
 * <ul>
 *   <li>Each key the preset lists replaces that key of the document: each {@code selected.<kind>}, {@code activatedKinds}
 *       and {@code missionTypes}. A key the preset does not list is left as it is, and so are {@code packs} and
 *       {@code languages}, which a preset never names.
 *   <li>A preset lists a key when it holds at least one member for it (A7). The packs read an absent list and an empty
 *       one alike, as an empty one, so an empty list counts as not listed and leaves the document's key untouched.
 *   <li>Without {@code force}, a member the document denies is left out of the replaced list and its deny entry stays.
 *       With {@code force}, the deny entries of the preset's members are removed first (a deny list that empties is
 *       dropped), so the members are selected; they are the change's {@code cleared}.
 *   <li>A kind of the preset's gate that a document cannot gate ({@code template}, {@code asset}) is left out of
 *       {@code activatedKinds}.
 * </ul>
 *
 * <p>A preset never adds a deny entry, so applying one cannot break the floor its own must-have items set. A member the
 * closure does not hold (a stale preset) is selected as the preset says, and {@link DocumentRules} refuses the result
 * with {@code 422 activation_invalid}: a broken preset is a pack fault, not a silent skip.
 *
 * <p>{@code applied} and {@code cleared} are {@link ActivationChange#of}'s, so a replaced key's dropped ids are applied
 * too (A1) and a kind is never an entry. The skipped report is empty: a preset walks nothing.
 */
public final class PresetRules {

    private PresetRules() {}

    /**
     * Applies a preset.
     *
     * @param before the stored document less the ids its closure no longer declares (ADR-0059 amendment R5)
     * @param preset the preset, from the activation's closure
     * @param force whether the deny entries of the preset's members are cleared
     * @return the change
     */
    public static ActivationChange apply(ActivationDocument before, PresetView preset, boolean force) {
        ActivationDocument base = force ? withoutMemberDenies(before, preset) : before;
        ActivationDocument.Builder after = ActivationDocument.builder();
        base.packs().ifPresent(after::packs);
        base.languages().ifPresent(after::languages);
        base.deactivated().forEach(after::deactivated);
        activatedKinds(base, preset).ifPresent(after::activatedKinds);
        missionTypes(base, preset).ifPresent(after::missionTypes);
        for (ArtifactKind kind : ActivationDocument.kinds()) {
            selected(base, preset, kind).ifPresent(ids -> after.selected(kind, ids));
        }
        return ActivationChange.of(before, after.build().document(), SkippedReport.empty());
    }

    /** The members of one kind the preset lists; empty when it lists none, which is not listing the kind (A7). */
    private static Set<String> members(PresetView preset, ArtifactKind kind) {
        return preset.members().getOrDefault(kind, Set.of());
    }

    private static ActivationDocument withoutMemberDenies(ActivationDocument document, PresetView preset) {
        ActivationDocument out = document;
        for (ArtifactKind kind : ActivationDocument.kinds()) {
            for (String id : members(preset, kind)) {
                out = out.withoutDeny(kind, id);
            }
        }
        return out;
    }

    private static Optional<List<ArtifactKind>> activatedKinds(ActivationDocument document, PresetView preset) {
        if (preset.activatedKinds().isEmpty()) {
            return document.activatedKinds().map(List::copyOf);
        }
        return Optional.of(preset.activatedKinds().stream()
                .filter(ActivationDocument.kinds()::contains)
                .toList());
    }

    private static Optional<? extends Collection<String>> missionTypes(ActivationDocument document, PresetView preset) {
        return preset.missionTypes().isEmpty() ? document.missionTypes() : Optional.of(preset.missionTypes());
    }

    private static Optional<? extends Collection<String>> selected(
            ActivationDocument document, PresetView preset, ArtifactKind kind) {
        Set<String> members = members(preset, kind);
        if (members.isEmpty()) {
            return document.selected(kind);
        }
        Set<String> denied = document.deactivated(kind);
        return Optional.of(members.stream().filter(id -> !denied.contains(id)).toList());
    }
}
