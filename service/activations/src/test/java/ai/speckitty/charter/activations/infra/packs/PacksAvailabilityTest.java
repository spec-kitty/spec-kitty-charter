package ai.speckitty.charter.activations.infra.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.speckitty.charter.activations.domain.ActivationDocument;
import ai.speckitty.charter.activations.domain.ClosureSnapshot;
import ai.speckitty.charter.activations.domain.DocumentRules;
import ai.speckitty.charter.activations.domain.PackRef;
import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.NodeKind;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.application.ClosureView;
import ai.speckitty.charter.packs.application.OfferingReadService.OfferingEntry;
import ai.speckitty.charter.packs.application.UnknownRootException;
import ai.speckitty.charter.packs.domain.MutabilityLevel;
import ai.speckitty.charter.packs.domain.MutabilityPolicySource;
import ai.speckitty.charter.packs.domain.PerPackPolicy;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.PresetItem;
import ai.speckitty.charter.packs.domain.RequiredFloor;
import ai.speckitty.charter.packs.domain.ResolvedEdge;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * The adapter over the packs slice: one {@code ClosureQueries.snapshot} per call, copied field by field, with each pack
 * of the floor and the locks named by its configured id; an unknown root named by the typed UnknownRootException alone (R4); and a refusal
 * of the compose passed on unchanged.
 */
class PacksAvailabilityTest {

    private static final String ALPHA = "01M4P01D5TWB5FKZGNNQG3943R";
    private static final String BETA = "01M4P01D5TWB5FKZGNNQG3943S";

    private final FakeClosures closures = new FakeClosures();
    private final PacksAvailability adapter = new PacksAvailability(closures);

    /**
     * A closure read that answers one set of roots with what it was given, and refuses every other set: a call with
     * roots the test did not expect fails the test instead of answering.
     */
    private static final class FakeClosures implements PacksAvailability.Closures {
        private Optional<List<PackIdentifier>> expected = Optional.empty();
        private Supplier<ClosureView> answer = () -> {
            throw new AssertionError("no answer was given");
        };
        private int calls;

        void answers(Optional<List<PackIdentifier>> roots, ClosureView view) {
            expected = roots;
            answer = () -> view;
        }

        void throwsOn(Optional<List<PackIdentifier>> roots, RuntimeException fault) {
            expected = roots;
            answer = () -> {
                throw fault;
            };
        }

        @Override
        public ClosureView snapshot(Optional<List<PackIdentifier>> roots) {
            calls++;
            assertThat(roots).as("the roots the adapter asks for").isEqualTo(expected);
            return answer.get();
        }
    }

    /** beta, then alpha, which depends on it; alpha locks D1 and its governing preset requires D2. */
    private static ClosureView view() {
        Urn d1 = new Urn(NodeKind.DIRECTIVE, "D1");
        Urn d2 = new Urn(NodeKind.DIRECTIVE, "D2");
        Map<ArtifactKind, Set<String>> held = new EnumMap<>(ArtifactKind.class);
        held.put(ArtifactKind.DIRECTIVE, Set.of("D1", "D2"));
        held.put(ArtifactKind.MISSION_TYPE, Set.of("software-dev"));
        Map<Urn, String> holderOf = new LinkedHashMap<>();
        holderOf.put(d1, BETA);
        holderOf.put(d2, BETA);
        holderOf.put(Urn.missionType("software-dev"), BETA);
        Preset governing = new Preset(
                "governing",
                "D2 is required.",
                List.of(ArtifactKind.DIRECTIVE),
                Map.of(ArtifactKind.DIRECTIVE, List.of(PresetItem.mustHave("D2"), PresetItem.of("D1"))),
                List.of(PresetItem.of("software-dev")));
        return new ClosureView(
                List.of(
                        new OfferingEntry(
                                new PackIdentifier("beta"), BETA, PackLevel.BUILT_IN, "beta-pack", "1.0.0", List.of()),
                        new OfferingEntry(
                                new PackIdentifier("alpha"),
                                ALPHA,
                                PackLevel.ORG,
                                "alpha-pack",
                                "2.0.0",
                                List.of(new ResolvedEdge(ALPHA, BETA, ">=1.0", "1.0.0")))),
                held,
                holderOf,
                Set.of("software-dev"),
                new RequiredFloor(Map.of(d2, List.of(ALPHA))),
                new PerPackPolicy(Map.of(
                        ALPHA,
                                MutabilityPolicySource.builder()
                                        .entry(d1, MutabilityLevel.LOCKED)
                                        .build(),
                        BETA,
                                MutabilityPolicySource.builder()
                                        .entry(d2, MutabilityLevel.EXTENDABLE)
                                        .build())),
                List.of(BETA, ALPHA),
                List.of(new ClosureView.HeldPreset(governing, ALPHA)),
                Map.of(ALPHA, BETA),
                "sha256:fingerprint");
    }

    @Test
    void theViewIsCopiedFieldByField() {
        closures.answers(Optional.of(List.of(new PackIdentifier("alpha"))), view());

        ClosureSnapshot snapshot = adapter.snapshot(Optional.of(List.of("alpha")));

        assertThat(closures.calls).as("one verified read per snapshot").isEqualTo(1);
        assertThat(snapshot.resolved()).isTrue();
        assertThat(snapshot.packs())
                .extracting(ClosureSnapshot.PackEntry::packId)
                .containsExactly("beta", "alpha");
        ClosureSnapshot.PackEntry alpha = snapshot.pack("alpha").orElseThrow();
        assertThat(alpha.descriptorPackId()).isEqualTo(ALPHA);
        assertThat(alpha.level()).isEqualTo("org");
        assertThat(alpha.name()).isEqualTo("alpha-pack");
        assertThat(alpha.version()).isEqualTo("2.0.0");
        assertThat(alpha.dependsOn()).containsExactly(new ClosureSnapshot.Dependency(BETA, ">=1.0", "1.0.0"));
        assertThat(snapshot.pack("beta").orElseThrow().level()).isEqualTo("built-in");
        assertThat(alpha.parentPackId())
                .as("alpha's parent_pack, named by the id it is configured under")
                .contains("beta");
        assertThat(snapshot.pack("beta").orElseThrow().parentPackId()).isEmpty();
        assertThat(snapshot.extendsChain("alpha", "beta")).contains(List.of("alpha"));
        assertThat(snapshot.held(ArtifactKind.DIRECTIVE)).containsExactlyInAnyOrder("D1", "D2");
        assertThat(snapshot.missionTypes()).containsExactly("software-dev");
        assertThat(snapshot.fingerprint()).isEqualTo("sha256:fingerprint");
    }

    @Test
    void theFloorAndTheLocksNameTheConfiguredPackAndOnlyLockedLevelsCount() {
        closures.answers(Optional.empty(), view());

        ClosureSnapshot snapshot = adapter.snapshot(Optional.empty());

        assertThat(snapshot.requiredBy()).containsExactly(Map.entry("directive:D2", List.of("alpha")));
        assertThat(snapshot.lockedBy()).containsExactly(Map.entry("directive:D1", "alpha"));
    }

    @Test
    void aDenyEntryOnAMustHaveItemOfTheChildsPresetIsRefusedNamingTheChild() {
        closures.answers(Optional.empty(), view());
        ClosureSnapshot snapshot = adapter.snapshot(Optional.empty());
        ActivationDocument.Built built = ActivationDocument.builder()
                .deactivated(ArtifactKind.DIRECTIVE, List.of("D2"))
                .build();

        assertThat(DocumentRules.check(built, snapshot)).hasValueSatisfying(refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.ARTIFACT_REQUIRED);
            assertThat(refusal.requiredBy()).extracting(by -> by.packId()).containsExactly("alpha");
        });
    }

    @Test
    void thePresetsAreCopiedWithTheirMustHaveItemsAmongTheMembers() {
        closures.answers(Optional.empty(), view());

        ClosureSnapshot.PresetView preset =
                adapter.snapshot(Optional.empty()).presets().get("governing");

        assertThat(preset.id()).isEqualTo("governing");
        assertThat(preset.activatedKinds()).containsExactly(ArtifactKind.DIRECTIVE);
        assertThat(preset.members().get(ArtifactKind.DIRECTIVE)).containsExactlyInAnyOrder("D1", "D2");
        assertThat(preset.missionTypes()).containsExactly("software-dev");
    }

    @Test
    void anUnknownRootIsNamedAndTheWriteIsRefusedAtItsPosition() {
        closures.throwsOn(
                Optional.of(List.of(new PackIdentifier("alpha"), new PackIdentifier("ghost"))),
                new UnknownRootException(List.of("ghost")));

        ClosureSnapshot snapshot = adapter.snapshot(Optional.of(List.of("alpha", "ghost")));

        assertThat(snapshot.resolved()).isFalse();
        assertThat(snapshot.unknownPacks()).containsExactly("ghost");
        ActivationDocument.Built built = ActivationDocument.builder()
                .packs(List.of(PackRef.of("alpha"), PackRef.of("ghost")))
                .build();
        assertThat(DocumentRules.check(built, snapshot)).hasValueSatisfying(refusal -> {
            assertThat(refusal.code()).isEqualTo(RefusalCode.ACTIVATION_INVALID);
            assertThat(refusal.violations()).singleElement().satisfies(violation -> {
                assertThat(violation.pointer()).isEqualTo("/packs/1/packId");
                assertThat(violation.code()).isEqualTo("unknown_pack");
            });
        });
    }

    @Test
    void anyOtherArgumentErrorOfTheComposeIsNoUnknownPackAndPropagatesUnchanged() {
        IllegalArgumentException fault = new IllegalArgumentException("a fault of the compose");
        closures.throwsOn(Optional.of(List.of(new PackIdentifier("alpha"))), fault);

        assertThatThrownBy(() -> adapter.snapshot(Optional.of(List.of("alpha"))))
                .isSameAs(fault);
    }

    @Test
    void aRefusalOfTheComposePassesUnchanged() {
        CharterRefusal missing = new CharterRefusal(RefusalCode.PACK_REFERENCE_PACK_MISSING, "alpha names beta-pack");
        closures.throwsOn(Optional.empty(), missing);

        assertThatThrownBy(() -> adapter.snapshot(Optional.empty())).isSameAs(missing);
    }
}
