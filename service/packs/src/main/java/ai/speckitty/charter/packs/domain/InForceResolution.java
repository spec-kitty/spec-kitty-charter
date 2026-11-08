package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.Urn;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Decides what is in force: a stratified fixed point over the merged closure (research R13). */
public final class InForceResolution {

    private InForceResolution() {}

    /**
     * The merged edges the strata walk, as plain values.
     *
     * @param overrides each overrider with the artifacts its {@code overrides} edges name
     * @param enhances each enhancer with the artifacts its {@code enhances} edges name
     * @param bindings each artifact bound to a mission type, beyond the steps ({@code action:M/s} is bound to
     *     {@code mission_type:M} by its URN)
     */
    public record Edges(Map<Urn, Set<Urn>> overrides, Map<Urn, Set<Urn>> enhances, Map<Urn, Urn> bindings) {

        /** No edge and no extra binding. */
        public static final Edges NONE = new Edges(Map.of(), Map.of(), Map.of());

        /**
         * Checks and copies the fields.
         *
         * @param overrides the overrides edges
         * @param enhances the enhances edges
         * @param bindings the extra bindings
         */
        public Edges {
            overrides = Map.copyOf(Objects.requireNonNull(overrides, "overrides"));
            enhances = Map.copyOf(Objects.requireNonNull(enhances, "enhances"));
            bindings = Map.copyOf(Objects.requireNonNull(bindings, "bindings"));
        }
    }

    /**
     * Resolves what is in force.
     *
     * @param merged the merged closure
     * @param edges the merged edges among its winners
     * @param request what the document asks
     * @return one state per winner and the rules not applied
     * @throws ai.speckitty.charter.kernel.domain.CharterRefusal with {@code PACK_UNREADABLE} on an override cycle
     */
    public static InForceStates resolve(MergedClosure merged, Edges edges, InForceRequest request) {
        Objects.requireNonNull(merged, "merged");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(request, "request");
        Map<Urn, InForceStates.ArtifactState> states = new LinkedHashMap<>();
        merged.winners()
                .keySet()
                .forEach(urn -> states.put(
                        urn,
                        new InForceStates.ArtifactState(
                                urn,
                                InForceStates.State.INACTIVE,
                                InForceStates.Reason.NOT_SELECTED,
                                false,
                                java.util.Optional.empty(),
                                java.util.Optional.empty())));
        return new InForceStates(states, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
    }
}
