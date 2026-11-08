package ai.speckitty.charter.packs.infra.fetch.git;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/** Runs git for one fetch (T089); a scaffold until the process machinery is built. */
final class GitCommand {

    /** The {@code -c} pins every run carries. */
    static final List<String> PINS = List.of();

    /**
     * What a remote adds to every run: configuration pins after the fixed ones, and environment variables over the
     * fixed ones.
     *
     * @param pins {@code key=value} pairs given with {@code -c}
     * @param environment variables set after the environment built from empty
     */
    record Transport(List<String> pins, Map<String, String> environment) {

        static final Transport NONE = new Transport(List.of(), Map.of());
    }

    /** A running git, for the output that is read as it is written. */
    static final class Running implements AutoCloseable {

        InputStream stdout() {
            return InputStream.nullInputStream();
        }

        String stderr() {
            return "";
        }

        void finish() {}

        @Override
        public void close() {}
    }

    GitCommand(Path executable, Path home, Duration timeout, Transport transport) {}

    GitCommand watching(BooleanSupplier overLimit) {
        return this;
    }

    byte[] run(List<String> arguments, int maxOutputBytes) {
        return new byte[0];
    }

    Running stream(List<String> arguments, @Nullable Path stdin) {
        return new Running();
    }
}
