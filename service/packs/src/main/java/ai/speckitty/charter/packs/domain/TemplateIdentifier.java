package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;

/**
 * The identity of a {@link Template} within its pack: its path under the pack's top-level {@code templates/}
 * directory, for example {@code writing/meeting-minutes.md}. It follows the same path rules as
 * {@link AssetFile#path()}.
 *
 * @param value the path under {@code templates/}
 */
public record TemplateIdentifier(String value) {

    private static final ModularRuleset<String> RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "A template identifier must be a relative, /-separated path that is not blank and has no empty or"
                            + " '..' segment.",
                    AssetFile::isRelativePath));

    /**
     * Checks the path.
     *
     * @param value the path under {@code templates/}
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when it is not a relative path
     */
    public TemplateIdentifier {
        RULES.guard(value);
    }

    /**
     * The file name: the last segment of the path.
     *
     * @return the file name, for example {@code meeting-minutes.md}
     */
    public String fileName() {
        return value.substring(value.lastIndexOf('/') + 1);
    }
}
