package ai.speckitty.charter.contracts.closure;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects violations. A view made by {@link #ignoringClosure()} keeps malformed-schema findings (unknown keywords,
 * {@code $ref}) but drops open positions: inside {@code not} and {@code if} nothing describes an admitted value.
 */
final class Findings {

    private final List<Violation> violations;
    private final boolean judgesClosure;

    private Findings(List<Violation> violations, boolean judgesClosure) {
        this.violations = violations;
        this.judgesClosure = judgesClosure;
    }

    static Findings collecting() {
        return new Findings(new ArrayList<>(), true);
    }

    Findings ignoringClosure() {
        return new Findings(violations, false);
    }

    void malformed(JsonPointer at, String reason) {
        violations.add(new Violation(at, reason));
    }

    void open(JsonPointer position, Closure open) {
        if (judgesClosure) {
            violations.add(new Violation(position, open.reason()));
        }
    }

    List<Violation> violations() {
        return List.copyOf(violations);
    }
}
