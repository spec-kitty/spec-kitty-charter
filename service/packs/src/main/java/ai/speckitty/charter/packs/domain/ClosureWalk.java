package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.Objects;
import java.util.Optional;

/**
 * Port: the closure walk of ADR-0075 decision 6 (#556), the checks one declaring pack's own closure must pass once a
 * write is in place: its ADR-0068 fallback profile, its role pins, then the profile enhancements in force, each reporting
 * every breach. The qualified references it also lists are judged by the compose that builds the closure (FR-006), and
 * no pack write is refused on the required floor (spec A1). The service composes it through the impact check; the
 * validation edge and {@code pack-check} compose none of it.
 */
@FunctionalInterface
public interface ClosureWalk {

    /**
     * Walks one declaring pack's own closure.
     *
     * @param closure the closure and what the walk reads of it
     * @return every breach, the fallback's first, then the pins', then the enhancements'; passing when there is none
     */
    ClosureVerdict walk(OwnClosure closure);

    /** Reads the agent profile of an id from the pack that holds the version in force. */
    @FunctionalInterface
    interface Profiles {
        /**
         * The profile a pack holds.
         *
         * @param holder the pack that holds the winning version
         * @param id the profile's id
         * @return the entity, when the pack holds and can read it
         */
        Optional<CharterArtifact> read(PackIdentifier holder, String id);
    }

    /**
     * One declaring pack's own closure, as the walk judges it.
     *
     * @param declaring the declaring pack, by registered id
     * @param descriptor its descriptor, with the write in place
     * @param closure what the walk reads of its closure
     * @param profiles where a winning profile's entity is read, the written pack's from the write
     * @param merge the profile merge that judges an enhancement; empty when the deployment has none
     */
    record OwnClosure(
            PackIdentifier declaring,
            PackDescriptor descriptor,
            ClosureFacts closure,
            Profiles profiles,
            Optional<ProfileEnhancementGateway> merge) {

        /**
         * Checks the fields.
         *
         * @param declaring the declaring pack
         * @param descriptor its descriptor
         * @param closure its closure's facts
         * @param profiles the profile reader
         * @param merge the profile merge, if any
         */
        public OwnClosure {
            Objects.requireNonNull(declaring, "declaring");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(closure, "closure");
            Objects.requireNonNull(profiles, "profiles");
            Objects.requireNonNull(merge, "merge");
        }
    }
}
