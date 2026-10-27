package ai.speckitty.charter.packs.infra.storage;

import java.util.Objects;

/**
 * What a companion file holds (ADR-0039): the content the store reads beside an artifact's file and writes back with
 * it. Only text exists today, read and written byte for byte as UTF-8; binary content (asset payloads, templates) is
 * #193's case to add.
 */
sealed interface CompanionContent {

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
    }
}
