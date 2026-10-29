package ai.speckitty.charter.deployable;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A bean-validation constraint the contract generator puts on a model getter must fit the getter's type (#194, S3).
 * The generator turns a schema {@code pattern} into {@code @Pattern} and a {@code minLength}/{@code maxLength} or
 * {@code minItems}/{@code maxItems} into {@code @Size}, whatever Java type it chose for the field: a {@code pattern}
 * on a {@code format: uri} field yields {@code @Pattern} on a {@link java.net.URI} getter. No validator is on the
 * classpath today, so the constraints are inert; once one is, Hibernate Validator throws
 * {@code UnexpectedTypeException} for such a pair, a server error on every request that carries the body.
 */
class GeneratedConstraintTypesTest {

    private static final String MODEL = "ai.speckitty.charter.api.model";

    private static final String PATTERN = "jakarta.validation.constraints.Pattern";
    private static final String SIZE = "jakarta.validation.constraints.Size";

    /** {@code @Pattern} on a {@link CharSequence}; {@code @Size} on one, a collection, a map or an array. */
    private static ArchRule constraintsFitTheirType(String inPackage) {
        return methods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage(inPackage + "..")
                .should(new ArchCondition<JavaMethod>("carry a constraint only on a type it applies to") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        JavaClass type = method.getRawReturnType();
                        if (method.isAnnotatedWith(PATTERN) && !type.isAssignableTo(CharSequence.class)) {
                            events.add(SimpleConditionEvent.violated(
                                    method, method.getFullName() + " carries @Pattern on " + type.getName()));
                        }
                        boolean sized = type.isAssignableTo(CharSequence.class)
                                || type.isAssignableTo(Collection.class)
                                || type.isAssignableTo(Map.class)
                                || type.isArray();
                        if (method.isAnnotatedWith(SIZE) && !sized) {
                            events.add(SimpleConditionEvent.violated(
                                    method, method.getFullName() + " carries @Size on " + type.getName()));
                        }
                    }
                })
                .allowEmptyShould(false);
    }

    @Test
    void everyGeneratedConstraintFitsTheTypeOfItsGetter() {
        JavaClasses models = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(MODEL);

        constraintsFitTheirType(MODEL).check(models);
    }

    @Test
    void theRuleFailsOnAPatternOnAUriGetter() {
        JavaClasses fixture = new ClassFileImporter().importClasses(PatternOnUri.class);

        assertThatThrownBy(() -> constraintsFitTheirType(PatternOnUri.class.getPackageName())
                        .check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("getGuideUri()")
                .hasMessageContaining("carries @Pattern on java.net.URI");
    }

    /** The known-bad: what the generator emitted for {@code guideUri} while it carried {@code pattern}. */
    static final class PatternOnUri {
        @jakarta.validation.constraints.Pattern(regexp = "^https://") java.net.URI getGuideUri() {
            return java.net.URI.create("https://example.org");
        }
    }
}
