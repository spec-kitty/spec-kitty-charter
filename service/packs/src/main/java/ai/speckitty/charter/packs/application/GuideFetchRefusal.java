package ai.speckitty.charter.packs.application;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * A guide fetch the service refused (NFR-004). Every refusal is typed by a {@link Reason}; the message names the
 * host of the URI concerned and never its path or query, which may carry secrets.
 */
public final class GuideFetchRefusal extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Why a fetch was refused. */
    public enum Reason {
        /** The URI is not absolute HTTPS (initially or after a redirect). */
        NOT_HTTPS,
        /** The host is absent, not a plain DNS name, or not exactly on the allowlist (empty by default). */
        HOST_NOT_ALLOWED,
        /** The URI names a port other than 443 (initially or after a redirect). */
        PORT_NOT_ALLOWED,
        /** The URI carries user information. */
        USERINFO,
        /** The host is an IP literal rather than a name. */
        IP_LITERAL,
        /** A resolved address is not global unicast (loopback, private, link-local, ...). */
        NON_GLOBAL_ADDRESS,
        /** More redirects than allowed. */
        TOO_MANY_REDIRECTS,
        /** A 3xx that is not a followable redirect, or one without a {@code Location}. */
        REDIRECT_REFUSED,
        /** The whole exchange (resolution, every hop, the body) outran the deadline. */
        TIMEOUT,
        /** The body exceeds the size cap. */
        TOO_LARGE,
        /** The response carries a {@code Content-Encoding} other than {@code identity}. */
        ENCODED,
        /**
         * The response declares no {@code Content-Type}, or one other than {@code text/markdown},
         * {@code text/x-markdown} or {@code text/plain} (parameters such as {@code charset} aside).
         */
        CONTENT_TYPE,
        /** The declared charset is not UTF-8, or the body is not valid UTF-8. */
        NOT_UTF8,
        /** The final response is not 2xx. */
        HTTP_STATUS,
        /** The host could not be resolved or connected to. */
        UNREACHABLE
    }

    /** Why the fetch was refused. */
    private final Reason reason;

    /**
     * A refusal.
     *
     * @param reason why the fetch was refused
     * @param host the host of the URI concerned, or {@code null} when the URI has none
     * @param detail what was refused, free of the URI's path and query
     */
    public GuideFetchRefusal(Reason reason, @Nullable String host, String detail) {
        super("guide fetch refused (" + reason + ") for host " + (host == null ? "<none>" : host) + ": " + detail);
        this.reason = reason;
    }

    /**
     * Why the fetch was refused.
     *
     * @return the reason
     */
    public Reason reason() {
        return reason;
    }
}
