package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The id of a registration is a ULID the status path's grammar accepts, sorted by time and not guessable. */
class UlidTest {

    private static final String GRAMMAR = "^[0-9A-HJKMNP-TV-Z]{26}$";

    @Test
    void anIdMatchesTheGrammarOfTheStatusPath() {
        SecureRandom random = new SecureRandom();

        for (int i = 0; i < 200; i++) {
            assertThat(Ulid.next(Instant.parse("2026-11-07T10:00:00Z"), random)).matches(GRAMMAR);
        }
    }

    @Test
    void idsSortByTimeAndDifferWithinOneMillisecond() {
        SecureRandom random = new SecureRandom();
        Instant now = Instant.parse("2026-11-07T10:00:00Z");

        String earlier = Ulid.next(now, random);
        String later = Ulid.next(now.plusMillis(1), random);
        Set<String> sameInstant = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            sameInstant.add(Ulid.next(now, random));
        }

        assertThat(earlier.substring(0, 10)).isLessThan(later.substring(0, 10));
        assertThat(sameInstant).as("80 random bits: no collision in 500").hasSize(500);
    }
}
