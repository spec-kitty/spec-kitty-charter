package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.model.CharterRefusal;
import ai.speckitty.charter.api.model.Problem;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.context.annotation.ImportRuntimeHints;

/** The problem bodies a native executable writes outside a controller's signature bind (ADR-0046 decision 5). */
class ProblemRuntimeHintsTest {

    private final RuntimeHints hints = registered();

    private static RuntimeHints registered() {
        RuntimeHints hints = new RuntimeHints();
        new ProblemRuntimeHints().registerHints(hints, ProblemRuntimeHintsTest.class.getClassLoader());
        return hints;
    }

    @Test
    void theProblemJsonConfigurationImportsTheHints() {
        assertThat(ProblemJson.class.getAnnotation(ImportRuntimeHints.class))
                .isNotNull()
                .extracting(ImportRuntimeHints::value)
                .isEqualTo(new Class<?>[] {ProblemRuntimeHints.class});
    }

    @Test
    void theProblemBodiesBindWithTheirAccessors() {
        assertThat(RuntimeHintsPredicates.reflection().onType(Problem.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(CharterRefusal.class))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(Problem.class, "getStatus"))
                .accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(CharterRefusal.class, "getCode"))
                .accepts(hints);
    }

    @Test
    void theMixInIsVisible() {
        assertThat(RuntimeHintsPredicates.reflection().onType(ProblemJson.omitAbsentMembers()))
                .accepts(hints);
    }

    @Test
    void theErrorReportValveCanBeInstantiated() {
        assertThat(RuntimeHintsPredicates.reflection()
                        .onType(ProblemErrorReportValve.class)
                        .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS))
                .accepts(hints);
    }
}
