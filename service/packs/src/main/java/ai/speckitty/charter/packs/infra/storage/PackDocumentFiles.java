package ai.speckitty.charter.packs.infra.storage;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import ai.speckitty.charter.packs.domain.PackDocumentText;
import ai.speckitty.charter.packs.domain.markdown.MarkdownDocument;
import ai.speckitty.charter.packs.infra.storage.markdown.MarkdownCodec;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The read of a pack's {@code README.md} for the document read (#451). It is given the pack's directory, which
 * {@link DomainRepositoryService} resolves for every port it serves (the one resolution, #495), and reads the file as
 * the store does: through {@link PackDocumentMarkdown#read}, so a file the store refuses is refused here, with the
 * store's wording, as {@code PACK_UNREADABLE} and never normalized.
 */
final class PackDocumentFiles {

    private PackDocumentFiles() {}

    /**
     * Whether the directory holds a document: a regular file, not a link, as the store's listing takes it.
     *
     * @param directory the pack's directory
     * @return {@code true} when {@code README.md} is a regular file
     */
    static boolean holds(Path directory) {
        return Files.isRegularFile(directory.resolve(PackDocumentMarkdown.FILE), LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * The document of a pack directory.
     *
     * @param pack the pack, named in a refusal
     * @param directory the pack's directory
     * @return the document; empty when the directory holds no {@code README.md}
     * @throws CharterRefusal with {@code PACK_UNREADABLE} when the file cannot be read as the store reads it
     */
    static Optional<PackDocumentText> read(PackIdentifier pack, Path directory) {
        if (!holds(directory)) {
            return Optional.empty();
        }
        Optional<byte[]> bytes = FilesystemPackStore.readBytes(directory.resolve(PackDocumentMarkdown.FILE));
        Optional<String> text = bytes.flatMap(FilesystemPackStore::decodeUtf8);
        if (text.isEmpty()) {
            throw unreadable(pack, FilesystemPackStore.notText(bytes));
        }
        return switch (PackDocumentMarkdown.read(text.get())) {
            case FileRead.Read<MarkdownDocument> good ->
                Optional.of(new PackDocumentText(
                        text.get(), good.value().title(), MarkdownCodec.canonicalBody(good.value())));
            case FileRead.Unreadable<MarkdownDocument> bad ->
                throw unreadable(
                        pack,
                        FilesystemPackStore.violationsOf(bad).stream()
                                .map(violation -> violation.reason())
                                .findFirst()
                                .orElse("it cannot be read"));
            case FileRead.Absent<MarkdownDocument> absent -> Optional.empty();
        };
    }

    private static CharterRefusal unreadable(PackIdentifier pack, String why) {
        return new CharterRefusal(
                RefusalCode.PACK_UNREADABLE, pack.value() + "/" + PackDocumentMarkdown.FILE + ": " + why);
    }
}
