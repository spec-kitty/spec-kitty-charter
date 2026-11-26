package ai.speckitty.charter.packs;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.packs.domain.PackGraph;
import ai.speckitty.charter.packs.domain.ValidationWalker;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Deque;
import java.util.PriorityQueue;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * PACK_GRAPH_WALKS_LIVE_IN_THE_DOMAIN (ADR-0082 decision 1, #637): the application layer leaves walking the pack graph
 * to {@code PackGraph}. Two rules pin what a hand-written walk needs:
 *
 * <ul>
 *   <li>{@link #NO_WORK_QUEUE}: no application class holds a walk's work queue ({@link Deque} or
 *       {@link PriorityQueue}), which every hand-written descent, ordering and cycle search of the layer used;
 *       {@code OwnerOnly}, which walks up a file path, is the one exception. A blocking queue that feeds an executor
 *       ({@code RegistrationJobs}) is no walk and is not matched;
 *   <li>{@link #EDGES_ARE_READ_IN_THE_DOMAIN}: no application code reads a descriptor's {@code depends_on} or
 *       {@code parent_pack}, by a call or a method reference; it asks {@code PackGraph.buildsOn}. Two read-model
 *       projections read them, each exempt by its full signature so a later overload is not: {@code
 *       ClosureQueries.parentsOf(List)} records each pack's parent in the closure view, whose resolved edges
 *       {@code ClosureRoleFacts} later hands to {@code PackGraph.ancestorsOf} (the walk stays in the domain), and
 *       {@code OfferingReadService.dependenciesOf} answers the declared edges with their held versions.
 *   <li>{@link #ONLY_THE_DOMAIN_DRIVES}: only {@code packs.domain..} calls {@code PackGraph.walk} and {@code
 *       ValidationWalker.run} (ADR-0082 decision 2, #638); the application layer runs a rule factory's {@code
 *       GraphCheck}, which picks the walk mode and the phases, and never drives a walk with a visitor of its own.
 * </ul>
 *
 * <p>A walk written with a list as its stack, or by recursion, over edges the domain already handed out is not caught
 * here; review keeps it out. The known-bad control follows {@code OwnerOnlyBoundaryTest}: a test-only class in a
 * {@code badfixture} package, against which the unchanged production rules must fail.
 */
class PackGraphWalkBoundaryTest {

    private static final String ROOT = "ai.speckitty.charter.packs";
    private static final String APPLICATION = ROOT + ".application..";
    private static final Set<String> EDGE_READS = Set.of("dependencies", "parentPack");
    private static final Set<String> READ_MODEL_PROJECTIONS = Set.of(
            ROOT + ".application.ClosureQueries.parentsOf(java.util.List)",
            ROOT + ".application.OfferingReadService.dependenciesOf(" + ROOT
                    + ".domain.PackCompositionResolver$PackUnderResolution, java.util.Map)");

    static final ArchRule NO_WORK_QUEUE = noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .and()
            .doNotHaveFullyQualifiedName(ROOT + ".application.OwnerOnly")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Deque.class)
            .orShould()
            .dependOnClassesThat()
            .areAssignableTo(PriorityQueue.class)
            .because("the packs domain owns every walk over the pack graph; call PackGraph (ADR-0082, #637)");

    private static final DescribedPredicate<JavaAccess<?>> A_DESCRIPTOR_EDGE_READ = DescribedPredicate.describe(
            "read PackDescriptor.dependencies() or parentPack() outside a read-model projection",
            access -> access.getTargetOwner().isEquivalentTo(PackDescriptor.class)
                    && EDGE_READS.contains(access.getName())
                    && !READ_MODEL_PROJECTIONS.contains(access.getOrigin().getFullName()));

    static final ArchRule EDGES_ARE_READ_IN_THE_DOMAIN = noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .should()
            .accessTargetWhere(A_DESCRIPTOR_EDGE_READ)
            .because("the packs domain reads a pack's builds-on edges and walks them; ask PackGraph.buildsOn and"
                    + " hand the edges to a PackGraph walk (ADR-0082, #637)");

    private static final DescribedPredicate<JavaAccess<?>> A_DRIVER_CALL = DescribedPredicate.describe(
            "call PackGraph.walk or ValidationWalker.run",
            access -> (access.getTargetOwner().isEquivalentTo(PackGraph.class)
                            && access.getName().equals("walk"))
                    || (access.getTargetOwner().isEquivalentTo(ValidationWalker.class)
                            && access.getName().equals("run")));

    static final ArchRule ONLY_THE_DOMAIN_DRIVES = noClasses()
            .that()
            .resideOutsideOfPackage(ROOT + ".domain..")
            .should()
            .accessTargetWhere(A_DRIVER_CALL)
            .because("a walk is driven by the packs domain through a rule factory's GraphCheck (ADR-0082, #638)");

    @Test
    void noApplicationClassWalksThePackGraph() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);

        assertThatCode(() -> NO_WORK_QUEUE.check(production)).doesNotThrowAnyException();
        assertThatCode(() -> EDGES_ARE_READ_IN_THE_DOMAIN.check(production)).doesNotThrowAnyException();
        assertThatCode(() -> ONLY_THE_DOMAIN_DRIVES.check(production)).doesNotThrowAnyException();
    }

    @Test
    void theRulesFailOnAHandWrittenDescent() {
        JavaClasses fixtures = new ClassFileImporter().importPackages(ROOT + ".application.badfixture");

        assertThatThrownBy(() -> NO_WORK_QUEUE.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("HandWrittenDescent.dependents")
                .hasMessageContaining("ArrayDeque")
                .hasMessageContaining("HandWrittenDescent.cycleCandidates")
                .hasMessageContaining("PriorityQueue");
        assertThatThrownBy(() -> EDGES_ARE_READ_IN_THE_DOMAIN.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PackDescriptor.dependencies()")
                .hasMessageContaining("PackDescriptor.parentPack()")
                .hasMessageContaining("HandWrittenDescent.parentsByReference");
        assertThatThrownBy(() -> ONLY_THE_DOMAIN_DRIVES.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("HandWrittenDescent.drive")
                .hasMessageContaining("PackGraph.walk")
                .hasMessageContaining("ValidationWalker.run");
    }
}
