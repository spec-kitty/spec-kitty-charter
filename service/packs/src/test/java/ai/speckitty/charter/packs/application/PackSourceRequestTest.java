package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Which git requests register whatever a branch points at when it is fetched (post-plan security squad 7). */
class PackSourceRequestTest {

    private static final String URL = "https://git.example.org/acme/packs.git";
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    private static PackSourceRequest git(String ref, String commit) {
        return new PackSourceRequest(PackSourceKind.GIT, URL, ref, commit, null, null);
    }

    @Test
    void aBranchRefThatNamesNoCommitIsTheOneThatNeedsAPrecondition() {
        assertThat(git("refs/heads/main", null).branchWithoutCommit()).isTrue();
        assertThat(git("refs/heads/feature/x", null).branchWithoutCommit()).isTrue();
    }

    @Test
    void aBranchRefThatNamesItsCommitIsPinned() {
        assertThat(git("refs/heads/main", COMMIT).branchWithoutCommit()).isFalse();
    }

    @Test
    void aTagAndACommitIdAreNotBranches() {
        assertThat(git("refs/tags/v1", null).branchWithoutCommit()).isFalse();
        assertThat(git(COMMIT, null).branchWithoutCommit()).isFalse();
    }

    @Test
    void anotherKindOrAMissingRefIsNotABranch() {
        assertThat(new PackSourceRequest(PackSourceKind.HTTPS_ARCHIVE, URL, "refs/heads/main", null, null, null)
                        .branchWithoutCommit())
                .isFalse();
        assertThat(new PackSourceRequest(PackSourceKind.GIT, URL, null, null, null, null).branchWithoutCommit())
                .isFalse();
        assertThat(new PackSourceRequest(PackSourceKind.FILESYSTEM, null, null, null, null, "refs/heads/x")
                        .branchWithoutCommit())
                .isFalse();
    }
}
