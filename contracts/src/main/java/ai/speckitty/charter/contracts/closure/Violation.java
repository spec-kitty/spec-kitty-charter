package ai.speckitty.charter.contracts.closure;

/**
 * One finding of the checker: where it is and why it fails the convention.
 *
 * @param pointer the JSON Pointer of the response schema position, or of the offending keyword
 * @param reason  why the checker cannot prove the position closed
 */
public record Violation(JsonPointer pointer, String reason) {

    @Override
    public String toString() {
        return (pointer.isRoot() ? "(document root)" : pointer.toString()) + ": " + reason;
    }
}
