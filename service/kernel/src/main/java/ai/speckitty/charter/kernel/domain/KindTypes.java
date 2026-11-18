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
import java.util.Map;
import java.util.Optional;

/** Maps each leaf kind type to its {@link NodeKind}, and back. */
final class KindTypes {

    private static final Map<Class<? extends Kind>, NodeKind> NODE_KINDS = Map.ofEntries(
            Map.entry(Directive.class, NodeKind.DIRECTIVE),
            Map.entry(Tactic.class, NodeKind.TACTIC),
            Map.entry(Styleguide.class, NodeKind.STYLEGUIDE),
            Map.entry(Toolguide.class, NodeKind.TOOLGUIDE),
            Map.entry(Paradigm.class, NodeKind.PARADIGM),
            Map.entry(Procedure.class, NodeKind.PROCEDURE),
            Map.entry(AgentProfile.class, NodeKind.AGENT_PROFILE),
            Map.entry(GlossaryContext.class, NodeKind.GLOSSARY_CONTEXT),
            Map.entry(Term.class, NodeKind.TERM),
            Map.entry(Skill.class, NodeKind.SKILL),
            Map.entry(AntiPattern.class, NodeKind.ANTI_PATTERN),
            Map.entry(Asset.class, NodeKind.ASSET),
            Map.entry(Template.class, NodeKind.TEMPLATE),
            Map.entry(Action.class, NodeKind.ACTION),
            Map.entry(MissionType.class, NodeKind.MISSION_TYPE),
            Map.entry(Guard.class, NodeKind.GUARD),
            Map.entry(Lifecycle.class, NodeKind.LIFECYCLE),
            Map.entry(StepEnhancement.class, NodeKind.STEP_ENHANCEMENT),
            Map.entry(RoutingCatalog.class, NodeKind.ROUTING_CATALOG));

    private KindTypes() {}

    /**
     * The node kind a leaf kind type stands for.
     *
     * @param leaf a leaf kind type, for example {@code Directive.class}
     * @return its node kind; empty for a group type
     */
    static Optional<NodeKind> nodeKindOf(Class<?> leaf) {
        return Optional.ofNullable(NODE_KINDS.get(leaf));
    }

    /**
     * The leaf kind type of a node kind.
     *
     * @param kind a node kind
     * @return its leaf type
     */
    static Class<? extends Kind> leafOf(NodeKind kind) {
        return NODE_KINDS.entrySet().stream()
                .filter(e -> e.getValue() == kind)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No leaf kind type for " + kind));
    }
}
