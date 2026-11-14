package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.model.Artifact;
import ai.speckitty.charter.api.model.GlossaryAppliesTo;
import ai.speckitty.charter.api.model.GlossaryContext;
import ai.speckitty.charter.api.model.GlossaryContextList;
import ai.speckitty.charter.api.model.GlossaryContextSummary;
import ai.speckitty.charter.api.model.GlossaryContextTerm;
import ai.speckitty.charter.api.model.GlossaryTerm;
import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.artifacts.application.GlossaryReads;
import ai.speckitty.charter.artifacts.application.GlossaryView;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * The wire form of a glossary context and its terms (#106): the one mapper of a context to the {@code GlossaryContext}
 * member, its list summary, a term's read and the links of the pack-scoped reads.
 *
 * <p>The context's read answers the {@code Artifact} envelope with {@code self}, {@code collection} (the pack's context
 * list) and {@code term}, templated by the term's id; a list item links {@code self}, the list links {@code self}, and a
 * term's read links {@code self} and {@code up} (its context). A list the file does not state (tags, an
 * {@code applies_to} list, a term's examples, aliases, forms to avoid or related terms) is {@code null} here and left
 * out on the wire, as the schema answers each only when it has an item; so is an {@code applies_to} with no list.
 */
@Component
public class GlossaryBodies {

    private static final String PACKS = "packs";
    private static final String ARTIFACTS = "artifacts";
    private static final String TERMS = "terms";
    private static final String GLOSSARY = ArtifactKind.GLOSSARY.token();
    private static final String SELF = "self";

    private final String basePath;

    /**
     * A mapper whose links start at the base path the controllers are mapped under.
     *
     * @param basePath the server prefix, for example {@code /api/v1}; a trailing {@code /} is ignored
     */
    @Autowired
    public GlossaryBodies(@Value("${charter.api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /**
     * The list of a pack's glossary contexts.
     *
     * @param pack the pack's identity, as the request named it
     * @param contexts the contexts, in the order they are answered
     * @return the {@code GlossaryContextList}, each item with its term count and linking its own read, the list linking
     *     itself
     */
    public GlossaryContextList list(String pack, List<GlossaryView> contexts) {
        List<GlossaryContextSummary> items = contexts.stream()
                .map(context -> {
                    GlossaryContextSummary summary = new GlossaryContextSummary(
                            context.id(),
                            context.name(),
                            context.description(),
                            context.terms().size(),
                            links(SELF, item(context.packId().value(), context.id())));
                    summary.setTags(present(context.tags()));
                    return summary;
                })
                .toList();
        return new GlossaryContextList(pack, items, links(SELF, collection(pack)));
    }

    /**
     * The envelope of one context's pack-scoped read.
     *
     * @param context the context
     * @return the {@code Artifact} with kind {@code glossary}, the context in member {@code glossary}, and the links
     *     {@code self}, {@code collection} and the templated {@code term}
     */
    public Artifact artifact(GlossaryView context) {
        String pack = context.packId().value();
        String self = item(pack, context.id());
        Map<String, Link> links = new LinkedHashMap<>();
        links.put(SELF, new Link(self));
        links.put("collection", new Link(collection(pack)));
        links.put("term", new Link(self + "/" + TERMS + "/{termId}").templated(true));
        Artifact envelope = new Artifact(GLOSSARY, context.id(), pack, links);
        envelope.setGlossary(context(context));
        return envelope;
    }

    /**
     * The {@code GlossaryContext} member: the context as its file declares it, field by field, every term included.
     *
     * @param context the context
     * @return the member
     */
    public GlossaryContext context(GlossaryView context) {
        GlossaryContext wire = new GlossaryContext(
                context.schemaVersion(),
                context.id(),
                context.name(),
                context.description(),
                context.terms().stream().map(GlossaryBodies::contextTerm).toList());
        wire.setTags(present(context.tags()));
        wire.setAppliesTo(appliesTo(context));
        return wire;
    }

    /**
     * The answer of a term's pack-scoped read.
     *
     * @param read the term, its context and its pack
     * @return the {@code GlossaryTerm}, with its URN, its context's id and the links {@code self} and {@code up}
     */
    public GlossaryTerm term(GlossaryReads.TermRead read) {
        GlossaryView.Term term = read.term();
        String context = item(read.packId().value(), read.contextId());
        Map<String, Link> links = new LinkedHashMap<>();
        links.put(SELF, new Link(context + href(TERMS, term.id())));
        links.put("up", new Link(context));
        GlossaryTerm wire = new GlossaryTerm(
                term.id(),
                term.name(),
                term.definition(),
                GlossaryTerm.StatusEnum.fromValue(term.status()),
                term.urn(),
                read.contextId(),
                links);
        wire.setExamples(present(term.examples()));
        wire.setAliases(present(term.aliases()));
        wire.setAvoid(present(term.avoid()));
        wire.setSeeAlso(present(term.seeAlso()));
        return wire;
    }

    private static GlossaryContextTerm contextTerm(GlossaryView.Term term) {
        GlossaryContextTerm wire = new GlossaryContextTerm(
                term.id(), term.name(), term.definition(), GlossaryContextTerm.StatusEnum.fromValue(term.status()));
        wire.setExamples(present(term.examples()));
        wire.setAliases(present(term.aliases()));
        wire.setAvoid(present(term.avoid()));
        wire.setSeeAlso(present(term.seeAlso()));
        return wire;
    }

    private static @Nullable GlossaryAppliesTo appliesTo(GlossaryView context) {
        if (context.paths().isEmpty()
                && context.languages().isEmpty()
                && context.stacks().isEmpty()) {
            return null;
        }
        GlossaryAppliesTo wire = new GlossaryAppliesTo();
        wire.setPaths(present(context.paths()));
        wire.setLanguages(present(context.languages()));
        wire.setStacks(present(context.stacks()));
        return wire;
    }

    /** A list the schema answers only when it has an item: {@code null}, left out on the wire, when it is empty. */
    private static @Nullable List<String> present(List<String> list) {
        return list.isEmpty() ? null : list;
    }

    private String collection(String pack) {
        return basePath + href(PACKS, pack, ARTIFACTS, GLOSSARY);
    }

    private String item(String pack, String id) {
        return basePath + href(PACKS, pack, ARTIFACTS, GLOSSARY, id);
    }

    private static Map<String, Link> links(String relation, String href) {
        Map<String, Link> links = new LinkedHashMap<>();
        links.put(relation, new Link(href));
        return links;
    }

    private static String href(String... segments) {
        return Arrays.stream(segments)
                .map(segment -> "/" + UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining());
    }
}
