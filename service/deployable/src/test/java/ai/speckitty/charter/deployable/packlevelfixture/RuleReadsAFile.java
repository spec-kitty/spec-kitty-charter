package ai.speckitty.charter.deployable.packlevelfixture;

import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.packs.infra.storage.rules.StorageCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** PACK_RULES_READ_NO_FILE, the positive control: a rule that reads a file and names the storage edge. */
public final class RuleReadsAFile {

    private RuleReadsAFile() {}

    /**
     * Reads a file a second time, and answers with a storage edge's code.
     *
     * @param file a file of the pack
     * @return the code when the file is empty
     * @throws IOException when the file cannot be read
     */
    public static ReasonCode judge(Path file) throws IOException {
        return Files.readString(file).isEmpty() ? StorageCodes.UNREADABLE : ReasonCode.INVARIANT;
    }
}
