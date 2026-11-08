package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.packs.application.PackSourceKind;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether each source kind is switched on right now (IC-18: {@code charter.pack-sources.<kind>.enabled}). It starts at
 * the configured positions and is read at every request, so a deployment that flips a flag at run time, and the
 * contract scenarios that switch a kind on and off, need no restart.
 */
public final class PackSourceSwitch {

    private final Set<PackSourceKind> on = ConcurrentHashMap.newKeySet();

    /**
     * A switch with some kinds on and the others off.
     *
     * @param initiallyOn the kinds that start switched on
     */
    public PackSourceSwitch(Set<PackSourceKind> initiallyOn) {
        on.addAll(initiallyOn);
    }

    /**
     * Whether a kind is on.
     *
     * @param kind the kind
     * @return true when a registration from this kind of source is taken
     */
    public boolean enabled(PackSourceKind kind) {
        return on.contains(kind);
    }

    /**
     * Moves the switch of a kind.
     *
     * @param kind the kind
     * @param enabled whether the kind is taken from now on
     */
    public void set(PackSourceKind kind, boolean enabled) {
        if (enabled) {
            on.add(kind);
        } else {
            on.remove(kind);
        }
    }
}
