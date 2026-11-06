package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.ClosureSnapshot.PresetView;
import ai.speckitty.charter.activations.domain.PresetRules;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Applies a preset to an activation (FR-016, ADR-0059 D6, ADR-0020 D6): the preconditions, the one read of the packs,
 * the rules check, the no-op and the compare-and-set are {@link ActivationWrites}'; what the preset changes is
 * {@link PresetRules}'. The preset is resolved in the activation's own closure ({@link ClosureSnapshot#presets()}, where
 * the last of two packs holding one preset id in merge order wins), so a preset only a pack outside it holds is
 * {@code 404 preset_not_found} like one no pack holds (FR-010).
 */
@Service
public class ActivationPresetService {

    private final ActivationWrites writes;

    /**
     * The preset write over the shared write flow.
     *
     * @param writes the shared write flow
     */
    @Autowired
    public ActivationPresetService(ActivationWrites writes) {
        this.writes = Objects.requireNonNull(writes, "writes");
    }

    /**
     * Applies one preset.
     *
     * @param id the activation's id
     * @param presetId the path's preset id
     * @param force whether the deny entries of the preset's members are cleared
     * @param ifMatch the {@code If-Match} header, verbatim; null when absent
     * @return the outcome
     * @throws CharterRefusal with {@code preset_not_found} when the activation's closure holds no such preset
     */
    public ActivationWrites.Outcome apply(ActivationId id, String presetId, boolean force, @Nullable String ifMatch) {
        StoredActivation loaded = writes.loaded(id, ifMatch);
        PacksSession session = writes.session(loaded);
        ClosureSnapshot snapshot = session.snapshot();
        PresetView preset = Optional.ofNullable(snapshot.presets().get(presetId))
                .orElseThrow(() -> new CharterRefusal(
                        RefusalCode.PRESET_NOT_FOUND, "The activation's packs hold no preset '" + presetId + "'."));
        return writes.commit(loaded, PresetRules.apply(loaded.document(), preset, force), snapshot);
    }
}
