package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.ClosureVerdict;
import ai.speckitty.charter.packs.domain.ClosureWalk;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * The {@link ClosureWalk} of this service (#556, ADR-0075 decision 6): over one declaring pack's own closure, the
 * ADR-0068 fallback profile the descriptor names ({@link FallbackProfileRule}), then its role pins and the pins of its
 * closure ({@link RolePinRule}), then the profile enhancements in force ({@link ProfileEnhancementRule}), each reporting
 * every breach. A breach names the packs it involves; the caller places it in the closure it judges.
 */
@Component
public final class OwnClosureWalk implements ClosureWalk {

    /** For Spring, which finds the component by scan; the walk holds no state. */
    public OwnClosureWalk() {}

    @Override
    public ClosureVerdict walk(OwnClosure closure) {
        Objects.requireNonNull(closure, "closure");
        List<ClosureFault> breaches = new ArrayList<>();
        closure.descriptor()
                .fallbackProfile()
                .ifPresent(named -> breaches.addAll(
                        FallbackProfileRule.check(closure.declaring(), named, closure.closure(), closure.profiles())));
        breaches.addAll(RolePinRule.check(closure.declaring(), closure.closure(), closure.profiles()));
        breaches.addAll(ProfileEnhancementRule.check(closure.closure(), closure.profiles(), closure.merge()));
        return new ClosureVerdict(breaches);
    }
}
