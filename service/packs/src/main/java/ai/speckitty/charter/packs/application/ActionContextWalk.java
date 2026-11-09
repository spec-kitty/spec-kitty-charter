package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Stub: the red test of T135 fails on its assertions. */
public final class ActionContextWalk {

    /** The five steps. */
    public enum Step {
        /** Scope. */
        SCOPE(RelationType.SCOPE),
        /** Requires. */
        REQUIRES(RelationType.REQUIRES),
        /** Suggests. */
        SUGGESTS(RelationType.SUGGESTS),
        /** Requires of suggested. */
        REQUIRES_OF_SUGGESTED(RelationType.REQUIRES),
        /** Vocabulary. */
        VOCABULARY(RelationType.VOCABULARY);

        private final RelationType relation;

        Step(RelationType relation) {
            this.relation = relation;
        }

        /**
         * The relation.
         *
         * @return the relation
         */
        public RelationType relation() {
            return relation;
        }
    }

    /** An entry. */
    public record Entry(
            Urn urn,
            Step step,
            Urn via,
            int hops,
            Optional<String> when,
            Optional<String> reason,
            List<Urn> enhancedBy,
            List<Urn> enhances) {}

    /** The answer. */
    public record Context(Urn action, boolean missionTypeInForce, List<Entry> entries) {}

    ActionContextWalk(EdgeIndex merged) {}

    Context walk(String missionType, String action, int depth, Predicate<Urn> inForce) {
        return new Context(Urn.action(missionType, action), true, List.of());
    }
}
