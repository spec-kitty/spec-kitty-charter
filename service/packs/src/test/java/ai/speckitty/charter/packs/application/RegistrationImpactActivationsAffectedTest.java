package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.packs.application.ActivationImpact.ClosureChange;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationService.RegistrationOutcome;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The activations a registration affects (IC-15, FR-027, B4): the impact check of a whole-pack registration gives the
 * activations slice the closures it composed anyway, before and after the write, for the root set of each stored
 * activation, and serves what the slice names as {@code notices.activationsAffected}. A root set whose closure does not
 * hold the pack is none of the registration's concern, the closure of a pack that did not exist has no before, and the
 * per-artifact writes, whose answers declare no notices, do not ask.
 */
class RegistrationImpactActivationsAffectedTest {

    private static final String BASE_ID = PackRegistrationServiceTest.ulid('E');
    private static final String LONE_ID = PackRegistrationServiceTest.ulid('K');

    private static Packs base(String version) {
        return Packs.of(BASE_ID, "base", version, "org", "").directive("A", "Version " + version);
    }

    private static Packs lone() {
        return Packs.of(LONE_ID, "lone", "1.0.0", "org", "").directive("O", "Other");
    }

    @Test
    void theActivationsSliceIsAskedWithTheClosuresOfTheRegistrationAndWhatItNamesIsTheNotice(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        rig.register("lone", rig.stage(lone()), Preconditions.none());
        rig.activations.add(Optional.of(List.of("base")));
        rig.activations.add(Optional.of(List.of("lone")));
        rig.asked.clear();
        rig.affected.addAll(List.of("a-1"));

        RegistrationOutcome outcome = rig.register("base", rig.stage(base("1.1.0")), Preconditions.none());

        assertThat(outcome.notices().activationsAffected()).containsExactly("a-1");
        assertThat(rig.asked)
                .as("asked once, by the impact check, before the commit")
                .hasSize(1);
        ActivationImpact.ClosureChanges closures = rig.asked.getFirst();
        Optional<ClosureChange> holding = closures.of(Optional.of(List.of("base")));
        assertThat(holding).isPresent();
        assertThat(holding.orElseThrow().before())
                .as("the closure existed before the write")
                .isPresent();
        assertThat(holding.orElseThrow().after().holds(ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE, "A"))
                .isTrue();
        assertThat(closures.of(Optional.of(List.of("lone"))))
                .as("the unrelated pack's closure does not hold base")
                .isEmpty();
        assertThat(closures.of(Optional.empty()))
                .as("an activation with no packs roots at every configured pack, which holds base")
                .isPresent();
    }

    @Test
    void aBrandNewPackHasNoClosureBeforeAndAnActivationOfEveryPackHoldsItAfter(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("lone", rig.stage(lone()), Preconditions.none());
        rig.activations.add(Optional.empty());
        rig.asked.clear();

        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());

        assertThat(rig.asked).hasSize(1);
        Optional<ClosureChange> everything = rig.asked.getFirst().of(Optional.empty());
        assertThat(everything).isPresent();
        assertThat(everything
                        .orElseThrow()
                        .after()
                        .holds(ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE, "A"))
                .isTrue();
        assertThat(everything
                        .orElseThrow()
                        .before()
                        .orElseThrow()
                        .holds(ai.speckitty.charter.kernel.domain.ArtifactKind.DIRECTIVE, "A"))
                .as("the closure before the write is every pack that was configured, and base was not")
                .isFalse();
    }

    @Test
    void noStoredActivationStillGivesTheNoticeAnEmptyList(@TempDir Path host) {
        Rig rig = new Rig(host);

        RegistrationOutcome outcome = rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());

        assertThat(outcome.notices().activationsAffected()).isEmpty();
        assertThat(rig.asked).hasSize(1);
    }

    @Test
    void aPerArtifactWriteDoesNotAskTheActivationsSliceBecauseItsAnswerDeclaresNoNotices(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(base("1.0.0")), Preconditions.none());
        rig.activations.add(Optional.of(List.of("base")));
        rig.asked.clear();

        rig.impact.check(new ai.speckitty.charter.kernel.domain.PackIdentifier("base"), facts -> facts);

        assertThat(rig.asked).isEmpty();
    }
}
