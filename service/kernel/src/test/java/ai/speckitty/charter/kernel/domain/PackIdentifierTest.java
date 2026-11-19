package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** The pack-id grammar that {@link PackIdentifier} holds (ADR-0075 decision 2): {@code slug(63)}. */
@DisplayName("PackIdentifier: the pack-id grammar")
class PackIdentifierTest {

    @ParameterizedTest(name = "\"{0}\" is a pack id")
    @ValueSource(strings = {"acme", "1-pack", "spec-kitty-internal", "a0"})
    @DisplayName("a kebab slug, a digit first allowed, is a pack id")
    void accepted(String text) {
        assertThat(PackIdentifier.isPackId(text)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" is no pack id")
    @NullAndEmptySource
    @ValueSource(strings = {"a--b", "acme-", "-acme", "Acme", "acme_x", "acme.x", " "})
    @DisplayName("a doubled, leading or trailing hyphen, an upper-case letter or another character is no pack id")
    void refused(String text) {
        assertThat(PackIdentifier.isPackId(text)).isFalse();
    }

    @Test
    @DisplayName("a pack id is at most 63 characters")
    void cappedAt63() {
        assertThat(PackIdentifier.isPackId("p".repeat(63))).isTrue();
        assertThat(PackIdentifier.isPackId("p".repeat(64))).isFalse();
    }

    @Test
    @DisplayName("the constructor refuses a blank id in the type's own words")
    void blankIsRefused() {
        assertThatThrownBy(() -> new PackIdentifier(" "))
                .isInstanceOfSatisfying(
                        ConstraintBreachException.class,
                        breach ->
                                assertThat(breach.reason().detail()).isEqualTo("A pack identifier must not be blank."));
    }

    @ParameterizedTest(name = "the constructor refuses \"{0}\"")
    @ValueSource(
            strings = {
                "a--b",
                "-a",
                "a-",
                "A",
                "my_pack",
                "pppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppp",
                "01J0Z3Y7S8QK4W5E6R2T9V1N0M"
            })
    @DisplayName("the constructor refuses a text that is no pack id, with the grammar's detail")
    void constructorRefusesNoPackId(String text) {
        assertThatThrownBy(() -> new PackIdentifier(text))
                .isInstanceOfSatisfying(
                        ConstraintBreachException.class,
                        breach -> assertThat(breach.reason().detail()).isEqualTo(PackIdentifier.GRAMMAR_DETAIL));
    }

    @ParameterizedTest(name = "the constructor accepts \"{0}\"")
    @ValueSource(strings = {"a", "1a", "my-pack", "ppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppp"})
    @DisplayName("the constructor accepts a pack id, up to 63 characters")
    void constructorAcceptsPackId(String text) {
        assertThat(new PackIdentifier(text).value()).isEqualTo(text);
    }
}
