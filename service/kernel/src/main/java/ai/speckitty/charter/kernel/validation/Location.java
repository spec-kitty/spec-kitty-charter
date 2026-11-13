package ai.speckitty.charter.kernel.validation;

import java.io.Serializable;
import java.util.Objects;

/**
 * Where a {@link Reason} points: a file, and a JSON pointer into it (RFC 6901). Typed and immutable, in place of
 * upstream's mutable {@code Evidence} map.
 *
 * <p>The empty pointer is the whole file, as RFC 6901 reads it. The empty file is no file: a value object's invariant
 * has no file to point at, and its reasons carry {@link #NOWHERE}.
 *
 * @param file the file, relative to the subject's root; empty for none
 * @param pointer the JSON pointer into the file; empty for the whole file
 */
public record Location(String file, String pointer) implements Serializable {

    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /** No file, and so no place in one. */
    public static final Location NOWHERE = new Location("", "");

    /** Rejects a null part, and a pointer without a file. */
    public Location {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(pointer, "pointer");
        if (file.isEmpty() && !pointer.isEmpty()) {
            throw new IllegalArgumentException("A pointer needs a file to point into: " + pointer);
        }
        if (!pointer.isEmpty() && !pointer.startsWith("/")) {
            throw new IllegalArgumentException("A JSON pointer starts with '/': " + pointer);
        }
    }

    /**
     * The whole of a file.
     *
     * @param file the file, relative to the subject's root
     * @return the location of the file
     * @throws IllegalArgumentException when the file is empty
     */
    public static Location in(String file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("A location in a file names the file; use Location.NOWHERE for none.");
        }
        return new Location(file, "");
    }

    /**
     * A place in this location's file.
     *
     * @param jsonPointer the JSON pointer into the file
     * @return the location at that pointer
     * @throws IllegalArgumentException when this location names no file, or the pointer does not start with '/'
     */
    public Location at(String jsonPointer) {
        return new Location(file, jsonPointer);
    }

    /**
     * Whether this location names a file.
     *
     * @return false for {@link #NOWHERE}
     */
    public boolean isPlaced() {
        return !file.isEmpty();
    }
}
