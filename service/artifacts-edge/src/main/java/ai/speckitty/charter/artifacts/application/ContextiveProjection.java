package ai.speckitty.charter.artifacts.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The Contextive v1 definitions document of the vocabulary in force under an activation (ADR-0076 decision 6;
 * ADR-0049 decision 15): a pure function of the active terms and the winning copies of their contexts, with no I/O. It
 * answers an ordered tree of plain values, not YAML; the inbound adapter renders it.
 *
 * <p>The mapping:
 *
 * <ul>
 *   <li>one context per context in force, by id: its {@code name}, its {@code description} as the
 *       {@code domainVisionStatement}, and its {@code applies_to.paths} as {@code paths} when it has any;
 *   <li>under each, its terms by term id: {@code name}, {@code definition}, {@code examples} and {@code aliases} when
 *       the term has any, and {@code meta} with the term's URN and status.
 * </ul>
 *
 * <p>An optional member the context or the term does not have is empty, which the renderer omits.
 */
public final class ContextiveProjection {

    private ContextiveProjection() {}

    /**
     * The document of the active terms.
     *
     * @param terms the terms in force, each held by a context of {@code contexts}
     * @param contexts the winning copies of the contexts in force, by id
     * @return the document, its contexts by id and their terms by term id
     * @throws IllegalArgumentException when a term names a context {@code contexts} does not hold: a broken invariant
     *     of the read that composed them
     */
    public static Document of(List<ActiveTerm> terms, Map<String, GlossaryView> contexts) {
        Map<String, List<ActiveTerm>> byContext = terms.stream().collect(Collectors.groupingBy(ActiveTerm::contextId));
        for (Map.Entry<String, List<ActiveTerm>> held : byContext.entrySet()) {
            if (!contexts.containsKey(held.getKey())) {
                throw new IllegalArgumentException(
                        "The term " + held.getValue().getFirst().urn() + " names no context in force.");
            }
        }
        return new Document(contexts.values().stream()
                .sorted(Comparator.comparing(GlossaryView::id))
                .map(context -> contextOf(context, byContext.getOrDefault(context.id(), List.of())))
                .toList());
    }

    private static Context contextOf(GlossaryView context, List<ActiveTerm> terms) {
        return new Context(
                context.name(),
                context.description(),
                present(context.paths()),
                terms.stream()
                        .sorted(Comparator.comparing(ActiveTerm::termId))
                        .map(ContextiveProjection::termOf)
                        .toList());
    }

    private static Term termOf(ActiveTerm term) {
        return new Term(
                term.name(),
                term.definition(),
                present(term.examples()),
                present(term.aliases()),
                new Meta(term.urn(), term.status()));
    }

    /** A list member that is omitted when empty. */
    private static Optional<List<String>> present(List<String> values) {
        return values.isEmpty() ? Optional.empty() : Optional.of(values);
    }

    /**
     * The definitions document.
     *
     * @param contexts the contexts in force, by id
     */
    public record Document(List<Context> contexts) {

        /**
         * Checks and copies the field.
         *
         * @param contexts the contexts
         */
        public Document {
            contexts = List.copyOf(Objects.requireNonNull(contexts, "contexts"));
        }
    }

    /**
     * One context of the document.
     *
     * @param name the context's name
     * @param domainVisionStatement the context's description
     * @param paths the context's {@code applies_to.paths}; empty when it declares none
     * @param terms its terms in force, by term id
     */
    public record Context(String name, String domainVisionStatement, Optional<List<String>> paths, List<Term> terms) {

        /**
         * Checks and copies the fields.
         *
         * @param name the name
         * @param domainVisionStatement the domain vision statement
         * @param paths the paths
         * @param terms the terms
         */
        public Context {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(domainVisionStatement, "domainVisionStatement");
            paths = Objects.requireNonNull(paths, "paths").map(List::copyOf);
            terms = List.copyOf(Objects.requireNonNull(terms, "terms"));
        }
    }

    /**
     * One term of a context of the document.
     *
     * @param name the term's name
     * @param definition its definition
     * @param examples its examples; empty when it has none
     * @param aliases its aliases; empty when it has none
     * @param meta its URN and status
     */
    public record Term(
            String name,
            String definition,
            Optional<List<String>> examples,
            Optional<List<String>> aliases,
            Meta meta) {

        /**
         * Checks and copies the fields.
         *
         * @param name the name
         * @param definition the definition
         * @param examples the examples
         * @param aliases the aliases
         * @param meta the meta
         */
        public Term {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(definition, "definition");
            examples = Objects.requireNonNull(examples, "examples").map(List::copyOf);
            aliases = Objects.requireNonNull(aliases, "aliases").map(List::copyOf);
            Objects.requireNonNull(meta, "meta");
        }
    }

    /**
     * The {@code meta} of a term: what links it back to the charter.
     *
     * @param urn the term's URN
     * @param status the term's status token
     */
    public record Meta(String urn, String status) {

        /**
         * Checks the fields.
         *
         * @param urn the URN
         * @param status the status
         */
        public Meta {
            Objects.requireNonNull(urn, "urn");
            Objects.requireNonNull(status, "status");
        }
    }
}
