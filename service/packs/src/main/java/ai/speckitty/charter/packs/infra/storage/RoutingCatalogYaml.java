package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.mappings;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Reads and writes a routing catalog file (ADR-0080 decision 5) in its persistence shape. The schema has judged the
 * document before {@link #read}, so a required member is present and a number is a number. A number is kept as the
 * loader gave it and written back as such, so the file round-trips without a change of meaning (ADR-0036).
 */
enum RoutingCatalogYaml implements YamlFormat<RoutingCatalogDso> {
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.ROUTING_CATALOG;
    }

    @Override
    public Class<RoutingCatalogDso> dsoType() {
        return RoutingCatalogDso.class;
    }

    @Override
    public RoutingCatalogDso read(Map<?, ?> root) {
        List<RoutingCatalogDso.TaskType> taskTypes = new ArrayList<>();
        for (Map<?, ?> t : mappings(root, "task_types")) {
            taskTypes.add(new RoutingCatalogDso.TaskType(
                    required(t, "id"),
                    required(t, "title"),
                    optional(t, "description"),
                    optional(t, "quality_sensitivity"),
                    optional(t, "cost_sensitivity")));
        }
        List<RoutingCatalogDso.Model> models = new ArrayList<>();
        for (Map<?, ?> m : mappings(root, "models")) {
            models.add(model(m));
        }
        List<RoutingCatalogDso.Source> sources = new ArrayList<>();
        for (Map<?, ?> s : mappings(root, "sources")) {
            sources.add(new RoutingCatalogDso.Source(
                    required(s, "name"),
                    required(s, "url"),
                    required(s, "access_method"),
                    required(s, "snapshot_at"),
                    optional(s, "license_notes")));
        }
        return new RoutingCatalogDso(
                required(root, "id"),
                optional(root, "description"),
                required(root, "generated_at"),
                taskTypes,
                models,
                policy(mapping(root, "routing_policy")),
                sources);
    }

    @Override
    public String write(RoutingCatalogDso catalog) {
        List<Map<String, Object>> taskTypes = new ArrayList<>();
        for (RoutingCatalogDso.TaskType type : catalog.taskTypes()) {
            Map<String, Object> t = YamlDumpEmitter.mapping();
            t.put("id", type.id());
            t.put("title", type.title());
            YamlDumpEmitter.scalarInto(t, "description", type.description());
            YamlDumpEmitter.scalarInto(t, "quality_sensitivity", type.qualitySensitivity());
            YamlDumpEmitter.scalarInto(t, "cost_sensitivity", type.costSensitivity());
            taskTypes.add(t);
        }
        List<Map<String, Object>> models = new ArrayList<>();
        for (RoutingCatalogDso.Model model : catalog.models()) {
            models.add(modelMapping(model));
        }
        List<Map<String, Object>> sources = new ArrayList<>();
        for (RoutingCatalogDso.Source source : catalog.sources()) {
            Map<String, Object> s = YamlDumpEmitter.mapping();
            s.put("name", source.name());
            s.put("url", source.url());
            s.put("access_method", source.accessMethod());
            s.put("snapshot_at", source.snapshotAt());
            YamlDumpEmitter.scalarInto(s, "license_notes", source.licenseNotes());
            sources.add(s);
        }
        return new YamlDumpEmitter()
                .scalar("id", catalog.id())
                .scalar("description", catalog.description())
                .scalar("generated_at", catalog.generatedAt())
                .mappings("task_types", taskTypes)
                .mappings("models", models)
                .present("routing_policy", policyMapping(catalog.policy()))
                .mappings("sources", sources)
                .dump();
    }

    private static RoutingCatalogDso.Model model(Map<?, ?> m) {
        List<RoutingCatalogDso.Fit> fits = new ArrayList<>();
        for (Map<?, ?> f : mappings(m, "task_fit")) {
            fits.add(new RoutingCatalogDso.Fit(
                    required(f, "task_type"), number(f, "score"), optional(f, "confidence"), optional(f, "rationale")));
        }
        Map<?, ?> cost = mapping(m, "cost");
        return new RoutingCatalogDso.Model(
                required(m, "id"),
                required(m, "provider"),
                optional(m, "family"),
                strings(m, "tools"),
                strings(m, "strengths"),
                strings(m, "weaknesses"),
                fits,
                new RoutingCatalogDso.Cost(
                        required(cost, "tier"),
                        optionalNumber(cost, "input_per_1m_usd"),
                        optionalNumber(cost, "output_per_1m_usd"),
                        optional(cost, "currency"),
                        optional(cost, "pricing_source_url")),
                optional(m, "latency_tier"));
    }

    private static RoutingCatalogDso.Policy policy(Map<?, ?> policy) {
        Map<?, ?> weights = mapping(policy, "weights");
        List<RoutingCatalogDso.Constraint> constraints = new ArrayList<>();
        for (Map<?, ?> c : mappings(policy, "tier_constraints")) {
            constraints.add(new RoutingCatalogDso.Constraint(required(c, "task_type"), required(c, "max_tier")));
        }
        return new RoutingCatalogDso.Policy(
                required(policy, "objective"),
                new RoutingCatalogDso.Weights(
                        number(weights, "quality"),
                        number(weights, "cost"),
                        number(weights, "risk"),
                        number(weights, "latency")),
                constraints);
    }

    private static Map<String, Object> modelMapping(RoutingCatalogDso.Model model) {
        Map<String, Object> m = YamlDumpEmitter.mapping();
        m.put("id", model.id());
        m.put("provider", model.provider());
        YamlDumpEmitter.scalarInto(m, "family", model.family());
        YamlDumpEmitter.stringsInto(m, "tools", model.tools());
        YamlDumpEmitter.stringsInto(m, "strengths", model.strengths());
        YamlDumpEmitter.stringsInto(m, "weaknesses", model.weaknesses());
        List<Map<String, Object>> fits = new ArrayList<>();
        for (RoutingCatalogDso.Fit fit : model.taskFit()) {
            Map<String, Object> f = YamlDumpEmitter.mapping();
            f.put("task_type", fit.taskType());
            f.put("score", fit.score());
            YamlDumpEmitter.scalarInto(f, "confidence", fit.confidence());
            YamlDumpEmitter.scalarInto(f, "rationale", fit.rationale());
            fits.add(f);
        }
        m.put("task_fit", fits);
        Map<String, Object> cost = YamlDumpEmitter.mapping();
        cost.put("tier", model.cost().tier());
        model.cost().inputPer1mUsd().ifPresent(value -> cost.put("input_per_1m_usd", value));
        model.cost().outputPer1mUsd().ifPresent(value -> cost.put("output_per_1m_usd", value));
        YamlDumpEmitter.scalarInto(cost, "currency", model.cost().currency());
        YamlDumpEmitter.scalarInto(cost, "pricing_source_url", model.cost().pricingSourceUrl());
        m.put("cost", cost);
        YamlDumpEmitter.scalarInto(m, "latency_tier", model.latencyTier());
        return m;
    }

    private static Map<String, Object> policyMapping(RoutingCatalogDso.Policy written) {
        Map<String, Object> weights = YamlDumpEmitter.mapping();
        weights.put("quality", written.weights().quality());
        weights.put("cost", written.weights().cost());
        weights.put("risk", written.weights().risk());
        weights.put("latency", written.weights().latency());
        Map<String, Object> policy = YamlDumpEmitter.mapping();
        policy.put("objective", written.objective());
        policy.put("weights", weights);
        List<Map<String, Object>> constraints = new ArrayList<>();
        for (RoutingCatalogDso.Constraint constraint : written.tierConstraints()) {
            Map<String, Object> c = YamlDumpEmitter.mapping();
            c.put("task_type", constraint.taskType());
            c.put("max_tier", constraint.maxTier());
            constraints.add(c);
        }
        YamlDumpEmitter.mappingsInto(policy, "tier_constraints", constraints);
        return policy;
    }

    private static Map<?, ?> mapping(Map<?, ?> owner, String key) {
        if (owner.get(key) instanceof Map<?, ?> map) {
            return map;
        }
        throw new IllegalStateException("'" + key + "' passed the schema but is no mapping");
    }

    private static Number number(Map<?, ?> owner, String key) {
        return optionalNumber(owner, key)
                .orElseThrow(() -> new IllegalStateException("'" + key + "' passed the schema but is no number"));
    }

    private static Optional<Number> optionalNumber(Map<?, ?> owner, String key) {
        @Nullable Object value = owner.get(key);
        return value instanceof Number number ? Optional.of(number) : Optional.empty();
    }
}
