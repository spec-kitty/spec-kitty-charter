package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.api.model.DirectiveRegistration;
import ai.speckitty.charter.api.model.ParadigmRegistration;
import ai.speckitty.charter.api.model.StyleguideRegistration;
import ai.speckitty.charter.api.model.ToolguideRegistration;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;

/**
 * The list members read off the generated registration DTOs, the explicit {@code null} found among them (#292) and the
 * {@code null} item found inside one (#309), and the bounded read of a registration body (#310). The names are pinned
 * here so a contract change that adds, renames or drops a list shows up as a failure.
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
        assertThat(firstNull("{\"nested\":{\"procedures\":null},\"other\":[null]}"))
                .as("only a top-level list member; a member nested in another, or another list, is not this check's")
                .isEmpty();
        assertThat(firstNull("{\"references\":"))
                .as("not JSON: the converter answers it")
                .isEmpty();
        assertThat(firstNull("null")).isEmpty();
        assertThat(firstNull("[{\"references\":null}]")).isEmpty();
    }

    @Test
    void aNullItemInsideAListIsFoundAndNamesTheListAndTheItem() {
        assertThat(firstNullValue("{\"references\":[null]}"))
                .contains(new RegistrationLists.NullValue("references", 0));
        assertThat(firstNullValue("{\"procedures\":[\"a\",\"b\",null,null]}"))
                .as("the first null item, counted from 0")
                .contains(new RegistrationLists.NullValue("procedures", 2));
        assertThat(firstNullValue("{\"references\":[{\"id\":\"a\",\"refs\":[null]},null]}"))
                .as("an object item is skipped whole; the null after it is the list's")
                .contains(new RegistrationLists.NullValue("references", 1));
        assertThat(firstNullValue("{\"procedures\":[null],\"references\":null}"))
                .as("the first member in the order given, whichever way it is null")
                .contains(new RegistrationLists.NullValue("references", RegistrationLists.NullValue.WHOLE));
        assertThat(firstNull("{\"references\":[{\"id\":null}],\"procedures\":[[null]]}"))
                .as("a null inside an item, or a list where an item belongs, is the converter's and the domain's")
                .isEmpty();
        assertThat(firstNull("{\"references\":[\"null\",\"\"]}"))
                .as("the string null is a value")
                .isEmpty();
    }

    @Test
    void aNullItemIsLookedForOnlyInAList() {
        // A guide field is a string: an array there is the converter's type error (400), not a null item.
        assertThat(RegistrationLists.firstNull(bytes("{\"guide\":[null]}"), null, List.of("guide"), MEMBERS))
                .isEmpty();
        assertThat(RegistrationLists.firstNull(bytes("{\"guide\":null}"), null, List.of("guide"), MEMBERS))
                .contains(new RegistrationLists.NullValue("guide", RegistrationLists.NullValue.WHOLE));
    }

    @Test
    void theLastOfARepeatedMemberCountsAsItDoesForTheConverter() {
        assertThat(firstNull("{\"references\":[],\"references\":null}")).contains("references");
        assertThat(firstNull("{\"references\":null,\"references\":[]}")).isEmpty();
        assertThat(firstNull("{\"references\":[],\"references\":[null]}")).contains("references");
        assertThat(firstNull("{\"references\":[null],\"references\":[]}")).isEmpty();
    }

    @Test
    void theDetailNamesTheListAndTheItem() {
        assertThat(new RegistrationLists.NullValue("references", RegistrationLists.NullValue.WHOLE).detail())
                .isEqualTo("The references list is null; a list is sent as an array, [] when it is empty, and an"
                        + " optional one may be left out.");
        assertThat(new RegistrationLists.NullValue("references", 3).detail())
                .isEqualTo("Item 3 of the references list is null; a list holds no null item.");
    }

    @Test
    void theBodyIsReadInTheCharsetTheRequestDeclares() {
        // A Latin-1 byte is not UTF-8: read as bytes, this body would not parse and would pass unchecked, while the
        // converter reads it in the declared charset and binds the null list as [].
        byte[] latin1 = "{\"title\":\"caf\u00e9\",\"references\":null}".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(firstNull(latin1, new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.ISO_8859_1)))
                .contains("references");
        byte[] latin1Item = "{\"title\":\"caf\u00e9\",\"references\":[null]}".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(firstNull(latin1Item, new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.ISO_8859_1)))
                .as("a null item too")
                .contains("references");

        byte[] utf16 = "{\"references\":null}".getBytes(StandardCharsets.UTF_16LE);
        assertThat(firstNull(utf16, MediaType.APPLICATION_JSON))
                .as("a charset JSON detects itself")
                .contains("references");
    }

    @Test
    void aBareUtf16DeclaredCharsetIsDetectedFromTheBytesAsTheConverterDoes() {
        // Spring's converter treats a bare "UTF-16"/"UTF-32" as self-detecting (endianness from the bytes), so a
        // BOM-less UTF-16LE body binds the null list as []. A Java InputStreamReader("UTF-16") would default to
        // big-endian and read garbage, stepping past the guard; the charset is detected from the bytes here too.
        byte[] utf16le = "{\"references\":null}".getBytes(StandardCharsets.UTF_16LE);
        assertThat(firstNull(utf16le, MediaType.parseMediaType("application/json;charset=UTF-16")))
                .contains("references");
        byte[] utf16leItem = "{\"references\":[null]}".getBytes(StandardCharsets.UTF_16LE);
        assertThat(firstNull(utf16leItem, MediaType.parseMediaType("application/json;charset=UTF-16")))
                .as("a null item too")
                .contains("references");
    }

    @Test
    void aBodyWithATokenAfterTheObjectIsTheConverters() {
        // Jackson 3 fails on trailing tokens by default, so the converter answers these 400, not a null item 422.
        assertThat(firstNull("{\"references\":[null]} x")).isEmpty();
        assertThat(firstNull("{\"references\":[null]}{}")).isEmpty();
        assertThat(firstNull("{\"references\":null}]")).isEmpty();
        assertThat(firstNull("{\"references\":[null]}  \n"))
                .as("whitespace is not a token")
                .contains("references");
    }

    @Test
    void aBodyThatEndsInsideAListIsTheConverters() {
        assertThat(firstNull("{\"references\":[null")).isEmpty();
        assertThat(firstNull("{\"references\":[\"a\",")).isEmpty();
    }

    @Test
    void theBoundedReadRefusesABodyOverTheLimitAndReadsNoMoreThanOneBytePastIt() throws IOException {
        HttpHeaders declared = new HttpHeaders();
        declared.setContentLength(9);
        assertThatThrownBy(() -> RegistrationLists.readBounded(message(new byte[0], declared), 8, "directive"))
                .as("by its Content-Length, before a byte is read")
                .isInstanceOfSatisfying(CharterRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(r.getMessage()).isEqualTo("The directive registration body is larger than 8 bytes.");
                });

        ByteArrayInputStream over = new ByteArrayInputStream(bytes("0123456789abcdef"));
        assertThatThrownBy(() -> RegistrationLists.readBounded(message(over, new HttpHeaders()), 8, "paradigm"))
                .as("without one, one byte past the limit")
                .isInstanceOf(CharterRefusal.class);
        assertThat(over.available()).as("the rest is never read").isEqualTo(16 - 9);

        assertThat(RegistrationLists.readBounded(message(bytes("01234567"), new HttpHeaders()), 8, "styleguide"))
                .as("a body at the limit is read whole")
                .isEqualTo(bytes("01234567"));
        assertThat(RegistrationLists.BODY_LIMIT).isEqualTo(1024 * 1024);
    }

    @Test
    void aDtoThatShowsNoListIsRefusedRatherThanLeftUnguarded() {
        assertThatThrownBy(() -> RegistrationLists.of(String.class)).isInstanceOf(IllegalStateException.class);
    }

    private static Optional<String> firstNull(String json) {
        return firstNull(bytes(json), null);
    }

    private static Optional<String> firstNull(byte[] body, @Nullable MediaType contentType) {
        return RegistrationLists.firstNull(body, contentType, List.of(), MEMBERS)
                .map(RegistrationLists.NullValue::member);
    }

    private static Optional<RegistrationLists.NullValue> firstNullValue(String json) {
        return RegistrationLists.firstNull(bytes(json), null, List.of(), MEMBERS);
    }

    private static HttpInputMessage message(byte[] body, HttpHeaders headers) {
        return message(new ByteArrayInputStream(body), headers);
    }

    private static HttpInputMessage message(InputStream body, HttpHeaders headers) {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return body;
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
