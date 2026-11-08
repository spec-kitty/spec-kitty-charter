package ai.speckitty.charter.packs.application;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Mints the id of a registration: 26 characters of the Crockford alphabet, a 48-bit millisecond time first and 80 random
 * bits after it, so the ids sort by acceptance and nobody can guess another client's status URI.
 */
final class Ulid {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int TIME_CHARS = 10;
    private static final int RANDOM_CHARS = 16;
    private static final int MASK = 31;
    private static final int BITS = 5;

    private Ulid() {}

    /**
     * The next id.
     *
     * @param now the time to stamp it with
     * @param random where its random part comes from
     * @return the id, matching {@code ^[0-9A-HJKMNP-TV-Z]{26}$}
     */
    static String next(Instant now, SecureRandom random) {
        char[] id = new char[TIME_CHARS + RANDOM_CHARS];
        long time = now.toEpochMilli();
        for (int i = TIME_CHARS - 1; i >= 0; i--) {
            id[i] = ALPHABET.charAt((int) (time & MASK));
            time >>>= BITS;
        }
        for (int i = TIME_CHARS; i < id.length; i++) {
            id[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(id);
    }
}
