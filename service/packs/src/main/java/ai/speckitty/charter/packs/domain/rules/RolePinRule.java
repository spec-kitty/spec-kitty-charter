package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RoleBearer;
import ai.speckitty.charter.kernel.validation.Origin;
import ai.speckitty.charter.packs.domain.ClosureFacts;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.PackOrigins;
import ai.speckitty.charter.packs.domain.RolePins;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private RolePinRule() {}

    /**
     * Judges one pack's own pins, every breach (#556, FR-005): each pin in authored order, unknown or not fitting.
     *
     * @param pack the declaring pack's configured identifier, the root of {@code closure}
     * @param closure the declaring pack's own closure
     * @param profiles where the winning profile's entity is read
     * @return the breaches; empty when every pin holds
     */
    static List<ClosureFault> ownPins(PackIdentifier pack, ClosureFacts closure, ProfileReader profiles) {
        List<ClosureFault> breaches = new ArrayList<>();
        RolePins own = closure.pinsOf(pack);
        for (Map.Entry<String, String> pin : own.byRole().entrySet()) {
            judge(pack, pin.getKey(), pin.getValue(), closure, profiles).ifPresent(breaches::add);
        }
        return List.copyOf(breaches);
    }

    /**
     * Judges the pins of a closure against each other (tighten-only): each role pinned in the closure, in closure
     * order, that two packs pin to different profiles.
     *
     * @param closure the declaring pack's own closure
     * @return one fault per conflicting role; empty when the pins agree
     */
    static List<ClosureFault> conflicts(ClosureFacts closure) {
        List<ClosureFault> breaches = new ArrayList<>();
        for (String role : closure.pinnedRoles()) {
            List<PackIdentifier> conflict = closure.pinConflict(role);
            if (conflict.isEmpty()) {
                continue;
            }
            String pins = conflict.stream()
                    .map(declaring -> "pack " + declaring.value() + " pins it to "
                            + closure.pinsOf(declaring).pinOf(role).orElseThrow())
                    .collect(Collectors.joining(", "));
            List<RefusalPack> named = closure.named(conflict);
            breaches.add(ClosureFault.of(
                    PackOrigins.firstOf(named),
                    RefusalCode.ROLE_PIN_CONFLICT,
                    "the role " + role + " is pinned to different profiles: " + pins
                            + "; pins are tighten-only, so every pack of a closure that pins a role pins it to one"
                            + " profile",
                    named));
        }
        return List.copyOf(breaches);
    }

    private static Optional<ClosureFault> judge(
            PackIdentifier pack, String role, String profileId, ClosureFacts closure, ProfileReader profiles) {
        String named = "the role pin " + role + ": " + profileId + " of pack " + pack.value();
        Origin origin = FallbackProfileRule.profileOrigin(pack, profileId);
        Optional<RoleBearer> held = FallbackProfileRule.heldProfile(profileId, closure, profiles);
        if (held.isEmpty()) {
            return Optional.of(ClosureFault.of(
                    origin,
                    RefusalCode.ROLE_PIN_UNKNOWN,
                    named + " names no agent profile the pack or a pack it depends on holds",
                    List.of()));
        }
        RoleBearer profile = held.get();
        if (!role.equals(profile.role()) && !profile.altRoles().contains(role)) {
            return Optional.of(ClosureFault.of(
                    origin,
                    RefusalCode.ROLE_PIN_NOT_FITTING,
                    named + " names a profile of role " + profile.role()
                            + (profile.altRoles().isEmpty() ? "" : " and alt-roles " + profile.altRoles())
                            + "; a pinned profile declares the pinned role as its role or one of its alt-roles",
                    List.of()));
        }
        return Optional.empty();
    }
}
