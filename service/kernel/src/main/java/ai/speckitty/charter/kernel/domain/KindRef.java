package ai.speckitty.charter.kernel.domain;

import ai.speckitty.charter.kernel.domain.kinds.Action;
import ai.speckitty.charter.kernel.domain.kinds.AgentProfile;
import ai.speckitty.charter.kernel.domain.kinds.AntiPattern;
import ai.speckitty.charter.kernel.domain.kinds.Asset;
import ai.speckitty.charter.kernel.domain.kinds.Directive;
import ai.speckitty.charter.kernel.domain.kinds.GlossaryContext;
import ai.speckitty.charter.kernel.domain.kinds.Guard;
import ai.speckitty.charter.kernel.domain.kinds.Kind;
import ai.speckitty.charter.kernel.domain.kinds.Lifecycle;
import ai.speckitty.charter.kernel.domain.kinds.MissionType;
import ai.speckitty.charter.kernel.domain.kinds.Paradigm;
import ai.speckitty.charter.kernel.domain.kinds.Procedure;
import ai.speckitty.charter.kernel.domain.kinds.RoutingCatalog;
import ai.speckitty.charter.kernel.domain.kinds.Skill;
import ai.speckitty.charter.kernel.domain.kinds.StepEnhancement;
import ai.speckitty.charter.kernel.domain.kinds.Styleguide;
import ai.speckitty.charter.kernel.domain.kinds.Tactic;
import ai.speckitty.charter.kernel.domain.kinds.Template;
import ai.speckitty.charter.kernel.domain.kinds.Term;
import ai.speckitty.charter.kernel.domain.kinds.Toolguide;
import java.util.Objects;
import java.util.Optional;

/**
 * A node reference whose kind is a type (ADR-0064 decision 13), so the typed factories of {@link Relationship} accept
 * only the kinds their relation allows. It is built only through the per-kind factories below, never for a group
 * type, so a reference to "any guidance kind" cannot exist and a same-kind relation is checked by the compiler.
 *
 * @param <K> the leaf kind type
 */
public final class KindRef<K extends Kind> {

    private final Urn urn;
    private final Optional<String> pack;

    private KindRef(Urn urn, Optional<String> pack) {
        this.urn = Objects.requireNonNull(urn, "urn");
        this.pack = Objects.requireNonNull(pack, "pack");
    }

    /**
     * The same reference, qualified by the pack that holds the node.
     *
     * @param packName the pack name, in the pack-name grammar
     * @return the qualified reference
     */
    public KindRef<K> inPack(String packName) {
        if (!PackQualifiedReference.isPackName(packName)) {
            throw new IllegalArgumentException("pack follows the pack-name grammar: " + packName);
        }
        return new KindRef<>(urn, Optional.of(packName));
    }

    /**
     * The node.
     *
     * @return its URN
     */
    public Urn urn() {
        return urn;
    }

    /**
     * The pack that holds the node, when the reference is qualified.
     *
     * @return the pack name; empty for this pack
     */
    public Optional<String> pack() {
        return pack;
    }

    /**
     * A reference to a step's {@code action} node.
     *
     * @param missionType the mission type's id
     * @param step the step's id
     * @return the typed reference, in this pack
     */
    public static KindRef<Action> action(String missionType, String step) {
        return new KindRef<>(Urn.action(missionType, step), Optional.empty());
    }

    /**
     * A reference to a {@code directive} node.
     *
     * @param id the node's id
     * @return the typed reference, in this pack
     */
    public static KindRef<Directive> directive(String id) {
        return new KindRef<>(new Urn(NodeKind.DIRECTIVE, id), Optional.empty());
    }

    /**
     * A reference to a {@code tactic} node.
     *
     * @param id the node's id
     * @return the typed reference, in this pack
     */
    public static KindRef<Tactic> tactic(String id) {
        return new KindRef<>(new Urn(NodeKind.TACTIC, id), Optional.empty());
    }

    /**
     * A reference to a {@code styleguide} node.
     *
     * @param id the node's id
     * @return the typed reference, in this pack
     */
    public static KindRef<Styleguide> styleguide(String id) {
        return new KindRef<>(new Urn(NodeKind.STYLEGUIDE, id), Optional.empty());
    }

    /**
     * A reference to a {@code toolguide} node.
     *
     * @param id the node's id
     * @return the typed reference, in this pack
     */
    public static KindRef<Toolguide> toolguide(String id) {
        return new KindRef<>(new Urn(NodeKind.TOOLGUIDE, id), Optional.empty());
    }

    /**
     * A reference to a {@code paradigm} node.
     *
     * @param id the node's id
     * @return the typed reference, in this pack
     */
    public static KindRef<Paradigm> paradigm(String id) {
        return new KindRef<>(new Urn(NodeKind.PARADIGM, id), Optional.empty());
    }

    /**
     * A reference to a {@code procedure} node.
     *
     * @param id the node's id
     * @return the typed reference, in this pack
     */
    public static KindRef<Procedure> procedure(String id) {
        return new KindRef<>(new Urn(NodeKind.PROCEDURE, id), Optional.empty());
    }

    /**
     * A reference to an {@code agent_profile} node.
     *
     * @param id the node's id
     * @return the typed reference, in this pack
     */
    public static KindRef<AgentProfile> agentProfile(String id) {
        return new KindRef<>(new Urn(NodeKind.AGENT_PROFILE, id), Optional.empty());
    }

    /**
     * A reference to a {@code glossary_context} node.
     *
     * @param id the context's id
     * @return the typed reference, in this pack
     */
    public static KindRef<GlossaryContext> glossaryContext(String id) {
        return new KindRef<>(new Urn(NodeKind.GLOSSARY_CONTEXT, id), Optional.empty());
    }

    /**
     * A reference to one term of a glossary context, {@code term:<context-id>/<term-id>} (ADR-0049 decision 4).
     *
     * @param contextId the context's id
     * @param termId the term's id within the context
     * @return the typed reference, in this pack
     */
    public static KindRef<Term> term(String contextId, String termId) {
        return new KindRef<>(new Urn(NodeKind.TERM, contextId + "/" + termId), Optional.empty());
    }

    /**
     * A reference to a {@code skill} node.
     *
     * @param id the node's id
     * @return the typed reference, in this pack
     */
    public static KindRef<Skill> skill(String id) {
        return new KindRef<>(new Urn(NodeKind.SKILL, id), Optional.empty());
    }

    /**
     * A reference to an {@code anti_pattern} node.
     *
     * @param id the node's id
     * @return the typed reference, in this pack
     */
    public static KindRef<AntiPattern> antiPattern(String id) {
        return new KindRef<>(new Urn(NodeKind.ANTI_PATTERN, id), Optional.empty());
    }

    /**
     * A reference to an {@code asset} node.
     *
     * @param id the node's id
     * @return the typed reference, in this pack
     */
    public static KindRef<Asset> asset(String id) {
        return new KindRef<>(new Urn(NodeKind.ASSET, id), Optional.empty());
    }

    /**
     * A reference to a {@code template} node.
     *
     * @param id the node's id
     * @return the typed reference, in this pack
     */
    public static KindRef<Template> template(String id) {
        return new KindRef<>(new Urn(NodeKind.TEMPLATE, id), Optional.empty());
    }

    /**
     * A reference to a {@code mission_type} node.
     *
     * @param id the node's id
     * @return the typed reference, in this pack
     */
    public static KindRef<MissionType> missionType(String id) {
        return new KindRef<>(new Urn(NodeKind.MISSION_TYPE, id), Optional.empty());
    }

    /**
     * A reference to a {@code guard} node.
     *
     * @param id the node's id
     * @return the typed reference, in this pack
     */
    public static KindRef<Guard> guard(String id) {
        return new KindRef<>(new Urn(NodeKind.GUARD, id), Optional.empty());
    }

    /**
     * A reference to a {@code lifecycle} node.
     *
     * @param id the node's id
     * @return the typed reference, in this pack
     */
    public static KindRef<Lifecycle> lifecycle(String id) {
        return new KindRef<>(new Urn(NodeKind.LIFECYCLE, id), Optional.empty());
    }

    /**
     * A reference to a {@code step_enhancement} node.
     *
     * @param id the node's id
     * @return the typed reference, in this pack
     */
    public static KindRef<StepEnhancement> stepEnhancement(String id) {
        return new KindRef<>(new Urn(NodeKind.STEP_ENHANCEMENT, id), Optional.empty());
    }

    /**
     * A reference to a {@code routing_catalog} node (ADR-0080). No relation admits it, so the reference names a catalog
     * for a read or a refusal, never for an edge.
     *
     * @param id the node's id
     * @return the typed reference, in this pack
     */
    public static KindRef<RoutingCatalog> routingCatalog(String id) {
        return new KindRef<>(new Urn(NodeKind.ROUTING_CATALOG, id), Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof KindRef<?> that && urn.equals(that.urn) && pack.equals(that.pack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(urn, pack);
    }

    @Override
    public String toString() {
        return pack.map(p -> p + "::").orElse("") + urn;
    }
}
