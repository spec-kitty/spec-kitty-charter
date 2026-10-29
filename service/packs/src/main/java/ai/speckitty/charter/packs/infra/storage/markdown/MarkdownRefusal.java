package ai.speckitty.charter.packs.infra.storage.markdown;

import java.util.Objects;

/**
 * Why a Markdown text is not read: it is ambiguous (or not text the form admits), at a named line. The reason is a
 * short, stable phrase, used verbatim in violations.
 *
 * @param line the 1-based line the refusal names
 * @param reason why the text is refused
 */
public record MarkdownRefusal(int line, String reason) implements MarkdownCodec.Outcome {

    /**
     * Checks the fields.
     *
     * @param line the 1-based line the refusal names
     * @param reason why the text is refused
     */
    public MarkdownRefusal {
        Objects.requireNonNull(reason, "reason");
        if (line < 1) {
            throw new IllegalArgumentException("line must be 1-based, but is " + line);
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    /**
     * The refusal as one message.
     *
     * @return {@code line <n>: <reason>}
     */
    public String message() {
        return "line " + line + ": " + reason;
    }
}
