package ai.speckitty.charter.domain;

import ai.speckitty.charter.domain.PackContents.FileRead;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The single owner of the pack rules: it chooses every code and writes every text. It is pure; an adapter reads
 * the files into {@link PackContents} and this class judges them. Only this class and the code enums name the
 * pack-level constants (ArchUnit enforces it).
 */
public final class PackPolicy {

    private static final String PACK_YAML = "pack.yaml";
    private static final String FRAGMENT = "drg/fragment.yaml";

    /** The rules that look at the pack as a whole; they are attached to {@code pack.yaml} and ordered last. */
    private static final Set<PackFindingCode> PACK_WIDE = EnumSet.of(
            PackFindingCode.PACK_LEVEL_MISSING,
            PackFindingCode.PACK_LEVEL_MISMATCH,
            PackFindingCode.ORG_FRAGMENT_MISSING);

    /** The plan's precedence: file group, then file name, then place. WP05 refines the order within a file. */
    private static final Comparator<PackFinding> PRECEDENCE = Comparator.comparingInt(PackPolicy::group)
            .thenComparing(PackFinding::file)
            .thenComparing(f -> String.join("/", f.place()));

    private PackPolicy() {}

    /**
     * The level rules (issue 48): a missing level is a warning, a different level is blocking.
     *
     * <p>They fire only on an absent or a read descriptor, never on an unreadable one, and never for a bare path,
     * which has no configured level to compare with.
     *
     * @param name the pack's configured name
     * @param configured the configured level; empty for a bare path
     * @param descriptor what {@code pack.yaml} gave
     * @return the level findings, attached to {@code pack.yaml} with an empty place
     */
    public static List<PackFinding> level(
            String name, Optional<PackLevel> configured, FileRead<PackDescriptor> descriptor) {
        if (configured.isEmpty()) {
            return List.of();
        }
        String lvl = configured.get().token();
        return switch (descriptor) {
            case FileRead.Absent<PackDescriptor> absent ->
                List.of(levelFinding(
                        PackFindingCode.PACK_LEVEL_MISSING,
                        lvl + " pack " + name + " has no pack.yaml; the configured level " + lvl + " is used"));
            case FileRead.Unreadable<PackDescriptor> unreadable -> List.of();
            case FileRead.Read<PackDescriptor> read -> declared(name, configured.get(), read.value());
        };
    }

    private static List<PackFinding> declared(String name, PackLevel configured, PackDescriptor descriptor) {
        Optional<PackLevel> declared = descriptor.declaredLevel();
        if (declared.isEmpty()) {
            return List.of(levelFinding(
                    PackFindingCode.PACK_LEVEL_MISSING,
                    "pack.yaml of " + name + " has no pack_level; the configured level " + configured.token()
                            + " is used"));
        }
        if (declared.get() != configured) {
            return List.of(levelFinding(
                    PackFindingCode.PACK_LEVEL_MISMATCH,
                    "Pack '" + name + "' is configured as " + configured.token() + " but declares pack_level "
                            + declared.get().token()));
        }
        return List.of();
    }

    private static PackFinding levelFinding(PackFindingCode code, String detail) {
        return new PackFinding(code, PACK_YAML, List.of(), detail);
    }

    /**
     * Runs every rule. For now that is the level rules; later rules join here.
     *
     * @param contents what the adapter read
     * @return the verdict, with the findings in precedence order
     */
    public static PackVerdict judge(PackContents contents) {
        List<PackFinding> findings = new ArrayList<>(
                level(contents.root().name(), contents.root().configuredLevel(), contents.descriptor()));
        findings.sort(PRECEDENCE);
        return new PackVerdict(contents.files(), findings);
    }

    private static int group(PackFinding finding) {
        if (PACK_WIDE.contains(finding.code())) {
            return 4;
        }
        String file = finding.file();
        if (PACK_YAML.equals(file)) {
            return 1;
        }
        if (FRAGMENT.equals(file)) {
            return 3;
        }
        return file.endsWith(".graph.yaml") && file.indexOf('/') < 0 ? 2 : 5;
    }

    /**
     * The one restriction shared by a load and a validation: the files a load reads.
     *
     * @param contents what the adapter read
     * @return {@code pack.yaml}, the root shards and {@code drg/fragment.yaml}, as pack-relative paths
     */
    public static List<String> loadPath(PackContents contents) {
        List<String> files = new ArrayList<>();
        files.add(PACK_YAML);
        contents.shards().forEach(shard -> files.add(shard.file()));
        files.add(FRAGMENT);
        return List.copyOf(files);
    }

    /**
     * Loads a pack: refuses with the first blocking finding on the load path, or builds the pack with the warnings
     * on the load path as its diagnostics.
     *
     * @param contents what the adapter read
     * @param graph the pack's graph, which the adapter built from the same files
     * @return the loaded pack
     * @throws CharterRefusal when a finding on the load path blocks
     * @throws IllegalArgumentException when the pack has no configured level: a bare path is validate-only
     */
    public static CharterPack load(PackContents contents, DoctrineGraph graph) {
        PackLevel configured = contents.root()
                .configuredLevel()
                .orElseThrow(() -> new IllegalArgumentException("load needs a configured level"));
        List<String> loadPath = loadPath(contents);
        List<PackFinding> findings = judge(contents).findings().stream()
                .filter(f -> loadPath.contains(f.file()))
                .toList();
        for (PackFinding finding : findings) {
            if (finding.severity() == PackFindingCode.Severity.BLOCKING) {
                throw finding.toRefusal();
            }
        }
        Optional<PackDescriptor> descriptor = switch (contents.descriptor()) {
            case FileRead.Read<PackDescriptor> read -> Optional.of(read.value());
            case FileRead.Absent<PackDescriptor> absent -> Optional.empty();
            case FileRead.Unreadable<PackDescriptor> unreadable ->
                throw new CharterRefusal(
                        RefusalCode.PACK_UNREADABLE,
                        "pack.yaml of " + contents.root().name() + " is unreadable");
        };
        return new CharterPack(
                contents.root().name(),
                configured,
                descriptor,
                graph,
                findings.stream().map(PackFinding::toDiagnostic).toList());
    }
}
