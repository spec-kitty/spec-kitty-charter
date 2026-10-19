package ai.speckitty.charter.contracts.closure;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Finds the response schemas of an OpenAPI document: {@code paths.*.<method>.responses.*.content.*.schema}. A media
 * type with no schema is reported, its body being unconstrained; a response with no content has no body to judge.
 */
final class ResponseSchemas {

    private static final Set<String> METHODS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
    private static final String REF = "$ref";

    private ResponseSchemas() {}

    static List<Node> in(Object document, Findings findings) {
        Node root = new Node(document, JsonPointer.ROOT);
        Dialect.check(root, findings);
        if (root.child("paths").filter(Node::isMap).isEmpty()) {
            findings.malformed(JsonPointer.ROOT, "the document has no paths");
            return List.of();
        }
        List<Node> schemas = new ArrayList<>();
        for (Node pathItem : root.entries("paths", findings)) {
            refused(pathItem, findings);
            pathItem.members().stream()
                    .filter(op -> METHODS.contains(op.pointer().tokens().getLast()))
                    .forEach(operation -> schemas.addAll(ofOperation(operation, findings)));
        }
        return schemas;
    }

    private static List<Node> ofOperation(Node operation, Findings findings) {
        List<Node> schemas = new ArrayList<>();
        for (Node response : operation.entries("responses", findings)) {
            refused(response, findings);
            for (Node media : response.entries("content", findings)) {
                media.child("schema")
                        .ifPresentOrElse(
                                schemas::add,
                                () -> findings.malformed(
                                        media.pointer(), "the media type has no schema, so its body is unconstrained"));
            }
        }
        return schemas;
    }

    private static void refused(Node node, Findings findings) {
        if (node.has(REF)) {
            findings.malformed(
                    node.pointer().child(REF), "a $ref is left in the bundle; the bundle must inline every reference");
        }
    }
}
