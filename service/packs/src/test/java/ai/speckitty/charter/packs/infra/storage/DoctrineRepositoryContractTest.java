package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.DirectiveReference;
import ai.speckitty.charter.packs.domain.DirectiveRepository;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackElementViolation;
import ai.speckitty.charter.packs.domain.PackIdentifier;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The per-kind repository contract for directives, styleguides and paradigms, driven through the ports over a
 * fixture pack: a saved entity loads back equal, a second entity with a new id gets its own file and leaves its
 * sibling intact, and an unknown id is empty rather than thrown. Each absent-id check sits beside a present-id
 * control on the same pack.
 */
class DoctrineRepositoryContractTest {

    private static final PackIdentifier PACK = new PackIdentifier("01M5DEH8ROUNDTRIPFIXTURE0A");

    private DomainRepositoryService service;
    private Path packDirectory;

    @BeforeEach
    void configuredOverTheFixturePack(@TempDir Path host) throws IOException {
        packDirectory = PackFixtures.copyOf("pack-a", host);
        PackLocation location = new PackLocation("pack-a", PackLevel.ORG, packDirectory);
        service = new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy());
    }

    private long filesEndingWith(String suffix) throws IOException {
        try (Stream<Path> list = Files.list(packDirectory)) {
            return list.filter(p -> p.getFileName().toString().endsWith(suffix)).count();
        }
    }

    private static Directive directive(String id, String title) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title(title)
                .intent("Keep it honest")
                .enforcement(Enforcement.LENIENT_ADHERENCE)
                .scope("global")
                .procedure("first")
                .procedure("second")
                .integrityRule("rule")
                .validationCriterion("criterion")
                .explicitAllowance("allowance")
                .reference(new DirectiveReference("tactic", "t-1", Optional.of("when"), Optional.of("why")))
                .build();
    }

    private static Styleguide styleguide(String id, String title) {
        return Styleguide.builder()
                .id(new StyleguideIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title(title)
                .scope(StyleguideScope.TESTING)
                .principle("be small")
                .pattern(new StyleguidePattern("P", "a pattern", Optional.of("bad"), Optional.empty()))
                .antiPattern(new StyleguideAntiPattern("A", "an anti-pattern", "bad", "good"))
                .qualityTest("does it read")
                .appliesToLanguage("java")
                .reference("other-styleguide")
                .build();
    }

    private static Paradigm paradigm(String id, String name) {
        return Paradigm.builder()
                .id(new ParadigmIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .name(name)
                .summary("A way of working")
                .directiveRef("DIRECTIVE_001")
                .reference(new ParadigmReference("tactic", "t-1"))
                .build();
    }

    @Test
    void aSavedDirectiveLoadsBackEqualAndASecondIdGetsItsOwnFile() throws IOException {
        DirectiveRepository repository = service;
        Directive first = directive("DIRECTIVE_901", "First");
        Directive second = directive("DIRECTIVE_902", "Second");

        repository.save(first);
        repository.save(second);

        assertThat(repository.load(PACK, first.id())).contains(first);
        assertThat(repository.load(PACK, second.id())).contains(second);
        assertThat(filesEndingWith(".directive.yaml")).isEqualTo(2);
        assertThat(repository.load(PACK, new DirectiveIdentifier("DIRECTIVE_999")))
                .isEmpty();
        assertThat(service.load(PACK).directives()).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void aSavedStyleguideLoadsBackEqualAndASecondIdGetsItsOwnFile() throws IOException {
        StyleguideRepository repository = service;
        Styleguide first = styleguide("style-one", "First");
        Styleguide second = styleguide("style-two", "Second");

        repository.save(first);
        repository.save(second);

        assertThat(repository.load(PACK, first.id())).contains(first);
        assertThat(repository.load(PACK, second.id())).contains(second);
        assertThat(filesEndingWith(".styleguide.yaml")).isEqualTo(2);
        assertThat(repository.load(PACK, new StyleguideIdentifier("style-missing")))
                .isEmpty();
        assertThat(service.load(PACK).styleguides()).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void aSavedParadigmLoadsBackEqualAndASecondIdGetsItsOwnFile() throws IOException {
        ParadigmRepository repository = service;
        Paradigm first = paradigm("paradigm-one", "First");
        Paradigm second = paradigm("paradigm-two", "Second");

        repository.save(first);
        repository.save(second);

        assertThat(repository.load(PACK, first.id())).contains(first);
        assertThat(repository.load(PACK, second.id())).contains(second);
        assertThat(filesEndingWith(".paradigm.yaml")).isEqualTo(2);
        assertThat(repository.load(PACK, new ParadigmIdentifier("paradigm-missing")))
                .isEmpty();
        assertThat(service.load(PACK).paradigms()).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void savingAKnownIdReplacesItsFileInPlace() throws IOException {
        DirectiveRepository repository = service;
        repository.save(directive("DIRECTIVE_901", "Before"));

        repository.save(directive("DIRECTIVE_901", "After"));

        assertThat(repository.load(PACK, new DirectiveIdentifier("DIRECTIVE_901")))
                .map(Directive::title)
                .contains("After");
        assertThat(filesEndingWith(".directive.yaml")).isEqualTo(1);
        Pack pack = service.load(PACK);
        assertThat(pack.directives()).hasSize(1);
    }

    @Test
    void aSchemaValidButDomainInvalidDirectiveIsAViolationWhileAValidSiblingStillLoads() throws IOException {
        Directive valid = directive("DIRECTIVE_901", "Valid");
        service.save(valid);
        Files.writeString(packDirectory.resolve("DIRECTIVE_902.directive.yaml"), """
                id: DIRECTIVE_902
                schema_version: '1.0'
                title: Blank procedure
                intent: i
                enforcement: required
                procedures: ['']
                """);

        Pack pack = service.load(PACK);

        assertThat(pack.directives()).containsExactly(valid);
        assertThat(pack.violations())
                .extracting(PackElementViolation::file, PackElementViolation::kind)
                .contains(tuple("DIRECTIVE_902", "domain"));
    }
}
