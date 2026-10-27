package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One payload file under a pack's {@code assets/} directory, held by the {@link Pack} (operator ruling R1: a payload
 * may have no sidecar and no id). An {@link Asset} names one by its {@link #path()}. Equal when path and payload are
 * equal.
 *
 * @param path the path relative to the pack's {@code assets/} directory, {@code /}-separated
 * @param payload the file's exact bytes
 */
public record AssetFile(String path, Payload payload) {

    private static final ModularRuleset<String> PATH_RULES = ModularRuleset.<String>emptyRules()
            .must(Constraints.of(
                    "An asset file path must be a relative, /-separated path that is not blank and has no empty or"
                            + " '..' segment.",
                    AssetFile::isRelativePath));

    /**
     * Checks the path.
     *
     * @param path the path relative to the pack's {@code assets/} directory
     * @param payload the file's bytes
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when the path is not a relative path
     */
    public AssetFile {
        PATH_RULES.guard(path);
        Objects.requireNonNull(payload, "payload");
    }

    /**
     * The path rule shared by asset files and template identifiers: not blank, not absolute, {@code /}-separated,
     * with no empty segment and no {@code ..} segment. Nothing is normalized.
     *
     * @param path the candidate path; may be null
     * @return whether it is a relative path in that sense
     */
    static boolean isRelativePath(@Nullable String path) {
        if (path == null || path.isBlank() || path.startsWith("/")) {
            return false;
        }
        return Arrays.stream(path.split("/", -1)).noneMatch(segment -> segment.isEmpty() || segment.equals(".."));
    }
}
