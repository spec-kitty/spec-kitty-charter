package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.PackSourceStagers;
import ai.speckitty.charter.packs.application.RegistrationJobs;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * What the inbound adapter needs to take a registration from a source (FR-004, IC-18): whether the kind's own flag is on,
 * the stager the job runs, and the worktree identity the audit record names. A kind whose flag is off, or that no fetcher
 * serves (the git source until it is built), is {@code 503 pack_source_unavailable}, and the answer names the kind and
 * nothing the client sent.
 */
public final class PackSourceIntake {

    private final @Nullable PackSourceSwitch switches;
    private final @Nullable PackSourceStagers stagers;
    private final Supplier<String> worktree;

    /**
     * An intake over the switch and the stagers.
     *
     * @param switches whether each kind is on
     * @param stagers the stagers of the kinds that are built
     * @param worktree the worktree identity the audit record names
     */
    public PackSourceIntake(PackSourceSwitch switches, PackSourceStagers stagers, Supplier<String> worktree) {
        this.switches = Objects.requireNonNull(switches, "switches");
        this.stagers = Objects.requireNonNull(stagers, "stagers");
        this.worktree = Objects.requireNonNull(worktree, "worktree");
    }

    private PackSourceIntake() {
        this.switches = null;
        this.stagers = null;
        this.worktree = () -> "";
    }

    /**
     * An intake that has no source: every kind is unavailable.
     *
     * @return the intake
     */
    public static PackSourceIntake none() {
        return new PackSourceIntake();
    }

    /**
     * The stager of a request, after the checks that come before a slot is taken.
     *
     * @param request the source as the request named it
     * @param target the target pack id
     * @return the stager the job runs
     * @throws CharterRefusal {@code pack_source_unavailable} when the kind is switched off or not built
     */
    RegistrationJobs.Stager stagerFor(PackSourceRequest request, String target) {
        PackSourceKind kind = request.kind();
        if (switches == null || !switches.enabled(kind)) {
            throw new CharterRefusal(
                    RefusalCode.PACK_SOURCE_UNAVAILABLE,
                    "The " + kind.wire() + " source is switched off: charter.pack-sources." + property(kind)
                            + ".enabled is not true.");
        }
        if (stagers == null || stagers.fetcherFor(kind).isEmpty()) {
            throw new CharterRefusal(
                    RefusalCode.PACK_SOURCE_UNAVAILABLE,
                    "The " + kind.wire() + " source is not available in this service.");
        }
        return stagers.stagerFor(request, target);
    }

    private static String property(PackSourceKind kind) {
        return kind == PackSourceKind.HTTPS_ARCHIVE ? "https" : kind.wire();
    }

    /**
     * The worktree identity.
     *
     * @return what the guard knows of the worktree
     */
    String worktree() {
        return worktree.get();
    }
}
