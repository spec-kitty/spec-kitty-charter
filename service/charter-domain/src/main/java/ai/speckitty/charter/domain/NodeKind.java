package ai.speckitty.charter.domain;

import java.util.Arrays;
import java.util.Optional;

/** The kind of a DRG node: every artifact kind plus {@code action} and {@code mission_type}. */
public enum NodeKind {
    /** A node of the artifact kind {@link ArtifactKind#DIRECTIVE}. */
    DIRECTIVE,
    /** A node of the artifact kind {@link ArtifactKind#TACTIC}. */
    TACTIC,
    /** A node of the artifact kind {@link ArtifactKind#STYLEGUIDE}. */
    STYLEGUIDE,
    /** A node of the artifact kind {@link ArtifactKind#TOOLGUIDE}. */
    TOOLGUIDE,
    /** A node of the artifact kind {@link ArtifactKind#PARADIGM}. */
    PARADIGM,
    /** A node of the artifact kind {@link ArtifactKind#PROCEDURE}. */
    PROCEDURE,
    /** A node of the artifact kind {@link ArtifactKind#AGENT_PROFILE}. */
    AGENT_PROFILE,
    /** A node of the artifact kind {@link ArtifactKind#MISSION_STEP_CONTRACT}. */
    MISSION_STEP_CONTRACT,
    /** A node of the artifact kind {@link ArtifactKind#GLOSSARY_PACK}. */
    GLOSSARY_PACK,
    /** A node of the artifact kind {@link ArtifactKind#SKILL}. */
    SKILL,
    /** A node of the artifact kind {@link ArtifactKind#ANTI_PATTERN}. */
    ANTI_PATTERN,
    /** A node of the artifact kind {@link ArtifactKind#ASSET}. */
    ASSET,
    /** A node of the artifact kind {@link ArtifactKind#TEMPLATE}. */
    TEMPLATE,
    /** One action of one mission type, for example {@code action:software-dev/plan}; action-scoped context starts here. */
    ACTION,
    /** A mission type, for example {@code mission_type:software-dev}. */
    MISSION_TYPE;

    /**
     * The spelling used in URNs.
     *
     * @return the lower-case constant name, for example {@code mission_type}
     */
    public String token() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The artifact kind this node kind stands for.
     *
     * @return the artifact kind; empty for {@link #ACTION} and {@link #MISSION_TYPE}
     */
    public Optional<ArtifactKind> artifactKind() {
        return ArtifactKind.fromToken(token());
    }

    /**
     * Looks a node kind up by its URN token.
     *
     * @param token a token, for example {@code directive}
     * @return the node kind; empty when no node kind has that token
     */
    public static Optional<NodeKind> fromToken(String token) {
        return Arrays.stream(values()).filter(k -> k.token().equals(token)).findFirst();
    }

    /**
     * The node kind of an artifact kind; every artifact kind has one.
     *
     * @param kind an artifact kind
     * @return the node kind of the same name
     */
    public static NodeKind of(ArtifactKind kind) {
        return valueOf(kind.name());
    }

    /**
     * The node kind a fragment node names by its plural {@code kind} (R14: one name per concept; core's aliases are
     * refused).
     *
     * @param plural the plural kind as written in {@code drg/fragment.yaml}
     * @return the node kind, or empty when the name is not one of the 15 plurals
     */
    public static Optional<NodeKind> fromPlural(String plural) {
        return switch (plural) {
            case "mission_types" -> Optional.of(MISSION_TYPE);
            case "actions" -> Optional.of(ACTION);
            default -> ArtifactKind.fromPlural(plural).map(NodeKind::of);
        };
    }
}
