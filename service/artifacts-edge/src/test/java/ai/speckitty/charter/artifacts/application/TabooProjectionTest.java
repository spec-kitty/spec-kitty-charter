package ai.speckitty.charter.artifacts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.artifacts.application.TabooProjection.Entry;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The taboo list (ADR-0076 decision 7): one entry per avoided word per term, with the term's URN and name, by word and
 * then URN; a word two terms avoid appears twice, and a term that avoids nothing gives no entry.
 */
class TabooProjectionTest {

    private static ActiveTerm term(String contextId, String id, List<String> avoid) {
        return new ActiveTerm(
                "term:" + contextId + "/" + id,
                contextId,
                id,
                "Name of " + id,
                "The definition of " + id + ".",
                "active",
                List.of(),
                List.of(),
                avoid,
                List.of(),
                new PackIdentifier("core"),
                PackLevel.BUILT_IN);
    }

    @Test
    void eachAvoidedWordOfEachTermIsOneEntryByWordAndThenUrn() {
        List<Entry> entries = TabooProjection.of(List.of(
                term("testing", "mission", List.of("feature", "epic")),
                term("core", "mission", List.of("feature")),
                term("core", "lane", List.of())));

        assertThat(entries)
                .containsExactly(
                        new Entry("epic", "term:testing/mission", "Name of mission"),
                        new Entry("feature", "term:core/mission", "Name of mission"),
                        new Entry("feature", "term:testing/mission", "Name of mission"));
    }

    @Test
    void aWordTwoTermsAvoidAppearsOncePerTerm() {
        List<Entry> entries = TabooProjection.of(
                List.of(term("core", "lane", List.of("track")), term("core", "branch", List.of("track"))));

        assertThat(entries)
                .extracting(Entry::word, Entry::term)
                .containsExactly(tuple("track", "term:core/branch"), tuple("track", "term:core/lane"));
    }

    @Test
    void termsThatAvoidNothingGiveNoEntry() {
        assertThat(TabooProjection.of(List.of(term("core", "lane", List.of())))).isEmpty();
        assertThat(TabooProjection.of(List.of())).isEmpty();
    }
}
