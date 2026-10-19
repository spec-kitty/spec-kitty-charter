package ai.speckitty.charter.contracts.closure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * The closed-response-objects convention of the charter contract: every response schema is closed, so a new
 * response member is a deliberate contract change that the breaking-change check sees, never something a server can
 * add unannounced (DM 01M584Q3NNK0B78H9NKHV7VJDK). This Javadoc is the authority on what the checker accepts.
 *
 * <ol>
 *   <li><b>Input.</b> The bundled contract ({@code bundleContract}'s output, every {@code $ref} inlined), an
 *       OpenAPI 3.1 document ({@code openapi: 3.1.x}) whose schemas are the OAS 3.1 default dialect,
 *       {@value #DEFAULT_DIALECT} (JSON Schema 2020-12 with the OpenAPI vocabulary). The checker assumes that
 *       dialect: another {@code openapi} version, or a {@code jsonSchemaDialect} other than the default, is a
 *       violation, because its keywords may mean something else. Every response schema,
 *       {@code paths.*.<method>.responses.*.content.*.schema}, is a <i>position</i>. A media type of
 *       {@code content} with no {@code schema} is a violation at that media type, its body being unconstrained;
 *       a response with no {@code content} (a 204) has no body and passes. A {@code $ref} left anywhere the
 *       checker reads (a path item, a response, a schema) is a violation: the bundle is wrong.</li>
 *   <li><b>Positive, recursive classification.</b> A schema is <i>closed</i> (admits no object with members it
 *       does not list), <i>not an object</i> (provably admits no object, and no array whose items go unjudged) or
 *       <i>open</i>. Only the first two pass; whatever the checker cannot prove is open. Internally the objects
 *       and the arrays a schema admits are two facets ({@link Closure}), each none, closed or open, combined
 *       separately: a part that rules out arrays ({@code type: object}) and a part that closes objects without a
 *       type of its own together are closed.</li>
 *   <li><b>Own keywords.</b> {@code false} is closed. {@code true}, {@code {}} and a schema with no type and no
 *       closing keyword are open. A scalar {@code type} (alone or with {@code null}) and a {@code const} or
 *       {@code enum} are not an object (a {@code const}/{@code enum} that lists an object admits only that object,
 *       so it is closed). A schema whose type admits objects (a type naming {@code object}, or no type) is closed
 *       only by {@code additionalProperties: false} with no {@code patternProperties};
 *       {@code additionalProperties: true} and a schema-valued {@code additionalProperties} (a map) are open. Each
 *       {@code properties}, {@code patternProperties} and schema-valued {@code additionalProperties} value is a
 *       position.</li>
 *   <li><b>Arrays.</b> {@code items} and each {@code prefixItems} entry are positions. A schema whose type admits
 *       arrays (a type naming {@code array}, or no type) is open without {@code items}, its items being
 *       unconstrained; a tuple is closed only by {@code items: false} (or an {@code items} position). So an
 *       untyped schema with {@code additionalProperties: false} and no {@code items} is open: it admits any
 *       array.</li>
 *   <li><b>Conjunction.</b> A schema's own keywords, each {@code allOf} part, each union (rule 6) and its
 *       condition (rule 7) all apply to one value. Together they are closed when at least one part is closed, and
 *       not an object when at least one part is not an object; otherwise open (per facet: the narrowest part
 *       decides). One exception, a convention against authoring mistakes: a part that admits no object never
 *       excuses an open part whose {@code type} names {@code object}. That pairing is reported even when the
 *       schema is satisfiable ({@code type: [object, string]} beside {@code allOf: [{type: string}]} admits
 *       {@code "abc"}). A message names each part by where it sits below the holder ({@code allOf/1},
 *       {@code anyOf}, {@code oneOf}, {@code then|else}) or as the holder's own keywords. Every position nested in
 *       every part is still judged, and an open nested position is a violation even where the conjunction is
 *       closed.</li>
 *   <li><b>Disjunction.</b> An {@code anyOf} or {@code oneOf} is closed or not an object only when every member
 *       is; one open member leaves the union open (per facet: the widest member decides). Members are not
 *       positions of their own: a union is judged together with its holder's own keywords, as one more
 *       conjunctive part. A <i>constraint-only member</i> (only {@code required}, {@code dependentRequired},
 *       {@code minProperties}, {@code maxProperties}, {@code not} and annotations) has no type and no closing
 *       keyword, so it is open by itself: on a closed holder the holder closes it (the exactly-one-of idiom
 *       passes); on a holder with no object keywords nothing does, and the holder is reported. Keywords count by
 *       presence, never by value: {@code minProperties: 0} is a constraint like any other. A member,
 *       constraint-only or not, can never reopen a closed holder.</li>
 *   <li><b>Conditions.</b> {@code not} never closes anything, and {@code if} only selects {@code then} or
 *       {@code else}; neither describes an admitted value, so nothing inside them is a position (their keywords
 *       are still vetted, rule 8). {@code then} and {@code else} are read only beside an {@code if}: there they
 *       are alternatives (rule 6), a missing one admitting whatever the holder admits, so they close their holder
 *       only when both do. Without an {@code if} they have no effect (JSON Schema 2020-12 core 10.2.2.2 and
 *       10.2.2.3): they close nothing, and only the positions nested in them are judged. A
 *       {@code dependentSchemas} entry applies only when its member is present, so it never closes its holder;
 *       its nested positions are judged.</li>
 *   <li><b>Fail closed on unknown keywords and malformed input.</b> The checker understands an explicit list of
 *       keywords ({@link Vocabulary}): the structure it classifies, scalar, array and object constraints, and
 *       annotations ({@code description}, {@code example} and the rest, every {@code x-} extension included). Any
 *       other keyword ({@code unevaluatedProperties}, {@code propertyNames}, {@code contains}, {@code $schema},
 *       ...) is a violation naming it, so a construct the checker has no semantics for cannot slip past. These
 *       are violations too, each naming the problem: a {@code type} that is not a type name or a non-empty list
 *       of them, a type name that is not a string (an unquoted YAML {@code null} included) or not a JSON Schema
 *       type, an {@code enum} that is not a non-empty list, a schema that is neither a mapping nor a boolean, an
 *       empty union and a schema containing itself (a YAML alias cycle). A malformed {@code type} or
 *       {@code enum} is then ignored, so the schema is judged as if it were absent.</li>
 *   <li><b>Output.</b> One {@link Violation} per finding: the JSON Pointer of the position (or of the offending
 *       keyword) and the reason. {@link CheckClosedResponseObjects} prints one line each and exits non-zero when
 *       there is any.</li>
 * </ol>
 */
public final class ClosureChecker {

    /** The OAS 3.1 default JSON Schema dialect, the only one the checker reads. */
    public static final String DEFAULT_DIALECT = "https://spec.openapis.org/oas/3.1/dialect/base";

    private ClosureChecker() {}

    /**
     * Checks the bundle at {@code bundle}, a YAML or JSON OpenAPI document.
     *
     * @param bundle the path of the bundled contract
     * @return the positions checked and the violations found
     * @throws IOException when the file cannot be read
     */
    public static ClosureReport check(Path bundle) throws IOException {
        try (InputStream in = Files.newInputStream(bundle)) {
            return check(
                    new Load(LoadSettings.builder().setLabel(bundle.toString()).build()).loadFromInputStream(in));
        }
    }

    /**
     * Checks a loaded OpenAPI document: mappings, lists and scalars as a YAML loader produces them.
     *
     * @param document the loaded document
     * @return the positions checked and the violations found; a malformed document is reported, never thrown
     */
    public static ClosureReport check(Object document) {
        Findings findings = Findings.collecting();
        List<Node> positions = ResponseSchemas.in(document, findings);
        SchemaClassifier classifier = new SchemaClassifier(findings);
        positions.forEach(classifier::judge);
        return new ClosureReport(positions.size(), findings.violations());
    }
}
