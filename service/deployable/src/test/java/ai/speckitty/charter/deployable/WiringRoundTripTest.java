package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.TacticRepository;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * FR-010 through the production path: the persistence ports are the beans Spring wires from {@code charter.packs},
 * not instances built by the test. They are addressed by domain port type and domain identifiers only.
 */
@SpringBootTest(classes = CharterServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WiringRoundTripTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final PackIdentifier PACK = new PackIdentifier("01M5EET9WIRINGFIXTURE0000A");
    private static final TacticIdentifier ALPHA = new TacticIdentifier("alpha-tactic");
    private static Path packs = Path.of("");

    @Autowired
    private TacticRepository tactics;

    @Autowired
    private PackRepository packRepository;

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void configuredFixturePack(DynamicPropertyRegistry registry) throws IOException {
        packs = Files.createTempDirectory("charter-wiring-roundtrip");
        write("fixture/pack.yaml", """
                pack_id: 01M5EET9WIRINGFIXTURE0000A
                pack_version: 1.0.0
                parent_pack: null
                name: fixture
                pack_level: org
                """);
        write("fixture/alpha-tactic.tactic.yaml", """
                id: alpha-tactic
                schema_version: '1.0'
                name: Alpha tactic
                purpose: Prove the wired bean is live.
                steps:
                  - title: Only step
                """);
        registry.add("charter.worktree-token", () -> TOKEN);
        registry.add("charter.packs[0].name", () -> "fixture");
        registry.add("charter.packs[0].level", () -> "org");
        registry.add("charter.packs[0].path", () -> packs.resolve("fixture").toString());
    }

    private static void write(String relativePath, String content) throws IOException {
        Path file = packs.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    @AfterAll
    static void deletePacks() throws IOException {
        try (Stream<Path> walk = Files.walk(packs)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    @Test
    void aTacticSavedThroughTheWiredRepositoryLoadsBackEqual() {
        Tactic original = tactics.load(PACK, ALPHA).orElseThrow();
        Tactic renamed = Tactic.builder()
                .id(original.id())
                .packId(original.packId())
                .schemaVersion(original.schemaVersion().orElseThrow())
                .name("Alpha tactic, renamed through the wired bean")
                .step(original.steps().get(0))
                .build();

        tactics.save(renamed);

        Tactic reloaded = tactics.load(PACK, ALPHA).orElseThrow();
        assertThat(reloaded).isEqualTo(renamed);
        assertThat(reloaded.name()).isNotEqualTo(original.name());
    }

    @Test
    void theWiredRepositoryIsFunctionalAndAnAbsentIdIsEmpty() {
        assertThat(tactics.load(PACK, ALPHA)).isPresent();
        assertThat(tactics.load(PACK, new TacticIdentifier("no-such-tactic"))).isEmpty();
        assertThat(packRepository.load(PACK).id()).isEqualTo(PACK);
        // scan-driven, exactly one: the ports resolve to the same single bean
        assertThat(context.getBeansOfType(DomainRepositoryService.class)).hasSize(1);
    }
}
