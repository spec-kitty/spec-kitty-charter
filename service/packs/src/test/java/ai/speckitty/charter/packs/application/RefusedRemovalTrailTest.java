package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.preconditionOf;
import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.packs.infra.storage.StagedPackViewFactory;
import ai.speckitty.charter.packs.infra.storage.YamlSerializationStrategy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A {@code DELETE} that is refused after the service has read the stored revision (issue 418: 412 at the {@code If-Match},
 * 409 and 422 after it) tells the trail that revision, so the audit entry of the refusal carries the revision before
 * instead of an empty one. A refusal before the revision is read (428, 404) tells it nothing, and a refusal never says
 * the pack moved.
 */
class RefusedRemovalTrailTest {

    private static final PackIdentifier QA = new PackIdentifier("qa");
    private static final String QA_ID = PackRegistrationServiceTest.ulid('V');
    private static final String SIBLING_A = PackRegistrationServiceTest.ulid('T');
    private static final String SIBLING_B = PackRegistrationServiceTest.ulid('K');
    private static final String STALE = "\"sha256:" + "0".repeat(64) + "\"";

    /** What a removal told its trail. */
    private static final class Recording implements RevisionTrail {
        final List<PackRevision> found = new ArrayList<>();
        final List<PackRevision> moved = new ArrayList<>();

        @Override
        public void found(PackRevision before) {
            found.add(before);
        }

        @Override
        public void moved(PackRevision before, PackRevision after) {
            moved.add(after);
        }
    }

    private static Packs qa() {
        return Packs.of(QA_ID, "qa", "1.0.0", "org")
                .directive("D1", "D1")
                .directive("D2", "D2")
                .directive("D3", "D3");
    }

    private static WritePolicyGate gateOf(Rig rig) {
        ConsistentRead read = new ConsistentRead(new FileSystemPackSources(rig.locations), rig.generation);
        return new PackMutabilityPolicy(
                new OfferingReadService(rig.repository, rig.repository, rig.repository, read),
                rig.repository,
                rig.repository,
                read);
    }

    private static ArtifactRemovalService artifacts(Rig rig) {
        return new ArtifactRemovalService(
                rig.repository,
                rig.writeStore,
                gateOf(rig),
                new InboundReferences(rig.repository, rig.repository, rig.repository),
                new StagedPackViewFactory(rig.locations, new YamlSerializationStrategy()),
                rig.impact);
    }

    private static RelationshipRemovalService relationships(Rig rig) {
        return new RelationshipRemovalService(rig.repository, rig.writeStore, gateOf(rig), rig.impact);
    }

    private static PackQualifiedReference reference(String text) {
        return PackQualifiedReference.parse(text);
    }

    private static void assertFoundOnly(Recording trail, PackRevision stored) {
        assertThat(trail.found).as("the revision the refused delete found").containsExactly(stored);
        assertThat(trail.moved).as("a refusal moved nothing").isEmpty();
    }

    @Test
    void anArtifactDeleteRefusedAsReferencedStaleOrBreakingAClosureTellsTheTrailTheRevisionItFound(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa().registeredEdge("D1", "D3")), Preconditions.none());
        ArtifactRemovalService service = artifacts(rig);
        PackRevision stored = rig.writeStore.currentRevision(QA);

        Recording referenced = new Recording();
        assertThat(refusal(() -> service.remove(QA, "directive", "D3", "*", referenced))
                        .code())
                .isEqualTo(RefusalCode.ARTIFACT_REFERENCED);
        assertFoundOnly(referenced, stored);

        Recording stale = new Recording();
        assertThat(preconditionOf(() -> service.remove(QA, "directive", "D2", STALE, stale)))
                .isEqualTo(PreconditionException.Kind.STALE);
        assertFoundOnly(stale, stored);

        Recording missing = new Recording();
        assertThat(preconditionOf(() -> service.remove(QA, "directive", "D2", null, missing)))
                .isEqualTo(PreconditionException.Kind.MISSING);
        Recording absent = new Recording();
        assertThat(refusal(() -> service.remove(QA, "directive", "GHOST", "*", absent))
                        .code())
                .isEqualTo(RefusalCode.DIRECTIVE_NOT_FOUND);
        assertThat(missing.found).as("no revision was read before a 428").isEmpty();
        assertThat(absent.found).as("none before a 404").isEmpty();
    }

    @Test
    void anArtifactDeleteRefusedWith422TellsTheTrailTheRevisionItFound(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install(
                "sib-a",
                Packs.of(SIBLING_A, "sib-a", "1.0.0", "org")
                        .directive("SHARED_ONE", "Mine")
                        .directive("EXTRA", "x"));
        rig.install("sib-b", Packs.of(SIBLING_B, "sib-b", "1.0.0", "org").directive("SHARED_ONE", "Yours"));
        rig.activations.add(Optional.of(List.of("sib-a", "sib-b")));
        PackIdentifier pack = new PackIdentifier("sib-a");
        PackRevision stored = rig.writeStore.currentRevision(pack);
        Recording trail = new Recording();

        assertThat(refusal(() -> artifacts(rig).remove(pack, "directive", "EXTRA", "*", trail))
                        .code())
                .isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT);

        assertFoundOnly(trail, stored);
    }

    @Test
    void aRelationshipDeleteRefusedStaleTellsTheTrailTheRevisionItFoundAndOneRefusedMissingTellsNothing(
            @TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa().registeredEdge("D1", "D2")), Preconditions.none());
        RelationshipRemovalService service = relationships(rig);
        PackRevision stored = rig.writeStore.currentRevision(QA);

        Recording stale = new Recording();
        assertThat(preconditionOf(
                        () -> service.remove(QA, reference("directive:D1"), reference("directive:D2"), STALE, stale)))
                .isEqualTo(PreconditionException.Kind.STALE);
        assertFoundOnly(stale, stored);

        Recording missing = new Recording();
        assertThat(preconditionOf(
                        () -> service.remove(QA, reference("directive:D1"), reference("directive:D2"), null, missing)))
                .isEqualTo(PreconditionException.Kind.MISSING);
        assertThat(missing.found).as("no revision was read before a 428").isEmpty();
    }

    @Test
    void aRelationshipDeleteRefusedWith422TellsTheTrailTheRevisionItFound(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install(
                "sib-a",
                Packs.of(SIBLING_A, "sib-a", "1.0.0", "org")
                        .directive("SHARED_ONE", "Mine")
                        .directive("OTHER", "x")
                        .registeredEdge("SHARED_ONE", "OTHER"));
        rig.install("sib-b", Packs.of(SIBLING_B, "sib-b", "1.0.0", "org").directive("SHARED_ONE", "Yours"));
        rig.activations.add(Optional.of(List.of("sib-a", "sib-b")));
        PackIdentifier pack = new PackIdentifier("sib-a");
        PackRevision stored = rig.writeStore.currentRevision(pack);
        Recording trail = new Recording();

        assertThat(refusal(() -> relationships(rig)
                                .remove(
                                        pack,
                                        reference("directive:SHARED_ONE"),
                                        reference("directive:OTHER"),
                                        "*",
                                        trail))
                        .code())
                .isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT);

        assertFoundOnly(trail, stored);
    }
}
