package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * One compose is one closure rooted at one level (FR-011), with dependencies before dependents (FR-004) and a shared
 * dependency loaded once (FR-005) — SC-001's five-pack diamond. Composition stays separate from the layer merge
 * across levels: a pack may depend only on its own level or a lower one, an upward edge is refused with
 * {@code pack_level_violation}, and packs at different levels changing one artifact compose as a layer merge, not a
 * sibling contest.
 */
class ComposeLevelSeparationTest {

    private static final String DIGEST = "sha256:" + "6".repeat(64);

    @Test
    void theFivePackDiamondResolvesWithEveryDependencyBeforeItsDependentsAndTheSharedPackOnce() {
        PackUnderResolution alpha = pack("01AAA", "alpha", PackLevel.ORG);
        PackUnderResolution bravo = pack("01BBB", "bravo", PackLevel.ORG, new PackDependency("01AAA", ">=1.0"));
        PackUnderResolution charlie = pack("01CCC", "charlie", PackLevel.ORG, new PackDependency("01AAA", "<2.0"));
        PackUnderResolution delta = pack(
                "01DDD",
                "delta",
                PackLevel.ORG,
                new PackDependency("01BBB", ">=1.0"),
                new PackDependency("01CCC", ">=1.0"));
        PackUnderResolution echo = pack("01EEE", "echo", PackLevel.ORG, new PackDependency("01AAA", ">=1.0"));

        List<PackUnderResolution> resolved =
                PackCompositionResolver.resolve(List.of(echo, delta, charlie, bravo, alpha));
        List<String> ids = idsOf(resolved);

        assertThat(ids)
                .as("SC-001: alpha before bravo and charlie, both before delta, alpha once; echo joins the queue"
                        + " the moment alpha frees it, ahead of delta, which waits on both siblings")
                .containsExactly("01AAA", "01BBB", "01CCC", "01EEE", "01DDD");
        assertThat(ids).containsOnlyOnce("01AAA");
        int alphaAt = ids.indexOf("01AAA");
        assertThat(ids.indexOf("01BBB")).as("bravo after alpha").isGreaterThan(alphaAt);
        assertThat(ids.indexOf("01CCC")).as("charlie after alpha").isGreaterThan(alphaAt);
        assertThat(ids.indexOf("01DDD"))
                .as("delta after bravo and charlie")
                .isGreaterThan(ids.indexOf("01BBB"))
                .isGreaterThan(ids.indexOf("01CCC"));
        assertEveryDependencyPrecedesItsDependent(resolved);
    }

    @Test
    void threeLevelsResolveAsOneClosureWithTheMergeOrderAcrossLevelsPreserved() {
        PackUnderResolution base = pack("01BASE", "base", PackLevel.BUILT_IN);
        PackUnderResolution middle = pack("01MID0", "middle", PackLevel.ORG, new PackDependency("01BASE", ">=1.0"));
        PackUnderResolution top = pack("01TOP0", "top", PackLevel.PROJECT, new PackDependency("01MID0", ">=1.0"));

        List<PackUnderResolution> resolved = PackCompositionResolver.resolve(List.of(top, middle, base));

        assertThat(idsOf(resolved))
                .as("one closure: the built-in base, the org pack that depends on it, the project pack last")
                .containsExactly("01BASE", "01MID0", "01TOP0");
        assertThat(resolved)
                .extracting(entry -> entry.configured().level().token())
                .as("the merge order across levels is preserved: project over org over built-in")
                .containsExactly("built-in", "org", "project");
        assertEveryDependencyPrecedesItsDependent(resolved);
    }

    @Test
    void aSameLevelDependencyIsAllowedAndResolvesInDependencyOrder() {
        PackUnderResolution left = pack("01LEFT", "left", PackLevel.ORG);
        PackUnderResolution right = pack("01RGHT", "right", PackLevel.ORG, new PackDependency("01LEFT", ">=1.0"));

        assertThat(idsOf(PackCompositionResolver.resolve(List.of(right, left))))
                .as("two org packs may depend on each other's artifacts; only an upward edge is a level violation")
                .containsExactly("01LEFT", "01RGHT");
    }

    @Test
    void packsAtDifferentLevelsChangingOneArtifactComposeAsALayerMergeNotAContest() {
        PackUnderResolution org = refining("01ORGP", "org-pack", PackLevel.ORG, "directive:DIRECTIVE_009");
        PackUnderResolution project = refining("01PROJ", "project-pack", PackLevel.PROJECT, "directive:DIRECTIVE_009");

        assertThat(idsOf(PackCompositionResolver.resolve(List.of(project, org))))
                .as("the compose does not mix the levels into one closure: only same-level packs are siblings")
                .containsExactly("01ORGP", "01PROJ");
    }

    @Test
    void anEdgeThatWouldCrossALevelBoundaryUpwardIsRefusedWithTheLevelViolationCode() {
        ConfiguredPack org = new ConfiguredPack(new PackIdentifier("01ORGP"), PackLevel.ORG);
        ConfiguredPack project = new ConfiguredPack(new PackIdentifier("01PROJ"), PackLevel.PROJECT);
        PackDescriptor orgDescriptor = new PackDescriptor(
                "01ORGP",
                "1.0.0",
                Optional.empty(),
                "org-pack",
                Optional.of(PackLevel.ORG),
                Optional.empty(),
                PackPaths.none(),
                List.of(new PackDependency("01PROJ", ">=1.0")));
        PackDescriptor projectDescriptor = descriptor("01PROJ", "project-pack");
        OfferingReadService service = offering(List.of(project, org), orgDescriptor, projectDescriptor);

        assertThatThrownBy(service::offering).isInstanceOfSatisfying(CharterRefusal.class, refused -> {
            assertThat(refused.code()).isEqualTo(RefusalCode.PACK_LEVEL_VIOLATION);
            assertThat(refused.code().token()).isEqualTo("pack_level_violation");
            assertThat(refused.getMessage()).contains("01ORGP").contains("01PROJ");
        });
    }

    private static void assertEveryDependencyPrecedesItsDependent(List<PackUnderResolution> resolved) {
        Map<String, Integer> position = new HashMap<>();
        for (int index = 0; index < resolved.size(); index++) {
            position.put(resolved.get(index).descriptor().packId(), index);
        }
        for (PackUnderResolution entry : resolved) {
            for (PackDependency dependency : entry.descriptor().dependencies()) {
                assertThat(position.get(dependency.packId()))
                        .as(
                                "%s precedes %s",
                                dependency.packId(), entry.descriptor().packId())
                        .isLessThan(position.get(entry.descriptor().packId()));
            }
        }
    }

    private static List<String> idsOf(List<PackUnderResolution> resolved) {
        return resolved.stream().map(entry -> entry.descriptor().packId()).toList();
    }

    private static PackUnderResolution pack(String id, String name, PackLevel level, PackDependency... dependencies) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(id), level),
                new PackDescriptor(
                        id,
                        "1.0.0",
                        Optional.empty(),
                        name,
                        Optional.of(level),
                        Optional.empty(),
                        PackPaths.none(),
                        List.of(dependencies)));
    }

    /** A pack whose graphs say it changes one artifact (an {@code enhances} or {@code overrides} relation on it). */
    private static PackUnderResolution refining(String id, String name, PackLevel level, String artifact) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(id), level),
                new PackDescriptor(id, "1.0.0", Optional.empty(), name, Optional.of(level), Optional.empty()),
                List.of(artifact));
    }

    private static PackDescriptor descriptor(String id, String name) {
        return new PackDescriptor(id, "1.0.0", Optional.empty(), name, Optional.empty(), Optional.empty());
    }

    /** The offering read over in-memory ports, exactly as the existing offering fixtures build it. */
    private static OfferingReadService offering(List<ConfiguredPack> configured, PackDescriptor... descriptors) {
        Map<String, PackDescriptor> byId = new HashMap<>();
        for (PackDescriptor descriptor : descriptors) {
            byId.put(descriptor.packId(), descriptor);
        }
        PackDescriptorRepository repository = new PackDescriptorRepository() {
            @Override
            public PackDescriptor get(PackIdentifier pack) {
                return find(pack).orElseThrow();
            }

            @Override
            public Optional<PackDescriptor> find(PackIdentifier pack) {
                return Optional.ofNullable(byId.get(pack.value()));
            }

            @Override
            public void save(PackIdentifier pack, PackDescriptor descriptor) {
                throw new UnsupportedOperationException("this test never writes");
            }
        };
        return new OfferingReadService(
                () -> configured, repository, pack -> List.of(), new ConsistentRead(() -> DIGEST));
    }
}
