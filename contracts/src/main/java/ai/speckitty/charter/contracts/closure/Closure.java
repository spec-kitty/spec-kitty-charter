package ai.speckitty.charter.contracts.closure;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * How one schema classifies, judged separately for the objects it admits and for the arrays it admits, because a
 * conjunction can rule out one kind of value with one part and close the other kind with another part
 * ({@code type: object} beside an untyped {@code then: {additionalProperties: false}}).
 *
 * @param at      the schema
 * @param objects what it admits of objects
 * @param arrays  what it admits of arrays
 */
public record Closure(JsonPointer at, Facet objects, Facet arrays) {

    /** The classification of the convention, derived from the two facets. */
    public enum Kind {
        /** Admits no object with members it does not list. */
        CLOSED,
        /** Provably admits no object, and no array whose items go unjudged. */
        NOT_AN_OBJECT,
        /** May admit an object with members it does not list, or an array of anything. */
        OPEN
    }

    /** What a schema admits of one kind of value, ordered from narrowest to widest. */
    public enum Admission {
        /** No value of this kind. */
        NONE,
        /**
         * Objects: only listed members. Arrays: only items judged at an {@code items} or {@code prefixItems}
         * position.
         */
        CLOSED,
        /** Values of this kind the checker cannot prove closed. */
        OPEN
    }

    /**
     * One facet of a classification.
     *
     * @param admission what is admitted
     * @param reason    why, naming the parts that decided it
     * @param declared  for an open object facet: whether an explicit {@code type} naming {@code object} is among what
     *                  is open, so that a part that admits no object beside it is a contradiction, not an excuse
     */
    public record Facet(Admission admission, String reason, boolean declared) {

        static Facet none(String reason) {
            return new Facet(Admission.NONE, reason, false);
        }

        static Facet closed(String reason) {
            return new Facet(Admission.CLOSED, reason, false);
        }

        static Facet open(String reason, boolean declared) {
            return new Facet(Admission.OPEN, reason, declared);
        }

        boolean isOpen() {
            return admission == Admission.OPEN;
        }
    }

    /** A closed object schema that admits no array. */
    static Closure closedObject(JsonPointer at, String reason) {
        return new Closure(at, Facet.closed(reason), Facet.none(reason));
    }

    /** A schema that admits neither objects nor arrays. */
    static Closure notAnObject(JsonPointer at, String reason) {
        return new Closure(at, Facet.none(reason), Facet.none(reason));
    }

    /** A schema open on both facets for one reason. */
    static Closure open(JsonPointer at, String reason, boolean declared) {
        return new Closure(at, Facet.open(reason, declared), Facet.open(reason, false));
    }

    /**
     * The classification: open when either facet is open, closed when it admits closed objects, else not an object.
     *
     * @return the schema's kind
     */
    public Kind kind() {
        if (objects.isOpen() || arrays.isOpen()) {
            return Kind.OPEN;
        }
        return objects.admission() == Admission.CLOSED ? Kind.CLOSED : Kind.NOT_AN_OBJECT;
    }

    /**
     * Whether the schema passes the convention.
     *
     * @return {@code true} unless the schema is open
     */
    public boolean passes() {
        return kind() != Kind.OPEN;
    }

    /**
     * Why the schema is open (each open facet's reason), or why it passes.
     *
     * @return a phrase for the report line
     */
    public String reason() {
        if (passes()) {
            return kind() == Kind.CLOSED ? objects.reason() : "it admits no object: " + objects.reason();
        }
        return Stream.of(objects, arrays)
                .filter(Facet::isOpen)
                .map(Facet::reason)
                .distinct()
                .collect(Collectors.joining("; and "));
    }
}
