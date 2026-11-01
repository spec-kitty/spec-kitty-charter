package ai.speckitty.charter.contracts.closure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Finds the response schema positions of an OpenAPI document (rules 2 and 3 of {@link ClosureChecker}): each
 * {@code paths.*.<method>.responses.*.content.*.schema}, each response header's {@code schema} and each schema of a
 * response header's {@code content}. The document around them fails closed: an operation with no {@code responses},
 * or an empty one, is reported (a response without a body is fine, an operation without responses is not); a key
 * outside the known set of a path item, an operation, a response, a header or a media type is reported, and so are
 * {@code webhooks} and {@code callbacks}, which the contract does not use.
 */
final class ResponseSchemas {

    private static final Set<String> METHODS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
    private static final Set<String> PATH_ITEM_KEYS = union(METHODS, "summary", "description", "servers", "parameters");
    private static final Set<String> OPERATION_KEYS = Set.of(
            "tags",
            "summary",
            "description",
            "externalDocs",
            "operationId",
            "parameters",
            "requestBody",
            "responses",
            "callbacks",
            "deprecated",
            "security",
            "servers");
    private static final Set<String> RESPONSE_KEYS = Set.of("description", "headers", "content", "links");
    private static final Set<String> HEADER_KEYS = Set.of(
            "description",
            "required",
            "deprecated",
            "allowEmptyValue",
            "allowReserved",
            "style",
            "explode",
            "schema",
            "example",
            "examples",
            "content");
    private static final Set<String> MEDIA_TYPE_KEYS = Set.of("schema", "example", "examples", "encoding");
    private static final Pattern STATUS = Pattern.compile("default|[1-5](\\d\\d|XX)");
    private static final String REF = "$ref";
    private static final String CONTENT = "content";
    private static final String SCHEMA = "schema";
    private static final String UNUSED = "the charter contract uses no %s, and the checker would need to judge their"
            + " responses; drop them or teach the checker";

    private ResponseSchemas() {}

    static List<Node> in(@Nullable Object document, Findings findings) {
        Node root = new Node(document, JsonPointer.ROOT);
        Dialect.check(root, findings);
        root.child("webhooks")
                .ifPresent(webhooks -> findings.malformed(webhooks.pointer(), UNUSED.formatted("webhooks")));
        if (root.child("paths").filter(Node::isMap).isEmpty()) {
            findings.malformed(JsonPointer.ROOT, "the document has no paths");
            return List.of();
        }
        List<Node> schemas = new ArrayList<>();
        for (Node pathItem : root.entries("paths", findings)) {
            String path = key(pathItem);
            if (path.startsWith("x-")) {
                continue;
            }
            if (!path.startsWith("/")) {
                findings.malformed(pathItem.pointer(), "a path must start with /");
            } else if (mapping(pathItem, findings)) {
                schemas.addAll(ofPathItem(pathItem, findings));
            }
        }
        if (schemas.isEmpty()) {
            findings.malformed(
                    JsonPointer.ROOT,
                    "no response schema was judged, so the checker proved nothing; a contract whose every response"
                            + " has no body is not one the checker can vouch for");
        }
        return schemas;
    }

    private static List<Node> ofPathItem(Node pathItem, Findings findings) {
        refused(pathItem, findings);
        known(pathItem, PATH_ITEM_KEYS, "a path item", findings);
        List<Node> schemas = new ArrayList<>();
        pathItem.members().stream()
                .filter(op -> METHODS.contains(key(op)))
                .filter(op -> mapping(op, findings))
                .forEach(operation -> schemas.addAll(ofOperation(operation, findings)));
        return schemas;
    }

    private static List<Node> ofOperation(Node operation, Findings findings) {
        known(operation, OPERATION_KEYS, "an operation", findings);
        operation.child("callbacks").ifPresent(c -> findings.malformed(c.pointer(), UNUSED.formatted("callbacks")));
        requireResponses(operation, findings);
        List<Node> schemas = new ArrayList<>();
        for (Node response : operation.entries("responses", findings)) {
            String status = key(response);
            if (status.startsWith("x-")) {
                continue;
            }
            if (!STATUS.matcher(status).matches()) {
                findings.malformed(
                        response.pointer(),
                        "'" + status + "' is not a status code, a status range such as 4XX, or default");
            } else if (mapping(response, findings)) {
                schemas.addAll(ofResponse(response, findings));
            }
        }
        return schemas;
    }

    /** An operation answers something: a missing or empty {@code responses} would leave nothing to judge. */
    private static void requireResponses(Node operation, Findings findings) {
        Optional<Node> responses = operation.child("responses");
        if (responses.isEmpty()) {
            findings.malformed(
                    operation.pointer(),
                    "the operation has no responses, so nothing it answers is judged; declare each response, even one"
                            + " without a body");
        } else if (responses.get().isMap()
                && responses.get().members().stream()
                        .allMatch(response -> key(response).startsWith("x-"))) {
            findings.malformed(
                    responses.get().pointer(),
                    "the operation's responses is empty, so nothing it answers is judged; declare each response, even"
                            + " one without a body");
        }
    }

    private static List<Node> ofResponse(Node response, Findings findings) {
        refused(response, findings);
        known(response, RESPONSE_KEYS, "a response", findings);
        List<Node> schemas = new ArrayList<>();
        response.entries("headers", findings).stream()
                .filter(header -> mapping(header, findings))
                .forEach(header -> schemas.addAll(ofHeader(header, findings)));
        schemas.addAll(ofContent(response, findings));
        return schemas;
    }

    /** A header is judged like a body: its schema, or each schema of its content, is a position. */
    private static List<Node> ofHeader(Node header, Findings findings) {
        refused(header, findings);
        known(header, HEADER_KEYS, "a header", findings);
        if (!header.has(SCHEMA) && !header.has(CONTENT)) {
            findings.malformed(
                    header.pointer(), "the header has neither a schema nor content, so its value is unconstrained");
        }
        return Stream.concat(header.child(SCHEMA).stream(), ofContent(header, findings).stream())
                .toList();
    }

    private static List<Node> ofContent(Node holder, Findings findings) {
        List<Node> schemas = new ArrayList<>();
        for (Node media : holder.entries(CONTENT, findings)) {
            if (!mapping(media, findings)) {
                continue;
            }
            refused(media, findings);
            known(media, MEDIA_TYPE_KEYS, "a media type", findings);
            media.child(SCHEMA)
                    .ifPresentOrElse(
                            schemas::add,
                            () -> findings.malformed(
                                    media.pointer(), "the media type has no schema, so its body is unconstrained"));
        }
        return schemas;
    }

    /** Reports every key of {@code node} outside {@code keys}; a {@code $ref} and every {@code x-} key are let by. */
    private static void known(Node node, Set<String> keys, String what, Findings findings) {
        for (String key : node.keys()) {
            if (!keys.contains(key) && !REF.equals(key) && !key.startsWith("x-")) {
                findings.malformed(
                        node.pointer().child(key),
                        "'" + key + "' is not " + what
                                + " key the checker knows, so what it holds goes unjudged; known: "
                                + new TreeSet<>(keys));
            }
        }
    }

    private static boolean mapping(Node node, Findings findings) {
        if (!node.isMap()) {
            findings.malformed(node.pointer(), "must be a mapping");
        }
        return node.isMap();
    }

    private static void refused(Node node, Findings findings) {
        if (node.has(REF)) {
            findings.malformed(
                    node.pointer().child(REF),
                    "a $ref is left in the bundle: it names no component of it, or a component that holds itself");
        }
    }

    private static String key(Node member) {
        return member.pointer().tokens().getLast();
    }

    private static Set<String> union(Set<String> base, String... more) {
        Set<String> all = new TreeSet<>(base);
        all.addAll(List.of(more));
        return Set.copyOf(all);
    }
}
