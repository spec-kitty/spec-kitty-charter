package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.Objects;
import java.util.Optional;

/**
 * What the client allows a guard while it runs (ADR-0054 decision 1), each kept as authored.
 *
 * @param timeoutSeconds the longest the guard may run, when stated
 * @param network whether the guard may reach the network, when stated; absent means {@code false}
 */
public record GuardLimits(Optional<Integer> timeoutSeconds, Optional<Boolean> network) {

    private static final ModularRuleset<GuardLimits> RULES = ModularRuleset.<GuardLimits>emptyRules()
            .must(Constraints.of(
                    "A guard's timeout_seconds is at least 1 and at most 86400.",
                    limits -> limits.timeoutSeconds
                            .map(t -> t >= 1 && t <= 86_400)
                            .orElse(true)));

    /**
     * Checks the limits.
     *
     * @param timeoutSeconds the timeout, possibly empty
     * @param network the network flag, possibly empty
     */
    public GuardLimits(Optional<Integer> timeoutSeconds, Optional<Boolean> network) {
        this.timeoutSeconds = Objects.requireNonNull(timeoutSeconds, "timeoutSeconds");
        this.network = Objects.requireNonNull(network, "network");
        RULES.guard(this);
    }

    /**
     * Whether the guard may reach the network.
     *
     * @return the stated flag, {@code false} when absent
     */
    public boolean allowsNetwork() {
        return network.orElse(false);
    }
}
