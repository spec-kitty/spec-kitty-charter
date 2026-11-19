package ai.speckitty.charter.packs.golden;

import static ai.speckitty.charter.packs.fixtures.FixtureUlids.registeredIdOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.speckitty.charter.kernel.domain.PackDependency;
import ai.speckitty.charter.kernel.domain.PackDescriptor;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackLevel;
import ai.speckitty.charter.kernel.domain.PackOverride;
import ai.speckitty.charter.kernel.domain.PackPaths;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.domain.RefusalPack;
import ai.speckitty.charter.packs.domain.ConfiguredPack;
import ai.speckitty.charter.packs.domain.PackCompositionResolver;
import ai.speckitty.charter.packs.domain.PackCompositionResolver.PackUnderResolution;
import ai.speckitty.charter.packs.fixtures.FixtureUlids;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

/**
 * The golden snapshot of the offering's composition refusals (#554, IC-01, T005): for every refusal case of
 * {@code PackCompositionResolverTest}, and for multi-fault sets built here, what the offering's composition sequence
 * answers today: the refusal's code, its detail and its {@link RefusalPack} list, or the composed order. WP08's
 * collect-all checks must reproduce {@code golden/composition.json} byte for byte, the first refusal included.
 *
 * <p>The sequence is the resolver's part of {@code OfferingReadService#composed}, in its order: {@link
 * PackCompositionResolver#resolveRooted}, then each member's references ({@link PackCompositionResolver#checkReferences}
 * over {@link PackCompositionResolver#resolveReference}), then the first of {@link PackCompositionResolver#presetContests},
 * then {@link PackCompositionResolver#refuseCaseOnlyMatches}. The delegation and step-enhancement checks the offering
 * runs between them are not the resolver's, and no case here writes either. Nor is the offering's
 * {@code refuseTargetOnlyAnUnreadablePackCouldDeclare}: every pack here is readable, so it never refuses.
 *
 * <p>The file is written only by {@code -Dgolden.update=true}, as {@code golden/surfaces.json} is.
 */
@DisplayName("the offering's composition answers what golden/composition.json recorded (#554)")
class CompositionGoldenTest {

    private static final String DIRECTIVE = "directive:DIRECTIVE_009";

    /** The rooted fixture of {@code PackCompositionResolverTest.Rooted}: alpha depends on beta, gamma is unrelated. */
    private static final PackUnderResolution BETA = pack(u("BETA"), "beta", "1.0.0", PackLevel.ORG);

    private static final PackUnderResolution ALPHA =
            pack(u("ALPHA"), "alpha", "1.0.0", PackLevel.ORG, depends(u("BETA"), ">=1.0"));
    private static final PackUnderResolution GAMMA = pack(u("GAMMA"), "gamma", "1.0.0", PackLevel.ORG);

    /**
     * One compose: the packs available, the roots it is rooted at, and what each member's facts say.
     *
     * @param name the golden file's key
     * @param packs the available packs, in the order the read lists them
     * @param roots the configured ids the compose is rooted at; every pack when empty
     * @param presets the preset ids each pack holds, by descriptor {@code pack_id}
     * @param mentions the URNs each pack's presets and policy name, by descriptor {@code pack_id}
     * @param references the references each pack writes, as written, by descriptor {@code pack_id}
     */
    record Case(
            String name,
            List<PackUnderResolution> packs,
            List<String> roots,
            Map<String, List<String>> presets,
            Map<String, List<String>> mentions,
            Map<String, List<String>> references) {

        Case(String name, PackUnderResolution... packs) {
            this(name, List.of(packs), List.of(), Map.of(), Map.of(), Map.of());
        }

        Case rootedAt(String... ids) {
            return new Case(name, packs, List.of(ids), presets, mentions, references);
        }

        Case presets(String packId, String... ids) {
            return new Case(name, packs, roots, with(presets, packId, ids), mentions, references);
        }

        Case mentions(String packId, String... urns) {
            return new Case(name, packs, roots, presets, with(mentions, packId, urns), references);
        }

        Case references(String packId, String... written) {
            return new Case(name, packs, roots, presets, mentions, with(references, packId, written));
        }

        private static Map<String, List<String>> with(Map<String, List<String>> map, String key, String... values) {
            Map<String, List<String>> copy = new LinkedHashMap<>(map);
            copy.put(key, List.of(values));
            return Map.copyOf(copy);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static List<Case> cases() {
        List<Case> cases = new ArrayList<>(resolverRefusals());
        cases.addAll(multiFaultSets());
        return List.copyOf(cases);
    }

    /** Every refusal case of {@code PackCompositionResolverTest}, named as its test is. */
    private static List<Case> resolverRefusals() {
        List<Case> cases = new ArrayList<>(cyclesPresetsAndMentions());
        cases.addAll(dependencyRefusals());
        cases.addAll(siblingContests());
        cases.addAll(distributionSettlements());
        cases.addAll(referencesAndRootedComposes());
        cases.addAll(levelViolations());
        return cases;
    }

    /** The parent cycles, the preset contests and a mention that matches only by case. */
    private static List<Case> cyclesPresetsAndMentions() {
        return List.of(
                new Case("parent-names-itself", extending(u("LOOP"), "loop", PackLevel.ORG, u("LOOP"))),
                new Case(
                        "parents-name-each-other",
                        extending(u("FIRST"), "first", PackLevel.ORG, u("SECOND")),
                        extending(u("SECOND"), "second", PackLevel.ORG, u("FIRST"))),
                new Case(
                        "parent-cycle-through-a-dependency",
                        pack(u("PARENT"), "parent", "1.0.0", PackLevel.ORG, depends(u("CHILD"), ">=1.0")),
                        extending(u("CHILD"), "child", PackLevel.ORG, u("PARENT"))),
                new Case(
                                "preset-contest-of-two-unrelated-packs",
                                pack(u("LEFT"), "left", "1.0.0", PackLevel.ORG),
                                pack(u("RIGHT"), "right", "1.0.0", PackLevel.ORG))
                        .presets(u("LEFT"), "team")
                        .presets(u("RIGHT"), "team"),
                new Case(
                                "preset-contest-of-two-children-of-one-parent",
                                pack(u("PARENT"), "parent", "1.0.0", PackLevel.ORG),
                                extending(u("ONE"), "one", PackLevel.ORG, u("PARENT")),
                                extending(u("TWO"), "two", PackLevel.ORG, u("PARENT")))
                        .presets(u("ONE"), "team")
                        .presets(u("TWO"), "team"),
                new Case(
                                "mention-of-a-held-id-only-by-case",
                                defining(u("HOLD"), "holder", PackLevel.ORG, "directive:DIRECTIVE_001"),
                                defining(u("MENT"), "mentioner", PackLevel.ORG))
                        .mentions(u("MENT"), "directive:directive_001"));
    }

    /** The dependency, range, name and parent refusals. */
    private static List<Case> dependencyRefusals() {
        return List.of(
                new Case(
                        "cycle-with-a-pack-downstream-of-it",
                        pack(u("AAA"), "a", "1.0.0", PackLevel.ORG, depends(u("BBB"), ">=1.0")),
                        pack(u("BBB"), "b", "1.0.0", PackLevel.ORG, depends(u("CCC"), ">=1.0")),
                        pack(u("CCC"), "c", "1.0.0", PackLevel.ORG, depends(u("BBB"), ">=1.0"))),
                new Case(
                        "pack-depends-on-itself",
                        pack(u("SELF"), "self", "1.0.0", PackLevel.ORG, depends(u("SELF"), ">=1.0"))),
                new Case(
                        "unknown-dependency",
                        pack(u("DEP"), "dependent", "1.0.0", PackLevel.ORG, depends(u("GONE"), ">=1.0"))),
                new Case(
                        "ranges-no-version-satisfies-together",
                        pack(u("TWO"), "two", "1.0.0", PackLevel.ORG, depends(u("BASE"), ">=2.0")),
                        pack(u("ONE"), "one", "1.0.0", PackLevel.ORG, depends(u("BASE"), ">=1.0,<2")),
                        pack(u("BASE"), "base", "1.5.0", PackLevel.BUILT_IN)),
                new Case(
                        "two-packs-with-one-name",
                        pack(u("AAA"), "shared-name", "1.0.0", PackLevel.ORG),
                        pack(u("BBB"), "shared-name", "1.0.0", PackLevel.ORG)),
                new Case(
                        "parent-unresolved",
                        new PackUnderResolution(
                                configured(u("ORPH"), PackLevel.ORG),
                                new PackDescriptor(
                                        u("ORPH"),
                                        "1.0.0",
                                        Optional.of(u("MISSING")),
                                        "orphan",
                                        Optional.of(PackLevel.ORG),
                                        Optional.empty()))));
    }

    /** The sibling contests and their settlements. */
    private static List<Case> siblingContests() {
        return List.of(
                new Case(
                        "siblings-changing-one-artifact",
                        refining(u("LEFT"), "left", PackLevel.ORG, DIRECTIVE),
                        refining(u("RIGHT"), "right", PackLevel.ORG, DIRECTIVE)),
                new Case(
                        "siblings-defining-one-artifact",
                        defining(u("RIGHT"), "right", PackLevel.ORG, DIRECTIVE),
                        defining(u("LEFT"), "left", PackLevel.ORG, DIRECTIVE)),
                new Case(
                        "siblings-defining-one-mission-type-and-its-step",
                        defining(u("RIGHT"), "right", PackLevel.ORG, "mission_type:m", "action:m/implement"),
                        defining(u("LEFT"), "left", PackLevel.ORG, "mission_type:m", "action:m/implement")),
                new Case(
                        "a-qualifier-does-not-make-two-contests",
                        refining(u("LEFT"), "left", PackLevel.ORG, DIRECTIVE),
                        refining(u("RIGHT"), "right", PackLevel.ORG, "base::" + DIRECTIVE)),
                new Case(
                        "two-distribution-packs-naming-different-winners",
                        settling(u("DTWO"), "dtwo", "org::" + DIRECTIVE, u("RIGHT"), u("LEFT"), u("RIGHT")),
                        settling(u("DONE"), "done", DIRECTIVE, u("LEFT"), u("LEFT"), u("RIGHT")),
                        refining(u("RIGHT"), "right", PackLevel.ORG, DIRECTIVE),
                        refining(u("LEFT"), "left", PackLevel.ORG, DIRECTIVE)),
                new Case(
                        "a14-two-settlements-naming-two-winners",
                        settling(u("DSTR"), "distribution", DIRECTIVE, u("LEFT"), u("LEFT"), u("RIGHT")),
                        settling(u("DTWO"), "dtwo", DIRECTIVE, u("RIGHT"), u("LEFT"), u("RIGHT")),
                        refining(u("LEFT"), "left", PackLevel.ORG, DIRECTIVE),
                        refining(u("RIGHT"), "right", PackLevel.ORG, DIRECTIVE)),
                new Case(
                        "overrides-naming-a-member-outside-the-compose",
                        settling(u("DSTR"), "distribution", DIRECTIVE, u("GHOST")),
                        refining(u("LEFT"), "left", PackLevel.ORG, DIRECTIVE),
                        refining(u("RIGHT"), "right", PackLevel.ORG, DIRECTIVE)),
                new Case(
                        "overrides-naming-a-member-that-is-not-a-contestant",
                        settling(u("DSTR"), "distribution", DIRECTIVE, u("DSTR")),
                        refining(u("LEFT"), "left", PackLevel.ORG, DIRECTIVE),
                        refining(u("RIGHT"), "right", PackLevel.ORG, DIRECTIVE)));
    }

    /** Who settles a sibling contest (#358): the distribution pack only. */
    private static List<Case> distributionSettlements() {
        return List.of(
                new Case(
                        "the-distribution-pack-settles",
                        settling(u("DSTR"), "distribution", DIRECTIVE, u("LEFT"), u("LEFT"), u("RIGHT")),
                        refining(u("LEFT"), "left", PackLevel.ORG, DIRECTIVE),
                        refining(u("RIGHT"), "right", PackLevel.ORG, DIRECTIVE)),
                new Case(
                        "a-member-of-the-distribution-does-not-settle",
                        settling(u("MEMB"), "member", DIRECTIVE, u("LEFT")),
                        pack(
                                u("DSTR"),
                                "distribution",
                                "1.0.0",
                                PackLevel.ORG,
                                List.of(
                                        new PackDependency(u("LEFT"), ">=1.0"),
                                        new PackDependency(u("RIGHT"), ">=1.0"),
                                        new PackDependency(u("MEMB"), ">=1.0"))),
                        refining(u("LEFT"), "left", PackLevel.ORG, DIRECTIVE),
                        refining(u("RIGHT"), "right", PackLevel.ORG, DIRECTIVE)),
                new Case(
                        "a-pack-that-does-not-depend-on-the-contestants-does-not-settle",
                        settling(u("UNRL"), "unrelated", DIRECTIVE, u("LEFT")),
                        refining(u("LEFT"), "left", PackLevel.ORG, DIRECTIVE),
                        refining(u("RIGHT"), "right", PackLevel.ORG, DIRECTIVE)),
                new Case(
                        "a-member-beside-the-distribution-pack-is-refused",
                        settling(u("MEMB"), "member", DIRECTIVE, u("RIGHT")),
                        settling(u("DSTR"), "distribution", DIRECTIVE, u("LEFT"), u("LEFT"), u("RIGHT"), u("MEMB")),
                        refining(u("LEFT"), "left", PackLevel.ORG, DIRECTIVE),
                        refining(u("RIGHT"), "right", PackLevel.ORG, DIRECTIVE)),
                new Case(
                        "a-contestant-does-not-settle-its-own-contest",
                        settlingRefiner(u("LEFT"), "left", DIRECTIVE, u("LEFT")),
                        refining(u("RIGHT"), "right", PackLevel.ORG, DIRECTIVE)),
                new Case(
                        "two-distribution-packs-naming-one-winner",
                        settling(u("DSTR"), "distribution", DIRECTIVE, u("LEFT"), u("LEFT"), u("RIGHT")),
                        settling(u("DTWO"), "dtwo", DIRECTIVE, u("LEFT"), u("LEFT"), u("RIGHT")),
                        refining(u("LEFT"), "left", PackLevel.ORG, DIRECTIVE),
                        refining(u("RIGHT"), "right", PackLevel.ORG, DIRECTIVE)));
    }

    /** The reference refusals, and the refusals of a rooted compose. */
    private static List<Case> referencesAndRootedComposes() {
        return List.of(
                new Case(
                                "qualified-reference-to-an-artifact-the-pack-does-not-hold",
                                pack(u("WRI"), "writing", "1.0.0", PackLevel.BUILT_IN),
                                pack(u("REA"), "reader", "1.0.0", PackLevel.PROJECT, depends(u("WRI"), ">=1.0")))
                        .references(u("REA"), "writing::" + DIRECTIVE),
                new Case(
                                "qualified-reference-to-a-pack-only-another-pack-depends-on",
                                defining(u("WRI"), "writing", PackLevel.BUILT_IN, DIRECTIVE),
                                pack(u("REA"), "reader", "1.0.0", PackLevel.PROJECT),
                                pack(u("OTH"), "other", "1.0.0", PackLevel.PROJECT, depends(u("WRI"), ">=1.0")))
                        .references(u("REA"), "writing::" + DIRECTIVE),
                new Case(
                                "bare-reference-that-resolves-only-in-another-pack",
                                defining(u("WRI"), "writing", PackLevel.BUILT_IN, "anti_pattern:big-ball-of-mud"),
                                pack(u("REA"), "reader", "1.0.0", PackLevel.PROJECT))
                        .references(u("REA"), "anti_pattern:big-ball-of-mud"),
                new Case(
                                "qualified-reference-to-a-pack-outside-the-compose",
                                pack(u("REA"), "reader", "1.0.0", PackLevel.PROJECT))
                        .references(u("REA"), "writing::" + DIRECTIVE),
                new Case(
                                "rooted-parent-cycle",
                                withParent(u("FIRST"), "first", u("SECOND")),
                                withParent(u("SECOND"), "second", u("FIRST")),
                                GAMMA)
                        .rootedAt(u("FIRST")),
                new Case(
                        "whole-compose-duplicate-pack-id",
                        ALPHA,
                        BETA,
                        GAMMA,
                        configuredAs("twin", u("GAMMA"), "gamma-twin")),
                new Case(
                        "whole-compose-duplicate-name",
                        ALPHA,
                        BETA,
                        GAMMA,
                        pack(u("OTHER"), "gamma", "1.0.0", PackLevel.ORG)),
                new Case(
                                "rooted-missing-dependency",
                                pack(u("DEP"), "dependent", "1.0.0", PackLevel.ORG, depends(u("GONE"), ">=1.0")),
                                GAMMA)
                        .rootedAt(u("DEP")),
                new Case(
                                "rooted-duplicate-pack-id-inside-the-closure",
                                ALPHA,
                                BETA,
                                configuredAs("beta-twin", u("BETA"), "beta-twin"),
                                GAMMA)
                        .rootedAt(u("ALPHA")),
                new Case(
                                "rooted-dependency-cycle",
                                pack(u("FIRST"), "first", "1.0.0", PackLevel.ORG, depends(u("SECOND"), ">=1.0")),
                                pack(u("SECOND"), "second", "1.0.0", PackLevel.ORG, depends(u("FIRST"), ">=1.0")),
                                GAMMA)
                        .rootedAt(u("FIRST")));
    }

    /** The refused rows of the level matrix: a dependency or a parent further from built-in than the pack's own level. */
    private static List<Case> levelViolations() {
        List<PackLevel> fromBuiltIn = List.of(PackLevel.BUILT_IN, PackLevel.ORG, PackLevel.PROJECT);
        List<Case> cases = new ArrayList<>();
        for (boolean parentEdge : new boolean[] {false, true}) {
            for (PackLevel own : fromBuiltIn) {
                for (PackLevel target : fromBuiltIn) {
                    if (fromBuiltIn.indexOf(target) <= fromBuiltIn.indexOf(own)) {
                        continue;
                    }
                    PackUnderResolution ownPack = parentEdge
                            ? new PackUnderResolution(
                                    configured(u("OWN0"), own),
                                    new PackDescriptor(
                                            u("OWN0"),
                                            "1.0.0",
                                            Optional.of(u("TRGT")),
                                            "own",
                                            Optional.of(own),
                                            Optional.empty()))
                            : pack(u("OWN0"), "own", "1.0.0", own, depends(u("TRGT"), ">=1.0"));
                    cases.add(new Case(
                            "level-violation-" + (parentEdge ? "parent" : "dependency") + "-" + own.token() + "-to-"
                                    + target.token(),
                            ownPack,
                            pack(u("TRGT"), "target", "1.0.0", target)));
                }
            }
        }
        return cases;
    }

    /** Sets with more than one fault: which one the offering answers today is the order WP08 keeps. */
    private static List<Case> multiFaultSets() {
        return List.of(
                new Case(
                        "multi-cycle-and-unknown-dependency",
                        pack(u("AAA"), "a", "1.0.0", PackLevel.ORG, depends(u("BBB"), ">=1.0")),
                        pack(u("BBB"), "b", "1.0.0", PackLevel.ORG, depends(u("AAA"), ">=1.0")),
                        pack(u("CCC"), "c", "1.0.0", PackLevel.ORG, depends(u("GONE"), ">=1.0"))),
                new Case(
                        "multi-name-conflict-and-sibling-contest",
                        defining(u("LEFT"), "left", PackLevel.ORG, DIRECTIVE),
                        defining(u("RIGHT"), "right", PackLevel.ORG, DIRECTIVE),
                        pack(u("AAA"), "shared-name", "1.0.0", PackLevel.ORG),
                        pack(u("BBB"), "shared-name", "1.0.0", PackLevel.ORG)),
                new Case(
                                "multi-preset-contest-and-unqualified-reference",
                                defining(u("LEFT"), "left", PackLevel.ORG, "anti_pattern:big-ball-of-mud"),
                                pack(u("RIGHT"), "right", "1.0.0", PackLevel.ORG))
                        .presets(u("LEFT"), "team")
                        .presets(u("RIGHT"), "team")
                        .references(u("RIGHT"), "anti_pattern:big-ball-of-mud"),
                new Case(
                        "multi-duplicate-id-and-out-of-range",
                        pack(u("BASE"), "base", "1.0.0", PackLevel.BUILT_IN),
                        new PackUnderResolution(
                                configured("base-twin", PackLevel.BUILT_IN),
                                new PackDescriptor(
                                        u("BASE"),
                                        "1.0.0",
                                        Optional.empty(),
                                        "base-twin",
                                        Optional.of(PackLevel.BUILT_IN),
                                        Optional.empty())),
                        pack(u("DEP"), "dependent", "1.0.0", PackLevel.ORG, depends(u("BASE"), ">=2.0"))),
                new Case(
                        "multi-level-violation-and-parent-unresolved",
                        pack(u("OWN0"), "own", "1.0.0", PackLevel.BUILT_IN, depends(u("TRGT"), ">=1.0")),
                        pack(u("TRGT"), "target", "1.0.0", PackLevel.PROJECT),
                        withParent(u("ORPH"), "orphan", u("MISSING"))),
                new Case(
                                "multi-sibling-contest-and-case-only-mention",
                                refining(u("LEFT"), "left", PackLevel.ORG, DIRECTIVE),
                                refining(u("RIGHT"), "right", PackLevel.ORG, DIRECTIVE),
                                defining(u("HOLD"), "holder", PackLevel.ORG, "directive:DIRECTIVE_001"))
                        .mentions(u("LEFT"), "directive:directive_001"),
                new Case(
                                "multi-unknown-dependency-and-missing-artifact",
                                pack(u("WRI"), "writing", "1.0.0", PackLevel.BUILT_IN),
                                pack(u("REA"), "reader", "1.0.0", PackLevel.PROJECT, depends(u("WRI"), ">=1.0")),
                                pack(u("DEP"), "dependent", "1.0.0", PackLevel.ORG, depends(u("GONE"), ">=1.0")))
                        .references(u("REA"), "writing::" + DIRECTIVE),
                new Case(
                                "multi-unqualified-reference-preset-contest-and-case-only-mention",
                                defining(u("WRI"), "writing", PackLevel.ORG, "anti_pattern:big-ball-of-mud"),
                                defining(u("HOLD"), "holder", PackLevel.ORG, "directive:DIRECTIVE_001"),
                                pack(u("REA"), "reader", "1.0.0", PackLevel.ORG))
                        .references(u("REA"), "anti_pattern:big-ball-of-mud")
                        .presets(u("WRI"), "team")
                        .presets(u("REA"), "team")
                        .mentions(u("REA"), "directive:directive_001"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("each compose answers as recorded: the refusal's code, detail and packs, or the composed order")
    void eachComposeAnswersAsRecorded(Case compose) {
        assumeFalse(Golden.updating(), "golden.update is true: the file is being rewritten, not compared");
        JsonNode recorded = Golden.read(Golden.COMPOSITION).get(compose.name());
        assertThat(recorded)
                .as("%s has no entry in %s", compose.name(), Golden.COMPOSITION)
                .isNotNull();

        assertThat(Golden.render(offer(compose)))
                .as(
                        "%s: what the offering answers today differs from its entry in %s",
                        compose.name(), Golden.COMPOSITION)
                .isEqualTo(Golden.render(Objects.requireNonNull(recorded)));
    }

    @Test
    @DisplayName("the file holds one entry per case and nothing else, in case order, written as the writer writes it")
    void theFileHoldsExactlyTheCases() {
        assumeFalse(Golden.updating(), "golden.update is true: the file is being rewritten, not compared");
        JsonNode recorded = Golden.read(Golden.COMPOSITION);

        assertThat(new ArrayList<>(recorded.propertyNames()))
                .containsExactlyElementsOf(cases().stream().map(Case::name).toList());
        assertThat(Golden.text(Golden.COMPOSITION)).isEqualTo(Golden.render(recorded));
    }

    @Test
    @DisplayName("at least five multi-fault sets are recorded, and every case of the file names a refusal or a compose")
    void theMultiFaultSetsAreThere() {
        assertThat(multiFaultSets()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(cases()).extracting(Case::name).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("-Dgolden.update=true rewrites golden/composition.json from today's answers")
    void rewriteTheFile() throws IOException {
        assumeTrue(Golden.updating(), "golden.update is not true");
        Map<String, Object> all = new LinkedHashMap<>();
        cases().forEach(compose -> all.put(compose.name(), offer(compose)));
        Golden.write(Golden.COMPOSITION, all);
    }

    /** The offering's composition sequence over the case, and what it answers. */
    private static Map<String, Object> offer(Case compose) {
        List<PackIdentifier> roots = compose.roots().isEmpty()
                ? compose.packs().stream().map(pack -> pack.configured().id()).toList()
                : compose.roots().stream().map(FixtureUlids::registeredIdOf).toList();
        try {
            List<PackUnderResolution> composed = PackCompositionResolver.resolveRooted(compose.packs(), roots);
            for (PackUnderResolution pack : composed) {
                String packId = pack.descriptor().packId();
                PackCompositionResolver.checkReferences(
                        composed,
                        packId,
                        compose.references().getOrDefault(packId, List.of()).stream()
                                .map(written -> PackCompositionResolver.resolveReference(
                                        composed, pack.configured().id(), PackQualifiedReference.parse(written)))
                                .toList());
            }
            PackCompositionResolver.refuseFirst(PackCompositionResolver.presetContests(
                    composed,
                    pack -> compose.presets().getOrDefault(pack.descriptor().packId(), List.of())));
            PackCompositionResolver.refuseCaseOnlyMatches(
                    composed,
                    pack -> compose.mentions().getOrDefault(pack.descriptor().packId(), List.of()));
            return Map.of(
                    "composed",
                    composed.stream()
                            .map(pack -> pack.configured().id().value())
                            .toList());
        } catch (PackCompositionResolver.Refusal refusal) {
            Map<String, Object> refused = new LinkedHashMap<>();
            refused.put("code", refusal.failure().code().token());
            refused.put("detail", refusal.detail());
            List<Map<String, Object>> packs = new ArrayList<>();
            for (RefusalPack pack : refusal.packs()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("descriptorPackId", pack.descriptorPackId().value());
                row.put("packId", pack.packId().value());
                row.put("name", pack.name());
                packs.add(row);
            }
            refused.put("packs", packs);
            return Map.of("refused", refused);
        }
    }

    // ---- the fixtures, as PackCompositionResolverTest builds them ----------------------------------------------

    private static List<PackDependency> depends(String packId, String range) {
        return List.of(new PackDependency(packId, range));
    }

    private static ConfiguredPack configured(String id, PackLevel level) {
        return new ConfiguredPack(registeredIdOf(id), level);
    }

    private static PackUnderResolution pack(String id, String name, String version, PackLevel level) {
        return pack(id, name, version, level, List.of());
    }

    private static PackUnderResolution pack(
            String id, String name, String version, PackLevel level, List<PackDependency> dependencies) {
        return new PackUnderResolution(
                configured(id, level),
                new PackDescriptor(
                        id,
                        version,
                        Optional.empty(),
                        name,
                        Optional.of(level),
                        Optional.empty(),
                        PackPaths.none(),
                        dependencies));
    }

    private static PackUnderResolution refining(String id, String name, PackLevel level, String artifact) {
        return new PackUnderResolution(
                configured(id, level),
                new PackDescriptor(id, "1.0.0", Optional.empty(), name, Optional.of(level), Optional.empty()),
                List.of(artifact));
    }

    private static PackUnderResolution settling(
            String id, String name, String artifact, String member, String... members) {
        return new PackUnderResolution(
                configured(id, PackLevel.ORG),
                new PackDescriptor(
                        id,
                        "1.0.0",
                        Optional.empty(),
                        name,
                        Optional.of(PackLevel.ORG),
                        Optional.empty(),
                        PackPaths.none(),
                        Arrays.stream(members)
                                .map(target -> new PackDependency(target, ">=1.0"))
                                .toList(),
                        List.of(new PackOverride(artifact, member))));
    }

    /** A sibling that changes {@code artifact} and declares an edge settling it. */
    private static PackUnderResolution settlingRefiner(String id, String name, String artifact, String member) {
        PackUnderResolution settler = settling(id, name, artifact, member);
        return new PackUnderResolution(settler.configured(), settler.descriptor(), List.of(artifact));
    }

    private static PackUnderResolution extending(String id, String name, PackLevel level, String parent) {
        return new PackUnderResolution(
                configured(id, level),
                new PackDescriptor(id, "1.0.0", Optional.of(parent), name, Optional.of(level), Optional.empty()));
    }

    private static PackUnderResolution withParent(String id, String name, String parent) {
        return extending(id, name, PackLevel.ORG, parent);
    }

    private static PackUnderResolution defining(String id, String name, PackLevel level, String... artifacts) {
        return new PackUnderResolution(
                configured(id, level),
                new PackDescriptor(id, "1.0.0", Optional.empty(), name, Optional.of(level), Optional.empty()),
                List.of(),
                Arrays.asList(artifacts));
    }

    /** A pack configured under one id whose descriptor declares another {@code pack_id}. */
    private static PackUnderResolution configuredAs(String configuredId, String packId, String name) {
        return new PackUnderResolution(
                configured(configuredId, PackLevel.ORG),
                new PackDescriptor(
                        packId, "1.0.0", Optional.empty(), name, Optional.of(PackLevel.ORG), Optional.empty()));
    }

    /** A descriptor {@code pack_id} in the order-keeping fixture encoding ({@link FixtureUlids#ordered}). */
    private static String u(String mnemonic) {
        return FixtureUlids.ordered(mnemonic);
    }
}
