package ai.speckitty.charter.packs.infra.fetch;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.packs.application.ArchiveFormat;
import ai.speckitty.charter.packs.application.ArchiveStaging;
import ai.speckitty.charter.packs.application.GuideFetchRefusal;
import ai.speckitty.charter.packs.application.OwnerOnly;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackSourceFetcher;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import ai.speckitty.charter.packs.application.SourceRefusal;
import ai.speckitty.charter.packs.application.SourceUpstreamException;
import ai.speckitty.charter.packs.application.SourceUrls;
import ai.speckitty.charter.packs.application.StagingArea;
import ai.speckitty.charter.packs.infra.fetch.HttpsExchange.BodyPolicy;
import ai.speckitty.charter.packs.infra.fetch.HttpsExchange.Fetched;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The https archive source (FR-004, IC-05, research R9): it fetches the archive the request's URL names through the
 * guarded exchange, spools it to an owner-only file inside the job's staging directory, checks the optional
 * {@code sha256}, and hands the file to the bounded extractor, the only way bytes become a staged tree. It runs in the
 * job, outside every lock.
 *
 * <ul>
 *   <li><b>Archives only.</b> A {@code Content-Type} of {@code application/gzip}, {@code application/x-gzip},
 *       {@code application/zip} or {@code application/octet-stream}, with no {@code Content-Encoding}; the format is
 *       the one the leading bytes show ({@code 1f 8b}, {@code PK 03 04} or {@code PK 05 06}), and a declared type that
 *       the bytes contradict is refused. An absent type is refused.
 *   <li><b>A fixed set of reasons.</b> Every refusal is a {@link SourceRefusal}, whose detail names no part of the URL,
 *       a header or upstream text (NFR-012). An upstream that failed or did not answer in time is a
 *       {@link SourceUpstreamException}, never a refusal of the source.
 *   <li><b>The record.</b> The staged tree is answered with its kind and its URL without the query, the fragment and the
 *       user information, so a presigned signature is never held in the status.
 * </ul>
 */
public final class HttpsArchiveFetcher implements PackSourceFetcher {

    /** The default cap on an archive, 32 MiB (NFR-004). */
    public static final long DEFAULT_MAX_BYTES = 32L * 1024 * 1024;

    private static final String ACCEPT =
            "application/gzip, application/x-gzip;q=0.9, application/zip;q=0.9, application/octet-stream;q=0.5";
    private static final String SPOOL_FILE = "source-archive";
    private static final Set<String> GZIP_TYPES = Set.of("application/gzip", "application/x-gzip");
    private static final String ZIP_TYPE = "application/zip";
    private static final String OCTET_STREAM = "application/octet-stream";
    private static final int LEAD_BYTES = 4;
    private static final int GZIP_FIRST = 0x1f;
    private static final int GZIP_SECOND = 0x8b;

    private final HttpsExchange exchange;
    private final PackArchiveReader reader;
    private final StagingArea staging;
    private final BodyPolicy policy;

    /**
     * A fetcher over the guarded exchange and the extractor.
     *
     * @param exchange the guarded exchange, on the archive allowlist
     * @param reader the extractor
     * @param maxBytes the largest archive accepted
     * @param staging the staging area the extraction directory is made in
     */
    public HttpsArchiveFetcher(HttpsExchange exchange, PackArchiveReader reader, long maxBytes, StagingArea staging) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.staging = Objects.requireNonNull(staging, "staging");
        this.policy = new BodyPolicy(maxBytes, ACCEPT);
    }

    @Override
    public PackSourceKind kind() {
        return PackSourceKind.HTTPS_ARCHIVE;
    }

    @Override
    public Staged fetch(PackSourceRequest request, Path directory) throws CharterRefusal {
        String url = requireWellFormed(request);
        Fetched fetched = fetched(parse(url));
        ArchiveFormat format = formatOf(fetched);
        Path archive = spool(fetched, directory, request.sha256());
        Staged staged = stage(format, archive, directory);
        return new Staged(staged.pack(), Map.of("kind", kind().wire(), "url", SourceUrls.withoutSecrets(url)));
    }

    /** The URL of a request that names an https archive and no member of another kind. */
    private static String requireWellFormed(PackSourceRequest request) {
        if (request.kind() != PackSourceKind.HTTPS_ARCHIVE) {
            throw new IllegalArgumentException("not an https archive source");
        }
        String url = request.url();
        if (url == null || request.path() != null || request.ref() != null || request.commit() != null) {
            throw SourceRefusal.SOURCE_MALFORMED.refusal();
        }
        return url;
    }

    private static URI parse(String url) {
        try {
            return URI.create(url);
        } catch (IllegalArgumentException unparsable) {
            throw SourceRefusal.URL_REFUSED.refusal();
        }
    }

    private Fetched fetched(URI uri) {
        try {
            return exchange.fetch(uri, policy);
        } catch (GuideFetchRefusal refusal) {
            throw typed(refusal);
        }
    }

    /** The reason of the fixed set an exchange refusal is, or the upstream failure it is not a refusal of the source. */
    private static RuntimeException typed(GuideFetchRefusal refusal) {
        return switch (refusal.reason()) {
            case NOT_HTTPS, PORT_NOT_ALLOWED, USERINFO, IP_LITERAL -> SourceRefusal.URL_REFUSED.refusal();
            case HOST_NOT_ALLOWED -> SourceRefusal.HOST_NOT_ALLOWED.refusal();
            case NON_GLOBAL_ADDRESS -> SourceRefusal.ADDRESS_NOT_GLOBAL.refusal();
            case TOO_MANY_REDIRECTS, REDIRECT_REFUSED -> SourceRefusal.REDIRECT_REFUSED.refusal();
            case TOO_LARGE -> SourceRefusal.TOO_LARGE.refusal();
            case ENCODED, CONTENT_TYPE, NOT_UTF8 -> SourceRefusal.WRONG_MEDIA_TYPE.refusal();
            case TIMEOUT -> SourceUpstreamException.deadline();
            case HTTP_STATUS, UNREACHABLE -> SourceUpstreamException.failed();
        };
    }

    /** The format the response is, judged from its type, its encoding and its leading bytes; else the wrong type. */
    private static ArchiveFormat formatOf(Fetched fetched) {
        for (String value : fetched.headers().allValues("Content-Encoding")) {
            for (String coding : value.split(",", -1)) {
                String token = coding.strip();
                if (!token.isEmpty() && !"identity".equalsIgnoreCase(token)) {
                    throw SourceRefusal.WRONG_MEDIA_TYPE.refusal();
                }
            }
        }
        String type = mediaTypeOf(fetched).orElseThrow(SourceRefusal.WRONG_MEDIA_TYPE::refusal);
        Optional<ArchiveFormat> sniffed = sniff(lead(fetched));
        boolean declaredGzip = GZIP_TYPES.contains(type);
        boolean declaredZip = ZIP_TYPE.equals(type);
        boolean declaredAny = OCTET_STREAM.equals(type);
        if (sniffed.isEmpty() || !(declaredAny || declaredGzip || declaredZip)) {
            throw SourceRefusal.WRONG_MEDIA_TYPE.refusal();
        }
        ArchiveFormat format = sniffed.get();
        if ((declaredGzip && format != ArchiveFormat.TAR_GZ) || (declaredZip && format != ArchiveFormat.ZIP)) {
            throw SourceRefusal.WRONG_MEDIA_TYPE.refusal();
        }
        return format;
    }

    private static Optional<String> mediaTypeOf(Fetched fetched) {
        return fetched.headers().firstValue("Content-Type").map(value -> {
            int semicolon = value.indexOf(';');
            return (semicolon < 0 ? value : value.substring(0, semicolon))
                    .strip()
                    .toLowerCase(Locale.ROOT);
        });
    }

    private static byte[] lead(Fetched fetched) {
        try (InputStream body = fetched.body()) {
            return body.readNBytes(LEAD_BYTES);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot read the fetched archive", failure);
        }
    }

    private static Optional<ArchiveFormat> sniff(byte[] lead) {
        if (lead.length >= 2 && (lead[0] & 0xff) == GZIP_FIRST && (lead[1] & 0xff) == GZIP_SECOND) {
            return Optional.of(ArchiveFormat.TAR_GZ);
        }
        boolean zip = lead.length >= LEAD_BYTES
                && lead[0] == 'P'
                && lead[1] == 'K'
                && ((lead[2] == 3 && lead[3] == 4) || (lead[2] == 5 && lead[3] == 6));
        return zip ? Optional.of(ArchiveFormat.ZIP) : Optional.empty();
    }

    /** Copies the body to an owner-only file in the directory, and checks the digest the request named, if any. */
    private static Path spool(Fetched fetched, Path directory, @Nullable String sha256) {
        Path file = directory.resolve(SPOOL_FILE);
        MessageDigest digest = sha256Digest();
        try (InputStream body = new DigestInputStream(fetched.body(), digest);
                OutputStream out = OwnerOnly.newFile(file)) {
            body.transferTo(out);
        } catch (IOException failure) {
            throw new UncheckedIOException("cannot spool the fetched archive", failure);
        }
        if (sha256 != null
                && !MessageDigest.isEqual(
                        HexFormat.of().formatHex(digest.digest()).getBytes(StandardCharsets.US_ASCII),
                        sha256.getBytes(StandardCharsets.US_ASCII))) {
            throw SourceRefusal.DIGEST_MISMATCH.refusal();
        }
        return file;
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("the JDK has no SHA-256", missing);
        }
    }

    private Staged stage(ArchiveFormat format, Path archive, Path directory) {
        try {
            return ArchiveStaging.of(staging, reader, format, archive).stage(directory);
        } catch (RuntimeException refused) {
            throw refused;
        } catch (Exception failure) {
            throw new IllegalStateException("cannot stage the fetched archive", failure);
        }
    }
}
