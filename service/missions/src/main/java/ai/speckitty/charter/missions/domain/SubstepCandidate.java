package ai.speckitty.charter.missions.domain;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.domain.PackQualifiedReference;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One candidate of a {@link SubstepDelegation}, written {@code [<pack>::]<id>}: the id of an artifact of the
 * delegation's kind, bare for one of the step's own pack, or qualified with the name of the pack that holds it. The
 * kind is given once, by the delegation (squad finding M8).
 *
 * @param packName the pack a qualified candidate names; empty for a bare one
 * @param id the artifact's id, in the id grammar every scope-target kind's id fits: a letter or digit, then letters,
 *     digits, hyphens and underscores, at most {@value #MAX_ID_LENGTH} characters
 */
public record SubstepCandidate(Optional<String> packName, String id) {

    /** The longest candidate id, as the pack schemas cap an artifact id. */
    public static final int MAX_ID_LENGTH = 64;

    /** The grammar of a candidate's id: a letter or digit, then letters, digits, hyphens and underscores. */
    private static final String ID_GRAMMAR = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}";

    /**
     * The grammar of a candidate as written: the kernel's optional qualifier ({@link PackQualifiedReference#QUALIFIER}),
     * then the id. It is the one statement of the grammar: {@link #parse} reads a candidate by it, and the
     * {@code candidates} pattern of the mission-type schema is pinned to it by test.
     */
    public static final String GRAMMAR = "^" + PackQualifiedReference.QUALIFIER + ID_GRAMMAR + "$";

    private static final Pattern WRITTEN = Pattern.compile(GRAMMAR);

    private static final Pattern ID = Pattern.compile(ID_GRAMMAR);

    private static final ModularRuleset<String> WRITTEN_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A substep delegation candidate is written [<pack>::]<id>, as " + GRAMMAR + ".",
                    text -> WRITTEN.matcher(text).matches()));

    private static final ModularRuleset<String> ID_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A substep delegation candidate's id is a letter or digit, then letters, digits, hyphens and"
                            + " underscores, at most " + MAX_ID_LENGTH + " characters.",
                    text -> ID.matcher(text).matches()));
    private static final ModularRuleset<String> PACK_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A substep delegation candidate names its pack by a pack id. " + PackIdentifier.GRAMMAR_DETAIL,
                    PackQualifiedReference::isPackName));

    /**
     * Checks the parts.
     *
     * @param packName the pack's name, possibly empty
     * @param id the id
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when a part breaks its grammar
     */
    public SubstepCandidate {
        Objects.requireNonNull(packName, "packName").ifPresent(PACK_RULES::guard);
        ID_RULES.guard(Objects.requireNonNull(id, "id"));
    }

    /**
     * Reads a candidate as written.
     *
     * @param written {@code <id>} or {@code <pack>::<id>}
     * @return the candidate
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the text breaks {@link #GRAMMAR}
     */
    public static SubstepCandidate parse(String written) {
        WRITTEN_RULES.guard(Objects.requireNonNull(written, "written"));
        int qualifier = written.indexOf("::");
        return qualifier < 0
                ? new SubstepCandidate(Optional.empty(), written)
                : new SubstepCandidate(Optional.of(written.substring(0, qualifier)), written.substring(qualifier + 2));
    }

    /**
     * Whether the candidate names a pack.
     *
     * @return {@code true} for {@code <pack>::<id>}
     */
    public boolean qualified() {
        return packName.isPresent();
    }

    /** The candidate as written: {@code [<pack>::]<id>}. */
    @Override
    public String toString() {
        return packName.map(name -> name + "::" + id).orElse(id);
    }
}
