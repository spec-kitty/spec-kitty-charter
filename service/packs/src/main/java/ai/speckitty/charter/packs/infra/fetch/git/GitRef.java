package ai.speckitty.charter.packs.infra.fetch.git;

/**
 * A ref of the grammar of research R9: a commit id or a {@code refs/heads/} or {@code refs/tags/} name.
 *
 * @param value the ref, as validated
 * @param kind what the ref names
 */
public record GitRef(String value, Kind kind) {

    /** What a ref names. */
    public enum Kind {
        /** A full 40- or 64-digit commit id. */
        COMMIT,
        /** A branch, {@code refs/heads/...}. */
        BRANCH,
        /** A tag, {@code refs/tags/...}. */
        TAG
    }
}
