package ai.speckitty.charter.activations.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ActivationId;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.ClosureSnapshots;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.activations.domain.StoredActivation;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.RelationType;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A cascading select over five hundred artifacts through {@link ActivationSelectionService} and the in-memory register
 * (NFR-006), report-only: it prints the p95 of a select with {@code ["all"]} and {@code includeDeactivated}, in thread
 * CPU time after a warm-up, and gates nothing (ADR-0057). The packs are a fake whose walk answers from a fixed graph,
 * so the time is the write's own: the plan, the cascade rules, the rules check and the compare-and-set. Tagged
 * {@code timing}: it runs under {@code :activations:timingBudgets}, alone, and never in {@code test}.
 */
@Tag("timing")
class ActivationSelectionBudgetTest {

    private static final int ARTIFACTS = 500;
    private static final int WARM_UP = 10;
    private static final int MEASURED = 50;
    private static final Optional<List<String>> ALL = Optional.of(List.of("all"));

    @Test
    void aCascadingSelectOverFiveHundredArtifactsReportsItsP95() {
        InMemoryActivationRepository repository = new InMemoryActivationRepository();
        FakeAvailablePacks packs = new FakeAvailablePacks(closure());
        Map<ArtifactRef, List<ArtifactRef>> graph = graph();
        packs.walks(plan -> walk(plan, graph));
        ActivationSelectionService service = new ActivationSelectionService(new ActivationWrites(repository, packs));
        assertThat(select(service, repository, -1).change().applied())
                .as("the fixture reaches beyond its target")
                .hasSizeGreaterThan(ARTIFACTS / 2);

        for (int warm = 0; warm < WARM_UP; warm++) {
            select(service, repository, warm);
        }
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long[] nanos = new long[MEASURED];
        for (int measured = 0; measured < MEASURED; measured++) {
            StoredActivation stored = stored(repository, WARM_UP + measured);
            long begin = threads.getCurrentThreadCpuTime();
            selectOn(service, stored);
            nanos[measured] = threads.getCurrentThreadCpuTime() - begin;
        }
        Arrays.sort(nanos);
        double p95Millis = nanos[(int) Math.ceil(0.95 * MEASURED) - 1] / 1_000_000.0;
        System.out.printf(
                "ActivationSelectionBudgetTest: p95 %.3f ms CPU over %d cascading selects of %d artifacts"
                        + " (NFR-006 budget 200 ms, report-only)%n",
                p95Millis, MEASURED, ARTIFACTS);
    }

    private static ActivationWrites.Outcome select(
            ActivationSelectionService service, InMemoryActivationRepository repository, int run) {
        return selectOn(service, stored(repository, run));
    }

    private static ActivationWrites.Outcome selectOn(ActivationSelectionService service, StoredActivation stored) {
        return service.select(
                stored.id(),
                "tactic",
                "t0",
                ALL,
                true,
                List.of(),
                "\"" + stored.revision().token() + "\"");
    }

    /** A fresh activation per run, with every seventh tactic denied, so the select clears as well as selects. */
    private static StoredActivation stored(InMemoryActivationRepository repository, int run) {
        List<String> denied = new ArrayList<>();
        for (int i = 7; i < ARTIFACTS; i += 7) {
            denied.add("t" + i);
        }
        ActivationDocument document = ActivationDocument.builder()
                .activatedKinds(List.of(ArtifactKind.TACTIC))
                .deactivated(ArtifactKind.TACTIC, denied)
                .build()
                .document();
        return repository
                .create(new ActivationId("budget-" + (run + 1)), document)
                .orElseThrow();
    }

    private static ClosureSnapshot closure() {
        String[] ids = new String[ARTIFACTS];
        for (int i = 0; i < ARTIFACTS; i++) {
            ids[i] = "t" + i;
        }
        return ClosureSnapshots.snapshot().held(ArtifactKind.TACTIC, ids).build();
    }

    /** Each tactic {@code i} requires {@code (i * k + 1) mod 500} for k from 2 to 6: cycles and shared targets. */
    private static Map<ArtifactRef, List<ArtifactRef>> graph() {
        Map<ArtifactRef, List<ArtifactRef>> out = new LinkedHashMap<>();
        for (int i = 0; i < ARTIFACTS; i++) {
            List<ArtifactRef> targets = new ArrayList<>();
            for (int k = 2; k <= 6; k++) {
                targets.add(tactic((i * k + 1) % ARTIFACTS));
            }
            out.put(tactic(i), targets);
        }
        return out;
    }

    /** A forward walk over the graph from the plan's starts, stopping at its stops, as the packs slice answers. */
    private static Reach walk(ReachPlan plan, Map<ArtifactRef, List<ArtifactRef>> graph) {
        Map<ArtifactRef, List<Reach.Via>> reached = new LinkedHashMap<>();
        Map<ArtifactRef, List<Reach.Via>> stopped = new LinkedHashMap<>();
        Deque<ArtifactRef> frontier = new ArrayDeque<>(plan.starts());
        plan.starts().forEach(start -> reached.put(start, new ArrayList<>()));
        while (!frontier.isEmpty()) {
            ArtifactRef from = frontier.pop();
            for (ArtifactRef to : graph.getOrDefault(from, List.of())) {
                Reach.Via via = new Reach.Via(from, RelationType.REQUIRES);
                boolean stop = plan.stopAt().contains(to);
                if (!stop && !reached.containsKey(to)) {
                    frontier.push(to);
                }
                (stop ? stopped : reached)
                        .computeIfAbsent(to, ignored -> new ArrayList<>())
                        .add(via);
            }
        }
        return new Reach(reached, stopped, Set.of(), Set.of(), Map.of());
    }

    private static ArtifactRef tactic(int i) {
        return new ArtifactRef(ArtifactKind.TACTIC, "t" + i);
    }
}
