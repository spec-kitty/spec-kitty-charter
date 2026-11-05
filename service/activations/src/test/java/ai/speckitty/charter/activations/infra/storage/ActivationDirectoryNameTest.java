package ai.speckitty.charter.activations.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationId;
import java.util.Locale;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The directory name of an activation id (T106): two ids that differ only in case get names that differ on a
 * case-insensitive file system too, and every name decodes back to its id.
 */
class ActivationDirectoryNameTest {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-";

    @Test
    void caseVariantsGetNamesThatDifferEvenWhenLowerCased() {
        String upper = ActivationDirectoryName.encode(new ActivationId("ABC"));
        String lower = ActivationDirectoryName.encode(new ActivationId("abc"));

        assertThat(upper.toLowerCase(Locale.ROOT)).isNotEqualTo(lower.toLowerCase(Locale.ROOT));
    }

    @Test
    void theNameIsTheLowerCasedIdAndAHexBitmapOfItsUpperCaseLetters() {
        assertThat(ActivationDirectoryName.encode(new ActivationId("aBc"))).isEqualTo("abc-2");
        assertThat(ActivationDirectoryName.encode(new ActivationId("abc"))).isEqualTo("abc-0");
        assertThat(ActivationDirectoryName.encode(new ActivationId("01M4P01D5T")))
                .isEqualTo("01m4p01d5t-" + Integer.toHexString((1 << 2) | (1 << 4) | (1 << 7) | (1 << 9)));
    }

    @Test
    void everyGeneratedIdRoundTripsAndItsNameIsLowerCaseLettersDigitsAndHyphens() {
        Random random = new Random(360);
        IntStream.range(0, 2_000).forEach(n -> {
            ActivationId id = randomId(random);
            String name = ActivationDirectoryName.encode(id);

            assertThat(name).matches("^[a-z0-9-]+$");
            assertThat(ActivationDirectoryName.decode(name)).isEqualTo(id);
        });
    }

    @Test
    void aSixtyFourCharacterIdOfCapitalsRoundTrips() {
        ActivationId id = new ActivationId("A".repeat(64));

        assertThat(ActivationDirectoryName.encode(id)).endsWith("-ffffffffffffffff");
        assertThat(ActivationDirectoryName.decode(ActivationDirectoryName.encode(id)))
                .isEqualTo(id);
    }

    @Test
    void aNameTheEncoderNeverWritesDecodesToNothing() {
        assertThat(ActivationDirectoryName.tryDecode("abc")).as("no bitmap").isEmpty();
        assertThat(ActivationDirectoryName.tryDecode("ABC-0")).as("upper case").isEmpty();
        assertThat(ActivationDirectoryName.tryDecode("a1-2"))
                .as("bit on a digit")
                .isEmpty();
        assertThat(ActivationDirectoryName.tryDecode("abc-8"))
                .as("bit past the end")
                .isEmpty();
        assertThat(ActivationDirectoryName.tryDecode("abc-02"))
                .as("not the canonical bitmap")
                .isEmpty();
        assertThatThrownBy(() -> ActivationDirectoryName.decode("abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("abc");
    }

    private static ActivationId randomId(Random random) {
        int length = 1 + random.nextInt(64);
        StringBuilder id = new StringBuilder();
        id.append(ALPHABET.charAt(random.nextInt(ALPHABET.length() - 1)));
        while (id.length() < length) {
            id.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new ActivationId(id.toString());
    }
}
