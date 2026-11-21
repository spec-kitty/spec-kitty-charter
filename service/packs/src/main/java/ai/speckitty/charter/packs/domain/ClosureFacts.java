package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What the closure walk reads of one composed closure (#556, ADR-0075 decision 6, squad A1): the facts its rules need,
 * in domain terms only, so the {@link ClosureWalk} port names no type of the application ring. The closure's view, as
 * the reads compose it, answers them.
 */
public interface ClosureFacts {

    /**
     * Whether a pack of the closure holds an artifact.
     *
     * @param kind the artifact's kind
     * @param id the artifact's id
     * @return {@code true} when the closure holds it
     */
    boolean holds(ArtifactKind kind, String id);

    /**
     * The pack that holds the version of an artifact in force along the closure.
     *
     * @param kind the artifact's kind
     * @param id the artifact's id
     * @return the winner's pack; empty when no pack of the closure holds the artifact
     */
    Optional<PackIdentifier> winnerPackOf(ArtifactKind kind, String id);

    /**
     * Whether a pack of the closure locks the target.
     *
     * @param urn the target
     * @return {@code true} when its level along the closure is locked
     */
    boolean isLocked(Urn urn);

    /**
     * The role pins a pack of the closure declares.
     *
     * @param pack the pack, by its registered id
     * @return its pins; none when it declares none
     */
    RolePins pinsOf(PackIdentifier pack);

    /**
     * Every role a pack of the closure pins, each once, in a stable order.
     *
     * @return the roles
     */
    List<String> pinnedRoles();

    /**
     * The packs that pin a role to different profiles, when they conflict (tighten-only pins).
     *
     * @param role the role
     * @return the conflicting packs; empty when the role's pins agree or it is not pinned
     */
    List<PackIdentifier> pinConflict(String role);

    /**
     * The profile enhancements in force along the closure, from its layer merge.
     *
     * @return the winners and each enhanced profile's enhancers in closure order; empty when no profile enhances a
     *     profile, or when the closure's layers do not merge (that write is refused by the rules and stores that own the
     *     merge, with their own code)
     */
    Optional<ProfileEnhancements> profileEnhancements();

    /**
     * Packs of the closure, each as a refusal names it.
     *
     * @param packs the packs, by registered id
     * @return each one the closure holds, in the order given
     */
    List<RefusalPack> named(List<PackIdentifier> packs);

    /**
     * The profile enhancements of a closure.
     *
     * @param winners the version in force of each artifact of the closure, by URN
     * @param enhancersOf each enhanced profile's enhancers, in closure order
     */
    record ProfileEnhancements(
            Map<Urn, MergedClosure.Winner> winners, Map<Urn, List<MergedClosure.Winner>> enhancersOf) {

        /**
         * Checks and copies the maps, the enhancers in their order.
         *
         * @param winners the winners
         * @param enhancersOf the enhancers
         */
        public ProfileEnhancements {
            winners = Map.copyOf(Objects.requireNonNull(winners, "winners"));
            enhancersOf = Collections.unmodifiableMap(
                    new LinkedHashMap<>(Objects.requireNonNull(enhancersOf, "enhancersOf")));
        }
    }
}
