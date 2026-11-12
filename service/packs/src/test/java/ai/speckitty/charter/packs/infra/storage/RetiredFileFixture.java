package ai.speckitty.charter.packs.infra.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

/**
 * The retired-file fixtures of ADR-0044 decision 6 (#194, FR-008): the bundled table's retired families with their
 * reasons, and one retired file of every family at the root layout and at the {@code missions/} layout.
 */
final class RetiredFileFixture {

    /**
     * The retired families, glob to reason, in table order: the ADR-0044 decision 6 families first, then the pack-level
     * files #451 retires ({@code pack.md}, {@code PROVENANCE.md}), then the org charter #431 retires. A later
     * retirement is appended after these, so the order the table is read in stays the order of this list.
     */
    static final SequencedMap<String, String> RULES = rules();

    /**
     * One retired file of every family at the layout the qa and cave-kitty packs held, the mission tier at the pack
     * root, each with the glob that retires it.
     */
    static final SequencedMap<String, String> ROOT_LAYOUT = paths(
            "mission_types/qa.yaml", "*/mission_types/*.yaml",
            "mission_types/qa/governance-profile.yaml", "*/mission_types/*/governance-profile.yaml",
            "mission-steps/qa/review/step.yaml", "*/mission-steps/**/*",
            "mission-steps/qa/review/prompt.md", "*/mission-steps/**/*",
            "mission-steps/qa/review/guidelines.md", "*/mission-steps/**/*",
            "missions/qa/command-templates/review.md", "*/missions/*/command-templates/*",
            "pack.md", "*/pack.md",
            "PROVENANCE.md", "*/PROVENANCE.md",
            "org-charter.yaml", "*/org-charter.yaml");

    /** One retired file of every family at the layout the built-in pack held, under {@code missions/}. */
    static final SequencedMap<String, String> MISSIONS_LAYOUT = paths(
            "missions/mission_types/plan.yaml", "*/missions/mission_types/*.yaml",
            "missions/mission_types/plan/governance-profile.yaml", "*/missions/mission_types/*/governance-profile.yaml",
            "missions/plan/mission.yaml", "*/missions/*/mission.yaml",
            "missions/plan/mission-runtime.yaml", "*/missions/*/mission-runtime.yaml",
            "missions/plan/governance-profile.yaml", "*/missions/*/governance-profile.yaml",
            "missions/plan/expected-artifacts.yaml", "*/missions/*/expected-artifacts.yaml",
            "missions/plan/actions/specify/index.yaml", "*/missions/*/actions/*/index.yaml",
            "missions/mission-steps/plan/specify/step.yaml", "*/missions/mission-steps/**/*",
            "missions/mission-steps/plan/specify/prompt.md", "*/missions/mission-steps/**/*",
            "missions/mission-steps/plan/specify/guidelines.md", "*/missions/mission-steps/**/*");

    /**
     * Payloads elsewhere in a pack that only share a name with a retired family: an anchored rule never retires one.
     */
    static final List<String> NAMESAKES = List.of(
            "assets/img/governance-profile.yaml",
            "assets/actions/x/index.yaml",
            "templates/mission-steps/checklist.md",
            "templates/mission_types/x.yaml",
            "missions/research/templates/actions/plan/index.yaml",
            "missions/research/templates/governance-profile.yaml",
            "missions/research/templates/mission-steps/x.md",
            "assets/PROVENANCE.md",
            "assets/org-charter.yaml",
            "missions/research/templates/pack.md");

    /**
     * Files that share a name with a retired pack-level file but are not at the pack root: only the root file is
     * retired, so none of these matches a retired rule.
     */
    static final List<String> NOT_AT_THE_ROOT =
            List.of("docs/pack.md", "docs/PROVENANCE.md", "sub/pack.md", "sub/org-charter.yaml");

    /**
     * Files at the pack root that differ only in letter case from a rule's file, each with the rule it is refused
     * under (FR-022): the pack document, and the retired pack-level files.
     */
    static final SequencedMap<String, String> CASE_VARIANTS = paths(
            "Readme.md", "*/README.md",
            "readme.md", "*/README.md",
            "README.MD", "*/README.md",
            "Pack.md", "*/pack.md",
            "PACK.MD", "*/pack.md",
            "PROVENANCE.MD", "*/PROVENANCE.md",
            "Provenance.md", "*/PROVENANCE.md",
            "Org-Charter.yaml", "*/org-charter.yaml",
            // The wider scope (any root-anchored files rule): a case variant of the descriptor, the policy and the
            // manifest is refused by name too, not left unmodelled.
            "Pack.yaml", "*/pack.yaml",
            "POLICY.yaml", "*/policy.yaml",
            "Pack-Manifest.yaml", "*/pack-manifest.yaml");

    /** A {@code pack.yaml} the store reads. */
    static final String PACK_YAML = """
            pack_id: 01ARWG13C000000000000000FG
            pack_version: 1.0.0
            name: qa
            pack_level: built-in
            """;

    private RetiredFileFixture() {}

    /**
     * Writes a pack: its {@code pack.yaml} and each of {@code files} with a line of text.
     *
     * @param pack the pack directory
     * @param files the pack-relative files
     * @return the pack directory
     */
    static Path plant(Path pack, Iterable<String> files) {
        try {
            Files.createDirectories(pack);
            Files.writeString(pack.resolve(PackFileSet.PACK_YAML), PACK_YAML);
            for (String file : files) {
                Files.createDirectories(pack.resolve(file).getParent());
                Files.writeString(pack.resolve(file), "a: 1\n");
            }
            return pack;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SequencedMap<String, String> rules() {
        SequencedMap<String, String> out = new LinkedHashMap<>();
        out.put(
                "*/mission_types/*.yaml",
                "the mission-type file moved to missions/<id>/<id>.mission-type.yaml (ADR-0044)");
        out.put(
                "*/missions/mission_types/*.yaml",
                "the mission-type file moved to missions/<id>/<id>.mission-type.yaml (ADR-0044)");
        out.put("*/missions/*/mission.yaml", "folded into the mission-type file (ADR-0044)");
        out.put("*/missions/*/mission-runtime.yaml", "folded into the mission-type file (ADR-0044)");
        out.put("*/missions/*/governance-profile.yaml", "folded into the mission-type file (ADR-0044)");
        out.put("*/mission_types/*/governance-profile.yaml", "folded into the mission-type file (ADR-0044)");
        out.put("*/missions/mission_types/*/governance-profile.yaml", "folded into the mission-type file (ADR-0044)");
        out.put("*/missions/*/expected-artifacts.yaml", "folded into the mission-type steps (ADR-0044)");
        out.put("*/missions/*/actions/*/index.yaml", "folded into the mission-type steps' references (ADR-0044)");
        out.put("*/mission-steps/**/*", "folded into the mission-type steps and their companions (ADR-0044)");
        out.put("*/missions/mission-steps/**/*", "folded into the mission-type steps and their companions (ADR-0044)");
        out.put("*/missions/*/command-templates/*", "folded into the mission-type steps' prompts (ADR-0044)");
        out.put("*/pack.md", "the pack document is README.md at the pack root (#451, ADR-0042 as amended 2026-11-11)");
        out.put("*/PROVENANCE.md", "folded into README.md as its provenance section (#451)");
        out.put(
                "*/org-charter.yaml",
                "org enforcement is an activation preset with must-have items plus policy.yaml levels (#431)");
        return out;
    }

    private static SequencedMap<String, String> paths(String... pairs) {
        SequencedMap<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put(pairs[i], pairs[i + 1]);
        }
        return out;
    }

    /** The two layouts together. */
    static Map<String, String> bothLayouts() {
        Map<String, String> out = new LinkedHashMap<>(ROOT_LAYOUT);
        out.putAll(MISSIONS_LAYOUT);
        return out;
    }
}
