package ai.speckitty.charter.packs.infra.fetch;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The hosts a guide may be fetched from ({@code charter.guide-fetch.allowed-hosts}), matched exactly after
 * normalisation: lower-cased ({@link Locale#ROOT}) with one trailing dot stripped. No wildcard, no suffix match: a
 * redirect from {@code github.com} to {@code raw.githubusercontent.com} needs both hosts listed.
 *
 * @param hosts the normalised hosts; empty refuses every host
 */
public record GuideAllowlist(Set<String> hosts) {

    /** The default: no host is allowed. */
    public static final GuideAllowlist EMPTY = new GuideAllowlist(Set.of());

    /**
     * An allowlist.
     *
     * @param hosts the normalised hosts; copied
     */
    public GuideAllowlist {
        hosts = Set.copyOf(hosts);
    }

    /**
     * The allowlist of the configured host names, normalised; blank entries are dropped.
     *
     * @param configured host names as configured
     * @return the allowlist
     */
    public static GuideAllowlist of(Collection<String> configured) {
        return new GuideAllowlist(configured.stream()
                .map(GuideAllowlist::normalise)
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet()));
    }

    /**
     * Whether the host is on the allowlist.
     *
     * @param host a host name as found in a URI or a lookup, or {@code null}
     * @return {@code true} when its normalised form is listed
     */
    public boolean allows(@Nullable String host) {
        return host != null && hosts.contains(normalise(host));
    }

    /**
     * Lower-cases a host name and strips one trailing dot.
     *
     * @param host a host name
     * @return its normalised form
     */
    public static String normalise(String host) {
        String lower = host.strip().toLowerCase(Locale.ROOT);
        return lower.endsWith(".") ? lower.substring(0, lower.length() - 1) : lower;
    }
}
