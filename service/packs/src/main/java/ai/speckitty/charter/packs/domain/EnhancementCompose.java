package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The two compose checks of a step enhancement (ADR-0066 decision 8, FR-002). Not yet built: every check passes.
 */
public final class EnhancementCompose {

    private EnhancementCompose() {}

    /**
     * A step enhancement whose target is not held.
     *
     * @param code the refusal code
     * @param enhancement the enhancement
     * @param target the step it names
     * @param packs the packs the refusal names
     * @param detail what is wrong
     */
    public record TargetViolation(
            RefusalCode code, Urn enhancement, Urn target, List<RefusalPack> packs, String detail) {

        /**
         * Checks the fields.
         *
         * @param code the refusal code
         * @param enhancement the enhancement
         * @param target the step it names
         * @param packs the packs the refusal names
         * @param detail what is wrong
         */
        public TargetViolation {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(enhancement, "enhancement");
            Objects.requireNonNull(target, "target");
            packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
            Objects.requireNonNull(detail, "detail");
        }

        /**
         * The wire refusal.
         *
         * @return the refusal
         */
        public CharterRefusal toCharterRefusal() {
            return new CharterRefusal(code, detail, packs);
        }
    }

    /**
     * An enhancement of a step whose mission type is locked.
     *
     * @param enhancement the enhancement
     * @param target the step
     * @param missionType the step's mission type
     * @param packs the packs the refusal names
     * @param detail what is wrong
     */
    public record LockedEnhancement(
            Urn enhancement, Urn target, Urn missionType, List<RefusalPack> packs, String detail) {

        /**
         * Checks the fields.
         *
         * @param enhancement the enhancement
         * @param target the step
         * @param missionType the mission type
         * @param packs the packs the refusal names
         * @param detail what is wrong
         */
        public LockedEnhancement {
            Objects.requireNonNull(enhancement, "enhancement");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(missionType, "missionType");
            packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
            Objects.requireNonNull(detail, "detail");
        }
    }

    /**
     * C37, not yet built.
     *
     * @param closure the composed closure
     * @param enhancing the enhancing pack
     * @param edges the edges it writes
     * @return empty
     */
    public static Optional<TargetViolation> targetHeld(
            List<PackUnderResolution> closure, PackUnderResolution enhancing, Collection<Relationship> edges) {
        return Optional.empty();
    }

    /**
     * The inherited lock, not yet built.
     *
     * @param closure the composed closure
     * @param edgesOf the edges each pack writes
     * @param policy the closure's policies
     * @return empty
     */
    public static List<LockedEnhancement> lockedTargets(
            List<PackUnderResolution> closure,
            Function<PackUnderResolution, ? extends Collection<Relationship>> edgesOf,
            PerPackPolicy policy) {
        return List.of();
    }
}
