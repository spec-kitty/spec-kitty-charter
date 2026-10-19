package ai.speckitty.charter.contracts.closure;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Alternatives of which a value satisfies at least one: {@code anyOf}, {@code oneOf}, or {@code then} and
 * {@code else} beside an {@code if}. Each facet is the widest of the alternatives' facets.
 */
final class Disjunction {

    private Disjunction() {
    }

    /** Combines alternatives into a classification at {@code at}; each is named relative to {@code at}. */
    static Closure of(JsonPointer at, List<Closure> alternatives) {
        return of(at, at, alternatives);
    }

    /**
     * Combines alternatives into one conjunctive part at {@code at} ({@code anyOf}, {@code then|else}), naming each
     * alternative relative to {@code base} ({@code 0}, {@code else}), so that a message names the part and its member.
     */
    static Closure of(JsonPointer at, JsonPointer base, List<Closure> alternatives) {
        if (alternatives.isEmpty()) {
            return Closure.open(at, "it has no alternatives", false);
        }
        return new Closure(at, widest(base, alternatives, Closure::objects),
                widest(base, alternatives, Closure::arrays));
    }

    /** One alternative open: open, naming it; one closed: closed; otherwise none. */
    private static Closure.Facet widest(JsonPointer base, List<Closure> alternatives,
            Function<Closure, Closure.Facet> facet) {
        Optional<Closure> open = alternatives.stream().filter(a -> facet.apply(a).isOpen()).findFirst();
        if (open.isPresent()) {
            Closure.Facet widest = facet.apply(open.get());
            return Closure.Facet.open("its alternative " + open.get().at().relativeTo(base) + " is open: "
                    + widest.reason(), widest.declared());
        }
        if (alternatives.stream().anyMatch(a -> facet.apply(a).admission() == Closure.Admission.CLOSED)) {
            return Closure.Facet.closed("every alternative is closed or admits none");
        }
        return Closure.Facet.none("no alternative admits one");
    }
}
