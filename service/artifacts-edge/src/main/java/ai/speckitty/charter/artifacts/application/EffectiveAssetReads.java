package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Outcome;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The asset reads under an activation (#105; FR-009 to FR-011, research R2, R3 and R15): the assets the activation's
 * closure holds, one asset and its payload, each the copy the layer merge keeps. Each composes two reads, as the
 * missions edge composes a mission type's: the activation names the winner, its pack and level and the source
 * fingerprint ({@link ActivationArtifacts}), and the winner's pack is read for the descriptor or the bytes against that
 * fingerprint ({@link AssetReads}), so the answer never mixes two states of the packs. An asset carries no state.
 *
 * <p>Refusals come in order: the activations port judges the activation ({@code activation_not_found}), then whether a
 * pack of the closure holds the asset ({@code artifact_not_found}), both before {@code If-None-Match}; a match is a
 * {@code 304} and the winner's pack is not read. A second read at another fingerprint, or of a winner its pack no longer
 * holds, is {@code stale_input}.
 */
@Service
public final class EffectiveAssetReads {

    private final ActivationArtifacts activations;
    private final AssetReads packs;

    /**
     * The reads over the activations port and the packs port.
     *
     * @param activations the port that names the winners under an activation
     * @param packs the port that reads what a pack stores of an asset
     */
    @Autowired
    public EffectiveAssetReads(ActivationArtifacts activations, AssetReads packs) {
        this.activations = Objects.requireNonNull(activations, "activations");
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    /**
     * The assets the activation's closure holds, each the winning copy, in id order.
     *
     * @param activationId the activation's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the assets, or that the client holds the current representation
     * @throws CharterRefusal as the activations port refuses, or with {@code stale_input}
     */
    public Outcome<Listed> list(String activationId, @Nullable String ifNoneMatch) {
        return switch (activations.winners(activationId, ArtifactKind.ASSET, ifNoneMatch)) {
            case Outcome.NotModified<ActivationArtifacts.Winners> notModified ->
                new Outcome.NotModified<>(notModified.etag());
            case Outcome.Found<ActivationArtifacts.Winners> found ->
                new Outcome.Found<>(listed(found.value()), found.etag());
        };
    }

    /**
     * One asset under the activation: the winning copy's descriptor, with its pack, level and policy.
     *
     * @param activationId the activation's id
     * @param assetId the asset's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the asset, or that the client holds the current representation
     * @throws CharterRefusal as the activations port refuses, or with {@code stale_input}
     */
    public Outcome<Read> asset(String activationId, String assetId, @Nullable String ifNoneMatch) {
        return switch (activations.asset(activationId, assetId, ifNoneMatch)) {
            case Outcome.NotModified<ActivationArtifacts.Winner> notModified ->
                new Outcome.NotModified<>(notModified.etag());
            case Outcome.Found<ActivationArtifacts.Winner> found -> {
                ActivationArtifacts.Winner winner = found.value();
                AssetReads.Read read = WinnerReads.of(() -> packs.asset(
                        winner.winner().packId(), assetId, winner.scope().sourceFingerprint()));
                yield new Outcome.Found<>(new Read(winner.scope(), winner.winner(), read.asset()), found.etag());
            }
        };
    }

    /**
     * The payload of one asset under the activation: the winning copy's bytes, read against the fingerprint of the
     * read that named the winner.
     *
     * @param activationId the activation's id
     * @param assetId the asset's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the bytes, or that the client holds the current representation
     * @throws CharterRefusal as the activations port refuses, or with {@code stale_input} when the packs changed since
     *     the activation named the winner
     */
    public Outcome<AssetReads.PayloadRead> payload(String activationId, String assetId, @Nullable String ifNoneMatch) {
        return switch (activations.assetPayload(activationId, assetId, ifNoneMatch)) {
            case Outcome.NotModified<ActivationArtifacts.Winner> notModified ->
                new Outcome.NotModified<>(notModified.etag());
            case Outcome.Found<ActivationArtifacts.Winner> found -> {
                ActivationArtifacts.Winner winner = found.value();
                yield new Outcome.Found<>(
                        WinnerReads.of(() -> packs.payload(
                                winner.winner().packId(),
                                assetId,
                                winner.scope().sourceFingerprint())),
                        found.etag());
            }
        };
    }

    /** Each winner with the descriptor its pack holds, every pack read once against the activation's fingerprint. */
    private Listed listed(ActivationArtifacts.Winners winners) {
        String fingerprint = winners.scope().sourceFingerprint();
        Map<PackIdentifier, Map<String, AssetView>> byPack = new HashMap<>();
        List<Item> items = winners.winners().stream()
                .map(winner -> {
                    Map<String, AssetView> held = byPack.computeIfAbsent(
                            winner.packId(),
                            pack -> WinnerReads.of(() -> packs.list(pack, fingerprint)).assets().stream()
                                    .collect(Collectors.toMap(AssetView::id, Function.identity())));
                    AssetView asset = held.get(winner.id());
                    if (asset == null) {
                        throw WinnerReads.stale();
                    }
                    return new Item(winner, asset);
                })
                .toList();
        return new Listed(winners.scope(), items);
    }

    /**
     * One asset of the list: the winner and the descriptor of the winning copy.
     *
     * @param winner the winner
     * @param asset the descriptor
     */
    public record Item(Held winner, AssetView asset) {

        /**
         * Checks the fields.
         *
         * @param winner the winner
         * @param asset the descriptor
         */
        public Item {
            Objects.requireNonNull(winner, "winner");
            Objects.requireNonNull(asset, "asset");
        }
    }

    /**
     * What the list answers.
     *
     * @param scope the activation and the read
     * @param items the assets, by id
     */
    public record Listed(Scope scope, List<Item> items) {

        /**
         * Checks and copies the fields.
         *
         * @param scope the scope
         * @param items the items
         */
        public Listed {
            Objects.requireNonNull(scope, "scope");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }

    /**
     * What the item answers.
     *
     * @param scope the activation and the read
     * @param winner the winner
     * @param asset the winning copy's descriptor
     */
    public record Read(Scope scope, Held winner, AssetView asset) {

        /**
         * Checks the fields.
         *
         * @param scope the scope
         * @param winner the winner
         * @param asset the descriptor
         */
        public Read {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(winner, "winner");
            Objects.requireNonNull(asset, "asset");
        }
    }
}
