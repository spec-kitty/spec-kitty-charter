package ai.speckitty.charter.packs.infra.storage;

import java.util.Objects;

/**
 * What a companion file holds (ADR-0039): the content the store reads beside an artifact's file and writes back with
 * it. Only text exists, read and written byte for byte as UTF-8 over the store's one bytes read and atomic write.
 * Asset payloads and templates (#193) are not companions: they are pack-level files the pack holds in its own right
 * (ADR-0040), so no {@code Bytes} case exists until a companion needs one.
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
