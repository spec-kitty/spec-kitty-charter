package ai.speckitty.charter.activations.infra.storage;

import ai.speckitty.charter.activations.domain.ActivationId;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The name of an activation's directory in the file register: the id lower-cased, a {@code -}, then a lowercase
 * hexadecimal bitmap of the positions that were upper case (bit {@code i} for the character at {@code i}). {@code aBc}
 * is {@code abc-2}, {@code ABC} is {@code abc-7} and {@code abc} is {@code abc-0}.
 *
 * <p>The id grammar admits ids that differ only in case, and a case-insensitive file system (the macOS and Windows
 * defaults) would give two such ids one directory. Every name matches {@code ^[a-z0-9-]+$}, so folding it changes
 * nothing, and two ids that differ in case differ in their bitmaps. The encoding is reversible: the bitmap follows the
 * last {@code -} (it holds no {@code -} itself), and an id is at most 64 characters, so the bitmap fits one
 * {@code long}. It is preferred to folding the case away, which would make {@code ABC} and {@code abc} one activation.
 */
public final class ActivationDirectoryName {

    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}-[0-9a-f]{1,16}");

    private ActivationDirectoryName() {}

    /**
     * The directory name of an id.
     *
     * @param id the activation's id
     * @return its name, lowercase letters, digits and hyphens only
     */
    public static String encode(ActivationId id) {
        String value = id.value();
        long upper = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isUpperCase(value.charAt(i))) {
                upper |= 1L << i;
            }
        }
        return value.toLowerCase(Locale.ROOT) + "-" + Long.toHexString(upper);
    }

    /**
     * The id a directory name was encoded from.
     *
     * @param name the directory name
     * @return the id
     * @throws IllegalArgumentException when {@link #encode} writes no such name
     */
    public static ActivationId decode(String name) {
        return tryDecode(name)
                .orElseThrow(() -> new IllegalArgumentException("'" + name + "' is no activation directory name."));
    }

    /**
     * The id a directory name was encoded from, when {@link #encode} writes that name.
     *
     * @param name the directory name
     * @return the id; empty for a name the encoder never writes
     */
    static Optional<ActivationId> tryDecode(String name) {
        if (!NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        int split = name.lastIndexOf('-');
        String lower = name.substring(0, split);
        long upper = Long.parseUnsignedLong(name.substring(split + 1), 16);
        char[] chars = lower.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if ((upper >>> i & 1L) == 1L) {
                chars[i] = Character.toUpperCase(chars[i]);
            }
        }
        ActivationId id = new ActivationId(new String(chars));
        return encode(id).equals(name) ? Optional.of(id) : Optional.empty();
    }
}
