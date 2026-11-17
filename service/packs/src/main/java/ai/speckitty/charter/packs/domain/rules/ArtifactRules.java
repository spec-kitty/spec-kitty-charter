package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import ai.speckitty.charter.kernel.validation.Reason;
import ai.speckitty.charter.kernel.validation.Rule;
import ai.speckitty.charter.packs.domain.PackCodes;
import ai.speckitty.charter.packs.domain.PackContents;
import ai.speckitty.charter.packs.domain.PackContents.ArtifactFacts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The content rules of a pack's artifacts (#554, FR-001, T031): an artifact id is unique within its kind. Each is a
 * kernel rule over the facts of the one read ({@link PackContents}); it reads no file and knows no surface.
 *
 * <p>Two kinds are judged elsewhere: a repeated asset id is {@link AssetRules#ID_REPEATED}'s (S2), and a repeated step
 * enhancement id is the step enhancement rules' {@code STEM_REPEATED}, at the same second file, so each repeat is one
 * finding.
 */
public final class ArtifactRules {

    /** The kinds whose repeated id another rule judges. */
    private static final Set<String> JUDGED_ELSEWHERE =
            Set.of(ArtifactKind.ASSET.token(), ArtifactKind.STEP_ENHANCEMENT.token());

    /**
     * An artifact id declared by two files of one kind, judged over the facts given: one
     * {@link PackCodes#DUPLICATE_ARTIFACT_ID} per later file, at that file, naming the first. The first file in path
     * order owns the id ({@link Precedence#comparePaths}, pathlib's order: segment by segment, so a directory sorts
     * before a sibling that extends its name), as core's {@code duplicate_id} does; the findings follow that order.
     */
    static final Rule<List<ArtifactFacts>> ID_REPEATED_AMONG = ArtifactRules::repeatedIds;

    /** {@link #ID_REPEATED_AMONG} over every artifact of the pack whose kind no other rule judges (R2 row 10). */
    public static final Rule<PackContents> ID_REPEATED = ID_REPEATED_AMONG.on(ArtifactRules::judgedHere);

    /** Every artifact rule, in this class's order. */
    public static final ModularRuleset<PackContents> RULES =
            ModularRuleset.<PackContents>emptyRules().must(ID_REPEATED);

    private ArtifactRules() {}

    static List<ArtifactFacts> judgedHere(PackContents contents) {
        return contents.artifacts().stream()
                .filter(artifact -> !JUDGED_ELSEWHERE.contains(artifact.kind()))
                .toList();
    }

    static List<Reason> repeatedIds(List<ArtifactFacts> artifacts) {
        List<ArtifactFacts> ordered = new ArrayList<>(artifacts);
        ordered.sort(Comparator.comparing(ArtifactFacts::file, Precedence::comparePaths));
        Map<List<String>, String> owners = new HashMap<>();
        List<Reason> findings = new ArrayList<>();
        for (ArtifactFacts artifact : ordered) {
            String first = owners.putIfAbsent(List.of(artifact.kind(), artifact.id()), artifact.file());
            if (first != null) {
                findings.add(Findings.at(
                        PackCodes.DUPLICATE_ARTIFACT_ID,
                        artifact.file(),
                        List.of(),
                        PackCodes.detail(
                                artifact.file(),
                                PackCodes.duplicateArtifactId(artifact.kind(), artifact.id(), first))));
            }
        }
        return findings;
    }
}
