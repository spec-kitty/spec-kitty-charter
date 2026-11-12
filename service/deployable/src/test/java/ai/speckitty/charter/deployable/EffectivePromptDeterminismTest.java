package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.infra.storage.Locations;
import ai.speckitty.charter.packs.infra.storage.PackLocation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * NFR-001 (mission effective-step-and-action-context, WP07): for one activation state the effective prompt read answers
 * a byte-identical body and the same {@code ETag} over 100 repeated reads, and over every permutation of the order in
 * which the closure's three packs are configured, and so loaded, the activation's revision held fixed. The service runs
 * whole, over real pack directories: a built-in pack holds the mission type and the step's prompt with two slots, and
 * it and two org packs each hold one enhancement in force of the same slot, so the order of the fragments is the
 * service's to decide (ADR-0066 decision 5), never the configuration's.
 */
@SpringBootTest(classes = {CharterServiceApplication.class, EffectivePromptDeterminismTest.Packs.class})
@AutoConfigureMockMvc
class EffectivePromptDeterminismTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final String PROMPT = "/api/v1/activations/a-1/mission-types/software-dev/actions/plan/prompt";
    private static final int REPEATS = 100;
    private static final AtomicReference<List<PackLocation>> CONFIGURED = new AtomicReference<>(List.of());
    private static Path root = Path.of("");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private InMemoryActivationRepository activations;

    /** The packs of the test, in the order {@link #CONFIGURED} holds now, and an activation register in memory. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Packs {

        @Bean
        @Primary
        Locations determinismPackLocations() {
            return CONFIGURED::get;
        }

        @Bean
        @Primary
        InMemoryActivationRepository determinismActivationRepository() {
            return new InMemoryActivationRepository();
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        root = Files.createTempDirectory("charter-prompt-determinism");
        CONFIGURED.set(packs(root));
        registry.add("charter.worktree-token", () -> TOKEN);
        registry.add("charter.config-root", () -> root.resolve("config").toString());
    }

    @AfterAll
    static void deleteRoot() throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    @BeforeEach
    void storeTheActivation() throws Exception {
        CONFIGURED.set(packs(root));
        activations.clear();
        MockHttpServletResponse created = send(put("/api/v1/activations/a-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schemaVersion\": 2, \"packs\": [{\"packId\": \"core\"}, {\"packId\": \"zz-arch\"},"
                        + " {\"packId\": \"aa-spdd\"}], \"selected\": {\"mission_type\": [\"software-dev\"],"
                        + " \"step_enhancement\": [\"z-core-note\", \"y-arch-plan\", \"spdd-plan\"]}}"));
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
    }

    @Test
    void oneHundredReadsAnswerTheSameBodyAndTheSameTag() throws Exception {
        MockHttpServletResponse first = read();
        assertThat(first.getContentAsString())
                .as("the fixture's three fragments, in the order of decision 5")
                .contains("\"enhancement\":\"z-core-note\"")
                .contains("\"enhancement\":\"y-arch-plan\"")
                .contains("\"enhancement\":\"spdd-plan\"");

        for (int repeat = 1; repeat < REPEATS; repeat++) {
            MockHttpServletResponse again = read();
            assertThat(again.getContentAsByteArray()).as("read %d", repeat).isEqualTo(first.getContentAsByteArray());
            assertThat(again.getHeader("ETag")).as("read %d", repeat).isEqualTo(first.getHeader("ETag"));
        }
    }

    @Test
    void everyPermutationOfTheConfiguredOrderOfTheThreePacksAnswersTheSameBodyAndTheSameTag() throws Exception {
        List<PackLocation> packs = packs(root);
        MockHttpServletResponse first = read();
        String body = first.getContentAsString();
        assertThat(body.indexOf("z-core-note"))
                .as("built-in first, then the org packs by name: architecture-notes, then spdd")
                .isLessThan(body.indexOf("y-arch-plan"));
        assertThat(body.indexOf("y-arch-plan")).isLessThan(body.indexOf("spdd-plan"));

        List<List<PackLocation>> orders = permutations(packs);
        assertThat(orders).hasSize(6);
        for (List<PackLocation> order : orders) {
            CONFIGURED.set(order);
            MockHttpServletResponse permuted = read();
            String names = order.stream().map(PackLocation::name).toList().toString();
            assertThat(permuted.getContentAsString())
                    .as("configured as %s", names)
                    .isEqualTo(body);
            assertThat(permuted.getHeader("ETag")).as("configured as %s", names).isEqualTo(first.getHeader("ETag"));
        }
    }

    private MockHttpServletResponse read() throws Exception {
        MockHttpServletResponse answer = send(get(PROMPT));
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        assertThat(answer.getHeader("ETag")).isNotBlank();
        return answer;
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + TOKEN))
                .andReturn()
                .getResponse();
    }

    private static List<List<PackLocation>> permutations(List<PackLocation> items) {
        if (items.size() <= 1) {
            return List.of(items);
        }
        List<List<PackLocation>> all = new ArrayList<>();
        for (PackLocation head : items) {
            List<PackLocation> rest = new ArrayList<>(items);
            rest.remove(head);
            for (List<PackLocation> tail : permutations(rest)) {
                List<PackLocation> order = new ArrayList<>();
                order.add(head);
                order.addAll(tail);
                all.add(List.copyOf(order));
            }
        }
        return all;
    }

    /** The three packs, written once under {@code root}: core (built-in), zz-arch and aa-spdd (org). */
    private static List<PackLocation> packs(Path root) throws IOException {
        Path core = root.resolve("built-in/core");
        Path arch = root.resolve("org/zz-arch");
        Path spdd = root.resolve("org/aa-spdd");
        if (!Files.exists(core)) {
            write(core, "pack.yaml", """
                    pack_id: 01M774HB000000000000000C0R
                    pack_version: 1.0.0
                    parent_pack: null
                    name: core
                    pack_level: built-in
                    """);
            write(core, "missions/software-dev/software-dev.mission-type.yaml", """
                    schema_version: "1.0"
                    id: software-dev
                    title: Software development
                    description: Build software
                    steps:
                      - id: plan
                        title: Plan
                        role: planner
                        prompt_path: steps/plan.prompt.md
                    """);
            write(core, "missions/software-dev/steps/plan.prompt.md", """
                    # Plan

                    <!-- spdd:reasons-block:start -->
                    <!-- spdd:reasons-block:end -->

                    <!-- team:checklist:start -->
                    <!-- team:checklist:end -->
                    """);
            enhancement(core, "z-core-note", "Name the constraint each decision answers.");
            write(core, "steps.graph.yaml", """
                    schema_version: '1.0'
                    generated_at: STATIC
                    nodes:
                    - urn: action:software-dev/plan
                      kind: action
                      label: plan
                    edges:
                    - source: step_enhancement:z-core-note
                      target: action:software-dev/plan
                      relation: enhances
                    """);
            org(arch, "01M774HB00000000000000ARCH", "architecture-notes", "y-arch-plan", "Name the ADR.");
            org(spdd, "01M774HB00000000000000SPDD", "spdd", "spdd-plan", "Fill the REASONS canvas.");
        }
        return List.of(
                new PackLocation("core", PackLevel.BUILT_IN, core),
                new PackLocation("zz-arch", PackLevel.ORG, arch),
                new PackLocation("aa-spdd", PackLevel.ORG, spdd));
    }

    private static void org(Path pack, String packId, String name, String enhancement, String text) throws IOException {
        write(pack, "pack.yaml", """
                pack_id: %s
                pack_version: 1.0.0
                parent_pack: null
                name: %s
                pack_level: org
                depends_on:
                - pack_id: 01M774HB000000000000000C0R
                  range: '>=1.0'
                """.formatted(packId, name));
        enhancement(pack, enhancement, text);
        write(pack, "enhancements.graph.yaml", """
                schema_version: '1.0'
                generated_at: STATIC
                nodes: []
                edges:
                - source: step_enhancement:%s
                  target: core::action:software-dev/plan
                  relation: enhances
                """.formatted(enhancement));
    }

    private static void enhancement(Path pack, String id, String text) throws IOException {
        write(pack, "missions/software-dev/enhancements/" + id + ".step-enhancement.yaml", """
                schema_version: '1.0'
                id: %s
                prompt_fragments:
                  - slot: spdd:reasons-block
                    path: %s.prompt-fragment.md
                """.formatted(id, id));
        write(pack, "missions/software-dev/enhancements/" + id + ".prompt-fragment.md", text + "\n");
    }

    private static void write(Path pack, String relative, String content) throws IOException {
        Path file = pack.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
