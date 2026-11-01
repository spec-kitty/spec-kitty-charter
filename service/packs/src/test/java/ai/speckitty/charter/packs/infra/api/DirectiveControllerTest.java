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
import ai.speckitty.charter.packs.application.DirectiveRegistrationService;
import ai.speckitty.charter.packs.application.PackRevision;
import ai.speckitty.charter.packs.application.PackWriteStore;
import ai.speckitty.charter.packs.application.PackWriteStore.Change;
import ai.speckitty.charter.packs.application.PermissiveWritePolicyGate;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The HTTP contract of the directive registrations: status, headers and the envelope they answer. The reads are
 * {@code ArtifactControllerTest}'s; the rules are the services' own tests.
 */
class DirectiveControllerTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    private final FakeStore store = new FakeStore();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DirectiveController(
                    new DirectiveRegistrationService(store, store, new PermissiveWritePolicyGate()),
                    new Hypermedia("/api/v1")))
            .setControllerAdvice(new RefusalHandler(), new RegistrationBodyAdvice())
            .build();

    DirectiveControllerTest() {
        store.put(directive("ALPHA", "Alpha"));
    }

    @Test
    void registersANewDirectiveWithLocationAndEtag() throws Exception {
        mvc.perform(post("/api/v1/packs/pack-a/artifacts/directive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("GUARD")))
                .andExpect(status().isCreated())
                .andExpect(
                        header().string("Location", "http://localhost/api/v1/packs/pack-a/artifacts/directive/GUARD"))
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.kind").value("directive"))
                .andExpect(jsonPath("$.id").value("GUARD"))
                .andExpect(jsonPath("$.packId").value("pack-a"))
                .andExpect(jsonPath("$.directive.id").value("GUARD"))
                .andExpect(jsonPath("$.directive.intent").value("Intent"))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/packs/pack-a/artifacts/directive/GUARD"))
                .andExpect(jsonPath("$._links.collection.href").value("/api/v1/packs/pack-a/artifacts?kind=directive"))
                .andExpect(jsonPath("$._links.edit").doesNotExist());
    }

    @Test
    void aDuplicateIdPostIsA409() throws Exception {
        mvc.perform(post("/api/v1/packs/pack-a/artifacts/directive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ALPHA")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("registration_conflict"));
    }

    @Test
    void aPutReplacesAndAnswers200() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/artifacts/directive/ALPHA")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ALPHA")))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.kind").value("directive"))
                .andExpect(jsonPath("$.directive.title").value("Title"))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/packs/pack-a/artifacts/directive/ALPHA"));
    }

    @Test
    void aStaleIfMatchOnAPutIsA412PlainProblem() throws Exception {
        mvc.perform(put("/api/v1/packs/pack-a/artifacts/directive/ALPHA")
                        .header("If-Match", "sha256:" + "0".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ALPHA")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * If-Match is a strong comparison (RFC 9110 13.1.1): a weak tag never matches, {@code *} matches the existing
     * pack, and a list matches when any strong member names the current revision. {@code CURRENT} stands for the
     * current revision token.
     */
    @ParameterizedTest(name = "If-Match {0} -> {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "'\"CURRENT\"'                  | 200",
                "'W/\"CURRENT\"'                | 412",
                "'*'                             | 200",
                "'\"sha256:stale\", \"CURRENT\"' | 200"
            })
    void ifMatchIsAStrongComparison(String ifMatch, int expected) throws Exception {
        String current = store.currentRevision(PACK).token();
        mvc.perform(put("/api/v1/packs/pack-a/artifacts/directive/ALPHA")
                        .header("If-Match", ifMatch.replace("CURRENT", current))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ALPHA")))
                .andExpect(status().is(expected));
    }

    @Test
    void aBodyRelationshipOfAnUnknownKindIsA422() throws Exception {
        String bad =
                "{\"id\":\"GUARD\",\"schemaVersion\":\"1.0\",\"title\":\"Guard\",\"intent\":\"guard\",\"enforcement\":\"required\","
                        + "\"relationships\":[{\"from\":\"directive:GUARD\",\"to\":\"nonsense:x\",\"type\":\"requires\"}]}";
        mvc.perform(post("/api/v1/packs/pack-a/artifacts/directive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    // ---- #292: an explicit null list is refused; an absent one is empty ------------------------------------------

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "procedures",
                "integrityRules",
                "validationCriteria",
                "explicitAllowances",
                "references",
                "relationships"
            })
    void anExplicitNullListIsA422OnPostAndPut(String list) throws Exception {
        String bad = body("GUARD").replace("}", ",\"" + list + "\":null}");
        mvc.perform(post("/api/v1/packs/pack-a/artifacts/directive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(list)));
        mvc.perform(put("/api/v1/packs/pack-a/artifacts/directive/GUARD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("validation_failed"));
        assertThat(store.commits).as("a refused registration commits nothing").isZero();
    }

    @Test
    void anAbsentListIsEmptyAndANullScopeIsStillAccepted() throws Exception {
        // The positive controls of the matrix above: every list left out, and the one member typed nullable.
        mvc.perform(post("/api/v1/packs/pack-a/artifacts/directive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("GUARD").replace("}", ",\"scope\":null}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.directive.references").isEmpty());
    }

    private static String body(String id) {
        return "{\"id\":\"" + id
                + "\",\"schemaVersion\":\"1.0\",\"title\":\"Title\",\"intent\":\"Intent\",\"enforcement\":\"required\"}";
    }

    private static Directive directive(String id, String title) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title(title)
                .intent("Intent of " + id)
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    /** One fake backing the read port, the directive port and the write-path port over a mutable pack. */
    private static final class FakeStore
            implements PackRepository, ai.speckitty.charter.packs.domain.DirectiveRepository, PackWriteStore {
        private Pack pack = Pack.builder()
                .id(PACK)
                .descriptor(new PackDescriptor(
                        "pack-a", "1.0.0", Optional.empty(), "pack-a", Optional.empty(), Optional.empty()))
                .build();
        private int commits;

        void put(Directive directive) {
            Pack.Builder builder = Pack.builder().id(pack.id()).descriptor(pack.descriptor());
            pack.directives().stream()
                    .filter(d -> !d.id().equals(directive.id()))
                    .forEach(builder::directive);
            builder.directive(directive);
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
        public void save(Directive directive) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Directive> load(PackIdentifier id, DirectiveIdentifier directive) {
            if (!PACK.equals(id)) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + id.value());
            }
            return pack.directives().stream()
                    .filter(d -> d.id().equals(directive))
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
