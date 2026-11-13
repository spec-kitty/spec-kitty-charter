package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.PresetsApi;
import ai.speckitty.charter.api.model.Preset;
import ai.speckitty.charter.api.model.PresetList;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.packs.application.PresetReadService;
import ai.speckitty.charter.web.DeferredETag;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /packs/{packId}/presets} and {@code GET /packs/{packId}/presets/{presetId}} (#98, ADR-0074 decision 1): the
 * pack-scoped reads of a pack's activation presets, over {@link PresetReadService}. Both answer the whole-source
 * fingerprint their verified read checked and, as {@code ETag}, the pack revision they were read at, as {@code getPack}
 * answers it; neither takes {@code If-None-Match}, so the tag is handed to the web transport's {@link DeferredETag} and
 * Spring's own conditional check never answers a {@code 304} the contract does not declare. A refusal the service throws is answered by the shared refusal
 * handler: {@code pack_not_found}, {@code preset_not_found}, {@code pack_unreadable} and {@code stale_input}.
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class PresetsController implements PresetsApi {

    private final PresetReadService presets;
    private final PresetMapper mapper;

    /**
     * A controller over the preset reads.
     *
     * @param presets the preset reads
     * @param mapper the contract shapes
     */
    @Autowired
    PresetsController(PresetReadService presets, PresetMapper mapper) {
        this.presets = Objects.requireNonNull(presets, "presets");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public ResponseEntity<PresetList> listPackPresets(String packId) {
        var verified = presets.list(new PackIdentifier(packId));
        DeferredETag.offer(EntityTags.quote(verified.value().revision().token()));
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .body(mapper.list(packId, verified.value().presets()));
    }

    @Override
    public ResponseEntity<Preset> getPackPreset(String packId, String presetId) {
        var verified = presets.read(new PackIdentifier(packId), presetId);
        DeferredETag.offer(EntityTags.quote(verified.value().revision().token()));
        return ResponseEntity.ok()
                .headers(CharterHeaders.fingerprint(verified.fingerprint()))
                .body(mapper.preset(packId, verified.value().preset()));
    }
}
