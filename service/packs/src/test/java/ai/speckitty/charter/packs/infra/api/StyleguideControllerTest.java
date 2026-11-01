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
import ai.speckitty.charter.packs.application.PermissiveWritePolicyGate;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The HTTP contract of the styleguide registrations: status, headers and the envelope they answer. The reads are
 * {@code ArtifactControllerTest}'s; the rules are the services' own tests.
 */
class StyleguideControllerTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    private final FakeStore store = new FakeStore();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new StyleguideController(
                    new StyleguideRegistrationService(store, store, new PermissiveWritePolicyGate()),
                    new Hypermedia("/api/v1")))
            .setControllerAdvice(new RefusalHandler(), new RegistrationBodyAdvice())
            .build();

    StyleguideControllerTest() {
        store.put(styleguide("java-style", "Java style"));
    }

    @Test
    void registersANewStyleguideWithLocationAndEtag() throws Exception {
        mvc.perform(post("/api/v1/packs/pack-a/artifacts/styleguide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("docs-style")))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                                "Location", "http://localhost/api/v1/packs/pack-a/artifacts/styleguide/docs-style"))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.kind").value("styleguide"))
                .andExpect(jsonPath("$.id").value("docs-style"))
                .andExpect(jsonPath("$.packId").value("pack-a"))
                .andExpect(jsonPath("$.styleguide.id").value("docs-style"))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/packs/pack-a/artifacts/styleguide/docs-style"))
                .andExpect(
                        jsonPath("$._links.collection.href").value("/api/v1/packs/pack-a/artifacts?kind=styleguide"));
    }

    @Test
    void aDuplicateIdPostIsA409() throws Exception {
        mvc.perform(post("/api/v1/packs/pack-a/artifacts/styleguide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("java-style")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("registration_conflict"));
    }

    @Test
    void aPutReplacesAndAnswers200() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/artifacts/styleguide/java-style")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("java-style")))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"));
    }

    @Test
    void aStaleIfMatchOnAPutIsA412PlainProblem() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/artifacts/styleguide/java-style")
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
        mvc.perform(post("/api/v1/packs/pack-a/artifacts/styleguide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    // ---- #292: an explicit null list is refused; an absent one is empty ------------------------------------------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"principles", "patterns", "antiPatterns", "tooling", "appliesToLanguages", "references"})
    void anExplicitNullListIsA422OnPostAndPut(String list) throws Exception {
        String bad = "{\"id\":\"docs-style\",\"schemaVersion\":\"1.0\",\"title\":\"Docs\",\"scope\":\"code\","
                + "\"principles\":[\"Prefer clarity\"],\"" + list + "\":null}";
        if (list.equals("principles")) {
            bad = bad.replace("\"principles\":[\"Prefer clarity\"],", "");
        }
        mvc.perform(post("/api/v1/packs/pack-a/artifacts/styleguide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(list)));
        mvc.perform(put("/api/v1/packs/pack-a/artifacts/styleguide/docs-style")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_failed"));
        assertThat(store.commits).as("a refused registration commits nothing").isZero();
    }

    @Test
    void anAbsentOptionalListIsEmpty() throws Exception {
        // The positive control of the matrix above: every optional list left out.
        mvc.perform(post("/api/v1/packs/pack-a/artifacts/styleguide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("docs-style")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.styleguide.patterns").isEmpty());
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
                        "pack-a", "1.0.0", Optional.empty(), "pack-a", Optional.empty(), Optional.empty()))
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
