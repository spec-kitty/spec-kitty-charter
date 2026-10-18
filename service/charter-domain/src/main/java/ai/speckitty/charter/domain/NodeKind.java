package ai.speckitty.charter.domain;

import java.util.Arrays;
import java.util.Optional;

/** The kind of a DRG node: every artifact kind plus {@code action} and {@code mission_type}. */
public enum NodeKind {
    DIRECTIVE, TACTIC, STYLEGUIDE, TOOLGUIDE, PARADIGM, PROCEDURE, AGENT_PROFILE, MISSION_STEP_CONTRACT,
    GLOSSARY_PACK, SKILL, ANTI_PATTERN, ASSET, TEMPLATE, ACTION, MISSION_TYPE;

    public String token() { return name().toLowerCase(java.util.Locale.ROOT); }

    public Optional<ArtifactKind> artifactKind() { return ArtifactKind.fromToken(token()); }

    public static Optional<NodeKind> fromToken(String token) {
        return Arrays.stream(values()).filter(k -> k.token().equals(token)).findFirst();
    }

    public static NodeKind of(ArtifactKind kind) { return valueOf(kind.name()); }
}
