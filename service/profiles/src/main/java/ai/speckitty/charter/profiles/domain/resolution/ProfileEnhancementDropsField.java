package ai.speckitty.charter.profiles.domain.resolution;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.io.Serial;
import java.util.Objects;

/**
 * An enhancement that would lose content of the profile it enhances (ruling D5), or that declares an
 * {@code excluding} (ruling D7): the enhancing pack and the top-level field that loses it. Registration answers it as
 * {@code profile_enhancement_drops_field} (422), and a read of content stored before the rule as the same code (500).
 */
public final class ProfileEnhancementDropsField extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The registered id of the enhancing pack. */
    private final transient PackIdentifier pack;

    /** The top-level field that loses base content. */
    private final String field;

    /**
     * A refused enhancement.
     *
     * @param pack the registered id of the enhancing pack
     * @param field the top-level field, as the schema names it (for example {@code specialization})
     * @param enhancer the enhancing profile's id
     * @param base the enhanced profile's id
     */
    public ProfileEnhancementDropsField(PackIdentifier pack, String field, String enhancer, String base) {
        super(message(
                Objects.requireNonNull(pack, "pack").value(),
                Objects.requireNonNull(field, "field"),
                Objects.requireNonNull(enhancer, "enhancer"),
                Objects.requireNonNull(base, "base")));
        this.pack = pack;
        this.field = field;
    }

    private static String message(String pack, String field, String enhancer, String base) {
        String named = "The agent profile " + enhancer + " of pack " + pack + " enhances " + base;
        return ProfileField.EXCLUDING.yamlName().equals(field)
                ? named + " and declares excluding; an enhancement drops nothing of the profile it enhances"
                : named + " and would drop part of its " + field
                        + "; an enhancement keeps every list item, sub-field and value of the profile it enhances";
    }

    /**
     * The enhancing pack, named first.
     *
     * @return the pack's registered id
     */
    public PackIdentifier pack() {
        return pack;
    }

    /**
     * The top-level field that loses base content.
     *
     * @return the field, as the schema names it
     */
    public String field() {
        return field;
    }
}
