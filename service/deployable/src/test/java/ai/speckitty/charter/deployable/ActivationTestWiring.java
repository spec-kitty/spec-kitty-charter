package ai.speckitty.charter.deployable;

import ai.speckitty.charter.activations.application.AvailablePacks;
import ai.speckitty.charter.activations.application.PacksSession;
import ai.speckitty.charter.activations.domain.ActionContext;
import ai.speckitty.charter.activations.domain.ArtifactRef;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.EffectiveArtifactRead;
import ai.speckitty.charter.activations.domain.EffectivePrompt;
import ai.speckitty.charter.activations.domain.GraphSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceAnswer;
import ai.speckitty.charter.activations.domain.MergedSnapshot.InForceQuery;
import ai.speckitty.charter.activations.domain.Reach;
import ai.speckitty.charter.activations.domain.ReachPlan;
import ai.speckitty.charter.activations.infra.storage.InMemoryActivationRepository;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The activation register over a fixture pack set, for the HTTP tests of the activation set: an in-memory register,
 * and packs that answer one fixed closure, the pack {@code fixture} holding {@link #IDS} of every activatable kind and
 * the mission types {@link #MISSION_TYPES}, whose governing preset requires {@link #REQUIRED} (a directive). The
 * fake counts its reads.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ActivationTestWiring {

    /** The token every request of these tests carries. */
    public static final String TOKEN = "activation-http-token-0123456789-abcdef";

    /** The ids the fixture pack holds of each kind. */
    public static final List<String> IDS = List.of("a-1", "b-2", "c-3", "d-4");

    /** The mission types the fixture pack holds. */
    public static final List<String> MISSION_TYPES = List.of("research", "software-dev");

    /** The directive the fixture pack requires, which no deny list may name. */
    public static final String REQUIRED = "a-1";

    /** For Spring, which instantiates the configuration class. */
    public ActivationTestWiring() {}

    /** The fixture closure. */
    static ClosureSnapshot fixture(String fingerprint) {
        Map<ArtifactKind, Set<String>> held = new EnumMap<>(ArtifactKind.class);
        Arrays.stream(ArtifactKind.values())
                .filter(kind -> kind.activatable() && kind != ArtifactKind.MISSION_TYPE)
                .forEach(kind -> held.put(kind, Set.copyOf(IDS)));
        held.put(ArtifactKind.MISSION_TYPE, Set.copyOf(MISSION_TYPES));
        return new ClosureSnapshot(
                List.of(new ClosureSnapshot.PackEntry(
                        "fixture", "01M4P01D5TWB5FKZGNNQG3943F", "org", "fixture-pack", "1.0.0", List.of())),
                held,
                Map.of("directive:" + REQUIRED, List.of("fixture")),
                Map.of(),
                Map.of(),
                fingerprint);
    }

    /** The packs as the fixture answers them, counting the reads; its fingerprint can be changed by a test. */
    public static final class FixturePacks implements AvailablePacks {

        private final AtomicInteger reads = new AtomicInteger();
        private volatile String fingerprint = "sha256:fixture";

        @Override
        public List<ArtifactKind> listedKinds() {
            return List.of(ArtifactKind.DIRECTIVE);
        }

        @Override
        public PacksSession session(Optional<List<String>> packIds) {
            reads.incrementAndGet();
            ClosureSnapshot snapshot = fixture(fingerprint);
            return new PacksSession() {
                @Override
                public ClosureSnapshot snapshot() {
                    return snapshot;
                }

                @Override
                public Reach reach(ReachPlan plan) {
                    return Reach.empty();
                }

                @Override
                public MergedSnapshot merged() {
                    return MergedSnapshot.empty();
                }

                @Override
                public InForceAnswer inForce(InForceQuery query) {
                    return InForceAnswer.empty();
                }

                @Override
                public GraphSnapshot graph(InForceQuery query) {
                    return GraphSnapshot.empty();
                }

                @Override
                public ActionContext actionContext(String missionType, String action, int depth, InForceQuery query) {
                    return ActionContext.notInForce();
                }

                @Override
                public Optional<EffectiveArtifactRead> effectiveArtifact(
                        ArtifactKind kind, String id, int depth, InForceQuery query) {
                    return Optional.empty();
                }

                @Override
                public boolean isInForce(ArtifactRef artifact, InForceQuery query) {
                    return false;
                }

                @Override
                public EffectivePrompt prompt(String missionType, String action, InForceQuery query) {
                    return EffectivePrompt.NONE;
                }

                @Override
                public void requireAction(String missionType, String action) {
                    // the fixture holds no mission type
                }

                @Override
                public Optional<String> stepPlace(String missionType, String action) {
                    return Optional.empty();
                }
            };
        }

        /**
         * How often the packs were read.
         *
         * @return the reads
         */
        public int reads() {
            return reads.get();
        }

        /**
         * Answers another fingerprint from now on, as if a pack had changed.
         *
         * @param next the fingerprint
         */
        public void fingerprint(String next) {
            fingerprint = next;
        }
    }

    @Bean
    @Primary
    InMemoryActivationRepository fixtureActivationRepository() {
        return new InMemoryActivationRepository();
    }

    @Bean
    @Primary
    FixturePacks fixturePacks() {
        return new FixturePacks();
    }
}
