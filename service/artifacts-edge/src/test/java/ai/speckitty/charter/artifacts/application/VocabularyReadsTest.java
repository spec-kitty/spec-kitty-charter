package ai.speckitty.charter.artifacts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts.GlossaryWinner;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Outcome;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Referrer;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.SkillWinner;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Winner;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Winners;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The vocabulary reads of an activation (#106, WP06; ADR-0076 decisions 1 to 3 and 5 to 7): each read asks the
 * activations port once for the contexts in force under its own vocabulary tag, then the packs port once for their
 * winning copies; the terms are the winning copies' terms, whole, whatever their status, by context id and term id;
 * and the projections are of those terms.
 *
 * <p>The packs: core (built-in) holds spec-kitty-core with lane and mission, and testing with mission and flake; proj
 * (project) holds its own copy of spec-kitty-core, with mission alone.
 */
class VocabularyReadsTest {

    private static final String FINGERPRINT = "sha256:" + "f".repeat(64);
    private static final PackIdentifier CORE = new PackIdentifier("core");
    private static final PackIdentifier PROJ = new PackIdentifier("proj");
    private static final Scope SCOPE = new Scope("a-1", "sha256:rev", "sha256:closure", FINGERPRINT);
    private static final String ETAG = "sha256:" + "e".repeat(64);
    private static final Held KITTY_BUILT_IN = new Held("spec-kitty-core", CORE, PackLevel.BUILT_IN, "open");
    private static final Held KITTY_PROJECT = new Held("spec-kitty-core", PROJ, PackLevel.PROJECT, "open");
    private static final Held TESTING = new Held("testing", CORE, PackLevel.BUILT_IN, "open");

    private static final GlossaryView.Term LANE = term(
            "spec-kitty-core", "lane", "Lane", "active", List.of("track"), List.of("term:spec-kitty-core/mission"));
    private static final GlossaryView.Term KITTY_MISSION =
            term("spec-kitty-core", "mission", "Mission", "deprecated", List.of("feature", "epic"), List.of());
    private static final GlossaryView.Term PROJECT_MISSION =
            term("spec-kitty-core", "mission", "Mission", "active", List.of("feature"), List.of());
    private static final GlossaryView.Term TESTING_MISSION =
            term("testing", "mission", "Mission", "draft", List.of("feature"), List.of("qa::term:checks/run"));
    private static final GlossaryView.Term FLAKE =
            term("testing", "flake", "Flaky test", "active", List.of(), List.of());

    private static final Map<String, GlossaryView> CORE_COPIES = Map.of(
            "spec-kitty-core", context("spec-kitty-core", CORE, LANE, KITTY_MISSION),
            "testing", context("testing", CORE, TESTING_MISSION, FLAKE));
    private static final Map<String, GlossaryView> PROJ_COPIES =
            Map.of("spec-kitty-core", context("spec-kitty-core", PROJ, PROJECT_MISSION));

    private final List<String> asked = new ArrayList<>();

    private static GlossaryView.Term term(
            String contextId, String id, String name, String status, List<String> avoid, List<String> seeAlso) {
        return new GlossaryView.Term(
                id,
                name,
                "The definition of " + id + ".",
                status,
                List.of("An example of " + id + "."),
                List.of(),
                avoid,
                seeAlso,
                "term:" + contextId + "/" + id);
    }

    private static GlossaryView context(String id, PackIdentifier pack, GlossaryView.Term... terms) {
        return new GlossaryView(
                id,
                pack,
                "1.0",
                "Name of " + id,
                "Where " + id + " applies.",
                List.of(),
                List.of("src/" + id + "/**"),
                List.of(),
                List.of(),
                List.of(terms));
    }

    private VocabularyReads reads(Outcome<ContextsInForce> answer) {
        return new VocabularyReads(new Activation(answer), new Packs());
    }

    private VocabularyReads inForce(Held... contexts) {
        return reads(new Outcome.Found<>(new ContextsInForce(SCOPE, List.of(contexts)), ETAG));
    }

    private static <T> T found(Outcome<T> outcome) {
        assertThat(outcome).isInstanceOf(Outcome.Found.class);
        Outcome.Found<T> answered = (Outcome.Found<T>) outcome;
        assertThat(answered.etag()).isEqualTo(ETAG);
        return answered.value();
    }

    private static List<String> urns(List<ActiveTerm> terms) {
        return terms.stream().map(ActiveTerm::urn).toList();
    }

    // ---- what is in force

    @Test
    void theProjectCopyOverridesTheBuiltInCopyWholeSoATermOnlyTheBuiltInCopyHoldsIsNotInForce() {
        VocabularyReads.ActiveTermList list =
                found(inForce(KITTY_PROJECT, TESTING).terms("a-1", null));

        assertThat(urns(list.terms()))
                .containsExactly("term:spec-kitty-core/mission", "term:testing/flake", "term:testing/mission")
                .doesNotContain("term:spec-kitty-core/lane");
        assertThat(list.terms().getFirst())
                .as("the project copy's term, not the built-in copy's: nothing is merged per term")
                .isEqualTo(new ActiveTerm(
                        "term:spec-kitty-core/mission",
                        "spec-kitty-core",
                        "mission",
                        "Mission",
                        "The definition of mission.",
                        "active",
                        List.of("An example of mission."),
                        List.of(),
                        List.of("feature"),
                        List.of(),
                        PROJ,
                        PackLevel.PROJECT));
        assertThat(list.scope()).isEqualTo(SCOPE);
    }

    @Test
    void withoutTheProjectCopyTheBuiltInCopyIsInForceWithLane() {
        VocabularyReads.ActiveTermList list =
                found(inForce(KITTY_BUILT_IN, TESTING).terms("a-1", null));

        assertThat(urns(list.terms()))
                .containsExactly(
                        "term:spec-kitty-core/lane",
                        "term:spec-kitty-core/mission",
                        "term:testing/flake",
                        "term:testing/mission");
        assertThat(list.terms().getFirst().packId()).isEqualTo(CORE);
        assertThat(list.terms().getFirst().level()).isEqualTo(PackLevel.BUILT_IN);
        assertThat(list.terms().getFirst().seeAlso())
                .as("see_also as written")
                .containsExactly("term:spec-kitty-core/mission");
    }

    @Test
    void aNameTwoContextsDefineIsTwoTermsWithTwoUrns() {
        List<ActiveTerm> missions = found(inForce(KITTY_PROJECT, TESTING).terms("a-1", null)).terms().stream()
                .filter(term -> term.name().equals("Mission"))
                .toList();

        assertThat(missions)
                .extracting(ActiveTerm::urn)
                .containsExactly("term:spec-kitty-core/mission", "term:testing/mission");
        assertThat(missions).extracting(ActiveTerm::contextId).containsExactly("spec-kitty-core", "testing");
    }

    @Test
    void everyTermIsListedWhateverItsStatusAndCarriesIt() {
        List<ActiveTerm> terms =
                found(inForce(KITTY_BUILT_IN, TESTING).terms("a-1", null)).terms();

        assertThat(terms)
                .extracting(ActiveTerm::termId, ActiveTerm::status)
                .containsExactly(
                        tuple("lane", "active"),
                        tuple("mission", "deprecated"),
                        tuple("flake", "active"),
                        tuple("mission", "draft"));
    }

    @Test
    void noContextInForceIsEmptyListsAndAnEmptyDocument() {
        VocabularyReads reads = inForce();

        assertThat(found(reads.terms("a-1", null)).terms()).isEmpty();
        assertThat(found(reads.taboos("a-1", null)).entries()).isEmpty();
        assertThat(found(reads.contextive("a-1", null)).document().contexts()).isEmpty();
    }

    // ---- the reads

    @ParameterizedTest
    @EnumSource(ContextsInForce.Vocabulary.class)
    void eachReadAsksTheActivationOnceUnderItsOwnTagThenEachWinningPackOnce(ContextsInForce.Vocabulary vocabulary) {
        VocabularyReads reads = inForce(KITTY_PROJECT, TESTING);

        Outcome<?> read = switch (vocabulary) {
            case TERMS -> reads.terms("a-1", "\"x\"");
            case CONTEXTIVE -> reads.contextive("a-1", "\"x\"");
            case TABOOS -> reads.taboos("a-1", "\"x\"");
        };

        assertThat(read).isInstanceOf(Outcome.Found.class);

        assertThat(asked)
                .containsExactly(
                        "in force a-1 " + vocabulary + " \"x\"",
                        "read spec-kitty-core@proj testing@core at " + FINGERPRINT);
    }

    @Test
    void aNotModifiedPassesThroughAndNoPackIsRead() {
        VocabularyReads reads = reads(new Outcome.NotModified<>(ETAG));

        assertThat(reads.terms("a-1", "\"" + ETAG + "\"")).isEqualTo(new Outcome.NotModified<>(ETAG));
        assertThat(reads.contextive("a-1", "*")).isEqualTo(new Outcome.NotModified<>(ETAG));
        assertThat(reads.taboos("a-1", "*")).isEqualTo(new Outcome.NotModified<>(ETAG));
        assertThat(asked).noneMatch(ask -> ask.startsWith("read"));
    }

    @Test
    void theActivationsRefusalIsTheAnswerAndNoPackIsRead() {
        CharterRefusal unknown = new CharterRefusal(RefusalCode.ACTIVATION_NOT_FOUND, "No activation a-9.");
        VocabularyReads reads = new VocabularyReads(new Activation(unknown), new Packs());

        assertThatThrownBy(() -> reads.terms("a-9", null)).isSameAs(unknown);
        assertThat(asked).containsExactly("in force a-9 TERMS null");
    }

    @Test
    void aWinnerItsPackNoLongerHoldsIsStaleInput() {
        Held ghost = new Held("ghost", CORE, PackLevel.BUILT_IN, "open");

        assertThatThrownBy(() -> inForce(TESTING, ghost).terms("a-1", null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void aWinningPackThatIsNoLongerConfiguredIsStaleInput() {
        Held gone = new Held("spec-kitty-core", new PackIdentifier("gone"), PackLevel.ORG, "open");

        assertThatThrownBy(() -> inForce(gone).taboos("a-1", null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    // ---- the projections of the read (SC-004)

    @Test
    void theContextiveDocumentHoldsExactlyTheActiveTerms() {
        VocabularyReads reads = inForce(KITTY_PROJECT, TESTING);
        List<ActiveTerm> terms = found(reads.terms("a-1", null)).terms();

        VocabularyReads.Contextive contextive = found(reads.contextive("a-1", null));

        assertThat(contextive.scope()).isEqualTo(SCOPE);
        assertThat(contextive.document().contexts())
                .extracting(ContextiveProjection.Context::name)
                .containsExactly("Name of spec-kitty-core", "Name of testing");
        assertThat(contextive.document().contexts().stream()
                        .flatMap(context -> context.terms().stream())
                        .map(term -> term.meta().urn()))
                .containsExactlyInAnyOrderElementsOf(urns(terms));
    }

    @Test
    void theTabooListIsExactlyTheFlattenedAvoidEntriesOfTheActiveTerms() {
        VocabularyReads reads = inForce(KITTY_BUILT_IN, TESTING);
        List<ActiveTerm> terms = found(reads.terms("a-1", null)).terms();

        VocabularyReads.TabooList taboos = found(reads.taboos("a-1", null));

        assertThat(taboos.scope()).isEqualTo(SCOPE);
        assertThat(taboos.entries().stream().map(entry -> entry.word() + " -> " + entry.term()))
                .containsExactlyInAnyOrderElementsOf(terms.stream()
                        .flatMap(term -> term.avoid().stream().map(word -> word + " -> " + term.urn()))
                        .toList())
                .hasSize(4);
    }

    /**
     * The activations port: answers one outcome, or throws one refusal, to the contexts in force, and records each ask.
     */
    private final class Activation implements ActivationArtifacts {

        private final @Nullable Outcome<ContextsInForce> answer;
        private final @Nullable CharterRefusal refusal;

        Activation(Outcome<ContextsInForce> answer) {
            this.answer = answer;
            this.refusal = null;
        }

        Activation(CharterRefusal refusal) {
            this.answer = null;
            this.refusal = refusal;
        }

        @Override
        public Outcome<ContextsInForce> contextsInForce(
                String activationId, ContextsInForce.Vocabulary vocabulary, @Nullable String ifNoneMatch) {
            asked.add("in force " + activationId + " " + vocabulary + " " + ifNoneMatch);
            if (refusal != null) {
                throw refusal;
            }
            return Objects.requireNonNull(answer);
        }

        @Override
        public Outcome<Winners> winners(String activationId, ArtifactKind kind, @Nullable String ifNoneMatch) {
            throw new UnsupportedOperationException("no list is read here");
        }

        @Override
        public Outcome<GlossaryWinner> glossary(String activationId, String contextId, @Nullable String ifNoneMatch) {
            throw new UnsupportedOperationException("no context is read here");
        }

        @Override
        public Outcome<GlossaryWinner> glossaryTerm(
                String activationId, String contextId, String termId, @Nullable String ifNoneMatch) {
            throw new UnsupportedOperationException("no term is read here");
        }

        @Override
        public Outcome<Winner> asset(String activationId, String assetId, @Nullable String ifNoneMatch) {
            throw new UnsupportedOperationException("no asset is read here");
        }

        @Override
        public Outcome<Winner> assetPayload(String activationId, String assetId, @Nullable String ifNoneMatch) {
            throw new UnsupportedOperationException("no asset is read here");
        }

        @Override
        public Outcome<SkillWinner> skill(String activationId, String skillId, @Nullable String ifNoneMatch) {
            throw new UnsupportedOperationException("no skill is read here");
        }

        @Override
        public Referrer referrer(String activationId, String kindToken, String artifactId) {
            throw new UnsupportedOperationException("no reference is read here");
        }
    }

    /**
     * The packs port: the copy each winner's own pack holds, so the copy the activation names decides what is in force;
     * a pack it does not know is not found, and a winner its pack does not hold is absent.
     */
    private final class Packs implements VocabularyReads.WinningContexts {

        private final Map<PackIdentifier, Map<String, GlossaryView>> held =
                Map.of(CORE, CORE_COPIES, PROJ, PROJ_COPIES);

        @Override
        public Map<String, GlossaryView> read(ContextsInForce inForce) {
            asked.add("read "
                    + String.join(
                            " ",
                            inForce.contexts().stream()
                                    .map(winner ->
                                            winner.id() + "@" + winner.packId().value())
                                    .toList())
                    + " at " + inForce.scope().sourceFingerprint());
            Map<String, GlossaryView> copies = new HashMap<>();
            for (Held winner : inForce.contexts()) {
                Map<String, GlossaryView> pack = held.get(winner.packId());
                if (pack == null) {
                    throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + winner.packId() + " was not found.");
                }
                if (pack.containsKey(winner.id())) {
                    copies.put(winner.id(), pack.get(winner.id()));
                }
            }
            return copies;
        }
    }
}
