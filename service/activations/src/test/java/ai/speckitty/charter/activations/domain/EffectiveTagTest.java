package ai.speckitty.charter.activations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import ai.speckitty.charter.activations.domain.EffectiveSet.State;
import ai.speckitty.charter.activations.domain.EffectiveTag.Query;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The entity tag of the reads that depend on the packs of the closure: the revision, the fingerprint of the packs in
 * the closure and the normalized query decide it, and nothing else does (T123, research R5).
 */
class EffectiveTagTest {

    private static final Revision REVISION =
            ActivationDocument.builder().build().document().revision();
    private static final Query FIRST_PAGE = new Query(Optional.empty(), Optional.empty(), Optional.empty(), 100);

    private static ClosureSnapshot closure(String closureFingerprint) {
        return ClosureSnapshots.snapshot()
                .pack("alpha", "1.0.0")
                .fingerprint("sha256:every-configured-pack")
                .closureFingerprint(closureFingerprint)
                .build();
    }

    @Test
    void theSameInputsGiveTheSameTag() {
        EffectiveTag first = EffectiveTag.artifacts(REVISION, closure("sha256:one"), FIRST_PAGE);
        EffectiveTag again = EffectiveTag.artifacts(
                REVISION, closure("sha256:one"), new Query(Optional.empty(), Optional.empty(), Optional.empty(), 100));

        assertThat(again).isEqualTo(first);
        assertThat(first.token()).matches("sha256:[0-9a-f]{64}");
        assertThat(first.token()).as("a digest, not a placeholder").isNotEqualTo("sha256:" + "0".repeat(64));
    }

    @Test
    void aChangedPackOfTheClosureGivesANewTag() {
        EffectiveTag before = EffectiveTag.artifacts(REVISION, closure("sha256:one"), FIRST_PAGE);
        EffectiveTag after = EffectiveTag.artifacts(REVISION, closure("sha256:two"), FIRST_PAGE);

        assertThat(after).isNotEqualTo(before);
        assertThat(EffectiveTag.activation(REVISION, closure("sha256:two")))
                .isNotEqualTo(EffectiveTag.activation(REVISION, closure("sha256:one")));
        assertThat(EffectiveTag.skipped(REVISION, closure("sha256:two")))
                .isNotEqualTo(EffectiveTag.skipped(REVISION, closure("sha256:one")));
    }

    @Test
    void aChangedPackOutsideTheClosureLeavesTheTagAsItIs() {
        ClosureSnapshot before = closure("sha256:one");
        ClosureSnapshot after = ClosureSnapshots.snapshot()
                .pack("alpha", "1.0.0")
                .fingerprint("sha256:a-pack-outside-the-closure-changed")
                .closureFingerprint("sha256:one")
                .build();

        assertThat(after.fingerprint()).isNotEqualTo(before.fingerprint());
        assertThat(EffectiveTag.artifacts(REVISION, after, FIRST_PAGE))
                .as("the tag covers the digest of the closure's own packs, not the one of every configured pack")
                .isEqualTo(EffectiveTag.artifacts(REVISION, before, FIRST_PAGE));
        assertThat(EffectiveTag.activation(REVISION, after)).isEqualTo(EffectiveTag.activation(REVISION, before));
        assertThat(EffectiveTag.skipped(REVISION, after)).isEqualTo(EffectiveTag.skipped(REVISION, before));
    }

    @Test
    void eachQueryVariantHasItsOwnTag() {
        ClosureSnapshot closure = closure("sha256:one");
        List<Query> variants = List.of(
                FIRST_PAGE,
                new Query(Optional.of(State.ACTIVE), Optional.empty(), Optional.empty(), 100),
                new Query(Optional.of(State.INACTIVE), Optional.empty(), Optional.empty(), 100),
                new Query(Optional.empty(), Optional.of(ArtifactKind.DIRECTIVE), Optional.empty(), 100),
                new Query(Optional.empty(), Optional.of(ArtifactKind.TACTIC), Optional.empty(), 100),
                new Query(Optional.empty(), Optional.empty(), Optional.of("c1"), 100),
                new Query(Optional.empty(), Optional.empty(), Optional.of("c2"), 100),
                new Query(Optional.empty(), Optional.empty(), Optional.empty(), 50),
                new Query(Optional.of(State.ACTIVE), Optional.of(ArtifactKind.DIRECTIVE), Optional.of("c1"), 50));

        Set<EffectiveTag> tags = new HashSet<>();
        variants.forEach(query -> tags.add(EffectiveTag.artifacts(REVISION, closure, query)));

        assertThat(tags).as("no two representations share a tag").hasSize(variants.size());
    }

    @Test
    void aFilterThatMovesBetweenFieldsIsAnotherRepresentation() {
        ClosureSnapshot closure = closure("sha256:one");

        assertThat(EffectiveTag.artifacts(
                        REVISION, closure, new Query(Optional.empty(), Optional.empty(), Optional.of("active"), 100)))
                .isNotEqualTo(EffectiveTag.artifacts(
                        REVISION,
                        closure,
                        new Query(Optional.of(State.ACTIVE), Optional.empty(), Optional.empty(), 100)));
    }

    @Test
    void theV1ToV2WriteGivesANewTag() {
        Revision v1 = Revision.of("{\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8));
        ClosureSnapshot closure = closure("sha256:one");

        assertThat(v1).isNotEqualTo(REVISION);
        assertThat(EffectiveTag.artifacts(REVISION, closure, FIRST_PAGE))
                .isNotEqualTo(EffectiveTag.artifacts(v1, closure, FIRST_PAGE));
        assertThat(EffectiveTag.activation(REVISION, closure)).isNotEqualTo(EffectiveTag.activation(v1, closure));
    }

    @Test
    void theReadsOfOneActivationAndClosureNeverShareATag() {
        ClosureSnapshot closure = closure("sha256:one");

        Set<EffectiveTag> tags = new HashSet<>(List.of(
                EffectiveTag.activation(REVISION, closure),
                EffectiveTag.skipped(REVISION, closure),
                EffectiveTag.artifacts(REVISION, closure, FIRST_PAGE)));

        assertThat(tags)
                .as("getActivation, the skipped report and the unfiltered list")
                .hasSize(3);
    }

    @Test
    void eachReadOfTheSameArtifactHasATagNamespaceOfItsOwn() {
        ClosureSnapshot closure = closure("sha256:one");
        EffectiveTag.ArtifactQuery skill = new EffectiveTag.ArtifactQuery(ArtifactKind.SKILL, "land-pr", 0);
        EffectiveTag.ArtifactQuery asset = new EffectiveTag.ArtifactQuery(ArtifactKind.ASSET, "run", 0);

        Set<EffectiveTag> ofTheSkill = new HashSet<>(List.of(
                EffectiveTag.artifact(REVISION, closure, skill),
                EffectiveTag.winner(EffectiveTag.Read.SKILL, REVISION, closure, skill),
                EffectiveTag.profile(REVISION, closure, skill)));
        Set<EffectiveTag> ofTheAsset = new HashSet<>(List.of(
                EffectiveTag.artifact(REVISION, closure, asset),
                EffectiveTag.winner(EffectiveTag.Read.ASSET, REVISION, closure, asset),
                EffectiveTag.winner(EffectiveTag.Read.ASSET_PAYLOAD, REVISION, closure, asset)));
        Set<EffectiveTag> listsOfOneKind = new HashSet<>(List.of(
                EffectiveTag.winners(EffectiveTag.Read.SKILLS, ArtifactKind.SKILL, REVISION, closure),
                EffectiveTag.winners(EffectiveTag.Read.ASSETS, ArtifactKind.SKILL, REVISION, closure)));

        assertThat(ofTheSkill)
                .as("the generic read, the skill read and the profile read of one id and depth")
                .hasSize(3);
        assertThat(ofTheAsset)
                .as("the generic read, the asset read and the payload read of one id and depth")
                .hasSize(3);
        assertThat(listsOfOneKind).as("two lists over the same query").hasSize(2);
    }

    // ---- the graph and the action context (T140, T141): each representation has its own tag ----

    private static final EffectiveTag.ContextQuery IMPLEMENT =
            new EffectiveTag.ContextQuery("software-dev", "implement", 0);

    @Test
    void theGraphAndTheContextEachHaveATagOfTheirOwnOverTheSameRevisionAndClosure() {
        ClosureSnapshot closure = closure("sha256:one");

        Set<EffectiveTag> tags = new HashSet<>(List.of(
                EffectiveTag.activation(REVISION, closure),
                EffectiveTag.skipped(REVISION, closure),
                EffectiveTag.artifacts(REVISION, closure, FIRST_PAGE),
                EffectiveTag.graph(REVISION, closure),
                EffectiveTag.context(REVISION, closure, IMPLEMENT)));

        assertThat(tags)
                .as("getActivation, the skipped report, the list, the graph and the context: five reads, five tags")
                .hasSize(5);
        assertThat(EffectiveTag.graph(REVISION, closure))
                .isEqualTo(EffectiveTag.graph(REVISION, closure("sha256:one")));
    }

    @Test
    void aChangeOfTheClosureMovesBothTagsAndALaterPackOutsideTheClosureMovesNeither() {
        ClosureSnapshot before = closure("sha256:one");
        ClosureSnapshot after = closure("sha256:two");
        ClosureSnapshot outside = ClosureSnapshots.snapshot()
                .pack("alpha", "1.0.0")
                .fingerprint("sha256:a-pack-outside-the-closure-changed")
                .closureFingerprint("sha256:one")
                .build();

        assertThat(EffectiveTag.graph(REVISION, after)).isNotEqualTo(EffectiveTag.graph(REVISION, before));
        assertThat(EffectiveTag.context(REVISION, after, IMPLEMENT))
                .isNotEqualTo(EffectiveTag.context(REVISION, before, IMPLEMENT));
        assertThat(EffectiveTag.graph(REVISION, outside)).isEqualTo(EffectiveTag.graph(REVISION, before));
        assertThat(EffectiveTag.context(REVISION, outside, IMPLEMENT))
                .isEqualTo(EffectiveTag.context(REVISION, before, IMPLEMENT));
    }

    @Test
    void aChangeOfTheDocumentMovesBothTags() {
        Revision other = Revision.of("{\"schemaVersion\":2,\"selected\":{}}".getBytes(StandardCharsets.UTF_8));
        ClosureSnapshot closure = closure("sha256:one");

        assertThat(other).isNotEqualTo(REVISION);
        assertThat(EffectiveTag.graph(other, closure)).isNotEqualTo(EffectiveTag.graph(REVISION, closure));
        assertThat(EffectiveTag.context(other, closure, IMPLEMENT))
                .isNotEqualTo(EffectiveTag.context(REVISION, closure, IMPLEMENT));
    }

    @Test
    void eachMissionTypeActionAndDepthHasItsOwnContextTag() {
        ClosureSnapshot closure = closure("sha256:one");
        List<EffectiveTag.ContextQuery> variants = List.of(
                IMPLEMENT,
                new EffectiveTag.ContextQuery("software-dev", "implement", 1),
                new EffectiveTag.ContextQuery("software-dev", "review", 0),
                new EffectiveTag.ContextQuery("research", "implement", 0),
                new EffectiveTag.ContextQuery("software-dev/implement", "", 0));

        Set<EffectiveTag> tags = new HashSet<>();
        variants.forEach(query -> tags.add(EffectiveTag.context(REVISION, closure, query)));

        assertThat(tags).as("no two contexts share a tag").hasSize(variants.size());
    }

    // ---- the effective prompt (WP07, research R10): a tag of its own, per mission type and action ----

    @Test
    void thePromptHasATagOfItsOwnPerStepThatMovesWithTheClosureAndNotWithAPackOutsideIt() {
        ClosureSnapshot closure = closure("sha256:one");
        EffectiveTag.PromptQuery plan = new EffectiveTag.PromptQuery("software-dev", "implement");
        List<EffectiveTag.PromptQuery> variants = List.of(
                plan,
                new EffectiveTag.PromptQuery("software-dev", "review"),
                new EffectiveTag.PromptQuery("research", "implement"),
                new EffectiveTag.PromptQuery("software-dev/implement", ""));
        Set<EffectiveTag> tags = new HashSet<>();
        variants.forEach(query -> tags.add(EffectiveTag.prompt(REVISION, closure, query)));
        ClosureSnapshot outside = ClosureSnapshots.snapshot()
                .pack("alpha", "1.0.0")
                .fingerprint("sha256:a-pack-outside-the-closure-changed")
                .closureFingerprint("sha256:one")
                .build();

        assertThat(tags).as("no two steps share a prompt tag").hasSize(variants.size());
        assertThat(EffectiveTag.prompt(REVISION, closure, plan))
                .as("the context of the same step is another representation")
                .isNotEqualTo(EffectiveTag.context(REVISION, closure, IMPLEMENT));
        assertThat(EffectiveTag.prompt(REVISION, closure("sha256:two"), plan))
                .isNotEqualTo(EffectiveTag.prompt(REVISION, closure, plan));
        assertThat(EffectiveTag.prompt(REVISION, outside, plan))
                .isEqualTo(EffectiveTag.prompt(REVISION, closure, plan));
    }

    // ---- one golden vector: it guards an accidental edit to DOMAIN or to the hash-input format, not a forgotten
    // ---- domain bump (a rule change that moves no input leaves the token as it was). ----

    private static final Revision GOLDEN_REVISION =
            new Revision("sha256:0e9561cfb83d50990a103b3896fe249a11fe27fa28985448187f93ec12116d72");

    @Test
    void theGraphTagOfAFixedInputIsAFixedLiteral() {
        assertThat(EffectiveTag.graph(GOLDEN_REVISION, closure("sha256:one")).token())
                .as("graph, domain v3")
                .isEqualTo("sha256:2fbffbb0d62e9a547dc75f1adfa62a4aa6dbf5af7a6cef81a54179685ee8412b");
    }

    @Test
    void theContextTagOfAFixedInputIsAFixedLiteral() {
        assertThat(EffectiveTag.context(
                                GOLDEN_REVISION,
                                closure("sha256:one"),
                                new EffectiveTag.ContextQuery("software-dev", "implement", 0))
                        .token())
                .as("context, domain v3")
                .isEqualTo("sha256:e70a49ba757301126af1b78987c6b4a3036178fce258f36bdbe9e048faf0d3e3");
    }

    /**
     * Rule E17 as amended (research R10, squad D1) and slice A2's links (#536): the context answers the ownership rule,
     * and existing reads carry new links, under an unchanged revision and closure fingerprint, so the domain moved once
     * to {@code v3} for every effective read, and a tag a client kept from {@code v2} never matches again. The {@code v2} tokens are the literals the earlier recipe gave for the same inputs.
     */
    @Test
    void aTagOfTheV2RecipeNoLongerMatchesAnyEffectiveRead() {
        String graphV2 = "sha256:a01f7179ac5e8f92de38adcaa3b6af17adbd68e4ac36e4f9c509da95de2654e9";
        String contextV2 = "sha256:2abe372a3a96ccbb03e1f250bd6f7f34bff98e0a12e0fc43e766ebf03300705a";

        assertThat(sha256("charter-effective-read\0v2\ngraph\n" + GOLDEN_REVISION.token() + "\nsha256:one\n"))
                .as("the v2 literal is the v2 recipe over the same inputs")
                .isEqualTo(graphV2);
        assertThat(sha256("charter-effective-read\0v2\ncontext\n" + GOLDEN_REVISION.token()
                        + "\nsha256:one\nmissionType=software-dev\naction=implement\ndepth=0"))
                .as("the v2 literal is the v2 recipe over the same inputs")
                .isEqualTo(contextV2);
        assertThat(EffectiveTag.graph(GOLDEN_REVISION, closure("sha256:one")).token())
                .isNotEqualTo(graphV2);
        assertThat(EffectiveTag.context(
                                GOLDEN_REVISION,
                                closure("sha256:one"),
                                new EffectiveTag.ContextQuery("software-dev", "implement", 0))
                        .token())
                .isNotEqualTo(contextV2);
        assertThat(sha256("charter-effective-read\0v3\ngraph\n" + GOLDEN_REVISION.token() + "\nsha256:one\n"))
                .as("the control: the v3 recipe over the same inputs is the tag")
                .isEqualTo(EffectiveTag.graph(GOLDEN_REVISION, closure("sha256:one"))
                        .token());
    }

    private static String sha256(String preimage) {
        try {
            return "sha256:"
                    + java.util.HexFormat.of()
                            .formatHex(java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(preimage.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
