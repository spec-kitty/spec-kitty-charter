package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The anti-pattern entity (ADR-0052 decision 8, as amended by ADR-0053 decision 30; #154). */
@DisplayName("AntiPattern entity")
class AntiPatternTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack");

    private static AntiPattern.Builder valid(String id) {
        return AntiPattern.builder()
                .id(new AntiPatternIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Title of " + id)
                .description("Description of " + id)
                .severity(AntiPatternSeverity.SMELL)
                .badExample("bad " + id);
    }

    @Test
    @DisplayName("a valid anti-pattern builds as a charter artifact of kind anti_pattern, URN anti_pattern:<id>, with"
            + " no derived relationship: its edges are the graph's (ADR-0064)")
    void aValidAntiPatternBuilds() {
        AntiPattern antiPattern = valid("clever-over-direct").build();

        assertThat(antiPattern.kind()).isEqualTo(ArtifactKind.ANTI_PATTERN);
        assertThat(antiPattern.localId()).isEqualTo("clever-over-direct");
        assertThat(antiPattern.urn().toString()).isEqualTo("anti_pattern:clever-over-direct");
        assertThat(antiPattern.packId()).isEqualTo(PACK);
        assertThat(antiPattern.schemaVersion()).contains("1.0");
        assertThat(antiPattern.title()).isEqualTo("Title of clever-over-direct");
        assertThat(antiPattern.label()).isEqualTo("Title of clever-over-direct");
        assertThat(antiPattern.description()).contains("Description of clever-over-direct");
        assertThat(antiPattern.severity()).isEqualTo(AntiPatternSeverity.SMELL);
        assertThat(antiPattern.badExample()).isEqualTo("bad clever-over-direct");
        assertThat(antiPattern.derivedRelationships()).isEmpty();
    }

    @Test
    @DisplayName("good_example is optional")
    void theGoodExampleIsOptional() {
        assertThat(valid("a").build().goodExample()).isEmpty();
        assertThat(valid("a").goodExample("good").build().goodExample()).isEqualTo(Optional.of("good"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n"})
    @DisplayName("a blank title, description or bad_example breaches a rule")
    void aBlankRequiredTextBreaches(String blank) {
        assertThatThrownBy(() -> valid("a").title(blank).build()).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> valid("a").description(blank).build()).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> valid("a").badExample(blank).build()).isInstanceOf(ConstraintBreachException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.0", "1", "1.0.0", ""})
    @DisplayName("a schema version other than 1.0 breaches a rule")
    void anotherSchemaVersionBreaches(String version) {
        assertThatThrownBy(() -> valid("a").schemaVersion(version).build())
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("the severity is required: there is no default (ADR-0053 decision 32)")
    void theSeverityIsRequired() {
        AntiPattern.Builder builder = AntiPattern.builder()
                .id(new AntiPatternIdentifier("a"))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("t")
                .description("d")
                .badExample("b");

        assertThatThrownBy(builder::build)
                .isInstanceOf(NullPointerException.class)
                .hasMessage("severity");
    }

    @Test
    @DisplayName("an identifier that is not the letter-first slug of at most 64 breaches a rule")
    void aMalformedIdentifierBreaches() {
        assertThatThrownBy(() -> new AntiPatternIdentifier("Not-Kebab")).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new AntiPatternIdentifier("1st")).isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> new AntiPatternIdentifier("a".repeat(65)))
                .isInstanceOf(ConstraintBreachException.class);
        assertThat(new AntiPatternIdentifier("a".repeat(64)).value()).hasSize(64);
    }

    @Test
    @DisplayName("two anti-patterns with the same identity are equal, whatever their other fields hold")
    void identityEquality() {
        AntiPattern one = valid("a").build();

        assertThat(one)
                .isEqualTo(valid("a")
                        .title("Other")
                        .severity(AntiPatternSeverity.BREAKING)
                        .build())
                .hasSameHashCodeAs(valid("a").build());
        assertThat(one).isNotEqualTo(valid("b").build());
    }

    @Test
    @DisplayName("the severity is a closed enum of smell, severe and breaking, parsed from its token")
    void theSeverityTokens() {
        assertThat(AntiPatternSeverity.values())
                .extracting(AntiPatternSeverity::token)
                .containsExactly("smell", "severe", "breaking");
        assertThat(AntiPatternSeverity.fromToken("severe")).contains(AntiPatternSeverity.SEVERE);
        assertThat(AntiPatternSeverity.fromToken("minor")).isEmpty();
        assertThat(AntiPatternSeverity.fromToken("SMELL")).isEmpty();
    }

    @Test
    @DisplayName("a pack holds its anti-patterns, read back through antiPatterns()")
    void aPackHoldsItsAntiPatterns() {
        Pack pack = Pack.builder()
                .id(PACK)
                .descriptor(new PackDescriptor(
                        "01PACK00000000000000000000",
                        "1.0.0",
                        Optional.empty(),
                        "demo",
                        Optional.empty(),
                        Optional.empty()))
                .antiPattern(valid("b").build())
                .antiPattern(valid("a").build())
                .build();

        assertThat(pack.antiPatterns()).extracting(AntiPattern::localId).containsExactlyInAnyOrder("a", "b");
    }
}
