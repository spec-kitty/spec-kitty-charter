package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.CharterArtifact;
import ai.speckitty.charter.kernel.domain.PackIdentifier;
import ai.speckitty.charter.kernel.validation.Constraints;
import ai.speckitty.charter.kernel.validation.ModularRuleset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A step enhancement of a {@link Pack} (ADR-0053 decision 22, ADR-0066): an artifact of the enhancing pack that only
 * adds, here the prompt fragments it fills into named slots of the step it enhances. Its URN is
 * {@code step_enhancement:<id>}.
 *
 * <p>It names no target: the step it enhances is the target of its one {@code enhances} edge in the pack's graph
 * documents, never a field, and it carries no range, anchor, position or priority either. It may hold no entry: the
 * absent {@code prompt_fragments} shape is refused by a load rule, not by this constructor. Two enhancements are equal
 * when they have the same id.
 */
public final class StepEnhancement implements CharterArtifact {

    /** The one schema version of the shape. */
    public static final String SCHEMA_VERSION = "1.0";

    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9]*(-[a-z0-9]+)*$");
    private static final Pattern SLOT = Pattern.compile("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$");

    private static final ModularRuleset<StepEnhancement> RULES = ModularRuleset.<StepEnhancement>emptyRules()
            .must(Constraints.of(
                    "A step enhancement identifier must match ^[a-z][a-z0-9]*(-[a-z0-9]+)*$.",
                    enhancement -> ID.matcher(enhancement.id).matches()))
            .must(Constraints.of(
                    "A step enhancement needs the schema version " + SCHEMA_VERSION + ".",
                    enhancement -> enhancement.schemaVersion.equals(SCHEMA_VERSION)))
            .must(Constraints.of(
                    "A step enhancement's slot must match ^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$.",
                    enhancement -> enhancement.promptFragments.stream()
                            .allMatch(entry -> SLOT.matcher(entry.slot()).matches())));

    /**
     * One prompt fragment the enhancement fills: the slot it fills and the companion that holds the text.
     *
     * @param slot the slot id, {@code <namespace>:<name>}
     * @param fragment the companion's path and body
     */
    public record Entry(String slot, PromptFragment fragment) {
        /**
         * Checks that both components are present.
         *
         * @param slot the slot id
         * @param fragment the fragment
         */
        public Entry {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(fragment, "fragment");
        }

        /**
         * The companion's name, beside the enhancement file.
         *
         * @return the fragment's path
         */
        public String path() {
            return fragment.path();
        }
    }

    private final String id;
    private final PackIdentifier packId;
    private final String schemaVersion;
    private final List<Entry> promptFragments;

    /**
     * Reconstructs a step enhancement from its parts.
     *
     * @param id the enhancement's id, equal to its file stem
     * @param packId the pack that owns it
     * @param schemaVersion the schema version text, {@value #SCHEMA_VERSION}
     * @param promptFragments the fragments it fills, in authored order; possibly none
     * @throws ai.speckitty.charter.kernel.validation.ConstraintBreachException when an invariant is breached
     */
    public StepEnhancement(String id, PackIdentifier packId, String schemaVersion, List<Entry> promptFragments) {
        this.id = Objects.requireNonNull(id, "id");
        this.packId = Objects.requireNonNull(packId, "packId");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        this.promptFragments = List.copyOf(Objects.requireNonNull(promptFragments, "promptFragments"));
        RULES.guard(this);
    }

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.STEP_ENHANCEMENT;
    }

    @Override
    public PackIdentifier packId() {
        return packId;
    }

    @Override
    public String localId() {
        return id;
    }

    @Override
    public Optional<String> schemaVersion() {
        return Optional.of(schemaVersion);
    }

    @Override
    public String label() {
        return id;
    }

    @Override
    public Optional<String> description() {
        return Optional.empty();
    }

    /**
     * The fragments the enhancement fills, in authored order.
     *
     * @return the entries, possibly none
     */
    public List<Entry> promptFragments() {
        return promptFragments;
    }

    /** Entity identity: equal when the id is equal, whatever the other fields hold. */
    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof StepEnhancement that && id.equals(that.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "StepEnhancement[" + id + " of pack " + packId.value() + "]";
    }
}
