package ai.speckitty.charter.missions.application;

import java.util.Objects;
import java.util.Optional;

/**
 * The one-segment name of a skeleton (D9): its path under the mission type's {@code templates/} directory, escaped as
 * in RFC 6901. Naming replaces {@code ~} with {@code ~0} first, then {@code /} with {@code ~1}; decoding replaces
 * {@code ~1} with {@code /}, then {@code ~0} with {@code ~}. The mapping is one to one, so {@code a~1b.md} is named
 * {@code a~01b.md} and never confused with the nested {@code a/b.md}, named {@code a~1b.md}.
 */
public final class SkeletonNames {

    private SkeletonNames() {}

    /**
     * The name of a skeleton.
     *
     * @param path the skeleton's path under the type's {@code templates/} directory, for example
     *     {@code divio/howto-template.md}
     * @return its one-segment name, for example {@code divio~1howto-template.md}
     */
    public static String encode(String path) {
        return Objects.requireNonNull(path, "path").replace("~", "~0").replace("/", "~1");
    }

    /**
     * The path a name stands for.
     *
     * @param name a one-segment name
     * @return the path under the type's {@code templates/} directory; empty when the name is no escaping of a path: it
     *     is empty, holds a {@code /}, or holds a {@code ~} followed by anything but {@code 0} or {@code 1}
     */
    public static Optional<String> decode(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty() || name.indexOf('/') >= 0) {
            return Optional.empty();
        }
        for (int at = name.indexOf('~'); at >= 0; at = name.indexOf('~', at + 2)) {
            if (at + 1 >= name.length() || (name.charAt(at + 1) != '0' && name.charAt(at + 1) != '1')) {
                return Optional.empty();
            }
        }
        return Optional.of(name.replace("~1", "/").replace("~0", "~"));
    }
}
