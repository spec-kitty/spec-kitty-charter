package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackWriteStore;
import ai.speckitty.charter.packs.application.PackWriteStore.Change;
import ai.speckitty.charter.packs.application.PermissiveWritePolicyGate;
import ai.speckitty.charter.packs.application.RelationshipRegistrationService;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The HTTP contract of the relationship route: idempotent upsert, ETag, the {@code RelationshipRegistered} answer (the
 * edge and the link to the pack's artifacts, no {@code self}), and the 412/428/409 refusals, and the body bound (#323).
 */
class RelationshipControllerTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    private final FakeStore store = new FakeStore();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RelationshipController(
                    new RelationshipRegistrationService(store, store, new PermissiveWritePolicyGate()),
                    new Hypermedia("/api/v1")))
            .setControllerAdvice(new RefusalHandler(), new RegistrationBodyAdvice())
            .build();

    private static final String BODY = "{\"from\":\"directive:DIR_A\",\"to\":\"procedure:p\",\"type\":\"requires\"}";

    @Test
    void registersANewRelationshipWithAFreshPreconditionAndAnswers201WithAnEtag() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/relationships")
                        .header("If-Match", store.currentRevision(PACK).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().exists("ETag"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        assertThat(store.edges).hasSize(1);
    }

    @Test
    void answersTheRegisteredEdgeAndTheLinkToThePacksArtifactsWithoutSelf() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/relationships")
                        .header("If-Match", store.currentRevision(PACK).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.relationship.from").value("directive:DIR_A"))
                .andExpect(jsonPath("$.relationship.to").value("procedure:p"))
                .andExpect(jsonPath("$.relationship.type").value("requires"))
                .andExpect(jsonPath("$._links.artifacts.href").value("/api/v1/packs/pack-a/artifacts"))
                .andExpect(jsonPath("$._links.self").doesNotExist());
    }

    /** A body over 1 MiB is refused before it is read whole, and nothing is registered (#323). */
    @Test
    void aBodyOverTheLimitIsAValidationFailedRefusalAndRegistersNothing() throws Exception {
        byte[] body = new byte[1024 * 1024 + 1];
        java.util.Arrays.fill(body, (byte) ' ');
        byte[] json = BODY.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(json, 0, body, 0, json.length);

        mvc.perform(put("/api/v1/packs/pack-a/relationships")
                        .header("If-Match", store.currentRevision(PACK).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(
                        jsonPath("$.detail").value("The relationship registration body is larger than 1048576 bytes."));
        assertThat(store.edges).isEmpty();
    }

    @Test
    void isIdempotentOnThePair() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/relationships")
                        .header("If-Match", store.currentRevision(PACK).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/v1/packs/pack-a/relationships")
                        .header("If-Match", store.currentRevision(PACK).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
        assertThat(store.edges)
                .as("re-registering the same pair keeps one edge")
                .hasSize(1);
    }

    @Test
    void aMissingPreconditionIsA428PlainProblem() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/relationships")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().is(428))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void aStalePreconditionIsA412PlainProblem() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/relationships")
                        .header("If-Match", "sha256:" + "0".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void aFromThatDoesNotResolveIsA409() throws Exception {
        String unresolvable = "{\"from\":\"directive:GHOST\",\"to\":\"procedure:p\",\"type\":\"requires\"}";
        mvc.perform(put("/api/v1/packs/pack-a/relationships")
                        .header("If-Match", store.currentRevision(PACK).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unresolvable))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private static final class FakeStore implements PackRepository, PackWriteStore {
        private final List<Relationship> edges = new ArrayList<>();
        private int commits;
        private final Pack pack = Pack.builder()
                .id(PACK)
                .descriptor(new PackDescriptor(
                        "pack-a", "1.0.0", Optional.empty(), "pack-a", Optional.empty(), Optional.empty()))
                .directive(Directive.builder()
                        .id(new DirectiveIdentifier("DIR_A"))
                        .packId(PACK)
                        .schemaVersion("1.0")
                        .title("Dir A")
                        .intent("a")
                        .enforcement(Enforcement.REQUIRED)
                        .build())
                .build();

        @Override
        public Pack load(PackIdentifier id) {
            if (!PACK.equals(id)) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + id.value());
            }
            return pack;
        }

        @Override
        public PackRevision currentRevision(PackIdentifier id) {
            return PackRevision.compute(List.of(id.value() + ":" + commits));
        }

        @Override
        public List<Relationship> registeredRelationships(PackIdentifier id) {
            return List.copyOf(edges);
        }

        @Override
        public <T> T exclusively(PackIdentifier id, Supplier<T> work) {
            return work.get();
        }

        @Override
        public PackRevision commit(PackIdentifier id, String ifMatch, Change change) {
            change.relationships().ifPresent(persisted -> {
                edges.clear();
                edges.addAll(persisted);
            });
            commits++;
            return currentRevision(id);
        }
    }
}
