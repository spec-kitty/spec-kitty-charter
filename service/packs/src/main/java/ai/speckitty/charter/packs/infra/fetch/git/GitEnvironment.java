package ai.speckitty.charter.packs.infra.fetch.git;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The environment git is started with, built from empty and never inherited (plan.md, "The system git binary",
 * configuration and credentials): so no proxy variable, credential helper, askpass program, {@code GIT_*} override, locale
 * or {@code HOME} of the service reaches it.
 */
final class GitEnvironment {

    private GitEnvironment() {}

    /**
     * The environment of a git run.
     *
     * @param executable the git binary; its directory is the whole {@code PATH}
     * @param home a directory of the run's own, which is {@code HOME} and {@code XDG_CONFIG_HOME}
     * @return the variables, and no others
     */
    static Map<String, String> from(Path executable, Path home) {
        Map<String, String> environment = new LinkedHashMap<>();
        Path directory = Objects.requireNonNull(executable.toAbsolutePath().getParent(), "the git binary has a directory");
        environment.put("PATH", directory.toString());
        environment.put("HOME", home.toString());
        environment.put("XDG_CONFIG_HOME", home.toString());
        environment.put("GIT_CONFIG_NOSYSTEM", "1");
        environment.put("GIT_CONFIG_GLOBAL", "/dev/null");
        environment.put("GIT_TERMINAL_PROMPT", "0");
        environment.put("LC_ALL", "C");
        environment.put("GIT_ALLOW_PROTOCOL", "https");
        environment.put("GIT_LFS_SKIP_SMUDGE", "1");
        return environment;
    }
}
