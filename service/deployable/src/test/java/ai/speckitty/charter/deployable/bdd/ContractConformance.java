package ai.speckitty.charter.deployable.bdd;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import tools.jackson.databind.JsonNode;

/**
 * Checks a response against the split contract on the test classpath ({@code contract/charter/openapi.yaml} and
 * the vendored {@code contract/_shared} components): the response the operation declares for the status, or its
 * {@code default}, must list the response's content type, and the body must validate against that content's
 * schema. A path no operation defines must answer the shared {@code Problem} response, the only non-2xx body of
 * any module.
 *
 * <p>The validator covers the JSON Schema keywords the contract uses: {@code $ref} (to a whole file),
 * {@code allOf}, {@code oneOf}, {@code type} (one name or a list), {@code properties}, {@code required},
 * {@code additionalProperties: false}, {@code items}, {@code enum}, {@code pattern}, {@code minLength}, {@code maxLength},
 * {@code maxItems}, {@code minimum} and {@code maximum}. Any other keyword fails the check, so a constraint the contract adds is
 * never ignored in silence: the check walks every schema of the contract once when it is built, not only the
 * ones a body reaches. The contract tree is a constructor argument, so the fail-closed branches are tested over
 * fixtures.
 *
 * <p>Not routed through here, as no scenario can provoke them: the {@code 405} of a method the path does not have
 * and the container's {@code /error} page, pinned in {@code ProblemResponsesTest}; and what Tomcat answers before
 * routing (a malformed path, a missing {@code Host}, an oversized header, {@code TRACE}), pinned in
 * {@code ConnectorErrorsTest}. Both assert the closed {@code Problem} body literally.
 */
final class ContractConformance {

    private static final String OPENAPI = "charter/openapi.yaml";
    private static final String SHARED_PROBLEM = "_shared/responses/Problem.yaml";
    private static final Set<String> CONSTRAINTS =
            Set.of("type", "required", "enum", "pattern", "minLength", "maxLength", "maxItems", "minimum", "maximum");
    private static final Set<String> ANNOTATIONS = Set.of("title", "description", "examples", "format");

    private final Path root;
    private final Map<String, Map<String, Object>> documents = new HashMap<>();

    /** The contract on the test classpath, the {@code contract} directory the test resources hold. */
    ContractConformance() {
        this(classpathRoot());
    }

    /**
     * The contract under {@code root}, for example a fixture: {@code charter/openapi.yaml} and the
     * {@code _shared} components below it.
     *
     * @param root the directory that holds {@code charter/} and {@code _shared/}
     */
    ContractConformance(Path root) {
        this.root = root;
        vetEverySchema();
    }

    /**
     * The contract's version.
     *
     * @return the {@code info.version} of {@code charter/openapi.yaml}
     */
    String version() {
        return String.valueOf(mapping(document(OPENAPI).get("info"), "info").get("version"));
    }

    /**
     * Walks every schema of the contract once: each file below a {@code schemas} directory, and each schema of
     * a response, whether the response sits in a {@code responses} directory or in an operation. A keyword the
     * check does not implement fails here, whether or not a body ever walks the schema that holds it.
     */
    private void vetEverySchema() {
        Set<String> vetted = new HashSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString();
                String directory = String.valueOf(file.getParent().getFileName());
                String resource = root.relativize(file).toString().replace(File.separatorChar, '/');
                if (!name.endsWith(".yaml") || name.equals("_index.yaml")) {
                    continue;
                }
                if (directory.equals("schemas")) {
                    vetFile(resource, vetted);
                } else if (directory.equals("responses")) {
                    vetResponse(new Located(document(resource), resource), vetted);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (Files.isRegularFile(root.resolve(OPENAPI))) {
            vetOperations(vetted);
        }
    }

    private void vetOperations(Set<String> vetted) {
        for (Map.Entry<String, Object> entry :
                mapping(document(OPENAPI).get("paths"), "paths").entrySet()) {
            Located item = resolve(new Located(mapping(entry.getValue(), entry.getKey()), OPENAPI));
            for (Object operation : item.node().values()) {
                if (operation instanceof Map<?, ?> map && map.get("responses") != null) {
                    for (Map.Entry<String, Object> response : mapping(
                                    map.get("responses"), entry.getKey() + " responses")
                            .entrySet()) {
                        vetResponse(
                                resolve(new Located(mapping(response.getValue(), entry.getKey()), item.file())),
                                vetted);
                    }
                }
            }
        }
    }

    private void vetResponse(Located response, Set<String> vetted) {
        if (response.node().get("content") == null) {
            return;
        }
        for (Object media : mapping(response.node().get("content"), response.file() + " content")
                .values()) {
            Object schema = mapping(media, response.file()).get("schema");
            if (schema != null) {
                vetNode(new Located(mapping(schema, response.file() + " schema"), response.file()), vetted);
            }
        }
    }

    private void vetFile(String resource, Set<String> vetted) {
        if (vetted.add(resource)) {
            vetNode(new Located(document(resource), resource), vetted);
        }
    }

    private void vetNode(Located schema, Set<String> vetted) {
        if (schema.node().containsKey("$ref")) {
            vetFile(resolve(schema).file(), vetted);
            return;
        }
        for (Map.Entry<String, Object> keyword : schema.node().entrySet()) {
            String key = keyword.getKey();
            Object argument = keyword.getValue();
            switch (key) {
                case "allOf", "oneOf" -> {
                    for (Object branch : list(argument, schema.file() + " " + key)) {
                        vetNode(new Located(mapping(branch, schema.file() + " " + key), schema.file()), vetted);
                    }
                }
                case "properties" -> {
                    for (Map.Entry<String, Object> property :
                            mapping(argument, schema.file() + " properties").entrySet()) {
                        vetNode(new Located(mapping(property.getValue(), property.getKey()), schema.file()), vetted);
                    }
                }
                case "items" ->
                    vetNode(new Located(mapping(argument, schema.file() + " items"), schema.file()), vetted);
                case "additionalProperties" -> {
                    if (!Boolean.FALSE.equals(argument)) {
                        throw new AssertionError(
                                schema.file() + ": only additionalProperties: false is supported, not " + argument);
                    }
                }
                default -> {
                    if (!CONSTRAINTS.contains(key) && !key.startsWith("x-") && !ANNOTATIONS.contains(key)) {
                        throw new AssertionError(
                                schema.file() + " uses the keyword '" + key + "', which this check does not implement");
                    }
                }
            }
        }
    }

    private static Path classpathRoot() {
        URL url = ContractConformance.class.getClassLoader().getResource("contract");
        if (url == null) {
            throw new AssertionError("the contract directory is not on the test classpath");
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError("the contract directory has no file location: " + url, e);
        }
    }

    /**
     * The contract violations of one response; empty when it conforms.
     *
     * @param method the request method, for example {@code get}
     * @param path the request path below the server prefix, without a query
     * @param status the response status
     * @param contentType the response content type, or {@code null} when there is none
     * @param body the response body as JSON, or {@code null} when it is empty or not JSON
     * @return what does not conform, one sentence each
     */
    List<String> violations(
            String method, String path, int status, @Nullable String contentType, @Nullable JsonNode body) {
        Located response = response(method, path, status);
        Map<String, Object> content = mapping(response.node().get("content"), response.file() + " content");
        String mediaType = contentType == null ? "" : contentType.split(";", 2)[0].strip();
        Object media = content.get(mediaType);
        if (media == null) {
            return List.of(response.file() + " declares no content of type '" + mediaType + "' for " + status
                    + "; it declares " + content.keySet());
        }
        if (body == null) {
            return List.of("the " + status + " body is not JSON");
        }
        Located schema = new Located(
                mapping(mapping(media, response.file()).get("schema"), response.file() + " schema"), response.file());
        List<String> violations = new ArrayList<>();
        validate(body, schema, "$", violations);
        return violations;
    }

    private Located response(String method, String path, int status) {
        Map<String, Object> openapi = document(OPENAPI);
        Map<String, Object> paths = mapping(openapi.get("paths"), "paths");
        for (Map.Entry<String, Object> entry : paths.entrySet()) {
            if (!template(entry.getKey()).matcher(path).matches()) {
                continue;
            }
            Located item = resolve(new Located(mapping(entry.getValue(), entry.getKey()), OPENAPI));
            Object operation = item.node().get(method);
            if (operation == null) {
                break;
            }
            Map<String, Object> responses = mapping(mapping(operation, path).get("responses"), path + " responses");
            Object declared = Optional.ofNullable(responses.get(String.valueOf(status)))
                    .orElseGet(() -> responses.get("default"));
            if (declared == null) {
                throw new AssertionError(item.file() + " declares no " + status + " and no default response");
            }
            return resolve(new Located(mapping(declared, path + " " + status), item.file()));
        }
        return new Located(document(SHARED_PROBLEM), SHARED_PROBLEM);
    }

    private static Pattern template(String pathTemplate) {
        StringBuilder regex = new StringBuilder();
        for (String part : pathTemplate.split("\\{[^}]+}", -1)) {
            if (!regex.isEmpty()) {
                regex.append("[^/]+");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString());
    }

    private void validate(JsonNode value, Located schema, String at, List<String> violations) {
        Located resolved = resolve(schema);
        Map<String, Object> node = resolved.node();
        for (Map.Entry<String, Object> keyword : node.entrySet()) {
            String key = keyword.getKey();
            Object argument = keyword.getValue();
            switch (key) {
                case "allOf" -> {
                    for (Object branch : list(argument, at + " allOf")) {
                        validate(value, new Located(mapping(branch, at), resolved.file()), at, violations);
                    }
                }
                case "oneOf" -> {
                    long matching = list(argument, at + " oneOf").stream()
                            .filter(branch -> {
                                List<String> branchViolations = new ArrayList<>();
                                validate(
                                        value, new Located(mapping(branch, at), resolved.file()), at, branchViolations);
                                return branchViolations.isEmpty();
                            })
                            .count();
                    if (matching != 1) {
                        violations.add(at + " matches " + matching + " of the oneOf branches, not exactly one");
                    }
                }
                case "type" -> {
                    List<?> types = argument instanceof List<?> l ? l : List.of(argument);
                    if (types.stream().noneMatch(t -> hasType(value, String.valueOf(t)))) {
                        violations.add(at + " is " + value.getNodeType() + ", not " + types);
                    }
                }
                case "properties" -> {
                    for (Map.Entry<String, Object> property :
                            mapping(argument, at + " properties").entrySet()) {
                        JsonNode member = value.isObject() ? value.get(property.getKey()) : null;
                        if (member != null) {
                            validate(
                                    member,
                                    new Located(mapping(property.getValue(), property.getKey()), resolved.file()),
                                    at + "." + property.getKey(),
                                    violations);
                        }
                    }
                }
                case "required" -> {
                    for (Object name : list(argument, at + " required")) {
                        if (value.isObject() && !value.has(String.valueOf(name))) {
                            violations.add(at + " lacks the required member '" + name + "'");
                        }
                    }
                }
                case "additionalProperties" -> {
                    if (!Boolean.FALSE.equals(argument)) {
                        throw new AssertionError(at + ": only additionalProperties: false is supported");
                    }
                    Set<String> declared = mapping(node.getOrDefault("properties", Map.of()), at)
                            .keySet();
                    if (value.isObject()) {
                        value.propertyNames().stream()
                                .filter(name -> !declared.contains(name))
                                .forEach(name -> violations.add(at + " has the undeclared member '" + name + "'"));
                    }
                }
                case "items" -> {
                    if (value.isArray()) {
                        for (int i = 0; i < value.size(); i++) {
                            validate(
                                    value.get(i),
                                    new Located(mapping(argument, at + " items"), resolved.file()),
                                    at + "[" + i + "]",
                                    violations);
                        }
                    }
                }
                case "enum" -> {
                    if (list(argument, at + " enum").stream()
                            .noneMatch(v -> value.isString() && value.asString().equals(String.valueOf(v)))) {
                        violations.add(at + " is " + value + ", not one of " + argument);
                    }
                }
                case "pattern" -> {
                    if (value.isString()
                            && !Pattern.compile(String.valueOf(argument))
                                    .matcher(value.asString())
                                    .find()) {
                        violations.add(at + " '" + value.asString() + "' does not match " + argument);
                    }
                }
                case "minLength" -> {
                    if (value.isString()
                            && value.asString()
                                            .codePointCount(0, value.asString().length())
                                    < ((Number) argument).intValue()) {
                        violations.add(at + " is shorter than " + argument);
                    }
                }
                case "maxLength" -> {
                    if (value.isString()
                            && value.asString()
                                            .codePointCount(0, value.asString().length())
                                    > ((Number) argument).intValue()) {
                        violations.add(at + " is longer than " + argument);
                    }
                }
                case "maxItems" -> {
                    if (value.isArray() && value.size() > ((Number) argument).intValue()) {
                        violations.add(at + " holds more than " + argument + " items");
                    }
                }
                case "minimum" -> {
                    if (value.isNumber() && value.asDouble() < ((Number) argument).doubleValue()) {
                        violations.add(at + " is below " + argument);
                    }
                }
                case "maximum" -> {
                    if (value.isNumber() && value.asDouble() > ((Number) argument).doubleValue()) {
                        violations.add(at + " is above " + argument);
                    }
                }
                default -> {
                    if (!key.startsWith("x-") && !ANNOTATIONS.contains(key)) {
                        throw new AssertionError(resolved.file() + " uses the keyword '" + key
                                + "', which this check does not implement");
                    }
                }
            }
        }
    }

    private static boolean hasType(JsonNode value, String type) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isString();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> throw new AssertionError("unknown type '" + type + "'");
        };
    }

    /** Follows {@code $ref} chains; a reference names a whole file, relative to the file that holds it. */
    private Located resolve(Located located) {
        Located current = located;
        while (current.node().get("$ref") instanceof String ref) {
            if (current.node().size() != 1) {
                throw new AssertionError(current.file() + ": a $ref with sibling keywords is not supported");
            }
            if (ref.contains("#")) {
                throw new AssertionError(current.file() + ": the fragment reference '" + ref + "' is not supported");
            }
            String file = URI.create(current.file()).resolve(ref).normalize().toString();
            current = new Located(document(file), file);
        }
        return current;
    }

    private Map<String, Object> document(String resource) {
        return documents.computeIfAbsent(resource, this::load);
    }

    private Map<String, Object> load(String resource) {
        Path file = root.resolve(resource);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError("the contract file " + resource + " is not under " + root);
        }
        try {
            Object document = new Load(LoadSettings.builder().build()).loadFromString(Files.readString(file));
            return mapping(document, resource);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A copy of a mapping whose keys are all strings, in document order. */
    private static Map<String, Object> mapping(@Nullable Object value, String what) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new AssertionError(what + " is not a mapping");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, member) -> copy.put(String.valueOf(key), member));
        return copy;
    }

    private static List<?> list(@Nullable Object value, String what) {
        if (value instanceof List<?> items) {
            return items;
        }
        throw new AssertionError(what + " is not a list");
    }

    /** A node of a contract file and the classpath name of that file, against which its references resolve. */
    private record Located(Map<String, Object> node, String file) {}
}
