package ai.speckitty.charter.packs.application;

import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.refusal;
import static ai.speckitty.charter.packs.application.PackRegistrationServiceTest.ulid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.PackRegistrationService.Outcome;
import ai.speckitty.charter.packs.application.PackRegistrationService.Preconditions;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Packs;
import ai.speckitty.charter.packs.application.PackRegistrationServiceTest.Rig;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackSources;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A {@code term:<context-id>/<term-id>} reference over stored packs (#106; spec edge case "a see_also URN, and a
 * vocabulary edge target, that no held context resolves are each reported as a broken reference"): a pack holds a term
 * when it holds the term's context and the context holds the term, and a term's {@code see_also} is a reference like
 * any other (operator ruling 2026-11-14). Each case that does not resolve sits beside the one that does, its positive
 * control, read through the one holding answer ({@link PackCompositionResolver#holderOf}, which the references read
 * asks) and the pack-scoped walk's written links ({@link EdgeIndex#writtenLinks()}).
 */
@DisplayName("a term reference resolves exactly when a held context holds the term")
class TermReferencesTest {

    private static final String BASE_ID = ulid('C');
    private static final String DERIVED_ID = ulid('D');
    private static final String DEPENDS_ON_BASE = "depends_on:\n  - pack_id: " + BASE_ID + "\n    range: '>=1.0'\n";
    private static final PackIdentifier BASE = new PackIdentifier("base");
    private static final PackIdentifier DERIVED = new PackIdentifier("derived");

    /** A glossary context file holding the terms, each with the {@code see_also} entries given after a colon. */
    private static String glossary(String id, String... terms) {
        StringBuilder text = new StringBuilder("""
                schema_version: "1.0"
                id: %s
                name: %s
                description: d
                terms:
                """.formatted(id, id));
        for (String term : terms) {
            String[] parts = term.split(" ", -1);
            text.append("- id: ").append(parts[0]).append("\n  name: ").append(parts[0]);
            text.append("\n  definition: d\n  status: active\n");
            if (parts.length > 1) {
                text.append("  see_also:\n");
                Stream.of(parts)
                        .skip(1)
                        .forEach(entry -> text.append("  - '").append(entry).append("'\n"));
            }
        }
        return text.toString();
    }

    private static String vocabulary(String source, String... targets) {
        return "schema_version: '1.0'\ngenerated_at: STATIC\nnodes: []\nedges:\n"
                + Stream.of(targets)
                        .map(target -> "- source: " + source + "\n  target: " + target + "\n  relation: vocabulary\n")
                        .collect(Collectors.joining());
    }

    /**
     * The base pack: context g holds lane, context h's term mesh sees lane in its own pack and nope, which g does not
     * hold; directive A_ONE writes a vocabulary edge to a held term, to a term g does not hold, and to a term of a
     * context no pack holds.
     */
    private static Packs base() {
        return Packs.of(BASE_ID, "base", "1.0.0", "org")
                .directive("A_ONE", "A")
                .file("glossaries/g.glossary.yaml", glossary("g", "lane"))
                .file("glossaries/h.glossary.yaml", glossary("h", "mesh term:g/lane term:g/nope"))
                .file("refs.graph.yaml", vocabulary("directive:A_ONE", "term:g/lane", "term:g/nope", "term:zz/lane"));
    }

    private static Packs derived(String seeAlso) {
        return Packs.of(DERIVED_ID, "derived", "1.0.0", "project", DEPENDS_ON_BASE)
                .file("glossaries/k.glossary.yaml", glossary("k", "word " + seeAlso));
    }

    private static Rig rigWithBase(Path host) {
        Rig rig = new Rig(host);
        assertThat(rig.register("base", rig.stage(base()), Preconditions.none()).outcome())
                .as("a broken bare term reference is reported, never refused")
                .isEqualTo(Outcome.CREATED);
        return rig;
    }

    private static OfferingReadService.ComposedClosure composed(Rig rig) {
        ConsistentRead read = new ConsistentRead(new FileSystemPackSources(rig.locations), rig.generation);
        return new OfferingReadService(rig.repository, rig.repository, rig.repository, read).composedWithFacts();
    }

    private static List<EdgeIndex.Link> writtenLinks(OfferingReadService.ComposedClosure closure, String source) {
        Urn from = Urn.parse(source).orElseThrow();
        return EdgeIndex.of(closure.packs(), closure.facts()).writtenLinks().stream()
                .filter(link -> link.source().equals(from))
                .toList();
    }

    private static Optional<String> holder(
            OfferingReadService.ComposedClosure closure, PackIdentifier referring, String reference) {
        return PackCompositionResolver.holderOf(closure.packs(), referring, PackQualifiedReference.parse(reference))
                .map(pack -> pack.configured().id().value());
    }

    private static Urn urn(String text) {
        return Urn.parse(text).orElseThrow();
    }

    @Test
    @DisplayName("a vocabulary edge onto a held term is held; a term the context lacks, or of no held context, is not")
    void aVocabularyEdgeOntoATermIsHeldExactlyWhenAHeldContextHoldsTheTerm(@TempDir Path host) {
        OfferingReadService.ComposedClosure closure = composed(rigWithBase(host));

        assertThat(writtenLinks(closure, "directive:A_ONE"))
                .extracting(EdgeIndex.Link::relation, EdgeIndex.Link::target, EdgeIndex.Link::held)
                .containsExactlyInAnyOrder(
                        tuple(RelationType.VOCABULARY, urn("term:g/lane"), true),
                        tuple(RelationType.VOCABULARY, urn("term:g/nope"), false),
                        tuple(RelationType.VOCABULARY, urn("term:zz/lane"), false));
        assertThat(holder(closure, BASE, "term:g/lane"))
                .as("the control resolves")
                .contains("base");
        assertThat(holder(closure, BASE, "term:g/nope"))
                .as("g is held, lane is not")
                .isEmpty();
        assertThat(holder(closure, BASE, "term:zz/lane")).as("no pack holds zz").isEmpty();
        assertThat(holder(closure, BASE, "glossary_context:g"))
                .as("the context is an artifact the pack holds, as before")
                .contains("base");
    }

    @Test
    @DisplayName("a see_also onto a term its own pack holds resolves; one onto a term no context holds is broken")
    void aSeeAlsoResolvesInItsOwnPackAndAnUnresolvedOneIsBroken(@TempDir Path host) {
        OfferingReadService.ComposedClosure closure = composed(rigWithBase(host));

        assertThat(writtenLinks(closure, "glossary_context:h"))
                .extracting(EdgeIndex.Link::relation, EdgeIndex.Link::target, EdgeIndex.Link::held)
                .containsExactly(
                        tuple(RelationType.VOCABULARY, urn("term:g/lane"), true),
                        tuple(RelationType.VOCABULARY, urn("term:g/nope"), false));
        assertThat(closure.facts().get("base").references())
                .as("the compose's reference check reads each see_also entry")
                .contains("term:g/lane", "term:g/nope");
    }

    @Test
    @DisplayName("a pack-qualified see_also onto a term another pack holds resolves there")
    void aQualifiedSeeAlsoResolvesInTheNamedPack(@TempDir Path host) {
        Rig rig = rigWithBase(host);

        assertThat(rig.register("derived", rig.stage(derived("base::term:g/lane")), Preconditions.none())
                        .outcome())
                .isEqualTo(Outcome.CREATED);

        OfferingReadService.ComposedClosure closure = composed(rig);
        assertThat(writtenLinks(closure, "glossary_context:k"))
                .extracting(EdgeIndex.Link::target, EdgeIndex.Link::held)
                .containsExactly(tuple(urn("term:g/lane"), true));
        assertThat(holder(closure, DERIVED, "base::term:g/lane")).contains("base");
    }

    @Test
    @DisplayName("a bare see_also onto a term only another pack holds is refused as an unqualified reference")
    void aBareSeeAlsoOntoAnotherPacksTermIsRefusedUnqualified(@TempDir Path host) {
        Rig rig = rigWithBase(host);

        CharterRefusal refused =
                refusal(() -> rig.register("derived", rig.stage(derived("term:g/lane")), Preconditions.none()));

        assertThat(refused.code()).isEqualTo(RefusalCode.PACK_REFERENCE_UNQUALIFIED);
        assertThat(refused.getMessage()).contains("term:g/lane");
        assertThat(rig.register("derived", rig.stage(derived("base::term:g/lane")), Preconditions.none())
                        .outcome())
                .as("the control: the same entry, pack-qualified")
                .isEqualTo(Outcome.CREATED);
    }

    @Test
    @DisplayName("a qualified reference to a term the named pack does not hold is refused, see_also or edge alike")
    void aQualifiedReferenceToAMissingTermIsRefusedArtifactMissing(@TempDir Path host) {
        Rig rig = rigWithBase(host);
        Packs edge = Packs.of(DERIVED_ID, "derived", "1.0.0", "project", DEPENDS_ON_BASE)
                .directive("D_ONE", "Own")
                .file("refs.graph.yaml", vocabulary("directive:D_ONE", "base::term:g/nope"));

        CharterRefusal bySeeAlso =
                refusal(() -> rig.register("derived", rig.stage(derived("base::term:g/nope")), Preconditions.none()));
        CharterRefusal byEdge = refusal(() -> rig.register("derived", rig.stage(edge), Preconditions.none()));
        CharterRefusal noContext =
                refusal(() -> rig.register("derived", rig.stage(derived("base::term:zz/lane")), Preconditions.none()));

        assertThat(List.of(bySeeAlso.code(), byEdge.code(), noContext.code()))
                .containsOnly(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
        assertThat(bySeeAlso.getMessage()).contains("does not hold `term:g/nope`");
        assertThat(noContext.getMessage()).contains("does not hold `term:zz/lane`");
        assertThat(rig.register(
                                "derived",
                                rig.stage(edge.with(
                                        "refs.graph.yaml", vocabulary("directive:D_ONE", "base::term:g/lane"))),
                                Preconditions.none())
                        .outcome())
                .as("the control: a qualified edge onto the held term")
                .isEqualTo(Outcome.CREATED);
    }
}
