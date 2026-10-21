package ai.speckitty.charter.deployable;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Null omission follows the contract's schemas: a generated model writes a member it leaves {@code null} exactly
 * when the schema requires the member. A member the schema neither requires nor allows to be {@code null} is
 * omitted (the closed {@code Problem} has no {@code "detail": null}); a required nullable member is written as
 * {@code null} (a pack without a descriptor has {@code "packId": null}). Every generated model is checked, so a
 * model the Jackson configuration does not cover, or a schema change that adds an optional member to a model
 * that has none now, fails here.
 */
@SpringBootTest(
        classes = CharterServiceApplication.class,
        properties = "charter.worktree-token=0123456789abcdef0123456789abcdef0123")
@ExtendWith(SpringExtension.class)
class NullOmissionTest {

    private static final String MODEL_PACKAGE_PATH = "ai/speckitty/charter/api/model/";
    private static final Pattern RENAMED = Pattern.compile("(.+)_\\d+");

    @Autowired
    private ObjectMapper json;

    /** One member of a contract schema. */
    private record Member(String name, boolean required, boolean nullable) {}

    @TestFactory
    List<DynamicTest> everyGeneratedModelWritesItsNullMembersAsItsSchemaSays() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (Class<?> model : generatedModels()) {
            tests.add(DynamicTest.dynamicTest(model.getSimpleName(), () -> check(model)));
        }
        assertThat(tests).as("generated models found").isNotEmpty();
        return tests;
    }

    @TestFactory
    List<DynamicTest> theModelsIncludeOneWithAnOptionalMemberAndOneWithARequiredNullableMember() throws IOException {
        List<Member> all = new ArrayList<>();
        for (Class<?> model : generatedModels()) {
            all.addAll(members(schemaOf(model)));
        }
        return List.of(
                DynamicTest.dynamicTest(
                        "an optional non-nullable member exists",
                        () -> assertThat(all).anyMatch(m -> !m.required() && !m.nullable())),
                DynamicTest.dynamicTest(
                        "a required nullable member exists",
                        () -> assertThat(all).anyMatch(m -> m.required() && m.nullable())));
    }

    private void check(Class<?> model) throws ReflectiveOperationException {
        JsonNode written = json.valueToTree(model.getDeclaredConstructor().newInstance());
        for (Member member : members(schemaOf(model))) {
            if (!member.required() && !member.nullable()) {
                assertThat(written.has(member.name()))
                        .as(
                                "%s.%s is optional and not nullable, so a null is omitted",
                                model.getSimpleName(), member.name())
                        .isFalse();
            } else if (member.required() && member.nullable()) {
                assertThat(written.has(member.name())
                                && written.get(member.name()).isNull())
                        .as(
                                "%s.%s is required and nullable, so a null is written",
                                model.getSimpleName(), member.name())
                        .isTrue();
            }
        }
    }

    private static List<Class<?>> generatedModels() throws IOException {
        List<Class<?>> models = new ArrayList<>();
        for (Resource resource : new PathMatchingResourcePatternResolver()
                .getResources("classpath*:" + MODEL_PACKAGE_PATH + "*.class")) {
            String name = resource.getFilename();
            if (name != null && !name.contains("$")) {
                try {
                    models.add(Class.forName(MODEL_PACKAGE_PATH.replace('/', '.') + name.replace(".class", "")));
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return models;
    }

    /** The schema file of a model: its name, or the name the generator renamed it from after a collision. */
    private static String schemaOf(Class<?> model) {
        JsonTypeName renamed = model.getAnnotation(JsonTypeName.class);
        if (renamed != null) {
            Matcher matcher = RENAMED.matcher(renamed.value());
            if (matcher.matches()) {
                return find(matcher.group(1));
            }
        }
        return find(model.getSimpleName());
    }

    private static String find(String name) {
        for (String directory : List.of("contract/charter/schemas/", "contract/_shared/schemas/")) {
            if (NullOmissionTest.class.getClassLoader().getResource(directory + name + ".yaml") != null) {
                return directory + name + ".yaml";
            }
        }
        throw new AssertionError("no contract schema " + name + ".yaml for the generated model of that name");
    }

    /** The members of a schema, merging its {@code allOf} branches and following references. */
    private static List<Member> members(String file) {
        Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
        Set<String> required = new LinkedHashSet<>();
        collect(file, load(file), properties, required);
        return properties.entrySet().stream()
                .map(e -> new Member(e.getKey(), required.contains(e.getKey()), nullable(file, e.getValue())))
                .toList();
    }

    private static void collect(
            String file,
            Map<String, Object> schema,
            Map<String, Map<String, Object>> properties,
            Set<String> required) {
        if (schema.get("$ref") instanceof String ref) {
            String target = resolve(file, ref);
            collect(target, load(target), properties, required);
            return;
        }
        for (Object branch : list(schema.get("allOf"))) {
            collect(file, mapping(branch), properties, required);
        }
        list(schema.get("required")).forEach(name -> required.add(String.valueOf(name)));
        mapping(schema.get("properties")).forEach((name, property) -> properties.put(name, mapping(property)));
    }

    /** Whether {@code null} is a value of the property: its type lists {@code null}, or a {@code oneOf} branch is. */
    private static boolean nullable(String file, Map<String, Object> property) {
        if (property.get("$ref") instanceof String ref) {
            String target = resolve(file, ref);
            return nullable(target, load(target));
        }
        Object type = property.get("type");
        if ((type instanceof List<?> types && types.contains("null")) || "null".equals(type)) {
            return true;
        }
        for (String combinator : List.of("oneOf", "anyOf", "allOf")) {
            for (Object branch : list(property.get(combinator))) {
                if (nullable(file, mapping(branch))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A copy of a mapping whose keys are strings; empty for an absent one. */
    private static Map<String, Object> mapping(Object value) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, member) -> copy.put(String.valueOf(key), member));
        } else if (value != null) {
            throw new AssertionError(value + " is not a mapping");
        }
        return copy;
    }

    /** A list; empty for an absent one. */
    private static List<?> list(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> items) {
            return items;
        }
        throw new AssertionError(value + " is not a list");
    }

    private static String resolve(String file, String ref) {
        return URI.create(file).resolve(ref).normalize().toString();
    }

    private static Map<String, Object> load(String file) {
        try (var in = NullOmissionTest.class.getClassLoader().getResourceAsStream(file)) {
            if (in == null) {
                throw new AssertionError("the contract file " + file + " is not on the test classpath");
            }
            return mapping(new Load(LoadSettings.builder().build())
                    .loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
