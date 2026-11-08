package ai.speckitty.charter.packs.infra.fetch.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.application.SourceRefusal;
import ai.speckitty.charter.packs.infra.fetch.GuideAllowlist;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The URL and the ref of a git source before any process starts (T090, FR-004, research R9, plan.md "The system git
 * binary"): the grammar, and the hostile values each part of it exists to refuse. Nothing here starts git.
 */
class GitSourceRulesTest {

    private static final GuideAllowlist ALLOWED =
            new GuideAllowlist(Set.of("git.example.org", "xn--bcher-kva.example"));
    private static final String HEX40 = "0123456789abcdef0123456789abcdef01234567";
    private static final String HEX64 = HEX40 + "0123456789abcdef01234567";

    private static SourceRefusal refusedWith(Supplier<?> call) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(call::get);
        assertThat(thrown).isInstanceOf(CharterRefusal.class);
        assertThat(((CharterRefusal) thrown).code()).isEqualTo(RefusalCode.PACK_SOURCE_REFUSED);
        for (SourceRefusal reason : SourceRefusal.values()) {
            if (thrown.getMessage().startsWith(reason.wire() + ": ")) {
                return reason;
            }
        }
        throw new AssertionError("not a refusal of the fixed set: " + thrown.getMessage());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "https://git.example.org/acme/packs.git|git.example.org|https://git.example.org/acme/packs.git",
                "https://GIT.Example.ORG/acme/packs.git|git.example.org|https://git.example.org/acme/packs.git",
                "HTTPS://git.example.org/acme/packs.git|git.example.org|https://git.example.org/acme/packs.git",
                "https://git.example.org:443/acme/packs.git|git.example.org|https://git.example.org/acme/packs.git",
                "https://git.example.org./acme/packs.git|git.example.org|https://git.example.org/acme/packs.git",
                "https://git.example.org/a_b.c-d~e/f.git|git.example.org|https://git.example.org/a_b.c-d~e/f.git",
                "https://git.example.org/packs|git.example.org|https://git.example.org/packs",
            })
    void aPlainHttpsUrlOnAnAllowedHostIsRebuiltFromItsParts(String sent, String host, String canonical) {
        GitRepository repository = GitSourceRules.repository(sent, ALLOWED);

        assertThat(repository.host()).isEqualTo(host);
        assertThat(repository.url()).isEqualTo(canonical);
    }

    static Stream<Arguments> refusedUrls() {
        return Stream.of(
                Arguments.of("http://git.example.org/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("ssh://git.example.org/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("git://git.example.org/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("file:///etc/passwd", SourceRefusal.URL_REFUSED),
                Arguments.of("ext::sh -c touch /tmp/pwned", SourceRefusal.URL_REFUSED),
                Arguments.of("//git.example.org/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("git.example.org/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("-https://git.example.org/a.git", SourceRefusal.URL_REFUSED),
                Arguments.of("--upload-pack=evil", SourceRefusal.URL_REFUSED),
                Arguments.of("https://user@git.example.org/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://user:s3cr3t@git.example.org/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org@evil.example.net/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/acme/packs.git?token=s3cr3t", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/acme/packs.git?", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/acme/packs.git#frag", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/acme/packs.git#", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org:8443/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org:80/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org:/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://127.0.0.1/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://2130706433/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://0x7f.1/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://[::1]/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org//acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/acme//packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/acme/../secret.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/./acme.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/acme/%2e%2e/secret.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/acme/packs.git/", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/-upload-pack/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/acme/-packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/acme/packs git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/acme/packs.git\n", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/acme\\packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/acme/pa%0acks.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://-git.example.org/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git_host.example.org/acme/packs.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://" + "a".repeat(2100) + ".example.org/x.git", SourceRefusal.URL_REFUSED),
                Arguments.of("https://git.example.org/" + "a".repeat(2100), SourceRefusal.URL_REFUSED),
                Arguments.of("https://elsewhere.example.net/acme/packs.git", SourceRefusal.HOST_NOT_ALLOWED),
                Arguments.of("https://git.example.org.evil.example.net/acme/packs.git", SourceRefusal.HOST_NOT_ALLOWED),
                Arguments.of("https://sub.git.example.org/acme/packs.git", SourceRefusal.HOST_NOT_ALLOWED),
                Arguments.of("https://example.org/acme/packs.git", SourceRefusal.HOST_NOT_ALLOWED));
    }

    @ParameterizedTest
    @MethodSource("refusedUrls")
    void aUrlOutsideTheGrammarOrTheAllowlistIsRefusedWithAFixedReason(String url, SourceRefusal expected) {
        assertThat(refusedWith(() -> GitSourceRules.repository(url, ALLOWED))).isEqualTo(expected);
    }

    @Test
    void theRefusalNamesNothingTheClientSent() {
        String hostile = "https://user:s3cr3t@git.example.org/acme/packs.git?token=s3cr3t#s3cr3t";

        assertThatThrownBy(() -> GitSourceRules.repository(hostile, ALLOWED))
                .isInstanceOf(CharterRefusal.class)
                .satisfies(refusal -> assertThat(refusal.getMessage())
                        .doesNotContain("s3cr3t")
                        .doesNotContain("example"));
    }

    @Test
    void anEmptyAllowlistRefusesEveryHost() {
        assertThat(refusedWith(
                        () -> GitSourceRules.repository("https://git.example.org/a/b.git", GuideAllowlist.EMPTY)))
                .isEqualTo(SourceRefusal.HOST_NOT_ALLOWED);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "refs/heads/main",
                "refs/heads/feature/x-1.2_3",
                "refs/tags/v1.0.0",
                "refs/tags/release/2026-11",
                "refs/heads/a",
                HEX40,
                HEX64,
            })
    void aCommitIdOrABranchOrTagNameOfTheGrammarIsTaken(String ref) {
        assertThat(GitSourceRules.ref(ref).value()).isEqualTo(ref);
    }

    @Test
    void theRefKnowsWhatItNames() {
        assertThat(GitSourceRules.ref("refs/heads/main").kind()).isEqualTo(GitRef.Kind.BRANCH);
        assertThat(GitSourceRules.ref("refs/tags/v1").kind()).isEqualTo(GitRef.Kind.TAG);
        assertThat(GitSourceRules.ref(HEX40).kind()).isEqualTo(GitRef.Kind.COMMIT);
        assertThat(GitSourceRules.ref(HEX64).kind()).isEqualTo(GitRef.Kind.COMMIT);
    }

    static Stream<String> badRefs() {
        return Stream.of(
                "",
                "main",
                "HEAD",
                "heads/main",
                "refs/main",
                "refs/heads/",
                "refs/tags/",
                "refs/remotes/origin/main",
                "refs/charter/fetched",
                "--upload-pack=evil",
                "-main",
                "--",
                "refs/heads/-evil",
                "refs/tags/-evil",
                "refs/heads/--upload-pack=x",
                "/refs/heads/main",
                "refs/heads//main",
                "refs/heads/../secret",
                "refs/heads/a/../b",
                "refs/heads/..",
                "refs/heads/a..b",
                "refs/heads/a@{1}",
                "refs/heads/@{-1}",
                "refs/heads/@",
                "refs/heads/topic.lock",
                "refs/heads/a.lock/b",
                "refs/heads/main/",
                "refs/heads/main.",
                "refs/heads/.hidden",
                "refs/heads/a/.hidden",
                "refs/heads/a b",
                "refs/heads/a\tb",
                "refs/heads/a\nb",
                "refs/heads/a\u0000b",
                "refs/heads/a\u007fb",
                "refs/heads/a~1",
                "refs/heads/a^",
                "refs/heads/a:b",
                "refs/heads/a?b",
                "refs/heads/a*b",
                "refs/heads/a[b",
                "refs/heads/a\\b",
                "refs/heads/é",
                "refs/heads/" + "a".repeat(201),
                "refs/tags/" + "a".repeat(250),
                HEX40.toUpperCase(java.util.Locale.ROOT),
                HEX40.substring(1),
                HEX40 + "0",
                HEX64.substring(1),
                "g" + HEX40.substring(1),
                " " + HEX40,
                HEX40 + "\n");
    }

    @ParameterizedTest
    @MethodSource("badRefs")
    void aRefOutsideTheGrammarIsRefusedBeforeAProcessStarts(String ref) {
        assertThat(refusedWith(() -> GitSourceRules.ref(ref))).isEqualTo(SourceRefusal.BAD_REF);
    }

    @Test
    void aRefIsAtMostTwoHundredFiftyFiveBytes() {
        String longest = "refs/heads/" + "a".repeat(200);
        assertThat(longest.length()).isLessThanOrEqualTo(255);
        assertThat(GitSourceRules.ref(longest).value()).isEqualTo(longest);
    }

    @Test
    void theRefusalOfARefNamesNothingTheClientSent() {
        assertThatThrownBy(() -> GitSourceRules.ref("--upload-pack=s3cr3t"))
                .satisfies(refusal -> assertThat(refusal.getMessage())
                        .doesNotContain("s3cr3t")
                        .doesNotContain("upload"));
    }

    @Test
    void aCommitIsAbsentOrAFullLowerCaseId() {
        assertThat(GitSourceRules.commit(null)).isNull();
        assertThat(GitSourceRules.commit(HEX40)).isEqualTo(HEX40);
        assertThat(GitSourceRules.commit(HEX64)).isEqualTo(HEX64);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "main",
                "--upload-pack=x",
                "refs/heads/main",
                "ABCDEF0123456789ABCDEF0123456789ABCDEF01",
                "0123"
            })
    void aCommitThatIsNotAFullIdIsRefused(String commit) {
        assertThat(refusedWith(() -> GitSourceRules.commit(commit))).isEqualTo(SourceRefusal.BAD_REF);
    }
}
