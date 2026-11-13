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
import java.util.EnumMap;
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
 * reported), that every artifact reported as required but not in force is of the enhancer or binding reason and really
 * grounded out, that an artifact the document or the floor puts forward is in force unless an artifact in force hides it
 * or the enhancer or binding rule prunes it (liveness), I9, I10 and I11 (conditional on the shape), that no artifact in
 * force is hidden by another, determinism under input order (the winners' order, the bindings' and the floor's included),
 * termination, and the override-cycle refusal. Every assertion names the seed and the case number,
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
        Map<Outcome, Integer> seen = new EnumMap<>(Outcome.class);
        Map<String, Integer> shapes = new TreeMap<>();
        for (long seed : SEEDS) {
            Random random = new Random(seed);
            for (int n = 0; n < CASES; n++) {
                seen.merge(check(seed, n, generate(random), shapes), 1, Integer::sum);
            }
        }
        int checked = seen.values().stream().mapToInt(Integer::intValue).sum();
        String tally = seen + " " + shapes;
        assertThat(checked).isEqualTo(SEEDS.size() * CASES).isGreaterThanOrEqualTo(1_000);
        assertThat(seen.getOrDefault(Outcome.ANSWERED, 0)).as(tally).isGreaterThan(checked * 9 / 10);
        assertThat(seen.getOrDefault(Outcome.CYCLE_REFUSED, 0)).as(tally).isPositive();
        assertThat(shapes.getOrDefault(IN_FORCE, 0))
                .as("a meaningful share of the answers has something in force: " + tally)
                .isGreaterThan(seen.getOrDefault(Outcome.ANSWERED, 0) * 9 / 10);
        assertThat(shapes.getOrDefault(NOT_IN_FORCE_REQUIRED, 0)).as(tally).isGreaterThanOrEqualTo(MIN_SHAPE);
        assertThat(shapes.getOrDefault(PUT_IN_FORCE, 0)).as(tally).isGreaterThanOrEqualTo(MIN_SHAPE);
        assertThat(shapes.getOrDefault(SATISFIED, 0)).as(tally).isGreaterThanOrEqualTo(MIN_SHAPE);
        assertThat(shapes.getOrDefault(PRUNED_BY_BINDING, 0)).as(tally).isGreaterThanOrEqualTo(MIN_SHAPE);
    }

    private static final String IN_FORCE = "somethingInForce";
    private static final String NOT_IN_FORCE_REQUIRED = "requiredNotInForce";
    private static final String PUT_IN_FORCE = "requiredPutInForce";
    private static final String SATISFIED = "requiredSatisfiedByAnOverrider";
    private static final String PRUNED_BY_BINDING = "prunedByAnExplicitBinding";

    /** The fewest answers of each shape the generator must reach, so that no invariant above is vacuous. */
    private static final int MIN_SHAPE = 200;

    /** How a generated closure was answered. */
    private enum Outcome {
        ANSWERED,
        CYCLE_REFUSED,
        UNSTABLE_REFUSED
    }

    private static Outcome check(long seed, int n, Case generated, Map<String, Integer> shapes) {
        String what = "seed " + seed + ", case " + n + ": " + generated;
        Optional<InForceStates> answer = answer(generated, what);
        if (generated.cyclic() && hasReachableCycle(generated)) {
            assertThat(answer).as(what + " (an override cycle is refused)").isEmpty();
            return Outcome.CYCLE_REFUSED;
        }
        if (answer.isEmpty()) {
            assertThat(generated.boundOverrider())
                    .as(what + " (only a bound or enhancing overrider can be refused as unstable)")
                    .isTrue();
            return Outcome.UNSTABLE_REFUSED;
        }
        InForceStates states = answer.get();
        assertThat(states.states().keySet())
                .as(what)
                .containsExactlyElementsOf(generated.closure().winners().keySet());
        invariants(generated, states, what);
        shapesOf(generated, states).forEach(shape -> shapes.merge(shape, 1, Integer::sum));
        assertThat(InForceResolution.resolve(
                        shuffled(generated.closure(), seed + n),
                        shuffled(generated.edges(), seed + n),
                        shuffled(generated.request(), seed + n)))
                .as(what + " (the answer does not depend on input order)")
                .isEqualTo(states);
        return Outcome.ANSWERED;
    }

    /** What the answer shows that the invariants are about, so a generator that stops reaching it fails the test. */
    private static Set<String> shapesOf(Case generated, InForceStates states) {
        Set<String> shapes = new TreeSet<>();
        if (!activeOf(states).isEmpty()) {
            shapes.add(IN_FORCE);
        }
        if (!states.requiredNotInForce().isEmpty()) {
            shapes.add(NOT_IN_FORCE_REQUIRED);
        }
        states.states().forEach((urn, state) -> {
            if (state.reason() == Reason.REQUIRED) {
                shapes.add(PUT_IN_FORCE);
            }
            if (!state.satisfiedBy().isEmpty()) {
                shapes.add(SATISFIED);
            }
            if (state.reason() == Reason.MISSION_TYPE_NOT_IN_FORCE
                    && generated.edges().bindings().containsKey(urn)) {
                shapes.add(PRUNED_BY_BINDING);
            }
        });
        return shapes;
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
        Map<Urn, Set<Urn>> reach = reachOf(generated);
        Map<Urn, Set<Urn>> direct = directOverrides(generated);
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
                hiddenBy(generated, direct, urn)
                        .forEach(hidden -> assertThat(inForce)
                                .as(what + " " + urn + " in force hides " + hidden)
                                .doesNotContain(hidden));
            }
        }
        states.requiredNotInForce().forEach(entry -> groundedOut(generated, states, inForce, entry, what));
        liveness(generated, states, inForce, reach, what);
        assertThat(states.requiredNotHeld())
                .as(what + " requiredNotHeld")
                .containsExactlyElementsOf(
                        new TreeSet<>(closure.floor().unheld().keySet()));
    }

    /**
     * A required or locked artifact reported as not in force is out for the enhancer or binding reason alone, and is
     * really grounded out: it enhances only artifacts that are not in force, or is bound to a mission type that is not.
     */
    private static void groundedOut(
            Case generated,
            InForceStates states,
            Set<Urn> inForce,
            InForceStates.RequiredNotInForce entry,
            String what) {
        Urn urn = entry.urn();
        ArtifactState state = states.stateOf(urn).orElseThrow();
        assertThat(entry.reason().name())
                .as(what + " " + urn + " reported as required but not in force")
                .isIn(Reason.ENHANCED_TARGET_NOT_IN_FORCE.name(), Reason.MISSION_TYPE_NOT_IN_FORCE.name());
        assertThat(state.reason().name())
                .as(what + " " + urn + " reason")
                .isEqualTo(entry.reason().name());
        assertThat(state.active())
                .as(what + " " + urn + " reported, so not in force")
                .isFalse();
        assertThat(state.required())
                .as(what + " " + urn + " is required or locked")
                .isTrue();
        if (entry.reason() == InForceStates.PruningReason.ENHANCED_TARGET_NOT_IN_FORCE) {
            Set<Urn> targets = generated.edges().enhances().getOrDefault(urn, Set.of());
            assertThat(targets).as(what + " " + urn + " enhances something").isNotEmpty();
            assertThat(targets.stream().noneMatch(inForce::contains))
                    .as(what + " " + urn + " has no target in force")
                    .isTrue();
        } else {
            Urn mission = bindingOf(generated.edges(), urn).orElseThrow();
            assertThat(inForce).as(what + " " + urn + " is bound to " + mission).doesNotContain(mission);
        }
    }

    /**
     * Liveness: a candidate (selected, not denied, within the language filter), a locked artifact or a required one is in
     * force, unless an artifact in force overrides it (judged over every edge of the case, an upper bound of what any
     * artifact in force hides), or it enhances only artifacts not in force, or is bound to a mission type not in force.
     */
    private static void liveness(
            Case generated, InForceStates states, Set<Urn> inForce, Map<Urn, Set<Urn>> reach, String what) {
        MergedClosure closure = generated.closure();
        InForceRequest request = generated.request();
        closure.winners().forEach((urn, winner) -> {
            boolean candidate = request.selected().contains(urn)
                    && !request.deactivated().contains(urn)
                    && request.admits(closure.languages().getOrDefault(urn, Set.of()));
            boolean put = candidate
                    || winner.policy().level() == MutabilityLevel.LOCKED
                    || closure.floor().isRequired(urn);
            boolean hidden = inForce.stream()
                    .anyMatch(by -> reach.getOrDefault(by, Set.of()).contains(urn));
            if (put && !hidden && !pruned(generated.edges(), inForce, urn)) {
                assertThat(inForce)
                        .as(what + " liveness: " + urn + " is put forward, hidden by nothing in force and grounded: "
                                + states.stateOf(urn).orElseThrow())
                        .contains(urn);
            }
        });
    }

    /** Whether the enhancer or binding rule removes the artifact from a set in force. */
    private static boolean pruned(Edges edges, Set<Urn> inForce, Urn urn) {
        Set<Urn> targets = edges.enhances().getOrDefault(urn, Set.of());
        boolean enhancerOut = !targets.isEmpty() && targets.stream().noneMatch(inForce::contains);
        boolean bindingOut = bindingOf(edges, urn)
                .filter(mission -> !inForce.contains(mission))
                .isPresent();
        return enhancerOut || bindingOut;
    }

    /**
     * What an overrider in force must hide: its direct targets, and the targets of every unprunable artifact among them
     * (one that enhances nothing and is bound to nothing, so nothing could have withdrawn its overrides).
     */
    private static Set<Urn> hiddenBy(Case generated, Map<Urn, Set<Urn>> direct, Urn overrider) {
        Set<Urn> hidden = new HashSet<>();
        List<Urn> todo = new ArrayList<>(direct.getOrDefault(overrider, Set.of()));
        while (!todo.isEmpty()) {
            Urn next = todo.removeLast();
            boolean unprunable = !generated.edges().enhances().containsKey(next)
                    && bindingOf(generated.edges(), next).isEmpty();
            if (hidden.add(next) && unprunable) {
                todo.addAll(direct.getOrDefault(next, Set.of()));
            }
        }
        return hidden;
    }

    private static boolean satisfied(ArtifactState state, Set<Urn> inForce, Map<Urn, Set<Urn>> reach, Urn urn) {
        return !state.satisfiedBy().isEmpty()
                && state.satisfiedBy().stream()
                        .allMatch(by -> inForce.contains(by)
                                && reach.getOrDefault(by, Set.of()).contains(urn));
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

    /** The open targets each overrider names directly, over every edge of the case. */
    private static Map<Urn, Set<Urn>> directOverrides(Case generated) {
        Map<Urn, Set<Urn>> direct = new HashMap<>();
        generated
                .edges()
                .overrides()
                .forEach((source, targets) -> targets.stream()
                        .filter(target -> overridable(generated.closure(), target))
                        .forEach(target -> direct.computeIfAbsent(source, k -> new HashSet<>())
                                .add(target)));
        return direct;
    }

    /** The chains of overrides over every edge of the case: an upper bound of what any overrider in force hides. */
    private static Map<Urn, Set<Urn>> reachOf(Case generated) {
        Map<Urn, Set<Urn>> direct = directOverrides(generated);
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
        Map<Urn, Set<Urn>> direct = directOverrides(generated);
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
        Map<Urn, Urn> bindings = bindings(random, urns);
        boolean bound = overrides.keySet().stream()
                .anyMatch(source -> source.kind() == NodeKind.ACTION
                        || enhances.containsKey(source)
                        || bindings.containsKey(source));
        Map<Urn, List<String>> required = new LinkedHashMap<>();
        Map<Urn, List<String>> unheld = new LinkedHashMap<>();
        floor(random, urns, required, unheld);
        Map<Urn, Set<String>> languages = new LinkedHashMap<>();
        urns.forEach(urn -> languages(random, urn, languages));
        MergedClosure closure =
                new MergedClosure(winners, List.of(), languages, Map.of(), new RequiredFloor(required, unheld));
        return new Case(closure, new Edges(overrides, enhances, bindings), request(random, urns), cyclic, bound);
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

    /** An explicit binding to a mission type of the closure for some artifacts that are neither a mission type nor a step. */
    private static Map<Urn, Urn> bindings(Random random, List<Urn> urns) {
        List<Urn> missions =
                urns.stream().filter(urn -> urn.kind() == NodeKind.MISSION_TYPE).toList();
        Map<Urn, Urn> bindings = new LinkedHashMap<>();
        if (missions.isEmpty()) {
            return bindings;
        }
        urns.stream()
                .filter(urn -> urn.kind() != NodeKind.MISSION_TYPE && urn.kind() != NodeKind.ACTION)
                .filter(urn -> random.nextInt(6) == 0)
                .forEach(urn -> bindings.put(urn, missions.get(random.nextInt(missions.size()))));
        return bindings;
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
        List<Map.Entry<Urn, Urn>> bindings = new ArrayList<>(edges.bindings().entrySet());
        Collections.shuffle(bindings, random);
        Map<Urn, Urn> boundIn = new LinkedHashMap<>();
        bindings.forEach(binding -> boundIn.put(binding.getKey(), binding.getValue()));
        return new Edges(shuffled(edges.overrides(), random), shuffled(edges.enhances(), random), boundIn);
    }

    /** The same closure with its winners, languages and floor in another order. */
    private static MergedClosure shuffled(MergedClosure closure, long seed) {
        Random random = new Random(seed);
        List<Urn> order = new ArrayList<>(closure.winners().keySet());
        Collections.shuffle(order, random);
        Map<Urn, MergedClosure.Winner> winners = new LinkedHashMap<>();
        order.forEach(urn -> winners.put(urn, closure.winners().get(urn)));
        List<Urn> required = new ArrayList<>(closure.floor().requiredBy().keySet());
        Collections.shuffle(required, random);
        Map<Urn, List<String>> requiredBy = new LinkedHashMap<>();
        required.forEach(urn -> requiredBy.put(urn, closure.floor().requiredBy().get(urn)));
        return new MergedClosure(
                winners,
                closure.layerOverrides(),
                closure.languages(),
                closure.presets(),
                new RequiredFloor(requiredBy, closure.floor().unheld()));
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
