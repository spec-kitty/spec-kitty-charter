package ai.speckitty.charter.packs.infra.api;

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
import ai.speckitty.charter.packs.application.OfferingReadService;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The HTTP shape of the offering: the entries in the service's order, their links, the fingerprint, the refusals. */
class OfferingControllerTest {

    private static final String DIGEST = "sha256:" + "7".repeat(64);
    private static final String FINGERPRINT = "Charter-Source-Fingerprint";

    private static MockMvc mvc(List<ConfiguredPack> packs, PackDescriptorRepository descriptors) {
        OfferingReadService reads = new OfferingReadService(() -> packs, descriptors, new ConsistentRead(() -> DIGEST));
        return MockMvcBuilders.standaloneSetup(new OfferingController(reads, new Hypermedia("/api/v1")))
                .setControllerAdvice(new RefusalHandler(), new ProblemHandler())
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
                        pack.value(),
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
                new ConfiguredPack(new PackIdentifier("my pack"), PackLevel.BUILT_IN));

        mvc(packs, named("2.0.0"))
                .perform(get("/api/v1/offering"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().stringValues(FINGERPRINT, DIGEST))
                .andExpect(header().doesNotExist("Link"))
                .andExpect(jsonPath("$.packs.length()").value(2))
                .andExpect(jsonPath("$.packs[0].packId").value("my pack"))
                .andExpect(jsonPath("$.packs[0].level").value("built-in"))
                .andExpect(jsonPath("$.packs[0].name").value("my pack pack"))
                .andExpect(jsonPath("$.packs[0].version").value("2.0.0"))
                .andExpect(jsonPath("$.packs[0]._links.artifacts.href").value("/api/v1/packs/my%20pack/artifacts"))
                .andExpect(jsonPath("$.packs[0]._links.self").doesNotExist())
                .andExpect(jsonPath("$.packs[1].packId").value("qa"))
                .andExpect(jsonPath("$.packs[1].level").value("org"))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/offering"))
                .andExpect(jsonPath("$._links.self.templated").doesNotExist());
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
}
