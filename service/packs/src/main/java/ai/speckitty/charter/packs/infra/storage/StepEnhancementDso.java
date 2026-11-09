package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of a step enhancement: a flat mirror of a {@code *.step-enhancement.yaml} (ADR-0066), plus the
 * body of each companion fragment the file names, filled in once the store has read the companions. Its record
 * equality is the round-trip fidelity assertion, so a fragment body that is dropped or altered makes two shapes
 * differ. The absent {@code prompt_fragments} key reads as no entry and writes no key.
 *
 * @param id the enhancement's {@code id}
 * @param schemaVersion the {@code schema_version}
 * @param promptFragments the {@code prompt_fragments}, in authored order; possibly none
 */
record StepEnhancementDso(String id, String schemaVersion, List<Entry> promptFragments) {

    /**
     * Checks that every component is present.
     *
     * @param id the id
     * @param schemaVersion the schema version
     * @param promptFragments the entries
     */
    StepEnhancementDso {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        promptFragments = List.copyOf(Objects.requireNonNull(promptFragments, "promptFragments"));
    }

    /**
     * One {@code prompt_fragments} entry.
     *
     * @param slot the {@code slot}
     * @param path the {@code path} of the companion, beside the enhancement file
     * @param body the companion's parsed body; empty until the store has read the companion
     */
    record Entry(String slot, String path, Optional<MarkdownDocument> body) {
        /**
         * Checks that every component is present.
         *
         * @param slot the slot
         * @param path the path
         * @param body the body
         */
        Entry {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(body, "body");
        }
    }
}
