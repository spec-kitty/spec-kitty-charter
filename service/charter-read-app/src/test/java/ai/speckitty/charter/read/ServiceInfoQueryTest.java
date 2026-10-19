package ai.speckitty.charter.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.domain.CharterRefusal;
import ai.speckitty.charter.domain.RefusalCode;
import org.junit.jupiter.api.Test;

class ServiceInfoQueryTest {

    @Test
    void answersTheWorktreeAndTheCurrentSourceFingerprint() {
        ServiceInfoQuery query = new ServiceInfoQuery("wt-1", () -> "sha256:" + "0".repeat(64));
        assertThat(query.answer()).isEqualTo(new ServiceInfo("wt-1", "sha256:" + "0".repeat(64)));
        assertThat(query.worktree()).isEqualTo("wt-1");
    }

    @Test
    void anUnreadablePackRefusesTheAnswerButNotTheWorktree() {
        ServiceInfoQuery query = new ServiceInfoQuery("wt-1", () -> {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack qa is not a directory");
        });
        assertThat(query.worktree()).isEqualTo("wt-1");
        assertThatThrownBy(query::answer)
                .isInstanceOfSatisfying(
                        CharterRefusal.class, r -> assertThat(r.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }
}
