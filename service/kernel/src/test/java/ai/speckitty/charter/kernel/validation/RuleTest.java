package ai.speckitty.charter.kernel.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rule vocabulary of ADR-0075 decision 1: a rule returns every reason it finds, composes with {@code on},
 * {@code forEach} and {@code when}, and a ruleset is itself a rule.
 */
@DisplayName("A rule")
class RuleTest {

    /** A tiny subject: a pack with an id and the files it holds. */
    record Shelf(String id, List<Book> books) {}

    /** One file of the subject. */
    record Book(String file, String title) {}

    private static final ReasonCode TITLE_BLANK = ReasonCode.of("title_blank");
    private static final ReasonCode SHELF_CROWDED = new ReasonCode("shelf_crowded", Severity.WARNING);

    // The detail and the location are functions of the subject: the dynamic description upstream never wired.
    private final Constraint<Book> titled = Constraints.<Book>of(
                    "a book has a title", book -> !book.title().isBlank())
            .coded(TITLE_BLANK)
            .locatedBy(book -> Location.in(book.file()).at("/title"))
            .detailedBy(book -> "The book '" + book.file() + "' has no title.");

    private final Shelf shelf =
            new Shelf("s1", List.of(new Book("a.md", "Alpha"), new Book("b.md", " "), new Book("c.md", "")));

    @Test
    void aFailingConstraintReportsItsCodeSeverityAndTheLocationAndDetailOfTheSubject() {
        Reason reason = titled.applyTo(new Book("b.md", " "));

        assertThat(reason.isFailing()).isTrue();
        assertThat(reason.rule()).isEqualTo("a book has a title");
        assertThat(reason.code()).isEqualTo(TITLE_BLANK);
        assertThat(reason.severity()).isEqualTo(Severity.VIOLATION);
        assertThat(reason.isFailing(Severity.WARNING)).isFalse();
        assertThat(reason.location()).isEqualTo(new Location("b.md", "/title"));
        assertThat(reason.detail()).isEqualTo("The book 'b.md' has no title.");
        // control: a held constraint reports its rule as its detail, at no location
        Reason held = titled.applyTo(new Book("a.md", "Alpha"));
        assertThat(held.isPassing()).isTrue();
        assertThat(held.detail()).isEqualTo("a book has a title");
        assertThat(held.location()).isEqualTo(Location.NOWHERE);
    }

    @Test
    void forEachAppliesTheRuleToEveryPartAndKeepsEveryFindingInOrder() {
        Rule<Shelf> everyBookTitled = titled.forEach(Shelf::books);

        Rationale rationale = everyBookTitled.check(shelf);

        assertThat(rationale.details()).hasSize(3);
        assertThat(rationale.violations())
                .extracting(Reason::location)
                .containsExactly(new Location("b.md", "/title"), new Location("c.md", "/title"));
        // control: a shelf of titled books passes
        assertThat(everyBookTitled
                        .check(new Shelf("s2", List.of(new Book("a.md", "Alpha"))))
                        .isPassing())
                .isTrue();
    }

    @Test
    void onNarrowsARuleToAPartOfTheSubject() {
        Rule<Shelf> idNotBlank = Constraints.notBlank().on(Shelf::id);

        assertThat(idNotBlank.check(new Shelf(" ", List.of())).violations())
                .extracting(Reason::detail)
                .containsExactly("mustn't be blank");
        // control: the same rule over a shelf with an id passes
        assertThat(idNotBlank.check(shelf).isPassing()).isTrue();
    }

    @Test
    void whenAppliesTheRuleOnlyWhileTheConditionHolds() {
        Rule<Book> markdownTitled = titled.when(book -> book.file().endsWith(".md"));

        assertThat(markdownTitled.apply(new Book("b.txt", " "))).isEmpty();
        // control: the same book as Markdown is judged, and fails
        assertThat(markdownTitled.apply(new Book("b.md", " ")))
                .singleElement()
                .satisfies(reason -> assertThat(reason.isFailing()).isTrue());
    }

    @Test
    void aRulesetIsARuleSoRulesetsNestAndFindingsKeepTheirSeverity() {
        Constraint<Shelf> fewBooks = Constraints.<Shelf>of(
                        "a shelf holds at most two books", s -> s.books().size() <= 2)
                .coded(SHELF_CROWDED);
        ModularRuleset<Book> bookRules = ModularRuleset.<Book>emptyRules().must(titled);
        ModularRuleset<Shelf> shelfRules = ModularRuleset.<Shelf>emptyRules()
                .must(Constraints.notBlank().on(Shelf::id))
                .must(fewBooks)
                .must(bookRules.forEach(Shelf::books));

        Rationale rationale = shelfRules.check(shelf);

        assertThat(rationale.violations()).extracting(Reason::code).containsExactly(TITLE_BLANK, TITLE_BLANK);
        assertThat(rationale.warnings()).extracting(Reason::code).containsExactly(SHELF_CROWDED);
        assertThat(rationale.isPassing()).isFalse();
        // control: with the untitled books gone, only the warning is left, and a warning does not fail the check
        Rationale crowdedOnly = shelfRules.check(
                new Shelf("s3", List.of(new Book("a.md", "A"), new Book("b.md", "B"), new Book("c.md", "C"))));
        assertThat(crowdedOnly.warnings()).hasSize(1);
        assertThat(crowdedOnly.violations()).isEmpty();
        assertThat(crowdedOnly.isPassing()).isTrue();
    }

    @Test
    void aGuardFailsOnAViolationButNotOnAWarning() {
        ModularRuleset<String> warnOnly = ModularRuleset.<String>emptyRules()
                .must(Constraints.<String>of("short", s -> s.length() <= 3)
                        .coded(new ReasonCode("long", Severity.WARNING)));

        assertThatCode(() -> warnOnly.guard("longer")).doesNotThrowAnyException();
        // control: the same rule as a violation breaks the guard
        assertThatThrownBy(() -> ModularRuleset.<String>emptyRules()
                        .must(Constraints.<String>of("short", s -> s.length() <= 3))
                        .guard("longer"))
                .isInstanceOf(ConstraintBreachException.class);
    }

    @Test
    void aWrappedRulesetStillStopsAtItsFirstViolationSoALaterRuleMayRelyOnAnEarlierOne() {
        // the second rule would throw on a null title; the guard must never reach it
        ModularRuleset<Book> titleRules = ModularRuleset.<Book>emptyRules()
                .must(Constraints.<Book>of("a book has a title", book -> book.title() != null))
                .must(Constraints.<Book>of(
                        "a title is short", book -> book.title().length() <= 40));
        Rule<Shelf> wrapped = titleRules
                .forEach(Shelf::books)
                .when(s -> !s.books().isEmpty())
                .on(java.util.function.Function.<Shelf>identity());
        Shelf untitled = new Shelf("s4", java.util.Arrays.asList(new Book("a.md", "A"), new Book("b.md", null)));

        assertThatThrownBy(() -> wrapped.guard(untitled))
                .isInstanceOf(ConstraintBreachException.class)
                .hasMessage("Constraint breached: a book has a title");
        // control: a shelf of titled books passes the same wrapped guard, and an empty one is not judged
        assertThatCode(() -> wrapped.guard(shelf)).doesNotThrowAnyException();
        assertThat(wrapped.apply(new Shelf("s5", List.of()))).isEmpty();
    }

    @Test
    void locatedAtPlacesEveryBreachOfASharedRuleByTheWholeSubject() {
        Rule<Shelf> idRule = Constraints.notBlank()
                .on(Shelf::id)
                .locatedAt(s ->
                        Location.in("shelves/" + s.books().size() + ".yaml").at("/id"));

        assertThat(idRule.check(new Shelf(" ", List.of())).violations())
                .extracting(Reason::location)
                .containsExactly(new Location("shelves/0.yaml", "/id"));
        assertThatThrownBy(() -> idRule.guard(new Shelf(" ", List.of())))
                .isInstanceOf(ConstraintBreachException.class)
                .extracting(e -> ((ConstraintBreachException) e).reason().location())
                .isEqualTo(new Location("shelves/0.yaml", "/id"));
        // control: a held reason is not placed
        assertThat(idRule.apply(shelf))
                .singleElement()
                .extracting(Reason::location)
                .isEqualTo(Location.NOWHERE);
    }
}
