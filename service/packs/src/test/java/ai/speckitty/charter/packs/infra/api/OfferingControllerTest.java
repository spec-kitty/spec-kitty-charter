package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.KindTaxonomyReadService;
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.fixtures.FixtureUlids;
import ai.speckitty.charter.web.ProblemHandler;
import ai.speckitty.charter.web.ProblemJson;
import ai.speckitty.charter.web.RefusalHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The HTTP shape of the offering: the entries in the service's order, their links, the fingerprint, the refusals. */
class OfferingControllerTest {

    private static final String DIGEST = "sha256:" + "7".repeat(64);
    private static final String FINGERPRINT = "Charter-Source-Fingerprint";
    /**
     * The application's mapper as far as these answers need it: a link's absent members are left out, and so are a kind
     * row's.
     */
    private static final JsonMapper JSON = JsonMapper.builder()
            .addModule(new ProblemJson().problemJsonModule())
            .addModule(new ArtifactJson().artifactJsonModule())
            .build();

    private static MockMvc mvc(List<ConfiguredPack> packs, PackDescriptorRepository descriptors) {
        OfferingReadService reads = new OfferingReadService(
                () -> packs, descriptors, pack -> PackCompositionFacts.NONE, new ConsistentRead(() -> DIGEST));
        Hypermedia links = new Hypermedia("/api/v1");
        KindTaxonomyAnswer kinds = new KindTaxonomyAnswer(new KindTaxonomyReadService(), links, JSON);
        return MockMvcBuilders.standaloneSetup(new OfferingController(reads, kinds, links))
                .setControllerAdvice(new RefusalHandler(), new ProblemHandler(), new KindTaxonomyETag(kinds))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(JSON))
                .build();
    }

    private static PackDescriptorRepository named(String version) {
        return new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier pack) {
                return find(pack).orElseThrow();
            }

            @Override
            public Optional<PackDescriptor> find(PackIdentifier pack) {
                return Optional.of(new PackDescriptor(
                        FixtureUlids.of(pack.value()),
                        version,
                        Optional.empty(),
                        pack.value() + " pack",
                        Optional.empty(),
                        Optional.empty()));
            }

            @Override
            public void save(PackIdentifier pack, PackDescriptor descriptor) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void theOfferingAnswersEveryPackInOrderWithItsArtifactsLinkAndTheFingerprint() throws Exception {
        List<ConfiguredPack> packs = List.of(
                new ConfiguredPack(new PackIdentifier("qa"), PackLevel.ORG),
                new ConfiguredPack(new PackIdentifier("my-pack"), PackLevel.BUILT_IN));

        mvc(packs, named("2.0.0"))
                .perform(get("/api/v1/offering"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().stringValues(FINGERPRINT, DIGEST))
                .andExpect(header().doesNotExist("Link"))
                .andExpect(jsonPath("$.packs.length()").value(2))
                .andExpect(jsonPath("$.packs[0].packId").value("my-pack"))
                .andExpect(jsonPath("$.packs[0].level").value("built-in"))
                .andExpect(jsonPath("$.packs[0].name").value("my-pack pack"))
                .andExpect(jsonPath("$.packs[0].version").value("2.0.0"))
                .andExpect(jsonPath("$.packs[0]._links.artifacts.href").value("/api/v1/packs/my-pack/artifacts"))
                .andExpect(jsonPath("$.packs[0]._links.self").doesNotExist())
                .andExpect(jsonPath("$.packs[1].packId").value("qa"))
                .andExpect(jsonPath("$.packs[1].level").value("org"))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/offering"))
                .andExpect(jsonPath("$._links.self.templated").doesNotExist())
                .andExpect(jsonPath("$._links.kinds.href").value("/api/v1/offering/kinds"));
    }

    @Test
    void noPackIsAnEmptyOffering() throws Exception {
        mvc(List.of(), named("1.0.0"))
                .perform(get("/api/v1/offering"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(FINGERPRINT, DIGEST))
                .andExpect(jsonPath("$.packs.length()").value(0))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/offering"));
    }

    @Test
    void anUnreadablePackIsA500WithoutTheFingerprint() throws Exception {
        PackDescriptorRepository refusing = new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier pack) {
                return find(pack).orElseThrow();
            }

            @Override
            public Optional<PackDescriptor> find(PackIdentifier pack) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack.yaml of qa cannot be read");
            }

            @Override
            public void save(PackIdentifier pack, PackDescriptor descriptor) {
                throw new UnsupportedOperationException();
            }
        };

        mvc(List.of(new ConfiguredPack(new PackIdentifier("qa"), PackLevel.ORG)), refusing)
                .perform(get("/api/v1/offering"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("pack_unreadable"))
                .andExpect(header().doesNotExist(FINGERPRINT));
    }

    @Test
    void theKindTaxonomyIsTaggedWithTheDigestOfItsBodyAndCarriesNoFingerprint() throws Exception {
        var result = mvc(List.of(), named("1.0.0"))
                .perform(get("/api/v1/offering/kinds"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist(FINGERPRINT))
                .andExpect(jsonPath("$.kinds[0].kind").value("directive"))
                .andExpect(jsonPath("$.kinds[0]._links.artifacts.href")
                        .value("/api/v1/packs/{packId}/artifacts?kind=directive"))
                .andExpect(jsonPath("$.kinds[0]._links.artifacts.templated").value(true))
                .andExpect(jsonPath("$.relations[0].relation").value("requires"))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/offering/kinds"))
                .andExpect(jsonPath("$._links.offering.href").value("/api/v1/offering"))
                .andReturn();
        byte[] body = result.getResponse().getContentAsByteArray();
        String digest =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));

        assertThat(result.getResponse().getHeader("ETag")).isEqualTo("\"sha256:" + digest + "\"");
    }

    @Test
    void aMatchingIfNoneMatchIsA304WithTheTagAndAnotherIsA200() throws Exception {
        MockMvc mvc = mvc(List.of(), named("1.0.0"));
        String tag = mvc.perform(get("/api/v1/offering/kinds"))
                .andReturn()
                .getResponse()
                .getHeader("ETag");

        mvc.perform(get("/api/v1/offering/kinds").header("If-None-Match", "W/" + tag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", tag))
                .andExpect(content().string(""));
        mvc.perform(get("/api/v1/offering/kinds").header("If-None-Match", "*")).andExpect(status().isNotModified());
        mvc.perform(get("/api/v1/offering/kinds").header("If-None-Match", "garbage " + tag))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", tag));
        mvc.perform(get("/api/v1/offering/kinds").header("If-None-Match", "\"sha256:" + "0".repeat(64) + "\""))
                .andExpect(status().isOk());
    }

    @Test
    void theContractsExampleIsTheServedTaxonomy() throws Exception {
        // The example of getKindTaxonomy lists every kind and every relation: it is the served body, not a sample.
        Path example = Path.of(System.getProperty("charter.repositoryRoot"))
                .resolve("contracts/charter/examples/KindTaxonomy.model.yaml");
        Map<?, ?> document = (Map<?, ?>) new Load(LoadSettings.builder().build())
                .loadFromString(Files.readString(example, StandardCharsets.UTF_8));
        String served = mvc(List.of(), named("1.0.0"))
                .perform(get("/api/v1/offering/kinds"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode expected = JSON.valueToTree(document.get("value"));

        assertThat(expected).as("the example; the served body is %s", served).isEqualTo(JSON.readTree(served));
    }
}
