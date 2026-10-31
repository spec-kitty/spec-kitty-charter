package ai.speckitty.charter.packs.infra.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;

/**
 * The retired-file fixtures of ADR-0044 decision 6 (#194, FR-008): the bundled table's retired families with their
 * reasons, and one retired file of every family at the root layout and at the {@code missions/} layout.
 */
final class RetiredFileFixture {

    /** The retired families, glob to reason, in the order ADR-0044 decision 6 names them. */
    static final SequencedMap<String, String> RULES = rules();

    /**
     * One retired file of every family a pack can hold at its root, each with the glob that retires it: the old
     * {@code mission_types/}, {@code mission-steps/} and {@code actions/} directories of the qa pack's layout.
     */
    static final SequencedMap<String, String> ROOT_LAYOUT = paths(
            "mission_types/qa.yaml", "**/mission_types/*.yaml",
            "governance-profile.yaml", "**/governance-profile.yaml",
            "actions/review/index.yaml", "**/actions/*/index.yaml",
            "mission-steps/review/step.yaml", "**/mission-steps/**/*",
            "mission-steps/review/prompt.md", "**/mission-steps/**/*",
            "mission-steps/review/guidelines.md", "**/mission-steps/**/*");

    /** One retired file of every family under {@code missions/<type>/}, each with the glob that retires it. */
    static final SequencedMap<String, String> MISSIONS_LAYOUT = paths(
            "missions/mission_types/plan.yaml", "**/mission_types/*.yaml",
            "missions/plan/mission.yaml", "**/missions/*/mission.yaml",
            "missions/plan/mission-runtime.yaml", "**/missions/*/mission-runtime.yaml",
            "missions/plan/governance-profile.yaml", "**/governance-profile.yaml",
            "missions/plan/expected-artifacts.yaml", "**/missions/*/expected-artifacts.yaml",
            "missions/plan/actions/specify/index.yaml", "**/actions/*/index.yaml",
            "missions/plan/mission-steps/specify/step.yaml", "**/mission-steps/**/*",
            "missions/plan/mission-steps/specify/prompt.md", "**/mission-steps/**/*",
            "missions/plan/mission-steps/specify/guidelines.md", "**/mission-steps/**/*",
            "missions/plan/command-templates/specify.md", "**/missions/*/command-templates/*");

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
                "**/mission_types/*.yaml",
                "the mission-type file moved to missions/<id>/<id>.mission-type.yaml (ADR-0044)");
        out.put("**/missions/*/mission.yaml", "folded into the mission-type file (ADR-0044)");
        out.put("**/missions/*/mission-runtime.yaml", "folded into the mission-type file (ADR-0044)");
        out.put("**/governance-profile.yaml", "folded into the mission-type file (ADR-0044)");
        out.put("**/missions/*/expected-artifacts.yaml", "folded into the mission-type steps (ADR-0044)");
        out.put("**/actions/*/index.yaml", "folded into the mission-type steps' references (ADR-0044)");
        out.put("**/mission-steps/**/*", "folded into the mission-type steps and their companions (ADR-0044)");
        out.put("**/missions/*/command-templates/*", "folded into the mission-type steps' prompts (ADR-0044)");
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
