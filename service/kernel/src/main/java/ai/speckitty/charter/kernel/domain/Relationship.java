package ai.speckitty.charter.kernel.domain;

import ai.speckitty.charter.kernel.domain.kinds.Action;
import ai.speckitty.charter.kernel.domain.kinds.Advisor;
import ai.speckitty.charter.kernel.domain.kinds.AgentProfile;
import ai.speckitty.charter.kernel.domain.kinds.AntiPattern;
import ai.speckitty.charter.kernel.domain.kinds.AppliesTarget;
import ai.speckitty.charter.kernel.domain.kinds.Asset;
import ai.speckitty.charter.kernel.domain.kinds.Enhanceable;
import ai.speckitty.charter.kernel.domain.kinds.GraphAuthored;
import ai.speckitty.charter.kernel.domain.kinds.Guard;
import ai.speckitty.charter.kernel.domain.kinds.Guidance;
import ai.speckitty.charter.kernel.domain.kinds.MissionType;
import ai.speckitty.charter.kernel.domain.kinds.Overridable;
import ai.speckitty.charter.kernel.domain.kinds.RequiresTarget;
import ai.speckitty.charter.kernel.domain.kinds.ScopeSource;
import ai.speckitty.charter.kernel.domain.kinds.ScopeTarget;
import ai.speckitty.charter.kernel.domain.kinds.StepEnhancement;
import ai.speckitty.charter.kernel.domain.kinds.SuggestsTarget;
import ai.speckitty.charter.kernel.domain.kinds.Template;
import ai.speckitty.charter.kernel.domain.kinds.VocabularyTarget;
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
 *
 * <p>The typed factories (ADR-0064 decision 13) each name the relation they build: a factory's name starts with the
 * relation's token in camel case, which {@code EndpointTableAgreementTest} relies on to check them against the table.
 */
public final class Relationship {

    private final Urn from;
    private final Optional<String> fromPack;
    private final Urn to;
    private final Optional<String> toPack;
    private final RelationType type;
    private final Optional<String> when;
    private final Optional<String> rationale;

    /**
     * Creates a relationship between two bare components.
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
        this(from, Optional.empty(), to, Optional.empty(), type, when, rationale);
    }

    /**
     * Creates a relationship whose endpoints may be written pack-qualified. The URNs stay bare and decide identity; a
     * qualifier is the pack the writer relies on for that endpoint (ADR-0053 decision 7), carried beside it.
     *
     * @param from the component the relationship is authored from
     * @param fromPack the pack the source reference names; empty for a bare reference
     * @param to the component the relationship is authored to
     * @param toPack the pack the target reference names; empty for a bare reference
     * @param type what the relationship means
     * @param when the authored edge condition that must round-trip; empty when absent, never {@code null}
     * @param rationale the authored reason; empty when absent, never {@code null}
     * @throws IllegalArgumentException when any argument is {@code null}, a qualifier breaks the pack-name grammar, or
     *     {@code from} equals {@code to} with neither endpoint pack-qualified (a self-loop; a qualified endpoint names
     *     another pack's artifact of the same id, which is legal)
     */
    public Relationship(
            Urn from,
            Optional<String> fromPack,
            Urn to,
            Optional<String> toPack,
            RelationType type,
            Optional<String> when,
            Optional<String> rationale) {
        this.from = requireArg(from, "from");
        this.fromPack = requirePack(fromPack, "fromPack");
        this.to = requireArg(to, "to");
        this.toPack = requirePack(toPack, "toPack");
        this.type = requireArg(type, "type");
        this.when = requireArg(when, "when");
        this.rationale = requireArg(rationale, "rationale");
        if (from.equals(to) && fromPack.isEmpty() && toPack.isEmpty()) {
            throw new IllegalArgumentException("A relationship cannot be a self-loop: " + from);
        }
    }

    private static Optional<String> requirePack(Optional<String> pack, String name) {
        if (requireArg(pack, name)
                .filter(value -> !PackQualifiedReference.isPackName(value))
                .isPresent()) {
            throw new IllegalArgumentException(name + " follows the pack-name grammar: " + pack.get());
        }
        return pack;
    }

    // The package is @NullMarked, but the check stays: a relationship is built from parsed YAML and request bodies by
    // callers NullAway does not see, and the constructor's contract is an IllegalArgumentException naming the field,
    // not a NullPointerException later (RelationshipTest pins it).
    private static <T> T requireArg(T value, String name) {
        if (value == null) { // NOSONAR java:S2583: unannotated callers can pass null; see above
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
     * Builds the {@code requires} relationship (ADR-0064 decision 13): a guidance kind, an agent profile or a skill
     * requires a guidance kind, an asset or a template.
     *
     * @param from the source
     * @param to the target
     * @return the relationship
     */
    public static Relationship requires(KindRef<? extends Advisor> from, KindRef<? extends RequiresTarget> to) {
        return typed(from, to, RelationType.REQUIRES);
    }

    /**
     * Builds the {@code requires} relationship (ADR-0064 decision 13): an asset requires another asset.
     *
     * @param from the source
     * @param to the target
     * @return the relationship
     */
    public static Relationship requiresAsset(KindRef<Asset> from, KindRef<Asset> to) {
        return typed(from, to, RelationType.REQUIRES);
    }

    /**
     * Builds the {@code requires} relationship (ADR-0064 decision 13): a mission type requires one of its steps'
     * actions.
     *
     * @param from the source
     * @param to the target
     * @return the relationship
     */
    public static Relationship requiresStep(KindRef<MissionType> from, KindRef<Action> to) {
        return typed(from, to, RelationType.REQUIRES);
    }

    /**
     * Builds the {@code suggests} relationship (ADR-0064 decision 13): a guidance kind, an agent profile or a skill
     * suggests a guidance kind, an asset, a template or an agent profile.
     *
     * @param from the source
     * @param to the target
     * @return the relationship
     */
    public static Relationship suggests(KindRef<? extends Advisor> from, KindRef<? extends SuggestsTarget> to) {
        return typed(from, to, RelationType.SUGGESTS);
    }

    /**
     * Builds the {@code applies} relationship (ADR-0064 decision 13): a step's action applies a directive or a tactic.
     *
     * @param from the source
     * @param to the target
     * @return the relationship
     */
    public static Relationship applies(KindRef<Action> from, KindRef<? extends AppliesTarget> to) {
        return typed(from, to, RelationType.APPLIES);
    }

    /**
     * Builds the {@code scope} relationship (ADR-0064 decision 13): a mission type or a step's action scopes a guidance
     * kind or an agent profile.
     *
     * @param from the source
     * @param to the target
     * @return the relationship
     */
    public static Relationship scope(KindRef<? extends ScopeSource> from, KindRef<? extends ScopeTarget> to) {
        return typed(from, to, RelationType.SCOPE);
    }

    /**
     * Builds the {@code vocabulary} relationship (ADR-0064 decision 13): any kind a graph names takes its vocabulary
     * from a glossary.
     *
     * @param from the source
     * @param to the target
     * @return the relationship
     */
    public static Relationship vocabulary(
            KindRef<? extends GraphAuthored> from, KindRef<? extends VocabularyTarget> to) {
        return typed(from, to, RelationType.VOCABULARY);
    }

    /**
     * Builds the {@code instantiates} relationship (ADR-0064 decision 13): a step's action instantiates a template.
     *
     * @param from the source
     * @param to the target
     * @return the relationship
     */
    public static Relationship instantiates(KindRef<Action> from, KindRef<Template> to) {
        return typed(from, to, RelationType.INSTANTIATES);
    }

    /**
     * Builds the {@code refines} relationship (ADR-0064 decision 13): a guidance kind refines a guidance kind.
     *
     * @param from the source
     * @param to the target
     * @return the relationship
     */
    public static Relationship refines(KindRef<? extends Guidance> from, KindRef<? extends Guidance> to) {
        return typed(from, to, RelationType.REFINES);
    }

    /**
     * Builds the {@code specializes_from} relationship (ADR-0064 decision 13): an agent profile specializes another.
     *
     * @param from the source
     * @param to the target
     * @return the relationship
     */
    public static Relationship specializesFrom(KindRef<AgentProfile> from, KindRef<AgentProfile> to) {
        return typed(from, to, RelationType.SPECIALIZES_FROM);
    }

    /**
     * Builds the {@code enhances} relationship (ADR-0064 decision 13): a step enhancement enhances a step's action.
     *
     * @param from the source
     * @param to the target
     * @return the relationship
     */
    public static Relationship enhancesStep(KindRef<StepEnhancement> from, KindRef<Action> to) {
        return typed(from, to, RelationType.ENHANCES);
    }

    /**
     * Builds the {@code enhances} relationship (ADR-0064 decision 13): an artifact enhances another of its own kind
     * (every activatable kind but mission_type and step_enhancement).
     *
     * @param <K> the one kind both ends share
     * @param from the source
     * @param to the target
     * @return the relationship
     */
    public static <K extends Enhanceable> Relationship enhancesSame(KindRef<K> from, KindRef<K> to) {
        return typed(from, to, RelationType.ENHANCES);
    }

    /**
     * Builds the {@code overrides} relationship (ADR-0064 decision 13): an artifact overrides another of its own kind
     * (every activatable kind but step_enhancement).
     *
     * @param <K> the one kind both ends share
     * @param from the source
     * @param to the target
     * @return the relationship
     */
    public static <K extends Overridable> Relationship overrides(KindRef<K> from, KindRef<K> to) {
        return typed(from, to, RelationType.OVERRIDES);
    }

    /**
     * Builds the {@code reconciles_tension} relationship (ADR-0064 decision 13): a guidance kind reconciles a tension
     * with a guidance kind.
     *
     * @param from the source
     * @param to the target
     * @return the relationship
     */
    public static Relationship reconcilesTension(KindRef<? extends Guidance> from, KindRef<? extends Guidance> to) {
        return typed(from, to, RelationType.RECONCILES_TENSION);
    }

    /**
     * Builds the {@code rejects} relationship (ADR-0064 decision 13): a guidance kind rejects an anti-pattern.
     *
     * @param from the source
     * @param to the target
     * @return the relationship
     */
    public static Relationship rejects(KindRef<? extends Guidance> from, KindRef<AntiPattern> to) {
        return typed(from, to, RelationType.REJECTS);
    }

    /**
     * Builds the {@code implemented_by} relationship (ADR-0064 decision 13): a guard is implemented by an asset
     * (derived from the guard's field, never authored).
     *
     * @param from the source
     * @param to the target
     * @return the relationship
     */
    public static Relationship implementedBy(KindRef<Guard> from, KindRef<Asset> to) {
        return typed(from, to, RelationType.IMPLEMENTED_BY);
    }

    private static Relationship typed(KindRef<?> from, KindRef<?> to, RelationType type) {
        return new Relationship(from.urn(), from.pack(), to.urn(), to.pack(), type, Optional.empty(), Optional.empty());
    }

    /**
     * The same relationship with its authored annotations.
     *
     * @param newWhen when the relationship applies
     * @param newRationale why it holds
     * @return a copy carrying them
     */
    public Relationship annotated(Optional<String> newWhen, Optional<String> newRationale) {
        return new Relationship(from, fromPack, to, toPack, type, newWhen, newRationale);
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
     * The pack the source reference names.
     *
     * @return the pack a qualified source relies on; empty for a bare source
     */
    public Optional<String> fromPack() {
        return fromPack;
    }

    /**
     * The pack the target reference names.
     *
     * @return the pack a qualified target relies on; empty for a bare target
     */
    public Optional<String> toPack() {
        return toPack;
    }

    /**
     * The source as the writer wrote it.
     *
     * @return the source reference, pack-qualified when the writer qualified it
     */
    public PackQualifiedReference fromReference() {
        return new PackQualifiedReference(fromPack, from.toString());
    }

    /**
     * The target as the writer wrote it.
     *
     * @return the target reference, pack-qualified when the writer qualified it
     */
    public PackQualifiedReference toReference() {
        return new PackQualifiedReference(toPack, to.toString());
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
