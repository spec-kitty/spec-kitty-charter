package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.config.StartupRefusal;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * A startup refusal is reported with the line that names the setting and the fix, without a stack trace of Spring's
 * bean-creation frames (#454). The analyzer is found the way Spring Boot finds it, so the whole start is run.
 */
@ExtendWith(OutputCaptureExtension.class)
class StartupRefusalAnalyzerTest {

    private static final Pattern STACK_FRAME = Pattern.compile("(?m)^\\s+at [\\w$.]+\\(");
    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123";

    @Test
    void aFailureThatIsNoRefusalIsLeftToSpring() {
        assertThat(new StartupRefusalAnalyzer().analyze(new IllegalStateException("a defect")))
                .isNull();
    }

    @Test
    void aTokenOnTheCommandLineStopsTheStartWithTheRefusalLineAndNoStackTrace(CapturedOutput output) {
        assertThatThrownBy(() -> SpringApplication.run(
                        CharterServiceApplication.class, "--charter.worktree-token=" + TOKEN, "--server.port=0"))
                .hasRootCauseInstanceOf(StartupRefusal.class);

        assertThat(output.getAll())
                .contains("charter.worktree-token must come from the environment (CHARTER_WORKTREE_TOKEN), not from the"
                        + " command line or a system property, where other processes can read it")
                .doesNotContain(TOKEN);
        assertThat(STACK_FRAME.matcher(output.getAll()).find())
                .as("a stack frame in the output:%n%s", output.getAll())
                .isFalse();
    }
}
