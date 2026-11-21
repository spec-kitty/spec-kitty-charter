package ai.speckitty.charter.packvalidation.placement;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.PackContents.FileRead;
import java.util.List;
import java.util.Optional;

/**
 * The level rules (issue 48; #554, S2, R2 rows 35 and 36): a pack whose descriptor is absent or declares no level is
 * warned {@link PlacementCodes#PACK_LEVEL_MISSING}; a declared level other than the configured one is
 * {@link PlacementCodes#PACK_LEVEL_MISMATCH}. They fire only when the pack has a configured level, never for a bare
 * path, and only on an absent or a read descriptor: an unreadable one speaks for itself.
 */
public final class LevelRules {

    /** Where a level finding is placed: the descriptor, as a whole. */
    static final String PACK_YAML = "pack.yaml";

    /** Every level rule. */
    public static final Rule<Placement> RULES = LevelRules::level;

    private LevelRules() {}

    static List<Reason> level(Placement placement) {
        if (placement.configuredLevel().isEmpty()) {
            return List.of();
        }
        PackLevel configured = placement.configuredLevel().get();
        String name = placement.name();
        return switch (placement.read().contents().descriptor()) {
            case FileRead.Absent<PackDescriptor> absent ->
                List.of(finding(
                        PlacementCodes.PACK_LEVEL_MISSING,
                        configured.token() + " pack " + name + " has no pack.yaml; the configured level "
                                + configured.token() + " is used"));
            case FileRead.Unreadable<PackDescriptor> unreadable -> List.of();
            case FileRead.Read<PackDescriptor> read -> declared(name, configured, read.value());
        };
    }

    static List<Reason> declared(String name, PackLevel configured, PackDescriptor descriptor) {
        Optional<PackLevel> declared = descriptor.declaredLevel();
        if (declared.isEmpty()) {
            return List.of(finding(
                    PlacementCodes.PACK_LEVEL_MISSING,
                    "pack.yaml of " + name + " has no pack_level; the configured level " + configured.token()
                            + " is used"));
        }
        if (declared.get() != configured) {
            return List.of(finding(
                    PlacementCodes.PACK_LEVEL_MISMATCH,
                    "Pack '" + name + "' is configured as " + configured.token() + " but declares pack_level "
                            + declared.get().token()));
        }
        return List.of();
    }

    /**
     * The pack-set check's finding for a pack it cannot place (#478, WP03 review M3): no level is configured and its
     * {@code pack.yaml} declares none, so {@code CLOSURE} cannot judge it. A violation, the twin of the warning
     * {@link PlacementCodes#PACK_LEVEL_MISSING}, at {@code pack.yaml}; the level findings keep one home here.
     *
     * @param name the pack's configured name
     * @return the failing reason
     */
    public static Reason unknownLevel(String name) {
        return finding(
                PlacementCodes.PACK_LEVEL_MISSING_VIOLATION,
                PACK_YAML + " of " + name + " declares no pack_level and none is configured");
    }

    /**
     * A placement finding: at {@code pack.yaml} as a whole, its rule named by its code's token, about no element
     * ({@link Origin#NONE}).
     */
    static Reason finding(ReasonCode code, String detail) {
        return new Reason(code.value(), Origin.NONE, Evaluation.FAIL, code, Location.in(PACK_YAML), detail);
    }
}
