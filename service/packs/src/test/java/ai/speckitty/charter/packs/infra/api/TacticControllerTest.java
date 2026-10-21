package ai.speckitty.charter.packs.infra.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.speckitty.charter.packs.application.TacticReadService;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.TacticRepository;
import ai.speckitty.charter.packs.domain.TacticStep;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The HTTP contract of the tactic routes: status and body shape. The read rules are the service's own tests. */
class TacticControllerTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");

    private final Tactic alpha = Tactic.builder()
            .id(new TacticIdentifier("alpha"))
            .packId(PACK)
            .schemaVersion("1.0")
            .name("Alpha")
            .purpose("Be first")
            .step(new TacticStep("Step one", Optional.empty(), List.of(), List.of()))
            .build();

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new TacticController(new TacticReadService(new FixedPacks(), new FixedTactics())))
            .setControllerAdvice(new RefusalHandler())
            .build();

    @Test
    void listsThePacksTacticsAsJson() throws Exception {
        mvc.perform(get("/api/v1/packs/pack-a/tactics"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("alpha"))
                .andExpect(jsonPath("$[0].name").value("Alpha"))
                .andExpect(jsonPath("$[0].purpose").value("Be first"))
                .andExpect(jsonPath("$[0].notes").value((Object) null))
                .andExpect(jsonPath("$[0].steps[0].title").value("Step one"));
    }

    @Test
    void returnsOneTacticByIdentity() throws Exception {
        mvc.perform(get("/api/v1/packs/pack-a/tactics/alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("alpha"))
                .andExpect(jsonPath("$.schemaVersion").value("1.0"))
                .andExpect(jsonPath("$.steps.length()").value(1))
                .andExpect(jsonPath("$.failureModes.length()").value(0));
    }

    @Test
    void anAbsentPackIsA404ProblemNotAServerError() throws Exception {
        mvc.perform(get("/api/v1/packs/absent/tactics"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("pack_not_found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void anAbsentTacticIsA404ProblemWhileThePackAnswers() throws Exception {
        mvc.perform(get("/api/v1/packs/pack-a/tactics/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("tactic_not_found"));
    }

    private final class FixedPacks implements PackRepository {
        @Override
        public Pack load(PackIdentifier pack) {
            throw new UnsupportedOperationException("the list is a point read");
        }

        @Override
        public List<Tactic> tacticsOf(PackIdentifier pack) {
            if (!PACK.equals(pack)) {
                throw new ai.speckitty.charter.kernel.domain.CharterRefusal(
                        ai.speckitty.charter.kernel.domain.RefusalCode.PACK_UNREADABLE, "no such pack");
            }
            return List.of(alpha);
        }
    }

    private final class FixedTactics implements TacticRepository {
        @Override
        public void save(Tactic tactic) {
            throw new UnsupportedOperationException("read only");
        }

        @Override
        public Optional<Tactic> load(PackIdentifier pack, TacticIdentifier tactic) {
            if (!PACK.equals(pack)) {
                throw new ai.speckitty.charter.kernel.domain.CharterRefusal(
                        ai.speckitty.charter.kernel.domain.RefusalCode.PACK_UNREADABLE, "no such pack");
            }
            return Optional.of(alpha).filter(t -> t.id().equals(tactic));
        }
    }
}
