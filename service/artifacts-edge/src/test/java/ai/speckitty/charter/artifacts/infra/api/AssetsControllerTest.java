package ai.speckitty.charter.artifacts.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.artifacts.application.AssetReads;
import ai.speckitty.charter.artifacts.application.AssetView;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.web.DeferredETag;
import ai.speckitty.charter.web.RefusalHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * The asset reads' controller over a fake port: the three answers, their headers and links, the payload's bytes and
 * media type, its {@code If-None-Match}, and that Spring answers no {@code 304}, {@code 206} or {@code 406} the contract
 * does not declare. The routing and the whole service are the contract scenarios ({@code assets.feature}).
 */
class AssetsControllerTest {

    private static final String FINGERPRINT = "sha256:" + "b".repeat(64);
    private static final String REVISION = "sha256:" + "a".repeat(64);
    private static final String ETAG = "\"" + REVISION + "\"";
    private static final PackIdentifier QA = new PackIdentifier("qa");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0, (byte) 0xff, '\r', '\n'};
    private static final String PAYLOAD = "/api/v1/packs/qa/artifacts/asset/logo/payload";

    private static final AssetView RUN = new AssetView(
            "run",
            QA,
            "text/x-python",
            true,
            "run.py",
            Optional.of("Run the lint"),
            Optional.of(new AssetView.Executable("python", Optional.of("3.11"), "run.py", List.of("--guard"))));
    private static final AssetView LOGO =
            new AssetView("logo", QA, "image/png", true, "logo.png", Optional.empty(), Optional.empty());

    private final List<String> asked = new ArrayList<>();

    private MockMvc mvc(AssetReads reads) {
        JsonMapper json = JsonMapper.builder()
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        return MockMvcBuilders.standaloneSetup(new AssetsController(reads, new AssetBodies("/api/v1")))
                .setMessageConverters(new ResourceHttpMessageConverter(), new JacksonJsonHttpMessageConverter(json))
                .setControllerAdvice(new RefusalHandler(), new DeferredETag())
                .build();
    }

    private AssetReads reads(AssetView payloadAsset) {
        return new AssetReads() {
            @Override
            public Listed list(PackIdentifier pack, String expectedFingerprint) {
                throw new UnsupportedOperationException("the pack route reads against no fingerprint");
            }

            @Override
            public Read asset(PackIdentifier pack, String assetId, String expectedFingerprint) {
                throw new UnsupportedOperationException("the pack route reads against no fingerprint");
            }

            @Override
            public PayloadRead payload(PackIdentifier pack, String assetId, String expectedFingerprint) {
                throw new UnsupportedOperationException("the pack route reads against no fingerprint");
            }

            @Override
            public Listed list(PackIdentifier pack) {
                asked.add("list " + pack.value());
                return new Listed(List.of(LOGO, RUN), REVISION, FINGERPRINT);
            }

            @Override
            public Read asset(PackIdentifier pack, String assetId) {
                asked.add("asset " + pack.value() + "/" + assetId);
                return new Read(RUN, REVISION, FINGERPRINT);
            }

            @Override
            public PayloadRead payload(PackIdentifier pack, String assetId) {
                asked.add("payload " + pack.value() + "/" + assetId);
                return new PayloadRead(payloadAsset, PNG, REVISION, FINGERPRINT);
            }
        };
    }

    private ResultActions payload(MockMvc mvc, String header, String value) throws Exception {
        return mvc.perform(get(PAYLOAD).header(header, value));
    }

    @Test
    void theListAnswersEachAssetsIdMimeAndWhetherItRunsWithItsSelfLinkAndTheListsOwn() throws Exception {
        mvc(reads(LOGO))
                .perform(get("/api/v1/packs/qa/artifacts/asset").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(header().string("ETag", ETAG))
                .andExpect(content().json("""
                        {
                          "items": [
                            {"id": "logo", "mime": "image/png", "executable": false,
                             "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/asset/logo"}}},
                            {"id": "run", "mime": "text/x-python", "executable": true,
                             "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/asset/run"}}}
                          ],
                          "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/asset"}}
                        }
                        """, JsonCompareMode.STRICT));
        assertThat(asked).containsExactly("list qa");
    }

    @Test
    void theItemAnswersTheEnvelopeWithTheDescriptorAndLinksItsPayloadAndItsCollection() throws Exception {
        mvc(reads(LOGO))
                .perform(get("/api/v1/packs/qa/artifacts/asset/run").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(header().string("ETag", ETAG))
                .andExpect(content().json("""
                        {
                          "kind": "asset",
                          "id": "run",
                          "packId": "qa",
                          "_links": {
                            "self": {"href": "/api/v1/packs/qa/artifacts/asset/run"},
                            "payload": {"href": "/api/v1/packs/qa/artifacts/asset/run/payload"},
                            "collection": {"href": "/api/v1/packs/qa/artifacts/asset"}
                          },
                          "asset": {
                            "id": "run",
                            "mime": "text/x-python",
                            "path": "run.py",
                            "title": "Run the lint",
                            "executable": {"runtime": "python", "minVersion": "3.11", "entry": "run.py", "args": ["--guard"]}
                          }
                        }
                        """, JsonCompareMode.STRICT));
        assertThat(asked).containsExactly("asset qa/run");
    }

    @Test
    void theItemTakesNoIfNoneMatchSoAMatchingTagIsStillA200() throws Exception {
        mvc(reads(LOGO))
                .perform(get("/api/v1/packs/qa/artifacts/asset/run").header("If-None-Match", ETAG))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", ETAG));
    }

    @Test
    void thePayloadIsTheExactBytesWithItsMimeAndTheHeadersEvery200Carries() throws Exception {
        byte[] body = mvc(reads(LOGO))
                .perform(get(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Length", String.valueOf(PNG.length)))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"))
                .andExpect(header().string("ETag", ETAG))
                .andExpect(header().string("Charter-Source-Fingerprint", FINGERPRINT))
                .andExpect(header().doesNotExist("Accept-Ranges"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(body).isEqualTo(PNG);
        assertThat(asked).containsExactly("payload qa/logo");
    }

    @Test
    void thePayloadIgnoresAcceptSoAJsonAcceptIsAnsweredTheSameBytesAndType() throws Exception {
        byte[] body = payload(mvc(reads(LOGO)), "Accept", "application/json")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(body).isEqualTo(PNG);
    }

    @Test
    void aRangeIsNotAnsweredWithA206ThePayloadIsWhole() throws Exception {
        byte[] body = payload(mvc(reads(LOGO)), "Range", "bytes=0-1")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(body).isEqualTo(PNG);
    }

    @Test
    void aMalformedMimeIsServedAsOctetStreamWithTheSameBytes() throws Exception {
        AssetView plain = new AssetView("logo", QA, "plain", false, "logo.png", Optional.empty(), Optional.empty());

        byte[] body = mvc(reads(plain))
                .perform(get(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(body).isEqualTo(PNG);
    }

    @Test
    void aMatchingIfNoneMatchIsA304WithTheTagAndNoBody() throws Exception {
        for (String held : List.of(ETAG, "*", "W/" + ETAG, "\"other\", " + ETAG)) {
            payload(mvc(reads(LOGO)), "If-None-Match", held)
                    .andExpect(status().isNotModified())
                    .andExpect(header().string("ETag", ETAG))
                    .andExpect(header().doesNotExist("Charter-Source-Fingerprint"))
                    .andExpect(content().bytes(new byte[0]));
        }
    }

    @Test
    void anIfNoneMatchThatMatchesNothingIsA200EvenWhenItHoldsTheTagInsideAValueThatIsNotATagList() throws Exception {
        for (String held : List.of("\"other\"", "\"unclosed, " + ETAG)) {
            payload(mvc(reads(LOGO)), "If-None-Match", held)
                    .andExpect(status().isOk())
                    .andExpect(header().string("ETag", ETAG));
        }
    }

    @Test
    void aRefusalOfTheReadIsAnsweredByTheSharedHandler() throws Exception {
        AssetReads refusing = new AssetReads() {
            @Override
            public Listed list(PackIdentifier pack, String expectedFingerprint) {
                throw new UnsupportedOperationException("the pack route reads against no fingerprint");
            }

            @Override
            public Read asset(PackIdentifier pack, String assetId, String expectedFingerprint) {
                throw new UnsupportedOperationException("the pack route reads against no fingerprint");
            }

            @Override
            public PayloadRead payload(PackIdentifier pack, String assetId, String expectedFingerprint) {
                throw new UnsupportedOperationException("the pack route reads against no fingerprint");
            }

            @Override
            public Listed list(PackIdentifier pack) {
                throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack nope was not found.");
            }

            @Override
            public Read asset(PackIdentifier pack, String assetId) {
                throw new CharterRefusal(RefusalCode.ARTIFACT_NOT_FOUND, "Pack qa has no asset nothing.");
            }

            @Override
            public PayloadRead payload(PackIdentifier pack, String assetId) {
                throw new CharterRefusal(RefusalCode.ARTIFACT_NOT_FOUND, "Pack qa has no asset nothing.");
            }
        };
        MockMvc mvc = mvc(refusing);

        mvc.perform(get("/api/v1/packs/nope/artifacts/asset"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("pack_not_found"));
        mvc.perform(get("/api/v1/packs/qa/artifacts/asset/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("artifact_not_found"));
        mvc.perform(get("/api/v1/packs/qa/artifacts/asset/nothing/payload").header("If-None-Match", "*"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("artifact_not_found"));
    }
}
