package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * NFR-003, the round trip, over HTTP: for each of {@value #DOCUMENTS} valid documents per seed, generated with a plain
 * seeded {@link Random} (DIRECTIVE_051: no property-testing library), a {@code PUT} creates it, a {@code GET} reads it,
 * and a {@code PUT} of the fetched {@code config} with the fetched {@code ETag} as {@code If-Match} answers {@code 200}
 * with the same {@code ETag}, in every case. The documents vary each list between absent, {@code []} and a random
 * subset written unsorted and with repeats; {@code packs} absent or present (pinned or not); and non-empty deny lists
 * that avoid the fixture's required floor. Every assertion names the seed and the document, so a failure replays.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        properties = "charter.worktree-token=" + ActivationTestWiring.TOKEN)
@AutoConfigureMockMvc
@Import({ConfigRootTestConfiguration.class, ActivationTestWiring.class})
class ActivationRoundTripPropertyTest {

    /** The documents generated per seed. */
    static final int DOCUMENTS = 1_000;

    /** The fixed seeds; each one generates {@link #DOCUMENTS} documents. */
    static final List<Long> SEEDS = List.of(20_261_105L);

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> LANGUAGES = List.of("go", "java", "python", "c++");

    /** The kinds whose ids the fixture holds as {@link ActivationTestWiring#IDS}: every activatable kind but one. */
    private static final List<ArtifactKind> ARTIFACT_KINDS = Arrays.stream(ArtifactKind.values())
            .filter(kind -> kind.activatable() && kind != ArtifactKind.MISSION_TYPE)
            .toList();

    @Autowired
    MockMvc mvc;

    @Autowired
    InMemoryActivationRepository repository;

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + ActivationTestWiring.TOKEN))
                .andReturn()
                .getResponse();
    }

    @Test
    void everyFetchedDocumentRegistersUnchanged() throws Exception {
        int checked = 0;
        for (long seed : SEEDS) {
            repository.clear();
            Random random = new Random(seed);
            for (int n = 0; n < DOCUMENTS; n++) {
                roundTrip(seed, n, document(random));
                checked++;
            }
        }
        assertThat(checked).isEqualTo(SEEDS.size() * DOCUMENTS).isGreaterThanOrEqualTo(1_000);
    }

    private void roundTrip(long seed, int n, ObjectNode document) throws Exception {
        String what = "seed " + seed + ", document " + n + ": " + document;
        String path = "/api/v1/activations/p-" + n;

        MockHttpServletResponse created =
                send(put(path).contentType(MediaType.APPLICATION_JSON).content(document.toString()));
        assertThat(created.getStatus())
                .as(what + " -> " + created.getContentAsString())
                .isEqualTo(201);
        String etag = created.getHeader(HttpHeaders.ETAG);

        MockHttpServletResponse read = send(get(path));
        assertThat(read.getStatus()).as(what).isEqualTo(200);
        assertThat(read.getHeader(HttpHeaders.ETAG)).as(what).isEqualTo(etag);
        JsonNode config = JSON.readTree(read.getContentAsString()).path("config");

        MockHttpServletResponse again = send(put(path)
                .header(HttpHeaders.IF_MATCH, etag)
                .contentType(MediaType.APPLICATION_JSON)
                .content(config.toString()));
        assertThat(again.getStatus())
                .as(what + " -> " + again.getContentAsString())
                .isEqualTo(200);
        assertThat(again.getHeader(HttpHeaders.ETAG)).as(what).isEqualTo(etag);
        assertThat(JSON.readTree(again.getContentAsString()).path("config"))
                .as(what)
                .isEqualTo(config);
    }

    /** One valid document of the fixture's closure. */
    private static ObjectNode document(Random random) {
        ObjectNode document = JSON.createObjectNode().put("schemaVersion", 2);
        if (random.nextBoolean()) {
            ObjectNode pack = document.putArray("packs").addObject().put("packId", "fixture");
            if (random.nextBoolean()) {
                pack.put("version", "1.0.0");
            }
        }
        maybeList(random, document, "languages", LANGUAGES);
        if (random.nextInt(3) > 0) {
            ObjectNode selected = document.putObject("selected");
            for (ArtifactKind kind : ARTIFACT_KINDS) {
                maybeList(random, selected, kind.token(), ActivationTestWiring.IDS);
            }
            maybeList(random, selected, ArtifactKind.MISSION_TYPE.token(), ActivationTestWiring.MISSION_TYPES);
        }
        if (random.nextBoolean()) {
            deactivated(random, document.putObject("deactivated"));
        }
        return document;
    }

    /** Leaves the member absent, writes {@code []}, or writes a random subset, unsorted and with repeats. */
    private static void maybeList(Random random, ObjectNode parent, String member, List<String> pool) {
        int shape = random.nextInt(3);
        if (shape == 0) {
            return;
        }
        ArrayNode list = parent.putArray(member);
        if (shape == 2) {
            subset(random, pool).forEach(list::add);
        }
    }

    /** Non-empty deny lists of some kinds, never naming the fixture's required directive. */
    private static void deactivated(Random random, ObjectNode deactivated) {
        for (ArtifactKind kind : ARTIFACT_KINDS) {
            List<String> pool = ActivationTestWiring.IDS.stream()
                    .filter(id -> kind != ArtifactKind.DIRECTIVE || !id.equals(ActivationTestWiring.REQUIRED))
                    .toList();
            if (random.nextInt(4) == 0) {
                ArrayNode list = deactivated.putArray(kind.token());
                List<String> ids = subset(random, pool);
                (ids.isEmpty() ? List.of(pool.getFirst()) : ids).forEach(list::add);
            }
        }
    }

    /** A random selection of the pool, shuffled, with an occasional repeat. */
    private static List<String> subset(Random random, List<String> pool) {
        List<String> ids = new ArrayList<>();
        for (String id : pool) {
            if (random.nextBoolean()) {
                ids.add(id);
            }
            if (random.nextInt(5) == 0) {
                ids.add(id);
            }
        }
        java.util.Collections.shuffle(ids, random);
        return ids;
    }
}
