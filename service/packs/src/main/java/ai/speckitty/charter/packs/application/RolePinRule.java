package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RoleBearer;
import ai.speckitty.charter.packs.domain.RolePins;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The role-pin rule of ruling D3 (ADR-0030, ADR-0032 decision 4), judged over the declaring pack's own closure with the
 * write in place, as {@link FallbackProfileRule} judges a fallback: each pin the pack declares names a profile its
 * closure holds ({@code role_pin_unknown}) that fits the pinned role, as its {@code role} or one of its
 * {@code alt-roles}, with no exemption for a human-performed profile ({@code role_pin_not_fitting}); and no two packs
 * of the closure pin one role to different profiles, since pins are tighten-only ({@code role_pin_conflict}).
 */
final class RolePinRule {

    /**
     * A breach of the rule.
     *
     * @param code the refusal code
     * @param detail what is wrong, naming the pack, the role and the profile
     * @param packs the packs involved beyond the closure's roots, in closure order
     */
    record Breach(RefusalCode code, String detail, List<PackIdentifier> packs) {
        Breach {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(detail, "detail");
            packs = List.copyOf(packs);
        }
    }

    private RolePinRule() {}

    /**
     * Judges one pack's pins and the pins of its closure.
     *
     * @param pack the declaring pack's configured identifier, the root of {@code closure}
     * @param closure the declaring pack's own closure
     * @param profiles where the winning profile's entity is read
     * @return the first breach: each of the pack's pins in authored order, unknown then not fitting, then each role
     *     pinned in the closure, in closure order, for a conflict; empty when the pins hold
     */
    static Optional<Breach> check(PackIdentifier pack, ClosureView closure, FallbackProfileRule.Profiles profiles) {
        ClosureRoleFacts facts = ClosureRoleFacts.of(closure);
        RolePins own = facts.pinsOf(pack);
        for (Map.Entry<String, String> pin : own.byRole().entrySet()) {
            Optional<Breach> breach = judge(pack, pin.getKey(), pin.getValue(), closure, profiles);
            if (breach.isPresent()) {
                return breach;
            }
        }
        for (String role : facts.pinnedRoles()) {
            if (facts.pinFor(role) instanceof ClosureRoleFacts.PinFact.Conflict conflict) {
                String pins = conflict.packs().stream()
                        .map(declaring -> "pack " + declaring.value() + " pins it to "
                                + facts.pinsOf(declaring).pinOf(role).orElseThrow())
                        .collect(Collectors.joining(", "));
                return Optional.of(new Breach(
                        RefusalCode.ROLE_PIN_CONFLICT,
                        "the role " + role + " is pinned to different profiles: " + pins
                                + "; pins are tighten-only, so every pack of a closure that pins a role pins it to one"
                                + " profile",
                        conflict.packs()));
            }
        }
        return Optional.empty();
    }

    private static Optional<Breach> judge(
            PackIdentifier pack,
            String role,
            String profileId,
            ClosureView closure,
            FallbackProfileRule.Profiles profiles) {
        String named = "the role pin " + role + ": " + profileId + " of pack " + pack.value();
        Optional<RoleBearer> held = FallbackProfileRule.heldProfile(profileId, closure, profiles);
        if (held.isEmpty()) {
            return Optional.of(new Breach(
                    RefusalCode.ROLE_PIN_UNKNOWN,
                    named + " names no agent profile the pack or a pack it depends on holds",
                    List.of()));
        }
        RoleBearer profile = held.get();
        if (!role.equals(profile.role()) && !profile.altRoles().contains(role)) {
            return Optional.of(new Breach(
                    RefusalCode.ROLE_PIN_NOT_FITTING,
                    named + " names a profile of role " + profile.role()
                            + (profile.altRoles().isEmpty() ? "" : " and alt-roles " + profile.altRoles())
                            + "; a pinned profile declares the pinned role as its role or one of its alt-roles",
                    List.of()));
        }
        return Optional.empty();
    }
}
