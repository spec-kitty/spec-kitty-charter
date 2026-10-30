package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackContents.ParseFailure;
import ai.speckitty.charter.packs.domain.PackContents.SchemaViolation;
import ai.speckitty.charter.packs.domain.PackDocument;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownDocument;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownRefusal;
import java.util.List;

/**
 * The storage rules of a pack's {@code pack.md} (#260, ADR-0042 decision 8): where it is, its file kind, and the
 * mapping between the domain {@link PackDocument} and #194's {@link MarkdownDocument}, part for part, and the one read
 * the store and validate mode share, so the two refuse the same files. Markdown is parsed and written by #194's codec
 * only.
 */
final class PackDocumentMarkdown {

    /** The pack document's pack-relative path. */
    static final String FILE = "pack.md";

    /** Its file kind and structure schema, as {@code contracts/packs/file-kinds.yaml} names them. */
    static final String KIND = "pack-document";

    private PackDocumentMarkdown() {}

    /**
     * Reads a {@code pack.md} text as the {@code markdown} content form does: parsed by the codec (a refusal names its
     * line: a carriage return, a byte order mark or an ambiguous heading is never normalized), then validated against
     * the {@code pack-document} structure schema.
     *
     * @param text the file's text
     * @return the model; or unreadable, with the codec's refusal as the parse failure or the schema's violations
     */
    static FileRead<MarkdownDocument> read(String text) {
        return switch (MarkdownCodec.parse(text)) {
            case MarkdownRefusal refusal ->
                new FileRead.Unreadable<>(FILE, new ParseFailure(FILE, refusal.message()), List.of());
            case MarkdownCodec.Parsed parsed -> {
                List<SchemaViolation> violations =
                        PackSchemas.bundled().validateMarkdown(KIND, FILE, parsed.document());
                yield violations.isEmpty()
                        ? new FileRead.Read<>(FILE, parsed.document())
                        : new FileRead.Unreadable<>(FILE, null, violations);
            }
        };
    }

    /**
     * The domain document a Markdown model holds.
     *
     * @param document the model, as the codec read it
     * @return the same parts as a domain document
     */
    static PackDocument toDomain(MarkdownDocument document) {
        return new PackDocument(
                document.frontmatter(),
                document.title(),
                document.preamble(),
                document.sections().stream()
                        .map(section -> new PackDocument.Section(section.title(), section.content()))
                        .toList());
    }

    /**
     * The Markdown model of a domain document, to write through the codec.
     *
     * @param document the domain document
     * @return the same parts as a Markdown model
     * @throws IllegalArgumentException when a part cannot stand in a Markdown document (the model's own invariants)
     */
    static MarkdownDocument toMarkdown(PackDocument document) {
        return new MarkdownDocument(
                document.frontmatter(),
                document.title(),
                document.preamble(),
                document.sections().stream()
                        .map(section -> new MarkdownDocument.Section(section.title(), section.content()))
                        .toList());
    }

    /**
     * The canonical text of a document about to be written, or a refusal naming {@code pack.md}: a domain document
     * holds the model's shape only, so a part that cannot stand in a Markdown document is refused here, before any byte
     * is written. The model refuses a part it cannot hold (a column-0 H1 in the preamble, a carriage return, an open
     * fence); the codec refuses a text it cannot read back as the same document (a title ending in a closing {@code #}
     * sequence).
     *
     * @param pack the pack, named in the refusal
     * @param document the document
     * @return the canonical Markdown text
     * @throws CharterRefusal with {@code VALIDATION_FAILED} when the document cannot stand as Markdown
     */
    static String toWritableText(PackIdentifier pack, PackDocument document) {
        try {
            return MarkdownCodec.write(toMarkdown(document));
        } catch (IllegalArgumentException | IllegalStateException breach) {
            throw new CharterRefusal(
                    RefusalCode.VALIDATION_FAILED,
                    pack.value() + "/" + FILE + " cannot be written as Markdown: " + breach.getMessage(),
                    breach);
        }
    }
}
