package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.Asset;
import ai.speckitty.charter.api.model.AssetExecutable;
import ai.speckitty.charter.api.model.AssetList;
import ai.speckitty.charter.api.model.AssetSummary;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.artifacts.application.AssetView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * The wire form of an asset (#105): the one mapper of the asset's descriptor to the {@code Asset} member, its list
 * summary and the links of the pack-scoped reads. The activation-scoped asset read renders its member with
 * {@link #asset(AssetView)} too, so the descriptor is mapped once (research R13).
 *
 * <p>The asset's own read answers the {@code Artifact} envelope with {@code self}, {@code payload} (its bytes) and
 * {@code collection} (the pack's asset list); a list item links {@code self}, and the list links {@code self}. A title or
 * an executable block the sidecar does not declare is {@code null} here and left out on the wire by the
 * {@code Artifact} mix-in, and so is a {@code minVersion} the block does not state.
 */
@Component
public class AssetBodies {

    private static final String PACKS = "packs";
    private static final String ARTIFACTS = "artifacts";
    private static final String PAYLOAD = "payload";
    private static final String ASSET = ArtifactKind.ASSET.token();

    private final String basePath;

    /**
     * A mapper whose links start at the base path the controllers are mapped under.
     *
     * @param basePath the server prefix, for example {@code /api/v1}; a trailing {@code /} is ignored
     */
    @Autowired
    public AssetBodies(@Value("${charter.api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /**
     * The list of a pack's assets.
     *
     * @param pack the pack's identity, as the request named it
     * @param assets the assets, in the order they are answered
     * @return the {@code AssetList}, each item linking its own read, the list linking itself
     */
    public AssetList list(String pack, List<AssetView> assets) {
        List<AssetSummary> items = assets.stream()
                .map(asset -> new AssetSummary(
                        asset.id(),
                        asset.mime(),
                        asset.executable().isPresent(),
                        links("self", item(asset.packId().value(), asset.id()))))
                .toList();
        return new AssetList(items, links("self", collection(pack)));
    }

    /**
     * The envelope of one asset's pack-scoped read.
     *
     * @param asset the asset
     * @return the {@code Artifact} with kind {@code asset}, the descriptor in member {@code asset}, and the links
     *     {@code self}, {@code payload} and {@code collection}
     */
    public Artifact artifact(AssetView asset) {
        String pack = asset.packId().value();
        String self = item(pack, asset.id());
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", new Link(self));
        links.put(PAYLOAD, new Link(self + "/" + PAYLOAD));
        links.put("collection", new Link(collection(pack)));
        Artifact envelope = new Artifact(ASSET, asset.id(), pack, links);
        envelope.setAsset(asset(asset));
        return envelope;
    }

    /**
     * The {@code Asset} member: the descriptor as the sidecar declares it.
     *
     * @param asset the asset
     * @return the member, with a title and an executable block only when the sidecar declares them
     */
    public Asset asset(AssetView asset) {
        Asset wire = new Asset(asset.id(), asset.mime(), asset.path());
        wire.setTitle(asset.title().orElse(null));
        wire.setExecutable(asset.executable().map(AssetBodies::executable).orElse(null));
        return wire;
    }

    private static AssetExecutable executable(AssetView.Executable executable) {
        AssetExecutable wire = new AssetExecutable(
                AssetExecutable.RuntimeEnum.fromValue(executable.runtime()), executable.entry(), executable.args());
        wire.setMinVersion(executable.minVersion().orElse(null));
        return wire;
    }

    private String collection(String pack) {
        return href(PACKS, pack, ARTIFACTS, ASSET);
    }

    private String item(String pack, String id) {
        return href(PACKS, pack, ARTIFACTS, ASSET, id);
    }

    private static Map<String, Link> links(String relation, String href) {
        Map<String, Link> links = new LinkedHashMap<>();
        links.put(relation, new Link(href));
        return links;
    }

    private String href(String... segments) {
        return basePath
                + Arrays.stream(segments)
                        .map(segment -> "/" + UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                        .collect(Collectors.joining());
    }
}
