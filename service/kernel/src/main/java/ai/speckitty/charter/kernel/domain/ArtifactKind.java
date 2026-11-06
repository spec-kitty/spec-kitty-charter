package ai.speckitty.charter.kernel.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * The artifact kinds and their facts, mirroring core's {@code ArtifactKind}
 * (docs/architecture/logical-model.md, "Artifact kinds"). The conformance corpus checks the facts.
 */
public enum ArtifactKind {
    /** A directive: a binding governance rule. */
    DIRECTIVE("directive", "directives", true, true, true, InForceWhenAbsent.NONE),
    /** A tactic: a practice that carries out directives. */
    TACTIC("tactic", "tactics", true, true, true, InForceWhenAbsent.NONE),
    /** A styleguide: conventions for the shape of a work product. */
    STYLEGUIDE("styleguide", "styleguides", true, true, true, InForceWhenAbsent.NONE),
    /** A toolguide: how to use one tool. */
    TOOLGUIDE("toolguide", "toolguides", true, true, true, InForceWhenAbsent.NONE),
    /** A paradigm: a way of working, such as domain-driven design. */
    PARADIGM("paradigm", "paradigms", true, true, true, InForceWhenAbsent.NONE),
    /** A procedure: an ordered set of steps. */
    PROCEDURE("procedure", "procedures", true, true, true, InForceWhenAbsent.NONE),
    /** An agent profile: an agent's identity, specialization and governance references. */
    AGENT_PROFILE("agent_profile", "agent_profiles", true, true, true, InForceWhenAbsent.NONE),
    /** A mission step contract: the structural sub-steps of one mission step and what each delegates to. */
    MISSION_STEP_CONTRACT("mission_step_contract", "mission_step_contracts", true, true, true, InForceWhenAbsent.NONE),
    /** A glossary pack: terms contributed to the project's vocabulary; not selection-overlayable. */
    GLOSSARY_PACK("glossary_pack", "glossary_packs", true, true, false, InForceWhenAbsent.NONE),
    /** A pack skill: an agent capability a pack ships. */
    SKILL("skill", "skills", true, true, false, InForceWhenAbsent.NONE),
    /** An anti-pattern: a re-kinded node inside another kind's graph fragment, never a standalone file. */
    ANTI_PATTERN("anti_pattern", "anti_patterns", true, false, false, InForceWhenAbsent.NONE),
    /** An asset: a blob described by a sidecar manifest; org-requirable but never activated. */
    ASSET("asset", "assets", false, true, false, InForceWhenAbsent.NONE),
    /** A template: mission-scoped content that resolves specially; never activated or required. */
    TEMPLATE("template", "templates", false, false, false, InForceWhenAbsent.NONE),
    /**
     * A mission type: one file holding the type's steps, their prompts and expected artifacts (ADR-0044). It is
     * activatable because a preset carries an {@code activated_mission_types} list (ADR-0042), kept in the preset's own
     * slot rather than among its activated kinds; it is neither org-requirable nor selection-overlayable. Core has no
     * such artifact kind (a recorded divergence).
     */
    MISSION_TYPE("mission_type", "mission_types", true, false, false, InForceWhenAbsent.NONE),
    /**
     * A guard: an enforced check a transition names, {@code implemented_by} an executable asset or a closed built-in
     * (ADR-0054). It is in force with its pack, as an event registry is (ADR-0053 decision 26): never activated,
     * required or selected; a transition that is in force puts its guards in force.
     */
    GUARD("guard", "guards", false, false, false, InForceWhenAbsent.NONE),
    /**
     * A lifecycle: a declared, reusable state machine a mission step runs as a submachine (ADR-0050 decision 17). It is
     * in force with its pack, like a guard: never activated, required or selected.
     */
    LIFECYCLE("lifecycle", "lifecycles", false, false, false, InForceWhenAbsent.NONE);

    /**
     * What is in force while a kind's activation key is absent. The service rules a single meaning, absent means none
     * (ADR-0053 decision 10), for every kind, {@code skill} included; the required floor is added by the packs, not by
     * this default. The enum keeps its one value so that the fact stays named beside the kind's others. Core keeps a
     * per-kind default (every artifact, or only the required skills): a recorded divergence.
     */
    public enum InForceWhenAbsent {
        /** Nothing of the kind is in force beyond the floor the packs require. */
        NONE
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

    /**
     * The canonical spelling used in URNs.
     *
     * @return the singular, snake_case token, for example {@code agent_profile}
     */
    public String token() {
        return token;
    }

    /**
     * The plural spelling, as in {@code activated_<plural>} and {@code required_<plural>} keys.
     *
     * @return the plural, snake_case token, for example {@code agent_profiles}
     */
    public String plural() {
        return plural;
    }

    /**
     * Whether the kind can appear in an {@code activated_<plural>} list.
     *
     * @return {@code false} only for kinds that resolve specially ({@code asset}, {@code template})
     */
    public boolean activatable() {
        return activatable;
    }

    /**
     * Whether an org pack may declare a {@code required_<plural>} list for the kind.
     *
     * @return {@code true} when the kind is org-requirable
     */
    public boolean orgRequirable() {
        return orgRequirable;
    }

    /**
     * Whether an org {@code required_<plural>} list also unions into the charter's {@code selected_<plural>} field.
     *
     * @return {@code true} when the kind is selection-overlayable
     */
    public boolean selectionOverlayable() {
        return selectionOverlayable;
    }

    /**
     * What is in force while the kind's activation key is absent.
     *
     * @return {@link InForceWhenAbsent#NONE} for every kind (ADR-0053 decision 10)
     */
    public InForceWhenAbsent inForceWhenAbsent() {
        return inForceWhenAbsent;
    }

    /**
     * Looks a kind up by its singular token.
     *
     * @param token a singular token, for example {@code directive}
     * @return the kind; empty when no kind has that token
     */
    public static Optional<ArtifactKind> fromToken(String token) {
        return Arrays.stream(values()).filter(k -> k.token.equals(token)).findFirst();
    }

    /**
     * Looks a kind up by its plural token.
     *
     * @param plural a plural token, for example {@code directives}
     * @return the kind; empty when no kind has that plural
     */
    public static Optional<ArtifactKind> fromPlural(String plural) {
        return Arrays.stream(values()).filter(k -> k.plural.equals(plural)).findFirst();
    }
}
