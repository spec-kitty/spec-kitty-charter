package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.packs.application.PackCompositionResolver.Failure;
import ai.speckitty.charter.packs.application.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The resolver orders one closure and refuses a broken graph where it breaks. */
class PackCompositionResolverTest {

    private static PackUnderResolution pack(String id, String name, String version, PackLevel level) {
        return pack(id, name, version, level, List.of());
    }

    private static PackUnderResolution pack(
            String id, String name, String version, PackLevel level, List<PackDependency> dependencies) {
        return new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier(id), level),
                new PackDescriptor(
                        id,
                        version,
                        Optional.empty(),
                        name,
                        Optional.of(level),
                        Optional.empty(),
                        PackPaths.none(),
                        dependencies));
    }

    private static List<String> ids(List<PackUnderResolution> resolved) {
        return resolved.stream().map(entry -> entry.descriptor().packId()).toList();
    }

    @Test
    void aDependencyComesBeforeThePackThatDeclaresIt() {
        PackUnderResolution base = pack("01BASE", "base", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution dependent =
                pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BASE", ">=1.0")));

        assertThat(ids(PackCompositionResolver.resolve(List.of(dependent, base))))
                .containsExactly("01BASE", "01DEP");
    }

    @Test
    void aSharedDependencyAppearsOnce() {
        PackUnderResolution base = pack("01BASE", "base", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution left =
                pack("01LEFT", "left", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BASE", ">=1.0")));
        PackUnderResolution right =
                pack("01RIGHT", "right", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BASE", "<2.0")));

        assertThat(ids(PackCompositionResolver.resolve(List.of(left, base, right))))
                .containsExactly("01BASE", "01LEFT", "01RIGHT");
    }

    @Test
    void aCycleIsRefusedAndNamesAPackInIt() {
        PackUnderResolution a =
                pack("01AAA", "a", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BBB", ">=1.0")));
        PackUnderResolution b =
                pack("01BBB", "b", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01AAA", ">=1.0")));

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(a, b)))
                .isInstanceOf(PackCompositionResolver.Refusal.class)
                .satisfies(refusal -> assertThat(((PackCompositionResolver.Refusal) refusal).failure())
                        .isEqualTo(Failure.CYCLE));
    }

    @Test
    void aPackThatDependsOnItselfIsACycle() {
        PackUnderResolution self =
                pack("01SELF", "self", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01SELF", ">=1.0")));

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(self)))
                .isInstanceOf(PackCompositionResolver.Refusal.class)
                .hasMessageContaining("depends on itself");
    }

    @Test
    void anUnknownDependencyIsRefusedAndNamesTheMissingId() {
        PackUnderResolution dependent =
                pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01GONE", ">=1.0")));

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(dependent)))
                .isInstanceOf(PackCompositionResolver.Refusal.class)
                .satisfies(refusal -> assertThat(((PackCompositionResolver.Refusal) refusal).failure())
                        .isEqualTo(Failure.UNKNOWN_DEPENDENCY))
                .hasMessageContaining("01GONE");
    }

    @Test
    void aVersionOutsideEveryRangeIsRefusedWithBothVersions() {
        PackUnderResolution base = pack("01BASE", "base", "1.0.0", PackLevel.BUILT_IN);
        PackUnderResolution dependent =
                pack("01DEP", "dependent", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01BASE", ">=2.0")));

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(dependent, base)))
                .isInstanceOf(PackCompositionResolver.Refusal.class)
                .satisfies(refusal -> assertThat(((PackCompositionResolver.Refusal) refusal).failure())
                        .isEqualTo(Failure.OUT_OF_RANGE))
                .hasMessageContaining("1.0.0");
    }

    @Test
    void twoPacksWithOneNameAreRefused() {
        PackUnderResolution first = pack("01AAA", "shared-name", "1.0.0", PackLevel.ORG);
        PackUnderResolution second = pack("01BBB", "shared-name", "1.0.0", PackLevel.ORG);

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(first, second)))
                .isInstanceOf(PackCompositionResolver.Refusal.class)
                .satisfies(refusal -> assertThat(((PackCompositionResolver.Refusal) refusal).failure())
                        .isEqualTo(Failure.NAME_CONFLICT));
    }

    @Test
    void aPackMayNotDependOnAPackAtAHigherLevel() {
        PackUnderResolution project = pack("01PROJ", "project-pack", "1.0.0", PackLevel.PROJECT);
        PackUnderResolution org =
                pack("01ORG", "org-pack", "1.0.0", PackLevel.ORG, List.of(new PackDependency("01PROJ", ">=1.0")));

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(org, project)))
                .isInstanceOf(PackCompositionResolver.Refusal.class)
                .satisfies(refusal -> assertThat(((PackCompositionResolver.Refusal) refusal).failure())
                        .isEqualTo(Failure.LEVEL_VIOLATION));
    }

    @Test
    void anUnresolvableParentIsRefusedNotDegraded() {
        PackUnderResolution orphan = new PackUnderResolution(
                new ConfiguredPack(new PackIdentifier("01ORPH"), PackLevel.ORG),
                new PackDescriptor(
                        "01ORPH",
                        "1.0.0",
                        Optional.of("01MISSING"),
                        "orphan",
                        Optional.of(PackLevel.ORG),
                        Optional.empty()));

        assertThatThrownBy(() -> PackCompositionResolver.resolve(List.of(orphan)))
                .isInstanceOf(PackCompositionResolver.Refusal.class)
                .satisfies(refusal -> assertThat(((PackCompositionResolver.Refusal) refusal).failure())
                        .isEqualTo(Failure.PARENT_UNRESOLVED));
    }
}
