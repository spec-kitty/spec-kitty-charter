package ai.speckitty.charter.packs.infra.fetch;

import ai.speckitty.charter.packs.application.GuideFetchRefusal;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpHeaders;

/**
 * The guarded HTTPS exchange as the fetchers use it (NFR-004): one URI, one body policy, and either the bytes and
 * headers of a success or a typed refusal. {@link GuardedHttpsExchange} is the production implementation; the contract
 * scenarios substitute one that opens no socket.
 */
@FunctionalInterface
public interface HttpsExchange {

    /**
     * What a body may be: its size cap and what the request asks for.
     *
     * @param maxBytes the largest body accepted
     * @param accept the {@code Accept} header value of the request
     */
    record BodyPolicy(long maxBytes, String accept) {

        /**
         * Checks the cap.
         *
         * @param maxBytes the largest body accepted; positive and below 2 GiB
         * @param accept the {@code Accept} header value of the request
         * @throws IllegalArgumentException when the cap is not positive or is 2 GiB or more
         */
        public BodyPolicy {
            if (maxBytes <= 0 || maxBytes > Integer.MAX_VALUE - 8) {
                throw new IllegalArgumentException("the body size cap must be positive and below 2 GiB");
            }
        }
    }

    /** A successful exchange: where the body came from, its headers and its bytes. */
    final class Fetched {

        private final String host;
        private final HttpHeaders headers;
        private final byte[] body;

        /**
         * A successful exchange.
         *
         * @param host the normalised host the body came from, after any followed redirect
         * @param headers the response headers
         * @param body the body, at most the policy's cap; handed over, and not changed by the caller afterwards
         */
        public Fetched(String host, HttpHeaders headers, byte[] body) {
            this.host = host;
            this.headers = headers;
            this.body = body;
        }

        /**
         * The host.
         *
         * @return the normalised host the body came from
         */
        public String host() {
            return host;
        }

        /**
         * The response headers.
         *
         * @return the headers
         */
        public HttpHeaders headers() {
            return headers;
        }

        /**
         * The body, read from the start.
         *
         * @return a new stream over the bytes
         */
        public InputStream body() {
            return new ByteArrayInputStream(body);
        }

        /**
         * The size of the body.
         *
         * @return the number of bytes
         */
        public int length() {
            return body.length;
        }

        /**
         * A copy of the body, for small bodies and tests.
         *
         * @return the bytes
         */
        public byte[] toByteArray() {
            return body.clone();
        }
    }

    /**
     * Fetches the URI under the policy.
     *
     * @param uri the location; only an HTTPS URI whose host is on the allowlist is fetched
     * @param policy what the body may be
     * @return the final successful response
     * @throws GuideFetchRefusal when the URI or anything the exchange meets is refused; nothing was fetched
     */
    Fetched fetch(URI uri, BodyPolicy policy) throws GuideFetchRefusal;
}
