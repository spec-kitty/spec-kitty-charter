package ai.speckitty.charter.packs.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.PackIdentifier;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.StyleguideAntiPattern;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.StyleguidePattern;
import ai.speckitty.charter.packs.domain.StyleguideScope;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.TacticStep;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * NFR-002, the validation-parity audit: each kind's {@code ModularRuleset} must reject a fixture that breaks one
 * canonical required field, enum, cardinality or the directive's lenient-adherence conditional. Every rejection is
 * paired with a control built from the same fixture with only the offending value repaired, and the rejection must
 * name the intended rule, so a case cannot pass because some other rule tripped (DIRECTIVE_041 non-vacuity).
 */
class DoctrineValidationParityTest {

    private static final PackIdentifier PACK = new PackIdentifier("01M5DEH8ROUNDTRIPFIXTURE0A");

    // ---- one fixture per kind, each field overridable so a case differs from its control in exactly one value ----

    private static Directive directive(
            String schema,
            String title,
            String intent,
            Enforcement enforcement,
            List<String> allowances,
            String procedure) {
        Directive.Builder builder = Directive.builder()
                .id(new DirectiveIdentifier("DIRECTIVE_950"))
                .packId(PACK)
                .schemaVersion(schema)
                .title(title)
                .intent(intent)
                .enforcement(enforcement)
                .procedure(procedure);
        allowances.forEach(builder::explicitAllowance);
        return builder.build();
    }

    private static Styleguide styleguide(
            String schema,
            String title,
            List<String> principles,
            Optional<List<StyleguidePattern>> patterns,
            Optional<List<StyleguideAntiPattern>> antiPatterns) {
        Styleguide.Builder builder = Styleguide.builder()
                .id(new StyleguideIdentifier("parity-style"))
                .packId(PACK)
                .schemaVersion(schema)
                .title(title)
                .scope(StyleguideScope.CODE);
        principles.forEach(builder::principle);
        patterns.ifPresent(builder::patterns);
        antiPatterns.ifPresent(builder::antiPatterns);
        return builder.build();
    }

    private static Paradigm paradigm(String schema, String name, String summary, String directiveRef) {
        return Paradigm.builder()
                .id(new ParadigmIdentifier("parity-paradigm"))
                .packId(PACK)
                .schemaVersion(schema)
                .name(name)
                .summary(summary)
                .directiveRef(directiveRef)
                .build();
    }

    private static final StyleguidePattern PATTERN =
            new StyleguidePattern("P", "a pattern", Optional.empty(), Optional.empty());
    private static final StyleguideAntiPattern ANTI = new StyleguideAntiPattern("A", "an anti-pattern", "bad", "good");

    /** A rejected build, the same build with the one value repaired, and the rule the rejection must name. */
    private record Case(String name, Supplier<Object> breach, Supplier<Object> control, String rule) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static Arguments row(String name, Supplier<Object> breach, Supplier<Object> control, String rule) {
        return Arguments.of(new Case(name, breach, control, rule));
    }

    static Stream<Arguments> rulesetCases() {
        List<String> allowance = List.of("spikes may skip it");
        return Stream.of(
                // directive: schema_version, title, intent, enforcement conditional, procedures
                row(
                        "directive: schema version other than 1.0",
                        () -> directive("2.0", "T", "I", Enforcement.REQUIRED, List.of(), "p"),
                        () -> directive("1.0", "T", "I", Enforcement.REQUIRED, List.of(), "p"),
                        "A directive needs a schema version of 1.0."),
                row(
                        "directive: blank title",
                        () -> directive("1.0", " ", "I", Enforcement.REQUIRED, List.of(), "p"),
                        () -> directive("1.0", "T", "I", Enforcement.REQUIRED, List.of(), "p"),
                        "A directive needs a title that is not blank."),
                row(
                        "directive: blank intent",
                        () -> directive("1.0", "T", "", Enforcement.REQUIRED, List.of(), "p"),
                        () -> directive("1.0", "T", "I", Enforcement.REQUIRED, List.of(), "p"),
                        "A directive needs an intent that is not blank."),
                row(
                        "directive: lenient-adherence without an explicit allowance",
                        () -> directive("1.0", "T", "I", Enforcement.LENIENT_ADHERENCE, List.of(), "p"),
                        () -> directive("1.0", "T", "I", Enforcement.LENIENT_ADHERENCE, allowance, "p"),
                        "A lenient-adherence directive needs at least one explicit allowance."),
                row(
                        "directive: blank procedure entry",
                        () -> directive("1.0", "T", "I", Enforcement.REQUIRED, List.of(), " "),
                        () -> directive("1.0", "T", "I", Enforcement.REQUIRED, List.of(), "p"),
                        "Every procedure of a directive must be non-blank."),
                // styleguide: schema_version, title, principles cardinality, patterns / anti_patterns non-empty when
                // present
                row(
                        "styleguide: schema version other than 1.0",
                        () -> styleguide("0.9", "T", List.of("p"), Optional.empty(), Optional.empty()),
                        () -> styleguide("1.0", "T", List.of("p"), Optional.empty(), Optional.empty()),
                        "A styleguide needs a schema version of 1.0."),
                row(
                        "styleguide: blank title",
                        () -> styleguide("1.0", "", List.of("p"), Optional.empty(), Optional.empty()),
                        () -> styleguide("1.0", "T", List.of("p"), Optional.empty(), Optional.empty()),
                        "A styleguide needs a title that is not blank."),
                row(
                        "styleguide: no principle",
                        () -> styleguide("1.0", "T", List.of(), Optional.empty(), Optional.empty()),
                        () -> styleguide("1.0", "T", List.of("p"), Optional.empty(), Optional.empty()),
                        "A styleguide needs at least one principle."),
                row(
                        "styleguide: present but empty patterns",
                        () -> styleguide("1.0", "T", List.of("p"), Optional.of(List.of()), Optional.empty()),
                        () -> styleguide("1.0", "T", List.of("p"), Optional.of(List.of(PATTERN)), Optional.empty()),
                        "A styleguide's patterns, when present, must not be empty."),
                row(
                        "styleguide: present but empty anti-patterns",
                        () -> styleguide("1.0", "T", List.of("p"), Optional.empty(), Optional.of(List.of())),
                        () -> styleguide("1.0", "T", List.of("p"), Optional.empty(), Optional.of(List.of(ANTI))),
                        "A styleguide's anti-patterns, when present, must not be empty."),
                // paradigm: schema_version, name, summary, directive refs
                row(
                        "paradigm: schema version other than 1.0",
                        () -> paradigm("3.0", "N", "S", "DIRECTIVE_001"),
                        () -> paradigm("1.0", "N", "S", "DIRECTIVE_001"),
                        "A paradigm needs a schema version of 1.0."),
                row(
                        "paradigm: blank name",
                        () -> paradigm("1.0", " ", "S", "DIRECTIVE_001"),
                        () -> paradigm("1.0", "N", "S", "DIRECTIVE_001"),
                        "A paradigm needs a name that is not blank."),
                row(
                        "paradigm: blank summary",
                        () -> paradigm("1.0", "N", "", "DIRECTIVE_001"),
                        () -> paradigm("1.0", "N", "S", "DIRECTIVE_001"),
                        "A paradigm needs a summary that is not blank."),
                row(
                        "paradigm: blank directive reference",
                        () -> paradigm("1.0", "N", "S", " "),
                        () -> paradigm("1.0", "N", "S", "DIRECTIVE_001"),
                        "A paradigm's directive references must not be blank."),
                // tactic (retrofit): steps cardinality, name
                row(
                        "tactic: no step",
                        () -> tactic("N", List.of()),
                        () -> tactic("N", List.of(new TacticStep("Only step", Optional.empty(), List.of(), List.of()))),
                        "A tactic needs at least one step."),
                row(
                        "tactic: blank name",
                        () -> tactic(" ", List.of(new TacticStep("Only step", Optional.empty(), List.of(), List.of()))),
                        () -> tactic("N", List.of(new TacticStep("Only step", Optional.empty(), List.of(), List.of()))),
                        "A tactic needs a name that is not blank."));
    }

    private static Tactic tactic(String name, List<TacticStep> steps) {
        Tactic.Builder builder = Tactic.builder()
                .id(new TacticIdentifier("parity-tactic"))
                .packId(PACK)
                .schemaVersion("1.0")
                .name(name);
        steps.forEach(builder::step);
        return builder.build();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rulesetCases")
    void aRulesetRejectsItsViolationAndAcceptsTheRepairedFixture(Case parity) {
        assertThatThrownBy(parity.breach()::get)
                .isInstanceOfSatisfying(
                        ConstraintBreachException.class,
                        breach -> assertThat(breach.constraintDescription()).isEqualTo(parity.rule()));

        assertThat(parity.control().get()).isNotNull();
    }

    @Test
    void identifiersEnforceTheirCanonicalPatterns() {
        assertThatThrownBy(() -> new DirectiveIdentifier("directive_001"))
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new StyleguideIdentifier("Not-Lower")).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new ParadigmIdentifier("9starts-with-digit"))
                .isInstanceOf(ConstraintBreachException.class);

        assertThat(new DirectiveIdentifier("DIRECTIVE_001").value()).isEqualTo("DIRECTIVE_001");
        assertThat(new StyleguideIdentifier("not-lower").value()).isEqualTo("not-lower");
        assertThat(new ParadigmIdentifier("starts-with-letter-9").value()).isEqualTo("starts-with-letter-9");
    }

    // ---- enums: a token outside the canonical set makes the file unreadable as an entity, a canonical token loads
    // ----

    @Test
    void anEnforcementTokenOutsideTheEnumIsNotLoadable(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-a", host);
        DomainRepositoryService service = serviceOver(pack);
        Directive directive = directive("1.0", "T", "I", Enforcement.REQUIRED, List.of(), "p");
        service.save(directive);

        assertThat(service.load(PACK, directive.id())).isPresent();

        Path file = onlyFile(pack, ".directive.yaml");
        replaceInFile(file, "enforcement: required", "enforcement: sometimes");
        assertThat(service.load(PACK, directive.id())).isEmpty();
    }

    @Test
    void aStyleguideScopeOutsideTheEnumIsNotLoadable(@TempDir Path host) throws IOException {
        Path pack = PackFixtures.copyOf("pack-a", host);
        DomainRepositoryService service = serviceOver(pack);
        Styleguide styleguide = styleguide("1.0", "T", List.of("p"), Optional.empty(), Optional.empty());
        service.save(styleguide);

        assertThat(service.load(PACK, styleguide.id())).isPresent();

        Path file = onlyFile(pack, ".styleguide.yaml");
        replaceInFile(file, "scope: code", "scope: vibes");
        assertThat(service.load(PACK, styleguide.id())).isEmpty();
    }

    private static DomainRepositoryService serviceOver(Path pack) {
        PackLocation location = new PackLocation("pack-a", PackLevel.ORG, pack);
        return new DomainRepositoryService(() -> List.of(location), new YamlSerializationStrategy());
    }

    private static Path onlyFile(Path pack, String suffix) throws IOException {
        try (Stream<Path> list = Files.list(pack)) {
            List<Path> files = list.filter(p -> p.getFileName().toString().endsWith(suffix))
                    .toList();
            assertThat(files).hasSize(1);
            return files.get(0);
        }
    }

    private static void replaceInFile(Path file, String from, String to) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(text).as("the saved file carries the token being replaced").contains(from);
        Files.writeString(file, text.replace(from, to), StandardCharsets.UTF_8);
    }
}
