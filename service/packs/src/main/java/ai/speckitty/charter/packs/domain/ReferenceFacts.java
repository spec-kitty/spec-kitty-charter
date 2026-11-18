package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ConfigValue;
import ai.speckitty.charter.kernel.domain.Urn;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What the closure rule judges of a pack, as the one read built it (#355): every asset, guard, lifecycle and mission type
 * the read could build, with the file it is stored in, before the load leaves any out; the paths of the asset payloads;
 * the ids of the mission types' own templates; and the port that compiles a guard's {@code config_schema}.
 *
 * <p>A lifecycle and a mission type are the missions slice's values, which the packs domain does not name
 * ({@code SLICES_DO_NOT_CROSS}): the storage edge gives the rule what it judges of them, as {@link LifecycleView} and
 * {@link MissionTypeView}.
 *
 * @param candidates each candidate with its pack-relative file, in the order the load meets them
 * @param payloads the pack-relative paths of the asset payloads, under {@code assets/}
 * @param namableTemplates the ids of the templates a mission type's output may name ({@code <type>/<path>})
 * @param schemas the port that compiles a {@code config_schema} and checks a {@code config}
 */
public record ReferenceFacts(
        List<Placed> candidates, Set<String> payloads, Set<String> namableTemplates, ConfigSchemas schemas) {

    /** No candidate: the facts of a read the load refused, or of facts a test writes by hand; they judge no closure. */
    public static final ReferenceFacts NONE = new ReferenceFacts(List.of(), Set.of(), Set.of(), new ConfigSchemas() {
        @Override
        public List<String> schemaProblems(ConfigValue schema) {
            return List.of();
        }

        @Override
        public List<String> valueProblems(ConfigValue schema, Optional<ConfigValue> value) {
            return List.of();
        }
    });

    /**
     * Checks the fields and copies the collections.
     *
     * @param candidates the candidates
     * @param payloads the payload paths
     * @param namableTemplates the namable template ids
     * @param schemas the port
     */
    public ReferenceFacts {
        candidates = List.copyOf(candidates);
        payloads = Set.copyOf(payloads);
        namableTemplates = Set.copyOf(namableTemplates);
        Objects.requireNonNull(schemas, "schemas");
    }

    /**
     * A candidate and the pack-relative file it is stored in.
     *
     * @param file the file
     * @param subject the candidate
     */
    public record Placed(String file, Subject subject) {
        /**
         * Checks the fields.
         *
         * @param file the file
         * @param subject the candidate
         */
        public Placed {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(subject, "subject");
        }
    }

    /** What the closure judges of one artifact. */
    public sealed interface Subject {

        /**
         * The artifact's URN.
         *
         * @return the URN
         */
        Urn urn();
    }

    /**
     * An asset: its {@code executable} entry must name a payload the pack holds.
     *
     * @param asset the asset
     */
    public record AssetSubject(Asset asset) implements Subject {
        /**
         * Checks the field.
         *
         * @param asset the asset
         */
        public AssetSubject {
            Objects.requireNonNull(asset, "asset");
        }

        @Override
        public Urn urn() {
            return asset.urn();
        }
    }

    /**
     * A guard: its {@code config_schema} must compile, and its asset must close and run.
     *
     * @param guard the guard
     */
    public record GuardSubject(Guard guard) implements Subject {
        /**
         * Checks the field.
         *
         * @param guard the guard
         */
        public GuardSubject {
            Objects.requireNonNull(guard, "guard");
        }

        @Override
        public Urn urn() {
            return guard.urn();
        }
    }

    /**
     * A lifecycle, as the closure judges it: each transition's guard entries.
     *
     * @param urn the lifecycle's URN
     * @param id the lifecycle's id
     * @param transitions the transitions, in authored order
     */
    public record LifecycleView(Urn urn, String id, List<TransitionView> transitions) implements Subject {
        /**
         * Checks the fields and copies the list.
         *
         * @param urn the URN
         * @param id the id
         * @param transitions the transitions
         */
        public LifecycleView {
            Objects.requireNonNull(urn, "urn");
            Objects.requireNonNull(id, "id");
            transitions = List.copyOf(transitions);
        }
    }

    /**
     * A lifecycle transition, as the closure judges it.
     *
     * @param described the transition as the load words it, {@code <from> -> <to>}
     * @param guards its guard entries, in authored order
     */
    public record TransitionView(String described, List<GuardEntryView> guards) {
        /**
         * Checks the fields and copies the list.
         *
         * @param described the transition, worded
         * @param guards the guard entries
         */
        public TransitionView {
            Objects.requireNonNull(described, "described");
            guards = List.copyOf(guards);
        }
    }

    /**
     * A guard entry of a transition.
     *
     * @param crossPack whether the entry names a guard of another pack
     * @param guardId the guard's id
     * @param spelling the reference as written
     * @param config the entry's {@code config}, if any
     */
    public record GuardEntryView(boolean crossPack, String guardId, String spelling, Optional<ConfigValue> config) {
        /**
         * Checks the fields.
         *
         * @param crossPack whether the entry is pack-qualified
         * @param guardId the guard's id
         * @param spelling the reference as written
         * @param config the config
         */
        public GuardEntryView {
            Objects.requireNonNull(guardId, "guardId");
            Objects.requireNonNull(spelling, "spelling");
            Objects.requireNonNull(config, "config");
        }
    }

    /**
     * A mission type, as the closure judges it: the output templates it names.
     *
     * @param urn the type's URN
     * @param id the type's id
     * @param templates the output templates it names, in the order the missions domain lists them
     */
    public record MissionTypeView(Urn urn, String id, List<OutputTemplate> templates) implements Subject {
        /**
         * Checks the fields and copies the list.
         *
         * @param urn the URN
         * @param id the id
         * @param templates the output templates
         */
        public MissionTypeView {
            Objects.requireNonNull(urn, "urn");
            Objects.requireNonNull(id, "id");
            templates = List.copyOf(templates);
        }
    }

    /**
     * An output template a mission type names.
     *
     * @param step the id of the step that names it; empty when the type's own output names it
     * @param templatePath the path as written
     * @param templateId the id of the template the path names, {@code <type>/<path after templates/>}
     */
    public record OutputTemplate(Optional<String> step, String templatePath, String templateId) {
        /**
         * Checks the fields.
         *
         * @param step the naming step, if any
         * @param templatePath the path as written
         * @param templateId the template id
         */
        public OutputTemplate {
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(templatePath, "templatePath");
            Objects.requireNonNull(templateId, "templateId");
        }
    }
}
