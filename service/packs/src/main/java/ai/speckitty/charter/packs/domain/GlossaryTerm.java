package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One term of a {@link GlossaryContext} (ADR-0049 decision 3): an item of its context, not an artifact, addressed
 * {@code term:<context-id>/<term-id>}. The lists keep their authored order; an empty list stands for an absent one, as
 * ADR-0036 decision 2 reads them alike.
 *
 * @param id the term's id, unique within its context
 * @param name the canonical surface form
 * @param definition what the term means
 * @param examples example uses
 * @param aliases accepted alternative forms
 * @param avoid forms not to use: the one source of banned vocabulary (ADR-0049 decisions 5 and 15)
 * @param status the term's status
 * @param seeAlso related terms, each a reference to a term: bare ({@code term:<context-id>/<term-id>}), a term of the
 *     term's own pack, or pack-qualified ({@code <pack>::term:<context-id>/<term-id>}), a term another pack holds. A
 *     reference like any other (operator ruling 2026-11-14): its context derives one {@code vocabulary} relationship
 *     to each ({@link GlossaryContext#derivedRelationships()}), so the compose judges it by the one reference rule
 */
public record GlossaryTerm(
        TermIdentifier id,
        String name,
        String definition,
        List<String> examples,
        List<String> aliases,
        List<String> avoid,
        TermStatus status,
        List<PackQualifiedReference> seeAlso) {

    private static final ModularRuleset<String> NAME_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A term needs a name that is not blank.", text -> !text.isBlank()));

    private static final ModularRuleset<String> DEFINITION_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of("A term needs a definition that is not blank.", text -> !text.isBlank()));

    private static final ModularRuleset<List<PackQualifiedReference>> SEE_ALSO_RULES =
            ModularRuleset.<List<PackQualifiedReference>>emptyRules()
                    .must(Constraints.of(
                            "A term's see_also names terms only: each entry is a term:<context-id>/<term-id> URN,"
                                    + " bare or pack-qualified.",
                            references -> references.stream().allMatch(GlossaryTerm::namesATerm)));

    /**
     * Checks the fields and copies the lists.
     *
     * @param id the id
     * @param name the name
     * @param definition the definition
     * @param examples the examples
     * @param aliases the aliases
     * @param avoid the forms to avoid
     * @param status the status
     * @param seeAlso the related terms
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the name or the definition is
     *     blank, or a {@code see_also} entry does not name a term
     */
    public GlossaryTerm {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(status, "status");
        examples = List.copyOf(examples);
        aliases = List.copyOf(aliases);
        avoid = List.copyOf(avoid);
        seeAlso = List.copyOf(seeAlso);
        NAME_RULES.guard(name);
        DEFINITION_RULES.guard(definition);
        SEE_ALSO_RULES.guard(seeAlso);
    }

    /**
     * The term's address in its context.
     *
     * @param context the context that holds the term
     * @return {@code term:<context-id>/<term-id>}
     */
    public Urn urn(GlossaryContextIdentifier context) {
        return termUrn(context, id);
    }

    /**
     * The address of a term in a context.
     *
     * @param context the context's id
     * @param term the term's id
     * @return {@code term:<context-id>/<term-id>}
     */
    public static Urn termUrn(GlossaryContextIdentifier context, TermIdentifier term) {
        return new Urn(NodeKind.TERM, context.value() + "/" + term.value());
    }

    /** Whether a reference's URN part is a term's address, {@code term:<context-id>/<term-id>}. */
    private static boolean namesATerm(PackQualifiedReference reference) {
        return reference.asUrn().flatMap(GlossaryTerm::contextOf).isPresent();
    }

    /**
     * The glossary context a term's address names: a term is an item of its context, so it goes when the context goes,
     * as an action node goes with its mission type.
     *
     * @param term a URN of any kind
     * @return {@code glossary_context:<context-id>} for {@code term:<context-id>/<term-id>}; empty for a URN of another
     *     kind or one whose id is not two segments
     */
    public static Optional<Urn> contextOf(Urn term) {
        Objects.requireNonNull(term, "term");
        int slash = term.id().indexOf('/');
        if (term.kind() != NodeKind.TERM || slash <= 0 || slash == term.id().length() - 1) {
            return Optional.empty();
        }
        return Optional.of(new Urn(NodeKind.GLOSSARY_CONTEXT, term.id().substring(0, slash)));
    }
}
