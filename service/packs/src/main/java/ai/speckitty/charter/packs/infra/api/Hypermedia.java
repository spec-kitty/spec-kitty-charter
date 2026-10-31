package ai.speckitty.charter.packs.infra.api;

import ai.speckitty.charter.api.model.Link;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * Builds the links of an answer (ADR-0019 D3): every href is the configured base path ({@code charter.api.base-path},
 * {@code /api/v1} by default) followed by a path whose segments are percent-encoded, so an identifier holding a space
 * or a {@code /} stays one segment. The server owns its URLs; no controller concatenates a path itself.
 */
@Component
final class Hypermedia {

    /** The methods a link may name; ADR-0026 D2 rules out {@code DELETE} and {@code PATCH}. */
    enum Method {
        GET,
        POST,
        PUT
    }

    /**
     * One link, in the shape of the contract's {@code Link}: an absent member is {@code null} and is not written.
     *
     * @param href      the absolute path, or an RFC 6570 template of one
     * @param templated {@code true} when {@code href} is a template; {@code null} otherwise
     * @param method    the method when it is not {@code GET}; {@code null} for {@code GET}
     */
    record LinkValue(
            String href,
            @Nullable Boolean templated,
            @Nullable String method) {}

    /** One whole simple RFC 6570 expression, a path variable such as {@code {packId}}. */
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[A-Za-z0-9_]+}");

    private final String basePath;

    /**
     * A builder for the base path the controllers are mapped under.
     *
     * @param basePath the server prefix, for example {@code /api/v1}; a trailing {@code /} is ignored
     */
    Hypermedia(@Value("${charter.api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /**
     * A {@code GET} link to the path made of {@code segments}; none is the root, {@code <base>/}.
     *
     * @param segments the path segments, unencoded
     * @return the link
     */
    LinkValue link(String... segments) {
        return link(Method.GET, segments);
    }

    /**
     * A link followed with {@code method} to the path made of {@code segments}.
     *
     * @param method   the method; {@code GET} is not written
     * @param segments the path segments, unencoded
     * @return the link
     */
    LinkValue link(Method method, String... segments) {
        return new LinkValue(href(segments), null, method == Method.GET ? null : method.name());
    }

    /**
     * A {@code GET} link to the path made of {@code segments} with a query: each parameter in the map's order, its
     * name and value percent-encoded as query components. An empty map writes no {@code ?}.
     *
     * @param query    the query parameters, in the order they are written; a {@link java.util.LinkedHashMap} keeps it
     * @param segments the path segments, unencoded
     * @return the link
     */
    LinkValue link(Map<String, String> query, String... segments) {
        String parameters = query.entrySet().stream()
                .map(parameter -> encodeQuery(parameter.getKey()) + "=" + encodeQuery(parameter.getValue()))
                .collect(Collectors.joining("&"));
        return new LinkValue(parameters.isEmpty() ? href(segments) : href(segments) + "?" + parameters, null, null);
    }

    /**
     * A templated {@code GET} link: the path made of {@code segments} followed by an RFC 6570 expression, for
     * example the query expression of {@code kind}. The expression is written as given, and so is a segment that is
     * one whole simple expression, a path variable such as {@code {packId}}; every other segment is percent-encoded.
     *
     * @param expression the RFC 6570 expression appended to the path, one braced expression
     * @param segments   the path segments, unencoded, or a path variable
     * @return the link, with {@code templated} true
     * @throws IllegalArgumentException when {@code expression} is not an RFC 6570 expression
     */
    LinkValue templated(String expression, String... segments) {
        if (!expression.startsWith("{") || !expression.endsWith("}")) {
            throw new IllegalArgumentException("not an RFC 6570 expression: " + expression);
        }
        String path = Arrays.stream(segments)
                .map(segment -> "/" + (PATH_VARIABLE.matcher(segment).matches() ? segment : encodeSegment(segment)))
                .collect(Collectors.joining());
        return new LinkValue((path.isEmpty() ? basePath + "/" : basePath + path) + expression, true, null);
    }

    /**
     * The {@code _links} of an answer: each link under its relation's wire key, in the order of {@link Relation}.
     *
     * @param links the links by relation; an {@link java.util.EnumMap} keeps the relation order
     * @return the links by wire key
     */
    static Map<String, LinkValue> links(Map<Relation, LinkValue> links) {
        Map<String, LinkValue> byWireKey = new LinkedHashMap<>();
        links.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> byWireKey.put(entry.getKey().wireKey(), entry.getValue()));
        return byWireKey;
    }

    /**
     * The {@code _links} of an answer as the contract's {@code Link} DTOs, keyed and ordered as {@link #links} keys
     * them; a member a link does not have stays {@code null}, and the JSON mapper leaves it out.
     *
     * @param links the links by relation
     * @return the wire links by wire key
     */
    static Map<String, Link> wire(Map<Relation, LinkValue> links) {
        Map<String, Link> wire = new LinkedHashMap<>();
        links(links)
                .forEach((key, value) -> wire.put(
                        key, new Link(value.href()).templated(value.templated()).method(value.method())));
        return wire;
    }

    /**
     * A query component percent-encoded so that it stays one name or one value: {@code &}, {@code =} and {@code +}
     * are encoded as well as what a URI never holds.
     */
    private static String encodeQuery(String component) {
        return UriUtils.encode(component, StandardCharsets.UTF_8);
    }

    private String href(String... segments) {
        if (segments.length == 0) {
            return basePath + "/";
        }
        return basePath
                + Arrays.stream(segments)
                        .map(segment -> "/" + encodeSegment(segment))
                        .collect(Collectors.joining());
    }

    private static String encodeSegment(String segment) {
        return UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8);
    }
}
