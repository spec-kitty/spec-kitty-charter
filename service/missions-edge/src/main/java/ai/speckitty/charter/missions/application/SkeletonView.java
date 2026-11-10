package ai.speckitty.charter.missions.application;

import java.util.Objects;

/**
 * One skeleton as its read serves it: the stored bytes as text when they are valid UTF-8, else as base64 (RFC 4648,
 * with padding), with the digest and size of the stored bytes.
 *
 * @param name its one-segment name (D9)
 * @param path its path beside its mission type, {@code templates/} and its path there
 * @param missionTypeId the id of its mission type
 * @param encoding how {@code content} spells the bytes
 * @param content the stored bytes, in that spelling
 * @param sha256 the digest of the stored bytes, {@code sha256:} and 64 lower-case hex digits
 * @param size the number of stored bytes
 */
public record SkeletonView(
        String name, String path, String missionTypeId, Encoding encoding, String content, String sha256, int size) {

    /** How a skeleton's content spells its bytes. */
    public enum Encoding {
        /** The bytes are valid UTF-8, and the content is their text. */
        TEXT,
        /** The bytes are not valid UTF-8, and the content is their base64 spelling. */
        BASE64
    }

    /**
     * Checks the fields.
     *
     * @param name the name
     * @param path the path
     * @param missionTypeId the type's id
     * @param encoding the encoding
     * @param content the content
     * @param sha256 the digest
     * @param size the size
     */
    public SkeletonView {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(missionTypeId, "missionTypeId");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(sha256, "sha256");
    }
}
