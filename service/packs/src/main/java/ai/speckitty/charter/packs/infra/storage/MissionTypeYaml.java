package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.mappings;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The YAML form of the mission type, in the sanitized shape only (ADR-0044). The schema has already refused unknown and
 * retired keys before {@link #read(Map)} runs, so this format reads no retired spelling and has no fallback for one.
 * It writes keys in schema order and leaves out every empty optional list (S16).
 */
enum MissionTypeYaml implements YamlFormat<MissionTypeDso> {
    /** The format. */
    INSTANCE;

    /** The keys of {@code path_conventions}, in schema order. */
    private static final List<String> CONVENTIONS =
            List.of("workspace", "tests", "deliverables", "documentation", "data");

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.MISSION_TYPE;
    }

    @Override
    public Class<MissionTypeDso> dsoType() {
        return MissionTypeDso.class;
    }

    @Override
    public MissionTypeDso read(Map<?, ?> root) {
        Map<String, String> conventions = new LinkedHashMap<>();
        if (root.get("path_conventions") instanceof Map<?, ?> written) {
            for (String key : CONVENTIONS) {
                optional(written, key).ifPresent(value -> conventions.put(key, value));
            }
        }
        return new MissionTypeDso(
                required(root, "id"),
                required(root, "schema_version"),
                required(root, "title"),
                required(root, "description"),
                optional(root, "content"),
                Boolean.TRUE.equals(root.get("requires_substantive_spec")),
                conventions,
                artifacts(root, "expected_output_artifacts"),
                references(root),
                steps(root, "steps"),
                steps(root, "on_demand_steps"));
    }

    private static List<MissionTypeDso.Step> steps(Map<?, ?> root, String key) {
        List<MissionTypeDso.Step> steps = new ArrayList<>();
        for (Map<?, ?> s : mappings(root, key)) {
            steps.add(new MissionTypeDso.Step(
                    required(s, "id"),
                    required(s, "title"),
                    optional(s, "description"),
                    optional(s, "role"),
                    optional(s, "prompt_path"),
                    optional(s, "guidelines_path"),
                    artifacts(s, "expected_input_artifacts"),
                    artifacts(s, "expected_output_artifacts"),
                    references(s),
                    Optional.empty(),
                    Optional.empty()));
        }
        return steps;
    }

    private static List<MissionTypeDso.Artifact> artifacts(Map<?, ?> owner, String key) {
        List<MissionTypeDso.Artifact> artifacts = new ArrayList<>();
        for (Map<?, ?> a : mappings(owner, key)) {
            artifacts.add(new MissionTypeDso.Artifact(
                    required(a, "artifact_key"),
                    optional(a, "artifact_class"),
                    required(a, "path_pattern"),
                    Boolean.TRUE.equals(a.get("blocking")),
                    optional(a, "template_path")));
        }
        return artifacts;
    }

    private static List<MissionTypeDso.Reference> references(Map<?, ?> owner) {
        List<MissionTypeDso.Reference> references = new ArrayList<>();
        for (Map<?, ?> r : mappings(owner, "references")) {
            references.add(new MissionTypeDso.Reference(
                    required(r, "type"), required(r, "id"), optional(r, "when"), optional(r, "reason")));
        }
        return references;
    }

    @Override
    public String write(MissionTypeDso type) {
        YamlDumpEmitter out = new YamlDumpEmitter()
                .scalar("schema_version", type.schemaVersion())
                .scalar("id", type.id())
                .scalar("title", type.title())
                .scalar("description", type.description())
                .scalar("content", type.content());
        if (type.requiresSubstantiveSpec()) {
            out.present("requires_substantive_spec", true);
        }
        if (!type.pathConventions().isEmpty()) {
            out.present("path_conventions", new LinkedHashMap<String, Object>(type.pathConventions()));
        }
        return out.mappings("expected_output_artifacts", writeArtifacts(type.expectedOutputArtifacts()))
                .mappings("references", writeReferences(type.references()))
                .mappings("steps", writeSteps(type.steps()))
                .mappings("on_demand_steps", writeSteps(type.onDemandSteps()))
                .dump();
    }

    private static List<Map<String, Object>> writeSteps(List<MissionTypeDso.Step> steps) {
        List<Map<String, Object>> written = new ArrayList<>();
        for (MissionTypeDso.Step step : steps) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("id", step.id());
            out.put("title", step.title());
            YamlDumpEmitter.scalarInto(out, "description", step.description());
            YamlDumpEmitter.scalarInto(out, "role", step.role());
            YamlDumpEmitter.scalarInto(out, "prompt_path", step.promptPath());
            YamlDumpEmitter.scalarInto(out, "guidelines_path", step.guidelinesPath());
            YamlDumpEmitter.mappingsInto(
                    out, "expected_input_artifacts", writeArtifacts(step.expectedInputArtifacts()));
            YamlDumpEmitter.mappingsInto(
                    out, "expected_output_artifacts", writeArtifacts(step.expectedOutputArtifacts()));
            YamlDumpEmitter.mappingsInto(out, "references", writeReferences(step.references()));
            written.add(out);
        }
        return written;
    }

    private static List<Map<String, Object>> writeArtifacts(List<MissionTypeDso.Artifact> artifacts) {
        List<Map<String, Object>> written = new ArrayList<>();
        for (MissionTypeDso.Artifact artifact : artifacts) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("artifact_key", artifact.artifactKey());
            YamlDumpEmitter.scalarInto(out, "artifact_class", artifact.artifactClass());
            out.put("path_pattern", artifact.pathPattern());
            if (artifact.blocking()) {
                out.put("blocking", true);
            }
            YamlDumpEmitter.scalarInto(out, "template_path", artifact.templatePath());
            written.add(out);
        }
        return written;
    }

    private static List<Map<String, Object>> writeReferences(List<MissionTypeDso.Reference> references) {
        List<Map<String, Object>> written = new ArrayList<>();
        for (MissionTypeDso.Reference reference : references) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("type", reference.type());
            out.put("id", reference.id());
            YamlDumpEmitter.scalarInto(out, "when", reference.when());
            YamlDumpEmitter.scalarInto(out, "reason", reference.reason());
            written.add(out);
        }
        return written;
    }
}
