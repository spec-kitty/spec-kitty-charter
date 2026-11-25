package ai.speckitty.charter.packs;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.PackDescriptor;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Deque;
import org.junit.jupiter.api.Test;

/**
 * PACK_GRAPH_WALKS_LIVE_IN_THE_DOMAIN (ADR-0082 decision 1, #637): no class of the application layer walks the pack
 * graph for cycles, groups or descendants. Two rules pin it:
 *
 * <ul>
 *   <li>{@link #NO_HAND_WRITTEN_DESCENT}: no application class holds a work queue ({@link Deque}), which every
 *       hand-written descent and cycle search of the layer used; {@code OwnerOnly}, which walks up a file path, is the
 *       one exception;
 *   <li>{@link #EDGES_ARE_READ_IN_THE_DOMAIN}: no application class reads a descriptor's {@code depends_on} or
 *       {@code parent_pack} itself; it asks {@code PackGraph.buildsOn}. {@code ClosureQueries} (a lookup of each pack's
 *       parent for the read model) and {@code OfferingReadService} (the declared edges of the offering, projected with
 *       their held versions) read them to answer, not to walk.
 * </ul>
 *
 * <p>The known-bad control follows {@code OwnerOnlyBoundaryTest}: a test-only class in a {@code badfixture} package,
 * against which the unchanged production rules must fail.
 */
class PackGraphWalkBoundaryTest {

    private static final String ROOT = "ai.speckitty.charter.packs";
    private static final String APPLICATION = ROOT + ".application..";

    static final ArchRule NO_HAND_WRITTEN_DESCENT = noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .and()
            .doNotHaveFullyQualifiedName(ROOT + ".application.OwnerOnly")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Deque.class)
            .because("the packs domain owns every walk over the pack graph; call PackGraph (ADR-0082, #637)");

    static final ArchRule EDGES_ARE_READ_IN_THE_DOMAIN = noClasses()
            .that()
            .resideInAPackage(APPLICATION)
            .and()
            .doNotHaveFullyQualifiedName(ROOT + ".application.ClosureQueries")
            .and()
            .doNotHaveFullyQualifiedName(ROOT + ".application.OfferingReadService")
            .should()
            .callMethod(PackDescriptor.class, "dependencies")
            .orShould()
            .callMethod(PackDescriptor.class, "parentPack")
            .because("the packs domain decides which edges a walk follows; ask PackGraph.buildsOn (ADR-0082, #637)");

    @Test
    void noApplicationClassWalksThePackGraph() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);

        assertThatCode(() -> NO_HAND_WRITTEN_DESCENT.check(production)).doesNotThrowAnyException();
        assertThatCode(() -> EDGES_ARE_READ_IN_THE_DOMAIN.check(production)).doesNotThrowAnyException();
    }

    @Test
    void theRulesFailOnAHandWrittenDescent() {
        JavaClasses fixtures = new ClassFileImporter().importPackages(ROOT + ".application.badfixture");

        assertThatThrownBy(() -> NO_HAND_WRITTEN_DESCENT.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("HandWrittenDescent")
                .hasMessageContaining("ArrayDeque");
        assertThatThrownBy(() -> EDGES_ARE_READ_IN_THE_DOMAIN.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("HandWrittenDescent")
                .hasMessageContaining("PackDescriptor.dependencies()")
                .hasMessageContaining("PackDescriptor.parentPack()");
    }
}
