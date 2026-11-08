package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.InForceResolution.Edges;
import ai.speckitty.charter.packs.domain.InForceStates.ArtifactState;
import ai.speckitty.charter.packs.domain.InForceStates.Reason;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The in-force resolution over seeded random closures (NFR-001, DIRECTIVE_051: a plain seeded {@link Random}, no
 * property-testing library). For each of {@value #CASES} closures per seed it checks, on the answer alone and with its own
 * small oracle: the FR-029 invariant (every held required id is in force, or satisfied by an artifact in force, or
 * reported), I9, I10 and I11 (conditional on the shape), that no artifact in force is hidden by another, determinism
 * under input order, termination, and the override-cycle refusal. Every assertion names the seed and the case number,
 * so a failure replays.
 */
class InForceResolutionPropertyTest {

    /** The closures generated per seed. */
    static final int CASES = 1_000;

    /** The fixed seeds; each one generates {@link #CASES} closures. */
    static final List<Long> SEEDS = List.of(20_261_108L, 7L);

    private static final List<NodeKind> KINDS =
            List.of(NodeKind.TACTIC, NodeKind.DIRECTIVE, NodeKind.PROCEDURE, NodeKind.SKILL, NodeKind.TOOLGUIDE);
    private static final List<String> LANGUAGES = List.of("go", "java", "python");

    /** One generated closure with the request that is put to it. */
    private record Case(
            MergedClosure closure, Edges edges, InForceRequest request, boolean cyclic, boolean boundOverrider) {}

    @Test
    void everyGeneratedClosureHoldsTheInvariants() {
        int checked = 0;
        for (long seed : SEEDS) {
            Random random = new Random(seed);
            for (int n = 0; n < CASES; n++) {
                check(seed, n, generate(random));
                checked++;
            }
        }
        assertThat(checked).isEqualTo(SEEDS.size() * CASES).isGreaterThanOrEqualTo(1_000);
    }

    private static void check(long seed, int n, Case generated) {
        String what = "seed " + seed + ", case " + n + ": " + generated;
        Optional<InForceStates> answer = answer(generated, what);
        if (generated.cyclic() && hasReachableCycle(generated)) {
            assertThat(answer).as(what + " (an override cycle is refused)").isEmpty();
            return;
        }
        if (answer.isEmpty()) {
            assertThat(generated.boundOverrider())
                    .as(what + " (only a bound or enhancing overrider can be refused as unstable)")
                    .isTrue();
            return;
        }
        InForceStates states = answer.get();
        assertThat(states.states().keySet())
                .as(what)
                .containsExactlyElementsOf(generated.closure().winners().keySet());
        invariants(generated, states, what);
        assertThat(InForceResolution.resolve(
                        generated.closure(),
                        shuffled(generated.edges(), seed + n),
                        shuffled(generated.request(), seed + n)))
                .as(what + " (the answer does not depend on input order)")
                .isEqualTo(states);
    }

    private static Optional<InForceStates> answer(Case generated, String what) {
        try {
            return Optional.of(InForceResolution.resolve(generated.closure(), generated.edges(), generated.request()));
        } catch (CharterRefusal refusal) {
            assertThat(refusal.code()).as(what).isEqualTo(RefusalCode.PACK_UNREADABLE);
            return Optional.empty();
        }
    }

    private static void invariants(Case generated, InForceStates states, String what) {
        Set<Urn> inForce = activeOf(states);
        Map<Urn, Set<Urn>> reach = reachOf(generated, states);
        MergedClosure closure = generated.closure();
        Set<Urn> reported = new HashSet<>();
        states.requiredNotInForce().forEach(entry -> reported.add(entry.urn()));
        for (Urn urn : closure.winners().keySet()) {
            ArtifactState state = states.stateOf(urn).orElseThrow();
            boolean locked = closure.winners().get(urn).policy().level() == MutabilityLevel.LOCKED;
            boolean required = closure.floor().isRequired(urn);
            assertThat(state.required()).as(what + " " + urn + " required flag").isEqualTo(required || locked);
            if (locked || required) {
                assertThat(inForce.contains(urn) || reported.contains(urn) || satisfied(state, inForce, reach, urn))
                        .as(what + " FR-029 for " + urn + ": " + state)
                        .isTrue();
            }
            if (state.active()) {
                enhancedAndBound(generated, inForce, urn, what);
                reach.getOrDefault(urn, Set.of())
                        .forEach(hidden -> assertThat(inForce)
                                .as(what + " " + urn + " in force hides " + hidden)
                                .doesNotContain(hidden));
            }
        }
        assertThat(states.requiredNotHeld())
                .as(what + " requiredNotHeld")
                .containsExactlyElementsOf(
                        new TreeSet<>(closure.floor().unheld().keySet()));
    }

    private static boolean satisfied(ArtifactState state, Set<Urn> inForce, Map<Urn, Set<Urn>> reach, Urn urn) {
        return state.satisfiedBy()
                .filter(by ->
                        inForce.contains(by) && reach.getOrDefault(by, Set.of()).contains(urn))
                .isPresent();
    }

    private static void enhancedAndBound(Case generated, Set<Urn> inForce, Urn urn, String what) {
        Set<Urn> targets = generated.edges().enhances().getOrDefault(urn, Set.of());
        if (!targets.isEmpty()) {
            assertThat(targets.stream().anyMatch(inForce::contains))
                    .as(what + " I11: " + urn + " enhances nothing in force")
                    .isTrue();
        }
        bindingOf(generated.edges(), urn)
                .ifPresent(mission -> assertThat(inForce)
                        .as(what + " I11: " + urn + " outlives " + mission)
                        .contains(mission));
    }

    private static Optional<Urn> bindingOf(Edges edges, Urn urn) {
        if (urn.kind() == NodeKind.ACTION) {
            return Optional.of(Urn.missionType(urn.id().substring(0, urn.id().indexOf('/'))));
        }
        return Optional.ofNullable(edges.bindings().get(urn));
    }

    private static Set<Urn> activeOf(InForceStates states) {
        Set<Urn> active = new HashSet<>();
        states.states().forEach((urn, state) -> {
            if (state.active()) {
                active.add(urn);
            }
        });
        return active;
    }

    /** The transitive overrides, over the edges whose source was not pruned: a pruned overrider withdraws its edges. */
    private static Map<Urn, Set<Urn>> reachOf(Case generated, InForceStates states) {
        Map<Urn, Set<Urn>> direct = new HashMap<>();
        generated.edges().overrides().forEach((source, targets) -> {
            Reason reason = states.stateOf(source).orElseThrow().reason();
            if (reason != Reason.MISSION_TYPE_NOT_IN_FORCE && reason != Reason.ENHANCED_TARGET_NOT_IN_FORCE) {
                targets.stream()
                        .filter(target -> overridable(generated.closure(), target))
                        .forEach(target -> direct.computeIfAbsent(source, k -> new HashSet<>())
                                .add(target));
            }
        });
        Map<Urn, Set<Urn>> reach = new HashMap<>();
        for (Urn source : generated.closure().winners().keySet()) {
            reach.put(source, descendants(direct, source));
        }
        return reach;
    }

    private static Set<Urn> descendants(Map<Urn, Set<Urn>> direct, Urn source) {
        Set<Urn> seen = new HashSet<>();
        List<Urn> todo = new ArrayList<>(direct.getOrDefault(source, Set.of()));
        while (!todo.isEmpty()) {
            Urn next = todo.removeLast();
            if (seen.add(next)) {
                todo.addAll(direct.getOrDefault(next, Set.of()));
            }
        }
        return seen;
    }

    private static boolean overridable(MergedClosure closure, Urn target) {
        return closure.winners().get(target).policy().level() == MutabilityLevel.OPEN;
    }

    /** Whether an override cycle is reachable from the candidate set or the floor, judged on the generated edges. */
    private static boolean hasReachableCycle(Case generated) {
        Map<Urn, Set<Urn>> direct = new HashMap<>();
        generated
                .edges()
                .overrides()
                .forEach((source, targets) -> targets.stream()
                        .filter(target -> overridable(generated.closure(), target))
                        .forEach(target -> direct.computeIfAbsent(source, k -> new HashSet<>())
                                .add(target)));
        Set<Urn> roots = new HashSet<>();
        generated.closure().winners().forEach((urn, winner) -> {
            boolean candidate = generated.request().selected().contains(urn)
                    && !generated.request().deactivated().contains(urn)
                    && generated
                            .request()
                            .admits(generated.closure().languages().getOrDefault(urn, Set.of()));
            if (candidate || winner.policy().level() == MutabilityLevel.LOCKED) {
                roots.add(urn);
            }
        });
        roots.addAll(generated.closure().floor().requiredBy().keySet());
        return roots.stream()
                .filter(direct::containsKey)
                .anyMatch(root -> descendants(direct, root).contains(root)
                        || descendants(direct, root).stream()
                                .anyMatch(node -> descendants(direct, node).contains(node)));
    }

    private static Case generate(Random random) {
        List<Urn> urns = urnsOf(random);
        Map<Urn, MergedClosure.Winner> winners = new LinkedHashMap<>();
        urns.forEach(urn -> winners.put(urn, winner(urn, random)));
        Map<Urn, Set<Urn>> overrides = new LinkedHashMap<>();
        Map<Urn, Set<Urn>> enhances = new LinkedHashMap<>();
        boolean cyclic = edges(random, urns, overrides, enhances);
        boolean bound = overrides.keySet().stream()
                .anyMatch(source -> source.kind() == NodeKind.ACTION || enhances.containsKey(source));
        Map<Urn, List<String>> required = new LinkedHashMap<>();
        Map<Urn, List<String>> unheld = new LinkedHashMap<>();
        floor(random, urns, required, unheld);
        Map<Urn, Set<String>> languages = new LinkedHashMap<>();
        urns.forEach(urn -> languages(random, urn, languages));
        MergedClosure closure =
                new MergedClosure(winners, List.of(), languages, Map.of(), new RequiredFloor(required, unheld));
        return new Case(closure, new Edges(overrides, enhances, Map.of()), request(random, urns), cyclic, bound);
    }

    private static List<Urn> urnsOf(Random random) {
        List<Urn> urns = new ArrayList<>();
        int plain = 3 + random.nextInt(9);
        for (int i = 0; i < plain; i++) {
            urns.add(new Urn(KINDS.get(random.nextInt(KINDS.size())), "a" + i));
        }
        int missions = random.nextInt(3);
        for (int m = 0; m < missions; m++) {
            urns.add(Urn.missionType("m" + m));
            int steps = random.nextInt(3);
            for (int s = 0; s < steps; s++) {
                urns.add(Urn.action("m" + m, "s" + s));
            }
        }
        return urns;
    }

    private static MergedClosure.Winner winner(Urn urn, Random random) {
        int roll = random.nextInt(10);
        MutabilityLevel level =
                roll == 0 ? MutabilityLevel.LOCKED : roll == 1 ? MutabilityLevel.EXTENDABLE : MutabilityLevel.OPEN;
        PerPackPolicy.Level policy = level == MutabilityLevel.OPEN
                ? PerPackPolicy.Level.OPEN
                : new PerPackPolicy.Level(level, Optional.of("id-pack"));
        return new MergedClosure.Winner(urn, new PackIdentifier("pack"), "id-pack", PackLevel.ORG, policy);
    }

    /** Fills the edges; answers whether a back edge was added that may close an override cycle. */
    private static boolean edges(
            Random random, List<Urn> urns, Map<Urn, Set<Urn>> overrides, Map<Urn, Set<Urn>> enhances) {
        boolean cyclic = false;
        for (int i = 0; i < urns.size(); i++) {
            for (int j = i + 1; j < urns.size(); j++) {
                int roll = random.nextInt(14);
                if (roll == 0) {
                    overrides
                            .computeIfAbsent(urns.get(i), k -> new LinkedHashSet<>())
                            .add(urns.get(j));
                } else if (roll == 1
                        && !overrides.getOrDefault(urns.get(i), Set.of()).contains(urns.get(j))) {
                    enhances.computeIfAbsent(urns.get(j), k -> new LinkedHashSet<>())
                            .add(urns.get(i));
                } else if (roll == 2 && random.nextInt(6) == 0) {
                    overrides
                            .computeIfAbsent(urns.get(j), k -> new LinkedHashSet<>())
                            .add(urns.get(i));
                    cyclic = true;
                }
            }
        }
        return cyclic;
    }

    private static void floor(
            Random random, List<Urn> urns, Map<Urn, List<String>> required, Map<Urn, List<String>> unheld) {
        urns.stream().filter(urn -> random.nextInt(4) == 0).forEach(urn -> required.put(urn, List.of("id-pack")));
        if (random.nextInt(5) == 0) {
            unheld.put(new Urn(NodeKind.TACTIC, "ghost" + random.nextInt(3)), List.of("id-pack"));
        }
    }

    private static void languages(Random random, Urn urn, Map<Urn, Set<String>> languages) {
        if (random.nextInt(4) == 0) {
            languages.put(urn, Set.of(LANGUAGES.get(random.nextInt(LANGUAGES.size()))));
        }
    }

    private static InForceRequest request(Random random, List<Urn> urns) {
        Set<Urn> selected = new LinkedHashSet<>();
        Set<Urn> denied = new LinkedHashSet<>();
        for (Urn urn : urns) {
            int roll = random.nextInt(10);
            if (roll < 5) {
                selected.add(urn);
            } else if (roll == 5) {
                denied.add(urn);
            }
        }
        if (random.nextInt(8) == 0) {
            selected.add(new Urn(NodeKind.TACTIC, "stale" + random.nextInt(3)));
        }
        Optional<Set<String>> languages = random.nextInt(3) == 0
                ? Optional.of(Set.of(LANGUAGES.get(random.nextInt(LANGUAGES.size()))))
                : Optional.empty();
        return new InForceRequest(selected, denied, languages, Map.of());
    }

    private static Edges shuffled(Edges edges, long seed) {
        Random random = new Random(seed);
        return new Edges(shuffled(edges.overrides(), random), shuffled(edges.enhances(), random), edges.bindings());
    }

    private static Map<Urn, Set<Urn>> shuffled(Map<Urn, Set<Urn>> edges, Random random) {
        List<Urn> keys = new ArrayList<>(edges.keySet());
        Collections.shuffle(keys, random);
        Map<Urn, Set<Urn>> copy = new LinkedHashMap<>();
        keys.forEach(key -> {
            List<Urn> targets = new ArrayList<>(edges.get(key));
            Collections.shuffle(targets, random);
            copy.put(key, new LinkedHashSet<>(targets));
        });
        return copy;
    }

    private static InForceRequest shuffled(InForceRequest request, long seed) {
        Random random = new Random(seed);
        return new InForceRequest(
                shuffledSet(request.selected(), random),
                shuffledSet(request.deactivated(), random),
                request.languages(),
                new TreeMap<>(request.pins()));
    }

    private static Set<Urn> shuffledSet(Set<Urn> set, Random random) {
        List<Urn> list = new ArrayList<>(set);
        Collections.shuffle(list, random);
        return new LinkedHashSet<>(list);
    }
}
