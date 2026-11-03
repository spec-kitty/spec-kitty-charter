package ai.speckitty.charter.kernel.domain;

import java.util.List;
import java.util.Objects;

/** A named refusal. The charter fails loud: never an empty success in place of one of these. */
public final class CharterRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** What is refused; part of the serialized form. */
    private final RefusalCode code;

    /** The packs the refusal names, the violating pack first; empty for a refusal about no particular pack. */
    private final transient List<RefusalPack> packs;

    /**
     * A refusal without an underlying cause.
     *
     * @param code what is refused
     * @param detail a sentence for the reader that names what was refused and why
     */
    public CharterRefusal(RefusalCode code, String detail) {
        this(code, detail, List.of());
    }

    /**
     * A refusal that names the packs that broke the rule.
     *
     * @param code what is refused
     * @param detail a sentence for the reader that names what was refused and why
     * @param packs the packs the refusal names, the violating pack first
     */
    public CharterRefusal(RefusalCode code, String detail, List<RefusalPack> packs) {
        super(detail);
        this.code = Objects.requireNonNull(code, "code");
        this.packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
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
        this.packs = List.of();
    }

    /**
     * The code of the refusal.
     *
     * @return what is refused
     */
    public RefusalCode code() {
        return code;
    }

    /**
     * The packs the refusal names, the violating pack first.
     *
     * @return the packs; empty when the refusal is about no particular pack
     */
    public List<RefusalPack> packs() {
        return packs;
    }
}
