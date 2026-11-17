package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.ArtifactFacts;
import ai.speckitty.charter.packs.domain.PackContents.AssetFacts;
import ai.speckitty.charter.packs.domain.PackRelativePaths;
import java.util.List;

/**
 * The content rules of a pack's assets (#554, FR-001, FR-017, T032): an asset's path stays inside {@code assets/}, and
 * an asset id is declared by one sidecar. Each is a kernel rule over the facts of the one read ({@link PackContents});
 * it reads no file and knows no surface.
 *
 * <p>Not here: a sidecar whose payload is missing is a read fault of the one reader, and the MIME format is its
 * precheck (S2).
 */
public final class AssetRules {

    /**
     * The escape, judged over one sidecar's facts (C-2): a path that leaves {@code assets/} as written
     * ({@link PackRelativePaths#leavesRoot}), or once the reader resolved it ({@link AssetFacts#containedInAssetsRoot},
     * through a symbolic link), is one {@link PackCodes#ASSET} at the sidecar (R2 row 13, open case 1: one finding, not
     * two). An empty path is the schema's to refuse, so it is not judged.
     *
     * <p>The facts hold the first sidecar of each id only, so a later sidecar of a repeated id is judged by
     * {@link #ID_REPEATED} alone. A sidecar the read refused is the read's fault: the composition that runs this rule
     * skips it (WP07, T035), so its escape is reported once.
     */
    static final Rule<AssetFacts> PATH_ESCAPES_IN_SIDECAR = AssetRules::escape;

    /** {@link #PATH_ESCAPES_IN_SIDECAR} over every asset of the pack, in read order. */
    public static final Rule<PackContents> PATH_ESCAPES = PATH_ESCAPES_IN_SIDECAR.forEach(PackContents::assets);

    /**
     * The repeated asset id (S2, R2 row 15): two sidecars that declare one id are one
     * {@link PackCodes#DUPLICATE_ARTIFACT_ID} at the later sidecar in path order, naming the first; a consistency rule,
     * so every surface that runs the content rules reports it.
     */
    public static final Rule<PackContents> ID_REPEATED = ArtifactRules.ID_REPEATED_AMONG.on(AssetRules::sidecars);

    /** Every asset rule, in this class's order. */
    public static final ModularRuleset<PackContents> RULES =
            ModularRuleset.<PackContents>emptyRules().must(PATH_ESCAPES).must(ID_REPEATED);

    private AssetRules() {}

    static List<Reason> escape(AssetFacts asset) {
        if (asset.path().isEmpty() || (asset.containedInAssetsRoot() && !PackRelativePaths.leavesRoot(asset.path()))) {
            return List.of();
        }
        return List.of(Findings.at(
                PackCodes.ASSET, asset.file(), List.of(), asset.file() + ": " + Paths.assetPathEscape(asset.path())));
    }

    /** The artifact facts of the asset sidecars: every sidecar, the later ones of a repeated id included. */
    static List<ArtifactFacts> sidecars(PackContents contents) {
        return contents.artifacts().stream()
                .filter(artifact -> ArtifactKind.ASSET.token().equals(artifact.kind()))
                .toList();
    }
}
