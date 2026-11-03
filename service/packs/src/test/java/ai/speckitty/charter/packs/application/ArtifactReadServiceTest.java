package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.Directive;
import ai.speckitty.charter.packs.domain.DirectiveIdentifier;
import ai.speckitty.charter.packs.domain.DirectiveRepository;
import ai.speckitty.charter.packs.domain.Enforcement;
import ai.speckitty.charter.packs.domain.Guide;
import ai.speckitty.charter.packs.domain.GuideBlock;
import ai.speckitty.charter.packs.domain.Pack;
import ai.speckitty.charter.packs.domain.PackCatalogRepository;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import ai.speckitty.charter.packs.domain.PackRefinementRepository;
import ai.speckitty.charter.packs.domain.PackRepository;
import ai.speckitty.charter.packs.domain.PackSources;
import ai.speckitty.charter.packs.domain.Paradigm;
import ai.speckitty.charter.packs.domain.ParadigmIdentifier;
import ai.speckitty.charter.packs.domain.ParadigmRepository;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
                    .paradigm(paradigm("layered"))
                    .tactic(tactic("alpha", "Alpha"))
                    .directive(directive("GUARD"))
                    .styleguide(styleguide("java-style"))
                    .tactic(tactic("Zeta", "Zeta"))
                    .build(),
            EMPTY,
            Pack.builder().id(EMPTY).descriptor(descriptor(EMPTY)).build(),
            BARE,
            Pack.builder().id(BARE).descriptor(descriptor(BARE)).build()));

    private final AtomicInteger digests = new AtomicInteger();
    /** What a descriptor lookup does to the inputs as it answers; nothing, unless a test plants a change. */
    private Runnable onFind = () -> {};

    private final ArtifactReadService service = serviceDigesting(() -> {
        digests.incrementAndGet();
        return D1;
    });

    private ArtifactReadService serviceDigesting(PackSources sources) {
        InMemoryStore store = new InMemoryStore();
        ConsistentRead read = new ConsistentRead(sources);
        PackRevisions revisions = pack -> REVISION;
        return new ArtifactReadService(
                store,
                store,
                store,
                read,
                new TacticReadService(store, store, read),
                new DirectiveReadService(store, store, read, revisions),
                new StyleguideReadService(store, store, read, revisions),
                new ParadigmReadService(store, store, read, revisions),
                new ToolguideReadService(store, store, read, revisions),
                new CompositeComponentResolver(
                        new DirectiveReadService(store, store, read, revisions),
                        new StyleguideReadService(store, store, read, revisions),
                        new ParadigmReadService(store, store, read, revisions),
                        new ToolguideReadService(store, store, read, revisions),
                        new TacticReadService(store, store, read),
                        new OfferingReadService(store, store, store, read)));
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
                        position(ArtifactKind.TOOLGUIDE, "git"));
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void aSummaryTitleIsTheNameOfATacticOrParadigmAndTheTitleOfTheOthers() {
        assertThat(service.list(PACK, Optional.empty(), Optional.empty(), 50)
                        .value()
                        .items())
                .extracting(ArtifactReadService.Summary::title)
                .containsExactly(
                        "Zeta", "Alpha", "Title of GUARD", "Title of java-style", "Name of layered", "How to use git");
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
        assertThatThrownBy(() -> position(ArtifactKind.PROCEDURE, "x")).isInstanceOf(IllegalArgumentException.class);
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
        assertThat(service.list(PACK, Optional.empty(), Optional.empty(), 6)
                        .value()
                        .hasMore())
                .isFalse();
        assertThat(service.list(PACK, Optional.empty(), Optional.empty(), 5)
                        .value()
                        .hasMore())
                .isTrue();
        assertThat(service.list(PACK, Optional.empty(), Optional.empty(), 5)
                        .value()
                        .items())
                .hasSize(5);
        assertThat(service.list(PACK, Optional.empty(), Optional.of(position(ArtifactKind.TOOLGUIDE, "git")), 5)
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
        assertThatThrownBy(() -> service.list(PACK, Optional.of(ArtifactKind.PROCEDURE), Optional.empty(), 50))
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
    void anUnconfiguredPackIsNotFoundOnTheListWithTheStorageRefusalAsCause() {
        assertThatThrownBy(() -> service.list(ABSENT, Optional.empty(), Optional.empty(), 50))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.PACK_NOT_FOUND);
                    assertThat(refused.getMessage()).isEqualTo("Pack absent was not found.");
                    assertThat(refused.getCause())
                            .isInstanceOfSatisfying(
                                    CharterRefusal.class,
                                    cause -> assertThat(cause.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
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
    void anUnconfiguredPackIsStillNotFoundOnTheItemOfAServedKind() {
        assertThat(codeOf(() -> service.item(ABSENT, "tactic", "rehearse"))).isEqualTo(RefusalCode.PACK_NOT_FOUND);
        assertThat(codeOf(() -> service.item(ABSENT, "directive", "GUARD"))).isEqualTo(RefusalCode.PACK_NOT_FOUND);
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
    void aTacticItemHasNoRevision() {
        var verified = service.item(PACK, "tactic", "alpha");

        assertThat(verified.value()).isInstanceOfSatisfying(ArtifactReadService.Item.OfTactic.class, item -> {
            assertThat(item.artifact().id()).isEqualTo(new TacticIdentifier("alpha"));
            assertThat(item.revision()).isEmpty();
        });
        assertThat(verified.fingerprint()).isEqualTo(D1);
    }

    @Test
    void eachWritableKindItemCarriesItsRevision() {
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
        for (String[] item : new String[][] {
            {"directive", "GUARD"}, {"styleguide", "java-style"}, {"paradigm", "layered"}, {"toolguide", "git"}
        }) {
            assertThat(service.item(PACK, item[0], item[1]).value().revision())
                    .as(item[0])
                    .contains(REVISION);
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
    @ValueSource(strings = {"procedure", "bogus", "Tactic", "tactics", ""})
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
    void theServedKindsAreTheFiveInTheirOrder() {
        assertThat(ArtifactReadService.SERVED)
                .containsExactly(
                        ArtifactKind.TACTIC,
                        ArtifactKind.DIRECTIVE,
                        ArtifactKind.STYLEGUIDE,
                        ArtifactKind.PARADIGM,
                        ArtifactKind.TOOLGUIDE);
        assertThat(ArtifactReadService.servedKind("toolguide")).contains(ArtifactKind.TOOLGUIDE);
        assertThat(ArtifactReadService.servedKind("procedure")).isEmpty();
        assertThat(ArtifactReadService.servedKind("TACTIC")).isEmpty();
    }

    // ---- the pack-qualified reference grammar (FR-013, FR-014, FR-015, FR-017; SC-004's shared fixture) --------

    @Test
    void aPackQualifiedReferenceReadsTheArtifactOfTheNamedPack() {
        ConsistentRead.Verified<ArtifactReadService.Item> read =
                referenceFixture(() -> D1).referenced(PACK, "bravo::directive:SHARED_1");

        assertThat(read.value()).isInstanceOf(ArtifactReadService.Item.OfDirective.class);
        assertThat(read.value().artifact().localId()).isEqualTo("SHARED_1");
        assertThat(read.value().artifact().packId()).isEqualTo(SIBLING);
        assertThat(read.value().revision()).isPresent();
        assertThat(read.fingerprint()).isEqualTo(D1);
    }

    @Test
    void aPackQualifiedReferenceToTheReferringPackItselfReadsItsOwnArtifact() {
        ConsistentRead.Verified<ArtifactReadService.Item> read =
                referenceFixture(() -> D1).referenced(PACK, "alpha::directive:OWN_1");

        assertThat(read.value().artifact().localId()).isEqualTo("OWN_1");
        assertThat(read.value().artifact().packId()).isEqualTo(PACK);
    }

    @Test
    void aBareReferenceReadsTheArtifactOfTheReferringPack() {
        ConsistentRead.Verified<ArtifactReadService.Item> read =
                referenceFixture(() -> D1).referenced(PACK, "directive:OWN_1");

        assertThat(read.value()).isInstanceOf(ArtifactReadService.Item.OfDirective.class);
        assertThat(read.value().artifact().localId()).isEqualTo("OWN_1");
        assertThat(read.value().artifact().packId()).isEqualTo(PACK);
        assertThat(read.fingerprint()).isEqualTo(D1);
    }

    @Test
    void aBareReferenceThatResolvesOnlyInAnotherPackIsRefusedNamingTheHoldingPack() {
        assertThatThrownBy(() -> referenceFixture(() -> D1).referenced(PACK, "directive:SHARED_1"))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.PACK_REFERENCE_UNQUALIFIED);
                    assertThat(refused.getMessage()).contains("bravo").contains("pack-qualified");
                });
    }

    @Test
    void aQualifiedReferenceOutsideTheClosureIsRefusedNamingTheMissingPack() {
        assertThatThrownBy(() -> referenceFixture(() -> D1).referenced(PACK, "charlie::directive:SHARED_1"))
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.PACK_REFERENCE_PACK_MISSING);
                    assertThat(refused.getMessage()).contains("charlie").contains("does not hold");
                });
    }

    @Test
    void aReferenceThatResolvesToNoArtifactIsTheNotFoundOfItsKind() {
        // FR-015's code is the "it exists, but not here" case: a bare id that resolves nowhere keeps the kind's
        // not-found, exactly as an unknown id of the item read does.
        assertThatThrownBy(() -> referenceFixture(() -> D1).referenced(PACK, "directive:NOWHERE"))
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.DIRECTIVE_NOT_FOUND));
    }

    @Test
    void aReferenceWhoseInputsChangeWhileItIsReadIsStale() {
        AtomicInteger digests = new AtomicInteger();
        ArtifactReadService changing = referenceFixture(() -> digests.incrementAndGet() == 1 ? D1 : D2);

        assertThatThrownBy(() -> changing.referenced(PACK, "bravo::directive:SHARED_1"))
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
     * directive {@code OWN_1}, {@code pack-b} is bravo and holds directive {@code SHARED_1}, and the two form one
     * compose closure — nothing else is configured, so the closure is exactly the two of them.
     */
    private ArtifactReadService referenceFixture(PackSources sources) {
        stored.clear();
        stored.put(
                PACK,
                Pack.builder()
                        .id(PACK)
                        .descriptor(descriptorNamed(PACK, "alpha", SIBLING))
                        .directive(directiveIn(PACK, "OWN_1"))
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

    private static Directive directiveIn(PackIdentifier pack, String id) {
        return Directive.builder()
                .id(new DirectiveIdentifier(id))
                .packId(pack)
                .schemaVersion("1.0")
                .title("Title of " + id)
                .intent("Intent of " + id)
                .enforcement(Enforcement.REQUIRED)
                .build();
    }

    private static Tactic tactic(String id, String name) {
        return Tactic.builder()
                .id(new TacticIdentifier(id))
                .packId(PACK)
                .schemaVersion("1.0")
                .name(name)
                .step(new TacticStep("Only step", Optional.empty(), List.of(), List.of()))
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
                .principle("Prefer clarity")
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

    /** Every port the generic and the per-kind reads ask, over the one map of packs; an unknown pack is unreadable. */
    private final class InMemoryStore
            implements PackRepository,
                    TacticRepository,
                    DirectiveRepository,
                    StyleguideRepository,
                    ParadigmRepository,
                    ToolguideRepository,
                    PackCatalogRepository,
                    PackDescriptorRepository,
                    PackRefinementRepository {

        /** The aggregate needs pack.yaml: {@link #BARE} has none, so it cannot be assembled. */
        @Override
        public Pack load(PackIdentifier pack) {
            if (pack.equals(BARE)) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack bare has no pack.yaml");
            }
            return contents(pack);
        }

        /** A pack's files, as the per-kind loads read them one by one, with or without a pack.yaml. */
        private Pack contents(PackIdentifier pack) {
            Pack found = stored.get(pack);
            if (found == null) {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack " + pack.value());
            }
            return found;
        }

        @Override
        public List<ConfiguredPack> configured() {
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

        /** No pack of the fixture refines an artifact, so the compose holds no sibling contest. */
        @Override
        public List<String> refinedArtifacts(PackIdentifier pack) {
            return List.of();
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
