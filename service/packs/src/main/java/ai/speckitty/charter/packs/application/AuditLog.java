package ai.speckitty.charter.packs.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The audit trail of registration and {@code DELETE} attempts (NFR-010, IC-18): a port the composition root binds to a
 * structured log by default. Every attempt writes exactly one {@link Entry}, accepted or refused, whichever answer
 * the client got.
 */
@FunctionalInterface
public interface AuditLog {

    /**
     * What the request presented as its credential. The service has no principal: every request needs the worktree
     * token, which the guard checks, so the only identity a record can name is the worktree and whether a token came.
     */
    enum Credential {
        /** The request carried the worktree bearer token the guard checked. */
        WORKTREE_TOKEN("worktree-token"),
        /** The guard runs without a token (the development flag), or the request carried none. */
        NONE("none"),
        /** The request presented a credential and the guard refused it. */
        REJECTED("rejected");

        private final String wire;

        Credential(String wire) {
            this.wire = wire;
        }

        /**
         * The value in the record.
         *
         * @return the token the log line carries
         */
        public String wire() {
            return wire;
        }
    }

    /**
     * One attempt. Every text is cleaned at construction (control, line-separator and format characters escaped, cut to
     * {@value #MAX_VALUE_BYTES} bytes), because a pack id and a locator come from the request.
     *
     * @param at when the attempt ended
     * @param worktree the {@code Charter-Worktree} identity the service serves
     * @param credential what the request carried
     * @param operation the operation: {@code registerPackFromArchive}, {@code registerPackFromSource},
     *     {@code deletePackArtifact} or {@code deletePackRelationship}
     * @param packId the target pack as the request named it; empty when the request did not get so far
     * @param sourceKind {@code archive}, {@code https_archive}, {@code git} or {@code filesystem}; empty when unknown
     * @param locator the source's locator with its query cut; empty for an archive upload
     * @param outcome how the attempt ended: {@code succeeded}, {@code refused} or {@code failed}, with the status or the
     *     code after a colon
     * @param revisionBefore the stored revision before the attempt; empty when no pack was stored
     * @param revisionAfter the stored revision after it; empty when no pack is stored
     */
    record Entry(
            Instant at,
            String worktree,
            Credential credential,
            String operation,
            String packId,
            String sourceKind,
            @Nullable String locator,
            String outcome,
            @Nullable String revisionBefore,
            String revisionAfter) {

        /** The longest value, in UTF-8 bytes, a record carries. */
        public static final int MAX_VALUE_BYTES = 256;

        private static final String INVALID = "invalid";
        private static final int LINE_SEPARATOR = 0x2028;
        private static final int PARAGRAPH_SEPARATOR = 0x2029;

        /**
         * Checks the fields and cleans the texts.
         *
         * @param at when the attempt ended
         * @param worktree the worktree identity
         * @param credential what the request carried
         * @param operation the operation
         * @param packId the target pack
         * @param sourceKind the source kind
         * @param locator the locator
         * @param outcome how it ended
         * @param revisionBefore the revision before
         * @param revisionAfter the revision after
         */
        public Entry {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(credential, "credential");
            worktree = clean(worktree);
            operation = clean(operation);
            packId = clean(packId);
            sourceKind = clean(sourceKind);
            locator = clean(locator);
            outcome = clean(outcome);
            revisionBefore = clean(revisionBefore);
            revisionAfter = clean(revisionAfter);
        }

        /**
         * The locator of an {@code https} source: scheme, host, port and path, with no user information, query or
         * fragment, so a token in a query never reaches the log.
         *
         * @param url the URL as sent
         * @return the cut locator; {@code invalid} when the text is no URL
         */
        public static String locatorOf(String url) {
            try {
                URI parsed = new URI(url);
                String host = parsed.getHost();
                if (!"https".equals(parsed.getScheme()) || host == null) {
                    return INVALID;
                }
                String port = parsed.getPort() < 0 ? "" : ":" + parsed.getPort();
                String path = parsed.getRawPath() == null ? "" : parsed.getRawPath();
                return "https://" + host + port + path;
            } catch (URISyntaxException notAUrl) {
                return INVALID;
            }
        }

        /**
         * Escapes control, line-separator and format characters and cuts the text to {@value #MAX_VALUE_BYTES} bytes.
         *
         * @param raw the text, or null
         * @return the clean text; empty for null
         */
        public static String clean(@Nullable String raw) {
            if (raw == null) {
                return "";
            }
            StringBuilder out = new StringBuilder(Math.min(raw.length(), MAX_VALUE_BYTES));
            int bytes = 0;
            for (int i = 0; i < raw.length(); ) {
                int codePoint = raw.codePointAt(i);
                i += Character.charCount(codePoint);
                String piece = isEscaped(codePoint)
                        ? String.format(Locale.ROOT, "\\u%04x", codePoint)
                        : new String(Character.toChars(codePoint));
                int width = piece.getBytes(StandardCharsets.UTF_8).length;
                if (bytes + width > MAX_VALUE_BYTES) {
                    break;
                }
                out.append(piece);
                bytes += width;
            }
            return out.toString();
        }

        private static boolean isEscaped(int codePoint) {
            return Character.isISOControl(codePoint)
                    || codePoint == LINE_SEPARATOR
                    || codePoint == PARAGRAPH_SEPARATOR
                    || Character.getType(codePoint) == Character.FORMAT;
        }
    }

    /**
     * Writes one entry.
     *
     * @param entry the attempt
     */
    void record(Entry entry);
}
