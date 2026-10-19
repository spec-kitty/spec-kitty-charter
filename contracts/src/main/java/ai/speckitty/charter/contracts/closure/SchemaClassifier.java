package ai.speckitty.charter.contracts.closure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Classifies a schema recursively and judges every position nested in it. The rules are those of
 * {@link ClosureChecker}.
 */
final class SchemaClassifier {

    private static final String THEN = "then";
    private static final String ELSE = "else";

    private final Findings findings;
    private final Set<Object> inProgress;

    SchemaClassifier(Findings findings) {
        this(findings, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private SchemaClassifier(Findings findings, Set<Object> inProgress) {
        this.findings = findings;
        this.inProgress = inProgress;
    }

    /** Judges a position: a schema that describes a value of a response. An open position is a violation. */
    void judge(Node position) {
        Closure closure = classify(position);
        if (!closure.passes() && position.isSchema()) {
            findings.open(position.pointer(), closure);
        }
    }

    /** Classifies a schema, its own keywords together with its composition; positions nested in it are judged. */
    Closure classify(Node schema) {
        if (!schema.isSchema()) {
            findings.malformed(schema.pointer(), "is neither a schema nor a boolean");
            return Closure.open(schema.pointer(), "it is not a schema", false);
        }
        if (!schema.isMap()) {
            return OwnShape.classify(schema);
        }
        if (!inProgress.add(schema.value())) {
            return Closure.open(
                    schema.pointer(),
                    "it contains itself through a YAML alias, and a recursive schema cannot be proven closed",
                    false);
        }
        try {
            return classifyMapping(schema);
        } finally {
            inProgress.remove(schema.value());
        }
    }

    private Closure classifyMapping(Node schema) {
        Vocabulary.vet(schema, findings);
        nestedPositions(schema).forEach(this::judge);
        schema.entries("dependentSchemas", findings).forEach(this::classify);
        SchemaClassifier conditionsOnly = new SchemaClassifier(findings.ignoringClosure(), inProgress);
        schema.child("not").ifPresent(conditionsOnly::classify);
        schema.child("if").ifPresent(conditionsOnly::classify);

        List<Closure> parts = new ArrayList<>();
        parts.add(OwnShape.classify(schema));
        schema.elements("allOf", findings).forEach(part -> parts.add(classify(part)));
        union(schema, "anyOf").ifPresent(parts::add);
        union(schema, "oneOf").ifPresent(parts::add);
        conditional(schema).ifPresent(parts::add);
        return parts.size() == 1 ? parts.getFirst() : Conjunction.of(schema.pointer(), parts);
    }

    /** The schemas of the members and items a schema admits: each is a position of its own. */
    private List<Node> nestedPositions(Node schema) {
        Optional<Node> map = schema.child("additionalProperties").filter(Node::isMap);
        return Stream.of(
                        schema.entries("properties", findings).stream(),
                        schema.entries("patternProperties", findings).stream(),
                        map.stream(),
                        schema.child("items").stream(),
                        schema.elements("prefixItems", findings).stream())
                .flatMap(s -> s)
                .toList();
    }

    /** A union is one conjunctive part, named by its keyword; its members are named by their index. */
    private Optional<Closure> union(Node schema, String keyword) {
        List<Node> members = schema.elements(keyword, findings);
        if (members.isEmpty()) {
            return Optional.empty();
        }
        JsonPointer at = schema.pointer().child(keyword);
        return Optional.of(
                Disjunction.of(at, members.stream().map(this::classify).toList()));
    }

    /**
     * Beside an {@code if}, {@code then} and {@code else} are alternatives, a missing one admitting whatever the
     * holder admits. Without an {@code if} they have no effect: they close nothing, and only the positions nested in
     * them are judged.
     */
    private Optional<Closure> conditional(Node schema) {
        if (!schema.has(THEN) && !schema.has(ELSE)) {
            return Optional.empty();
        }
        JsonPointer at = schema.pointer().child(THEN + "|" + ELSE);
        if (!schema.has("if")) {
            schema.child(THEN).ifPresent(this::classify);
            schema.child(ELSE).ifPresent(this::classify);
            return Optional.of(
                    Closure.open(at, "without an if, then and else have no effect, so they close nothing", false));
        }
        Closure then = branch(schema, THEN, "there is no then, so nothing applies where the if holds");
        Closure otherwise = branch(schema, ELSE, "there is no else, so nothing applies where the if fails");
        return Optional.of(Disjunction.of(at, schema.pointer(), List.of(then, otherwise)));
    }

    private Closure branch(Node schema, String keyword, String whenMissing) {
        return schema.child(keyword)
                .map(this::classify)
                .orElseGet(() -> Closure.open(schema.pointer().child(keyword), whenMissing, false));
    }
}
