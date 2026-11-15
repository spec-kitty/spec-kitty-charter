package ai.speckitty.charter.profiles.domain.resolution;

import ai.speckitty.charter.profiles.domain.AgentProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The profile enhancement merge (ruling D5 of ADR-0078, #380): a profile merged with its in-force enhancers, in closure
 * order, the nearest pack last. An enhancer is a complete profile, so its identity fields ({@code profile-id},
 * {@code name}, {@code purpose}, {@code role}, {@code alt-roles}, {@code performer}, {@code routing-priority}) are its own
 * and never merged. Every other top-level field it declares replaces the value so far, and the replacement is refused
 * with {@link ProfileEnhancementDropsField} when it loses content of that value: a list item missing (in any order), an
 * object sub-field missing or losing content (recursively), or a scalar changed. An enhancer that declares a non-empty
 * {@code excluding} is refused the same way, naming {@code excluding} (ruling D7): it is a declared drop.
 *
 * <p>The fields are walked from the one table {@link ProfileField}, so a new schema field cannot skip the merge.
 */
public final class ProfileEnhancementMerge {

    /**
     * One enhancer of the profile.
     *
     * @param profile the enhancing profile
     * @param pack the pack that holds it, named first in a refusal
     */
    public record Enhancer(AgentProfile profile, String pack) {

        /**
         * Checks the fields.
         *
         * @param profile the enhancing profile
         * @param pack the holding pack
         */
        public Enhancer {
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(pack, "pack");
        }
    }

    private ProfileEnhancementMerge() {}

    /**
     * Merges a profile with its enhancers.
     *
     * @param base the enhanced profile; the merged profile keeps its identity, pack and identity fields
     * @param inClosureOrder its in-force enhancers, in closure order, the nearest pack last
     * @return the merged profile; {@code base}'s content when there is no enhancer
     * @throws ProfileEnhancementDropsField naming the first enhancer, in that order, and the first field, in schema
     *     order, whose replacement loses base content, or {@code excluding} for an enhancer that declares one
     */
    public static AgentProfile merge(AgentProfile base, List<Enhancer> inClosureOrder) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(inClosureOrder, "inClosureOrder");
        Map<ProfileField, AgentProfile> sources = new LinkedHashMap<>();
        for (Enhancer enhancer : inClosureOrder) {
            AgentProfile enhancing = enhancer.profile();
            if (ProfileField.EXCLUDING.declaredBy(enhancing)) {
                throw refusal(enhancer, ProfileField.EXCLUDING, base);
            }
            for (ProfileField field : ProfileField.values()) {
                if (field.identity() || field.equals(ProfileField.EXCLUDING) || !field.declaredBy(enhancing)) {
                    continue;
                }
                AgentProfile current = sources.getOrDefault(field, base);
                if (ProfileField.loses(field.shapeOf(current), field.shapeOf(enhancing))) {
                    throw refusal(enhancer, field, base);
                }
                sources.put(field, enhancing);
            }
        }
        return ProfileField.assemble(base, sources);
    }

    private static ProfileEnhancementDropsField refusal(Enhancer enhancer, ProfileField field, AgentProfile base) {
        return new ProfileEnhancementDropsField(
                enhancer.pack(), field.yamlName(), enhancer.profile().localId(), base.localId());
    }
}
