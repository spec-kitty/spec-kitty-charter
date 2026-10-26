package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Each contract schema is generated as one model (issue #119). The generator used to see a schema file
 * referenced from two places as two schemas, {@code Tactic} and {@code Tactic_1}, and emit a second class
 * ({@code Tactic1}) for it. The second class carries the collision name in {@code @JsonTypeName}, so a model
 * annotated with a {@code _<n>} name is a duplicate, and core, which generates from the same contract, would
 * inherit it.
 */
class GeneratedModelNamesTest {

    private static final String MODEL_PACKAGE_PATH = "ai/speckitty/charter/api/model/";
    private static final Pattern COLLISION_NAME = Pattern.compile(".+_\\d+");

    @Test
    void noGeneratedModelIsASecondCopyOfASchema() throws IOException, ClassNotFoundException {
        List<Class<?>> models = generatedModels();
        assertThat(models).as("generated models found").isNotEmpty();
        List<String> duplicates = new ArrayList<>();
        for (Class<?> model : models) {
            JsonTypeName name = model.getAnnotation(JsonTypeName.class);
            if (name != null && COLLISION_NAME.matcher(name.value()).matches()) {
                duplicates.add(model.getSimpleName() + " (" + name.value() + ")");
            }
        }
        assertThat(duplicates)
                .as("models generated a second time under a collision name")
                .isEmpty();
    }

    private static List<Class<?>> generatedModels() throws IOException, ClassNotFoundException {
        List<Class<?>> models = new ArrayList<>();
        for (Resource resource : new PathMatchingResourcePatternResolver()
                .getResources("classpath*:" + MODEL_PACKAGE_PATH + "*.class")) {
            String name = resource.getFilename();
            if (name != null && !name.contains("$")) {
                models.add(Class.forName(MODEL_PACKAGE_PATH.replace('/', '.') + name.replace(".class", "")));
            }
        }
        return models;
    }
}
