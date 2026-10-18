package ai.speckitty.charter.domain;

import java.util.Objects;

/** A named refusal. The charter fails loud: never an empty success in place of one of these. */
public final class CharterRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final RefusalCode code;

    public CharterRefusal(RefusalCode code, String detail) {
        super(detail);
        this.code = Objects.requireNonNull(code, "code");
    }

    public CharterRefusal(RefusalCode code, String detail, Throwable cause) {
        super(detail, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public RefusalCode code() { return code; }
}
