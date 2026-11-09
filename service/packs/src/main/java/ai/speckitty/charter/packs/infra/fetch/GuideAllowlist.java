package ai.speckitty.charter.packs.infra.fetch;

import java.net.IDN;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The hosts a guide may be fetched from ({@code charter.guide-fetch.allowed-hosts}), matched exactly after
 * normalisation: lower-cased ({@link Locale#ROOT}) with one trailing dot stripped. No wildcard, no suffix match: a
 * redirect from {@code github.com} to {@code raw.githubusercontent.com} needs both hosts listed.
 *
 * <p>A configured entry is checked when it is bound, so a mistake fails the start instead of never matching: it must
 * be a plain host name (letters, digits and hyphens in dot-separated labels), not a URI, not {@code host:port}, not a
 * wildcard and not an IP literal. A Unicode host name is listed in its ASCII ({@code xn--}) form, the form a
 * URI's host takes; a Unicode entry is converted to it.
 *
 * @param hosts the normalised hosts; empty refuses every host
 */
public record GuideAllowlist(Set<String> hosts) {

    /** A host name of LDH labels: each 1 to 63 letters, digits and inner hyphens, at most 253 characters in all. */
    private static final Pattern HOST_NAME =
            Pattern.compile("(?=.{1,253}$)[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*");

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
     * @throws IllegalArgumentException naming the first entry that is not a plain host name
     */
    public static GuideAllowlist of(Collection<String> configured) {
        return new GuideAllowlist(configured.stream()
                .map(GuideAllowlist::normalise)
                .filter(host -> !host.isEmpty())
                .map(GuideAllowlist::checked)
                .collect(Collectors.toUnmodifiableSet()));
    }

    /** A normalised entry in its ASCII form, or a refusal naming it. */
    private static String checked(String entry) {
        String ascii;
        try {
            ascii = IDN.toASCII(entry, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException invalid) {
            throw notAHostName(entry, "it is not a valid host name");
        }
        if (!HOST_NAME.matcher(ascii).matches()) {
            throw notAHostName(entry, "only a plain host name is listed: no scheme, port, path or wildcard");
        }
        if (HttpsGuideFetcher.isIpLiteral(ascii)) {
            throw notAHostName(entry, "an IP literal is never fetched from");
        }
        return ascii;
    }

    private static IllegalArgumentException notAHostName(String entry, String why) {
        return new IllegalArgumentException(
                "charter.guide-fetch.allowed-hosts entry '" + entry + "' is not a plain host name; " + why);
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
