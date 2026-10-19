package ai.speckitty.charter.contracts.closure;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Parts that all apply to one value: a schema's own keywords, its {@code allOf} parts, unions and conditions. Each
 * facet is the narrowest of the parts' facets. A part is named by where it sits below the holder ({@code allOf/1},
 * {@code anyOf}, {@code then|else}); the holder's own keywords sit at the holder.
 */
final class Conjunction {

    private Conjunction() {
    }

    static Closure of(JsonPointer holder, List<Closure> parts) {
        return new Closure(holder, objects(holder, parts), narrowest(holder, parts, Closure::arrays));
    }

    /**
     * Like {@link #narrowest}, except that a part admitting no object never excuses an open part whose type names
     * {@code object}. The pairing is refused by convention, as a likely authoring mistake, even where a value
     * satisfies both parts ({@code type: [object, string]} beside {@code allOf: [{type: string}]}).
     */
    private static Closure.Facet objects(JsonPointer holder, List<Closure> parts) {
        Optional<Closure> none = parts.stream()
                .filter(p -> p.objects().admission() == Closure.Admission.NONE).findFirst();
        Optional<Closure> declared = parts.stream()
                .filter(p -> p.objects().isOpen() && p.objects().declared()).findFirst();
        if (none.isEmpty() || declared.isEmpty()) {
            return narrowest(holder, parts, Closure::objects);
        }
        return Closure.Facet.open("an open object (" + declared.get().objects().reason() + "; from "
                + name(holder, declared.get()) + ") sits beside a part that admits no object ("
                + none.get().objects().reason() + "; from " + name(holder, none.get()) + "): this pairing is refused"
                + " by convention, as a likely authoring mistake, even where a value satisfies both parts", true);
    }

    /** No part admits the kind: none; one part closes it: closed; otherwise open, naming every part. */
    private static Closure.Facet narrowest(JsonPointer holder, List<Closure> parts, Function<Closure, Closure.Facet> facet) {
        Optional<Closure> none = parts.stream().filter(p -> facet.apply(p).admission() == Closure.Admission.NONE).findFirst();
        if (none.isPresent()) {
            return Closure.Facet.none(describe(holder, none.get(), facet));
        }
        Optional<Closure> closed = parts.stream().filter(p -> facet.apply(p).admission() == Closure.Admission.CLOSED).findFirst();
        if (closed.isPresent()) {
            return Closure.Facet.closed("closed by " + describe(holder, closed.get(), facet));
        }
        return Closure.Facet.open("no part closes it: " + parts.stream().map(p -> describe(holder, p, facet)).collect(Collectors.joining("; ")),
                parts.stream().anyMatch(p -> facet.apply(p).declared()));
    }

    /**
     * A part's reason, prefixed with where the part sits below the holder; a part at the holder is its reason
     * alone.
     */
    private static String describe(JsonPointer holder, Closure part, Function<Closure, Closure.Facet> facet) {
        String where = part.at().relativeTo(holder);
        return (where.isEmpty() ? "" : where + ": ") + facet.apply(part).reason();
    }

    private static String name(JsonPointer holder, Closure part) {
        String where = part.at().relativeTo(holder);
        return where.isEmpty() ? "its own keywords" : where;
    }
}
