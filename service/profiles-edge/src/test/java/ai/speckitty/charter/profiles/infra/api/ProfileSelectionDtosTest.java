package ai.speckitty.charter.profiles.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.api.model.ProfileSelectionCandidate;
import ai.speckitty.charter.api.model.ProfileSelectionRead;
import ai.speckitty.charter.profiles.application.ProfileSelectionService.Selection;
import ai.speckitty.charter.profiles.application.ProfileSelectionService.Subject;
import ai.speckitty.charter.profiles.domain.selection.Fit;
import ai.speckitty.charter.profiles.domain.selection.Pick;
import ai.speckitty.charter.profiles.domain.selection.SelectionAnswer;
import ai.speckitty.charter.profiles.domain.selection.SelectionContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link ProfileSelectionDtos}: the wire form of a selection. Each pick keeps its own pack and fit, the order of the
 * picks is the selection's, a candidate links its read under the activation, {@code self} repeats exactly the context
 * the request asked, and an unasked dimension is absent from {@code context}.
 */
class ProfileSelectionDtosTest {

    private final ProfileSelectionDtos dtos = new ProfileSelectionDtos("/api/v1/");

    private static Selection selection(SelectionContext context, Pick... picks) {
        return new Selection(
                "a-1",
                new Subject.Role("reviewer"),
                context,
                new SelectionAnswer.Matched(List.of(picks), picks.length > 1));
    }

    @Test
    void aTieListsEveryPickInOrderWithItsOwnPackFitAndLink() {
        ProfileSelectionRead read = dtos.read(selection(
                SelectionContext.NONE,
                new Pick("reviewer-renata", "built-in", Fit.PRIMARY),
                new Pick("reviewer-rita", "org-base", Fit.PRIMARY)));

        assertThat(read.getRole()).isEqualTo("reviewer");
        assertThat(read.getTie()).isTrue();
        assertThat(read.getCandidates())
                .extracting(ProfileSelectionCandidate::getId, ProfileSelectionCandidate::getPack)
                .containsExactly(tuple("reviewer-renata", "built-in"), tuple("reviewer-rita", "org-base"));
        assertThat(read.getCandidates()
                        .get(1)
                        .getLinks()
                        .get("effective-artifact")
                        .getHref())
                .isEqualTo("/api/v1/activations/a-1/artifacts/agent_profile/reviewer-rita");
        assertThat(read.getLinks().get("self").getHref())
                .as("the trailing / of the base path is ignored, and nothing was asked")
                .isEqualTo("/api/v1/activations/a-1/roles/reviewer/profile-selection");
        assertThat(read.getContext().getLanguage()).isNull();
        assertThat(read.getContext().getFramework()).isNull();
    }

    @Test
    void theSelfLinkRepeatsTheAskedContextLanguageFirstAndEncoded() {
        ProfileSelectionRead read = dtos.read(selection(
                new SelectionContext(Optional.of("c#"), Optional.of("spring")),
                new Pick("project-reviewer", "project", Fit.ALT)));

        assertThat(read.getTie()).isFalse();
        assertThat(read.getContext().getLanguage()).isEqualTo("c#");
        assertThat(read.getContext().getFramework()).isEqualTo("spring");
        assertThat(read.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/activations/a-1/roles/reviewer/profile-selection?language=c%23&framework=spring");
        assertThat(dtos.read(selection(
                                new SelectionContext(Optional.empty(), Optional.of("spring")),
                                new Pick("project-reviewer", "project", Fit.ALT)))
                        .getLinks()
                        .get("self")
                        .getHref())
                .endsWith("/profile-selection?framework=spring");
    }

    @Test
    void aStepsSelectionIsSelfLinkedToTheStepAndCarriesTheStepsRoleOrNone() {
        ProfileSelectionRead review = dtos.read(new Selection(
                "a-1",
                new Subject.Step("software-dev", "review", Optional.of("reviewer")),
                new SelectionContext(Optional.of("java"), Optional.empty()),
                new SelectionAnswer.Matched(List.of(new Pick("reviewer-renata", "built-in", Fit.PRIMARY)), false)));
        ProfileSelectionRead specify = dtos.read(new Selection(
                "a-1",
                new Subject.Step("software-dev", "specify", Optional.empty()),
                SelectionContext.NONE,
                new SelectionAnswer.Matched(List.of(new Pick("generic-agent", "built-in", Fit.FALLBACK)), false)));

        assertThat(review.getRole()).isEqualTo("reviewer");
        assertThat(review.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/activations/a-1/mission-types/software-dev/actions/review/profile-selection"
                        + "?language=java");
        assertThat(specify.getRole()).as("a step that names no role").isNull();
        assertThat(specify.getLinks().get("self").getHref())
                .isEqualTo("/api/v1/activations/a-1/mission-types/software-dev/actions/specify/profile-selection");
    }

    @Test
    void everyFitHasItsWireToken() {
        assertThat(ProfileSelectionDtos.fit(Fit.PRIMARY)).isEqualTo(ProfileSelectionCandidate.FitEnum.PRIMARY);
        assertThat(ProfileSelectionDtos.fit(Fit.ALT)).isEqualTo(ProfileSelectionCandidate.FitEnum.ALT);
        assertThat(ProfileSelectionDtos.fit(Fit.FALLBACK)).isEqualTo(ProfileSelectionCandidate.FitEnum.FALLBACK);
        assertThat(ProfileSelectionCandidate.FitEnum.FALLBACK.getValue()).isEqualTo("fallback");
    }

    @Test
    void anUnaskedDimensionIsAbsentOnTheWireAndAnEmptyContextIsAnEmptyObject() {
        JsonMapper json = JsonMapper.builder()
                .addModule(new ProfileJson().profileJsonModule())
                .build();

        String none = json.writeValueAsString(
                dtos.read(selection(SelectionContext.NONE, new Pick("generic-agent", "built-in", Fit.FALLBACK))));
        String java = json.writeValueAsString(dtos.read(selection(
                new SelectionContext(Optional.of("java"), Optional.empty()),
                new Pick("java-jenny", "built-in", Fit.PRIMARY))));

        assertThat(none).contains("\"context\":{}").contains("\"fit\":\"fallback\"");
        assertThat(json.writeValueAsString(dtos.read(new Selection(
                        "a-1",
                        new Subject.Step("software-dev", "specify", Optional.empty()),
                        SelectionContext.NONE,
                        new SelectionAnswer.Matched(
                                List.of(new Pick("generic-agent", "built-in", Fit.FALLBACK)), false)))))
                .as("a step that names no role leaves the role out")
                .doesNotContain("\"role\"");
        assertThat(java).contains("\"context\":{\"language\":\"java\"}");
    }
}
