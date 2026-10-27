package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.DirectiveReference;
import ai.speckitty.charter.packs.domain.DirectiveRepository;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.ParadigmReference;
import ai.speckitty.charter.packs.domain.ParadigmRepository;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.StyleguideAntiPattern;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.StyleguidePattern;
import ai.speckitty.charter.packs.domain.StyleguideRepository;
import ai.speckitty.charter.packs.domain.StyleguideScope;
import ai.speckitty.charter.packs.infra.storage.DomainRepositoryService;
import ai.speckitty.charter.packs.infra.storage.SerializationStrategy;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The doctrine kinds through the production path: the directive, styleguide and paradigm ports and the serializer
 * are the beans Spring wires from {@code charter.storage.root}, not instances built by the test. A save followed by a load
 * must hand back every field, compared field by field because entity {@code equals} is identity only; each
 * round-trip sits beside a control that the wired pack already serves its seeded entity.
 */
@SpringBootTest(classes = CharterServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DoctrineWiringRoundTripTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";
    private static final PackIdentifier PACK = new PackIdentifier("01M5JHD0DOCTRINEWIRING0000A");
    private static Path packs = Path.of("");

    @Autowired
    private DirectiveRepository directives;

    @Autowired
    private StyleguideRepository styleguides;

    @Autowired
    private ParadigmRepository paradigms;

    @Autowired
    private PackRepository packRepository;

    @Autowired
    private SerializationStrategy serializer;

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void configuredFixturePack(DynamicPropertyRegistry registry) throws IOException {
        packs = Files.createTempDirectory("charter-doctrine-wiring");
        write("fixture/pack.yaml", """
                pack_id: 01M5JHD0DOCTRINEWIRING0000A
                pack_version: 1.0.0
                parent_pack: null
                name: fixture
                pack_level: org
                """);
        write("fixture/seed.directive.yaml", """
                id: DIRECTIVE_800
                schema_version: '1.0'
                title: Seeded directive
                intent: Prove the wired bean reads a directive from disk.
                enforcement: required
                """);
        registry.add("charter.worktree-token", () -> TOKEN);
        registry.add("charter.storage.root", () -> packs.toString());
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
    void theWiredPortsAndSerializerAreOneScanDrivenServiceAndServeTheSeededDirective() {
        assertThat(context.getBeansOfType(DomainRepositoryService.class)).hasSize(1);
        assertThat(serializer).isInstanceOf(YamlSerializationStrategy.class);
        assertThat(directives).isSameAs(styleguides).isSameAs(paradigms).isSameAs(packRepository);

        Directive seeded =
                directives.load(PACK, new DirectiveIdentifier("DIRECTIVE_800")).orElseThrow();
        assertThat(seeded.title()).isEqualTo("Seeded directive");
        assertThat(directives.load(PACK, new DirectiveIdentifier("DIRECTIVE_899")))
                .isEmpty();
    }

    @Test
    void aDirectiveSavedThroughTheWiredRepositoryLoadsBackWithEveryField() {
        Directive saved = Directive.builder()
                .id(new DirectiveIdentifier("DIRECTIVE_801"))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Round-tripped directive")
                .intent("Every field must survive")
                .enforcement(Enforcement.LENIENT_ADHERENCE)
                .scope("global")
                .procedure("release")
                .integrityRule("rule")
                .validationCriterion("criterion")
                .explicitAllowance("spikes may skip it")
                .reference(new DirectiveReference("tactic", "tdd", Optional.of("always"), Optional.of("why")))
                .build();

        directives.save(saved);

        Directive reloaded = directives.load(PACK, saved.id()).orElseThrow();
        assertThat(reloaded).usingRecursiveComparison().isEqualTo(saved);
        assertThat(reloaded.enforcement()).isEqualTo(Enforcement.LENIENT_ADHERENCE);
    }

    @Test
    void aStyleguideSavedThroughTheWiredRepositoryLoadsBackWithEveryField() {
        Styleguide saved = Styleguide.builder()
                .id(new StyleguideIdentifier("wired-style"))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Round-tripped styleguide")
                .scope(StyleguideScope.TESTING)
                .principle("be small")
                .pattern(new StyleguidePattern("P", "a pattern", Optional.of("bad"), Optional.empty()))
                .antiPattern(new StyleguideAntiPattern("A", "an anti-pattern", "bad", "good"))
                .qualityTest("does it read")
                .appliesToLanguage("java")
                .reference("other-style")
                .build();

        styleguides.save(saved);

        Styleguide reloaded = styleguides.load(PACK, saved.id()).orElseThrow();
        assertThat(reloaded).usingRecursiveComparison().isEqualTo(saved);
        assertThat(reloaded.scope()).isEqualTo(StyleguideScope.TESTING);
    }

    @Test
    void aParadigmSavedThroughTheWiredRepositoryLoadsBackAndTheAggregateServesAllKinds() {
        Paradigm saved = Paradigm.builder()
                .id(new ParadigmIdentifier("wired-paradigm"))
                .packId(PACK)
                .schemaVersion("1.0")
                .name("Round-tripped paradigm")
                .summary("A way of working")
                .directiveRef("DIRECTIVE_800")
                .reference(new ParadigmReference("tactic", "tdd"))
                .build();

        paradigms.save(saved);

        Paradigm reloaded = paradigms.load(PACK, saved.id()).orElseThrow();
        assertThat(reloaded).usingRecursiveComparison().isEqualTo(saved);

        Pack pack = packRepository.load(PACK);
        assertThat(pack.paradigms()).contains(saved);
        assertThat(pack.directives()).extracting(Directive::id).contains(new DirectiveIdentifier("DIRECTIVE_800"));
    }
}
