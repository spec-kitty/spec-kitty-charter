package ai.speckitty.charter.packs.infra.fetch;

import static ai.speckitty.charter.packs.infra.fetch.AddressClasses.GLOBAL;
import static ai.speckitty.charter.packs.infra.fetch.AddressClasses.PRIVATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.ArchiveFormat;
import ai.speckitty.charter.packs.application.ArchiveRefusal;
import ai.speckitty.charter.packs.application.ArchiveViolation;
import ai.speckitty.charter.packs.application.GuideFetchRefusal;
import ai.speckitty.charter.packs.application.GuideFetchRefusal.Reason;
import ai.speckitty.charter.packs.application.PackArchiveReader;
import ai.speckitty.charter.packs.application.PackSourceKind;
import ai.speckitty.charter.packs.application.PackSourceRequest;
import ai.speckitty.charter.packs.application.RegistrationJobs.Staged;
import ai.speckitty.charter.packs.application.SourceRefusal;
import ai.speckitty.charter.packs.application.SourceUpstreamException;
import ai.speckitty.charter.packs.application.StagedPack;
import ai.speckitty.charter.packs.infra.fetch.HttpsExchange.BodyPolicy;
import ai.speckitty.charter.packs.infra.fetch.HttpsExchange.Fetched;
import ai.speckitty.charter.packs.infra.fetch.HttpsGuideFetcherTest.Canned;
import ai.speckitty.charter.packs.infra.fetch.HttpsGuideFetcherTest.FakeTransport;
import ai.speckitty.charter.packs.infra.storage.FileStagingArea;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The https archive source (T085, FR-004, NFR-004, NFR-012): what it asks of the exchange, which responses it takes for
 * an archive, the digest, the spool file, and the fixed reasons every refusal ends with. The exchange is a stub that
 * answers what a test arms, and the extractor a recording fake; one test runs the real exchange over the fake
 * transport. No test opens a socket.
 */
class HttpsArchiveFetcherTest {

    private static final String URL = "https://packs.example.org/acme.tar.gz";
    private static final byte[] GZIP = {0x1f, (byte) 0x8b, 8, 0, 1, 2, 3, 4};
    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0, 0, 0};
    private static final byte[] EMPTY_ZIP = {'P', 'K', 5, 6, 0, 0, 0, 0};

    @TempDir
    Path directory;

    private final List<BodyPolicy> policies = new ArrayList<>();
    private final List<URI> requested = new ArrayList<>();
    private Fetched answer = fetched("application/gzip", GZIP);
    private GuideFetchRefusal exchangeRefusal;
    private final RecordingReader reader = new RecordingReader();

    @BeforeEach
    void noRefusal() {
        exchangeRefusal = null;
    }

    private HttpsArchiveFetcher fetcher() {
        HttpsExchange exchange = (uri, policy) -> {
            requested.add(uri);
            policies.add(policy);
            if (exchangeRefusal != null) {
                throw exchangeRefusal;
            }
            return answer;
        };
        return new HttpsArchiveFetcher(
                exchange, reader, HttpsArchiveFetcher.DEFAULT_MAX_BYTES, new FileStagingArea(directory));
    }

    private static Fetched fetched(String contentType, byte[] body) {
        return new Fetched(
                "packs.example.org",
                HttpHeaders.of(
                        contentType == null ? Map.of() : Map.of("Content-Type", List.of(contentType)), (a, b) -> true),
                body);
    }

    private static PackSourceRequest request(String url, String sha256) {
        return new PackSourceRequest(PackSourceKind.HTTPS_ARCHIVE, url, null, null, sha256, null);
    }

    private SourceRefusal refusedWith(PackSourceRequest request) {
        try {
            fetcher().fetch(request, directory);
        } catch (CharterRefusal refusal) {
            return SourceRefusalOf.of(refusal);
        }
        throw new AssertionError("the source was not refused");
    }

    @Test
    void aGzipArchiveIsSpooledExtractedAndRecordedAsSent() throws Exception {
        Staged staged = fetcher().fetch(request(URL, null), directory);

        assertThat(reader.format).isEqualTo(ArchiveFormat.TAR_GZ);
        assertThat(reader.body).isEqualTo(GZIP);
        assertThat(staged.pack().directory()).isEqualTo(reader.into.resolve("pack"));
        assertThat(staged.source()).containsExactlyInAnyOrderEntriesOf(Map.of("kind", "https_archive", "url", URL));
        assertThat(requested).containsExactly(URI.create(URL));
    }

    @Test
    void theRecordKeepsNoQueryFragmentOrUserInformationOfAPresignedUrl() throws Exception {
        String presigned = "https://packs.example.org/acme.tar.gz?X-Amz-Signature=s3cr3t&X-Amz-Expires=60#part";

        Staged staged = fetcher().fetch(request(presigned, null), directory);

        assertThat(staged.source()).containsExactlyInAnyOrderEntriesOf(Map.of("kind", "https_archive", "url", URL));
        assertThat(staged.source().values()).noneMatch(value -> value.contains("s3cr3t"));
        assertThat(requested).containsExactly(URI.create(presigned));
    }

    @Test
    void theSpoolFileIsOwnerOnlyAndInsideTheJobsDirectory() throws Exception {
        fetcher().fetch(request(URL, null), directory);

        try (Stream<Path> files = Files.list(directory)) {
            List<Path> spooled = files.filter(Files::isRegularFile).toList();
            assertThat(spooled).hasSize(1);
            assertThat(Files.getPosixFilePermissions(spooled.getFirst()))
                    .isEqualTo(PosixFilePermissions.fromString("rw-------"));
            assertThat(Files.readAllBytes(spooled.getFirst())).isEqualTo(GZIP);
        }
        assertThat(reader.into).startsWith(directory);
    }

    @Test
    void theRequestAsksForArchivesAndHoldsThemToThirtyTwoMebibytes() throws Exception {
        fetcher().fetch(request(URL, null), directory);

        assertThat(policies).hasSize(1);
        assertThat(policies.getFirst().maxBytes()).isEqualTo(32L * 1024 * 1024);
        assertThat(policies.getFirst().accept())
                .contains("application/gzip", "application/zip", "application/x-gzip", "application/octet-stream")
                .doesNotContain("*/*");
    }

    @ParameterizedTest
    @MethodSource("takenAsAnArchive")
    void aResponseThatIsAnArchiveOfItsDeclaredOrSniffedFormatIsTaken(
            String contentType, byte[] body, ArchiveFormat format) throws Exception {
        answer = fetched(contentType, body);

        fetcher().fetch(request(URL, null), directory);

        assertThat(reader.format).isEqualTo(format);
    }

    static Stream<Arguments> takenAsAnArchive() {
        return Stream.of(
                row("application/gzip", GZIP, ArchiveFormat.TAR_GZ),
                row("application/x-gzip", GZIP, ArchiveFormat.TAR_GZ),
                row("Application/GZIP; charset=binary", GZIP, ArchiveFormat.TAR_GZ),
                row("application/zip", ZIP, ArchiveFormat.ZIP),
                row("application/zip", EMPTY_ZIP, ArchiveFormat.ZIP),
                row("application/octet-stream", GZIP, ArchiveFormat.TAR_GZ),
                row("application/octet-stream", ZIP, ArchiveFormat.ZIP));
    }

    private static Arguments row(String type, byte[] body, ArchiveFormat format) {
        return Arguments.of(type, body, format);
    }

    @ParameterizedTest
    @MethodSource("notAnArchive")
    void aResponseThatIsNotTheArchiveItClaimsIsRefusedAsTheWrongMediaType(String contentType, byte[] body) {
        answer = fetched(contentType, body);

        assertThat(refusedWith(request(URL, null))).isEqualTo(SourceRefusal.WRONG_MEDIA_TYPE);
        assertThat(reader.calls).isZero();
    }

    static Stream<Arguments> notAnArchive() {
        byte[] html = "<html></html>".getBytes(StandardCharsets.UTF_8);
        return Stream.of(
                Arguments.of("text/html", html),
                Arguments.of("application/gzip", ZIP),
                Arguments.of("application/zip", GZIP),
                Arguments.of("application/octet-stream", html),
                Arguments.of("application/gzip", html),
                Arguments.of("application/x-tar", GZIP),
                Arguments.of(null, GZIP),
                Arguments.of("application/gzip", new byte[0]));
    }

    @Test
    void aContentEncodedResponseIsRefusedAsTheWrongMediaType() {
        answer = new Fetched(
                "packs.example.org",
                HttpHeaders.of(
                        Map.of("Content-Type", List.of("application/gzip"), "Content-Encoding", List.of("gzip")),
                        (a, b) -> true),
                GZIP);

        assertThat(refusedWith(request(URL, null))).isEqualTo(SourceRefusal.WRONG_MEDIA_TYPE);
        assertThat(reader.calls).isZero();
    }

    @Test
    void aMatchingSha256IsAcceptedAndAMismatchIsRefusedBeforeAnythingIsExtracted() throws Exception {
        String right = sha256(GZIP);

        fetcher().fetch(request(URL, right), directory);
        assertThat(reader.calls).isEqualTo(1);

        String wrong = "0".repeat(63) + "f";
        Path another = Files.createDirectory(directory.resolve("another-job"));
        assertThatThrownBy(() -> fetcher().fetch(request(URL, wrong), another))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.getMessage()).startsWith("digest_mismatch: "));
        assertThat(reader.calls).isEqualTo(1);
    }

    private static String sha256(byte[] body) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    }

    @ParameterizedTest
    @MethodSource("refusalsOfTheExchange")
    void everyRefusalOfTheExchangeEndsWithAReasonFromTheFixedSet(Reason reason, SourceRefusal expected) {
        exchangeRefusal = new GuideFetchRefusal(reason, "packs.example.org", "detail with s3cr3t");

        SourceRefusal refusal = refusedWith(request(URL + "?token=s3cr3t", null));

        assertThat(refusal).isEqualTo(expected);
        assertThat(expected.refusal().getMessage()).doesNotContain("s3cr3t", "packs.example.org");
    }

    static Stream<Arguments> refusalsOfTheExchange() {
        return Stream.of(
                row(Reason.NOT_HTTPS, SourceRefusal.URL_REFUSED),
                row(Reason.USERINFO, SourceRefusal.URL_REFUSED),
                row(Reason.PORT_NOT_ALLOWED, SourceRefusal.URL_REFUSED),
                row(Reason.IP_LITERAL, SourceRefusal.URL_REFUSED),
                row(Reason.HOST_NOT_ALLOWED, SourceRefusal.HOST_NOT_ALLOWED),
                row(Reason.NON_GLOBAL_ADDRESS, SourceRefusal.ADDRESS_NOT_GLOBAL),
                row(Reason.TOO_MANY_REDIRECTS, SourceRefusal.REDIRECT_REFUSED),
                row(Reason.REDIRECT_REFUSED, SourceRefusal.REDIRECT_REFUSED),
                row(Reason.TOO_LARGE, SourceRefusal.TOO_LARGE),
                row(Reason.ENCODED, SourceRefusal.WRONG_MEDIA_TYPE),
                row(Reason.CONTENT_TYPE, SourceRefusal.WRONG_MEDIA_TYPE));
    }

    private static Arguments row(Reason reason, SourceRefusal refusal) {
        return Arguments.of(reason, refusal);
    }

    @ParameterizedTest
    @EnumSource(
            value = Reason.class,
            names = {"HTTP_STATUS", "UNREACHABLE"})
    void anUpstreamThatFailedIsABadGatewayAndNeverARefusalOfTheSource(Reason reason) {
        exchangeRefusal = new GuideFetchRefusal(reason, "packs.example.org", "status 500 for s3cr3t");

        assertThatThrownBy(() -> fetcher().fetch(request(URL, null), directory))
                .isInstanceOfSatisfying(SourceUpstreamException.class, failure -> {
                    assertThat(failure.timedOut()).isFalse();
                    assertThat(failure.getMessage()).doesNotContain("s3cr3t", "packs.example.org");
                });
    }

    @Test
    void anUpstreamThatDidNotAnswerInTimeIsAGatewayTimeout() {
        exchangeRefusal = new GuideFetchRefusal(Reason.TIMEOUT, "packs.example.org", "the deadline");

        assertThatThrownBy(() -> fetcher().fetch(request(URL, null), directory))
                .isInstanceOfSatisfying(
                        SourceUpstreamException.class,
                        failure -> assertThat(failure.timedOut()).isTrue());
    }

    @ParameterizedTest
    @MethodSource("malformedSources")
    void aMemberForeignToTheKindOrAMissingUrlIsMalformedAndNothingIsFetched(PackSourceRequest source) {
        assertThat(refusedWith(source)).isEqualTo(SourceRefusal.SOURCE_MALFORMED);
        assertThat(requested).isEmpty();
    }

    static Stream<PackSourceRequest> malformedSources() {
        return Stream.of(
                new PackSourceRequest(PackSourceKind.HTTPS_ARCHIVE, URL, null, null, null, "packs/acme"),
                new PackSourceRequest(PackSourceKind.HTTPS_ARCHIVE, URL, "refs/tags/v1", null, null, null),
                new PackSourceRequest(PackSourceKind.HTTPS_ARCHIVE, URL, null, "a".repeat(40), null, null),
                new PackSourceRequest(PackSourceKind.HTTPS_ARCHIVE, null, null, null, null, null));
    }

    @Test
    void aUrlThatDoesNotParseIsRefusedWithoutEchoingIt() {
        PackSourceRequest source = request("https://exa mple.org/s3cr3t path", null);

        assertThat(refusedWith(source)).isEqualTo(SourceRefusal.URL_REFUSED);
        assertThat(requested).isEmpty();
    }

    @Test
    void anArchiveTheExtractorRefusesIsAValidationFailureThatNamesTheEntry() {
        reader.refuse = new ArchiveRefusal(
                List.of(new ArchiveViolation(ArchiveViolation.Code.PATH_TRAVERSAL, "../evil")), 0, 0);

        assertThatThrownBy(() -> fetcher().fetch(request(URL, null), directory))
                .isInstanceOfSatisfying(CharterRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(RefusalCode.VALIDATION_FAILED);
                    assertThat(refusal.violations())
                            .extracting(violation -> violation.code())
                            .contains("path_traversal");
                });
    }

    @Test
    void everyReasonIsACodedRefusalWhoseDetailStartsWithItsToken() {
        for (SourceRefusal reason : SourceRefusal.values()) {
            CharterRefusal refusal = reason.refusal();
            assertThat(refusal.code()).isEqualTo(RefusalCode.PACK_SOURCE_REFUSED);
            assertThat(refusal.getMessage()).startsWith(reason.wire() + ": ");
        }
        assertThat(Set.of(SourceRefusal.values()).stream().map(SourceRefusal::wire))
                .containsExactlyInAnyOrder(
                        "host_not_allowed",
                        "address_not_global",
                        "url_refused",
                        "redirect_refused",
                        "wrong_media_type",
                        "too_large",
                        "digest_mismatch",
                        "name_mismatch",
                        "source_malformed",
                        "path_refused",
                        "bad_ref",
                        "commit_mismatch");
    }

    @Test
    void overTheRealExchangeAPrivateHostIsRefusedAndAnAllowlistedArchiveIsStaged() throws Exception {
        Map<String, List<InetAddress>> dns =
                Map.of("packs.example.org", List.of(GLOBAL), "inner.example.org", List.of(PRIVATE));
        FakeTransport transport = new FakeTransport();
        transport.route(
                URL, new Canned(200, Map.of("Content-Type", List.of("application/gzip")), List.of(GZIP), false));
        GuardedHttpsExchange exchange = new GuardedHttpsExchange(
                new GuideAllowlist(Set.of("packs.example.org", "inner.example.org")),
                Duration.ofSeconds(10),
                3,
                host -> {
                    List<InetAddress> found = dns.get(host);
                    if (found == null) {
                        throw new UnknownHostException(host);
                    }
                    return found;
                },
                transport,
                GuardedHttpsExchange.LOOKUP_SLOTS);
        HttpsArchiveFetcher real = new HttpsArchiveFetcher(
                exchange, reader, HttpsArchiveFetcher.DEFAULT_MAX_BYTES, new FileStagingArea(directory));

        assertThat(real.fetch(request(URL, null), directory).pack()).isNotNull();
        assertThatThrownBy(() -> real.fetch(request("https://inner.example.org/acme.tar.gz", null), directory))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.getMessage()).startsWith("address_not_global: "));
        assertThat(transport.requested()).containsExactly(URL);
    }

    /** The reason a refusal carries, read back from its detail. */
    private static final class SourceRefusalOf {
        static SourceRefusal of(Throwable refusal) {
            String detail = refusal.getMessage();
            for (SourceRefusal reason : SourceRefusal.values()) {
                if (detail.startsWith(reason.wire() + ": ")) {
                    return reason;
                }
            }
            throw new AssertionError("not a refusal of the fixed set: " + detail);
        }
    }

    /** An extractor that records what it was handed and stages nothing. */
    private static final class RecordingReader implements PackArchiveReader {
        int calls;
        byte[] body;
        ArchiveFormat format;
        Path into;
        ArchiveRefusal refuse;

        @Override
        public StagedPack extract(InputStream stream, ArchiveFormat archiveFormat, Path stagingRoot)
                throws ArchiveRefusal {
            calls++;
            if (refuse != null) {
                throw refuse;
            }
            try {
                body = stream.readAllBytes();
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
            format = archiveFormat;
            into = stagingRoot;
            return new StagedPack(stagingRoot.resolve("pack"));
        }
    }
}
