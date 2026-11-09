package ai.speckitty.charter.kernel.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * The artifact kinds and their facts, mirroring core's {@code ArtifactKind}
 * (docs/architecture/logical-model.md, "Artifact kinds"). It is the service's one kind registry (IC-20): which kinds an
 * activation holds, the plural of every {@code activated_<plural>} and JSON key, and the parse of a kind token. The
 * conformance corpus checks the facts.
 */
public enum ArtifactKind {
    /** A directive: a binding governance rule. */
    DIRECTIVE("directive", "directives", true),
    /** A tactic: a practice that carries out directives. */
    TACTIC("tactic", "tactics", true),
    /** A styleguide: conventions for the shape of a work product. */
    STYLEGUIDE("styleguide", "styleguides", true),
    /** A toolguide: how to use one tool. */
    TOOLGUIDE("toolguide", "toolguides", true),
    /** A paradigm: a way of working, such as domain-driven design. */
    PARADIGM("paradigm", "paradigms", true),
    /** A procedure: an ordered set of steps. */
    PROCEDURE("procedure", "procedures", true),
    /** An agent profile: an agent's identity, specialization and governance references. */
    AGENT_PROFILE("agent_profile", "agent_profiles", true),
    /** A mission step contract: the structural sub-steps of one mission step and what each delegates to. */
    MISSION_STEP_CONTRACT("mission_step_contract", "mission_step_contracts", true),
    /** A glossary pack: terms contributed to the project's vocabulary. */
    GLOSSARY_PACK("glossary_pack", "glossary_packs", true),
    /** A pack skill: an agent capability a pack ships. */
    SKILL("skill", "skills", true),
    /** An anti-pattern: a re-kinded node inside another kind's graph fragment, never a standalone file. */
    ANTI_PATTERN("anti_pattern", "anti_patterns", true),
    /** An asset: a blob described by a sidecar manifest; never activated. */
    ASSET("asset", "assets", false),
    /** A template: mission-scoped content that resolves specially; never activated or required. */
    TEMPLATE("template", "templates", false),
    /**
     * A mission type: one file holding the type's steps, their prompts and expected artifacts (ADR-0044). It is an
     * activatable kind like any other (one kind model, IC-20): an activation selects it under
     * {@code selected.mission_type} and denies it under {@code deactivated.mission_type}, a preset names it among its
     * items, and a governing preset may require it, as {@code RequiredFloor} does for every kind. Core has no such
     * artifact kind (a recorded divergence).
     */
    MISSION_TYPE("mission_type", "mission_types", true),
    /**
     * A guard: an enforced check a transition names, {@code implemented_by} an executable asset or a closed built-in
     * (ADR-0054). It is in force with its pack, as an event registry is (ADR-0053 decision 26): never activated,
     * required or selected; a transition that is in force puts its guards in force.
     */
    GUARD("guard", "guards", false),
    /**
     * A lifecycle: a declared, reusable state machine a mission step runs as a submachine (ADR-0050 decision 17). It is
     * in force with its pack, like a guard: never activated, required or selected.
     */
    LIFECYCLE("lifecycle", "lifecycles", false),
    /**
     * A step enhancement: add-only guards, emits and prompt fragments joined to a step by an enhances edge
     * (ADR-0053 decision 22, ADR-0066). It is activated explicitly or by a preset's activated_step_enhancements list.
     * The step-enhancement schema holds only prompt fragments so far; the pack store holds the kind and its fragment
     * companions (#428), and the guard and emit additions wait for #434.
     */
    STEP_ENHANCEMENT("step_enhancement", "step_enhancements", true);

    private final String token;
    private final String plural;
    private final boolean activatable;

    ArtifactKind(String token, String plural, boolean activatable) {
        this.token = token;
        this.plural = plural;
        this.activatable = activatable;
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
     * Whether the kind is one an activation document holds: selected, denied, named by a preset item or required by a
     * governing preset. In declaration order these are the contract's {@code ActivatableKind} values, mission type
     * included; a parity test pins the two together.
     *
     * @return {@code false} only for kinds that resolve specially or are in force with their pack ({@code asset},
     *     {@code template}, {@code guard}, {@code lifecycle})
     */
    public boolean activatable() {
        return activatable;
    }

    /**
     * Looks a kind up by its singular token; the one parse of a kind token, for the API layer and preset reading alike.
     * The match is exact: a token that differs only in case, or a plural, is not a kind.
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
