package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.AssetsApi;
import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.AssetList;
import ai.speckitty.charter.artifacts.application.AssetReads;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.web.DeferredETag;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The asset reads of a pack (#105, tag {@code Assets}): {@code GET /packs/{packId}/artifacts/asset}, its item
 * {@code …/asset/{artifactId}} and the item's {@code …/payload}. A refusal the reads throw is answered by the shared
 * refusal handler.
 *
 * <p>Every answer carries the pack revision as its {@code ETag} and the fingerprint the one verified read agreed on.
 * The list and the item take no {@code If-None-Match}; their tag is handed to the web transport's {@link DeferredETag}
 * so that Spring's own conditional check never answers a {@code 304} the contract does not declare. The payload judges
 * {@code If-None-Match} itself ({@link PayloadResponses}).
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class AssetsController implements AssetsApi {

    private final AssetReads assets;
    private final AssetBodies bodies;

    /**
     * A controller over the asset reads.
     *
     * @param assets the reads
     * @param bodies the wire mapper of an asset and its links
     */
    @Autowired
    public AssetsController(AssetReads assets, AssetBodies bodies) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.bodies = Objects.requireNonNull(bodies, "bodies");
    }

    @Override
    public ResponseEntity<AssetList> listPackAssets(String packId) {
        AssetReads.Listed listed = assets.list(new PackIdentifier(packId));
        DeferredETag.offer(EntityTags.quote(listed.revision()));
        return ResponseEntity.ok()
                .header(HeaderNames.SOURCE_FINGERPRINT, listed.sourceFingerprint())
                .body(bodies.list(packId, listed.assets()));
    }

    @Override
    public ResponseEntity<Artifact> getPackAsset(String packId, String artifactId) {
        AssetReads.Read read = assets.asset(new PackIdentifier(packId), artifactId);
        DeferredETag.offer(EntityTags.quote(read.revision()));
        return ResponseEntity.ok()
                .header(HeaderNames.SOURCE_FINGERPRINT, read.sourceFingerprint())
                .body(bodies.artifact(read.asset()));
    }

    @Override
    public ResponseEntity<Resource> getPackAssetPayload(
            String packId, String artifactId, @Nullable String ifNoneMatch) {
        AssetReads.PayloadRead read = assets.payload(new PackIdentifier(packId), artifactId);
        return PayloadResponses.answer(
                read.bytes(),
                read.asset().mime(),
                read.asset().wellFormedMime(),
                read.revision(),
                read.sourceFingerprint(),
                ifNoneMatch);
    }
}
