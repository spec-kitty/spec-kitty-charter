package ai.speckitty.charter.packs.domain.rules;

import ai.speckitty.charter.kernel.domain.Urn;
import ai.speckitty.charter.packs.domain.Asset;
import ai.speckitty.charter.packs.domain.ConfigSchemas;
import ai.speckitty.charter.packs.domain.Guard;
import ai.speckitty.charter.packs.domain.GuardImplementation;
import ai.speckitty.charter.packs.domain.ReferenceFacts;
import ai.speckitty.charter.packs.domain.ReferenceFacts.AssetSubject;
import ai.speckitty.charter.packs.domain.ReferenceFacts.GuardEntryView;
import ai.speckitty.charter.packs.domain.ReferenceFacts.GuardSubject;
import ai.speckitty.charter.packs.domain.ReferenceFacts.LifecycleView;
import ai.speckitty.charter.packs.domain.ReferenceFacts.MissionTypeView;
import ai.speckitty.charter.packs.domain.ReferenceFacts.OutputTemplate;
import ai.speckitty.charter.packs.domain.ReferenceFacts.Subject;
import ai.speckitty.charter.packs.domain.ReferenceFacts.TransitionView;
import ai.speckitty.charter.packs.domain.Template;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The pack's closure (#355; ADR-0054 decisions 3 to 5, ADR-0044 decision 7 as amended for #277): the one rule that
 * decides whether a guard, a lifecycle, an executable asset or a mission type closes over what it names. The load leaves
 * out what does not close, the whole-pack write refuses it, the PACK ruleset states it ({@link ReferenceRules}), and a
 * per-file write refuses it with {@code registration_conflict}; each calls this class.
 *
 * <ul>
 *   <li>An asset's {@code executable} entry names a payload file the pack holds under {@code assets/}, its own or
 *       another's (ADR-0054 decision 5 as amended on 2026-11-03).
 *   <li>A guard's {@code config_schema} compiles offline; its {@code implemented_by} asset is an asset of the pack that
 *       closes and declares an {@code executable} block. A built-in needs nothing more than the schema's closed list.
 *   <li>A lifecycle's guard entries name guards of the pack that close, and each entry's {@code config} fits its guard's
 *       {@code config_schema}; a {@code config} for a guard that declares none is refused, since nothing can check it.
 *   <li>A mission type's output templates are its own templates, filed under {@code missions/<type>/templates/}.
 *   <li>A reference to another pack ({@code <pack>::asset:<id>}, {@code <pack>::guard:<id>}, ADR-0053 decision 7) is
 *       refused until the pack dependency closure (ADR-0048) can resolve it.
 * </ul>
 *
 * <p>The checks cascade, as the load applies them: an asset that does not close backs no guard, and a guard that does not
 * close is named by no lifecycle. Among candidates of one id, the first one that closes is the one named.
 */
public final class ReferenceClosure {

    private static final String CROSS_PACK =
            ", a pack-qualified reference; it is refused until the pack dependency closure resolves it";

    private final Set<String> payloads;
    private final Set<String> namableTemplates;
    private final ConfigSchemas schemas;
    private final Map<String, Asset> assets = new LinkedHashMap<>();
    private final Map<String, Guard> guards = new LinkedHashMap<>();

    private ReferenceClosure(
            Collection<? extends Subject> candidates,
            Set<String> payloads,
            Set<String> namableTemplates,
            ConfigSchemas schemas) {
        this.payloads = Set.copyOf(payloads);
        this.namableTemplates = Set.copyOf(namableTemplates);
        this.schemas = Objects.requireNonNull(schemas, "schemas");
        for (Subject candidate : candidates) {
            if (candidate instanceof AssetSubject(Asset asset)
                    && unresolved(asset).isEmpty()) {
                assets.putIfAbsent(asset.localId(), asset);
            }
        }
        for (Subject candidate : candidates) {
            if (candidate instanceof GuardSubject(Guard guard)
                    && unresolved(guard).isEmpty()) {
                guards.putIfAbsent(guard.localId(), guard);
            }
        }
    }

    /**
     * The closure over a pack's candidates.
     *
     * @param candidates every asset, guard, lifecycle and mission type the pack could hold, in its order
     * @param payloads the pack-relative paths of its asset payloads
     * @param namableTemplates the ids its mission types' output templates may name ({@link #namable})
     * @param schemas the port that compiles a {@code config_schema}
     * @return the closure
     */
    public static ReferenceClosure of(
            Collection<? extends Subject> candidates,
            Set<String> payloads,
            Set<String> namableTemplates,
            ConfigSchemas schemas) {
        return new ReferenceClosure(candidates, payloads, namableTemplates, schemas);
    }

    /**
     * The closure over the facts of a read.
     *
     * @param facts the facts
     * @return the closure
     */
    public static ReferenceClosure of(ReferenceFacts facts) {
        return of(
                facts.candidates().stream().map(ReferenceFacts.Placed::subject).toList(),
                facts.payloads(),
                facts.namableTemplates(),
                facts.schemas());
    }

    /**
     * The ids a mission type's output template can name: those of the mission types' own templates. A template of the
     * same path under the pack's top-level {@code templates/} is filed where the path does not lead.
     *
     * @param templates the pack's templates
     * @return their ids, {@code <type>/<path>}
     */
    public static Set<String> namable(Collection<Template> templates) {
        return templates.stream()
                .filter(template -> template.missionType().isPresent())
                .map(template -> template.id().value())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Why a candidate does not close, in the words the load records at its file.
     *
     * @param subject the candidate
     * @return the reason, a sentence naming what does not resolve; empty when it closes
     */
    public Optional<String> firstUnresolved(Subject subject) {
        return switch (subject) {
            case AssetSubject(Asset asset) -> unresolved(asset);
            case GuardSubject(Guard guard) -> unresolved(guard);
            case LifecycleView lifecycle -> unresolved(lifecycle);
            case MissionTypeView type -> unresolved(type);
        };
    }

    /**
     * Every candidate that does not close, in the order given.
     *
     * @param candidates the candidates
     * @return the findings; empty when every one closes
     */
    public List<Finding> findings(Collection<? extends Subject> candidates) {
        return candidates.stream()
                .flatMap(candidate -> firstUnresolved(candidate).map(reason -> new Finding(candidate, reason)).stream())
                .toList();
    }

    /**
     * A candidate that does not close.
     *
     * @param subject the candidate
     * @param reason why, in the load's words
     */
    public record Finding(Subject subject, String reason) {
        /**
         * Checks the fields.
         *
         * @param subject the candidate
         * @param reason the reason
         */
        public Finding {
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(reason, "reason");
        }

        /**
         * The URN of the candidate that does not close.
         *
         * @return the URN
         */
        public Urn urn() {
            return subject.urn();
        }

        /**
         * The reason as a write refuses the pack with it: a mission type's is prefixed with the type, as
         * {@code mission_type '<id>' names <reason>}; any other's is the reason itself.
         *
         * @return the refusal's message
         */
        public String refusal() {
            return subject instanceof MissionTypeView type
                    ? "mission_type '" + type.id() + "' names " + reason
                    : reason;
        }
    }

    /** An executable asset's entry names a payload the pack holds (ADR-0054 decision 5 as amended 2026-11-03). */
    private Optional<String> unresolved(Asset asset) {
        return asset.executable()
                .filter(executable -> !payloads.contains(executable.entry()))
                .map(executable -> "asset '" + asset.localId() + "' declares executable entry '" + executable.entry()
                        + "', which is not a payload file the pack holds under assets/");
    }

    private Optional<String> unresolved(Guard guard) {
        String named = "guard '" + guard.localId() + "'";
        if (guard.configSchema().isPresent()) {
            List<String> problems = schemas.schemaProblems(guard.configSchema().get());
            if (!problems.isEmpty()) {
                return Optional.of(
                        named + ": its config_schema is not a valid JSON Schema: " + String.join("; ", problems));
            }
        }
        if (!(guard.implementation() instanceof GuardImplementation.AssetBacked asset)) {
            return Optional.empty();
        }
        String implemented = named + " is implemented_by " + asset.spelling();
        if (asset.pack().isPresent()) {
            return Optional.of(implemented + CROSS_PACK);
        }
        Asset held = assets.get(asset.assetId());
        if (held == null) {
            return Optional.of(implemented + ", which the pack does not hold");
        }
        if (held.executable().isEmpty()) {
            return Optional.of(implemented + ", which declares no executable block");
        }
        return Optional.empty();
    }

    private Optional<String> unresolved(LifecycleView lifecycle) {
        for (TransitionView transition : lifecycle.transitions()) {
            String where = "lifecycle '" + lifecycle.id() + "' transition " + transition.described();
            for (GuardEntryView entry : transition.guards()) {
                if (entry.crossPack()) {
                    return Optional.of(where + " names guard " + entry.spelling() + CROSS_PACK);
                }
                Guard guard = guards.get(entry.guardId());
                if (guard == null) {
                    return Optional.of(where + " names guard '" + entry.guardId() + "', which the pack does not hold");
                }
                Optional<String> config = configProblem(guard, entry);
                if (config.isPresent()) {
                    return Optional.of(where + ": " + config.get());
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> configProblem(Guard guard, GuardEntryView entry) {
        String named = "guard '" + guard.localId() + "'";
        if (guard.configSchema().isEmpty()) {
            return entry.config().isPresent()
                    ? Optional.of(named + " declares no config_schema, so its config cannot be checked")
                    : Optional.empty();
        }
        List<String> problems = schemas.valueProblems(guard.configSchema().get(), entry.config());
        return problems.isEmpty()
                ? Optional.empty()
                : Optional.of(
                        "the config of " + named + " does not fit its config_schema: " + String.join("; ", problems));
    }

    /**
     * The first output template of a mission type that is not one of the type's own templates, worded as the load words a
     * named file's refusal: {@code <path> (named by step '<id>'): is not a template of the type}, or
     * {@code (named by the type)}.
     */
    private Optional<String> unresolved(MissionTypeView type) {
        for (OutputTemplate template : type.templates()) {
            if (!namableTemplates.contains(template.templateId())) {
                String namedBy = template.step().map(id -> "step '" + id + "'").orElse("the type");
                return Optional.of(
                        template.templatePath() + " (named by " + namedBy + "): is not a template of the type");
            }
        }
        return Optional.empty();
    }
}
