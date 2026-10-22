package ai.speckitty.charter.packs.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.DrgEdge;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.Relation;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.kernel.validation.ConstraintBreachException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Pack aggregate holds doctrine kinds; Tactic is a CharterArtifact")
class AggregateRetrofitTest {

    private static final PackIdentifier ID = new PackIdentifier("01PACK");
    private static final PackIdentifier OTHER = new PackIdentifier("02OTHER");
    private static final PackDescriptor DESCRIPTOR = new PackDescriptor(
            "01PACK", "1.0.0", Optional.empty(), "demo", Optional.empty(), Optional.empty(), List.of());

    private static Directive directive(String id, PackIdentifier owner) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(owner)
                .schemaVersion("1.0")
                .title("t")
                .intent("i")
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    private static Styleguide styleguide(String id, PackIdentifier owner) {
        return Styleguide.builder()
                .id(new StyleguideIdentifier(id))
                .packId(owner)
                .schemaVersion("1.0")
                .title("t")
                .scope(StyleguideScope.CODE)
                .principle("p")
                .build();
    }

    private static Paradigm paradigm(String id, PackIdentifier owner) {
        return Paradigm.builder()
                .id(new ParadigmIdentifier(id))
                .packId(owner)
                .schemaVersion("1.0")
                .name("n")
                .summary("s")
                .build();
    }

    private static Pack.Builder pack() {
        return Pack.builder().id(ID).descriptor(DESCRIPTOR);
    }

    @Test
    @DisplayName("a well-formed set of directives, styleguides and paradigms is accepted and exposed")
    void wellFormedSetIsAccepted() {
        Pack built = pack().directive(directive("D-1", ID))
                .directive(directive("D-2", ID))
                .styleguide(styleguide("s-1", ID))
                .paradigm(paradigm("p-1", ID))
                .build();

        assertThat(built.directives()).extracting(d -> d.id().value()).containsExactly("D-1", "D-2");
        assertThat(built.styleguides()).extracting(s -> s.id().value()).containsExactly("s-1");
        assertThat(built.paradigms()).extracting(p -> p.id().value()).containsExactly("p-1");
        assertThatThrownBy(() -> built.directives().add(directive("D-3", ID)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("a directive of another pack is rejected; one of this pack is accepted")
    void directiveMustBelongToPack() {
        assertThatCode(() -> pack().directive(directive("D-1", ID)).build()).doesNotThrowAnyException();
        assertThatThrownBy(() -> pack().directive(directive("D-1", OTHER)).build())
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a styleguide of another pack is rejected; one of this pack is accepted")
    void styleguideMustBelongToPack() {
        assertThatCode(() -> pack().styleguide(styleguide("s-1", ID)).build()).doesNotThrowAnyException();
        assertThatThrownBy(() -> pack().styleguide(styleguide("s-1", OTHER)).build())
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a paradigm of another pack is rejected; one of this pack is accepted")
    void paradigmMustBelongToPack() {
        assertThatCode(() -> pack().paradigm(paradigm("p-1", ID)).build()).doesNotThrowAnyException();
        assertThatThrownBy(() -> pack().paradigm(paradigm("p-1", OTHER)).build())
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("duplicate ids are rejected per kind; distinct ids and the same id across kinds are accepted")
    void duplicateIdsAreRejectedPerKind() {
        assertThatCode(() -> pack().directive(directive("D-1", ID))
                        .directive(directive("D-2", ID))
                        .styleguide(styleguide("same", ID))
                        .paradigm(paradigm("same", ID))
                        .build())
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> pack().directive(directive("D-1", ID))
                        .directive(directive("D-1", ID))
                        .build())
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> pack().styleguide(styleguide("s-1", ID))
                        .styleguide(styleguide("s-1", ID))
                        .build())
                .isInstanceOf(ConstraintBreachException.class);
        assertThatThrownBy(() -> pack().paradigm(paradigm("p-1", ID))
                        .paradigm(paradigm("p-1", ID))
                        .build())
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    @DisplayName("a Tactic is a CharterArtifact with kind-correct label, description, urn and edges")
    void tacticIsACharterArtifact() {
        Tactic tactic = Tactic.builder()
                .id(new TacticIdentifier("t-1"))
                .packId(ID)
                .schemaVersion("1.0")
                .name("Tactic name")
                .purpose("Why")
                .step(new TacticStep("s", Optional.empty(), List.of(), List.of()))
                .reference(new TacticReference("Dir", "directive", "D-1", "when it rains"))
                .reference(new TacticReference("Para", "paradigm", "p-1", ""))
                .build();

        CharterArtifact artifact = tactic;

        assertThat(artifact.kind()).isEqualTo(ArtifactKind.TACTIC);
        assertThat(artifact.localId()).isEqualTo("t-1");
        assertThat(artifact.label()).isEqualTo("Tactic name");
        assertThat(artifact.description()).contains("Why");
        assertThat(artifact.urn()).isEqualTo(new Urn(NodeKind.TACTIC, "t-1"));
        assertThat(artifact.edges())
                .containsExactly(
                        new DrgEdge(
                                artifact.urn(),
                                new Urn(NodeKind.DIRECTIVE, "D-1"),
                                Relation.SUGGESTS,
                                Optional.empty(),
                                Optional.of("when it rains"),
                                Optional.empty()),
                        new DrgEdge(
                                artifact.urn(),
                                new Urn(NodeKind.PARADIGM, "p-1"),
                                Relation.SUGGESTS,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty()));
    }

    @Test
    @DisplayName("a tactic without purpose or references has no description and no edges")
    void bareTacticHasNoDescriptionOrEdges() {
        Tactic tactic = Tactic.builder()
                .id(new TacticIdentifier("t-2"))
                .packId(ID)
                .schemaVersion("1.0")
                .name("n")
                .step(new TacticStep("s", Optional.empty(), List.of(), List.of()))
                .build();

        assertThat(tactic.description()).isEmpty();
        assertThat(tactic.edges()).isEmpty();
    }
}
