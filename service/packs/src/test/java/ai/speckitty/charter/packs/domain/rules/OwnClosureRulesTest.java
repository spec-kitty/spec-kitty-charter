package ai.speckitty.charter.packs.domain.rules;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackUlid;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ClosureFacts;
import ai.speckitty.charter.packs.domain.ClosureFault;
import ai.speckitty.charter.packs.domain.MergedClosure;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.PackOrigins;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.ProfileEnhancementGateway;
import ai.speckitty.charter.packs.domain.RolePins;
import ai.speckitty.charter.packs.domain.ValidationWalker;
import ai.speckitty.charter.packs.fixtures.FixtureUlids;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.Specialization;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The own-closure rules from the factory, run by the one walker (#556 FR-005, #638): over a closure's facts, the
 * fallback profile first, then the pins, then the profile enhancements, every breach of each rule, in one phase.
 */
@DisplayName("PackRulesets.ownClosure: every breach of a pack's own closure, run by the ValidationWalker (#638)")
class OwnClosureRulesTest {

    private static final PackIdentifier ACME = new PackIdentifier("acme");
    private static final PackIdentifier BASE = new PackIdentifier("base");

    /** A closure's facts, as a test sets them: it holds no profile; pins, conflicts and enhancements are given. */
    private static final class Facts implements ClosureFacts {
        private final Map<String, String> pins = new LinkedHashMap<>();
        private final Map<String, List<PackIdentifier>> conflicts = new LinkedHashMap<>();
        private Optional<ProfileEnhancements> enhancements = Optional.empty();

        @Override
        public boolean holds(ArtifactKind kind, String id) {
            return false;
        }

        @Override
        public Optional<PackIdentifier> winnerPackOf(ArtifactKind kind, String id) {
            return Optional.empty();
        }

        @Override
        public boolean isLocked(Urn urn) {
            return false;
        }

        @Override
        public RolePins pinsOf(PackIdentifier pack) {
            return pack.equals(ACME)
                    ? new RolePins(pins)
                    : new RolePins(Map.of("reviewer", "other", "implementer", "other"));
        }

        @Override
        public List<String> pinnedRoles() {
            return List.copyOf(conflicts.keySet());
        }

        @Override
        public List<PackIdentifier> pinConflict(String role) {
            return conflicts.getOrDefault(role, List.of());
        }

        @Override
        public Optional<ProfileEnhancements> profileEnhancements() {
            return enhancements;
        }

        @Override
        public List<RefusalPack> named(List<PackIdentifier> packs) {
            return packs.stream()
                    .map(pack -> new RefusalPack(new PackUlid(FixtureUlids.of(pack.value())), pack, pack.value()))
                    .toList();
        }
    }

    private static List<ClosureFault> walk(
            Optional<String> fallback,
            ClosureFacts facts,
            ProfileReader profiles,
            Optional<ProfileEnhancementGateway> merge) {
        return ValidationWalker.of(PackRulesets.ownClosure(ACME, fallback, profiles, merge)).walk(facts);
    }

    private static List<ClosureFault> walk(Optional<String> fallback, ClosureFacts facts) {
        return walk(fallback, facts, (holder, id) -> Optional.empty(), Optional.empty());
    }

    private static Facts pinningTwoUnknown() {
        Facts facts = new Facts();
        facts.pins.put("reviewer", "nobody");
        facts.pins.put("implementer", "nobody-either");
        return facts;
    }

    @Test
    @DisplayName("the factory hands out one collect-all phase: the fallback, the pins, then the enhancements")
    void oneCollectAllPhase() {
        List<ValidationWalker.Phase<ClosureFacts>> phases =
                PackRulesets.ownClosure(ACME, Optional.empty(), (holder, id) -> Optional.empty(), Optional.empty());

        assertThat(phases).singleElement().satisfies(phase -> assertThat(phase.rules())
                .extracting(ValidationWalker.Selected::name)
                .containsExactly("fallback_profile", "role_pins", "profile_enhancements"));
    }

    @Test
    @DisplayName("the fallback rule runs only where a fallback is declared; the pins and the enhancements run everywhere")
    void thePredicatesSelect() {
        List<ValidationWalker.Selected<ClosureFacts>> rules = PackRulesets.ownClosure(
                        ACME, Optional.empty(), (holder, id) -> Optional.empty(), Optional.empty())
                .getFirst()
                .rules();
        Facts plain = new Facts();

        assertThat(rules).extracting(rule -> rule.when().test(plain)).containsExactly(false, true, true);
    }

    @Test
    @DisplayName("the fallback's breach comes first, then every pin's, in one verdict")
    void theFallbackThenEveryPin() {
        List<ClosureFault> faults = walk(Optional.of("generic-agent"), pinningTwoUnknown());

        assertThat(faults)
                .extracting(ClosureFault::code)
                .containsExactly(
                        RefusalCode.FALLBACK_PROFILE_UNKNOWN,
                        RefusalCode.ROLE_PIN_UNKNOWN,
                        RefusalCode.ROLE_PIN_UNKNOWN);
        assertThat(faults.get(1).detail()).contains("the role pin reviewer: nobody");
        assertThat(faults.get(2).detail()).contains("the role pin implementer: nobody-either");
    }

    @Test
    @DisplayName("a fallback or pin breach is about the declaring pack and the profile it names (R-o)")
    void aFallbackOrPinBreachIsAboutTheDeclaringPackAndTheProfile() {
        List<ClosureFault> faults = walk(Optional.of("generic-agent"), pinningTwoUnknown());

        assertThat(faults)
                .extracting(fault -> fault.origin().id())
                .containsExactly(
                        "acme::agent_profile:generic-agent",
                        "acme::agent_profile:nobody",
                        "acme::agent_profile:nobody-either");
        assertThat(faults)
                .extracting(fault -> fault.reason().rule())
                .as("the rule stays the check's name")
                .containsOnly(ClosureFault.RULE);
    }

    @Test
    @DisplayName("no fallback declared: only the pins are judged")
    void noFallbackDeclaredJudgesOnlyThePins() {
        assertThat(walk(Optional.empty(), pinningTwoUnknown()))
                .extracting(ClosureFault::code)
                .containsExactly(RefusalCode.ROLE_PIN_UNKNOWN, RefusalCode.ROLE_PIN_UNKNOWN);
    }

    @Test
    @DisplayName("every role whose pins conflict is a fault of its own, in order, naming its packs (review 1)")
    void everyConflictingRoleIsAFault() {
        Facts facts = new Facts();
        facts.pins.put("reviewer", "a");
        facts.pins.put("implementer", "b");
        facts.conflicts.put("reviewer", List.of(ACME, BASE));
        facts.conflicts.put("implementer", List.of(BASE, ACME));
        ProfileReader fits = (holder, id) -> Optional.empty();

        List<ClosureFault> conflicts = walk(Optional.empty(), facts, fits, Optional.empty()).stream()
                        .filter(fault -> fault.code() == RefusalCode.ROLE_PIN_CONFLICT)
                        .toList();

        assertThat(conflicts).hasSize(2);
        assertThat(conflicts.get(0).detail()).contains("the role reviewer is pinned to different profiles");
        assertThat(conflicts.get(0).packs())
                .extracting(pack -> pack.packId().value())
                .containsExactly("acme", "base");
        assertThat(conflicts.get(1).detail()).contains("the role implementer is pinned to different profiles");
        assertThat(conflicts.get(1).packs())
                .extracting(pack -> pack.packId().value())
                .containsExactly("base", "acme");
        assertThat(conflicts)
                .as("a conflict is about the first pack that pins the role (R-o)")
                .extracting(ClosureFault::origin)
                .containsExactly(PackOrigins.of(ACME), PackOrigins.of(BASE));
    }

    @Test
    @DisplayName(
            "every enhanced profile that loses content is a fault of its own, naming the enhancing pack (review 1)")
    void everyEnhancedProfileThatDropsIsAFault() {
        Urn first = Urn.parse("agent_profile:first").orElseThrow();
        Urn second = Urn.parse("agent_profile:second").orElseThrow();
        Urn enhancer = Urn.parse("agent_profile:enhancer").orElseThrow();
        Map<Urn, MergedClosure.Winner> winners = Map.of(
                first, winner(first, BASE),
                second, winner(second, BASE),
                enhancer, winner(enhancer, ACME));
        Map<Urn, List<MergedClosure.Winner>> enhancersOf = new LinkedHashMap<>();
        enhancersOf.put(first, List.of(winner(enhancer, ACME)));
        enhancersOf.put(second, List.of(winner(enhancer, ACME)));
        Facts facts = new Facts();
        facts.enhancements = Optional.of(new ClosureFacts.ProfileEnhancements(winners, enhancersOf));
        ProfileReader profiles = (holder, id) -> Optional.of(profile(id));
        ProfileEnhancementGateway dropsEverything = new ProfileEnhancementGateway() {
            @Override
            public Optional<Dropped> firstDrop(CharterArtifact base, List<Enhancing> inClosureOrder) {
                return Optional.of(new Dropped(
                        inClosureOrder.getFirst().pack(),
                        "purpose",
                        "the enhancement of " + ((AgentProfile) base).id().value() + " drops purpose"));
            }

            @Override
            public Composed compose(List<Layer> rootToLeaf) {
                throw new UnsupportedOperationException("the rule only judges drops");
            }
        };

        List<ClosureFault> faults = walk(Optional.empty(), facts, profiles, Optional.of(dropsEverything));

        assertThat(faults)
                .extracting(ClosureFault::code)
                .containsExactly(
                        RefusalCode.PROFILE_ENHANCEMENT_DROPS_FIELD, RefusalCode.PROFILE_ENHANCEMENT_DROPS_FIELD);
        assertThat(faults.get(0).detail()).contains("first");
        assertThat(faults.get(1).detail()).contains("second");
        assertThat(faults)
                .allSatisfy(fault -> assertThat(fault.packs())
                        .extracting(pack -> pack.packId().value())
                        .containsExactly("acme"));
        assertThat(faults)
                .as("each drop is about the enhancing pack's profile that breaks the merge (R-o)")
                .extracting(ClosureFault::origin)
                .containsExactly(PackOrigins.of(ACME, enhancer), PackOrigins.of(ACME, enhancer));
    }

    @Test
    @DisplayName("a closure whose layers do not merge has no enhancement fault: the merge's owners refuse it")
    void aClosureThatDoesNotMergeHasNoEnhancementFault() {
        assertThat(walk(Optional.empty(), new Facts())).isEmpty();
    }

    private static MergedClosure.Winner winner(Urn urn, PackIdentifier pack) {
        return new MergedClosure.Winner(
                urn,
                pack,
                new PackUlid(FixtureUlids.of(pack.value())),
                PackLevel.ORG,
                new PerPackPolicy.Level(MutabilityLevel.OPEN, Optional.empty()));
    }

    private static AgentProfile profile(String id) {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier(id))
                .packId(BASE)
                .name(id)
                .role("implementer")
                .purpose("Do the work.")
                .specialization(new Specialization("Anything", Optional.empty(), Optional.empty(), Optional.empty()))
                .build();
    }
}
