package ai.speckitty.charter.packs.infra.fetch.git;

import java.util.List;
import java.util.Map;

/**
 * What a remote adds to every git run: configuration pins after the fixed ones of {@code GitCommand}, and environment
 * variables over the fixed ones. The https remote adds the pin of the checked address ({@code http.curloptResolve}); a
 * test remote adds the transport of its own repository, which is how the production policy (https only) is shown to
 * refuse the others.
 *
 * @param pins {@code key=value} pairs given with {@code -c}
 * @param environment variables set after the environment built from empty
 */
public record GitTransport(List<String> pins, Map<String, String> environment) {

    /** Adds nothing. */
    public static final GitTransport NONE = new GitTransport(List.of(), Map.of());

    /**
     * Copies the members.
     *
     * @param pins {@code key=value} pairs given with {@code -c}
     * @param environment variables set after the environment built from empty
     */
    public GitTransport {
        pins = List.copyOf(pins);
        environment = Map.copyOf(environment);
    }
}
