package ai.speckitty.charter.packs.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackGraph;
import ai.speckitty.charter.packs.domain.RolePins;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The fallback and the role pins in force for one set of packs: the one place that answers them, for the registration
 * rules and for selection.
 *
 * <ul>
 *   <li><b>Fallback</b> (ADR-0068 decision 5): the fallback in force is the one the most derived declaring pack names.
 *       Two declaring packs, neither derived from the other, that name different profiles conflict.
 *   <li><b>Pins</b> (ruling D3): pins are tighten-only. Two packs that pin one role to different profiles conflict,
 *       whatever their derivation.
 * </ul>
 *
 * <p>A pack is derived from another when it builds on it, through {@code depends_on} or {@code parent_pack}, directly or
 * transitively: the derivation the closure's own packs and their resolved edges record ({@link ClosureView#packs()},
 * {@link ClosureView#parentOf}), descended by {@link PackGraph#ancestorsOf}. Nothing is composed or read again.
 */
public final class ClosureRoleFacts {

    /** The fallback in force: declared by one pack, by none, or contested. */
    public sealed interface FallbackFact {

        /**
         * The fallback in force.
         *
         * @param profileId the profile the most derived declaring pack names
         * @param pack the most derived declaring pack
         */
        record Declared(String profileId, PackIdentifier pack) implements FallbackFact {
            /**
             * Checks the fields.
             *
             * @param profileId the profile-id
             * @param pack the declaring pack
             */
            public Declared {
                Objects.requireNonNull(profileId, "profileId");
                Objects.requireNonNull(pack, "pack");
            }
        }

        /** No pack of the set declares a fallback. */
        record None() implements FallbackFact {}

        /**
         * Declaring packs, neither derived from the other, name different fallbacks (ADR-0068
         * {@code fallback_profile_conflict}).
         *
         * @param packs every pack of such a pair, in closure order
         */
        record Conflict(List<PackIdentifier> packs) implements FallbackFact {
            /**
             * Copies the packs.
             *
             * @param packs the packs in conflict
             */
            public Conflict {
                packs = List.copyOf(packs);
            }
        }
    }

    /** The pin in force for one role: declared, absent, or contested. */
    public sealed interface PinFact {

        /**
         * The pin in force.
         *
         * @param profileId the profile the role is pinned to
         * @param pack the most derived pack that pins it
         */
        record Pinned(String profileId, PackIdentifier pack) implements PinFact {
            /**
             * Checks the fields.
             *
             * @param profileId the profile-id
             * @param pack the pinning pack
             */
            public Pinned {
                Objects.requireNonNull(profileId, "profileId");
                Objects.requireNonNull(pack, "pack");
            }
        }

        /** No pack of the set pins the role. */
        record None() implements PinFact {}

        /**
         * Packs pin the role to different profiles ({@code role_pin_conflict}).
         *
         * @param packs every pack that pins the role, in closure order
         */
        record Conflict(List<PackIdentifier> packs) implements PinFact {
            /**
             * Copies the packs.
             *
             * @param packs the packs in conflict
             */
            public Conflict {
                packs = List.copyOf(packs);
            }
        }
    }

    /**
     * One pack of the set, as the facts read it.
     *
     * @param pack the configured identifier
     * @param buildsOn the packs of the set it builds on directly
     * @param fallback the fallback profile its descriptor names
     * @param pins the role pins its policy declares
     */
    record Member(PackIdentifier pack, List<PackIdentifier> buildsOn, Optional<String> fallback, RolePins pins) {
        Member {
            Objects.requireNonNull(pack, "pack");
            buildsOn = List.copyOf(buildsOn);
            Objects.requireNonNull(fallback, "fallback");
            Objects.requireNonNull(pins, "pins");
        }
    }

    private final List<Member> members;
    private final Map<PackIdentifier, Set<PackIdentifier>> ancestors;

    private ClosureRoleFacts(List<Member> members) {
        this.members = List.copyOf(members);
        Map<PackIdentifier, Member> byPack = new LinkedHashMap<>();
        this.members.forEach(member -> byPack.putIfAbsent(member.pack(), member));
        Map<PackIdentifier, Set<PackIdentifier>> along = new LinkedHashMap<>();
        this.members.forEach(member -> along.put(member.pack(), ancestorsOf(member, byPack)));
        this.ancestors = along;
    }

    /**
     * The facts of a closure.
     *
     * @param closure the closure, as one verified read gave it
     * @return the fallback and the pins in force for its packs
     */
    public static ClosureRoleFacts of(ClosureView closure) {
        Objects.requireNonNull(closure, "closure");
        Map<PackUlid, PackIdentifier> byDescriptor = new HashMap<>();
        closure.packs().forEach(entry -> byDescriptor.putIfAbsent(entry.descriptorPackId(), entry.id()));
        Map<PackIdentifier, PackUnderResolution> composed = new HashMap<>();
        closure.mergeInputs()
                .packs()
                .forEach(pack -> composed.putIfAbsent(pack.configured().id(), pack));
        Map<String, PackCompositionFacts> facts = closure.mergeInputs().facts();
        List<Member> members = new ArrayList<>();
        for (OfferingReadService.OfferingEntry entry : closure.packs()) {
            // The edges the offering resolved for the pack and its recorded parent: the read model's projection, which
            // answers for a view whose layer merge is not built from the closure's packs too. PackGraph descends them.
            List<PackIdentifier> buildsOn = Stream.concat(
                            entry.dependencies().stream().map(edge -> new PackUlid(edge.to())),
                            closure.parentOf(entry.descriptorPackId()).stream())
                    .map(byDescriptor::get)
                    .filter(Objects::nonNull)
                    .toList();
            Optional<String> fallback = Optional.ofNullable(composed.get(entry.id()))
                    .flatMap(pack -> pack.descriptor().fallbackProfile());
            RolePins pins = Optional.ofNullable(facts.get(entry.id().value()))
                    .map(held -> held.policy().rolePins())
                    .orElse(RolePins.NONE);
            members.add(new Member(entry.id(), buildsOn, fallback, pins));
        }
        return new ClosureRoleFacts(members);
    }

    /**
     * The facts of a set of packs given as members.
     *
     * @param members the packs, in closure order (dependencies first)
     * @return their facts
     */
    static ClosureRoleFacts of(List<Member> members) {
        return new ClosureRoleFacts(members);
    }

    /**
     * The fallback in force.
     *
     * @return the most derived declaring pack's fallback, none, or the conflict
     */
    public FallbackFact fallbackInForce() {
        List<Member> declaring =
                members.stream().filter(member -> member.fallback().isPresent()).toList();
        if (declaring.isEmpty()) {
            return new FallbackFact.None();
        }
        Set<PackIdentifier> contested = new LinkedHashSet<>();
        for (Member one : declaring) {
            for (Member other : declaring) {
                if (!one.fallback().equals(other.fallback()) && !related(one.pack(), other.pack())) {
                    contested.add(one.pack());
                }
            }
        }
        if (!contested.isEmpty()) {
            return new FallbackFact.Conflict(List.copyOf(contested));
        }
        Member winner = mostDerived(declaring);
        return new FallbackFact.Declared(winner.fallback().orElseThrow(), winner.pack());
    }

    /**
     * The pin in force for a role.
     *
     * @param role the role token
     * @return the pin, none, or the conflict when packs pin the role to different profiles
     */
    public PinFact pinFor(String role) {
        Objects.requireNonNull(role, "role");
        List<Member> pinning = members.stream()
                .filter(member -> member.pins().pinOf(role).isPresent())
                .toList();
        if (pinning.isEmpty()) {
            return new PinFact.None();
        }
        long profiles = pinning.stream()
                .map(member -> member.pins().pinOf(role).orElseThrow())
                .distinct()
                .count();
        if (profiles > 1) {
            return new PinFact.Conflict(pinning.stream().map(Member::pack).toList());
        }
        Member winner = mostDerived(pinning);
        return new PinFact.Pinned(winner.pins().pinOf(role).orElseThrow(), winner.pack());
    }

    /**
     * Every role a pack of the set pins, in closure order and then authored order, each once.
     *
     * @return the pinned roles
     */
    public List<String> pinnedRoles() {
        Set<String> roles = new LinkedHashSet<>();
        members.forEach(member -> roles.addAll(member.pins().byRole().keySet()));
        return List.copyOf(roles);
    }

    /**
     * The pins one pack of the set declares.
     *
     * @param pack the pack's configured identifier
     * @return its pins; {@link RolePins#NONE} for a pack outside the set or one that pins nothing
     */
    public RolePins pinsOf(PackIdentifier pack) {
        Objects.requireNonNull(pack, "pack");
        return members.stream()
                .filter(member -> member.pack().equals(pack))
                .findFirst()
                .map(Member::pins)
                .orElse(RolePins.NONE);
    }

    /** Whether one of the two packs is derived from the other. */
    private boolean related(PackIdentifier one, PackIdentifier other) {
        return derivedFrom(one, other) || derivedFrom(other, one);
    }

    private boolean derivedFrom(PackIdentifier pack, PackIdentifier ancestor) {
        return ancestors.getOrDefault(pack, Set.of()).contains(ancestor);
    }

    /**
     * The most derived of the declaring packs: the last, in closure order, that no other declaring pack is derived
     * from. When the declarations agree, any such pack names the same profile.
     */
    private Member mostDerived(List<Member> declaring) {
        Member winner = declaring.getFirst();
        for (Member candidate : declaring) {
            boolean outranked = declaring.stream()
                    .anyMatch(other ->
                            !other.pack().equals(candidate.pack()) && derivedFrom(other.pack(), candidate.pack()));
            if (!outranked) {
                winner = candidate;
            }
        }
        return winner;
    }

    /** Every pack of the set a member builds on, transitively: {@link PackGraph#ancestorsOf} from what it builds on. */
    private static Set<PackIdentifier> ancestorsOf(Member member, Map<PackIdentifier, Member> byPack) {
        return PackGraph.ancestorsOf(member.buildsOn(), byPack, Member::buildsOn);
    }
}
