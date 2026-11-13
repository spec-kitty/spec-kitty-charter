package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PresetReadService;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PresetItem;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.web.DeferredETag;
import ai.speckitty.charter.web.ProblemHandler;
import ai.speckitty.charter.web.RefusalHandler;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * The HTTP answers of {@code GET /packs/{packId}/presets} and {@code GET /packs/{packId}/presets/{presetId}} (#98): the
 * body with its links, the pack revision as the {@code ETag}, the fingerprint and the shared headers, the refusals, and
 * the wire name of every kind's list, which must be the camel case of the file's {@code activated_<plural>} key. Neither
 * read takes {@code If-None-Match}: a matching one still answers {@code 200} with the body.
 */
class PresetsControllerTest {

    private static final PackIdentifier QA = new PackIdentifier("qa");
    private static final PackRevision REVISION = PackRevision.compute(List.of("qa", "revision"));
    private static final String TAG = "\"" + REVISION.token() + "\"";
    private static final String FINGERPRINT = "sha256:" + "a".repeat(64);

    private final PresetReadService presets = mock(PresetReadService.class);
    private final MockMvc mvc = mvc(presets);

    private static MockMvc mvc(PresetReadService presets) {
        ServiceInfoQuery info =
                new ServiceInfoQuery("worktree-1", new ConsistentRead(new FileSystemPackSources(List::of)));
        return MockMvcBuilders.standaloneSetup(
                        new PresetsController(presets, new PresetMapper(new Hypermedia("/api/v1"))))
                .setMessageConverters(converters().toArray(new HttpMessageConverter<?>[0]))
                .setControllerAdvice(new RefusalHandler(), new ProblemHandler(), new DeferredETag())
                .addFilters(new CharterHeaders(info))
                .addPlaceholderValue("charter.api.base-path", "/api/v1")
                .build();
    }

    /** The server's converters, the JSON one with the service's preset module, as the application's mapper has it. */
    private static List<HttpMessageConverter<?>> converters() {
        List<HttpMessageConverter<?>> converters = new ArrayList<>(HttpConverters.serverConverters());
        converters.addFirst(new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                .addModule(new PresetJson().presetJsonModule())
                .build()));
        return converters;
    }

    private static Preset team() {
        return new Preset(
                "team",
                "What a review team puts in force.",
                Map.of(
                        ArtifactKind.TACTIC, List.of(PresetItem.of("t")),
                        ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("D"))));
    }

    private static Preset minimal() {
        return new Preset(
                "minimal", "The least a review needs.", Map.of(ArtifactKind.TACTIC, List.of(PresetItem.of("t"))));
    }

    private void holding(Preset preset) {
        when(presets.read(QA, preset.id()))
                .thenReturn(new ConsistentRead.Verified<>(new PresetReadService.Held(preset, REVISION), FINGERPRINT));
    }

    /** The camel case of a kind's file key without its {@code activated_} prefix: {@code agent_profiles} → AgentProfiles. */
    private static String wireName(ArtifactKind kind) {
        return "activated"
                + Arrays.stream(kind.plural().split("_"))
                        .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                        .collect(Collectors.joining());
    }

    @Test
    void theListAnswersThePresetsInTheOrderReadWithTheirLinksTheRevisionAndTheSharedHeaders() throws Exception {
        when(presets.list(QA))
                .thenReturn(new ConsistentRead.Verified<>(
                        new PresetReadService.Listed(List.of(minimal(), team()), REVISION), FINGERPRINT));

        mvc.perform(get("/api/v1/packs/qa/presets"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", TAG))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(header().exists("Charter-Contract-Version"))
                .andExpect(header().string("Charter-Worktree", "worktree-1"))
                .andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.contains("minimal", "team")))
                .andExpect(jsonPath("$.items[1]._links.self.href").value("/api/v1/packs/qa/presets/team"))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/packs/qa/presets"))
                .andExpect(jsonPath("$._links.pack.href").value("/api/v1/packs/qa"))
                .andExpect(jsonPath("$._links.length()").value(2));
    }

    @Test
    void onePresetAnswersItsItemsWithMustHaveTrueAndFalseItsTwoLinksAndThePackRevisionAsItsETag() throws Exception {
        holding(team());

        String body = mvc.perform(get("/api/v1/packs/qa/presets/team"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", TAG))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(header().exists("Charter-Contract-Version"))
                .andExpect(header().string("Charter-Worktree", "worktree-1"))
                .andExpect(jsonPath("$.id").value("team"))
                .andExpect(jsonPath("$.description").value("What a review team puts in force."))
                .andExpect(jsonPath("$.activatedDirectives[0].id").value("D"))
                .andExpect(jsonPath("$.activatedDirectives[0].mustHave").value(true))
                .andExpect(jsonPath("$.activatedTactics[0].id").value("t"))
                .andExpect(jsonPath("$.activatedTactics[0].mustHave").value(false))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/packs/qa/presets/team"))
                .andExpect(jsonPath("$._links.pack.href").value("/api/v1/packs/qa"))
                .andExpect(jsonPath("$._links.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> answer = JsonPath.parse(body).read("$");
        assertThat(answer.keySet())
                .as("a kind the preset lists nothing of is left out, never null, and schemaVersion is not answered")
                .containsExactlyInAnyOrder("id", "description", "activatedDirectives", "activatedTactics", "_links");
    }

    @Test
    void everyKindsListIsAnsweredUnderTheCamelCaseOfItsFileKey() throws Exception {
        Map<ArtifactKind, List<PresetItem>> everyKind = new EnumMap<>(ArtifactKind.class);
        Preset.ACTIVATION_KINDS.forEach(kind -> everyKind.put(kind, List.of(PresetItem.of(kind.token()))));
        holding(new Preset("every-kind", "One item of every kind.", everyKind));

        String body = mvc.perform(get("/api/v1/packs/qa/presets/every-kind"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> answer = JsonPath.parse(body).read("$");
        List<String> lists = answer.keySet().stream()
                .filter(key -> key.startsWith("activated"))
                .toList();
        assertThat(lists)
                .as("one list per activatable kind, named after the plural its file key names")
                .containsExactlyInAnyOrderElementsOf(Preset.ACTIVATION_KINDS.stream()
                        .map(PresetsControllerTest::wireName)
                        .toList());
        for (ArtifactKind kind : Preset.ACTIVATION_KINDS) {
            assertThat(JsonPath.parse(body).<String>read("$." + wireName(kind) + "[0].id"))
                    .as("the list %s holds the %s item", wireName(kind), kind.token())
                    .isEqualTo(kind.token());
        }
    }

    @Test
    void aMatchingIfNoneMatchStillAnswers200WithTheBodyOnBothReads() throws Exception {
        holding(team());
        when(presets.list(QA))
                .thenReturn(new ConsistentRead.Verified<>(
                        new PresetReadService.Listed(List.of(team()), REVISION), FINGERPRINT));

        mvc.perform(get("/api/v1/packs/qa/presets/team").header("If-None-Match", TAG))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", TAG))
                .andExpect(jsonPath("$.id").value("team"));
        mvc.perform(get("/api/v1/packs/qa/presets").header("If-None-Match", TAG))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", TAG))
                .andExpect(jsonPath("$.items[0].id").value("team"));
    }

    @Test
    void aPresetThePackDoesNotHoldIs404PresetNotFound() throws Exception {
        when(presets.read(QA, "ghost"))
                .thenThrow(new CharterRefusal(RefusalCode.PRESET_NOT_FOUND, "Pack qa holds no preset ghost."));

        mvc.perform(get("/api/v1/packs/qa/presets/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(header().doesNotExist("Charter-Source-Fingerprint"))
                .andExpect(jsonPath("$.code").value("preset_not_found"));
    }

    @Test
    void anUnknownPackIs404PackNotFoundOnTheList() throws Exception {
        when(presets.list(new PackIdentifier("nope")))
                .thenThrow(new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack nope was not found."));

        mvc.perform(get("/api/v1/packs/nope/presets"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("pack_not_found"));
    }
}
