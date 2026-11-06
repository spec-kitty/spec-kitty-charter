package ai.speckitty.charter.activations.domain;

import static ai.speckitty.charter.activations.domain.Reaches.reach;
import static ai.speckitty.charter.activations.domain.Reaches.ref;
import static ai.speckitty.charter.activations.domain.Reaches.refs;
import static ai.speckitty.charter.kernel.domain.ArtifactKind.TACTIC;
import static ai.speckitty.charter.kernel.domain.RelationType.INSTANTIATES;
import static ai.speckitty.charter.kernel.domain.RelationType.REQUIRES;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Who the reports name (ArtifactReference.yaml, ADR-0059 D2.4): {@code SkippedDeactivated.requiredBy} follows each way
 * back to the nearest predecessor of a kind an {@code ArtifactReference} can name, and {@code SharedSkip.referencedBy}
 * to the nearest artifact that is active after the write. A template, an asset, a lineage ancestor or an unselected
 * intermediate is never named: a lineage pass-through leads back to its descendant. Among the nearest, the first in
 * code-point order is named.
 */
class CascadeRulesReportingTest {

    private static final ArtifactRef D = ref("directive:D");
    private static final ArtifactRef P = ref("agent_profile:P");
    private static final ArtifactRef X = ref("tactic:X");
    private static final ArtifactRef T2 = ref("tactic:T2");

    private static final ActivationDocument X_DENIED = ActivationDocument.builder()
            .deactivated(TACTIC, List.of("X"))
            .build()
            .document();

    private static List<SkippedReport.SkippedDeactivated> denied(ArtifactRef target, Reach reach) {
        return CascadeRules.applySelect(X_DENIED, target, CascadeScope.all(), false, reach)
                .skipped()
                .skippedDeactivated();
    }

    private static ActivationDocument selecting(String... urns) {
        ActivationDocument document = ActivationDocument.builder().build().document();
        for (ArtifactRef ref : refs(urns)) {
            document = document.withSelected(ref.kind(), ref.id());
        }
        return document;
    }

    private static List<SkippedReport.SharedSkip> shared(ActivationDocument document, Reach others) {
        Reach own = reach().start("directive:D")
                .reached("tactic:T2", "directive:D", REQUIRES)
                .build();
        return CascadeRules.applyDeactivate(document, D, CascadeScope.all(), own, others, Set.of())
                .skipped()
                .skippedShared();
    }

    @Test
    void aStopReachedFromATemplateIsRequiredByWhatInstantiatesIt() {
        Reach reach = reach().start("directive:D")
                .reached("template:tpl", "directive:D", INSTANTIATES)
                .stopped("tactic:X", "template:tpl", REQUIRES)
                .build();

        assertThat(denied(D, reach)).containsExactly(new SkippedReport.SkippedDeactivated(X, true, List.of(D)));
    }

    @Test
    void aStopReachedThroughALineageAncestorIsRequiredByTheDescendant() {
        Reach reach = reach().start("agent_profile:P")
                .ancestor("agent_profile:Q", "agent_profile:P")
                .stopped("tactic:X", "agent_profile:Q", REQUIRES)
                .build();

        assertThat(denied(P, reach)).containsExactly(new SkippedReport.SkippedDeactivated(X, true, List.of(P)));
    }

    @Test
    void aStopReachedThroughAnAssetChainIsRequiredByTheNearestNamableArtifactsSortedAndOnce() {
        Reach reach = reach().start("directive:D")
                .reached("directive:B", "directive:D", REQUIRES)
                .reached("asset:a", "directive:B", REQUIRES)
                .reached("template:tpl", "directive:D", INSTANTIATES)
                .stopped("tactic:X", "asset:a", REQUIRES)
                .stopped("tactic:X", "template:tpl", REQUIRES)
                .stopped("tactic:X", "directive:D", REQUIRES)
                .build();

        assertThat(denied(D, reach))
                .containsExactly(new SkippedReport.SkippedDeactivated(X, true, List.of(ref("directive:B"), D)));
    }

    @Test
    void aCandidateKeptThroughATemplateIsReferencedByWhatInstantiatesIt() {
        Reach others = reach().start("directive:D2")
                .reached("template:tpl", "directive:D2", INSTANTIATES)
                .reached("tactic:T2", "template:tpl", REQUIRES)
                .build();

        assertThat(shared(selecting("directive:D", "directive:D2", "tactic:T2"), others))
                .containsExactly(new SkippedReport.SharedSkip(T2, ref("directive:D2")));
    }

    @Test
    void aCandidateKeptThroughALineageAncestorIsReferencedByTheDescendant() {
        Reach others = reach().start("agent_profile:P")
                .ancestor("agent_profile:A", "agent_profile:P")
                .reached("tactic:T2", "agent_profile:A", REQUIRES)
                .build();

        assertThat(shared(selecting("directive:D", "agent_profile:P", "tactic:T2"), others))
                .containsExactly(new SkippedReport.SharedSkip(T2, P));
    }

    @Test
    void aCandidateKeptThroughAnUnselectedIntermediateIsReferencedByTheActiveArtifactBehindIt() {
        Reach others = reach().start("directive:D2")
                .reached("directive:I", "directive:D2", REQUIRES)
                .reached("tactic:T2", "directive:I", REQUIRES)
                .build();

        assertThat(shared(selecting("directive:D", "directive:D2", "tactic:T2"), others))
                .containsExactly(new SkippedReport.SharedSkip(T2, ref("directive:D2")));
    }

    @Test
    void amongTheNearestActiveArtifactsTheFirstInCodePointOrderIsNamed() {
        Reach others = reach().start("directive:Z")
                .start("directive:B")
                .reached("template:tz", "directive:Z", INSTANTIATES)
                .reached("template:tb", "directive:B", INSTANTIATES)
                .reached("tactic:T2", "template:tz", REQUIRES)
                .reached("tactic:T2", "template:tb", REQUIRES)
                .build();

        assertThat(shared(selecting("directive:D", "directive:B", "directive:Z", "tactic:T2"), others))
                .containsExactly(new SkippedReport.SharedSkip(T2, ref("directive:B")));
    }
}
