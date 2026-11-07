package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.preconditionOf;
import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The {@code DELETE} of one relationship (IC-06, FR-006; decisions DM-01M6TQ62HXYF362GAFS2BZ3PGF and
 * DM-01M6TR1KDAJ64GMJD6AYNZKT59): only an edge of {@code registered.graph.yaml} is removed, and a file the author wrote
 * is never rewritten. One test per outcome, each with a control on the same fixture.
 */
class RelationshipRemovalServiceTest {

    private static final String QA_ID = PackRegistrationServiceTest.ulid('V');
    private static final PackIdentifier QA = new PackIdentifier("qa");
    private static final String AUTHORED_EDGE = """
            schema_version: '1.0'
            generated_at: STATIC
            nodes: []
            edges:
            - {source: 'directive:D1', target: 'directive:D2', relation: suggests}
            """;

    private static Packs qa() {
        return Packs.of(QA_ID, "qa", "1.0.0", "org")
                .directive("D1", "D1")
                .directive("D2", "D2")
                .directive("D3", "D3");
    }

    private static PackQualifiedReference reference(String text) {
        return PackQualifiedReference.parse(text);
    }

    private static final class Removals {
        final Rig rig;
        final RelationshipRemovalService service;

        Removals(Rig rig) {
            this.rig = rig;
            this.service = new RelationshipRemovalService(rig.repository, rig.writeStore, rig.impact);
        }

        PackRevision remove(String from, String to) {
            return service.remove(QA, reference(from), reference(to), "*");
        }

        Map<String, String> files() {
            return rig.filesOf(rig.packs.resolve("qa"));
        }
    }

    @ParameterizedTest
    @CsvSource({"directive:D1, directive:D2", "directive:D2, directive:D1"})
    void aRegisteredEdgeIsRemovedWhicheverEndTheRequestNamesFirstAndTheArtifactsStay(
            String from, String to, @TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa().registeredEdge("D1", "D2")), Preconditions.none());
        Removals removals = new Removals(rig);
        PackRevision before = rig.writeStore.currentRevision(QA);

        PackRevision after = removals.remove(from, to);

        assertThat(after).isNotEqualTo(before).isEqualTo(rig.writeStore.currentRevision(QA));
        assertThat(rig.writeStore.registeredRelationships(QA))
                .as("the edge is gone")
                .isEmpty();
        assertThat(removals.files())
                .as("the artifacts stay")
                .containsKeys("directives/D1.directive.yaml", "directives/D2.directive.yaml");
        assertThat(refusal(() -> removals.remove(from, to)).code())
                .as("the pair is no longer held anywhere")
                .isEqualTo(RefusalCode.RELATIONSHIP_NOT_FOUND);
    }

    @Test
    void aPairNothingHoldsIsA404AndARegisteredPairOnTheSameFixtureIsRemoved(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa().registeredEdge("D1", "D2")), Preconditions.none());
        Removals removals = new Removals(rig);
        Map<String, String> before = removals.files();

        assertThat(refusal(() -> removals.remove("directive:D1", "directive:D3"))
                        .code())
                .isEqualTo(RefusalCode.RELATIONSHIP_NOT_FOUND);
        assertThat(refusal(() -> removals.remove("directive:D1", "directive:D1"))
                        .code())
                .as("a pair that joins an artifact to itself is none either")
                .isEqualTo(RefusalCode.RELATIONSHIP_NOT_FOUND);
        assertThat(refusal(() -> removals.service.remove(
                                new PackIdentifier("ghost"), reference("directive:D1"), reference("directive:D2"), "*"))
                        .code())
                .isEqualTo(RefusalCode.PACK_NOT_FOUND);
        assertThat(removals.files()).as("nothing changed").isEqualTo(before);

        removals.remove("directive:D1", "directive:D2");
        assertThat(rig.writeStore.registeredRelationships(QA)).as("the control").isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"lineage.graph.yaml", "drg/fragment.yaml"})
    void anEdgeTheAuthorWroteIsRefusedNothingIsRewrittenAndARegisteredEdgeStillGoes(String file, @TempDir Path host) {
        Rig rig = new Rig(host);
        Packs pack = qa().registeredEdge("D1", "D3");
        String authored = AUTHORED_EDGE;
        if (file.startsWith("drg/")) {
            // A fragment has no header: its schema holds nodes and edges only.
            authored = AUTHORED_EDGE.substring(AUTHORED_EDGE.indexOf("nodes:"));
            pack = Packs.of(QA_ID, "qa", "1.0.0", "org", "pack_drg: drg/fragment.yaml\n")
                    .directive("D1", "D1")
                    .directive("D2", "D2")
                    .directive("D3", "D3")
                    .registeredEdge("D1", "D3");
        }
        rig.register("qa", rig.stage(pack.file(file, authored)), Preconditions.none());
        Removals removals = new Removals(rig);
        Map<String, String> before = removals.files();

        CharterRefusal refused = refusal(() -> removals.remove("directive:D1", "directive:D2"));

        assertThat(refused.code()).isEqualTo(RefusalCode.RELATIONSHIP_NOT_REGISTERED);
        assertThat(refused.getMessage()).contains("register the whole pack");
        assertThat(removals.files())
                .as("byte-identical, the author's file included")
                .isEqualTo(before);

        removals.remove("directive:D1", "directive:D3");
        assertThat(rig.writeStore.registeredRelationships(QA))
                .as("the control: the pair that is registered only")
                .isEmpty();
        assertThat(removals.files()).containsEntry(file, before.get(file));
    }

    @Test
    void aPairHeldBothAsARegisteredEdgeAndInAnAuthoredShardIsRefusedAndTheEdgeStaysInForce(@TempDir Path host) {
        Rig rig = new Rig(host);
        String authored = AUTHORED_EDGE.replace(
                "{source: 'directive:D1', target: 'directive:D2', relation: suggests}",
                "{source: 'directive:D2', target: 'directive:D1', relation: suggests}");
        rig.register(
                "qa",
                rig.stage(qa().registeredEdge("D1", "D2").file("lineage.graph.yaml", authored)),
                Preconditions.none());
        Removals removals = new Removals(rig);
        Map<String, String> before = removals.files();

        assertThat(refusal(() -> removals.remove("directive:D1", "directive:D2"))
                        .code())
                .as("the author wrote it, and a 204 that left the edge in force would mislead")
                .isEqualTo(RefusalCode.RELATIONSHIP_NOT_REGISTERED);

        assertThat(removals.files()).isEqualTo(before);
        assertThat(rig.writeStore.registeredRelationships(QA)).hasSize(1);
    }

    @Test
    void theRevisionIsRequiredAndAStaleOneIsRefused(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa().registeredEdge("D1", "D2")), Preconditions.none());
        Removals removals = new Removals(rig);
        Map<String, String> before = removals.files();
        String stale = "\"sha256:" + "0".repeat(64) + "\"";

        assertThat(preconditionOf(
                        () -> removals.service.remove(QA, reference("directive:D1"), reference("directive:D2"), null)))
                .isEqualTo(PreconditionException.Kind.MISSING);
        assertThat(preconditionOf(
                        () -> removals.service.remove(QA, reference("directive:D1"), reference("directive:D2"), stale)))
                .isEqualTo(PreconditionException.Kind.STALE);
        assertThat(removals.files()).isEqualTo(before);

        removals.service.remove(
                QA,
                reference("directive:D1"),
                reference("directive:D2"),
                rig.writeStore.currentRevision(QA).token());
        assertThat(rig.writeStore.registeredRelationships(QA))
                .as("the control: the current revision")
                .isEmpty();
    }

    private static final String SIBLING_A = PackRegistrationServiceTest.ulid('W');
    private static final String SIBLING_B = PackRegistrationServiceTest.ulid('X');

    @Test
    void aRemovalThatLeavesAClosureBrokenIsRefusedAndTheSameRemovalOtherwiseIsNot(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install(
                "sib-a",
                Packs.of(SIBLING_A, "sib-a", "1.0.0", "org")
                        .directive("SHARED_ONE", "Mine")
                        .directive("OTHER", "x")
                        .registeredEdge("SHARED_ONE", "OTHER"));
        rig.install("sib-b", Packs.of(SIBLING_B, "sib-b", "1.0.0", "org").directive("SHARED_ONE", "Yours"));
        rig.activations.add(Optional.of(List.of("sib-a", "sib-b")));
        RelationshipRemovalService service = new RelationshipRemovalService(rig.repository, rig.writeStore, rig.impact);
        PackIdentifier pack = new PackIdentifier("sib-a");
        Map<String, String> before = rig.filesOf(rig.packs.resolve("sib-a"));

        assertThat(refusal(() -> service.remove(
                                pack, reference("directive:SHARED_ONE"), reference("directive:OTHER"), "*"))
                        .code())
                .as("PR A does not repair a closure that is broken: the activation lists the siblings together")
                .isEqualTo(RefusalCode.PACK_SIBLING_CONFLICT);
        assertThat(rig.filesOf(rig.packs.resolve("sib-a"))).isEqualTo(before);

        rig.activations.clear();
        service.remove(pack, reference("directive:SHARED_ONE"), reference("directive:OTHER"), "*");
        assertThat(rig.writeStore.registeredRelationships(pack))
                .as("the control")
                .isEmpty();
    }
}
