package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.DirectiveRegistration;
import ai.speckitty.charter.api.model.ParadigmRegistration;
import ai.speckitty.charter.api.model.StyleguideRegistration;
import ai.speckitty.charter.api.model.ToolguideRegistration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The list members read off the generated registration DTOs, and the explicit {@code null} found among them (#292).
 * The names are pinned here so a contract change that adds, renames or drops a list shows up as a failure.
 */
class RegistrationListsTest {

    @Test
    void theListMembersOfEachRegistrationAreReadOffItsDto() {
        assertThat(RegistrationLists.of(DirectiveRegistration.class))
                .containsExactly(
                        "explicitAllowances",
                        "integrityRules",
                        "procedures",
                        "references",
                        "relationships",
                        "validationCriteria");
        assertThat(RegistrationLists.of(ParadigmRegistration.class))
                .containsExactly("directiveRefs", "references", "relationships");
        assertThat(RegistrationLists.of(StyleguideRegistration.class))
                .containsExactly(
                        "antiPatterns", "appliesToLanguages", "patterns", "principles", "references", "tooling");
        assertThat(RegistrationLists.of(ToolguideRegistration.class))
                .containsExactly("appliesToLanguages", "commands", "references");
    }

    @Test
    void onlyAnExplicitNullAmongTheMembersIsFound() {
        List<String> members = List.of("references", "procedures");

        assertThat(RegistrationLists.firstExplicitNull(bytes("{\"procedures\":null,\"references\":null}"), members))
                .as("the first in the order given")
                .contains("references");
        assertThat(RegistrationLists.firstExplicitNull(bytes("{\"references\":[]}"), members))
                .as("an empty list is a value")
                .isEmpty();
        assertThat(RegistrationLists.firstExplicitNull(bytes("{\"title\":\"t\"}"), members))
                .as("an absent list is empty")
                .isEmpty();
        assertThat(RegistrationLists.firstExplicitNull(bytes("{\"scope\":null}"), members))
                .as("a null that is not a list member is not this check's")
                .isEmpty();
        assertThat(RegistrationLists.firstExplicitNull(bytes("{\"references\":[null]}"), members))
                .as("only the top-level member; its items are the converter's and the domain's")
                .isEmpty();
        assertThat(RegistrationLists.firstExplicitNull(bytes("{\"references\":"), members))
                .as("not JSON: the converter answers it")
                .isEmpty();
        assertThat(RegistrationLists.firstExplicitNull(bytes("null"), members)).isEmpty();
    }

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
