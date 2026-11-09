package ai.speckitty.charter.activations.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.application.ActivationWrites;
import ai.speckitty.charter.activations.domain.ActivationChange;
import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.SkippedReport;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.api.model.ArtifactReference;
import ai.speckitty.charter.api.model.ExclusionRequest;
import ai.speckitty.charter.api.model.PresetApplication;
import ai.speckitty.charter.api.model.SelectionRequest;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.RefusalViolation;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;

/**
 * The inbound half of the cascade {@code depth} (IC-21, FR-043): the body guard judges the {@code depth} of a select and
 * of a deactivate, and the answer's {@code skippedByDepth} is written only when the report has one.
 */
class ActivationDepthGlueTest {

    private static final ArtifactRef HA = new ArtifactRef(ArtifactKind.DIRECTIVE, "HA");
    private static final ArtifactRef HB = new ArtifactRef(ArtifactKind.DIRECTIVE, "HB");

    private final ActivationChangeMapper mapper = new ActivationChangeMapper(new ActivationMapper("/api/v1"));

    private static List<RefusalViolation> guard(Class<?> target, String body) {
        return ActivationBodyAdvice.violations(
                body.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON, target);
    }

    @ParameterizedTest
    @CsvSource({
        "SelectionRequest, -1, below_minimum",
        "ExclusionRequest, -1, below_minimum",
        "SelectionRequest, -99999999999, below_minimum",
        "SelectionRequest, 2147483648, above_maximum",
        "ExclusionRequest, 99999999999, above_maximum",
    })
    void aWholeDepthOutsideTheInt32RangeIsRefusedAtDepth(String body, String depth, String code) {
        Class<?> target = body.equals("SelectionRequest") ? SelectionRequest.class : ExclusionRequest.class;

        assertThat(guard(target, "{\"cascade\": [\"all\"], \"depth\": " + depth + "}"))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(Tuple.tuple("/depth", code));
    }

    @ParameterizedTest
    @CsvSource({"0", "1", "2147483647"})
    void aDepthInRangeIsNotAFaultOfTheBody(String depth) {
        assertThat(guard(SelectionRequest.class, "{\"depth\": " + depth + "}")).isEmpty();
        assertThat(guard(ExclusionRequest.class, "{\"cascade\": [\"all\"], \"depth\": " + depth + "}"))
                .isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"\"1\"", "1.5", "1.0", "true", "[]"})
    void aDepthThatIsNoWholeNumberIsUnreadableAtDepth(String depth) {
        assertThat(guard(SelectionRequest.class, "{\"depth\": " + depth + "}"))
                .extracting(RefusalViolation::pointer, RefusalViolation::code)
                .containsExactly(Tuple.tuple("/depth", "unreadable_body"));
    }

    @Test
    void aDepthIsJudgedOnlyOnTheBodiesThatDeclareIt() {
        assertThat(guard(PresetApplication.class, "{\"depth\": -1}"))
                .extracting(RefusalViolation::code)
                .containsExactly("unknown_member");
    }

    @Test
    void theFrontierPastTheBoundIsMappedOnlyWhenTheReportHasOne() {
        ActivationDocument after = ActivationDocument.builder().build().document();
        StoredActivation stored = StoredActivation.of(new ActivationId("a-1"), after);
        SkippedReport bounded = SkippedReport.empty().withSkippedByDepth(List.of(HB, HA));

        var with = mapper.toChange(
                new ActivationWrites.Outcome(stored, new ActivationChange(after, List.of(), List.of(), bounded)));
        var without = mapper.toChange(new ActivationWrites.Outcome(
                stored, new ActivationChange(after, List.of(), List.of(), SkippedReport.empty())));

        assertThat(with.getSkipped().getSkippedByDepth())
                .containsExactly(
                        new ArtifactReference(ArtifactReference.KindEnum.DIRECTIVE, "HA"),
                        new ArtifactReference(ArtifactReference.KindEnum.DIRECTIVE, "HB"));
        assertThat(without.getSkipped().getSkippedByDepth()).isNull();
    }
}
