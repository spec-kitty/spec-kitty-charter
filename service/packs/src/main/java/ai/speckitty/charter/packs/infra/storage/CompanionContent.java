package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownDocument;
import java.util.Objects;

/**
 * What a companion file holds (ADR-0039): the content the store reads beside an artifact's file and writes back with
 * it, in the {@linkplain KindBinding.Companion#form() form the companion declares}. Text is read and written byte for
 * byte as UTF-8; Markdown (#194) is read into its model and written in its canonical form, both over the store's one
 * bytes read and atomic write. Asset payloads and templates (#193) are not companions: they are pack-level files the
 * pack holds in its own right (ADR-0040), so no {@code Bytes} case exists until a companion needs one.
 */
sealed interface CompanionContent {

    /**
     * The form this content is held in; a companion's content always has the form the companion declares.
     *
     * @return {@link ContentForm#TEXT} or {@link ContentForm#MARKDOWN}
     */
    ContentForm form();

    /**
     * Companion text, kept exactly as read: no trimming, and no line ending is converted.
     *
     * @param value the text
     */
    record Text(String value) implements CompanionContent {
        /**
         * Checks the text is present.
         *
         * @param value the text
         */
        public Text {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public ContentForm form() {
            return ContentForm.TEXT;
        }
    }

    /**
     * A Markdown companion, held as its model (#194): it is written in the canonical form
     * ({@link ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec#write}), so a read and a write agree on
     * meaning, not on bytes.
     *
     * @param document the parsed document
     */
    record Markdown(MarkdownDocument document) implements CompanionContent {
        /**
         * Checks the document is present.
         *
         * @param document the parsed document
         */
        public Markdown {
            Objects.requireNonNull(document, "document");
        }

        @Override
        public ContentForm form() {
            return ContentForm.MARKDOWN;
        }
    }
}
