package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ActivationIdMintTest {

    private static final String ULID = "[0-9A-HJKMNP-TV-Z]{26}";

    private static ActivationIdMint at(long millis, long seed) {
        return new ActivationIdMint(Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC), new Random(seed));
    }

    @Test
    void anIdIsTwentySixCrockfordCharacters() {
        assertThat(new ActivationIdMint().mint().value()).matches(ULID);
    }

    @Test
    void theTimeLeadsInBase32() {
        // 1 ms is the last of the ten time characters; the epoch is all zeros.
        assertThat(at(0, 1).mint().value()).startsWith("0000000000");
        assertThat(at(1, 1).mint().value()).startsWith("0000000001");
        assertThat(at(32, 1).mint().value()).startsWith("0000000010");
        // The ULID specification's own example time, 1469918176385 ms, is 01ARYZ6S41.
        assertThat(at(1_469_918_176_385L, 1).mint().value()).startsWith("01ARYZ6S41");
    }

    @Test
    void anIdMintedLaterSortsLater() {
        String earlier = at(1_700_000_000_000L, 7).mint().value();
        String later = at(1_700_000_000_001L, 3).mint().value();

        assertThat(later).isGreaterThan(earlier);
    }

    @Test
    void theRandomPartDiffersFromIdToId() {
        ActivationIdMint mint = new ActivationIdMint();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            ids.add(mint.mint().value());
        }
        assertThat(ids).hasSize(1_000);
    }

    @Test
    void theRandomPartIsTheEightyBitsOfTheSource() {
        Random zeros = new Random() {
            @Override
            public void nextBytes(byte[] bytes) {
                java.util.Arrays.fill(bytes, (byte) 0);
            }
        };
        Random ones = new Random() {
            @Override
            public void nextBytes(byte[] bytes) {
                java.util.Arrays.fill(bytes, (byte) 0xFF);
            }
        };
        Clock epoch = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

        assertThat(new ActivationIdMint(epoch, zeros).mint().value()).isEqualTo("0".repeat(26));
        assertThat(new ActivationIdMint(epoch, ones).mint().value()).isEqualTo("0".repeat(10) + "Z".repeat(16));
    }

    @Test
    void aClockOutsideFortyEightBitsIsRefused() {
        assertThatThrownBy(() -> at(1L << 48, 1).mint()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> at(-1, 1).mint()).isInstanceOf(IllegalStateException.class);
    }
}
