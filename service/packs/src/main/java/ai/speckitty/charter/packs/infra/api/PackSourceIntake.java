package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.PackSourceStagers;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** What the inbound adapter needs to take a registration from a source (skeleton: signatures only, built next). */
public final class PackSourceIntake {

    private final @Nullable PackSourceStagers stagers;

    /**
     * An intake over the switch and the stagers.
     *
     * @param switches whether each kind is on
     * @param stagers the stagers of the kinds that are built
     * @param worktree the worktree identity the audit record names
     */
    public PackSourceIntake(PackSourceSwitch switches, PackSourceStagers stagers, Supplier<String> worktree) {
        this.stagers = stagers;
    }

    private PackSourceIntake() {
        this.stagers = null;
    }

    /**
     * An intake that has no source: every kind is unavailable.
     *
     * @return the intake
     */
    public static PackSourceIntake none() {
        return new PackSourceIntake();
    }

    RegistrationJobs.Stager stagerFor(PackSourceRequest request, String target) {
        if (stagers == null) {
            return directory -> {
                throw new UnsupportedOperationException("not built yet");
            };
        }
        return stagers.stagerFor(request, target);
    }

    String worktree() {
        return "";
    }
}
