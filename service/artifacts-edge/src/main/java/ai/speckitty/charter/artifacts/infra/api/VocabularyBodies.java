package ai.speckitty.charter.artifacts.infra.api;

import ai.speckitty.charter.api.model.Link;
import ai.speckitty.charter.api.model.TabooEntry;
import ai.speckitty.charter.api.model.TabooList;
import ai.speckitty.charter.api.model.VocabularyTerm;
import ai.speckitty.charter.api.model.VocabularyTermList;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts;
import ai.speckitty.charter.artifacts.application.TabooProjection;
import ai.speckitty.charter.artifacts.application.VocabularyReads;
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
 * The wire form of the vocabulary reads of an activation (#106; ADR-0076 decisions 5, 7 and 9). The links start at the
 * base path the controllers are mapped under.
 *
 * <ul>
 *   <li>A list ({@code VocabularyTermList}, {@code TabooList}) links {@code self} and {@code activation}.
 *   <li>A term links {@code effective-term}, its read under the activation, and {@code effective-glossary}, its
 *       context's read under the activation. A member the term does not have is left {@code null}, which the JSON
 *       omits ({@link EffectiveJson}).
 * </ul>
 */
@Component
public class VocabularyBodies {

    private static final String ACTIVATIONS = "activations";
    private static final String VOCABULARY = "vocabulary";
    private static final String GLOSSARY = ArtifactKind.GLOSSARY.token();

    private final String basePath;

    /**
     * A mapper whose links start at the base path the controllers are mapped under.
     *
     * @param basePath the server prefix, for example {@code /api/v1}; a trailing {@code /} is ignored
     */
    @Autowired
    public VocabularyBodies(@Value("${charter.api.base-path:/api/v1}") String basePath) {
        this.basePath = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
    }

    /**
     * The terms in force under an activation.
     *
     * @param read what the application ring read
     * @return the {@code VocabularyTermList}
     */
    public VocabularyTermList terms(VocabularyReads.VocabularyTermList read) {
        ActivationArtifacts.Scope scope = read.scope();
        String activation = scope.activationId();
        List<VocabularyTerm> terms =
                read.terms().stream().map(term -> term(activation, term)).toList();
        return new VocabularyTermList(
                activation, scope.revision(), scope.governanceFingerprint(), terms, listLinks(activation, "terms"));
    }

    /**
     * The taboo list of an activation.
     *
     * @param read what the application ring read
     * @return the {@code TabooList}
     */
    public TabooList taboos(VocabularyReads.TabooList read) {
        ActivationArtifacts.Scope scope = read.scope();
        String activation = scope.activationId();
        List<TabooEntry> entries =
                read.entries().stream().map(VocabularyBodies::entry).toList();
        return new TabooList(
                activation, scope.revision(), scope.governanceFingerprint(), entries, listLinks(activation, "taboos"));
    }

    private VocabularyTerm term(String activation, ai.speckitty.charter.artifacts.application.VocabularyTerm term) {
        String context = href(ACTIVATIONS, activation, "artifacts", GLOSSARY, term.contextId());
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("effective-term", new Link(context + segments("terms", term.id())));
        links.put("effective-glossary", new Link(context));
        VocabularyTerm wire = new VocabularyTerm(
                term.urn(),
                term.contextId(),
                term.id(),
                term.name(),
                term.definition(),
                VocabularyTerm.StatusEnum.fromValue(term.status()),
                term.packId().value(),
                VocabularyTerm.LevelEnum.fromValue(term.level().token()),
                links);
        wire.setExamples(present(term.examples()));
        wire.setAliases(present(term.aliases()));
        wire.setAvoid(present(term.avoid()));
        wire.setSeeAlso(present(term.seeAlso()));
        return wire;
    }

    private static TabooEntry entry(TabooProjection.Entry entry) {
        return new TabooEntry(entry.word(), entry.urn(), entry.termName());
    }

    private Map<String, Link> listLinks(String activation, String leaf) {
        Map<String, Link> links = new LinkedHashMap<>();
        links.put("self", new Link(href(ACTIVATIONS, activation, VOCABULARY, leaf)));
        links.put("activation", new Link(href(ACTIVATIONS, activation)));
        return links;
    }

    /** A list member that is omitted when empty. */
    private static @Nullable List<String> present(List<String> values) {
        return values.isEmpty() ? null : values;
    }

    private String href(String... segments) {
        return basePath + segments(segments);
    }

    /** The segments, each percent-encoded and led by {@code /}. */
    private static String segments(String... segments) {
        return Arrays.stream(segments)
                .map(segment -> "/" + UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining());
    }
}
