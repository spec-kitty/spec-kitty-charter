package ai.speckitty.charter.kernel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** The descriptor identity {@link PackUlid} holds (#583): the ULID of {@code pack-descriptor.schema.json}. */
@DisplayName("PackUlid: the descriptor's pack_id")
class PackUlidTest {

    private static final String ULID = "01J6XW9KQT7M0YB3N4R5CQZ2EX";

    @ParameterizedTest(name = "\"{0}\" is a ULID")
    @ValueSource(strings = {ULID, "00000000000000000000000000", "7ZZZZZZZZZZZZZZZZZZZZZZZZZ"})
    @DisplayName("26 characters of upper-case Crockford base32 are accepted")
    void accepted(String text) {
        assertThat(PackUlid.isUlid(text)).isTrue();
        assertThat(new PackUlid(text).value()).isEqualTo(text);
    }

    @ParameterizedTest(name = "\"{0}\" is no ULID")
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "01PACK",
                "01J6XW9KQT7M0YB3N4R5CQZ2E",
                "01J6XW9KQT7M0YB3N4R5CQZ2EXX",
                "01J6XW9KQT7M0YB3N4R5CQZ2EI",
                "01J6XW9KQT7M0YB3N4R5CQZ2EL",
                "01J6XW9KQT7M0YB3N4R5CQZ2EO",
                "01J6XW9KQT7M0YB3N4R5CQZ2EU",
                "01j6xw9kqt7m0yb3n4r5cqz2ex",
                "my-pack",
                " "
            })
    @DisplayName("a short, long or lower-case text, or one holding I, L, O or U, is no ULID")
    void refused(String text) {
        assertThat(PackUlid.isUlid(text)).isFalse();
        assertThatThrownBy(() -> new PackUlid(text))
                .isInstanceOfSatisfying(
                        ConstraintBreachException.class,
                        breach -> assertThat(breach.reason().detail()).isEqualTo(PackUlid.GRAMMAR_DETAIL));
    }

    @Test
    @DisplayName("two descriptor identities are equal by value")
    void equalByValue() {
        assertThat(new PackUlid(ULID)).isEqualTo(new PackUlid(ULID));
    }
}
