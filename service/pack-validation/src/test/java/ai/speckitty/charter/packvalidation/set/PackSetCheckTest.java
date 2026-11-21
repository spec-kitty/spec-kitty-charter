package ai.speckitty.charter.packvalidation.set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterRefusal;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.RefusalCode;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.kernel.validation.Evaluation;
import ai.speckitty.charter.kernel.validation.Location;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.ReasonCode;
import ai.speckitty.charter.kernel.validation.Severity;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.domain.PackReading;
import ai.speckitty.charter.packs.domain.PackReadout;
import ai.speckitty.charter.packs.domain.PackRoot;
import ai.speckitty.charter.packs.domain.PackVerdict;
import ai.speckitty.charter.packs.domain.Preset;
import ai.speckitty.charter.packs.domain.roundtrip.PackRoundTripResult;
import ai.speckitty.charter.packs.domain.rules.CompositionRules.CompositionFault;
import ai.speckitty.charter.packs.domain.rules.PackRulesets;
import ai.speckitty.charter.packs.examples.ExamplePacks;
import ai.speckitty.charter.packs.examples.PlantedPacks;
import ai.speckitty.charter.packs.infra.storage.FileSystemPackInspector;
import ai.speckitty.charter.packs.infra.storage.PackRoundTrip;
import ai.speckitty.charter.packs.infra.storage.RoundTripControls;
import ai.speckitty.charter.packvalidation.PackCheck;
import ai.speckitty.charter.packvalidation.placement.PlacementCodes;
import ai.speckitty.charter.packvalidation.roundtrip.RoundTripping;
import ai.speckitty.charter.packvalidation.roundtrip.WholePackRoundTripRule;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.Check;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.PackResult;
import ai.speckitty.charter.packvalidation.set.PackSetVerdict.Unjudged;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The pack-set check (#478, FR-002 to FR-004, FR-007, T013), over the one reader and charter's example packs: validate
 * mode per pack, {@code CLOSURE} with its preset phase over the set as given (D5), skipped when a pack has no facts or no
 * level (D3), and the round trip per pack when asked.
 */
@DisplayName("PackSetCheck: PACK per pack, CLOSURE over the set as given, ROUND_TRIP when asked (#478)")
class PackSetCheckTest {

    private static final String BASE_ID = "01M6Y9ACNAAD96XPNQS0XWP2FF";
    private static final String ALT_ID = "01M74H0000000000000000RG2A";

    /** A directory name that is not a pack id (#583, D5). */
    private static final String BAD_NAME = "Bad_Name";

    private static final PackSetCheck CHECK = new PackSetCheck(new FileSystemPackInspector());

    private static PackSetEntry example(String name) {
        return at(name, ExamplePacks.pack(name), Optional.of(ExamplePacks.level(name)));
    }

    private static PackSetEntry at(String name, Path dir, Optional<PackLevel> level) {
        return new PackSetEntry(new PackRoot(name, level, dir.toString()));
    }

    private static List<PackSetEntry> examples(String... names) {
        return java.util.Arrays.stream(names).map(PackSetCheckTest::example).toList();
    }

    private static List<String> codes(List<CompositionFault> faults) {
        return faults.stream().map(fault -> fault.code().value()).toList();
    }

    private static boolean hasViolation(List<Reason> reasons) {
        return reasons.stream().anyMatch(reason -> reason.isFailing(Severity.VIOLATION));
    }

    private static void rewrite(Path file, UnaryOperator<String> change) {
        try {
            Files.writeString(
                    file, change.apply(Files.readString(file, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Renames org-mandate's preset to org-base's {@code spdd}, as an author would: the file, its id and the manifest's
     * entry, so the pack stays valid on its own.
     */
    private static void holdSpdd(Path mandate) {
        try {
            Path preset = mandate.resolve("presets/mandate.yaml");
            Files.writeString(
                    mandate.resolve("presets/spdd.yaml"),
                    Files.readString(preset, StandardCharsets.UTF_8).replace("id: mandate\n", "id: spdd\n"),
                    StandardCharsets.UTF_8);
            Files.delete(preset);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        rewrite(
                mandate.resolve("pack-manifest.yaml"),
                text -> text.replace("id: mandate\n", "id: spdd\n")
                        .replace("presets/mandate.yaml", "presets/spdd.yaml"));
    }

    private static void dependOn(Path pack, String packId) {
        rewrite(pack.resolve("pack.yaml"), text -> text + "  - pack_id: " + packId + "\n    range: \">=1.0,<2\"\n");
    }

    @Nested
    @DisplayName("CLOSURE over the set as given")
    class Closure {

        @Test
        @DisplayName("charter's example packs at their levels: no violation, no fault, PACK then CLOSURE")
        void theExamplePacksAreValid() {
            PackSetVerdict verdict = CHECK.check(examples(ExamplePacks.names().toArray(String[]::new)));

            assertThat(verdict.packs()).hasSize(ExamplePacks.names().size()).noneMatch(PackResult::failing);
            assertThat(verdict.packs())
                    .allSatisfy(
                            result -> assertThat(hasViolation(result.verdict().findings()))
                                    .isFalse());
            assertThat(verdict.faults()).isEmpty();
            assertThat(verdict.checks()).containsExactly(Check.PACK, Check.CLOSURE);
            assertThat(verdict.skipped()).isEmpty();
            assertThat(verdict.failing()).isFalse();
        }

        @Test
        @DisplayName("D5: project alone is judged as given, so both its dependencies are unknown")
        void aDependentPackAloneHasUnknownDependencies() {
            PackSetVerdict verdict = CHECK.check(examples(ExamplePacks.PROJECT));

            assertThat(verdict.checks()).containsExactly(Check.PACK, Check.CLOSURE);
            assertThat(verdict.faults()).hasSize(2).allSatisfy(fault -> {
                assertThat(fault.code().value()).isEqualTo(RefusalCode.PACK_DEPENDENCY_UNKNOWN.token());
                assertThat(fault.packs()).extracting(RefusalPack::name).contains(ExamplePacks.PROJECT);
            });
            assertThat(verdict.packs())
                    .singleElement()
                    .satisfies(result -> assertThat(result.failing())
                            .as("the pack itself is valid; the set is not")
                            .isFalse());
            assertThat(verdict.failing()).isTrue();
        }

        @Test
        @DisplayName("D5: project with its dependencies passes")
        void aDependentPackWithItsDependenciesPasses() {
            PackSetVerdict verdict = CHECK.check(
                    examples(ExamplePacks.PROJECT, ExamplePacks.ORG, ExamplePacks.ORG_ALT, ExamplePacks.BUILT_IN));

            assertThat(verdict.faults()).isEmpty();
            assertThat(verdict.failing()).isFalse();
        }

        @Test
        @DisplayName("two org packs that depend on each other: pack_dependency_cycle naming both")
        void twoPacksThatDependOnEachOtherAreACycle(@TempDir Path dir) {
            Path base = ExamplePacks.copyOf(ExamplePacks.ORG, dir);
            Path alt = ExamplePacks.copyOf(ExamplePacks.ORG_ALT, dir);
            dependOn(base, ALT_ID);
            dependOn(alt, BASE_ID);

            PackSetVerdict verdict = CHECK.check(List.of(
                    example(ExamplePacks.BUILT_IN),
                    at(ExamplePacks.ORG, base, Optional.of(PackLevel.ORG)),
                    at(ExamplePacks.ORG_ALT, alt, Optional.of(PackLevel.ORG))));

            assertThat(verdict.checks()).containsExactly(Check.PACK, Check.CLOSURE);
            assertThat(verdict.faults())
                    .filteredOn(fault -> fault.code().value().equals(RefusalCode.PACK_DEPENDENCY_CYCLE.token()))
                    .singleElement()
                    .satisfies(fault -> assertThat(fault.packs())
                            .extracting(RefusalPack::name)
                            .contains(ExamplePacks.ORG, ExamplePacks.ORG_ALT));
        }

        @Test
        @DisplayName("two unrelated org packs holding one preset id: the preset phase's sibling conflict")
        void twoOrgPacksHoldingOnePresetConflict(@TempDir Path dir) {
            Path mandate = ExamplePacks.copyOf(ExamplePacks.ORG_MANDATE, dir);
            holdSpdd(mandate);

            PackSetVerdict verdict = CHECK.check(List.of(
                    example(ExamplePacks.BUILT_IN),
                    example(ExamplePacks.ORG),
                    at(ExamplePacks.ORG_MANDATE, mandate, Optional.of(PackLevel.ORG))));

            assertThat(verdict.packs()).noneMatch(PackResult::failing);
            assertThat(codes(verdict.faults())).containsExactly(RefusalCode.PACK_SIBLING_CONFLICT.token());
            assertThat(verdict.faults().getFirst().detail()).contains("preset `spdd`");
            assertThat(verdict.faults().getFirst().packs())
                    .extracting(RefusalPack::name)
                    .containsExactlyInAnyOrder(ExamplePacks.ORG, ExamplePacks.ORG_MANDATE);
        }

        @Test
        @DisplayName("one judging path: over the example packs, the faults are PackRulesets.closure(presetIdsOf)'s")
        void theExamplePacksFaultsAreTheRulesetsReasons() {
            PackSetVerdict verdict = CHECK.check(examples(ExamplePacks.names().toArray(String[]::new)));

            assertThat(verdict.faults()).isEmpty();
            assertFaultsAreTheRulesets(verdict);
        }

        @Test
        @DisplayName("one judging path: over a set with faults, the faults are PackRulesets.closure(presetIdsOf)'s")
        void theFaultsAreTheRulesetsReasons(@TempDir Path dir) {
            Path mandate = ExamplePacks.copyOf(ExamplePacks.ORG_MANDATE, dir);
            holdSpdd(mandate);
            PackSetVerdict verdict = CHECK.check(List.of(
                    example(ExamplePacks.PROJECT),
                    example(ExamplePacks.ORG),
                    at(ExamplePacks.ORG_MANDATE, mandate, Optional.of(PackLevel.ORG))));

            assertThat(codes(verdict.faults()))
                    .as("project's unknown org-alt, then the preset contest")
                    .contains(RefusalCode.PACK_DEPENDENCY_UNKNOWN.token(), RefusalCode.PACK_SIBLING_CONFLICT.token());
            assertFaultsAreTheRulesets(verdict);
        }

        /** The set's faults equal what the named ruleset answers over the same facts, code and detail, in order. */
        private static void assertFaultsAreTheRulesets(PackSetVerdict verdict) {
            List<PackUnderResolution> set = new ArrayList<>();
            Map<String, List<String>> presets = new HashMap<>();
            for (PackResult result : verdict.packs()) {
                PackReadout.Built built = (PackReadout.Built) result.facts();
                set.add(built.underResolution(new ConfiguredPack(
                        new PackIdentifier(result.entry().name()),
                        result.level().orElseThrow())));
                presets.put(
                        result.entry().name(),
                        built.facts().presets().stream().map(Preset::id).toList());
            }

            List<Reason> reasons = PackRulesets.closure(
                            pack -> presets.getOrDefault(pack.configured().id().value(), List.of()))
                    .apply(set);

            assertThat(verdict.faults())
                    .extracting(fault -> fault.code(), CompositionFault::detail)
                    .containsExactlyElementsOf(reasons.stream()
                            .map(reason -> tuple(reason.code(), reason.detail()))
                            .toList());
        }

        @Test
        @DisplayName("a pack with no configured level is placed at the level its pack.yaml declares")
        void anUnconfiguredLevelIsTheDeclaredOne() {
            PackSetVerdict verdict = CHECK.check(List.of(
                    at(ExamplePacks.BUILT_IN, ExamplePacks.pack(ExamplePacks.BUILT_IN), Optional.empty()),
                    at(ExamplePacks.ORG, ExamplePacks.pack(ExamplePacks.ORG), Optional.empty())));

            assertThat(verdict.packs())
                    .extracting(PackResult::level)
                    .containsExactly(Optional.of(PackLevel.BUILT_IN), Optional.of(PackLevel.ORG));
            assertThat(verdict.checks()).containsExactly(Check.PACK, Check.CLOSURE);
            assertThat(verdict.faults()).isEmpty();
        }

        @Test
        @DisplayName("a configured level wins over the declared one, and CLOSURE judges the pack there")
        void theConfiguredLevelWins() {
            PackSetVerdict verdict = CHECK.check(List.of(
                    example(ExamplePacks.BUILT_IN),
                    at(ExamplePacks.ORG, ExamplePacks.pack(ExamplePacks.ORG), Optional.of(PackLevel.PROJECT))));

            assertThat(verdict.packs().get(1).level()).contains(PackLevel.PROJECT);
            assertThat(verdict.checks()).contains(Check.CLOSURE);
        }

        @Test
        @DisplayName("the results follow the order the set was given in")
        void theResultsFollowTheInputOrder() {
            List<String> order =
                    List.of(ExamplePacks.PROJECT, ExamplePacks.BUILT_IN, ExamplePacks.ORG_ALT, ExamplePacks.ORG);

            PackSetVerdict verdict = CHECK.check(examples(order.toArray(String[]::new)));

            assertThat(verdict.packs())
                    .extracting(result -> result.entry().name())
                    .containsExactlyElementsOf(order);
        }
    }

    @Nested
    @DisplayName("D3: a pack with no facts or no level skips CLOSURE")
    class Skipped {

        @Test
        @DisplayName("a pack whose graph cannot be read: CLOSURE is skipped and the skip names it; the pack fails")
        void anUnreadableGraphSkipsClosure(@TempDir Path dir) throws IOException {
            Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, dir);
            Files.writeString(builtIn.resolve("broken.graph.yaml"), "nodes: [valid\n", StandardCharsets.UTF_8);

            PackSetVerdict verdict = CHECK.check(List.of(
                    at(ExamplePacks.BUILT_IN, builtIn, Optional.of(PackLevel.BUILT_IN)), example(ExamplePacks.ORG)));

            assertThat(verdict.checks()).containsExactly(Check.PACK);
            assertThat(verdict.faults()).isEmpty();
            assertThat(verdict.skipped()).singleElement().satisfies(skip -> {
                assertThat(skip.check()).isEqualTo(Check.CLOSURE);
                assertThat(skip.packs())
                        .containsExactly(new Unjudged(
                                ExamplePacks.BUILT_IN,
                                "its composition facts cannot be read: pack_unreadable: a graph document of built-in"
                                        + " cannot be read"));
                assertThat(skip.reason())
                        .isEqualTo("CLOSURE needs the composition facts and the level of every pack; it has none for "
                                + "built-in (its composition facts cannot be read: pack_unreadable: a graph document"
                                + " of built-in cannot be read)");
            });
            PackResult broken = verdict.packs().getFirst();
            assertThat(broken.failing()).isTrue();
            assertThat(broken.unreportedRefusal())
                    .as("its verdict already reports the fault, so the refusal is not a second problem")
                    .isEmpty();
            assertThat(verdict.packs().get(1).failing()).isFalse();
            assertThat(verdict.failing()).isTrue();
        }

        @Test
        @DisplayName("a pack whose pack_version is malformed: CLOSURE is skipped and names it, never handed it (#624)")
        void aMalformedDescriptorSkipsClosure(@TempDir Path dir) throws IOException {
            Path org = ExamplePacks.copyOf(ExamplePacks.ORG, dir);
            Path descriptor = org.resolve("pack.yaml");
            Files.writeString(
                    descriptor,
                    Files.readString(descriptor).replace("pack_version: 1.0.0", "pack_version: not-a-version"),
                    StandardCharsets.UTF_8);

            PackSetVerdict verdict = CHECK.check(
                    List.of(example(ExamplePacks.BUILT_IN), at(ExamplePacks.ORG, org, Optional.of(PackLevel.ORG))));

            assertThat(verdict.checks()).containsExactly(Check.PACK);
            assertThat(verdict.faults()).isEmpty();
            assertThat(verdict.skipped()).singleElement().satisfies(skip -> {
                assertThat(skip.check()).isEqualTo(Check.CLOSURE);
                assertThat(skip.packs())
                        .containsExactly(new Unjudged(
                                ExamplePacks.ORG,
                                "its composition facts cannot be read: pack_unreadable: pack.yaml of org-base:"
                                        + " pack_version 'not-a-version' is not a PEP 440 version"));
            });
            PackResult malformed = verdict.packs().get(1);
            assertThat(malformed.verdict().findings())
                    .as("PACK reports the fault once, at pack.yaml, under the precheck's code")
                    .filteredOn(finding -> finding.code().value().equals("pack_version_invalid"))
                    .singleElement()
                    .satisfies(finding -> assertThat(finding.location().file()).isEqualTo("pack.yaml"));
            assertThat(malformed.failing()).isTrue();
            assertThat(malformed.unreportedRefusal())
                    .as("its verdict already reports the fault, so the refusal is not a second problem")
                    .isEmpty();
            assertThat(verdict.packs().getFirst().failing()).isFalse();
        }

        @Test
        @DisplayName("a pack whose facts cannot be read keeps the level its read pack.yaml declares")
        void anUnreadablePackKeepsItsDeclaredLevel(@TempDir Path dir) throws IOException {
            Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, dir);
            Files.writeString(builtIn.resolve("broken.graph.yaml"), "nodes: [valid\n", StandardCharsets.UTF_8);

            PackResult result = CHECK.check(List.of(at(ExamplePacks.BUILT_IN, builtIn, Optional.empty())))
                    .packs()
                    .getFirst();

            assertThat(result.facts()).isInstanceOf(PackReadout.Unreadable.class);
            assertThat(result.level()).contains(PackLevel.BUILT_IN);
        }

        @Test
        @DisplayName("a pack with no configured and no declared level: CLOSURE is skipped, the skip names it, it fails")
        void anUnknownLevelSkipsClosure(@TempDir Path dir) {
            Path bare = PlantedPacks.copyOf("control-no-pack-level-on-a-bare-path", dir);

            PackSetVerdict verdict =
                    CHECK.check(List.of(example(ExamplePacks.BUILT_IN), at("qa", bare, Optional.empty())));

            assertThat(verdict.packs().get(1).facts()).isInstanceOf(PackReadout.Built.class);
            assertThat(verdict.packs().get(1).level()).isEmpty();
            assertThat(verdict.checks()).containsExactly(Check.PACK);
            assertThat(verdict.skipped())
                    .singleElement()
                    .satisfies(skip -> assertThat(skip.packs())
                            .containsExactly(new Unjudged(
                                    "qa", "its level is unknown: none is configured and pack.yaml declares none")));
            assertThat(verdict.packs().get(1).verdict().findings())
                    .as("validate mode has no level to judge a bare path by")
                    .isEmpty();
            assertThat(verdict.packs().get(1).setCheck()).singleElement().satisfies(reason -> {
                assertThat(reason.code()).isEqualTo(PlacementCodes.PACK_LEVEL_MISSING_VIOLATION);
                assertThat(reason.severity()).isEqualTo(Severity.VIOLATION);
                assertThat(reason.location()).isEqualTo(Location.in("pack.yaml"));
                assertThat(reason.detail()).isEqualTo("pack.yaml of qa declares no pack_level and none is configured");
            });
            assertThat(verdict.packs().get(0).setCheck()).isEmpty();
            assertThat(verdict.packs().get(1).failing())
                    .as("an unknown level stops CLOSURE, so it is the pack's fault: never a silent pass")
                    .isTrue();
            assertThat(verdict.packs().get(0).failing()).isFalse();
            assertThat(verdict.failing()).isTrue();
        }

        @Test
        @DisplayName("a pack with no pack.yaml and no level: its refusal speaks for it, the set check adds nothing")
        void anUnreadablePackWithNoLevelGetsNoLevelReason(@TempDir Path dir) throws IOException {
            Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, dir);
            Files.delete(builtIn.resolve("pack.yaml"));

            PackResult result = CHECK.check(List.of(at(ExamplePacks.BUILT_IN, builtIn, Optional.empty())))
                    .packs()
                    .getFirst();

            assertThat(result.facts()).isInstanceOf(PackReadout.Unreadable.class);
            assertThat(result.level()).isEmpty();
            assertThat(result.setCheck()).isEmpty();
            assertThat(result.failing()).isTrue();
        }

        /**
         * Error handling (#583, D5): a pack in a directory whose name is not a pack id. The set takes the pack's identity
         * from that name, so the read refuses it with one {@code pack_id_invalid} violation at the pack, its facts carry
         * the same refusal, and nothing throws.
         */
        @Test
        @DisplayName(
                "a pack in a directory named Bad_Name: one pack_id_invalid problem, CLOSURE skipped and named (D3)")
        void aPackWhoseDirectoryNameIsNoPackIdIsOneProblemAndSkipsClosure(@TempDir Path dir) throws IOException {
            Path bad = Files.move(ExamplePacks.copyOf(ExamplePacks.ORG, dir), dir.resolve(BAD_NAME));

            PackSetVerdict verdict =
                    CHECK.check(List.of(example(ExamplePacks.BUILT_IN), at(BAD_NAME, bad, Optional.of(PackLevel.ORG))));

            PackResult result = verdict.packs().get(1);
            assertThat(result.verdict().findings())
                    .filteredOn(finding -> finding.isFailing(Severity.VIOLATION))
                    .singleElement()
                    .satisfies(finding -> {
                        assertThat(finding.code().value()).isEqualTo("pack_id_invalid");
                        assertThat(finding.location().file()).isEqualTo("pack");
                        assertThat(finding.detail()).contains(BAD_NAME);
                    });
            assertThat(result.facts()).isInstanceOf(PackReadout.Unreadable.class);
            assertThat(result.unreportedRefusal())
                    .as("the violation speaks for the pack, so its refusal is no second problem")
                    .isEmpty();
            assertThat(result.setCheck()).isEmpty();
            assertThat(result.failing()).isTrue();
            assertThat(verdict.packs().getFirst().failing()).isFalse();
            assertThat(verdict.checks()).containsExactly(Check.PACK);
            assertThat(verdict.faults()).isEmpty();
            assertThat(verdict.skipped()).singleElement().satisfies(skip -> {
                assertThat(skip.check()).isEqualTo(Check.CLOSURE);
                assertThat(skip.packs())
                        .as("the skip names the cause, pack_id_invalid, not only pack_unreadable (PR #608 review L3)")
                        .containsExactly(new Unjudged(
                                BAD_NAME,
                                "its composition facts cannot be read: pack_unreadable: pack_id_invalid: pack"
                                        + " directory '" + BAD_NAME + "' is not a pack id. "
                                        + PackIdentifier.GRAMMAR_DETAIL));
            });
            assertThat(verdict.failing()).isTrue();
        }

        /**
         * Error handling: no real read of a canonical pack gives facts that cannot be read with a verdict that holds no
         * violation (the one reader reports every refusal the composing read gives as a violation). So the readout of a
         * real read of org-base is given the facts of a read with no assembly input, the one wording of
         * {@link PackReadout.Unreadable#noAssembly}.
         */
        @Test
        @DisplayName("facts that cannot be read fail the pack even when its verdict holds no violation")
        void unreadableFactsFailAPackWithNoViolation() {
            FileSystemPackInspector inspector = new FileSystemPackInspector();
            PackReading noAssembly = root -> {
                PackReadout read = inspector.read(root);
                return new PackReadout(
                        read.contents(),
                        read.files(),
                        read.notHeld(),
                        read.rationale(),
                        PackReadout.Unreadable.noAssembly(root.name()));
            };

            PackSetVerdict verdict = new PackSetCheck(noAssembly).check(examples(ExamplePacks.ORG));

            PackResult result = verdict.packs().getFirst();
            assertThat(hasViolation(result.verdict().findings())).isFalse();
            assertThat(result.failing()).isTrue();
            assertThat(result.unreportedRefusal()).contains(PackReadout.Unreadable.noAssembly(ExamplePacks.ORG));
            assertThat(verdict.checks()).containsExactly(Check.PACK);
            assertThat(verdict.failing()).isTrue();
        }
    }

    @Nested
    @DisplayName("ROUND_TRIP when asked")
    class RoundTrip {

        /** A red run: an empty census, and a written file no source file pairs with. */
        private static final PackRoundTripResult RED = new PackRoundTripResult(
                new TreeMap<>(), new TreeMap<>(), Set.of(), new TreeMap<>(), new TreeSet<>(Set.of("stray.yaml")));

        @Test
        @DisplayName(
                "a red run gives its reasons, a pack with a PACK violation is round-tripped too, ROUND_TRIP is run")
        void aRedRunGivesItsReasons(@TempDir Path dir) {
            Path planted = PlantedPacks.copyOf("duplicate-artifact-id", dir);
            List<String> asked = new ArrayList<>();
            RoundTripping red = root -> {
                asked.add(root.name());
                return RED;
            };

            PackSetVerdict verdict = CHECK.checkWithRoundTrip(
                    List.of(example(ExamplePacks.BUILT_IN), at("qa", planted, Optional.of(PackLevel.ORG))), red);

            assertThat(asked).containsExactly(ExamplePacks.BUILT_IN, "qa");
            assertThat(verdict.checks()).endsWith(Check.ROUND_TRIP);
            PackResult builtIn = verdict.packs().getFirst();
            assertThat(builtIn.roundTrip())
                    .extracting(Reason::code, Reason::detail)
                    .containsExactly(
                            tuple(
                                    WholePackRoundTripRule.RED,
                                    "EMPTY_PACK: the census has no file, so the run proves nothing"),
                            tuple(WholePackRoundTripRule.UNPAIRED, "written but unpaired stray.yaml"));
            assertThat(builtIn.failing()).as("a red round trip fails the pack").isTrue();
            assertThat(verdict.packs().get(1).roundTrip()).isNotEmpty();
        }

        @Test
        @DisplayName("the real flow over built-in: no reason")
        void theRealFlowOverBuiltInIsWhole(@TempDir Path dir) {
            RoundTripping flow = realFlow(dir);

            PackSetVerdict verdict = CHECK.checkWithRoundTrip(examples(ExamplePacks.BUILT_IN), flow);

            assertThat(verdict.checks()).containsExactly(Check.PACK, Check.CLOSURE, Check.ROUND_TRIP);
            assertThat(verdict.packs().getFirst().roundTrip()).isEmpty();
            assertThat(verdict.failing()).isFalse();
        }

        private RoundTripping realFlow(Path dir) {
            return root -> {
                try {
                    return PackRoundTrip.run(
                            new PackIdentifier(root.name()),
                            Path.of(root.locator()),
                            Files.createTempDirectory(dir, root.name()));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            };
        }

        /** A symbolic link, or the test is skipped where the file system cannot make one (failed under requireSymlinks). */
        private static void link(Path link, Path target) throws IOException {
            try {
                Files.createSymbolicLink(link, target);
            } catch (IOException | UnsupportedOperationException noLinks) {
                org.junit.jupiter.api.Assumptions.assumeTrue(
                        Boolean.getBoolean("charter.requireSymlinks"), "the file system cannot create links");
                throw noLinks;
            }
        }

        private static List<Reason> atFile(List<Reason> reasons, String file) {
            return reasons.stream()
                    .filter(reason -> reason.location().file().equals(file))
                    .toList();
        }

        @Test
        @DisplayName(
                "the real flow over a planted link: the link is one problem, validate mode's, not a round-trip red too")
        void aLinkIsOneProblemNotARoundTripRedToo(@TempDir Path dir) throws IOException {
            Path pack = PlantedPacks.copyOf("pack-entry-linked-file", dir.resolve("plant"));
            String linked = "directives/002-b.directive.yaml";
            link(pack.resolve(linked), Path.of("001-a.directive.yaml"));

            PackSetVerdict verdict =
                    CHECK.checkWithRoundTrip(List.of(at("qa", pack, Optional.of(PackLevel.ORG))), realFlow(dir));

            PackResult result = verdict.packs().getFirst();
            assertThat(atFile(result.verdict().findings(), linked))
                    .extracting(reason -> reason.code().value())
                    .containsExactly("pack_entry_linked");
            assertThat(result.roundTrip())
                    .as("the round trip's red at the link, kind other:symlink, is the same cause")
                    .isEmpty();
        }

        @Test
        @DisplayName(
                "only a violation at a file drops the round trip's reason there: a warning, or a violation of no file, does"
                        + " not")
        void onlyAViolationAtAFileDropsTheReasonThere() {
            PackCheck.Checked real = new PackCheck(new FileSystemPackInspector())
                    .check(example(ExamplePacks.BUILT_IN).root());
            Reason warning = new Reason(
                    "test",
                    Evaluation.FAIL,
                    new ReasonCode("file_not_held", Severity.WARNING),
                    Location.in("notes.md"),
                    "w");
            Reason violation = new Reason(
                    "test",
                    Evaluation.FAIL,
                    new ReasonCode("pack_entry_linked", Severity.VIOLATION),
                    Location.in("tactics/linked.tactic.yaml"),
                    "linked");
            Reason unplaced = new Reason(
                    "test",
                    Evaluation.FAIL,
                    new ReasonCode("pack_unreadable", Severity.VIOLATION),
                    Location.NOWHERE,
                    "u");
            PackCheck.Checked checked = new PackCheck.Checked(
                    real.readout(), new PackVerdict(List.of(), List.of(warning, violation, unplaced)));
            Reason atTheWarning = red("notes.md");
            Reason atTheViolation = red("tactics/linked.tactic.yaml");
            Reason ofTheRun = new Reason(
                    "round-trip", Evaluation.FAIL, WholePackRoundTripRule.RED, Location.NOWHERE, "STALE_BASELINE x");

            assertThat(PackSetCheck.notReported(checked, List.of(atTheWarning, atTheViolation, ofTheRun)))
                    .containsExactly(atTheWarning, ofTheRun);
        }

        private static Reason red(String file) {
            return new Reason(
                    "round-trip",
                    Evaluation.FAIL,
                    WholePackRoundTripRule.RED,
                    Location.in(file),
                    "UNEXPECTED_RED " + file);
        }

        @Test
        @DisplayName("a link beside an independent round-trip red: the red stays, the link is validate mode's alone")
        void anIndependentRedBesideALinkStays(@TempDir Path dir) throws IOException {
            Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, dir.resolve("packs"));
            String linked = "tactics/linked.tactic.yaml";
            link(builtIn.resolve(linked), Path.of("acceptance-test-first.tactic.yaml"));
            RoundTripping lossy = root -> {
                try {
                    return RoundTripControls.rewritingWrites(
                            new PackIdentifier(root.name()),
                            Path.of(root.locator()),
                            Files.createTempDirectory(dir, root.name()),
                            ArtifactKind.TACTIC,
                            text -> text.replaceAll("(?m)^purpose:.*\\n(?: .*\\n)*", ""));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            };

            PackSetVerdict verdict = CHECK.checkWithRoundTrip(
                    List.of(at(ExamplePacks.BUILT_IN, builtIn, Optional.of(PackLevel.BUILT_IN))), lossy);

            PackResult result = verdict.packs().getFirst();
            assertThat(atFile(result.verdict().findings(), linked))
                    .extracting(reason -> reason.code().value())
                    .containsExactly("pack_entry_linked");
            assertThat(atFile(result.roundTrip(), linked))
                    .as("no second problem at the link")
                    .isEmpty();
            assertThat(atFile(result.roundTrip(), "tactics/acceptance-test-first.tactic.yaml"))
                    .as("the independent red at a real tactic stays")
                    .extracting(Reason::code)
                    .containsExactly(WholePackRoundTripRule.RED);
        }

        @Test
        @DisplayName("the real flow over a garbled pack.yaml: the set still has a verdict; the pack has one problem")
        void aRefusedPackIsItsResultNotAnException(@TempDir Path dir) throws IOException {
            Path builtIn = ExamplePacks.copyOf(ExamplePacks.BUILT_IN, dir.resolve("packs"));
            Files.writeString(builtIn.resolve("pack.yaml"), "pack_id: [\n", StandardCharsets.UTF_8);

            PackSetVerdict verdict = CHECK.checkWithRoundTrip(
                    List.of(
                            at(ExamplePacks.BUILT_IN, builtIn, Optional.of(PackLevel.BUILT_IN)),
                            example(ExamplePacks.ORG)),
                    realFlow(dir));

            PackResult garbled = verdict.packs().getFirst();
            assertThat(garbled.failing()).isTrue();
            assertThat(hasViolation(garbled.verdict().findings())).isTrue();
            assertThat(garbled.roundTrip())
                    .as("validate mode reports the fault, so the refusal is not a second problem")
                    .isEmpty();
            assertThat(verdict.checks()).contains(Check.ROUND_TRIP);
            assertThat(verdict.skipped())
                    .as("only CLOSURE is ever skipped: the garbled pack has no facts; its round trip is no skip")
                    .extracting(PackSetVerdict.Skip::check)
                    .containsExactly(Check.CLOSURE);
            assertThat(verdict.packs().get(1).roundTrip()).isEmpty();
        }

        /**
         * Error handling (#583, D5): the round trip configures a pack under its name (D2b), so a name that is not a pack
         * id would refuse every modelled file for the cause validate mode already reports at the pack.
         */
        @Test
        @DisplayName("the real flow over a pack named Bad_Name: pack_id_invalid is its one problem, no round-trip red")
        void aPackWhoseDirectoryNameIsNoPackIdIsNotRoundTripped(@TempDir Path dir) throws IOException {
            Path bad =
                    Files.move(ExamplePacks.copyOf(ExamplePacks.BUILT_IN, dir.resolve("packs")), dir.resolve(BAD_NAME));
            List<String> asked = new ArrayList<>();
            RoundTripping flow = realFlow(dir);
            RoundTripping counted = root -> {
                asked.add(root.name());
                return flow.run(root);
            };

            PackSetVerdict verdict = CHECK.checkWithRoundTrip(
                    List.of(at(BAD_NAME, bad, Optional.of(PackLevel.BUILT_IN)), example(ExamplePacks.ORG)), counted);

            PackResult result = verdict.packs().getFirst();
            assertThat(result.verdict().findings())
                    .filteredOn(finding -> finding.isFailing(Severity.VIOLATION))
                    .extracting(finding -> finding.code().value())
                    .containsExactly("pack_id_invalid");
            assertThat(result.roundTrip()).isEmpty();
            assertThat(result.failing()).isTrue();
            assertThat(asked)
                    .as("only the pack with a pack id is round-tripped")
                    .containsExactly(ExamplePacks.ORG);
            assertThat(verdict.checks()).containsExactly(Check.PACK, Check.ROUND_TRIP);
        }

        /** Error handling: a refusal of a pack validate mode passes, which no canonical pack gives today. */
        @Test
        @DisplayName("a refused round trip of a pack with no violation is one round_trip_red, so the pack fails")
        void aRefusedPackWithNoViolationIsRed() {
            RoundTripping refusing = root -> {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "pack.yaml of built-in is not valid YAML");
            };

            PackSetVerdict verdict = CHECK.checkWithRoundTrip(examples(ExamplePacks.BUILT_IN), refusing);

            PackResult result = verdict.packs().getFirst();
            assertThat(hasViolation(result.verdict().findings())).isFalse();
            assertThat(result.roundTrip())
                    .extracting(Reason::code, Reason::detail)
                    .containsExactly(
                            tuple(
                                    WholePackRoundTripRule.RED,
                                    "the round trip refused the pack: pack_unreadable: pack.yaml of built-in is not valid YAML"));
            assertThat(result.failing()).isTrue();
            assertThat(verdict.skipped())
                    .as("one problem, the red; no skip beside it")
                    .isEmpty();
        }

        /**
         * Error handling, as {@code unreadableFactsFailAPackWithNoViolation}: the readout of a real read of org-base is
         * given facts that cannot be read, so its verdict holds no violation and its refusal is its one problem. A
         * refused round trip then adds no {@code round_trip_red} (#478, WP04: one problem per pack).
         */
        @Test
        @DisplayName(
                "a refused round trip of a pack whose facts cannot be read adds nothing: its refusal is its problem")
        void aRefusedRoundTripOfAnUnreadablePackAddsNoRed() {
            FileSystemPackInspector inspector = new FileSystemPackInspector();
            PackReading noAssembly = root -> {
                PackReadout read = inspector.read(root);
                return new PackReadout(
                        read.contents(),
                        read.files(),
                        read.notHeld(),
                        read.rationale(),
                        PackReadout.Unreadable.noAssembly(root.name()));
            };
            RoundTripping refusing = root -> {
                throw new CharterRefusal(RefusalCode.PACK_UNREADABLE, "the pack " + root.name() + " cannot be read");
            };

            PackSetVerdict verdict =
                    new PackSetCheck(noAssembly).checkWithRoundTrip(examples(ExamplePacks.ORG), refusing);

            PackResult result = verdict.packs().getFirst();
            assertThat(hasViolation(result.verdict().findings())).isFalse();
            assertThat(result.roundTrip())
                    .as("the refusal is the pack's one problem")
                    .isEmpty();
            assertThat(result.unreportedRefusal()).contains(PackReadout.Unreadable.noAssembly(ExamplePacks.ORG));
            assertThat(result.failing()).isTrue();
            assertThat(verdict.checks()).containsExactly(Check.PACK, Check.ROUND_TRIP);
        }

        @Test
        @DisplayName("an I/O failure of the round trip propagates: it is no fault of the pack")
        void anIoFailurePropagates() {
            RoundTripping failing = root -> {
                throw new UncheckedIOException(new IOException("no temporary directory"));
            };

            assertThatThrownBy(() -> CHECK.checkWithRoundTrip(examples(ExamplePacks.BUILT_IN), failing))
                    .isInstanceOf(UncheckedIOException.class);
        }

        @Test
        @DisplayName("without the round trip, no ROUND_TRIP and no round-trip reason")
        void withoutTheRoundTripThereIsNone() {
            PackSetVerdict verdict = CHECK.check(examples(ExamplePacks.BUILT_IN));

            assertThat(verdict.checks()).doesNotContain(Check.ROUND_TRIP);
            assertThat(verdict.packs().getFirst().roundTrip()).isEmpty();
        }
    }

    @Nested
    @DisplayName("the set")
    class TheSet {

        @Test
        @DisplayName("two entries with one name are refused")
        void aRepeatedNameIsRefused() {
            List<PackSetEntry> twice = List.of(example(ExamplePacks.ORG), example(ExamplePacks.ORG));

            assertThatThrownBy(() -> CHECK.check(twice))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("the pack org-base is given twice");
        }

        @Test
        @DisplayName("an empty set is refused")
        void anEmptySetIsRefused() {
            assertThatThrownBy(() -> CHECK.check(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("a pack set holds at least one pack");
        }

        @Test
        @DisplayName("a skip names at least one pack")
        void aSkipNamesAPack() {
            assertThatThrownBy(() -> new PackSetVerdict.Skip(Check.CLOSURE, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
