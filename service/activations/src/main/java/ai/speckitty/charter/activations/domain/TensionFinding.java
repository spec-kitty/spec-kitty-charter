package ai.speckitty.charter.activations.domain;

import java.util.List;
import java.util.Objects;

/**
 * An {@code unreconciled_tension} finding (ADR-0045 decisions 11 and 12, ruling G3): a tension in force that no artifact
 * in force reconciles, with the ways the activation can resolve it. A finding never refuses a read or a write; it says
 * what to do.
 *
 * <p>{@link Resolution#RECONCILE} is always offered: an author can always select or write an artifact that reconciles
 * both sides. {@link Resolution#DEACTIVATE_ONE_SIDE} is offered only when the exclusion write would accept at least one
 * side, that is a side neither locked nor required on the floor, judged as the exclusion write judges it.
 *
 * @param source the side with the smaller URN
 * @param target the side with the larger URN
 * @param sourceExcludable whether the exclusion write would accept the source
 * @param targetExcludable whether the exclusion write would accept the target
 */
public record TensionFinding(NodeRef source, NodeRef target, boolean sourceExcludable, boolean targetExcludable) {

    /** The finding's code, as the contract spells it. */
    public static final String CODE = "unreconciled_tension";

    /** A way to resolve an unreconciled tension. */
    public enum Resolution {
        /** Select or write an artifact that reconciles both sides. */
        RECONCILE("reconcile"),
        /** Deactivate one side through the exclusion write. */
        DEACTIVATE_ONE_SIDE("deactivate_one_side");

        private final String token;

        Resolution(String token) {
            this.token = token;
        }

        /**
         * The resolution as the contract spells it.
         *
         * @return the token
         */
        public String token() {
            return token;
        }
    }

    /**
     * Checks the fields.
     *
     * @param source the source
     * @param target the target
     * @param sourceExcludable whether the source is excludable
     * @param targetExcludable whether the target is excludable
     */
    public TensionFinding {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
    }

    /**
     * The finding of an unreconciled tension.
     *
     * @param tension the tension in force; one an artifact in force reconciles has no finding
     * @param sourceExcludable whether the exclusion write would accept its source
     * @param targetExcludable whether the exclusion write would accept its target
     * @return the finding
     * @throws IllegalArgumentException when an artifact in force reconciles the tension
     */
    public static TensionFinding of(TensionSnapshot tension, boolean sourceExcludable, boolean targetExcludable) {
        if (!tension.reconciledBy().isEmpty()) {
            throw new IllegalArgumentException("a reconciled tension has no finding: "
                    + tension.source().urn() + " " + tension.target().urn());
        }
        return new TensionFinding(tension.source(), tension.target(), sourceExcludable, targetExcludable);
    }

    /**
     * The resolutions, {@code reconcile} first.
     *
     * @return {@code reconcile}, then {@code deactivate_one_side} when a side is excludable
     */
    public List<Resolution> resolutions() {
        return sourceExcludable || targetExcludable
                ? List.of(Resolution.RECONCILE, Resolution.DEACTIVATE_ONE_SIDE)
                : List.of(Resolution.RECONCILE);
    }
}
