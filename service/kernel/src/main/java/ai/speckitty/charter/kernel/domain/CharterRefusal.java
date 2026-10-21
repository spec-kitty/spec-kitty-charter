package ai.speckitty.charter.kernel.domain;

import java.util.Objects;

/** A named refusal. The charter fails loud: never an empty success in place of one of these. */
public final class CharterRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** What is refused; part of the serialized form. */
    private final RefusalCode code;

    /**
     * A refusal without an underlying cause.
     *
     * @param code what is refused
     * @param detail a sentence for the reader that names what was refused and why
     */
    public CharterRefusal(RefusalCode code, String detail) {
        super(detail);
        this.code = Objects.requireNonNull(code, "code");
    }

    /**
     * A refusal caused by another failure, for example a YAML parse error.
     *
     * @param code what is refused
     * @param detail a sentence for the reader that names what was refused and why
     * @param cause the failure that led to the refusal
     */
    public CharterRefusal(RefusalCode code, String detail, Throwable cause) {
        super(detail, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    /**
     * The code of the refusal.
     *
     * @return what is refused
     */
    public RefusalCode code() {
        return code;
    }
}
