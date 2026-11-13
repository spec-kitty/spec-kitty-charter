package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.AssetFile;
import ai.speckitty.charter.packs.domain.AssetIdentifier;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.ContractStep;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.DirectiveRepository;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.GuideBlock;
import ai.speckitty.charter.packs.domain.MissionStepContract;
import ai.speckitty.charter.packs.domain.MissionStepContractIdentifier;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackEdge;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.PackSources;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.ParadigmRepository;
import ai.speckitty.charter.packs.domain.Payload;
import ai.speckitty.charter.packs.domain.Procedure;
import ai.speckitty.charter.packs.domain.ProcedureIdentifier;
import ai.speckitty.charter.packs.domain.ProcedureRepository;
import ai.speckitty.charter.packs.domain.ProcedureStep;
import ai.speckitty.charter.packs.domain.Skill;
import ai.speckitty.charter.packs.domain.SkillForm;
import ai.speckitty.charter.packs.domain.SkillIdentifier;
import ai.speckitty.charter.packs.domain.StepDelegation;
import ai.speckitty.charter.packs.domain.Styleguide;
import ai.speckitty.charter.packs.domain.StyleguideIdentifier;
import ai.speckitty.charter.packs.domain.StyleguideRepository;
import ai.speckitty.charter.packs.domain.StyleguideScope;
import ai.speckitty.charter.packs.domain.Tactic;
import ai.speckitty.charter.packs.domain.TacticIdentifier;
import ai.speckitty.charter.packs.domain.TacticRepository;
import ai.speckitty.charter.packs.domain.TacticStep;
import ai.speckitty.charter.packs.domain.Toolguide;
import ai.speckitty.charter.packs.domain.ToolguideIdentifier;
import ai.speckitty.charter.packs.domain.ToolguideRepository;
import ai.speckitty.charter.profiles.domain.AgentProfile;
import ai.speckitty.charter.profiles.domain.AgentProfileIdentifier;
import ai.speckitty.charter.profiles.domain.Specialization;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The generic artifact reads over in-memory ports: the list's order, cursor and page bound, the kind filter, and the
 * order of the item's refusals (the pack, then the kind, then the id). The reference grammar reads through the same
 * service: a pack-qualified reference answers the artifact of the pack it names, a bare one only the referring pack's,
 * and the two refusals name their packs.
 */
class ArtifactReadServiceTest {

    private static final PackIdentifier PACK = new PackIdentifier("pack-a");
    private static final PackIdentifier EMPTY = new PackIdentifier("empty");
    private static final PackIdentifier ABSENT = new PackIdentifier("absent");
    // Configured, but without a readable pack.yaml: its files can be read one by one, the pack aggregate cannot.
    private static final PackIdentifier BARE = new PackIdentifier("bare");
    // The second pack of the shared two-pack fixture (SC-004): bravo, holding directive SHARED_1.
    private static final PackIdentifier SIBLING = new PackIdentifier("pack-b");
    // The pack of the mission kinds' fixture (missionFixture): two mission types and three step contracts.
    private static final PackIdentifier MISSIONS = new PackIdentifier("missions");
    // The pack of the literal kinds' fixture (#105, #107): two assets, one without a title, and a skill.
    private static final PackIdentifier HOLDER = new PackIdentifier("holder");
    private static final String D1 = "sha256:" + "1".repeat(64);
    private static final String D2 = "sha256:" + "2".repeat(64);
    private static final PackRevision REVISION = PackRevision.compute(List.of("committed"));

    // Stored out of order on purpose: the list sorts, whatever order the storage gives.
    private final Map<PackIdentifier, Pack> stored = new LinkedHashMap<>(Map.of(
            PACK,
            Pack.builder()
                    .id(PACK)
                    .descriptor(descriptor(PACK))
                    .toolguide(toolguide("git"))
                    .artifact(procedure("refactoring"))
                    .artifact(profile("jenny"))
                    .paradigm(paradigm("layered"))
                    .tactic(tactic("alpha", "Alpha"))
                    .directive(directive("GUARD"))
                    .styleguide(styleguide("java-style"))
                    .tactic(tactic("Zeta", "Zeta"))
                    .build(),
            EMPTY,
            Pack.builder().id(EMPTY).descriptor(descriptor(EMPTY)).build(),
            BARE,
            Pack.builder().id(BARE).descriptor(descriptor(BARE)).build(),
            HOLDER,
            Pack.builder()
                    .id(HOLDER)
                    .descriptor(descriptor(HOLDER))
                    .artifact(skill("land-pr"))
                    .artifact(asset("run", "run.py", "Run the lint"))
                    .artifact(asset("logo", "logo.png", null))
                    .assetFile(new AssetFile("run.py", Payload.of(new byte[] {'1'})))
                    .assetFile(new AssetFile("logo.png", Payload.of(new byte[] {(byte) 0x89, 'P'})))
                    .build()));

    private final AtomicInteger digests = new AtomicInteger();
    /** What a descriptor lookup does to the inputs as it answers; nothing, unless a test plants a change. */
    private Runnable onFind = () -> {};

    /** Whether the aggregate load fails for every pack although its pack.yaml reads. */
    private boolean aggregateUnreadable;

    /** How many times the compose asked the catalog for the configured packs: once per resolution of it. */
    private final AtomicInteger composeResolutions = new AtomicInteger();

    private final ArtifactReadService service = serviceDigesting(() -> {
        digests.incrementAndGet();
        return D1;
    });

    /** The step ids each mission type declares, by its id, as the store reads them from the mission type's file. */
    private final Map<String, Set<String>> stepIds = new HashMap<>();

    /** The graph-document edges of each pack, the references an artifact writes (ADR-0064). */
    private final Map<PackIdentifier, List<PackEdge>> edges = new HashMap<>();

    private ArtifactReadService serviceDigesting(PackSources sources) {
        return serviceDigesting(sources, pack -> REVISION);
    }

    private ArtifactReadService serviceDigesting(PackSources sources, PackRevisions revisions) {
        InMemoryStore store = new InMemoryStore();
        ConsistentRead read = new ConsistentRead(sources);
        return new ArtifactReadService(
                store,
                store,
                read,
                new TacticReadService(store, store, read, revisions),
                new DirectiveReadService(store, store, read, revisions),
                new StyleguideReadService(store, store, read, revisions),
                new ParadigmReadService(store, store, read, revisions),
                new ToolguideReadService(store, store, read, revisions),
                new ProcedureReadService(store, read, revisions),
                new OfferingReadService(store, store, store, read),
                new CompositeComponentResolver(),
                pack -> edges.getOrDefault(pack, List.of()),
                type -> stepIds.getOrDefault(type.localId(), Set.of()),
                revisions);
    }

    private ArtifactReadService serviceWhoseInputsChange() {
        AtomicInteger count = new AtomicInteger();
        return serviceDigesting(() -> count.incrementAndGet() == 1 ? D1 : D2);
    }

    // ---- the list ---------------------------------------------------------------------------------------------

    @Test
    void listsInTheServedKindOrderThenByIdInCodePointOrder() {
        ArtifactReadService.Page page =
                service.list(PACK, Optional.empty(), Optional.empty(), 50).value();

        assertThat(page.items())
                .extracting(ArtifactReadService.Summary::position)
                .containsExactly(
                        position(ArtifactKind.TACTIC, "Zeta"),
                        position(ArtifactKind.TACTIC, "alpha"),
                        position(ArtifactKind.DIRECTIVE, "GUARD"),
                        position(ArtifactKind.STYLEGUIDE, "java-style"),
                        position(ArtifactKind.PARADIGM, "layered"),
                        position(ArtifactKind.TOOLGUIDE, "git"),
                        position(ArtifactKind.PROCEDURE, "refactoring"),
                        position(ArtifactKind.AGENT_PROFILE, "jenny"));
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void aSummaryTitleIsTheNameOfATacticOrParadigmAndTheTitleOfTheOthers() {
        assertThat(service.list(PACK, Optional.empty(), Optional.empty(), 50)
                        .value()
                        .items())
                .extracting(ArtifactReadService.Summary::title)
                .containsExactly(
                        "Zeta",
                        "Alpha",
                        "Title of GUARD",
                        "Title of java-style",
                        "Name of layered",
                        "How to use git",
                        "Title of refactoring",
                        "Name of jenny");
    }

    @Test
    void theOrderComparesCodePointsNotUtf16Units() {
        // U+FB01 is one UTF-16 unit above the surrogates; U+1F600 is a surrogate pair, so String.compareTo puts it
        // first, while code-point order puts it last.
        ArtifactPosition bmp = position(ArtifactKind.TACTIC, "ﬁ");
        ArtifactPosition supplementary = position(ArtifactKind.TACTIC, "😀");

        assertThat(ArtifactPosition.ORDER.compare(bmp, supplementary)).isNegative();
        assertThat(ArtifactPosition.ORDER.compare(supplementary, bmp)).isPositive();
        assertThat(ArtifactPosition.ORDER.compare(bmp, position(ArtifactKind.TACTIC, "ﬁ")))
                .isZero();
        assertThat(ArtifactPosition.ORDER.compare(
                        position(ArtifactKind.TACTIC, "ab"), position(ArtifactKind.TACTIC, "a")))
                .as("a longer id after its prefix")
                .isPositive();
        assertThat(ArtifactPosition.ORDER.compare(
                        position(ArtifactKind.TOOLGUIDE, "a"), position(ArtifactKind.DIRECTIVE, "z")))
                .as("the kind before the id")
                .isPositive();
    }

    @Test
    void aPositionNamesAServedKind() {
        assertThatThrownBy(() -> position(ArtifactKind.TEMPLATE, "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thePageHoldsTheItemsStrictlyAfterThePosition() {
        ArtifactReadService.Page page = service.list(
                        PACK, Optional.empty(), Optional.of(position(ArtifactKind.TACTIC, "alpha")), 2)
                .value();

        assertThat(page.items())
                .extracting(ArtifactReadService.Summary::position)
                .containsExactly(
                        position(ArtifactKind.DIRECTIVE, "GUARD"), position(ArtifactKind.STYLEGUIDE, "java-style"));
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void aPositionNoArtifactHoldsAnyMorePagesOnFromWhereItWas() {
        // "b" sorts between "alpha" and the directives; the pack holds no tactic b (a deleted anchor).
        ArtifactReadService.Page page = service.list(
                        PACK, Optional.empty(), Optional.of(position(ArtifactKind.TACTIC, "b")), 1)
                .value();

        assertThat(page.items())
                .extracting(ArtifactReadService.Summary::position)
                .containsExactly(position(ArtifactKind.DIRECTIVE, "GUARD"));
    }

    @Test
    void aPageOfExactlyTheRemainingItemsHasNoMore() {
        assertThat(service.list(PACK, Optional.empty(), Optional.empty(), 8)
                        .value()
                        .hasMore())
                .isFalse();
        assertThat(service.list(PACK, Optional.empty(), Optional.empty(), 7)
                        .value()
                        .hasMore())
                .isTrue();
        assertThat(service.list(PACK, Optional.empty(), Optional.empty(), 7)
                        .value()
                        .items())
                .hasSize(7);
        assertThat(service.list(PACK, Optional.empty(), Optional.of(position(ArtifactKind.AGENT_PROFILE, "jenny")), 5)
                        .value())
                .as("after the last item")
                .isEqualTo(new ArtifactReadService.Page(List.of(), false));
    }

    @Test
    void aPageSizeBelowOneIsRefusedBeforeAnyRead() {
        assertThatThrownBy(() -> service.list(PACK, Optional.empty(), Optional.empty(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(digests).hasValue(0);
    }

    @Test
    void theKindFilterListsThatKindOnly() {
        ArtifactReadService.Page tactics = service.list(PACK, Optional.of(ArtifactKind.TACTIC), Optional.empty(), 50)
                .value();
        ArtifactReadService.Page afterZeta = service.list(
                        PACK, Optional.of(ArtifactKind.TACTIC), Optional.of(position(ArtifactKind.TACTIC, "Zeta")), 50)
                .value();

        assertThat(tactics.items())
                .extracting(ArtifactReadService.Summary::position)
                .containsExactly(position(ArtifactKind.TACTIC, "Zeta"), position(ArtifactKind.TACTIC, "alpha"));
        assertThat(afterZeta.items())
                .extracting(ArtifactReadService.Summary::position)
                .containsExactly(position(ArtifactKind.TACTIC, "alpha"));
        assertThat(service.list(EMPTY, Optional.of(ArtifactKind.DIRECTIVE), Optional.empty(), 50)
                        .value())
                .isEqualTo(new ArtifactReadService.Page(List.of(), false));
    }

    @Test
    void aKindFilterThatIsNotServedIsRefusedBeforeAnyRead() {
        assertThatThrownBy(() -> service.list(PACK, Optional.of(ArtifactKind.TEMPLATE), Optional.empty(), 50))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(digests).hasValue(0);
    }

    @Test
    void theListIsOneVerifiedReadAndCarriesItsFingerprint() {
        var verified = service.list(PACK, Optional.empty(), Optional.empty(), 50);

        assertThat(verified.fingerprint()).isEqualTo(D1);
        assertThat(digests).as("one read digests twice").hasValue(2);
    }

    @Test
    void anUnconfiguredPackIsNotFoundOnTheListWithNothingChained() {
        assertThatThrownBy(() -> service.list(ABSENT, Optional.empty(), Optional.empty(), 50))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND);
                    assertThat(refused.getMessage()).isEqualTo("Pack absent was not found.");
                    assertThat(refused.getCause()).isNull();
                });
    }

    // ---- a configured pack without a readable pack.yaml (operator ruling of 2026-10-31) -----------------------

    @Test
    void aConfiguredPackWithoutAReadableDescriptorIsUnreadableOnTheListNotNotFound() {
        assertThatThrownBy(() -> service.list(BARE, Optional.empty(), Optional.empty(), 50))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tactic", "directive", "toolguide", "bogus"})
    void aConfiguredPackWithoutAReadableDescriptorIsUnreadableOnTheItem(String kind) {
        assertThat(codeOf(() -> service.item(BARE, kind, "rehearse"))).isEqualTo(RefusalCode.PACK_UNREADABLE);
    }

    /**
     * The pack.yaml check and the artifact read are judged against one pair of digests: pack.yaml goes right after the
     * check saw it, so the artifact read alone would see two agreeing digests of a state that must refuse. The item
     * never answers 200 for it.
     */
    @ParameterizedTest
    @ValueSource(strings = {"tactic", "directive"})
    void aPackYamlRemovedRightAfterTheItemCheckedItIsNeverAnswered200(String kind) {
        AtomicBoolean removed = new AtomicBoolean();
        onFind = () -> removed.set(true);
        ArtifactReadService racing = serviceDigesting(() -> removed.get() ? D2 : D1);
        String id = kind.equals("tactic") ? "Zeta" : "GUARD";

        assertThatThrownBy(() -> racing.item(PACK, kind, id)).isInstanceOfSatisfying(CharterRefusal.class, refused -> {
            assertThat(refused.code()).isEqualTo(RefusalCode.STALE_INPUT);
            assertThat(refused.getMessage())
                    .isEqualTo("The inputs changed while the " + kind + " was read; read it again.");
        });
        assertThat(removed).as("the change happened during the read").isTrue();
    }

    @Test
    void anItemIsAnsweredWithTheDigestTheDescriptorCheckWasJudgedAgainst() {
        AtomicInteger calls = new AtomicInteger();
        List<String> order = new ArrayList<>();
        onFind = () -> order.add("find after digest " + calls.get());
        ArtifactReadService spied = serviceDigesting(() -> {
            calls.incrementAndGet();
            order.add("digest " + calls.get());
            return D1;
        });

        ConsistentRead.Verified<ArtifactReadService.Item> verified = spied.item(PACK, "tactic", "Zeta");

        assertThat(verified.fingerprint()).isEqualTo(D1);
        assertThat(order.indexOf("find after digest 1"))
                .as("the descriptor is checked after the first digest: %s", order)
                .isPositive();
        assertThat(order.getLast())
                .as("and the last digest comes after it: %s", order)
                .startsWith("digest");
    }

    @Test
    void aListWhoseInputsChangeIsStale() {
        assertThatThrownBy(() -> serviceWhoseInputsChange().list(PACK, Optional.empty(), Optional.empty(), 50))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.STALE_INPUT);
                    assertThat(refused.getMessage())
                            .isEqualTo("The inputs changed while the artifacts were read; read them again.");
                });
    }

    // ---- the item ---------------------------------------------------------------------------------------------

    @Test
    void aTacticItemCarriesItsRevision() {
        var verified = service.item(PACK, "tactic", "alpha");

        assertThat(verified.value()).isInstanceOfSatisfying(ArtifactReadService.Item.OfTactic.class, item -> {
            assertThat(item.artifact().id()).isEqualTo(new TacticIdentifier("alpha"));
            assertThat(item.revision()).isEqualTo(REVISION);
        });
        assertThat(verified.fingerprint()).isEqualTo(D1);
    }

    /** NFR-002: one item read of a tactic or a step contract asks for the pack revision exactly once. */
    @ParameterizedTest
    @CsvSource({"tactic, alpha", "mission_step_contract, specify"})
    void anItemReadsTheRevisionOnce(String kind, String id) {
        missionFixture();
        AtomicInteger asked = new AtomicInteger();
        ArtifactReadService counting = serviceDigesting(() -> D1, pack -> {
            asked.incrementAndGet();
            return REVISION;
        });

        counting.item(packOf(kind), kind, id);

        assertThat(asked).hasValue(1);
    }

    /** FR-004: an item whose inputs change during the read is refused stale, so it answers no revision. */
    @ParameterizedTest
    @CsvSource({"tactic, alpha", "mission_step_contract, specify"})
    void anItemWhoseInputsChangeIsStaleAndAnswersNoRevision(String kind, String id) {
        missionFixture();
        AtomicInteger asked = new AtomicInteger();
        AtomicInteger count = new AtomicInteger();
        ArtifactReadService racing = serviceDigesting(() -> count.incrementAndGet() == 1 ? D1 : D2, pack -> {
            asked.incrementAndGet();
            return REVISION;
        });

        assertThatThrownBy(() -> racing.item(packOf(kind), kind, id))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.STALE_INPUT));
        assertThat(asked).as("the revision is read inside the refused read").hasValue(1);
    }

    /** One mapping for every kind on the item route: a revision the storage cannot read is a pack not found. */
    @ParameterizedTest
    @CsvSource({"tactic, alpha", "directive, GUARD", "mission_step_contract, specify"})
    void anUnreadableRevisionIsThePackNotFoundForEveryItemKind(String kind, String id) {
        missionFixture();
        ArtifactReadService unreadable = serviceDigesting(() -> D1, pack -> {
            throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "the revision cannot be read");
        });

        assertThat(codeOf(() -> unreadable.item(packOf(kind), kind, id))).isEqualTo(RefusalCode.PACK_NOT_FOUND);
    }

    /** The aggregate the step contract read loads can fail after pack.yaml was read; that too is a pack not found. */
    @Test
    void anUnreadableAggregateIsThePackNotFoundForAStepContract() {
        ArtifactReadService missions = missionFixture();
        aggregateUnreadable = true;

        assertThat(codeOf(() -> missions.item(MISSIONS, "mission_step_contract", "specify")))
                .isEqualTo(RefusalCode.PACK_NOT_FOUND);
    }

    private static PackIdentifier packOf(String kind) {
        return kind.equals("mission_step_contract") ? MISSIONS : PACK;
    }

    @Test
    void eachItemKindCarriesItsRevision() {
        assertThat(service.item(PACK, "directive", "GUARD").value())
                .isInstanceOfSatisfying(
                        ArtifactReadService.Item.OfDirective.class,
                        item -> assertThat(item.artifact().id()).isEqualTo(new DirectiveIdentifier("GUARD")));
        assertThat(service.item(PACK, "styleguide", "java-style").value())
                .isInstanceOf(ArtifactReadService.Item.OfStyleguide.class);
        assertThat(service.item(PACK, "paradigm", "layered").value())
                .isInstanceOf(ArtifactReadService.Item.OfParadigm.class);
        assertThat(service.item(PACK, "toolguide", "git").value())
                .isInstanceOf(ArtifactReadService.Item.OfToolguide.class);
        assertThat(service.item(PACK, "procedure", "refactoring").value())
                .isInstanceOfSatisfying(
                        ArtifactReadService.Item.OfProcedure.class,
                        item -> assertThat(item.artifact().id()).isEqualTo(new ProcedureIdentifier("refactoring")));
        for (String[] item : new String[][] {
            {"tactic", "alpha"},
            {"directive", "GUARD"},
            {"styleguide", "java-style"},
            {"paradigm", "layered"},
            {"toolguide", "git"},
            {"procedure", "refactoring"}
        }) {
            assertThat(service.item(PACK, item[0], item[1]).value().revision())
                    .as(item[0])
                    .isEqualTo(REVISION);
        }
    }

    @Test
    void anUnknownIdIsTheNotFoundOfItsKind() {
        assertThat(codeOf(() -> service.item(PACK, "tactic", "nope"))).isEqualTo(RefusalCode.TACTIC_NOT_FOUND);
        assertThat(codeOf(() -> service.item(PACK, "directive", "NOPE"))).isEqualTo(RefusalCode.DIRECTIVE_NOT_FOUND);
        assertThat(codeOf(() -> service.item(PACK, "styleguide", "nope"))).isEqualTo(RefusalCode.STYLEGUIDE_NOT_FOUND);
        assertThat(codeOf(() -> service.item(PACK, "paradigm", "nope"))).isEqualTo(RefusalCode.PARADIGM_NOT_FOUND);
        assertThat(codeOf(() -> service.item(PACK, "toolguide", "nope"))).isEqualTo(RefusalCode.TOOLGUIDE_NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"directive", "styleguide", "paradigm", "toolguide"})
    void anIdTheKindsRulesRefuseIsTheNotFoundOfItsKindNotAServerError(String kind) {
        // "Not an id" breaks every writable kind's identifier pattern; it names no artifact of the kind.
        assertThatThrownBy(() -> service.item(PACK, kind, "Not an id"))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code().token()).isEqualTo(kind + "_not_found");
                    assertThat(refused.getMessage()).isEqualTo("Pack pack-a has no " + kind + " Not an id.");
                });
        // The pack is judged inside the item's verified read (two digests) around the not-found's own (two more).
        assertThat(digests)
                .as("the pack is judged inside the item's verified read")
                .hasValue(4);
        assertThat(codeOf(() -> service.item(ABSENT, kind, "Not an id"))).isEqualTo(RefusalCode.PACK_NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"template", "bogus", "Tactic", "tactics", ""})
    void aKindTheServiceDoesNotServeIsKindNotFound(String kind) {
        assertThatThrownBy(() -> service.item(PACK, kind, "alpha"))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.KIND_NOT_FOUND);
                    assertThat(refused.getMessage()).isEqualTo("The service serves no artifact kind " + kind + ".");
                });
        assertThat(digests).as("the pack is judged inside one verified read").hasValue(2);
    }

    @Test
    void thePackIsJudgedBeforeTheKind() {
        assertThat(codeOf(() -> service.item(ABSENT, "bogus", "x"))).isEqualTo(RefusalCode.PACK_NOT_FOUND);
        assertThat(codeOf(() -> service.item(ABSENT, "tactic", "x"))).isEqualTo(RefusalCode.PACK_NOT_FOUND);
    }

    @Test
    void anUnservedKindWhoseInputsChangeIsStale() {
        assertThat(codeOf(() -> serviceWhoseInputsChange().item(PACK, "bogus", "x")))
                .isEqualTo(RefusalCode.STALE_INPUT);
    }

    // ---- the served kinds -------------------------------------------------------------------------------------

    @Test
    void theServedKindsAreTheFiveThenTheAssetAndTheSkillThenTheAgentProfileInTheirOrder() {
        assertThat(ArtifactReadService.SERVED)
                .containsExactly(
                        ArtifactKind.TACTIC,
                        ArtifactKind.DIRECTIVE,
                        ArtifactKind.STYLEGUIDE,
                        ArtifactKind.PARADIGM,
                        ArtifactKind.TOOLGUIDE,
                        ArtifactKind.PROCEDURE,
                        ArtifactKind.ASSET,
                        ArtifactKind.SKILL,
                        ArtifactKind.AGENT_PROFILE);
        assertThat(ArtifactReadService.listedKind("toolguide")).contains(ArtifactKind.TOOLGUIDE);
        assertThat(ArtifactReadService.listedKind("agent_profile")).contains(ArtifactKind.AGENT_PROFILE);
        assertThat(ArtifactReadService.listedKind("procedure")).contains(ArtifactKind.PROCEDURE);
        assertThat(ArtifactReadService.listedKind("asset")).contains(ArtifactKind.ASSET);
        assertThat(ArtifactReadService.listedKind("skill")).contains(ArtifactKind.SKILL);
        assertThat(ArtifactReadService.listedKind("template")).isEmpty();
        assertThat(ArtifactReadService.listedKind("TACTIC")).isEmpty();
    }

    // ---- the listed kinds: the served ones, then mission_type, then mission_step_contract (#99) ---------------

    @Test
    void theListedKindsAreTheServedOnesThenTheMissionTypeThenTheMissionStepContract() {
        assertThat(ArtifactReadService.LISTED)
                .as("the served kinds first, in their order, so a kind appended to them stays before the two")
                .startsWith(ArtifactReadService.SERVED.toArray(ArtifactKind[]::new))
                .endsWith(ArtifactKind.MISSION_TYPE, ArtifactKind.MISSION_STEP_CONTRACT)
                .hasSize(ArtifactReadService.SERVED.size() + 2)
                .doesNotHaveDuplicates();
        assertThat(ArtifactReadService.listedKind("mission_type")).contains(ArtifactKind.MISSION_TYPE);
        assertThat(ArtifactReadService.listedKind("mission_step_contract"))
                .contains(ArtifactKind.MISSION_STEP_CONTRACT);
        assertThat(ArtifactReadService.listedKind("toolguide")).contains(ArtifactKind.TOOLGUIDE);
        assertThat(ArtifactReadService.listedKind("template")).isEmpty();
        assertThat(ArtifactReadService.listedKind("mission-type")).isEmpty();
    }

    @Test
    void theTemplatedItemReadRendersTheFiveThenTheMissionStepContractButNoMissionTypeOrLiteralKind() {
        assertThat(ArtifactReadService.ITEM_KINDS)
                .containsExactly(
                        ArtifactKind.TACTIC,
                        ArtifactKind.DIRECTIVE,
                        ArtifactKind.STYLEGUIDE,
                        ArtifactKind.PARADIGM,
                        ArtifactKind.TOOLGUIDE,
                        ArtifactKind.PROCEDURE,
                        ArtifactKind.MISSION_STEP_CONTRACT)
                .doesNotContain(
                        ArtifactKind.MISSION_TYPE, ArtifactKind.AGENT_PROFILE, ArtifactKind.ASSET, ArtifactKind.SKILL);
        assertThat(ArtifactReadService.LISTED).containsAll(ArtifactReadService.ITEM_KINDS);
        assertThat(ArtifactReadService.LITERAL_ITEM_KINDS)
                .containsExactly(ArtifactKind.AGENT_PROFILE, ArtifactKind.ASSET, ArtifactKind.SKILL);
        assertThat(ArtifactReadService.SERVED)
                .as("every served kind is rendered by the templated item read or at its literal path, never both")
                .allSatisfy(kind -> assertThat(ArtifactReadService.ITEM_KINDS.contains(kind))
                        .as(kind.token())
                        .isNotEqualTo(ArtifactReadService.LITERAL_ITEM_KINDS.contains(kind)));
        assertThat(ArtifactReadService.SERVED)
                .doesNotContain(ArtifactKind.MISSION_TYPE, ArtifactKind.MISSION_STEP_CONTRACT);
    }

    @Test
    void eachListedKindHasItsNotFoundCode() {
        // The five older kinds and the two mission kinds keep their own codes; the kinds served after them, the agent
        // profile, the procedure, the asset and the skill, answer artifact_not_found (procedures.feature pins it end to
        // end).
        Map<ArtifactKind, RefusalCode> expected = Map.ofEntries(
                Map.entry(ArtifactKind.TACTIC, RefusalCode.TACTIC_NOT_FOUND),
                Map.entry(ArtifactKind.DIRECTIVE, RefusalCode.DIRECTIVE_NOT_FOUND),
                Map.entry(ArtifactKind.STYLEGUIDE, RefusalCode.STYLEGUIDE_NOT_FOUND),
                Map.entry(ArtifactKind.PARADIGM, RefusalCode.PARADIGM_NOT_FOUND),
                Map.entry(ArtifactKind.TOOLGUIDE, RefusalCode.TOOLGUIDE_NOT_FOUND),
                Map.entry(ArtifactKind.MISSION_TYPE, RefusalCode.MISSION_TYPE_NOT_FOUND),
                Map.entry(ArtifactKind.MISSION_STEP_CONTRACT, RefusalCode.MISSION_STEP_CONTRACT_NOT_FOUND),
                Map.entry(ArtifactKind.AGENT_PROFILE, RefusalCode.ARTIFACT_NOT_FOUND),
                Map.entry(ArtifactKind.PROCEDURE, RefusalCode.ARTIFACT_NOT_FOUND),
                Map.entry(ArtifactKind.ASSET, RefusalCode.ARTIFACT_NOT_FOUND),
                Map.entry(ArtifactKind.SKILL, RefusalCode.ARTIFACT_NOT_FOUND));

        assertThat(expected.keySet())
                .as("the map names every listed kind, so a newly listed kind needs its row")
                .containsExactlyInAnyOrderElementsOf(ArtifactReadService.LISTED);
        expected.forEach((kind, code) -> assertThat(ArtifactReadService.absentCode(kind))
                .as(kind.token())
                .isEqualTo(code));
        assertThatThrownBy(() -> ArtifactReadService.absentCode(ArtifactKind.TEMPLATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theListNamesTheMissionKindsAfterTheServedOnesTitledByTheirLabel() {
        ArtifactReadService missions = missionFixture();

        ArtifactReadService.Page page =
                missions.list(MISSIONS, Optional.empty(), Optional.empty(), 50).value();

        assertThat(page.items())
                .extracting(summary -> summary.position(), summary -> summary.title())
                .containsExactly(
                        tuple(position(ArtifactKind.DIRECTIVE, "GUARD"), "Title of GUARD"),
                        tuple(position(ArtifactKind.MISSION_TYPE, "documentation"), "Documentation"),
                        tuple(position(ArtifactKind.MISSION_TYPE, "software-dev"), "Software Development"),
                        tuple(position(ArtifactKind.MISSION_STEP_CONTRACT, "orphan"), "gather"),
                        tuple(position(ArtifactKind.MISSION_STEP_CONTRACT, "specify"), "specify"),
                        tuple(position(ArtifactKind.MISSION_STEP_CONTRACT, "unbound"), "deploy"));
    }

    @Test
    void theKindFilterListsAMissionKindOnlyAndPagesOnWithinIt() {
        ArtifactReadService missions = missionFixture();

        assertThat(missions.list(MISSIONS, Optional.of(ArtifactKind.MISSION_TYPE), Optional.empty(), 50)
                        .value()
                        .items())
                .extracting(ArtifactReadService.Summary::position)
                .containsExactly(
                        position(ArtifactKind.MISSION_TYPE, "documentation"),
                        position(ArtifactKind.MISSION_TYPE, "software-dev"));
        ArtifactReadService.Page first = missions.list(
                        MISSIONS, Optional.of(ArtifactKind.MISSION_STEP_CONTRACT), Optional.empty(), 2)
                .value();
        assertThat(first.items())
                .extracting(ArtifactReadService.Summary::position)
                .containsExactly(
                        position(ArtifactKind.MISSION_STEP_CONTRACT, "orphan"),
                        position(ArtifactKind.MISSION_STEP_CONTRACT, "specify"));
        assertThat(first.hasMore()).isTrue();
        assertThat(missions.list(
                                MISSIONS,
                                Optional.of(ArtifactKind.MISSION_STEP_CONTRACT),
                                Optional.of(first.items().getLast().position()),
                                2)
                        .value()
                        .items())
                .extracting(ArtifactReadService.Summary::position)
                .containsExactly(position(ArtifactKind.MISSION_STEP_CONTRACT, "unbound"));
    }

    @Test
    void aStepContractItemCarriesItsRevisionAndNamesWhatThePackHoldsOfItsMissionTypeStepAndDelegates() {
        ArtifactReadService missions = missionFixture();

        var verified = missions.item(MISSIONS, "mission_step_contract", "specify");

        assertThat(verified.fingerprint()).isEqualTo(D1);
        assertThat(verified.value())
                .isInstanceOfSatisfying(ArtifactReadService.Item.OfMissionStepContract.class, item -> {
                    assertThat(item.artifact().id()).isEqualTo(new MissionStepContractIdentifier("specify"));
                    assertThat(item.revision())
                            .as("every item carries its revision")
                            .isEqualTo(REVISION);
                    assertThat(item.missionTypeHeld()).isTrue();
                    assertThat(item.stepHeld()).isTrue();
                    assertThat(item.heldCandidates())
                            .as("GUARD is held; NOPE is not; the procedure is listed but the pack does not hold it")
                            .containsExactly(position(ArtifactKind.DIRECTIVE, "GUARD"));
                });
    }

    @Test
    void aStepContractWhoseMissionTypeOrStepThePackDoesNotHoldStillReads() {
        ArtifactReadService missions = missionFixture();

        assertThat(missions.item(MISSIONS, "mission_step_contract", "orphan").value())
                .isInstanceOfSatisfying(ArtifactReadService.Item.OfMissionStepContract.class, item -> {
                    assertThat(item.missionTypeHeld())
                            .as("research is not held")
                            .isFalse();
                    assertThat(item.stepHeld()).isFalse();
                    assertThat(item.heldCandidates()).isEmpty();
                });
        assertThat(missions.item(MISSIONS, "mission_step_contract", "unbound").value())
                .isInstanceOfSatisfying(ArtifactReadService.Item.OfMissionStepContract.class, item -> {
                    assertThat(item.missionTypeHeld())
                            .as("software-dev is held")
                            .isTrue();
                    assertThat(item.stepHeld())
                            .as("but declares no step deploy")
                            .isFalse();
                });
    }

    @Test
    void anUnknownStepContractIsItsKindsNotFoundAfterThePack() {
        ArtifactReadService missions = missionFixture();

        assertThatThrownBy(() -> missions.item(MISSIONS, "mission_step_contract", "nope"))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.MISSION_STEP_CONTRACT_NOT_FOUND);
                    assertThat(refused.getMessage()).isEqualTo("Pack missions has no mission_step_contract nope.");
                });
        assertThat(codeOf(() -> missions.item(ABSENT, "mission_step_contract", "specify")))
                .isEqualTo(RefusalCode.PACK_NOT_FOUND);
        assertThat(codeOf(() -> missions.item(BARE, "mission_step_contract", "specify")))
                .isEqualTo(RefusalCode.PACK_UNREADABLE);
    }

    /**
     * The deployed service answers a mission type's item at its own literal path, which wins over the templated one;
     * a request that reached the generic item read anyway is kind_not_found, never a server error, after the pack.
     */
    @Test
    void theGenericItemReadNeverThrowsForAMissionTypeItIsKindNotFoundAfterThePack() {
        ArtifactReadService missions = missionFixture();

        assertThatThrownBy(() -> missions.item(MISSIONS, "mission_type", "software-dev"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.KIND_NOT_FOUND));
        assertThat(codeOf(() -> missions.item(ABSENT, "mission_type", "software-dev")))
                .isEqualTo(RefusalCode.PACK_NOT_FOUND);
        assertThat(codeOf(() -> missions.item(BARE, "mission_type", "software-dev")))
                .isEqualTo(RefusalCode.PACK_UNREADABLE);
    }

    @Test
    void theReferencesOfAMissionTypeAndOfAStepContractAreReadLikeAnyListedKinds() {
        ArtifactReadService missions = missionFixture();
        // The compose is resolved over every configured pack: the pack without a pack.yaml would refuse it.
        stored.keySet().retainAll(Set.of(MISSIONS));
        edges.put(
                MISSIONS,
                List.of(
                        new PackEdge("mission_type:software-dev", "directive:GUARD", RelationType.REQUIRES),
                        new PackEdge("mission_step_contract:specify", "directive:GUARD", RelationType.REQUIRES),
                        new PackEdge("directive:GUARD", "mission_type:software-dev", RelationType.SUGGESTS)));

        assertThat(missions.references(MISSIONS, "mission_type", "software-dev").value())
                .extracting(reference -> reference.urn().toString(), reference -> reference.relation())
                .containsExactly(tuple("directive:GUARD", RelationType.REQUIRES));
        assertThat(missions.references(MISSIONS, "mission_step_contract", "specify")
                        .value())
                .extracting(reference -> reference.urn().toString(), reference -> reference.relation())
                .containsExactly(tuple("directive:GUARD", RelationType.REQUIRES));
        assertThat(codeOf(() -> missions.references(MISSIONS, "mission_type", "nope")))
                .isEqualTo(RefusalCode.MISSION_TYPE_NOT_FOUND);
        assertThat(codeOf(() -> missions.references(MISSIONS, "mission_step_contract", "nope")))
                .isEqualTo(RefusalCode.MISSION_STEP_CONTRACT_NOT_FOUND);
        assertThat(codeOf(() -> missions.references(ABSENT, "mission_type", "nope")))
                .isEqualTo(RefusalCode.PACK_NOT_FOUND);
    }

    @Test
    void theListedKindsAreTheOnesTheListNamesTheServedOnesThenTheMissionKinds() {
        assertThat(ArtifactReadService.listed()).containsExactlyElementsOf(ArtifactReadService.LISTED);
        assertThat(ArtifactReadService.listed()).containsAll(ArtifactReadService.SERVED);
        assertThat(ArtifactReadService.listed()).contains(ArtifactKind.AGENT_PROFILE);
        assertThat(ArtifactReadService.listed()).isUnmodifiable();
    }

    @Test
    void anAgentProfileIsListedAndFiltered() {
        assertThat(service.list(PACK, Optional.of(ArtifactKind.AGENT_PROFILE), Optional.empty(), 50)
                        .value()
                        .items())
                .extracting(ArtifactReadService.Summary::position, ArtifactReadService.Summary::title)
                .containsExactly(tuple(position(ArtifactKind.AGENT_PROFILE, "jenny"), "Name of jenny"));
    }

    @Test
    void anAgentProfileIsNotRenderedByTheTemplatedItemReadAndIsKindNotFoundAfterThePack() {
        assertThatThrownBy(() -> service.item(PACK, "agent_profile", "jenny"))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.KIND_NOT_FOUND);
                    assertThat(refused.getMessage()).contains("/packs/pack-a/artifacts/agent_profile/jenny");
                });
        assertThat(codeOf(() -> service.item(ABSENT, "agent_profile", "jenny"))).isEqualTo(RefusalCode.PACK_NOT_FOUND);
    }

    // ---- the asset and the skill: listed, answered at their literal paths (#105, #107) --------------------------

    @Test
    void assetsAreListedBeforeSkillsAnAssetTitledByItsTitleOrItsId() {
        assertThat(service.list(HOLDER, Optional.empty(), Optional.empty(), 50)
                        .value()
                        .items())
                .extracting(ArtifactReadService.Summary::position, ArtifactReadService.Summary::title)
                .containsExactly(
                        tuple(position(ArtifactKind.ASSET, "logo"), "logo"),
                        tuple(position(ArtifactKind.ASSET, "run"), "Run the lint"),
                        tuple(position(ArtifactKind.SKILL, "land-pr"), "Land a PR"));
    }

    @Test
    void anAssetFilterListsTheAssetsAndASkillFilterTheSkills() {
        assertThat(service.list(HOLDER, Optional.of(ArtifactKind.ASSET), Optional.empty(), 50)
                        .value()
                        .items())
                .extracting(ArtifactReadService.Summary::position)
                .containsExactly(position(ArtifactKind.ASSET, "logo"), position(ArtifactKind.ASSET, "run"));
        assertThat(service.list(HOLDER, Optional.of(ArtifactKind.SKILL), Optional.empty(), 50)
                        .value()
                        .items())
                .extracting(ArtifactReadService.Summary::position)
                .containsExactly(position(ArtifactKind.SKILL, "land-pr"));
    }

    @ParameterizedTest
    @CsvSource({"asset, run", "skill, land-pr", "asset, ghost", "skill, ghost"})
    void anAssetOrASkillIsNotRenderedByTheTemplatedItemReadAndIsKindNotFoundNamingItsLiteralPath(
            String kind, String id) {
        assertThatThrownBy(() -> service.item(HOLDER, kind, id))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.KIND_NOT_FOUND);
                    assertThat(refused.getMessage())
                            .contains("read it at /packs/holder/artifacts/" + kind + "/" + id + ".");
                });
        assertThat(codeOf(() -> service.item(ABSENT, kind, id))).isEqualTo(RefusalCode.PACK_NOT_FOUND);
    }

    @Test
    void anAssetOrASkillIsArtifactNotFoundWhenAbsent() {
        assertThat(ArtifactReadService.absentCode(ArtifactKind.ASSET)).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
        assertThat(ArtifactReadService.absentCode(ArtifactKind.SKILL)).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
        ArtifactReadService withEdges = referenceFixture(() -> D1);
        assertThat(codeOf(() -> withEdges.references(PACK, "asset", "ghost")))
                .isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
        assertThat(codeOf(() -> withEdges.references(PACK, "skill", "ghost")))
                .isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
    }

    @Test
    void anAgentProfileIsArtifactNotFoundWhenAbsent() {
        assertThat(ArtifactReadService.absentCode(ArtifactKind.AGENT_PROFILE))
                .isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
    }

    @Test
    void theReferencesOfAnAgentProfileAreItsWrittenEdges() {
        ArtifactReadService withEdges = referenceFixture(() -> D1);
        edges.put(
                PACK,
                List.of(
                        new PackEdge("agent_profile:ada", "directive:OWN_1", RelationType.REQUIRES),
                        new PackEdge("agent_profile:jenny", "directive:OWN_2", RelationType.REQUIRES)));

        assertThat(withEdges.references(PACK, "agent_profile", "jenny").value())
                .extracting(reference -> reference.written().toString(), reference -> reference.relation())
                .containsExactly(tuple("directive:OWN_2", RelationType.REQUIRES));
    }

    @Test
    void theReferencesOfAnAgentProfileThePackDoesNotHoldAreArtifactNotFound() {
        ArtifactReadService withEdges = referenceFixture(() -> D1);

        assertThatThrownBy(() -> withEdges.references(PACK, "agent_profile", "nobody"))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.ARTIFACT_NOT_FOUND);
                    assertThat(refused.getMessage()).isEqualTo("Pack pack-a has no agent_profile nobody.");
                });
    }

    // ---- the pack-qualified reference grammar (FR-013, FR-014, FR-015, FR-017; SC-004's shared fixture) --------

    @Test
    void theReferencesReadResolvesEachReferenceAgainstTheComposeItResolvedOnce() {
        ArtifactReadService counting = referenceFixture(() -> D1);

        List<ArtifactReadService.Reference> references =
                counting.references(PACK, "directive", "OWN_1").value();

        assertThat(references)
                .as("the edges whose source is OWN_1, bare or qualified with its own pack, in document order")
                .extracting(reference -> reference.written().toString(), reference -> reference.relation())
                .containsExactly(
                        tuple("bravo::directive:SHARED_1", RelationType.SUGGESTS),
                        tuple("directive:OWN_2", RelationType.REQUIRES));
        assertThat(references)
                .extracting(reference ->
                        reference.holder().orElseThrow().descriptor().name())
                .as("the qualified reference is held by the pack it names, the bare one by the referring pack")
                .containsExactly("bravo", "alpha");
        assertThat(composeResolutions)
                .as("the two references are resolved against one resolution of the compose")
                .hasValue(1);
    }

    @Test
    void aTargetReachedUnderTwoRelationsIsListedOnceForEachRelation() {
        // The real packs hold such pairs (a tactic that both requires and suggests one styleguide); dropping one
        // relation would hide an authored edge (ADR-0064 decision 6). The same edge written twice, from an
        // own-qualified
        // source or to an own-qualified target, is still listed once, under its first spelling.
        ArtifactReadService service = referenceFixture(() -> D1);
        edges.put(
                PACK,
                List.of(
                        new PackEdge("directive:OWN_1", "directive:OWN_2", RelationType.REQUIRES),
                        new PackEdge("directive:OWN_1", "directive:OWN_2", RelationType.SUGGESTS),
                        new PackEdge("alpha::directive:OWN_1", "directive:OWN_2", RelationType.REQUIRES),
                        new PackEdge("directive:OWN_1", "alpha::directive:OWN_2", RelationType.SUGGESTS)));

        assertThat(service.references(PACK, "directive", "OWN_1").value())
                .extracting(reference -> reference.written().toString(), reference -> reference.relation())
                .containsExactly(
                        tuple("directive:OWN_2", RelationType.REQUIRES),
                        tuple("directive:OWN_2", RelationType.SUGGESTS));
    }

    @Test
    void theReferencesReadRefusesAnUnknownKindAfterThePack() {
        ArtifactReadService counting = referenceFixture(() -> D1);

        assertThatThrownBy(() -> counting.references(PACK, "template", "x"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.KIND_NOT_FOUND));
        assertThat(codeOf(() -> counting.references(ABSENT, "template", "x"))).isEqualTo(RefusalCode.PACK_NOT_FOUND);
    }

    @Test
    void theReferencesOfAnArtifactWhoseInputsChangeWhileTheyAreReadAreStale() {
        AtomicInteger digests = new AtomicInteger();
        ArtifactReadService changing = referenceFixture(() -> digests.incrementAndGet() == 1 ? D1 : D2);

        assertThatThrownBy(() -> changing.references(PACK, "directive", "OWN_1"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.STALE_INPUT));
    }

    // ---- fixtures ---------------------------------------------------------------------------------------------

    private static ArtifactPosition position(ArtifactKind kind, String id) {
        return new ArtifactPosition(kind, id);
    }

    private static RefusalCode codeOf(Runnable read) {
        try {
            read.run();
        } catch (CharterRefusal refused) {
            return refused.code();
        }
        throw new AssertionError("the read was not refused");
    }

    private static PackDescriptor descriptor(PackIdentifier pack) {
        return new PackDescriptor(
                pack.value(), "1.0.0", Optional.empty(), pack.value(), Optional.empty(), Optional.empty());
    }

    /** A descriptor named {@code name} that depends on the given packs (alpha relies on bravo in the grammar fixture). */
    private static PackDescriptor descriptorNamed(PackIdentifier pack, String name, PackIdentifier... dependsOn) {
        return new PackDescriptor(
                pack.value(),
                "1.0.0",
                Optional.empty(),
                name,
                Optional.of(PackLevel.ORG),
                Optional.empty(),
                PackPaths.none(),
                java.util.Arrays.stream(dependsOn)
                        .map(target -> new PackDependency(target.value(), ">=1.0"))
                        .toList());
    }

    /**
     * The shared two-pack fixture of the grammar (SC-004), over the given digest: {@code pack-a} is alpha and holds
     * directives {@code OWN_1} and {@code OWN_2}, and its graph document holds three edges: {@code OWN_1} suggests
     * bravo's {@code SHARED_1} (qualified), {@code alpha::OWN_1} (its own pack's qualifier) requires {@code OWN_2}, and
     * {@code OWN_2} requires {@code OWN_1}, which is not {@code OWN_1}'s to list; {@code pack-b} is bravo and holds directive {@code SHARED_1}, and the two form one
     * compose closure — nothing else is configured, so the closure is exactly the two of them.
     */
    private ArtifactReadService referenceFixture(PackSources sources) {
        stored.clear();
        edges.clear();
        edges.put(
                PACK,
                List.of(
                        new PackEdge("directive:OWN_1", "bravo::directive:SHARED_1", RelationType.SUGGESTS),
                        new PackEdge("alpha::directive:OWN_1", "directive:OWN_2", RelationType.REQUIRES),
                        new PackEdge("directive:OWN_2", "directive:OWN_1", RelationType.REQUIRES)));
        stored.put(
                PACK,
                Pack.builder()
                        .id(PACK)
                        .descriptor(descriptorNamed(PACK, "alpha", SIBLING))
                        .directive(directiveIn(PACK, "OWN_1"))
                        .directive(directiveIn(PACK, "OWN_2"))
                        .artifact(profile("ada"))
                        .artifact(profile("jenny"))
                        .build());
        stored.put(
                SIBLING,
                Pack.builder()
                        .id(SIBLING)
                        .descriptor(descriptorNamed(SIBLING, "bravo"))
                        .directive(directiveIn(SIBLING, "SHARED_1"))
                        .build());
        return serviceDigesting(sources);
    }

    /**
     * The mission kinds' fixture: pack {@code missions} holds directive GUARD, the mission types software-dev (steps
     * specify and plan) and documentation, and three step contracts: specify (software-dev's specify, delegating to
     * directives GUARD and NOPE and to a procedure), orphan (action gather of research, a type the pack does not hold)
     * and unbound (action deploy of software-dev, a step the type does not declare). Stored out of order.
     */
    private ArtifactReadService missionFixture() {
        stored.put(
                MISSIONS,
                Pack.builder()
                        .id(MISSIONS)
                        .descriptor(descriptor(MISSIONS))
                        .artifact(stepContract(
                                "unbound", "deploy", "software-dev", new ContractStepSpec("first", Optional.empty())))
                        .artifact(new MissionTypeStub(MISSIONS, "software-dev", "Software Development"))
                        .artifact(stepContract(
                                "specify",
                                "specify",
                                "software-dev",
                                new ContractStepSpec(
                                        "capture",
                                        Optional.of(
                                                new StepDelegation(ArtifactKind.DIRECTIVE, List.of("GUARD", "NOPE")))),
                                new ContractStepSpec(
                                        "map",
                                        Optional.of(new StepDelegation(
                                                ArtifactKind.PROCEDURE, List.of("example-mapping-workshop"))))))
                        .artifact(stepContract(
                                "orphan", "gather", "research", new ContractStepSpec("first", Optional.empty())))
                        .artifact(new MissionTypeStub(MISSIONS, "documentation", "Documentation"))
                        .directive(directiveIn(MISSIONS, "GUARD"))
                        .build());
        stepIds.put("software-dev", Set.of("specify", "plan"));
        stepIds.put("documentation", Set.of("discover"));
        return service;
    }

    private record ContractStepSpec(String id, Optional<StepDelegation> delegatesTo) {}

    private static MissionStepContract stepContract(
            String id, String action, String mission, ContractStepSpec... steps) {
        MissionStepContract.Builder builder = MissionStepContract.builder()
                .id(new MissionStepContractIdentifier(id))
                .packId(MISSIONS)
                .schemaVersion("1.0")
                .action(action)
                .mission(mission);
        for (ContractStepSpec step : steps) {
            builder.step(new ContractStep(
                    step.id(),
                    "Description of " + step.id(),
                    Optional.empty(),
                    List.of(),
                    step.delegatesTo(),
                    Optional.empty()));
        }
        return builder.build();
    }

    /**
     * A mission type as the packs rings hold it: an artifact of kind {@code mission_type}, labelled by its title. The
     * packs rings never name the missions domain, so the fixture does not either; its steps come from {@link #stepIds}.
     */
    private record MissionTypeStub(PackIdentifier packId, String localId, String label) implements CharterArtifact {

        @Override
        public ArtifactKind kind() {
            return ArtifactKind.MISSION_TYPE;
        }

        @Override
        public Optional<String> schemaVersion() {
            return Optional.of("1.0");
        }

        @Override
        public Optional<String> description() {
            return Optional.of("Description of " + localId);
        }
    }

    private static Directive directiveIn(PackIdentifier pack, String id) {
        return directiveBuilderIn(pack, id).build();
    }

    private static Directive.Builder directiveBuilderIn(PackIdentifier pack, String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(pack)
                .schemaVersion("1.0")
                .title("Title of " + id)
                .intent("Intent of " + id)
                .enforcement(Enforcement.REQUIRED);
    }

    private static Asset asset(String id, String path, @Nullable String title) {
        return Asset.builder()
                .id(new AssetIdentifier(id))
                .packId(HOLDER)
                .mime("application/octet-stream")
                .path(path)
                .title(title)
                .build();
    }

    private static Skill skill(String id) {
        return Skill.builder()
                .id(new SkillIdentifier(id))
                .packId(HOLDER)
                .schemaVersion("1.0")
                .title("Land a PR")
                .description("Use when a pull request needs landing.")
                .form(SkillForm.PROMPT)
                .bodyPath(Optional.of(id + ".skill.md"))
                .body(Optional.of("Goal: land it.\n"))
                .build();
    }

    private static AgentProfile profile(String id) {
        return AgentProfile.builder()
                .id(new AgentProfileIdentifier(id))
                .packId(PACK)
                .name("Name of " + id)
                .role("implementer")
                .purpose("Purpose of " + id)
                .specialization(new Specialization("Focus", Optional.empty(), Optional.empty(), Optional.empty()))
                .build();
    }

    private static Tactic tactic(String id, String name) {
        return Tactic.builder()
                .id(new TacticIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .name(name)
                .step(new TacticStep("Only step", Optional.empty(), List.of()))
                .build();
    }

    private static Directive directive(String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Title of " + id)
                .intent("Intent of " + id)
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    private static Styleguide styleguide(String id) {
        return Styleguide.builder()
                .id(new StyleguideIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Title of " + id)
                .scope(StyleguideScope.CODE)
                .principle("Prefer clarity", "Prefer clarity, in one sentence.")
                .build();
    }

    private static Paradigm paradigm(String id) {
        return Paradigm.builder()
                .id(new ParadigmIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .name("Name of " + id)
                .summary("Summary of " + id)
                .build();
    }

    private static Procedure procedure(String id) {
        return Procedure.builder()
                .id(new ProcedureIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .title("Title of " + id)
                .description("What " + id + " is.")
                .entryCondition("ready")
                .exitCondition("done")
                .step(new ProcedureStep(
                        "Do it", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
                .build();
    }

    private static Toolguide toolguide(String id) {
        return Toolguide.builder()
                .id(new ToolguideIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .tool(id)
                .title("How to use " + id)
                .guide(new Guide(Map.of(GuideBlock.OVERVIEW, "How to use " + id + "."), List.of()))
                .summary("Using " + id + ".")
                .build();
    }

    /** Every port the generic and the per-kind reads ask, over the one map of packs; an unknown pack is not found. */
    private final class InMemoryStore
            implements PackRepository,
                    TacticRepository,
                    DirectiveRepository,
                    StyleguideRepository,
                    ParadigmRepository,
                    ToolguideRepository,
                    ProcedureRepository,
                    PackCatalogRepository,
                    PackDescriptorRepository,
                    PackArtifactRepository {

        /** The aggregate needs pack.yaml: {@link #BARE} has none, so it cannot be assembled. */
        @Override
        public Pack load(PackIdentifier pack) {
            if (pack.equals(BARE) || aggregateUnreadable) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack " + pack.value() + " cannot be assembled");
            }
            return contents(pack);
        }

        /** A pack's files, as the per-kind loads read them one by one, with or without a pack.yaml. */
        private Pack contents(PackIdentifier pack) {
            Pack found = stored.get(pack);
            if (found == null) {
                throw new CharterRefusal(RefusalCode.PACK_NOT_FOUND, "Pack " + pack.value() + " was not found.");
            }
            return found;
        }

        @Override
        public List<ConfiguredPack> configured() {
            composeResolutions.incrementAndGet();
            return stored.keySet().stream()
                    .map(id -> new ConfiguredPack(id, PackLevel.ORG))
                    .toList();
        }

        @Override
        public PackDescriptor get(PackIdentifier pack) {
            return find(pack).orElseThrow(() -> new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack.yaml"));
        }

        @Override
        public Optional<PackDescriptor> find(PackIdentifier pack) {
            onFind.run();
            return pack.equals(BARE)
                    ? Optional.empty()
                    : Optional.of(contents(pack).descriptor());
        }

        @Override
        public void save(PackIdentifier pack, PackDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }

        /**
         * No pack of the fixture refines an artifact, so the compose holds no sibling contest; a pack holds the
         * artifacts of its aggregate, which the compose reads once per pack.
         */
        @Override
        public PackCompositionFacts compositionFacts(PackIdentifier pack) {
            return new PackCompositionFacts(
                    List.of(),
                    contents(pack).artifacts().stream()
                            .map(artifact -> artifact.urn().toString())
                            .toList(),
                    List.of());
        }

        @Override
        public Optional<Tactic> load(PackIdentifier pack, TacticIdentifier tactic) {
            return contents(pack).tactics().stream()
                    .filter(t -> t.id().equals(tactic))
                    .findFirst();
        }

        @Override
        public Optional<Directive> load(PackIdentifier pack, DirectiveIdentifier directive) {
            return contents(pack).directives().stream()
                    .filter(d -> d.id().equals(directive))
                    .findFirst();
        }

        @Override
        public Optional<Styleguide> load(PackIdentifier pack, StyleguideIdentifier styleguide) {
            return contents(pack).styleguides().stream()
                    .filter(s -> s.id().equals(styleguide))
                    .findFirst();
        }

        @Override
        public Optional<Paradigm> load(PackIdentifier pack, ParadigmIdentifier paradigm) {
            return contents(pack).paradigms().stream()
                    .filter(p -> p.id().equals(paradigm))
                    .findFirst();
        }

        @Override
        public Optional<Toolguide> load(PackIdentifier pack, ToolguideIdentifier toolguide) {
            return contents(pack).toolguides().stream()
                    .filter(t -> t.id().equals(toolguide))
                    .findFirst();
        }

        @Override
        public Optional<Procedure> load(PackIdentifier pack, ProcedureIdentifier procedure) {
            return contents(pack).artifacts(ArtifactKind.PROCEDURE, Procedure.class).stream()
                    .filter(p -> p.id().equals(procedure))
                    .findFirst();
        }

        @Override
        public void save(Tactic tactic) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(Directive directive) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(Styleguide styleguide) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(Paradigm paradigm) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void save(Toolguide toolguide) {
            throw new UnsupportedOperationException();
        }
    }
}
