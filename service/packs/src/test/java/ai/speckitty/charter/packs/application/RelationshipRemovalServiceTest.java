package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.preconditionOf;
import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.transport.PreconditionException;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.domain.WritePolicyGate;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import ai.speckitty.charter.packs.infra.storage.RelationshipShard;
import java.nio.file.Path;
import java.util.ArrayList;
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

    /** The real mutability gate, over the rig's packs. */
    private static WritePolicyGate gateOf(Rig rig) {
        ConsistentRead read = new ConsistentRead(new FileSystemPackSources(rig.locations), rig.generation);
        return new PackMutabilityPolicy(
                new OfferingReadService(rig.repository, rig.repository, rig.repository, read),
                rig.repository,
                rig.repository,
                read);
    }

    private static PackQualifiedReference reference(String text) {
        return PackQualifiedReference.parse(text);
    }

    private static final class Removals {
        final Rig rig;
        final RelationshipRemovalService service;

        Removals(Rig rig) {
            this.rig = rig;
            this.service = new RelationshipRemovalService(rig.repository, rig.writeStore, gateOf(rig), rig.impact);
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
    void theTrailIsToldTheRevisionTheDeleteFoundAndTheOneItLeftAndNothingOnARefusal(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("qa", rig.stage(qa().registeredEdge("D1", "D2")), Preconditions.none());
        Removals removals = new Removals(rig);
        PackRevision stored = rig.writeStore.currentRevision(QA);
        List<PackRevision[]> trail = new ArrayList<>();

        assertThat(refusal(() -> removals.service.remove(
                                QA,
                                reference("directive:D1"),
                                reference("directive:D3"),
                                "*",
                                (b, a) -> trail.add(new PackRevision[] {b, a})))
                        .code())
                .isEqualTo(RefusalCode.RELATIONSHIP_NOT_FOUND);
        assertThat(trail).as("a refused delete tells the trail nothing").isEmpty();

        PackRevision after = removals.service.remove(
                QA,
                reference("directive:D1"),
                reference("directive:D2"),
                "*",
                (b, a) -> trail.add(new PackRevision[] {b, a}));

        assertThat(trail).singleElement().satisfies(moved -> {
            assertThat(moved[0]).as("found").isEqualTo(stored);
            assertThat(moved[1]).as("left").isEqualTo(after).isEqualTo(rig.writeStore.currentRevision(QA));
        });
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
        RelationshipRemovalService service =
                new RelationshipRemovalService(rig.repository, rig.writeStore, gateOf(rig), rig.impact);
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

    private static final String SIBLING_C = PackRegistrationServiceTest.ulid('Q');

    @Test
    void aBrokenClosureTheTargetPackDoesNotJoinLeavesTheRemovalAlone(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.install(
                "target",
                Packs.of(SIBLING_C, "target", "1.0.0", "org")
                        .directive("ONE", "x")
                        .directive("TWO", "x")
                        .registeredEdge("ONE", "TWO"));
        rig.install("sib-a", Packs.of(SIBLING_A, "sib-a", "1.0.0", "org").directive("SHARED_ONE", "Mine"));
        rig.install("sib-b", Packs.of(SIBLING_B, "sib-b", "1.0.0", "org").directive("SHARED_ONE", "Yours"));
        rig.activations.add(Optional.of(List.of("sib-a", "sib-b")));
        RelationshipRemovalService service =
                new RelationshipRemovalService(rig.repository, rig.writeStore, gateOf(rig), rig.impact);
        PackIdentifier target = new PackIdentifier("target");

        service.remove(target, reference("directive:ONE"), reference("directive:TWO"), "*");

        assertThat(rig.writeStore.registeredRelationships(target))
                .as("the activation's closure is broken, but it does not hold the pack the removal is from")
                .isEmpty();
    }

    // ---- the edge gate (WP20 item 2; DM-01M6TR1KDAJ64GMJD6AYNZKT59 item 4)
    // ---------------------------------------------

    private static final String BASE_ID = PackRegistrationServiceTest.ulid('C');
    private static final String DERIVED_ID = PackRegistrationServiceTest.ulid('D');

    private static Packs base() {
        return Packs.of(BASE_ID, "base", "1.0.0", "org", "mutability_policy: policy.yaml\n")
                .file("policy.yaml", "schema_version: 1\nartifacts:\n  directive:LOCKED_ONE: locked\n")
                .directive("LOCKED_ONE", "Locked")
                .directive("OPEN_ONE", "Open");
    }

    private static Packs derivedWithEdgesFrom(String... sources) {
        List<Relationship> edges = new java.util.ArrayList<>();
        for (String source : sources) {
            edges.add(new Relationship(
                    Urn.parse("directive:" + source).orElseThrow(),
                    Optional.of("base"),
                    Urn.parse("directive:DERIVED_ONE").orElseThrow(),
                    Optional.empty(),
                    RelationType.REQUIRES,
                    Optional.empty(),
                    Optional.empty()));
        }
        return Packs.of(
                        DERIVED_ID,
                        "derived",
                        "1.0.0",
                        "project",
                        "depends_on:\n  - pack_id: " + BASE_ID + "\n    range: '>=1.0'\n")
                .directive("DERIVED_ONE", "Own")
                .file("registered.graph.yaml", RelationshipShard.write(edges));
    }

    @Test
    void anEdgeWhoseSourceIsALockedArtifactOfAnAncestorIsNotRemovedAndOneFromAnOpenArtifactIs(@TempDir Path host) {
        Rig rig = new Rig(host);
        rig.register("base", rig.stage(base()), Preconditions.none());
        rig.install("derived", derivedWithEdgesFrom("LOCKED_ONE", "OPEN_ONE"));
        RelationshipRemovalService service =
                new RelationshipRemovalService(rig.repository, rig.writeStore, gateOf(rig), rig.impact);
        PackIdentifier derived = new PackIdentifier("derived");
        Map<String, String> before = rig.filesOf(rig.packs.resolve("derived"));

        assertThat(refusal(() -> service.remove(
                                derived,
                                reference("base::directive:LOCKED_ONE"),
                                reference("directive:DERIVED_ONE"),
                                "*"))
                        .code())
                .as("a whole-pack replace is refused for dropping this edge: so is the DELETE")
                .isEqualTo(RefusalCode.GOVERNANCE_POLICY_VIOLATION);
        assertThat(rig.filesOf(rig.packs.resolve("derived")))
                .as("nothing changed")
                .isEqualTo(before);

        service.remove(derived, reference("base::directive:OPEN_ONE"), reference("directive:DERIVED_ONE"), "*");
        assertThat(rig.writeStore.registeredRelationships(derived))
                .as("the control: the edge from an open artifact goes")
                .extracting(edge -> edge.from().toString())
                .containsExactly("directive:LOCKED_ONE");
    }

    // ---- pack-qualified targets (WP20 item 3) ---------------------------------------------------------------------

    private static Relationship edgeToX(String targetPack) {
        return new Relationship(
                Urn.parse("directive:D1").orElseThrow(),
                Optional.empty(),
                Urn.parse("directive:X").orElseThrow(),
                Optional.ofNullable(targetPack),
                RelationType.REQUIRES,
                Optional.empty(),
                Optional.empty());
    }

    private static final String PACK_ONE = PackRegistrationServiceTest.ulid('Y');
    private static final String PACK_TWO = PackRegistrationServiceTest.ulid('Z');

    /** Two packs that both hold {@code directive:X}, and {@code qa}, which depends on both and has an edge to each. */
    private static Rig rigWithTwoEdgesToTheSameIdInTwoPacks(Path host) {
        Rig rig = new Rig(host);
        rig.install("base", Packs.of(PACK_ONE, "base", "1.0.0", "built-in").directive("X", "Base X"));
        rig.install("other", Packs.of(PACK_TWO, "other", "1.0.0", "org").directive("X", "Other X"));
        rig.install(
                "qa",
                Packs.of(
                                QA_ID,
                                "qa",
                                "1.0.0",
                                "project",
                                "depends_on:\n  - pack_id: " + PACK_ONE + "\n    range: '>=1.0'\n  - pack_id: "
                                        + PACK_TWO + "\n    range: '>=1.0'\n")
                        .directive("D1", "D1")
                        .file(
                                "registered.graph.yaml",
                                RelationshipShard.write(List.of(edgeToX("base"), edgeToX("other")))));
        return rig;
    }

    private static List<String> targetsOf(Rig rig) {
        return rig.writeStore.registeredRelationships(QA).stream()
                .map(edge -> edge.toReference().toString())
                .toList();
    }

    @Test
    void aDeleteThatNamesOnePackQualifiedTargetRemovesOnlyThatEdge(@TempDir Path host) {
        Rig rig = rigWithTwoEdgesToTheSameIdInTwoPacks(host);
        Removals removals = new Removals(rig);
        assertThat(targetsOf(rig))
                .as("the fixture holds both")
                .containsExactly("base::directive:X", "other::directive:X");

        removals.remove("directive:D1", "base::directive:X");

        assertThat(targetsOf(rig)).containsExactly("other::directive:X");
    }

    @Test
    void aDeleteThatNamesNoPackKeepsTheKernelComparisonAndRemovesTheEdgeOnThatPair(@TempDir Path host) {
        Rig rig = rigWithTwoEdgesToTheSameIdInTwoPacks(host);
        Removals removals = new Removals(rig);

        removals.remove("directive:D1", "directive:X");

        assertThat(targetsOf(rig))
                .as("the unqualified reference matches by the kernel's pair")
                .isEmpty();
    }
}
