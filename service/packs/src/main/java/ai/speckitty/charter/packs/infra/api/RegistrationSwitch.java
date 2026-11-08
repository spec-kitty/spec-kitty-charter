package ai.speckitty.charter.packs.infra.api;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Whether registration is switched on right now. It starts at {@code charter.registration.enabled} and is read at every
 * request, so a deployment that flips the flag at run time, and the contract scenarios that switch it on and off, need
 * no restart.
 */
public final class RegistrationSwitch {

    private final AtomicBoolean enabled;

    /**
     * A switch at its configured position.
     *
     * @param enabled whether registration starts switched on
     */
    public RegistrationSwitch(boolean enabled) {
        this.enabled = new AtomicBoolean(enabled);
    }

    /**
     * Whether registration is on.
     *
     * @return true when a registration is taken
     */
    public boolean enabled() {
        return enabled.get();
    }

    /**
     * Moves the switch.
     *
     * @param on whether registration is taken from now on
     */
    public void set(boolean on) {
        enabled.set(on);
    }
}
