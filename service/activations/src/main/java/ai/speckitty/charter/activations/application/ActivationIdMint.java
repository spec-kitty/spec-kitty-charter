package ai.speckitty.charter.activations.application;

import ai.speckitty.charter.activations.domain.ActivationId;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Mints the id of an activation a client creates with {@code POST} (ADR-0020 D3): a ULID, written in-house so no
 * library is added for 26 characters. A ULID is 48 bits of Unix-epoch milliseconds followed by 80 random bits, in
 * Crockford base32 (no I, L, O or U), 26 characters; the time comes first, so ids minted later sort later at the
 * millisecond. Ids minted in one millisecond are ordered by chance only: nothing reads an order into an id.
 */
@Component
public class ActivationIdMint {

    /** Crockford's base32 alphabet. */
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private static final int LENGTH = 26;
    private static final int TIME_CHARS = 10;
    private static final int BITS_PER_CHAR = 5;
    private static final int RANDOM_BYTES = 10;
    private static final long MAX_TIME = (1L << 48) - 1;

    private final Clock clock;
    private final Random random;

    /** A mint over the system clock and a {@link SecureRandom}. */
    @Autowired
    public ActivationIdMint() {
        this(Clock.systemUTC(), new SecureRandom());
    }

    /**
     * A mint over a given clock and source of randomness, for a test.
     *
     * @param clock the clock whose milliseconds lead the id
     * @param random the source of the 80 random bits
     */
    ActivationIdMint(Clock clock, Random random) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * A new id.
     *
     * @return a ULID, 26 Crockford base32 characters
     */
    public ActivationId mint() {
        return ActivationId.mint(this::ulid);
    }

    private String ulid() {
        long time = clock.millis();
        if (time < 0 || time > MAX_TIME) {
            throw new IllegalStateException("The clock reads " + time + " ms, outside the 48 bits a ULID holds.");
        }
        byte[] entropy = new byte[RANDOM_BYTES];
        random.nextBytes(entropy);
        char[] text = new char[LENGTH];
        for (int i = TIME_CHARS - 1; i >= 0; i--) {
            text[i] = ALPHABET[(int) (time & 0x1F)];
            time >>>= BITS_PER_CHAR;
        }
        for (int i = 0; i < LENGTH - TIME_CHARS; i++) {
            text[TIME_CHARS + i] = ALPHABET[fiveBits(entropy, i * BITS_PER_CHAR)];
        }
        return new String(text);
    }

    /** The five bits of {@code bytes} starting at bit {@code offset}, most significant first. */
    private static int fiveBits(byte[] bytes, int offset) {
        int value = 0;
        for (int bit = offset; bit < offset + BITS_PER_CHAR; bit++) {
            int b = bytes[bit / Byte.SIZE] >> (Byte.SIZE - 1 - bit % Byte.SIZE) & 1;
            value = value << 1 | b;
        }
        return value;
    }
}
