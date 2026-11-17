package ai.speckitty.charter.deployable.packlevelfixture;

import java.io.IOException;
import java.io.InputStream;

/** PACK_RULES_READ_NO_FILE, the positive control of its {@code java.io} branch: a rule that reads a stream. */
public final class RuleReadsAStream {

    private RuleReadsAStream() {}

    /**
     * Reads the pack's bytes a second time.
     *
     * @param in a stream over a file of the pack
     * @return whether the stream is empty
     * @throws IOException when the stream cannot be read
     */
    public static boolean empty(InputStream in) throws IOException {
        return in.read() < 0;
    }
}
