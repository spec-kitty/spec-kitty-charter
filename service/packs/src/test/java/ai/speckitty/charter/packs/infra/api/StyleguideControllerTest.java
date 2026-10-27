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
import ai.speckitty.charter.packs.application.PackWriteStore;
import ai.speckitty.charter.packs.application.PackWriteStore.Change;
import ai.speckitty.charter.packs.application.PermissiveWritePolicyGate;
import ai.speckitty.charter.packs.application.StyleguideReadService;
import ai.speckitty.charter.packs.application.StyleguideRegistrationService;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.StyleguideRepository;
import ai.speckitty.charter.packs.domain.StyleguideScope;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The HTTP contract of the styleguide routes: status, headers and body shape. The rules are the services' own tests. */
class StyleguideControllerTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private static final String DIGEST = "sha256:" + "7".repeat(64);
    private static final String FINGERPRINT = "Charter-Source-Fingerprint";

    private final FakeStore store = new FakeStore();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new StyleguideController(
                    new StyleguideReadService(store, store, new ConsistentRead(() -> DIGEST), store),
                    new StyleguideRegistrationService(store, store, new PermissiveWritePolicyGate())))
            .setControllerAdvice(new RefusalHandler())
            .build();

    StyleguideControllerTest() {
        store.put(styleguide("java-style", "Java style"));
    }

    @Test
    void listsThePacksStyleguidesAsJson() throws Exception {
        mvc.perform(get("/api/v1/packs/pack-a/styleguides"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(FINGERPRINT, DIGEST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("java-style"))
                .andExpect(jsonPath("$[0].scope").value("code"))
                .andExpect(jsonPath("$[0].relationships").isArray())
                .andExpect(jsonPath("$[0].relationships").isEmpty());
    }

    @Test
    void returnsOneStyleguideWithAnEtag() throws Exception {
        mvc.perform(get("/api/v1/packs/pack-a/styleguides/java-style"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(FINGERPRINT, DIGEST))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.id").value("java-style"))
                .andExpect(jsonPath("$.title").value("Java style"));
    }

    @Test
    void anAbsentStyleguideIsA404Problem() throws Exception {
        mvc.perform(get("/api/v1/packs/pack-a/styleguides/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("styleguide_not_found"));
    }

    @Test
    void registersANewStyleguideWithLocationAndEtag() throws Exception {
        mvc.perform(post("/api/v1/packs/pack-a/styleguides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("docs-style")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/packs/pack-a/styleguides/docs-style"))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.id").value("docs-style"));
    }

    @Test
    void aDuplicateIdPostIsA409() throws Exception {
        mvc.perform(post("/api/v1/packs/pack-a/styleguides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("java-style")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("registration_conflict"));
    }

    @Test
    void aPutReplacesAndAnswers200() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/styleguides/java-style")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("java-style")))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"));
    }

    @Test
    void aStaleIfMatchOnAPutIsA412PlainProblem() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/styleguides/java-style")
                        .header("If-Match", "sha256:" + "0".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("java-style")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void aBodyWithAnUnknownScopeIsA422() throws Exception {
        String bad = "{\"id\":\"docs-style\",\"schemaVersion\":\"1.0\",\"title\":\"Docs\",\"scope\":\"nonsense\","
                + "\"principles\":[\"Prefer clarity\"]}";
        mvc.perform(post("/api/v1/packs/pack-a/styleguides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    private static String body(String id) {
        return "{\"id\":\"" + id
                + "\",\"schemaVersion\":\"1.0\",\"title\":\"Title\",\"scope\":\"code\",\"principles\":[\"Prefer clarity\"]}";
    }

    private static Styleguide styleguide(String id, String title) {
        return Styleguide.builder()
                .id(new StyleguideIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title(title)
                .scope(StyleguideScope.CODE)
                .principle("Prefer clarity")
                .build();
    }

    /** One fake backing the read port, the styleguide port and the write-path port over a mutable pack. */
    private static final class FakeStore implements PackRepository, StyleguideRepository, PackWriteStore {
        private Pack pack = Pack.builder()
                .id(PACK)
                .descriptor(new PackDescriptor(
                        "pack-a", "1.0.0", Optional.empty(), "pack-a", Optional.empty(), Optional.empty(), List.of()))
                .build();
        private int commits;

        void put(Styleguide styleguide) {
            Pack.Builder builder = Pack.builder().id(pack.id()).descriptor(pack.descriptor());
            pack.styleguides().stream()
                    .filter(s -> !s.id().equals(styleguide.id()))
                    .forEach(builder::styleguide);
            builder.styleguide(styleguide);
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
        public void save(Styleguide styleguide) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Styleguide> load(PackIdentifier id, StyleguideIdentifier styleguide) {
            if (!PACK.equals(id)) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + id.value());
            }
            return pack.styleguides().stream()
                    .filter(s -> s.id().equals(styleguide))
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
