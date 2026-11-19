package ai.speckitty.charter.packs.fixtures;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackUlid;
import java.util.Locale;

/**
 * Descriptor {@code pack_id}s for test fixtures: a real ULID that a reader of the test can still tell by its mnemonic.
 * The mnemonic is upper-cased, each letter Crockford base32 leaves out is read as Crockford reads it ({@code I} and
 * {@code L} as {@code 1}, {@code O} as {@code 0}, {@code U} as {@code V}), any other character is {@code 0}, and the
 * text is prefixed {@code 01} and padded with {@code Z} to 26 characters: {@code left} is
 * {@code 011EFTZZZZZZZZZZZZZZZZZZZZ}.
 */
public final class FixtureUlids {

    private static final int LENGTH = 26;
    private static final String PREFIX = "01";

    private FixtureUlids() {}

    /**
     * The ULID text of a mnemonic.
     *
     * @param mnemonic the mnemonic, at most 24 characters
     * @return the ULID text
     * @throws IllegalArgumentException when the mnemonic is longer than 24 characters
     */
    public static String of(String mnemonic) {
        if (mnemonic.length() > LENGTH - PREFIX.length()) {
            throw new IllegalArgumentException("a fixture ULID mnemonic has at most 24 characters: " + mnemonic);
        }
        StringBuilder text = new StringBuilder(PREFIX);
        String upper = mnemonic.toUpperCase(Locale.ROOT);
        for (int i = 0; i < upper.length(); i++) {
            text.append(crockford(upper.charAt(i)));
        }
        while (text.length() < LENGTH) {
            text.append('Z');
        }
        return text.toString();
    }

    /**
     * The descriptor identity of a mnemonic ({@link #of}).
     *
     * @param mnemonic the mnemonic
     * @return the identity
     */
    public static PackUlid ulid(String mnemonic) {
        return new PackUlid(of(mnemonic));
    }

    /**
     * The ULID text of a mnemonic, in an encoding that keeps the order of the mnemonics: a fixture whose packs are ordered
     * by an id written in this encoding orders them as it did by the mnemonic. The mnemonic is upper case, digits and
     * {@code _}; each letter Crockford base32 leaves out is written as the letter before it and {@code Z} ({@code I} as
     * {@code HZ}, {@code L} as {@code KZ}, {@code O} as {@code NZ}, {@code U} as {@code TZ}), {@code _} as {@code ZZ}
     * and {@code 0} as {@code 01}; the text is prefixed {@code 01} and padded with {@code 0}: {@code LEFT} is
     * {@code 01KZEFT0000000000000000000}.
     *
     * @param mnemonic the mnemonic
     * @return the ULID text
     * @throws IllegalArgumentException when the mnemonic does not fit in 26 characters, or holds another character
     */
    public static String ordered(String mnemonic) {
        StringBuilder text = new StringBuilder(PREFIX);
        for (int i = 0; i < mnemonic.length(); i++) {
            char c = mnemonic.charAt(i);
            text.append(
                    switch (c) {
                        case 'I' -> "HZ";
                        case 'L' -> "KZ";
                        case 'O' -> "NZ";
                        case 'U' -> "TZ";
                        case '_' -> "ZZ";
                        case '0' -> "01";
                        default -> {
                            if (!((c >= '1' && c <= '9') || (c >= 'A' && c <= 'Z'))) {
                                throw new IllegalArgumentException("an ordered mnemonic is upper case: " + mnemonic);
                            }
                            yield String.valueOf(c);
                        }
                    });
        }
        if (text.length() > LENGTH) {
            throw new IllegalArgumentException("an ordered mnemonic does not fit in a ULID: " + mnemonic);
        }
        while (text.length() < LENGTH) {
            text.append('0');
        }
        return text.toString();
    }

    /**
     * The registered id a fixture configures a pack under when the test knows the pack by its descriptor ULID: the
     * ULID in lower case. It is a pack id, distinct from the ULID, and it sorts as the ULID does, so an order by
     * registered id is the order the test wrote by descriptor id. A text that is already a pack id is its own
     * registered id.
     *
     * @param descriptorPackId the descriptor's {@code pack_id}, or a pack id
     * @return the registered id
     */
    public static PackIdentifier registeredIdOf(String descriptorPackId) {
        return new PackIdentifier(descriptorPackId.toLowerCase(Locale.ROOT));
    }

    private static char crockford(char c) {
        return switch (c) {
            case 'I', 'L' -> '1';
            case 'O' -> '0';
            case 'U' -> 'V';
            default -> (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') ? c : '0';
        };
    }
}
