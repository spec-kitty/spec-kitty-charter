package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Facts;
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
 * The routing catalog reads under an activation (ADR-0080 decision 6), {@code listEffectiveRoutingCatalogs} and
 * {@code getEffectiveRoutingCatalog}, as {@link EffectiveSkillReads} reads the skills: the winners and their facts are
 * the activations port's, and what each winning pack stores is the packs port's, read against the source fingerprint of
 * the activation's read.
 */
@Service
public final class EffectiveRoutingCatalogReads {

    private final ActivationArtifacts activations;
    private final ActivationRoutingCatalogs catalogs;
    private final RoutingCatalogReads packs;

    /**
     * The reads over the two ports.
     *
     * @param activations the winners of a kind under an activation
     * @param catalogs the winner of one catalog under an activation
     * @param packs what a pack holds of the kind
     */
    @Autowired
    public EffectiveRoutingCatalogReads(
            ActivationArtifacts activations, ActivationRoutingCatalogs catalogs, RoutingCatalogReads packs) {
        this.activations = Objects.requireNonNull(activations, "activations");
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    /**
     * The catalog winners of an activation's closure, in id order, each with its view.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the winners, or that the client holds the current representation
     * @throws CharterRefusal as {@link ActivationArtifacts#winners} does, or {@code STALE_INPUT} when a winner's pack no
     *     longer holds it at the activation's fingerprint
     */
    public Outcome<Listed> list(String activationId, @Nullable String ifNoneMatch) {
        return switch (activations.winners(activationId, ArtifactKind.ROUTING_CATALOG, ifNoneMatch)) {
            case Outcome.NotModified<ActivationArtifacts.Winners> notModified ->
                new Outcome.NotModified<>(notModified.etag());
            case Outcome.Found<ActivationArtifacts.Winners> found ->
                new Outcome.Found<>(listed(found.value()), found.etag());
        };
    }

    /**
     * One catalog under an activation, whatever its state.
     *
     * @param activationId the activation's id, exactly as the request names it
     * @param catalogId the catalog's id
     * @param ifNoneMatch the {@code If-None-Match} header, verbatim; null when absent
     * @return the catalog with its facts, or that the client holds the current representation
     * @throws CharterRefusal as {@link ActivationRoutingCatalogs#routingCatalog} does
     */
    public Outcome<Read> catalog(String activationId, String catalogId, @Nullable String ifNoneMatch) {
        return switch (catalogs.routingCatalog(activationId, catalogId, ifNoneMatch)) {
            case Outcome.NotModified<ActivationRoutingCatalogs.CatalogWinner> notModified ->
                new Outcome.NotModified<>(notModified.etag());
            case Outcome.Found<ActivationRoutingCatalogs.CatalogWinner> found -> {
                ActivationRoutingCatalogs.CatalogWinner winner = found.value();
                RoutingCatalogView catalog = packs.view(
                        winner.catalog(),
                        "The read of the routing catalog " + catalogId + " under activation " + activationId);
                yield new Outcome.Found<>(new Read(winner.scope(), winner.facts(), catalog), found.etag());
            }
        };
    }

    private Listed listed(ActivationArtifacts.Winners winners) {
        String fingerprint = winners.scope().sourceFingerprint();
        Map<PackIdentifier, Map<String, RoutingCatalogView>> byPack = new HashMap<>();
        List<Item> items = winners.winners().stream()
                .map(winner -> {
                    Map<String, RoutingCatalogView> held = byPack.computeIfAbsent(
                            winner.packId(),
                            pack -> WinnerReads.of(() -> packs.list(pack, fingerprint)).catalogs().stream()
                                    .collect(Collectors.toMap(RoutingCatalogView::id, Function.identity())));
                    RoutingCatalogView catalog = held.get(winner.id());
                    if (catalog == null) {
                        throw WinnerReads.stale();
                    }
                    return new Item(winner, catalog);
                })
                .toList();
        return new Listed(winners.scope(), items);
    }

    /**
     * One winner with its catalog.
     *
     * @param winner the winner
     * @param catalog its catalog
     */
    public record Item(Held winner, RoutingCatalogView catalog) {

        /**
         * Checks the fields.
         *
         * @param winner the winner
         * @param catalog the catalog
         */
        public Item {
            Objects.requireNonNull(winner, "winner");
            Objects.requireNonNull(catalog, "catalog");
        }
    }

    /**
     * The catalog winners of an activation.
     *
     * @param scope the activation and the read
     * @param items the winners, in id order
     */
    public record Listed(Scope scope, List<Item> items) {

        /**
         * Checks the fields and copies the list.
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
     * One catalog under an activation.
     *
     * @param scope the activation and the read
     * @param facts the facts of the winner
     * @param catalog the catalog
     */
    public record Read(Scope scope, Facts facts, RoutingCatalogView catalog) {

        /**
         * Checks the fields.
         *
         * @param scope the scope
         * @param facts the facts
         * @param catalog the catalog
         */
        public Read {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(facts, "facts");
            Objects.requireNonNull(catalog, "catalog");
        }
    }
}
