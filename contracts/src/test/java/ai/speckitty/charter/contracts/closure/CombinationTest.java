package ai.speckitty.charter.contracts.closure;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.contracts.closure.Closure.Admission;
import ai.speckitty.charter.contracts.closure.Closure.Facet;
import ai.speckitty.charter.contracts.closure.Closure.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The two ways parts combine: a conjunction ({@code allOf}) and a disjunction ({@code anyOf}/{@code oneOf}). */
class CombinationTest {

    private static final JsonPointer HOLDER = JsonPointer.ROOT.child("holder");
    private static final Closure CLOSED = Closure.closedObject(HOLDER.child("a"), "closed");
    private static final Closure SCALAR = Closure.notAnObject(HOLDER.child("b"), "a string");
    private static final Closure UNTYPED = Closure.open(HOLDER.child("c"), "untyped", false);
    private static final Closure OPEN_OBJECT =
            new Closure(HOLDER.child("d"), Facet.open("an open object", true), Facet.none("no array"));
    private static final Closure CLOSED_UNTYPED =
            new Closure(HOLDER.child("e"), Facet.closed("closed"), Facet.open("any array", false));

    @Test
    void aConjunctionIsClosedByAnyClosedPart() {
        assertThat(Conjunction.of(HOLDER, List.of(UNTYPED, OPEN_OBJECT, CLOSED)).kind())
                .isEqualTo(Kind.CLOSED);
    }

    @Test
    void aConjunctionJudgesObjectsAndArraysSeparately() {
        Closure typedObjectBesideUntypedClosed = Conjunction.of(HOLDER, List.of(OPEN_OBJECT, CLOSED_UNTYPED));
        assertThat(typedObjectBesideUntypedClosed.objects().admission()).isEqualTo(Admission.CLOSED);
        assertThat(typedObjectBesideUntypedClosed.arrays().admission()).isEqualTo(Admission.NONE);
        assertThat(typedObjectBesideUntypedClosed.kind()).isEqualTo(Kind.CLOSED);
    }

    @Test
    void aConjunctionWithAScalarPartIsNotAnObjectUnlessAnOpenObjectPartContradictsIt() {
        assertThat(Conjunction.of(HOLDER, List.of(UNTYPED, SCALAR)).kind()).isEqualTo(Kind.NOT_AN_OBJECT);
        Closure contradiction = Conjunction.of(HOLDER, List.of(SCALAR, OPEN_OBJECT));
        assertThat(contradiction.kind()).isEqualTo(Kind.OPEN);
        assertThat(contradiction.reason())
                .isEqualTo("an open object (an open object; from d) sits beside a part that admits"
                        + " no object (a string; from b): this pairing is refused by convention, as a likely authoring mistake,"
                        + " even where a value satisfies both parts");
    }

    @Test
    void aConjunctionOfOpenPartsIsOpenAndNamesEachPart() {
        Closure open = Conjunction.of(HOLDER, List.of(UNTYPED, UNTYPED));
        assertThat(open.at()).isEqualTo(HOLDER);
        assertThat(open.reason()).isEqualTo("no part closes it: c: untyped; c: untyped");
    }

    @Test
    void aPartAtTheHolderItselfIsDescribedByItsReasonAlone() {
        Closure own = Closure.open(HOLDER, "it has no type and no closing keyword", false);
        assertThat(Conjunction.of(HOLDER, List.of(own, UNTYPED)).reason())
                .isEqualTo("no part closes it: it has no type and no closing keyword; c: untyped");
    }

    @Test
    void aDisjunctionIsOpenWhenAnyAlternativeIsOpen() {
        Closure open = Disjunction.of(HOLDER, List.of(CLOSED, SCALAR, UNTYPED));
        assertThat(open.kind()).isEqualTo(Kind.OPEN);
        assertThat(open.reason()).isEqualTo("its alternative c is open: untyped");
    }

    @Test
    void aDisjunctionPassesOnlyWhenEveryAlternativePasses() {
        assertThat(Disjunction.of(HOLDER, List.of(CLOSED, SCALAR)).kind()).isEqualTo(Kind.CLOSED);
        assertThat(Disjunction.of(HOLDER, List.of(SCALAR, SCALAR)).kind()).isEqualTo(Kind.NOT_AN_OBJECT);
        assertThat(Disjunction.of(HOLDER, List.of()).kind()).isEqualTo(Kind.OPEN);
    }

    @Test
    void onlyAnOpenFacetFails() {
        assertThat(List.of(CLOSED, SCALAR, UNTYPED, CLOSED_UNTYPED))
                .extracting(Closure::passes)
                .containsExactly(true, true, false, false);
    }
}
