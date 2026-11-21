package ai.speckitty.charter.packs.domain;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ONE_CLOSURE_FAULT_TYPE (#614, slice 1 of #636): the closure checks state their faults in {@link ClosureFault} alone.
 * The {@code CLOSURE} composition's own fault type, {@code CompositionRules.CompositionFault}, is deleted, and no bridge
 * factory from it survives: every {@code ClosureFault.of} takes the check that found the fault.
 */
@DisplayName("One closure fault type: CompositionFault is gone (#614)")
class OneClosureFaultTypeTest {

    private static final String ROOT = "ai.speckitty.charter.packs";

    static final ArchRule NO_COMPOSITION_FAULT = noClasses()
            .should()
            .haveSimpleName("CompositionFault")
            .because("the closure checks have one fault type, ClosureFault (#614)");

    @Test
    @DisplayName("no class of the packs module is named CompositionFault")
    void noCompositionFaultRemains() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);

        assertThat(production.containPackage(ROOT + ".domain.rules")).isTrue();
        assertThatCode(() -> NO_COMPOSITION_FAULT.check(production)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName(
            "every ClosureFault.of names the check that found the fault: there is no bridge from another fault type")
    void everyFactoryTakesTheOrigin() {
        List<Method> factories = Arrays.stream(ClosureFault.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("of") && Modifier.isStatic(method.getModifiers()))
                .toList();

        assertThat(factories).isNotEmpty();
        assertThat(factories)
                .allSatisfy(factory -> assertThat(factory.getParameterTypes()[0].getSimpleName())
                        .isEqualTo("Origin"));
    }
}
