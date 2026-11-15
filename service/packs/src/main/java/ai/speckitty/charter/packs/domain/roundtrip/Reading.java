package ai.speckitty.charter.packs.domain.roundtrip;

import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One side of a round-tripped file, read in the content form its {@code file-kinds.yaml} rule (or its owner's companion
 * declaration) gives it. The form is decided before any text is read: a {@link Bytes} file is never read as text, and a
 * {@link Markdown} file never reaches the YAML reader (#193, #194).
 */
public sealed interface Reading
        permits Reading.Bytes, Reading.Text, Reading.Yaml, Reading.Markdown, Reading.MarkdownRefused {

    /** A payload, compared byte for byte (#193). Not a record: a record's array component compares by identity. */
    final class Bytes implements Reading {

        private final byte[] bytes;

        /**
         * Copies the bytes.
         *
         * @param bytes the file's bytes
         */
        public Bytes(byte[] bytes) {
            this.bytes = bytes.clone();
        }

        /**
         * The file's bytes.
         *
         * @return a copy of the bytes
         */
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return other instanceof Bytes that && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }

        @Override
        public String toString() {
            return "Bytes[" + bytes.length + "]";
        }
    }

    /**
     * A text companion (ADR-0039), compared exactly.
     *
     * @param text the file's text
     */
    record Text(String text) implements Reading {
        /**
         * Checks the text is present.
         *
         * @param text the file's text
         */
        public Text {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * A YAML document, parsed with the pack readers' own settings into plain maps, lists and scalars.
     *
     * @param tree the parsed document; {@code null} for an empty document
     */
    record Yaml(@Nullable Object tree) implements Reading {}

    /**
     * A Markdown document, read into its model (ADR-0040 decision 3).
     *
     * @param document the model
     */
    record Markdown(MarkdownDocument document) implements Reading {
        /**
         * Checks the model is present.
         *
         * @param document the model
         */
        public Markdown {
            Objects.requireNonNull(document, "document");
        }
    }

    /**
     * A text of the Markdown form that the Markdown reader refuses.
     *
     * @param message the reader's refusal, naming its line
     */
    record MarkdownRefused(String message) implements Reading {
        /**
         * Checks the message is present.
         *
         * @param message the refusal
         */
        public MarkdownRefused {
            Objects.requireNonNull(message, "message");
        }
    }
}
