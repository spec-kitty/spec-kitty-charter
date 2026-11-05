package ai.speckitty.charter.kernel.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConstrainedTest {

    private static final String NAMES_A_DIRECTIVE = "names a directive";
    private static final String IS_NUMBERED = "names a numbered directive";

    private final ModularRuleset<Urn> directivesOnly = ModularRuleset.<Urn>emptyRules()
            .must(Constraints.of(NAMES_A_DIRECTIVE, urn -> urn.kind() == NodeKind.DIRECTIVE));
    private final Constraint<Urn> numbered =
            Constraints.of(IS_NUMBERED, urn -> urn.id().startsWith("DIRECTIVE_"));

    private final Urn directive = new Urn(NodeKind.DIRECTIVE, "DIRECTIVE_041");
    private final Urn tactic = new Urn(NodeKind.TACTIC, "tdd-red-green-refactor");

    @Test
    void aValidValueIsValidAndItsActionRunsWhileAnInvalidOneIsNotAndItsActionDoesNot() {
        List<Urn> accepted = new ArrayList<>();

        assertThat(directivesOnly.constrain(tactic).isValid()).isFalse();
        directivesOnly.constrain(tactic).ifValid(accepted::add);
        assertThat(accepted).isEmpty();

        // control: the same ruleset accepts a directive and hands it to the action
        assertThat(directivesOnly.constrain(directive).isValid()).isTrue();
        directivesOnly.constrain(directive).ifValid(accepted::add);
        assertThat(accepted).containsExactly(directive);
    }

    @Test
    void theRationaleOfAnInvalidValueNamesTheBreachedConstraint() {
        assertThat(Constrained.of(tactic, directivesOnly).rationale().failures())
                .containsExactly(Reason.failed(NAMES_A_DIRECTIVE));
        // control: the same pairing over a valid value reports the constraint as held
        assertThat(Constrained.of(directive, directivesOnly).rationale().details())
                .containsExactly(Reason.passed(NAMES_A_DIRECTIVE));
    }

    @Test
    void adheresToAddsAConstraintOnTopAndLeavesTheOriginalPairingUnchanged() {
        Constrained<Urn> houseStyle = directivesOnly.constrain(new Urn(NodeKind.DIRECTIVE, "house-style"));
        Constrained<Urn> stricter = houseStyle.adheresTo(numbered);

        assertThat(stricter.isValid()).isFalse();
        assertThat(stricter.rationale().details())
                .containsExactly(Reason.passed(NAMES_A_DIRECTIVE), Reason.failed(IS_NUMBERED));
        // the pairing it came from still knows only the one constraint
        assertThat(houseStyle.isValid()).isTrue();
        assertThat(houseStyle.rationale().details()).containsExactly(Reason.passed(NAMES_A_DIRECTIVE));
        // control: a value that meets the added constraint passes both
        assertThat(directivesOnly.constrain(directive).adheresTo(numbered).isValid())
                .isTrue();
    }
}
