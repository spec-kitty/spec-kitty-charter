package ai.speckitty.charter.packs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackOverride;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.domain.RelationType;
import ai.speckitty.charter.kernel.domain.Relationship;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PackArtifactRepository;
import ai.speckitty.charter.packs.domain.PackCompositionFacts;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.Failure;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackDescriptorRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The offering over in-memory ports: the merge order (configured level, then id in code-point order), the configured
 * level over the declared one, the descriptor's name and version, and the refusals of one verified read.
 */
class OfferingReadServiceTest {

    private static final String D1 = "sha256:" + "1".repeat(64);
    private static final String D2 = "sha256:" + "2".repeat(64);

    private final Map<String, Optional<PackDescriptor>> descriptors = new HashMap<>();
    private final Map<String, CharterRefusal> unreadable = new HashMap<>();
    private final Map<String, List<String>> defined = new HashMap<>();
    private final Map<String, List<String>> written = new HashMap<>();
    private final Map<String, List<Relationship>> edges = new HashMap<>();
    private final Map<String, CharterRefusal> factsUnreadable = new HashMap<>();
    private final List<String> factsRead = new ArrayList<>();

    private ConfiguredPack configured(String id, PackLevel level) {
        descriptors.putIfAbsent(id, Optional.of(descriptor(id, Optional.empty())));
        return new ConfiguredPack(new PackIdentifier(id), level);
    }

    private static PackDescriptor descriptor(String id, Optional<PackLevel> declared) {
        return new PackDescriptor(id, "1.0." + id.length(), Optional.empty(), id + " pack", declared, Optional.empty());
    }

    private OfferingReadService service(List<ConfiguredPack> packs, ConsistentRead read) {
        return new OfferingReadService(() -> packs, new InMemoryDescriptors(), new InMemoryRefinements(), read);
    }

    private OfferingReadService service(List<ConfiguredPack> packs) {
        return service(packs, new ConsistentRead(() -> D1));
    }

    @Test
    void theOrderIsByConfiguredLevelThenByIdInCodePointOrder() {
        // Configured out of order: project first, and the lower-case id before the upper-case one at every level.
        List<ConfiguredPack> packs = List.of(
                configured("charlie", PackLevel.PROJECT),
                configured("Kilo", PackLevel.PROJECT),
                configured("bravo", PackLevel.ORG),
                configured("Omega", PackLevel.ORG),
                configured("alpha", PackLevel.BUILT_IN),
                configured("Zeta", PackLevel.BUILT_IN));

        assertThat(service(packs).offering().value())
                .extracting(entry -> entry.id().value() + "@" + entry.level().token())
                .containsExactly(
                        "Zeta@built-in", "alpha@built-in", "Omega@org", "bravo@org", "Kilo@project", "charlie@project");
    }

    @Test
    void theIdOrderComparesCodePointsNotUtf16Units() {
        // U+1F600 is a surrogate pair, which String.compareTo puts before U+FB01; code-point order puts it after.
        List<ConfiguredPack> packs = List.of(configured("😀", PackLevel.ORG), configured("ﬁ", PackLevel.ORG));

        assertThat(service(packs).offering().value())
                .extracting(entry -> entry.id().value())
                .containsExactly("ﬁ", "😀");
    }

    @Test
    void anEntryCarriesTheConfiguredLevelAndTheDescriptorsNameAndVersion() {
        descriptors.put("local", Optional.of(descriptor("local", Optional.of(PackLevel.ORG))));

        OfferingReadService.OfferingEntry entry = service(List.of(configured("local", PackLevel.PROJECT)))
                .offering()
                .value()
                .getFirst();

        assertThat(entry.id()).isEqualTo(new PackIdentifier("local"));
        assertThat(entry.level())
                .as("the configured level, not the declared one")
                .isEqualTo(PackLevel.PROJECT);
        assertThat(entry.name()).isEqualTo("local pack");
        assertThat(entry.version()).isEqualTo("1.0.5");
    }

    @Test
    void aPackWithoutADescriptorRefusesTheWholeOfferingAsUnreadable() {
        // Operator ruling of 2026-10-31: a configured pack without pack.yaml is no readable canonical pack.
        descriptors.put("bare", Optional.empty());
        List<ConfiguredPack> packs = List.of(configured("qa", PackLevel.ORG), configured("bare", PackLevel.ORG));

        assertThatThrownBy(() -> service(packs).offering()).isInstanceOfSatisfying(CharterRefusal.class, refused -> {
            assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
            assertThat(refused.getMessage()).isEqualTo("pack bare has no pack.yaml");
        });
    }

    @Test
    void anUnreadableDescriptorRefusesTheWholeOffering() {
        unreadable.put("broken", new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack.yaml of broken cannot be read"));
        List<ConfiguredPack> packs = List.of(configured("qa", PackLevel.ORG), configured("broken", PackLevel.ORG));

        assertThatThrownBy(() -> service(packs).offering())
                .isInstanceOfSatisfying(
                        CharterRefusal.class,
                        refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE));
    }

    @Test
    void theAnswerCarriesTheFingerprintItVerified() {
        AtomicInteger digests = new AtomicInteger();
        ConsistentRead read = new ConsistentRead(() -> {
            digests.incrementAndGet();
            return D1;
        });

        ConsistentRead.Verified<List<OfferingReadService.OfferingEntry>> verified =
                service(List.of(configured("qa", PackLevel.ORG)), read).offering();

        assertThat(verified.fingerprint()).isEqualTo(D1);
        assertThat(digests.get()).as("one verified read: two digests").isEqualTo(2);
    }

    @Test
    void anEmptyCatalogIsAnEmptyOffering() {
        assertThat(service(List.of()).offering().value()).isEmpty();
    }

    @Test
    void inputsThatChangeWhileTheOfferingIsReadAreStale() {
        AtomicInteger count = new AtomicInteger();
        ConsistentRead drifting = new ConsistentRead(() -> count.incrementAndGet() == 1 ? D1 : D2);

        assertThatThrownBy(() -> service(List.of(configured("qa", PackLevel.ORG)), drifting)
                        .offering())
                .isInstanceOfSatisfying(CharterRefusal.class, refused -> {
                    assertThat(refused.code()).isEqualTo(RefusalCode.STALE_INPUT);
                    assertThat(refused.getMessage())
                            .contains("The inputs changed while the offering was read; read it again.");
                });
    }

    /**
     * A bare reference is held by the pack that holds the artifact it names, whatever the kind: a procedure, which no
     * read path of the service serves, is refused when it resolves only in another pack, and resolves when the
     * referring pack holds it (the control, same shape).
     */
    @Test
    void aBareReferenceToAKindOutsideTheServedFiveIsRefusedWhenOnlyAnotherPackHoldsIt() {
        List<ConfiguredPack> packs = List.of(configured("alpha", PackLevel.ORG), configured("bravo", PackLevel.ORG));
        defined.put("bravo", List.of("procedure:release"));
        written.put("alpha", List.of("procedure:release"));

        assertThatThrownBy(() -> service(packs).offering()).isInstanceOfSatisfying(CharterRefusal.class, refused -> {
            assertThat(refused.code()).isEqualTo(RefusalCode.PACK_REFERENCE_UNQUALIFIED);
            assertThat(refused.getMessage()).contains("procedure:release").contains("bravo");
        });

        defined.put("alpha", List.of("procedure:release"));
        defined.remove("bravo");
        assertThat(service(packs).offering().value()).hasSize(2);
    }

    /**
     * The compose rooted at a set of configured packs (#360): only the closure of the roots is read and judged.
     * Fixture: alpha depends on beta, gamma is unrelated; each configured id is its descriptor's {@code pack_id}.
     */
    @Nested
    class Rooted {

        private List<ConfiguredPack> alphaBetaGamma() {
            return List.of(
                    declared("alpha", PackLevel.ORG, withDependencies("alpha", "beta")),
                    configured("beta", PackLevel.ORG),
                    configured("gamma", PackLevel.ORG));
        }

        @Test
        void theRootedComposeReadsFactsOnlyForTheClosure() {
            List<PackUnderResolution> composed = service(alphaBetaGamma()).composed(roots("alpha"));

            assertThat(composed)
                    .extracting(pack -> pack.configured().id().value())
                    .containsExactly("beta", "alpha");
            assertThat(factsRead).as("gamma's facts are never read").containsExactlyInAnyOrder("alpha", "beta");
        }

        @Test
        void aMissingDescriptorOutsideTheClosureIsNeverJudged() {
            List<ConfiguredPack> packs = alphaBetaGamma();
            descriptors.put("gamma", Optional.empty());

            assertThat(service(packs).composed(roots("alpha"))).hasSize(2);
            assertThat(refusalOf(() -> service(packs).composed()))
                    .satisfies(refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                    .hasMessage("pack gamma has no pack.yaml");
        }

        @Test
        void anInvalidDescriptorOutsideTheClosureIsNeverJudged() {
            List<ConfiguredPack> packs = alphaBetaGamma();
            descriptors.put(
                    "gamma",
                    Optional.of(new PackDescriptor(
                            "gamma",
                            "not a version",
                            Optional.empty(),
                            "gamma pack",
                            Optional.empty(),
                            Optional.empty())));

            assertThat(service(packs).composed(roots("alpha"))).hasSize(2);
            assertThat(refusalOf(() -> service(packs).composed()).code()).isEqualTo(RefusalCode.PACK_UNREADABLE);
        }

        @Test
        void anUnreadableDescriptorOrFactsOutsideTheClosureAreNeverRead() {
            List<ConfiguredPack> packs = alphaBetaGamma();
            unreadable.put(
                    "gamma", new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack.yaml of gamma cannot be read"));
            assertThat(service(packs).composed(roots("alpha"))).hasSize(2);

            unreadable.remove("gamma");
            factsUnreadable.put(
                    "gamma", new CharterRefusal(RefusalCode.PACK_UNREADABLE, "graph of gamma cannot be read"));
            assertThat(service(packs).composed(roots("alpha"))).hasSize(2);
            assertThat(refusalOf(() -> service(packs).composed())).hasMessage("graph of gamma cannot be read");
        }

        @Test
        void aRootWithoutAReadableDescriptorRefusesNamingIt() {
            List<ConfiguredPack> packs = alphaBetaGamma();
            descriptors.put("gamma", Optional.empty());

            assertThat(refusalOf(() -> service(packs).composed(roots("alpha", "gamma"))))
                    .satisfies(refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                    .hasMessage("pack gamma has no pack.yaml");
        }

        @Test
        void aMissingTargetOnlyAnUnreadablePackCouldDeclareRefusesAsUnreadableNamingIt() {
            List<ConfiguredPack> packs = List.of(
                    declared("alpha", PackLevel.ORG, withDependencies("alpha", "hidden")),
                    configured("gamma", PackLevel.ORG));
            descriptors.put("gamma", Optional.empty());

            assertThat(refusalOf(() -> service(packs).composed(roots("alpha"))))
                    .satisfies(refused -> assertThat(refused.code()).isEqualTo(RefusalCode.PACK_UNREADABLE))
                    .hasMessageContaining("hidden")
                    .hasMessageContaining("gamma");
        }

        @Test
        void aMissingTargetWithEveryDescriptorReadableIsAnUnknownDependency() {
            List<ConfiguredPack> packs = List.of(
                    declared("alpha", PackLevel.ORG, withDependencies("alpha", "hidden")),
                    configured("gamma", PackLevel.ORG));

            assertThat(refusalOf(() -> service(packs).composed(roots("alpha"))).code())
                    .isEqualTo(RefusalCode.PACK_DEPENDENCY_UNKNOWN);
        }

        @Test
        void twoPacksDeclaringAPackIdInTheClosureAreRefusedNamingBoth() {
            List<ConfiguredPack> packs = new ArrayList<>(alphaBetaGamma());
            packs.add(declared("twin", PackLevel.ORG, named("beta", "twin pack", List.of())));

            CharterRefusal refused = refusalOf(() -> service(packs).composed(roots("alpha")));

            assertThat(refused.code()).isEqualTo(RefusalCode.PACK_DUPLICATE_ID);
            assertThat(refused.packs()).extracting(RefusalPack::packId).containsExactlyInAnyOrder("beta", "twin");
        }

        @Test
        void aDuplicatePackIdOrNameOutsideTheClosureIsNeverJudged() {
            List<ConfiguredPack> packs = new ArrayList<>(alphaBetaGamma());
            packs.add(declared("twin", PackLevel.ORG, named("gamma", "twin pack", List.of())));
            packs.add(declared("namesake", PackLevel.ORG, named("namesake", "gamma pack", List.of())));

            assertThat(service(packs).composed(roots("alpha"))).hasSize(2);
            assertThat(refusalOf(() -> service(packs).composed()).code()).isEqualTo(RefusalCode.PACK_DUPLICATE_ID);
        }

        @Test
        void aRootNoPackIsConfiguredAsIsAnArgumentError() {
            assertThatThrownBy(() -> service(alphaBetaGamma()).composed(roots("nowhere")))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nowhere");
        }

        @Test
        void theComposeWithFactsReturnsTheFactsItReadOncePerClosurePack() {
            defined.put("beta", List.of("directive:B1"));

            OfferingReadService.ComposedClosure composed =
                    service(alphaBetaGamma()).composedWithFacts(roots("alpha"));

            assertThat(composed.packs())
                    .extracting(pack -> pack.configured().id().value())
                    .containsExactly("beta", "alpha");
            assertThat(composed.facts()).containsOnlyKeys("alpha", "beta");
            assertThat(composed.facts().get("beta").defined()).containsExactly("directive:B1");
            assertThat(factsRead).as("each closure pack once").containsExactlyInAnyOrder("alpha", "beta");
            assertThat(OfferingReadService.entriesOf(composed.packs()))
                    .extracting(entry -> entry.id().value())
                    .containsExactly("beta", "alpha");
            assertThat(OfferingReadService.entriesOf(composed.packs()).getLast().dependencies())
                    .singleElement()
                    .satisfies(edge -> assertThat(edge.to()).isEqualTo("beta"));
        }
    }

    /**
     * C37 (FR-002): the {@code enhances} target {@code action:M/s} of a step enhancement is held by a pack of the
     * enhancing pack's {@code depends_on} closure. The twin differs only in the step it names.
     */
    @Nested
    class StepEnhancementTarget {

        private List<ConfiguredPack> enhancing(String target) {
            defined.put("base", List.of("mission_type:software-dev", "action:software-dev/plan"));
            defined.put("enhancer", List.of("step_enhancement:spdd-plan"));
            written.put("enhancer", List.of("step_enhancement:spdd-plan", target));
            edges.put(
                    "enhancer",
                    List.of(new Relationship(
                            Urn.parse("step_enhancement:spdd-plan").orElseThrow(),
                            Optional.empty(),
                            PackQualifiedReference.parse(target).asUrn().orElseThrow(),
                            PackQualifiedReference.parse(target).packName(),
                            RelationType.ENHANCES,
                            Optional.empty(),
                            Optional.empty())));
            return List.of(
                    declared("base", PackLevel.ORG, named("base", "base", List.of())),
                    declared("enhancer", PackLevel.ORG, withDependencies("enhancer", "base")));
        }

        @Test
        void aStepEnhancementOfAStepTheNamedPackDoesNotHoldRefusesTheCompose() {
            List<ConfiguredPack> packs = enhancing("base::action:software-dev/nosuch");

            CharterRefusal refused = refusalOf(() -> service(packs).offering());

            assertThat(refused.code()).isEqualTo(RefusalCode.PACK_REFERENCE_ARTIFACT_MISSING);
            assertThat(refused.getMessage())
                    .contains("step_enhancement:spdd-plan")
                    .contains("action:software-dev/nosuch");
            assertThat(refused.packs()).extracting(RefusalPack::packId).containsExactly("enhancer", "base");
        }

        @Test
        void theTwinAStepEnhancementOfAHeldStepComposes() {
            List<ConfiguredPack> packs = enhancing("base::action:software-dev/plan");

            assertThat(service(packs).composed()).hasSize(2);
        }
    }

    /**
     * The offering ratchet (FR-020, SC-004): the whole compose is the compose rooted at every configured pack, and each
     * composition refusal reached through the offering is the one the unrooted resolve gives over every pack.
     */
    @Nested
    class Ratchet {

        @Test
        void theWholeComposeIsTheComposeRootedAtEveryConfiguredPack() {
            List<ConfiguredPack> packs = List.of(
                    configured("base", PackLevel.BUILT_IN),
                    declared("left", PackLevel.ORG, withDependencies("left", "base")),
                    configured("right", PackLevel.ORG),
                    declared("distribution", PackLevel.ORG, settling("distribution", "right")),
                    declared("top", PackLevel.PROJECT, withDependencies("top", "left")));
            defined.put("left", List.of("directive:DIRECTIVE_009"));
            defined.put("right", List.of("directive:DIRECTIVE_009"));

            OfferingReadService service = service(packs);

            assertThat(service.composed())
                    .isEqualTo(service.composed(allConfiguredIds(packs)))
                    .hasSize(5);
        }

        @ParameterizedTest
        @EnumSource(Failure.class)
        void eachCompositionRefusalIsTheOneTheUnrootedResolveGives(Failure failure) {
            List<ConfiguredPack> packs = provoke(failure);

            CharterRefusal served = refusalOf(() -> service(packs).offering());
            CharterRefusal unrooted = refusalOf(() -> unrootedCompose(packs));

            assertThat(served.code()).isEqualTo(failure.code());
            assertThat(served.code()).isEqualTo(unrooted.code());
            assertThat(served.getMessage()).isEqualTo(unrooted.getMessage());
            assertThat(served.packs()).isEqualTo(unrooted.packs());
        }

        /** One fixture per composition failure, the shapes the resolver tests use. */
        private List<ConfiguredPack> provoke(Failure failure) {
            return switch (failure) {
                case CYCLE ->
                    List.of(
                            declared("one", PackLevel.ORG, withDependencies("one", "two")),
                            declared("two", PackLevel.ORG, withDependencies("two", "one")));
                case UNKNOWN_DEPENDENCY -> List.of(declared("one", PackLevel.ORG, withDependencies("one", "gone")));
                case OUT_OF_RANGE ->
                    List.of(
                            configured("base", PackLevel.ORG),
                            declared(
                                    "one",
                                    PackLevel.ORG,
                                    named("one", "one pack", List.of(new PackDependency("base", ">=9")))));
                case PARENT_UNRESOLVED -> List.of(declared("one", PackLevel.ORG, withParent("one", "gone")));
                case LEVEL_VIOLATION ->
                    List.of(
                            configured("top", PackLevel.PROJECT),
                            declared("one", PackLevel.ORG, withDependencies("one", "top")));
                case NAME_CONFLICT ->
                    List.of(
                            configured("one", PackLevel.ORG),
                            declared("two", PackLevel.ORG, named("two", "one pack", List.of())));
                case DUPLICATE_ID ->
                    List.of(
                            configured("one", PackLevel.ORG),
                            declared("two", PackLevel.ORG, named("one", "two pack", List.of())));
                case SIBLING_CONFLICT -> contest();
                case REFERENCE_UNQUALIFIED -> referring("procedure:release", "procedure:release", List.of());
                case REFERENCE_PACK_MISSING -> referring("procedure:release", "nobody::procedure:release", List.of());
                case REFERENCE_ARTIFACT_MISSING ->
                    referring("procedure:release", "base::directive:DIRECTIVE_009", List.of("base"));
            };
        }

        private List<ConfiguredPack> contest() {
            defined.put("left", List.of("directive:DIRECTIVE_009"));
            defined.put("right", List.of("directive:DIRECTIVE_009"));
            return List.of(configured("left", PackLevel.ORG), configured("right", PackLevel.ORG));
        }

        /** The base pack defines one artifact, and the reader writes one reference. */
        private List<ConfiguredPack> referring(String held, String reference, List<String> readerDependsOn) {
            defined.put("base", List.of(held));
            written.put("reader", List.of(reference));
            return List.of(
                    declared("base", PackLevel.ORG, named("base", "base", List.of())),
                    declared(
                            "reader",
                            PackLevel.ORG,
                            withDependencies("reader", readerDependsOn.toArray(String[]::new))));
        }

        /** The compose as it was read before it was rooted: every pack resolved together, then each one's references. */
        private void unrootedCompose(List<ConfiguredPack> packs) {
            List<PackUnderResolution> all = packs.stream()
                    .map(pack -> new PackUnderResolution(
                            pack,
                            descriptors.get(pack.id().value()).orElseThrow(),
                            List.of(),
                            defined.getOrDefault(pack.id().value(), List.of())))
                    .toList();
            try {
                List<PackUnderResolution> closure = PackCompositionResolver.resolve(all);
                for (PackUnderResolution pack : closure) {
                    PackCompositionResolver.checkReferences(
                            closure,
                            pack.descriptor().packId(),
                            written.getOrDefault(pack.configured().id().value(), List.of()).stream()
                                    .map(text -> PackCompositionResolver.resolveReference(
                                            closure, pack.configured().id(), PackQualifiedReference.parse(text)))
                                    .toList());
                }
            } catch (PackCompositionResolver.Refusal refusal) {
                throw refusal.toCharterRefusal();
            }
        }
    }

    private ConfiguredPack declared(String id, PackLevel level, PackDescriptor descriptor) {
        descriptors.put(id, Optional.of(descriptor));
        return new ConfiguredPack(new PackIdentifier(id), level);
    }

    private static PackDescriptor named(String packId, String name, List<PackDependency> dependencies) {
        return new PackDescriptor(
                packId,
                "1.0.0",
                Optional.empty(),
                name,
                Optional.empty(),
                Optional.empty(),
                PackPaths.none(),
                dependencies);
    }

    private static PackDescriptor withDependencies(String packId, String... targets) {
        return named(
                packId,
                packId + " pack",
                Arrays.stream(targets)
                        .map(target -> new PackDependency(target, ">=1.0"))
                        .toList());
    }

    private static PackDescriptor withParent(String packId, String parent) {
        return new PackDescriptor(
                packId, "1.0.0", Optional.of(parent), packId + " pack", Optional.empty(), Optional.empty());
    }

    private static PackDescriptor settling(String packId, String member) {
        return new PackDescriptor(
                packId,
                "1.0.0",
                Optional.empty(),
                packId + " pack",
                Optional.empty(),
                Optional.empty(),
                PackPaths.none(),
                List.of(),
                List.of(new PackOverride("directive:DIRECTIVE_009", member)));
    }

    private static List<PackIdentifier> roots(String... ids) {
        return Arrays.stream(ids).map(PackIdentifier::new).toList();
    }

    private static List<PackIdentifier> allConfiguredIds(List<ConfiguredPack> packs) {
        return packs.stream().map(ConfiguredPack::id).toList();
    }

    private static CharterRefusal refusalOf(Runnable read) {
        try {
            read.run();
        } catch (CharterRefusal refusal) {
            return refusal;
        }
        throw new AssertionError("the read was expected to refuse");
    }

    /** The descriptors by pack id; an id in {@link #unreadable} refuses, as storage refuses an invalid pack.yaml. */
    private final class InMemoryDescriptors implements PackDescriptorRepository {

        @Override
        public PackDescriptor get(PackIdentifier pack) {
            return find(pack).orElseThrow(() -> new CharterRefusal(RefusalCode.PACK_UNREADABLE, "no pack.yaml"));
        }

        @Override
        public Optional<PackDescriptor> find(PackIdentifier pack) {
            CharterRefusal refusal = unreadable.get(pack.value());
            if (refusal != null) {
                throw refusal;
            }
            return descriptors.getOrDefault(pack.value(), Optional.empty());
        }

        @Override
        public void save(PackIdentifier pack, PackDescriptor descriptor) {
            throw new UnsupportedOperationException("the offering never writes");
        }
    }

    /** The defined and written artifacts and the typed edges by pack id; no pack refines any. */
    private final class InMemoryRefinements implements PackArtifactRepository {

        @Override
        public PackCompositionFacts compositionFacts(PackIdentifier pack) {
            factsRead.add(pack.value());
            CharterRefusal refusal = factsUnreadable.get(pack.value());
            if (refusal != null) {
                throw refusal;
            }
            return new PackCompositionFacts(
                    List.of(),
                    defined.getOrDefault(pack.value(), List.of()),
                    written.getOrDefault(pack.value(), List.of()),
                    MutabilityPolicySource.empty(),
                    List.of(),
                    edges.getOrDefault(pack.value(), List.of()));
        }
    }
}
