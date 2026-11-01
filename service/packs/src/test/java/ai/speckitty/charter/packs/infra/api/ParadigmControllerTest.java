package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import ai.speckitty.charter.packs.application.ParadigmRegistrationService;
import ai.speckitty.charter.packs.application.PermissiveWritePolicyGate;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.ParadigmRepository;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The HTTP contract of the paradigm registrations: status, headers and the envelope they answer. The reads are
 * {@code ArtifactControllerTest}'s; the rules are the services' own tests.
 */
class ParadigmControllerTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    private final FakeStore store = new FakeStore();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ParadigmController(
                    new ParadigmRegistrationService(store, store, new PermissiveWritePolicyGate()),
                    new Hypermedia("/api/v1")))
            .setControllerAdvice(new RefusalHandler(), new RegistrationBodyAdvice())
            .build();

    ParadigmControllerTest() {
        store.put(paradigm("clean-architecture", "Clean Architecture", "ALPHA"));
    }

    @Test
    void registersANewParadigmWithLocationAndEtag() throws Exception {
        mvc.perform(post("/api/v1/packs/pack-a/artifacts/paradigm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("hexagonal")))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                                "Location", "http://localhost/api/v1/packs/pack-a/artifacts/paradigm/hexagonal"))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.kind").value("paradigm"))
                .andExpect(jsonPath("$.id").value("hexagonal"))
                .andExpect(jsonPath("$.packId").value("pack-a"))
                .andExpect(jsonPath("$.paradigm.id").value("hexagonal"))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/packs/pack-a/artifacts/paradigm/hexagonal"))
                .andExpect(jsonPath("$._links.collection.href").value("/api/v1/packs/pack-a/artifacts?kind=paradigm"));
    }

    @Test
    void aDuplicateIdPostIsA409() throws Exception {
        mvc.perform(post("/api/v1/packs/pack-a/artifacts/paradigm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("clean-architecture")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("registration_conflict"));
    }

    @Test
    void aPutReplacesAndAnswers200() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/artifacts/paradigm/clean-architecture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("clean-architecture")))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"));
    }

    @Test
    void aStaleIfMatchOnAPutIsA412PlainProblem() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/artifacts/paradigm/clean-architecture")
                        .header("If-Match", "sha256:" + "0".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("clean-architecture")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void aBodyRelationshipOfAnUnknownKindIsA422() throws Exception {
        String bad = "{\"id\":\"hexagonal\",\"schemaVersion\":\"1.0\",\"name\":\"Hexagonal\",\"summary\":\"ports\","
                + "\"relationships\":[{\"from\":\"paradigm:hexagonal\",\"to\":\"nonsense:x\",\"type\":\"requires\"}]}";
        mvc.perform(post("/api/v1/packs/pack-a/artifacts/paradigm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    // ---- #292: an explicit null list is refused; an absent one is empty ------------------------------------------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"directiveRefs", "references", "relationships"})
    void anExplicitNullListIsA422OnPostAndPut(String list) throws Exception {
        String bad = body("hexagonal").replace("}", ",\"" + list + "\":null}");
        mvc.perform(post("/api/v1/packs/pack-a/artifacts/paradigm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(list)));
        mvc.perform(put("/api/v1/packs/pack-a/artifacts/paradigm/hexagonal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_failed"));
        assertThat(store.commits).as("a refused registration commits nothing").isZero();
    }

    @Test
    void anAbsentListIsEmpty() throws Exception {
        // The positive control of the matrix above: every list left out.
        mvc.perform(post("/api/v1/packs/pack-a/artifacts/paradigm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("hexagonal")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paradigm.directiveRefs").isArray())
                .andExpect(jsonPath("$.paradigm.directiveRefs", org.hamcrest.Matchers.hasSize(0)));
    }

    private static String body(String id) {
        return "{\"id\":\"" + id + "\",\"schemaVersion\":\"1.0\",\"name\":\"Name\",\"summary\":\"Summary\"}";
    }

    private static Paradigm paradigm(String id, String name, String directiveRef) {
        return Paradigm.builder()
                .id(new ParadigmIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .name(name)
                .summary("Summary of " + id)
                .directiveRef(directiveRef)
                .build();
    }

    /** One fake backing the read port, the paradigm port and the write-path port over a mutable pack. */
    private static final class FakeStore implements PackRepository, ParadigmRepository, PackWriteStore {
        private Pack pack = Pack.builder()
                .id(PACK)
                .descriptor(new PackDescriptor(
                        "pack-a", "1.0.0", Optional.empty(), "pack-a", Optional.empty(), Optional.empty()))
                .build();
        private int commits;

        void put(Paradigm paradigm) {
            Pack.Builder builder = Pack.builder().id(pack.id()).descriptor(pack.descriptor());
            pack.paradigms().stream().filter(p -> !p.id().equals(paradigm.id())).forEach(builder::paradigm);
            builder.paradigm(paradigm);
            pack = builder.build();
        }

        @Override
        public Pack load(PackIdentifier id) {
            if (!PACK.equals(id)) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + id.value());
            }
            return pack;
        }

        @Override
        public void save(Paradigm paradigm) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Paradigm> load(PackIdentifier id, ParadigmIdentifier paradigm) {
            if (!PACK.equals(id)) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + id.value());
            }
            return pack.paradigms().stream()
                    .filter(p -> p.id().equals(paradigm))
                    .findFirst();
        }

        @Override
        public PackRevision currentRevision(PackIdentifier id) {
            return PackRevision.compute(List.of(id.value() + ":" + commits));
        }

        @Override
        public List<Relationship> registeredRelationships(PackIdentifier id) {
            return List.of();
        }

        @Override
        public <T> T exclusively(PackIdentifier id, Supplier<T> work) {
            return work.get();
        }

        @Override
        public PackRevision commit(PackIdentifier id, String ifMatch, Change change) {
            this.pack = change.revalidated();
            commits++;
            return currentRevision(id);
        }
    }
}
