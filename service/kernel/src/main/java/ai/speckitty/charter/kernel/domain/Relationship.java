package ai.speckitty.charter.kernel.domain;

import java.util.Optional;

/**
 * A first-class relationship between two components (FR-001).
 *
 * <p><strong>Identity is the unordered {@code {from, to}} pair</strong> (ADR-0027 Decision 1): an
 * {@code A → B} relationship {@linkplain #equals(Object) equals} a {@code B → A} relationship and
 * the two {@linkplain #hashCode() hash} alike, so a {@code Set} keeps at most one relationship per
 * component pair. {@code type}, {@code when} and {@code rationale} are <em>not</em> part of identity.
 * The authored {@code from}/{@code to} orientation is still stored and readable, so a
 * {@link Directionality#DIRECTED} relation keeps its direction.
 *
 * <p>This is a {@code final class} rather than a {@code record} precisely because the generated
 * component-wise equality would make {@code A → B} unequal to {@code B → A} and silently break
 * "one relationship per pair"; equality is overridden to compare the unordered pair.
 */
public final class Relationship {

    private final Urn from;
    private final Urn to;
    private final RelationType type;
    private final Optional<String> when;
    private final Optional<String> rationale;

    /**
     * Creates a relationship.
     *
     * @param from the component the relationship is authored from
     * @param to the component the relationship is authored to
     * @param type what the relationship means
     * @param when the authored edge condition that must round-trip; empty when absent, never {@code null}
     * @param rationale the authored reason; empty when absent, never {@code null}
     * @throws IllegalArgumentException when any argument is {@code null}, or when {@code from} equals
     *     {@code to} (a self-loop)
     */
    public Relationship(Urn from, Urn to, RelationType type, Optional<String> when, Optional<String> rationale) {
        this.from = requireArg(from, "from");
        this.to = requireArg(to, "to");
        this.type = requireArg(type, "type");
        this.when = requireArg(when, "when");
        this.rationale = requireArg(rationale, "rationale");
        if (from.equals(to)) {
            throw new IllegalArgumentException("A relationship cannot be a self-loop: " + from);
        }
    }

    private static <T> T requireArg(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    /**
     * A relationship without a condition or a rationale.
     *
     * @param from the component the relationship is authored from
     * @param to the component the relationship is authored to
     * @param type what the relationship means
     * @return the relationship
     * @throws IllegalArgumentException when any argument is {@code null} or {@code from} equals {@code to}
     */
    public static Relationship of(Urn from, Urn to, RelationType type) {
        return new Relationship(from, to, type, Optional.empty(), Optional.empty());
    }

    /**
     * The component the relationship is authored from.
     *
     * @return the stored {@code from} orientation
     */
    public Urn from() {
        return from;
    }

    /**
     * The component the relationship is authored to.
     *
     * @return the stored {@code to} orientation
     */
    public Urn to() {
        return to;
    }

    /**
     * What the relationship means.
     *
     * @return the relation type
     */
    public RelationType type() {
        return type;
    }

    /**
     * The authored edge condition.
     *
     * @return the condition; empty when it has none
     */
    public Optional<String> when() {
        return when;
    }

    /**
     * The authored reason.
     *
     * @return the rationale; empty when it has none
     */
    public Optional<String> rationale() {
        return rationale;
    }

    /**
     * Whether this relationship joins the same unordered pair as another. Identity is the
     * {@code {from, to}} set only; type, condition and rationale are ignored.
     *
     * @param other the object to compare with
     * @return {@code true} when {@code other} is a relationship over the same pair, in either orientation
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Relationship that)) {
            return false;
        }
        return (from.equals(that.from) && to.equals(that.to)) || (from.equals(that.to) && to.equals(that.from));
    }

    /**
     * An order-independent hash of the {@code {from, to}} pair, so the two orientations hash alike.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return from.hashCode() + to.hashCode();
    }

    @Override
    public String toString() {
        return "Relationship{" + from + " -(" + type.token() + ")-> " + to + "}";
    }
}
