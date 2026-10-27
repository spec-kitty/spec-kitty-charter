package ai.speckitty.charter.packs.infra.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import ai.speckitty.charter.packs.application.ConsistentRead;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackWriteStore.Change;
import ai.speckitty.charter.packs.application.PackWriteStore;
import ai.speckitty.charter.packs.application.ParadigmReadService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The HTTP contract of the paradigm routes: status, headers and body shape. The rules are the services' own tests. */
class ParadigmControllerTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private static final String DIGEST = "sha256:" + "7".repeat(64);
    private static final String FINGERPRINT = "Charter-Source-Fingerprint";

    private final FakeStore store = new FakeStore();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ParadigmController(
                    new ParadigmReadService(store, store, new ConsistentRead(() -> DIGEST), store),
                    new ParadigmRegistrationService(store, store, new PermissiveWritePolicyGate())))
            .setControllerAdvice(new RefusalHandler())
            .build();

    ParadigmControllerTest() {
        store.put(paradigm("clean-architecture", "Clean Architecture", "ALPHA"));
    }

    @Test
    void listsThePacksParadigmsAsJson() throws Exception {
        mvc.perform(get("/api/v1/packs/pack-a/paradigms"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(FINGERPRINT, DIGEST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("clean-architecture"))
                .andExpect(jsonPath("$[0].name").value("Clean Architecture"))
                .andExpect(jsonPath("$[0].relationships").isArray())
                .andExpect(jsonPath("$[0].relationships[0].to").value("directive:ALPHA"));
    }

    @Test
    void returnsOneParadigmWithAnEtag() throws Exception {
        mvc.perform(get("/api/v1/packs/pack-a/paradigms/clean-architecture"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(FINGERPRINT, DIGEST))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.id").value("clean-architecture"))
                .andExpect(jsonPath("$.summary").value("Summary of clean-architecture"));
    }

    @Test
    void anAbsentParadigmIsA404Problem() throws Exception {
        mvc.perform(get("/api/v1/packs/pack-a/paradigms/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("paradigm_not_found"));
    }

    @Test
    void registersANewParadigmWithLocationAndEtag() throws Exception {
        mvc.perform(post("/api/v1/packs/pack-a/paradigms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("hexagonal")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/packs/pack-a/paradigms/hexagonal"))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.id").value("hexagonal"));
    }

    @Test
    void aDuplicateIdPostIsA409() throws Exception {
        mvc.perform(post("/api/v1/packs/pack-a/paradigms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("clean-architecture")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("registration_conflict"));
    }

    @Test
    void aPutReplacesAndAnswers200() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/paradigms/clean-architecture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("clean-architecture")))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"));
    }

    @Test
    void aStaleIfMatchOnAPutIsA412PlainProblem() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/paradigms/clean-architecture")
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
        mvc.perform(post("/api/v1/packs/pack-a/paradigms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_failed"));
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
                        "pack-a", "1.0.0", Optional.empty(), "pack-a", Optional.empty(), Optional.empty(), List.of()))
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
