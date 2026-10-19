package ai.speckitty.charter.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * The artifact kinds and their facts, mirroring core's {@code ArtifactKind}
 * (docs/architecture/logical-model.md, "Artifact kinds"). The conformance corpus checks the facts.
 */
public enum ArtifactKind {
    DIRECTIVE("directive", "directives", true, true, true, InForceWhenAbsent.ALL),
    TACTIC("tactic", "tactics", true, true, true, InForceWhenAbsent.ALL),
    STYLEGUIDE("styleguide", "styleguides", true, true, true, InForceWhenAbsent.ALL),
    TOOLGUIDE("toolguide", "toolguides", true, true, true, InForceWhenAbsent.ALL),
    PARADIGM("paradigm", "paradigms", true, true, true, InForceWhenAbsent.ALL),
    PROCEDURE("procedure", "procedures", true, true, true, InForceWhenAbsent.ALL),
    AGENT_PROFILE("agent_profile", "agent_profiles", true, true, true, InForceWhenAbsent.ALL),
    MISSION_STEP_CONTRACT("mission_step_contract", "mission_step_contracts", true, true, true, InForceWhenAbsent.ALL),
    GLOSSARY_PACK("glossary_pack", "glossary_packs", true, true, false, InForceWhenAbsent.ALL),
    SKILL("skill", "skills", true, true, false, InForceWhenAbsent.REQUIRED),
    ANTI_PATTERN("anti_pattern", "anti_patterns", true, false, false, InForceWhenAbsent.ALL),
    ASSET("asset", "assets", false, true, false, InForceWhenAbsent.ALL),
    TEMPLATE("template", "templates", false, false, false, InForceWhenAbsent.ALL);

    /** What is in force while a kind's activation key is absent. */
    public enum InForceWhenAbsent {
        ALL,
        REQUIRED
    }

    private final String token;
    private final String plural;
    private final boolean activatable;
    private final boolean orgRequirable;
    private final boolean selectionOverlayable;
    private final InForceWhenAbsent inForceWhenAbsent;

    ArtifactKind(
            String token,
            String plural,
            boolean activatable,
            boolean orgRequirable,
            boolean selectionOverlayable,
            InForceWhenAbsent inForceWhenAbsent) {
        this.token = token;
        this.plural = plural;
        this.activatable = activatable;
        this.orgRequirable = orgRequirable;
        this.selectionOverlayable = selectionOverlayable;
        this.inForceWhenAbsent = inForceWhenAbsent;
    }

    /** The canonical (singular, snake_case) spelling used in URNs. */
    public String token() {
        return token;
    }

    public String plural() {
        return plural;
    }

    public boolean activatable() {
        return activatable;
    }

    public boolean orgRequirable() {
        return orgRequirable;
    }

    public boolean selectionOverlayable() {
        return selectionOverlayable;
    }

    public InForceWhenAbsent inForceWhenAbsent() {
        return inForceWhenAbsent;
    }

    public static Optional<ArtifactKind> fromToken(String token) {
        return Arrays.stream(values()).filter(k -> k.token.equals(token)).findFirst();
    }

    public static Optional<ArtifactKind> fromPlural(String plural) {
        return Arrays.stream(values()).filter(k -> k.plural.equals(plural)).findFirst();
    }
}
