package ai.speckitty.charter.kernel.domain;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The kind of a CRG node: one per artifact kind, plus {@code action} and {@code term}. Each constant states its facts in one table row, its
 * URN token, the artifact kind it stands for, its fragment plural and its two graph flags, and nothing is derived from
 * the constant's name: a URN token may differ from its artifact kind's token (ADR-0053 decision 13).
 */
public enum NodeKind {
    /** A node of the artifact kind {@link ArtifactKind#DIRECTIVE}. */
    DIRECTIVE("directive", ArtifactKind.DIRECTIVE, "directives", true, true),
    /** A node of the artifact kind {@link ArtifactKind#TACTIC}. */
    TACTIC("tactic", ArtifactKind.TACTIC, "tactics", true, true),
    /** A node of the artifact kind {@link ArtifactKind#STYLEGUIDE}. */
    STYLEGUIDE("styleguide", ArtifactKind.STYLEGUIDE, "styleguides", true, true),
    /** A node of the artifact kind {@link ArtifactKind#TOOLGUIDE}. */
    TOOLGUIDE("toolguide", ArtifactKind.TOOLGUIDE, "toolguides", true, true),
    /** A node of the artifact kind {@link ArtifactKind#PARADIGM}. */
    PARADIGM("paradigm", ArtifactKind.PARADIGM, "paradigms", true, true),
    /** A node of the artifact kind {@link ArtifactKind#PROCEDURE}. */
    PROCEDURE("procedure", ArtifactKind.PROCEDURE, "procedures", true, true),
    /** A node of the artifact kind {@link ArtifactKind#AGENT_PROFILE}. */
    AGENT_PROFILE("agent_profile", ArtifactKind.AGENT_PROFILE, "agent_profiles", true, true),
    /** A node of the artifact kind {@link ArtifactKind#MISSION_STEP_CONTRACT}. */
    MISSION_STEP_CONTRACT(
            "mission_step_contract", ArtifactKind.MISSION_STEP_CONTRACT, "mission_step_contracts", true, true),
    /**
     * A glossary context, the artifact of the kind {@link ArtifactKind#GLOSSARY}, for example
     * {@code glossary_context:spec-kitty-core}. Its URN token is not its kind's token {@code glossary} on purpose
     * (ADR-0053 decision 13): it keeps clear of core's retiring {@code glossary} node kind, whose meaning differs.
     */
    GLOSSARY_CONTEXT("glossary_context", ArtifactKind.GLOSSARY, "glossaries", true, true),
    /**
     * One term of a glossary context, {@code term:<context-id>/<term-id>} (ADR-0049 decision 4): an item of its
     * context, not an artifact, so it stands for no artifact kind. It has no file and no fragment plural and is never
     * declared in a graph; it is derived from its context, and it is an edge endpoint only as the target of a
     * {@code vocabulary} edge (operator ruling of 2026-11-14).
     */
    TERM("term", RelationType.VOCABULARY),
    /** A node of the artifact kind {@link ArtifactKind#SKILL}. */
    SKILL("skill", ArtifactKind.SKILL, "skills", true, true),
    /** A node of the artifact kind {@link ArtifactKind#ANTI_PATTERN}. */
    ANTI_PATTERN("anti_pattern", ArtifactKind.ANTI_PATTERN, "anti_patterns", true, true),
    /** A node of the artifact kind {@link ArtifactKind#ASSET}. */
    ASSET("asset", ArtifactKind.ASSET, "assets", true, true),
    /** A node of the artifact kind {@link ArtifactKind#TEMPLATE}. */
    TEMPLATE("template", ArtifactKind.TEMPLATE, "templates", true, true),
    /** One action of one mission type, for example {@code action:software-dev/plan}; action-scoped context starts here. */
    ACTION("action", null, "actions", true, true),
    /** A node of the artifact kind {@link ArtifactKind#MISSION_TYPE}, for example {@code mission_type:software-dev}. */
    MISSION_TYPE("mission_type", ArtifactKind.MISSION_TYPE, "mission_types", true, true),
    /**
     * A node of the artifact kind {@link ArtifactKind#GUARD}, for example {@code guard:subtasks-complete}. Neither
     * declared in a graph nor an edge endpoint: its only edge is the {@code implemented_by} a guard derives, and an
     * authored edge to or from it is refused until its contract set lands (operator ruling of 2026-11-02, #156; work
     * breakdown item 13).
     */
    GUARD("guard", ArtifactKind.GUARD, "guards", false, false),
    /**
     * A node of the artifact kind {@link ArtifactKind#LIFECYCLE}, for example {@code lifecycle:work-package}. Neither
     * declared in a graph nor an edge endpoint, like {@link #GUARD} (#156).
     */
    LIFECYCLE("lifecycle", ArtifactKind.LIFECYCLE, "lifecycles", false, false),
    /** A node of the artifact kind {@link ArtifactKind#STEP_ENHANCEMENT}. */
    STEP_ENHANCEMENT("step_enhancement", ArtifactKind.STEP_ENHANCEMENT, "step_enhancements", true, true);

    /**
     * The node kind of each artifact kind, built once and checked then: it is total and one to one, so
     * {@link #of(ArtifactKind)} has no branch of its own.
     */
    private static final Map<ArtifactKind, NodeKind> BY_ARTIFACT_KIND =
            byArtifactKind(List.of(values()), NodeKind::artifactKind);

    private final String token;
    private final @Nullable ArtifactKind artifactKind;
    private final @Nullable String plural;
    private final boolean declarable;
    private final boolean edgeTarget;
    private final @Nullable RelationType onlyTargetOf;

    NodeKind(
            String token,
            @Nullable ArtifactKind artifactKind,
            @Nullable String plural,
            boolean declarable,
            boolean edgeTarget) {
        this.token = token;
        this.artifactKind = artifactKind;
        this.plural = plural;
        this.declarable = declarable;
        this.edgeTarget = edgeTarget;
        this.onlyTargetOf = null;
    }

    /** A kind that is an edge endpoint only as the target of one relation, and nothing else: no artifact, no plural. */
    NodeKind(String token, RelationType onlyTargetOf) {
        this.token = token;
        this.artifactKind = null;
        this.plural = null;
        this.declarable = false;
        this.edgeTarget = true;
        this.onlyTargetOf = onlyTargetOf;
    }

    /**
     * Indexes node kinds by the artifact kind each stands for, refusing a table that is not one to one: two node kinds
     * that stand for one artifact kind, or an artifact kind no node kind stands for. Run once, when the class
     * initializes, so a bad table fails at once rather than answering one of two kinds.
     *
     * @param kinds the node kinds, in declaration order
     * @param standsFor the artifact kind a node kind stands for, if any
     * @return the node kind of every artifact kind
     * @throws IllegalStateException when two node kinds stand for one artifact kind, or an artifact kind has none
     */
    static Map<ArtifactKind, NodeKind> byArtifactKind(
            List<NodeKind> kinds, Function<NodeKind, Optional<ArtifactKind>> standsFor) {
        Map<ArtifactKind, NodeKind> map = new EnumMap<>(ArtifactKind.class);
        for (NodeKind kind : kinds) {
            standsFor.apply(kind).ifPresent(artifactKind -> {
                NodeKind earlier = map.putIfAbsent(artifactKind, kind);
                if (earlier != null) {
                    throw new IllegalStateException("the node kinds " + earlier.token + " and " + kind.token
                            + " both stand for the artifact kind " + artifactKind.token());
                }
            });
        }
        for (ArtifactKind kind : ArtifactKind.values()) {
            if (!map.containsKey(kind)) {
                throw new IllegalStateException("no node kind stands for the artifact kind " + kind.token());
            }
        }
        return map;
    }

    /**
     * The spelling used in URNs.
     *
     * @return the URN token, for example {@code mission_type}
     */
    public String token() {
        return token;
    }

    /**
     * Whether a graph document or a fragment may declare a node of this kind.
     *
     * @return {@code false} for {@link #GUARD} and {@link #LIFECYCLE}, which wait for their contract set (#156), and
     *     for {@link #TERM}, which is derived from its glossary context
     */
    public boolean declarableInGraphs() {
        return declarable;
    }

    /**
     * Whether an edge, authored in a graph document or registered as a relationship, may name this kind at all, at one
     * end or the other. Which end, and of which relation, is {@link #edgeSource()} and {@link #edgeTargetOf}.
     *
     * @return {@code false} for {@link #GUARD} and {@link #LIFECYCLE}, which wait for their contract set (#156)
     */
    public boolean edgeTarget() {
        return edgeTarget;
    }

    /**
     * Whether an authored or registered edge, a tension included, may name this kind as its source.
     *
     * @return {@code false} for {@link #GUARD} and {@link #LIFECYCLE} (#156), and for {@link #TERM}, which is only
     *     ever a {@code vocabulary} target
     */
    public boolean edgeSource() {
        return edgeTarget && onlyTargetOf == null;
    }

    /**
     * Whether an authored or registered edge of the relation may name this kind as its target.
     *
     * @param relation the edge's relation type; empty for a tension, which has none
     * @return {@code false} for {@link #GUARD} and {@link #LIFECYCLE} (#156), and for {@link #TERM} unless the
     *     relation is {@code vocabulary}
     */
    public boolean edgeTargetOf(Optional<RelationType> relation) {
        return edgeTarget
                && (onlyTargetOf == null
                        || relation.filter(onlyTargetOf::equals).isPresent());
    }

    /**
     * The artifact kind this node kind stands for.
     *
     * @return the artifact kind; empty for {@link #ACTION} and {@link #TERM}
     */
    public Optional<ArtifactKind> artifactKind() {
        return Optional.ofNullable(artifactKind);
    }

    /**
     * Looks a node kind up by its URN token.
     *
     * @param token a token, for example {@code directive}
     * @return the node kind; empty when no node kind has that token
     */
    public static Optional<NodeKind> fromToken(String token) {
        return Arrays.stream(values()).filter(k -> k.token.equals(token)).findFirst();
    }

    /**
     * The node kind of an artifact kind; every artifact kind has one.
     *
     * @param kind an artifact kind
     * @return the node kind that stands for it
     */
    public static NodeKind of(ArtifactKind kind) {
        return Objects.requireNonNull(BY_ARTIFACT_KIND.get(kind), "the index is total");
    }

    /**
     * Whether a fragment node may name this kind by a plural.
     *
     * @return {@code true} when the kind has a {@linkplain #plural() plural}
     */
    public boolean hasPlural() {
        return plural != null;
    }

    /**
     * The plural a fragment node names this kind by, the inverse of {@link #fromPlural(String)}. A kind that stands for
     * an artifact kind has that kind's {@link ArtifactKind#plural()}.
     *
     * @return the plural kind, for example {@code agent_profiles}, {@code actions} or {@code mission_types}
     * @throws IllegalStateException when the kind has no plural ({@link #hasPlural()} is {@code false})
     */
    public String plural() {
        if (plural == null) {
            throw new IllegalStateException("the node kind " + token + " has no fragment plural");
        }
        return plural;
    }

    /**
     * The node kind a fragment node names by its plural {@code kind} (R14: one name per concept; core's aliases are
     * refused).
     *
     * @param plural the plural kind as written in {@code drg/fragment.yaml}
     * @return the node kind, or empty when the name is not the plural of a node kind
     */
    public static Optional<NodeKind> fromPlural(String plural) {
        return Arrays.stream(values()).filter(k -> plural.equals(k.plural)).findFirst();
    }
}
