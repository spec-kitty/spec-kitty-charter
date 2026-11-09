package ai.speckitty.charter.deployable.bdd;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * The link checks the scenario hook runs on every 2xx answer with a JSON body (R-20), statically, without a request:
 *
 * <ul>
 *   <li><b>Self.</b> When the contract has a {@code GET} for the request path, {@code _links.self.href} is the server
 *       prefix plus the request path, plus the query for a {@code GET} with one. Otherwise a 201 with a
 *       {@code Location} names its resource there and its {@code self} is that path; any other answer (the
 *       relationships {@code PUT}) has no {@code self}.
 *   <li><b>Resolution.</b> Every link, at any depth, has an {@code href} under the server prefix that, with its query
 *       and a trailing form-style query expression ({@code {?kind}}) cut off, matches a path template of the contract
 *       that defines the link's {@code method} ({@code GET} when absent). A path variable expression that is a whole
 *       segment ({@code {packId}}) matches a variable segment of the template. Any other RFC 6570 form is reported,
 *       not guessed at. {@code templated} is {@code true} exactly when the {@code href} holds an expression. The
 *       operation must be built: one that carries {@code x-reserved} answers 501 until it is built (ADR-0020 D8), and
 *       no link points to an unbuilt operation.
 *   <li><b>Location.</b> On a 201 with a {@code Location}, its path is {@code _links.self.href}.
 *   <li><b>Relations.</b> Every key of every {@code _links} of the body is a relation of the hypermedia guide's table.
 * </ul>
 */
final class LinkChecks {

    private static final String BASE = "/api/v1";
    private static final String LINKS = "_links";
    private static final String HREF = "href";
    private static final int CREATED = 201;
    private static final Pattern FORM_QUERY = Pattern.compile("\\{\\?[A-Za-z0-9_,]+}$");
    private static final Pattern SEGMENT_VARIABLE = Pattern.compile("(?<=/)\\{[A-Za-z0-9_]+}(?=/|$)");

    /**
     * One answer to check.
     *
     * @param method the lower-case request method
     * @param path the request path below the server prefix, without its query
     * @param query the raw query of the request, or {@code null} when it has none
     * @param status the response status
     * @param location the {@code Location} header, or {@code null}
     * @param body the JSON body
     */
    record Answer(
            String method,
            String path,
            @Nullable String query,
            int status,
            @Nullable String location,
            JsonNode body) {}

    private final ContractConformance contract;
    private final Set<String> relations;

    /**
     * The checks against a contract and a relation table.
     *
     * @param contract the contract whose path templates the links must resolve to
     * @param relations the relation keys of the hypermedia guide's table
     */
    LinkChecks(ContractConformance contract, Set<String> relations) {
        this.contract = contract;
        this.relations = Set.copyOf(relations);
    }

    /**
     * What breaks the link rules in one 2xx JSON answer.
     *
     * @param answer the answer
     * @return one sentence per violation; empty when the answer passes
     */
    List<String> violations(Answer answer) {
        List<String> violations = new ArrayList<>();
        String what = answer.method().toUpperCase(Locale.ROOT) + " " + answer.path() + " (" + answer.status() + ")";
        checkSelf(answer, what, violations);
        checkLocation(answer, what, violations);
        walk(answer.body(), "", what, violations);
        return violations;
    }

    /** The {@code self} an answer must carry, or {@code null} when it must carry none. */
    private @Nullable String expectedSelf(Answer answer) {
        if (contract.operationTemplate("get", answer.path()).isPresent()) {
            boolean withQuery = answer.method().equals("get") && answer.query() != null;
            return BASE + answer.path() + (withQuery ? "?" + answer.query() : "");
        }
        if (answer.status() == CREATED && answer.location() != null) {
            return URI.create(answer.location()).getRawPath();
        }
        return null;
    }

    private void checkSelf(Answer answer, String what, List<String> violations) {
        JsonNode self = answer.body().path(LINKS).path("self");
        String expected = expectedSelf(answer);
        if (expected == null) {
            if (!self.isMissingNode()) {
                violations.add(what + ": the request URI has no GET operation, so the answer has no self, but it links "
                        + self);
            }
        } else if (!expected.equals(href(self))) {
            violations.add(what + ": _links.self.href must be " + expected + ", not " + self.path(HREF));
        }
    }

    private void checkLocation(Answer answer, String what, List<String> violations) {
        if (answer.status() != CREATED || answer.location() == null) {
            return;
        }
        String path = URI.create(answer.location()).getRawPath();
        String self = href(answer.body().path(LINKS).path("self"));
        if (!path.equals(self)) {
            violations.add(what + ": the path of Location, " + path + ", must be _links.self.href, " + self);
        }
    }

    /** The {@code href} of a link, or {@code null} when it has no string one. */
    private static @Nullable String href(JsonNode link) {
        JsonNode href = link.path(HREF);
        return href.isString() ? href.asString() : null;
    }

    /** Checks every {@code _links} of {@code node} and below. */
    private void walk(JsonNode node, String at, String what, List<String> violations) {
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                walk(node.get(i), at + "/" + i, what, violations);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> member : node.properties()) {
            String memberAt = at + "/" + member.getKey();
            if (member.getKey().equals(LINKS)) {
                checkLinks(member.getValue(), memberAt, what, violations);
            } else {
                walk(member.getValue(), memberAt, what, violations);
            }
        }
    }

    private void checkLinks(JsonNode links, String at, String what, List<String> violations) {
        if (!links.isObject()) {
            violations.add(what + " " + at + ": _links is not a JSON object but " + links.getNodeType());
            return;
        }
        for (Map.Entry<String, JsonNode> link : links.properties()) {
            String linkAt = what + " " + at + "/" + link.getKey();
            if (!relations.contains(link.getKey())) {
                violations.add(linkAt + ": the relation " + link.getKey()
                        + " is not in the relation table of docs/development/hypermedia-hateoas.md");
            }
            checkTarget(link.getValue(), linkAt, violations);
        }
    }

    /** The link's href resolves, statically, to an operation of the contract that defines the link's method. */
    private void checkTarget(JsonNode link, String at, List<String> violations) {
        String href = Objects.requireNonNullElse(href(link), "");
        if (!href.startsWith(BASE + "/")) {
            violations.add(at + ": the href " + href + " is not under " + BASE + "/");
            return;
        }
        String rest = href.substring(BASE.length());
        String template = FORM_QUERY.matcher(rest).replaceFirst("");
        boolean expressions =
                !template.equals(rest) || SEGMENT_VARIABLE.matcher(template).find();
        String unread = SEGMENT_VARIABLE.matcher(template).replaceAll("");
        if (unread.contains("{") || unread.contains("}")) {
            violations.add(at + ": the href " + href + " holds an RFC 6570 form the check does not read");
            return;
        }
        if (expressions != link.path("templated").asBoolean(false)) {
            violations.add(at + ": templated must be " + expressions + " for the href " + href);
        }
        String method = link.path("method").asString("GET");
        String path = Objects.requireNonNull(template.split("\\?", 2)[0]);
        Optional<ContractConformance.DeclaredOperation> target = contract.operation(method, path);
        if (target.isEmpty()) {
            violations.add(at + ": the href " + href + " names no " + method + " operation of the contract");
        } else if (target.get().reserved()) {
            violations.add(at + ": the href " + href + " names the reserved " + method + " "
                    + target.get().pathTemplate() + ", which answers 501 until it is built; no link points to an"
                    + " unbuilt operation (ADR-0019 D3, ADR-0020 D8)");
        }
    }
}
