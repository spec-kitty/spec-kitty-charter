package ai.speckitty.charter.packs.infra.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.api.RegistrationApi;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * {@link ArchiveBodyConverters} replaces the {@code Resource} message converter for every operation, so the one operation
 * of the contract with a {@code Resource} request body, the archive upload, must stay the only one (#418, F7). A second
 * would be read through the streaming converter, which hands the controller a body it never judged. This scans the
 * generated API interfaces and fails when one appears, until the converter is narrowed to the operations it is for.
 */
class ResourceBodyOperationsTest {

    @Test
    void theArchiveUploadIsTheOnlyOperationWithAResourceRequestBody() {
        assertThat(resourceBodyOperations(generatedApis()))
                .as(
                        "a second Resource request body is read by StreamingResourceConverter: narrow ArchiveBodyConverters")
                .containsExactly("RegistrationApi.registerPackFromArchive");
    }

    @Test
    void theScanSeesAResourceBodyWhereverItAppears() {
        assertThat(generatedApis())
                .as("the scan found the generated interfaces")
                .contains(RegistrationApi.class);

        assertThat(resourceBodyOperations(List.of(RegistrationApi.class, Another.class)))
                .containsExactly("Another.upload", "RegistrationApi.registerPackFromArchive");
    }

    private static Set<String> resourceBodyOperations(Collection<Class<?>> apis) {
        Set<String> operations = new TreeSet<>();
        for (Class<?> api : apis) {
            for (Method method : api.getMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (parameter.isAnnotationPresent(RequestBody.class)
                            && Resource.class.isAssignableFrom(parameter.getType())) {
                        operations.add(api.getSimpleName() + "." + method.getName());
                    }
                }
            }
        }
        return operations;
    }

    /** Every generated {@code *Api} interface of the contract. */
    private static List<Class<?>> generatedApis() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter(new RegexPatternTypeFilter(java.util.regex.Pattern.compile(".*\\.[A-Za-z]+Api")));
        List<Class<?>> apis = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("ai.speckitty.charter.api")) {
            try {
                apis.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException missing) {
                throw new AssertionError("a scanned class cannot be loaded: " + definition.getBeanClassName(), missing);
            }
        }
        return apis;
    }

    /** A second operation with a {@code Resource} request body, as a future contract could declare. */
    interface Another {
        void upload(@RequestBody Resource body);
    }
}
