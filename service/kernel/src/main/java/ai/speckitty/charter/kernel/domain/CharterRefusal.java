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

    /** The parts of the request body refused; empty but for a {@code 422 activation_invalid} (ADR-0020 D10). */
    private final transient List<RefusalViolation> violations;

    /** The packs that require or lock the artifact; empty but for a {@code 409 artifact_required}/{@code _locked}. */
    private final transient List<RequiringPack> requiredBy;

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
        this(code, detail, packs, List.of(), List.of());
    }

    private CharterRefusal(
            RefusalCode code,
            String detail,
            List<RefusalPack> packs,
            List<RefusalViolation> violations,
            List<RequiringPack> requiredBy) {
        super(detail);
        this.code = Objects.requireNonNull(code, "code");
        this.packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
        this.violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
        this.requiredBy = List.copyOf(Objects.requireNonNull(requiredBy, "requiredBy"));
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
        this.violations = List.of();
        this.requiredBy = List.of();
    }

    /**
     * A refusal that names the parts of the request body it refuses, as a {@code 422 activation_invalid} does
     * (ADR-0020 D10).
     *
     * @param code what is refused
     * @param detail a sentence for the reader that names what was refused and why
     * @param violations the refused parts of the request body
     * @return the refusal
     */
    public static CharterRefusal withViolations(RefusalCode code, String detail, List<RefusalViolation> violations) {
        return new CharterRefusal(code, detail, List.of(), violations, List.of());
    }

    /**
     * A refusal that names the packs requiring or locking an artifact, as a {@code 409 artifact_required} or
     * {@code artifact_locked} does (ADR-0020 D10).
     *
     * @param code what is refused
     * @param detail a sentence for the reader that names what was refused and why
     * @param requiredBy the packs that require or lock the artifact
     * @return the refusal
     */
    public static CharterRefusal withRequiredBy(RefusalCode code, String detail, List<RequiringPack> requiredBy) {
        return new CharterRefusal(code, detail, List.of(), List.of(), requiredBy);
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

    /**
     * The parts of the request body the refusal names.
     *
     * @return the violations, unmodifiable; empty when the refusal names none
     */
    public List<RefusalViolation> violations() {
        return violations;
    }

    /**
     * The packs that require or lock the artifact the refusal is about.
     *
     * @return the requiring packs, unmodifiable; empty when the refusal names none
     */
    public List<RequiringPack> requiredBy() {
        return requiredBy;
    }
}
