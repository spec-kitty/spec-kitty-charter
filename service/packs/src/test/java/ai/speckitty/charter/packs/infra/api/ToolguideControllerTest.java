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
import ai.speckitty.charter.packs.application.PermissiveWritePolicyGate;
import ai.speckitty.charter.packs.application.ToolguideReadService;
import ai.speckitty.charter.packs.application.ToolguideRegistrationService;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.domain.ToolguideRepository;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The HTTP contract of the toolguide routes: status, headers and body shape. The rules are the services' own tests. */
class ToolguideControllerTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private static final String DIGEST = "sha256:" + "7".repeat(64);
    private static final String FINGERPRINT = "Charter-Source-Fingerprint";

    private final FakeStore store = new FakeStore();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ToolguideController(
                    new ToolguideReadService(store, store, new ConsistentRead(() -> DIGEST), store),
                    new ToolguideRegistrationService(store, store, new PermissiveWritePolicyGate())))
            .setControllerAdvice(new RefusalHandler())
            .build();

    ToolguideControllerTest() {
        store.put(toolguide("git", "2026-03-03"));
    }

    @Test
    void listsThePacksToolguidesAsJson() throws Exception {
        mvc.perform(get("/api/v1/packs/pack-a/toolguides"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(FINGERPRINT, DIGEST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("git"))
                .andExpect(jsonPath("$[0].tool").value("git"))
                .andExpect(jsonPath("$[0].guidePath").value("git.md"))
                .andExpect(jsonPath("$[0].lastUpdated").value("2026-03-03"));
    }

    @Test
    void returnsOneToolguideWithAnEtag() throws Exception {
        mvc.perform(get("/api/v1/packs/pack-a/toolguides/git"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(FINGERPRINT, DIGEST))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.id").value("git"))
                .andExpect(jsonPath("$.summary").value("Using git under governance."));
    }

    @Test
    void anAbsentToolguideIsA404Problem() throws Exception {
        mvc.perform(get("/api/v1/packs/pack-a/toolguides/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("toolguide_not_found"));
    }

    @Test
    void registersANewToolguideWithLocationAndEtag() throws Exception {
        mvc.perform(post("/api/v1/packs/pack-a/toolguides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("make")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/packs/pack-a/toolguides/make"))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.id").value("make"));
    }

    @Test
    void aDuplicateIdPostIsA409() throws Exception {
        mvc.perform(post("/api/v1/packs/pack-a/toolguides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("git")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("registration_conflict"));
    }

    @Test
    void aPutReplacesAndAnswers200() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/toolguides/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("git")))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"));
    }

    @Test
    void aPutCreatesAndAnswers201() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/toolguides/make")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("make")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.id").value("make"));
    }

    @Test
    void aStaleIfMatchOnAPutIsA412PlainProblem() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/toolguides/git")
                        .header("If-Match", "sha256:" + "0".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("git")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void aMalformedBodyIsA422() throws Exception {
        // Well-formed JSON, DTO-valid, but domain-invalid: schemaVersion breaks the toolguide's ^1\.0$ invariant.
        String bad = "{\"id\":\"make\",\"schemaVersion\":\"9.9\",\"tool\":\"make\",\"title\":\"Make\","
                + "\"guidePath\":\"make.md\",\"summary\":\"Using make.\"}";
        mvc.perform(post("/api/v1/packs/pack-a/toolguides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    private static String body(String id) {
        return "{\"id\":\"" + id + "\",\"schemaVersion\":\"1.0\",\"tool\":\"" + id + "\",\"title\":\"How to use " + id
                + "\",\"guidePath\":\"" + id + ".md\",\"summary\":\"Using " + id + " under governance.\"}";
    }

    private static Toolguide toolguide(String id, String lastUpdated) {
        return Toolguide.builder()
                .id(new ToolguideIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .tool(id)
                .title("How to use " + id)
                .guidePath(id + ".md")
                .summary("Using " + id + " under governance.")
                .lastUpdated(lastUpdated)
                .build();
    }

    /** One fake backing the read port, the toolguide port and the write-path port over a mutable pack. */
    private static final class FakeStore implements PackRepository, ToolguideRepository, PackWriteStore {
        private Pack pack = Pack.builder()
                .id(PACK)
                .descriptor(new PackDescriptor(
                        "pack-a", "1.0.0", Optional.empty(), "pack-a", Optional.empty(), Optional.empty(), List.of()))
                .build();
        private int commits;

        void put(Toolguide toolguide) {
            Pack.Builder builder = Pack.builder().id(pack.id()).descriptor(pack.descriptor());
            pack.toolguides().stream()
                    .filter(t -> !t.id().equals(toolguide.id()))
                    .forEach(builder::toolguide);
            builder.toolguide(toolguide);
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
        public void save(Toolguide toolguide) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Toolguide> load(PackIdentifier id, ToolguideIdentifier toolguide) {
            if (!PACK.equals(id)) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + id.value());
            }
            return pack.toolguides().stream()
                    .filter(t -> t.id().equals(toolguide))
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
