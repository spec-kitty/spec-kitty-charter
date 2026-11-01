package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.api.model.DirectiveRegistration;
import ai.speckitty.charter.api.model.ParadigmRegistration;
import ai.speckitty.charter.api.model.StyleguideRegistration;
import ai.speckitty.charter.api.model.ToolguideRegistration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * The list members read off the generated registration DTOs, and the explicit {@code null} found among them (#292).
 * The names are pinned here so a contract change that adds, renames or drops a list shows up as a failure.
 */
class RegistrationListsTest {

    private static final List<String> MEMBERS = List.of("references", "procedures");

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
        assertThat(firstNull("{\"procedures\":null,\"references\":null}"))
                .as("the first in the order given")
                .contains("references");
        assertThat(firstNull("{\"references\":[]}"))
                .as("an empty list is a value")
                .isEmpty();
        assertThat(firstNull("{\"title\":\"t\"}")).as("an absent list is empty").isEmpty();
        assertThat(firstNull("{\"scope\":null}"))
                .as("a null that is not a list member is not this check's")
                .isEmpty();
        assertThat(firstNull("{\"references\":[null],\"nested\":{\"procedures\":null}}"))
                .as("only a top-level member; what is inside one is the converter's and the domain's")
                .isEmpty();
        assertThat(firstNull("{\"references\":"))
                .as("not JSON: the converter answers it")
                .isEmpty();
        assertThat(firstNull("null")).isEmpty();
        assertThat(firstNull("[{\"references\":null}]")).isEmpty();
    }

    @Test
    void theLastOfARepeatedMemberCountsAsItDoesForTheConverter() {
        assertThat(firstNull("{\"references\":[],\"references\":null}")).contains("references");
        assertThat(firstNull("{\"references\":null,\"references\":[]}")).isEmpty();
    }

    @Test
    void theBodyIsReadInTheCharsetTheRequestDeclares() {
        // A Latin-1 byte is not UTF-8: read as bytes, this body would not parse and would pass unchecked, while the
        // converter reads it in the declared charset and binds the null list as [].
        byte[] latin1 = "{\"title\":\"caf\u00e9\",\"references\":null}".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(RegistrationLists.firstExplicitNull(
                        latin1, new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.ISO_8859_1), MEMBERS))
                .contains("references");

        byte[] utf16 = "{\"references\":null}".getBytes(StandardCharsets.UTF_16LE);
        assertThat(RegistrationLists.firstExplicitNull(utf16, MediaType.APPLICATION_JSON, MEMBERS))
                .as("a charset JSON detects itself")
                .contains("references");
    }

    @Test
    void aDtoThatShowsNoListIsRefusedRatherThanLeftUnguarded() {
        assertThatThrownBy(() -> RegistrationLists.of(String.class)).isInstanceOf(IllegalStateException.class);
    }

    private static java.util.Optional<String> firstNull(String json) {
        return RegistrationLists.firstExplicitNull(bytes(json), null, MEMBERS);
    }

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
