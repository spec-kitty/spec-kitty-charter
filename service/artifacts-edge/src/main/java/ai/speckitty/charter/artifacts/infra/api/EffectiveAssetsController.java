package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.EffectiveAssetsApi;
import ai.speckitty.charter.api.model.AssetList;
import ai.speckitty.charter.api.model.EffectiveAssetRead;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Outcome;
import ai.speckitty.charter.artifacts.application.AssetReads;
import ai.speckitty.charter.artifacts.application.EffectiveAssetReads;
import ai.speckitty.charter.kernel.transport.EntityTags;
import ai.speckitty.charter.kernel.transport.HeaderNames;
import ai.speckitty.charter.web.DeferredETag;
import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The asset reads under an activation (#105, tag {@code EffectiveAssets}): {@code GET /activations/{activationId}/artifacts/asset},
 * its item {@code …/asset/{artifactId}} and the item's {@code …/payload}, each the copy the layer merge keeps. The literal
 * paths are preferred to the activation set's templated read of an artifact, which never answers an asset. A refusal
 * the reads throw is answered by the shared refusal handler.
 *
 * <p>Every read judges {@code If-None-Match} in the application ring, against the tag of its own representation, and a
 * match is a {@code 304} that sends the tag alone. A {@code 200} carries the tag, handed to the web transport's
 * {@link DeferredETag} so that Spring's own conditional check never runs, and the source fingerprint of the read. The
 * payload is answered as the pack's payload is ({@link PayloadResponses}).
 */
@RestController
@RequestMapping("${charter.api.base-path:/api/v1}")
public class EffectiveAssetsController implements EffectiveAssetsApi {

    private final EffectiveAssetReads assets;
    private final EffectiveBodies bodies;

    /**
     * A controller over the asset reads under an activation.
     *
     * @param assets the reads
     * @param bodies the wire mapper of the answers and their links
     */
    @Autowired
    public EffectiveAssetsController(EffectiveAssetReads assets, EffectiveBodies bodies) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.bodies = Objects.requireNonNull(bodies, "bodies");
    }

    @Override
    public ResponseEntity<AssetList> listEffectiveAssets(String activationId, @Nullable String ifNoneMatch) {
        return answer(
                assets.list(activationId, ifNoneMatch), listed -> listed.scope().sourceFingerprint(), bodies::assets);
    }

    @Override
    public ResponseEntity<EffectiveAssetRead> getEffectiveAsset(
            String activationId, String artifactId, @Nullable String ifNoneMatch) {
        return answer(
                assets.asset(activationId, artifactId, ifNoneMatch),
                read -> read.scope().sourceFingerprint(),
                bodies::asset);
    }

    @Override
    public ResponseEntity<Resource> getEffectiveAssetPayload(
            String activationId, String artifactId, @Nullable String ifNoneMatch) {
        return switch (assets.payload(activationId, artifactId, ifNoneMatch)) {
            case Outcome.NotModified<AssetReads.PayloadRead> notModified -> notModified(notModified.etag());
            case Outcome.Found<AssetReads.PayloadRead> found -> {
                AssetReads.PayloadRead read = found.value();
                // If-None-Match was judged against this tag already: the payload answer is not asked to judge it again.
                yield PayloadResponses.answer(
                        read.length(),
                        read.stream(),
                        read.asset().mime(),
                        read.asset().wellFormedMime(),
                        found.etag(),
                        read.sourceFingerprint(),
                        null);
            }
        };
    }

    /** A read's answer: a {@code 304} with the tag alone, or a {@code 200} with the tag, the fingerprint and the body. */
    static <T, B> ResponseEntity<B> answer(Outcome<T> outcome, Function<T, String> fingerprint, Function<T, B> body) {
        return switch (outcome) {
            case Outcome.NotModified<T> notModified -> notModified(notModified.etag());
            case Outcome.Found<T> found -> {
                DeferredETag.offer(EntityTags.quote(found.etag()));
                yield ResponseEntity.ok()
                        .header(HeaderNames.SOURCE_FINGERPRINT, fingerprint.apply(found.value()))
                        .body(body.apply(found.value()));
            }
        };
    }

    private static <B> ResponseEntity<B> notModified(String etag) {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .eTag(EntityTags.quote(etag))
                .build();
    }
}
