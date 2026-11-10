package ai.speckitty.charter.missions.application;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The exact bytes of a stored file of a mission type: a step's prompt or guidelines, or a skeleton. A value object that
 * never hands its array out, so what a read serves is what was stored (PROJECT_002, ADR-0036).
 */
public final class StoredFile {

    private final byte[] bytes;

    private StoredFile(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * A stored file holding a copy of {@code bytes}.
     *
     * @param bytes the file's content, of any length
     * @return the file
     */
    public static StoredFile of(byte[] bytes) {
        return new StoredFile(Objects.requireNonNull(bytes, "bytes").clone());
    }

    /**
     * The content.
     *
     * @return a fresh copy of the bytes
     */
    public byte[] bytes() {
        return bytes.clone();
    }

    /**
     * The content's length.
     *
     * @return the number of bytes
     */
    public int size() {
        return bytes.length;
    }

    /**
     * The SHA-256 digest of the bytes.
     *
     * @return 64 lower-case hex digits
     */
    public String sha256() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides SHA-256.", e);
        }
    }

    /**
     * The bytes as text, when they are valid UTF-8: decoded strictly, so no byte is replaced.
     *
     * @return the text; empty when the bytes are not valid UTF-8
     */
    public Optional<String> text() {
        try {
            return Optional.of(StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString());
        } catch (CharacterCodingException notUtf8) {
            return Optional.empty();
        }
    }

    /** Value equality: equal when the bytes are equal. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof StoredFile that && Arrays.equals(bytes, that.bytes));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    /** Names the size, never the bytes. */
    @Override
    public String toString() {
        return "StoredFile[" + bytes.length + " bytes]";
    }
}
