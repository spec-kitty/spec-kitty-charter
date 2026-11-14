package ai.speckitty.charter.artifacts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Facts;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.GlossaryWinner;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Held;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Outcome;
import ai.speckitty.charter.artifacts.application.ActivationArtifacts.Scope;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The glossary reads under an activation (#106, WP05): the list composes the activation's winners with one read of each
 * winner's pack against the activation's fingerprint; the context and the term are each one read of the activations
 * port, which the packs port maps without reading a pack; a {@code 304} passes through untouched; and a term carries
 * its context's facts.
 *
 * <p>The closure: proj (project) holds the winning copy of spec-kitty-core, with the term mission alone; core (built-in)
 * holds qa.
 */
class EffectiveGlossaryReadsTest {

    private static final String FINGERPRINT = "sha256:" + "f".repeat(64);
    private static final PackIdentifier CORE = new PackIdentifier("core");
    private static final PackIdentifier PROJ = new PackIdentifier("proj");
    private static final Scope SCOPE = new Scope("a-1", "sha256:rev", "sha256:closure", FINGERPRINT);
    private static final String ETAG = "sha256:" + "e".repeat(64);
    private static final Held KITTY_WINNER = new Held("spec-kitty-core", PROJ, PackLevel.PROJECT, "open");
    private static final Held QA_WINNER = new Held("qa", CORE, PackLevel.BUILT_IN, "open");
    private static final Facts FACTS =
            new Facts(KITTY_WINNER, "inactive", false, "not_selected", List.of(), Optional.empty());
    private static final GlossaryView.Term MISSION = new GlossaryView.Term(
            "mission",
            "mission",
            "A unit of planned work.",
            "active",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            "term:spec-kitty-core/mission");
    private static final GlossaryView KITTY = context("spec-kitty-core", PROJ, MISSION);
    private static final GlossaryView QA = context(
            "qa",
            CORE,
            new GlossaryView.Term(
                    "flaky-test",
                    "flaky test",
                    "A test that passes and fails on the same code.",
                    "active",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    "term:qa/flaky-test"));

    private final List<String> asked = new ArrayList<>();

    private static GlossaryView context(String id, PackIdentifier pack, GlossaryView.Term term) {
        return new GlossaryView(
                id,
                pack,
                "1.0",
                "Name of " + id,
                "Where " + id + " applies.",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(term));
    }

    private static <T> T found(Outcome<T> outcome) {
        assertThat(outcome).isInstanceOf(Outcome.Found.class);
        assertThat(((Outcome.Found<T>) outcome).etag()).isEqualTo(ETAG);
        return ((Outcome.Found<T>) outcome).value();
    }

    // ---- the list

    @Test
    void theListIsEachWinnerWithItsPacksCopyEachPackReadOnceAgainstTheActivationsFingerprint() {
        Activation activation = new Activation(new Outcome.Found<>(
                new ActivationArtifacts.Winners(
                        SCOPE, List.of(QA_WINNER, KITTY_WINNER, new Held("tools", CORE, PackLevel.BUILT_IN, "open"))),
                ETAG));
        Packs packs = new Packs(Map.of(
                CORE, List.of(QA, context("tools", CORE, MISSION)),
                PROJ, List.of(KITTY)));

        EffectiveGlossaryReads.Listed listed = found(new EffectiveGlossaryReads(activation, packs).list("a-1", null));

        assertThat(listed.scope()).isEqualTo(SCOPE);
        assertThat(listed.items())
                .extracting(
                        EffectiveGlossaryReads.Item::winner,
                        item -> item.context().id())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(QA_WINNER, "qa"),
                        org.assertj.core.groups.Tuple.tuple(KITTY_WINNER, "spec-kitty-core"),
                        org.assertj.core.groups.Tuple.tuple(
                                new Held("tools", CORE, PackLevel.BUILT_IN, "open"), "tools"));
        assertThat(asked)
                .containsExactly(
                        "winners a-1 glossary null", "list core at " + FINGERPRINT, "list proj at " + FINGERPRINT);
    }

    @Test
    void aWinnerItsPackNoLongerListsIsStaleInput() {
        Activation activation =
                new Activation(new Outcome.Found<>(new ActivationArtifacts.Winners(SCOPE, List.of(QA_WINNER)), ETAG));

        assertThatThrownBy(() ->
                        new EffectiveGlossaryReads(activation, new Packs(Map.of(CORE, List.of()))).list("a-1", null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void aWinnersPackThatIsNoLongerConfiguredIsStaleInput() {
        Activation activation =
                new Activation(new Outcome.Found<>(new ActivationArtifacts.Winners(SCOPE, List.of(QA_WINNER)), ETAG));

        assertThatThrownBy(() -> new EffectiveGlossaryReads(activation, new Packs(Map.of())).list("a-1", null))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    @Test
    void aNotModifiedListReadsNoPack() {
        Activation activation = new Activation(new Outcome.NotModified<>(ETAG));

        assertThat(new EffectiveGlossaryReads(activation, new Packs(Map.of())).list("a-1", "\"" + ETAG + "\""))
                .isEqualTo(new Outcome.NotModified<EffectiveGlossaryReads.Listed>(ETAG));
        assertThat(asked).containsExactly("winners a-1 glossary \"" + ETAG + "\"");
    }

    // ---- the context

    @Test
    void theContextMapsTheKernelArtifactTheActivationReadThroughThePacksPortAndReadsNoPack() {
        Activation activation =
                new Activation(new Outcome.Found<>(new GlossaryWinner(SCOPE, FACTS, entity("spec-kitty-core")), ETAG));

        EffectiveGlossaryReads.Read read = found(
                new EffectiveGlossaryReads(activation, new Packs(Map.of())).context("a-1", "spec-kitty-core", null));

        assertThat(read.scope()).isEqualTo(SCOPE);
        assertThat(read.facts()).isEqualTo(FACTS);
        assertThat(read.context()).isEqualTo(KITTY);
        assertThat(asked)
                .containsExactly(
                        "glossary a-1 spec-kitty-core null",
                        "view spec-kitty-core for The read of the glossary context spec-kitty-core under activation"
                                + " a-1");
    }

    @Test
    void aNotModifiedContextMapsNothing() {
        Activation activation = new Activation(new Outcome.NotModified<>(ETAG));

        assertThat(new EffectiveGlossaryReads(activation, new Packs(Map.of())).context("a-1", "spec-kitty-core", "*"))
                .isEqualTo(new Outcome.NotModified<EffectiveGlossaryReads.Read>(ETAG));
        assertThat(asked).containsExactly("glossary a-1 spec-kitty-core *");
    }

    // ---- the term

    @Test
    void theTermIsTheWinningContextsTermWithTheContextsFacts() {
        Activation activation =
                new Activation(new Outcome.Found<>(new GlossaryWinner(SCOPE, FACTS, entity("spec-kitty-core")), ETAG));

        EffectiveGlossaryReads.TermRead read = found(new EffectiveGlossaryReads(activation, new Packs(Map.of()))
                .term("a-1", "spec-kitty-core", "mission", null));

        assertThat(read.scope()).isEqualTo(SCOPE);
        assertThat(read.facts())
                .as("the context's facts: a term has none of its own")
                .isEqualTo(FACTS);
        assertThat(read.term()).isEqualTo(MISSION);
        assertThat(asked)
                .containsExactly(
                        "term a-1 spec-kitty-core mission null",
                        "view spec-kitty-core for The read of the term mission of the glossary context spec-kitty-core"
                                + " under activation a-1");
    }

    @Test
    void aNotModifiedTermMapsNothing() {
        Activation activation = new Activation(new Outcome.NotModified<>(ETAG));

        assertThat(new EffectiveGlossaryReads(activation, new Packs(Map.of()))
                        .term("a-1", "spec-kitty-core", "mission", "*"))
                .isEqualTo(new Outcome.NotModified<EffectiveGlossaryReads.TermRead>(ETAG));
        assertThat(asked).containsExactly("term a-1 spec-kitty-core mission *");
    }

    @Test
    void aTermTheWinningContextDoesNotHoldAfterTheActivationJudgedItHeldIsAFault() {
        Activation activation =
                new Activation(new Outcome.Found<>(new GlossaryWinner(SCOPE, FACTS, entity("spec-kitty-core")), ETAG));

        assertThatThrownBy(() -> new EffectiveGlossaryReads(activation, new Packs(Map.of()))
                        .term("a-1", "spec-kitty-core", "lane", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The read of the term lane of the glossary context spec-kitty-core under activation a-1:"
                        + " the winning context does not hold the term the activation judged held.");
    }

    /** The activations port: answers one outcome and records each ask. */
    private final class Activation implements ActivationArtifacts {

        private final Outcome<?> answer;

        Activation(Outcome<?> answer) {
            this.answer = answer;
        }

        @SuppressWarnings("unchecked")
        private <T> Outcome<T> answer() {
            return (Outcome<T>) answer;
        }

        @Override
        public Outcome<Winners> winners(String activationId, ArtifactKind kind, @Nullable String ifNoneMatch) {
            asked.add("winners " + activationId + " " + kind.token() + " " + ifNoneMatch);
            return answer();
        }

        @Override
        public Outcome<GlossaryWinner> glossary(String activationId, String contextId, @Nullable String ifNoneMatch) {
            asked.add("glossary " + activationId + " " + contextId + " " + ifNoneMatch);
            return answer();
        }

        @Override
        public Outcome<ContextsInForce> contextsInForce(
                String activationId, ContextsInForce.Vocabulary vocabulary, @Nullable String ifNoneMatch) {
            throw new UnsupportedOperationException("no vocabulary is read here");
        }

        @Override
        public Outcome<GlossaryWinner> glossaryTerm(
                String activationId, String contextId, String termId, @Nullable String ifNoneMatch) {
            asked.add("term " + activationId + " " + contextId + " " + termId + " " + ifNoneMatch);
            return answer();
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

    /** The packs port: each pack lists its contexts, a pack it does not know is not found; it maps the known views. */
    private final class Packs implements GlossaryReads {

        private final Map<PackIdentifier, List<GlossaryView>> held;

        Packs(Map<PackIdentifier, List<GlossaryView>> held) {
            this.held = held;
        }

        @Override
        public Listed list(PackIdentifier pack, String expectedFingerprint) {
            asked.add("list " + pack.value() + " at " + expectedFingerprint);
            List<GlossaryView> contexts = held.get(pack);
            if (contexts == null) {
                throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.");
            }
            return new Listed(contexts, "rev", expectedFingerprint);
        }

        @Override
        public GlossaryView view(CharterArtifact context, String read) {
            asked.add("view " + context.localId() + " for " + read);
            return KITTY;
        }

        @Override
        public Listed list(PackIdentifier pack) {
            throw new UnsupportedOperationException("the twin reads against the activation's fingerprint");
        }

        @Override
        public Read context(PackIdentifier pack, String contextId) {
            throw new UnsupportedOperationException("the twin maps the activation's read");
        }

        @Override
        public TermRead term(PackIdentifier pack, String contextId, String termId) {
            throw new UnsupportedOperationException("the twin maps the activation's read");
        }
    }

    private static CharterArtifact entity(String id) {
        return new CharterArtifact() {
            @Override
            public ArtifactKind kind() {
                return ArtifactKind.GLOSSARY;
            }

            @Override
            public PackIdentifier packId() {
                return PROJ;
            }

            @Override
            public String localId() {
                return id;
            }

            @Override
            public Optional<String> schemaVersion() {
                return Optional.of("1.0");
            }

            @Override
            public String label() {
                return id;
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }
        };
    }
}
