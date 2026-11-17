package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.ArtifactFacts;
import java.util.List;

/**
 * The content rules of a pack's assets (#554, FR-001, FR-017, T032): an asset id is declared by one sidecar. It is a
 * kernel rule over the facts of the one read ({@link PackContents}); it reads no file and knows no surface.
 *
 * <p>Not here (S2: format and schema checks are the edge's, and each check has one implementation): a path that escapes
 * {@code assets/}, a missing payload and the MIME format are the one reader's, through the store's sidecar rule and its
 * precheck (R2 rows 12 to 14).
 */
public final class AssetRules {

    /**
     * The repeated asset id (S2, R2 row 15): two sidecars that declare one id are one
     * {@link PackCodes#DUPLICATE_ARTIFACT_ID} at the later sidecar in path order, naming the first; a consistency rule,
     * so every surface that runs the content rules reports it.
     */
    public static final Rule<PackContents> ID_REPEATED = ArtifactRules.ID_REPEATED_AMONG.on(AssetRules::sidecars);

    /** Every asset rule, in this class's order. */
    public static final ModularRuleset<PackContents> RULES =
            ModularRuleset.<PackContents>emptyRules().must(ID_REPEATED);

    private AssetRules() {}

    /** The artifact facts of the asset sidecars: every sidecar, the later ones of a repeated id included. */
    static List<ArtifactFacts> sidecars(PackContents contents) {
        return contents.artifacts().stream()
                .filter(artifact -> ArtifactKind.ASSET.token().equals(artifact.kind()))
                .toList();
    }
}
