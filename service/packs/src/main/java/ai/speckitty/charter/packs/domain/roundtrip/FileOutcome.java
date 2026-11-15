package ai.speckitty.charter.packs.domain.roundtrip;

import java.util.List;
import java.util.Objects;

/** What the round trip did with one source file of a modelled kind. */
public sealed interface FileOutcome permits FileOutcome.Written, FileOutcome.Refused {

    /**
     * The file was read, written and read back.
     *
     * @param writtenPath the target-relative path of the written file it pairs with
     * @param source the source file, read
     * @param written the written file, read
     * @param sourceStale the source manifest's derived fields that do not match the source bytes (#195, ADR-0042); empty
     *     for every other kind
     * @param writtenStale the written manifest's derived fields that do not match the written files it lists; empty for
     *     every other kind
     */
    record Written(
            String writtenPath, Reading source, Reading written, List<String> sourceStale, List<String> writtenStale)
            implements FileOutcome {
        /**
         * Checks every field and copies the lists.
         *
         * @param writtenPath the written file's path
         * @param source the source, read
         * @param written the written file, read
         * @param sourceStale the source's stale derived fields
         * @param writtenStale the written file's stale derived fields
         */
        public Written {
            Objects.requireNonNull(writtenPath, "writtenPath");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(written, "written");
            sourceStale = List.copyOf(sourceStale);
            writtenStale = List.copyOf(writtenStale);
        }

        /**
         * A written file with no derived fields to verify.
         *
         * @param writtenPath the written file's path
         * @param source the source, read
         * @param written the written file, read
         */
        public Written(String writtenPath, Reading source, Reading written) {
            this(writtenPath, source, written, List.of(), List.of());
        }
    }

    /**
     * The file was refused on read, on write or on read-back.
     *
     * @param reason why, naming the field when the reader did
     */
    record Refused(String reason) implements FileOutcome {
        /**
         * Checks the reason is present.
         *
         * @param reason why
         */
        public Refused {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
