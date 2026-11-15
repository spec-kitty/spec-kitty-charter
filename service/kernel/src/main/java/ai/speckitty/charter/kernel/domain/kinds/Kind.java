package ai.speckitty.charter.kernel.domain.kinds;

/**
 * A node kind as a type, so a relationship's endpoint kinds are checked by the compiler (ADR-0064 decision 13).
 *
 * <p>Each {@link ai.speckitty.charter.kernel.domain.NodeKind} has exactly one final leaf class here, which is never
 * instantiated; the groups are sealed interfaces whose permitted leaves are one side of a rectangle of the endpoint
 * table. {@code ai.speckitty.charter.kernel.domain.KindTypes} maps a leaf to its {@code NodeKind}.
 */
public sealed interface Kind
        permits Directive,
                Tactic,
                Styleguide,
                Toolguide,
                Paradigm,
                Procedure,
                AgentProfile,
                MissionStepContract,
                GlossaryContext,
                Skill,
                AntiPattern,
                Asset,
                Template,
                Action,
                MissionType,
                Guard,
                Lifecycle,
                Term,
                StepEnhancement,
                Guidance,
                Advisor,
                RequiresTarget,
                SuggestsTarget,
                ScopeSource,
                ScopeTarget,
                AppliesTarget,
                VocabularyTarget,
                GraphAuthored,
                Enhanceable,
                Overridable {}
