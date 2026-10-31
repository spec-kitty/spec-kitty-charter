package ai.speckitty.charter.packs.infra.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.ServiceInfoQuery;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The API root's links, and the service info's links and Link header; the root carries no fingerprint. */
class InfoControllerTest {

    private static final String DIGEST = "sha256:" + "7".repeat(64);
    private static final String FINGERPRINT = "Charter-Source-Fingerprint";

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new InfoController(
                    new ServiceInfoQuery("wt", new ConsistentRead(() -> DIGEST)),
                    new ContractDocument("openapi: 3.1.0\n".getBytes(StandardCharsets.UTF_8)),
                    new Hypermedia("/api/v1")))
            .build();

    @Test
    void theRootLinksEveryTopLevelResourceAndCarriesNoFingerprint() throws Exception {
        mvc.perform(get("/api/v1/"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(FINGERPRINT))
                .andExpect(header().doesNotExist("Link"))
                .andExpect(jsonPath("$._links.length()").value(5))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/"))
                .andExpect(jsonPath("$._links.info.href").value("/api/v1/info"))
                .andExpect(jsonPath("$._links.offering.href").value("/api/v1/offering"))
                .andExpect(jsonPath("$._links.artifacts.href").value("/api/v1/packs/{packId}/artifacts{?kind}"))
                .andExpect(jsonPath("$._links.artifacts.templated").value(true))
                .andExpect(jsonPath("$._links.service-desc.href").value("/api/v1/openapi.yaml"))
                .andExpect(jsonPath("$._links.self.templated").doesNotExist());
    }

    @Test
    void theRootLinksFollowTheConfiguredBasePath() throws Exception {
        MockMvcBuilders.standaloneSetup(new InfoController(
                        new ServiceInfoQuery("wt", new ConsistentRead(() -> DIGEST)),
                        new ContractDocument("openapi: 3.1.0\n".getBytes(StandardCharsets.UTF_8)),
                        new Hypermedia("/charter")))
                .build()
                .perform(get("/api/v1/"))
                .andExpect(jsonPath("$._links.self.href").value("/charter/"))
                .andExpect(jsonPath("$._links.artifacts.href").value("/charter/packs/{packId}/artifacts{?kind}"));
    }

    @Test
    void theInfoLinksItselfAndTheContractAndSaysSoInALinkHeader() throws Exception {
        mvc.perform(get("/api/v1/info"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(FINGERPRINT, DIGEST))
                .andExpect(header().stringValues("Link", "</api/v1/openapi.yaml>; rel=\"service-desc\""))
                .andExpect(jsonPath("$.worktree").value("wt"))
                .andExpect(jsonPath("$.sourceFingerprint").value(DIGEST))
                .andExpect(jsonPath("$._links.length()").value(2))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/info"))
                .andExpect(jsonPath("$._links.service-desc.href").value("/api/v1/openapi.yaml"));
    }

    @Test
    void theContractDocumentCarriesNoLinkHeader() throws Exception {
        mvc.perform(get("/api/v1/openapi.yaml"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Link"))
                .andExpect(header().doesNotExist(FINGERPRINT));
    }
}
