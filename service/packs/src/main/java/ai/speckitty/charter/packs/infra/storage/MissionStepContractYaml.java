package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.list;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The YAML form of the mission step contract. The two booleans of the schema ({@code fail_open} and {@code optional})
 * are read and written as booleans, and kept as written: a field equal to its schema default stays written, an omitted
 * one stays omitted.
 */
enum MissionStepContractYaml implements YamlFormat<MissionStepContractDso> {
    /** The format. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.MISSION_STEP_CONTRACT;
    }

    @Override
    public Class<MissionStepContractDso> dsoType() {
        return MissionStepContractDso.class;
    }

    @Override
    public MissionStepContractDso read(Map<?, ?> root) {
        List<MissionStepContractDso.Step> steps = new ArrayList<>();
        for (Object step : list(root, "steps")) {
            if (step instanceof Map<?, ?> s) {
                steps.add(new MissionStepContractDso.Step(
                        required(s, "id"),
                        required(s, "description"),
                        optional(s, "command"),
                        inputs(s),
                        delegation(s),
                        optional(s, "guidance")));
            }
        }
        List<MissionStepContractDso.Gate> gates = new ArrayList<>();
        for (Object gate : list(root, "gates")) {
            if (gate instanceof Map<?, ?> g) {
                gates.add(new MissionStepContractDso.Gate(
                        required(g, "on_transition"),
                        required(g, "handler"),
                        optional(g, "handler_kind"),
                        required(g, "schema_version"),
                        bool(g, "fail_open"),
                        optional(g, "provenance")));
            }
        }
        return new MissionStepContractDso(
                required(root, "id"),
                required(root, "schema_version"),
                required(root, "action"),
                required(root, "mission"),
                steps,
                gates);
    }

    @Override
    public String write(MissionStepContractDso contract) {
        List<Map<String, Object>> steps = new ArrayList<>();
        for (MissionStepContractDso.Step step : contract.steps()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("id", step.id());
            out.put("description", step.description());
            YamlDumpEmitter.scalarInto(out, "command", step.command());
            YamlDumpEmitter.mappingsInto(out, "inputs", inputsOut(step.inputs()));
            step.delegatesTo().ifPresent(delegation -> {
                Map<String, Object> delegatesTo = YamlDumpEmitter.mapping();
                delegatesTo.put("kind", delegation.kind());
                YamlDumpEmitter.stringsInto(delegatesTo, "candidates", delegation.candidates());
                out.put("delegates_to", delegatesTo);
            });
            YamlDumpEmitter.scalarInto(out, "guidance", step.guidance());
            steps.add(out);
        }
        List<Map<String, Object>> gates = new ArrayList<>();
        for (MissionStepContractDso.Gate gate : contract.gates()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("on_transition", gate.onTransition());
            out.put("handler", gate.handler());
            YamlDumpEmitter.scalarInto(out, "handler_kind", gate.handlerKind());
            out.put("schema_version", gate.schemaVersion());
            gate.failOpen().ifPresent(value -> out.put("fail_open", value));
            YamlDumpEmitter.scalarInto(out, "provenance", gate.provenance());
            gates.add(out);
        }
        return new YamlDumpEmitter()
                .scalar("schema_version", contract.schemaVersion())
                .scalar("id", contract.id())
                .scalar("action", contract.action())
                .scalar("mission", contract.mission())
                .mappings("steps", steps)
                .mappings("gates", gates)
                .dump();
    }

    private static List<MissionStepContractDso.Input> inputs(Map<?, ?> step) {
        List<MissionStepContractDso.Input> out = new ArrayList<>();
        for (Object entry : list(step, "inputs")) {
            if (entry instanceof Map<?, ?> i) {
                out.add(new MissionStepContractDso.Input(
                        required(i, "flag"), required(i, "source"), bool(i, "optional")));
            }
        }
        return out;
    }

    private static Optional<MissionStepContractDso.Delegation> delegation(Map<?, ?> step) {
        return step.get("delegates_to") instanceof Map<?, ?> d
                ? Optional.of(new MissionStepContractDso.Delegation(required(d, "kind"), strings(d, "candidates")))
                : Optional.empty();
    }

    private static List<Map<String, Object>> inputsOut(List<MissionStepContractDso.Input> inputs) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (MissionStepContractDso.Input input : inputs) {
            Map<String, Object> entry = YamlDumpEmitter.mapping();
            entry.put("flag", input.flag());
            entry.put("source", input.source());
            input.optional().ifPresent(value -> entry.put("optional", value));
            out.add(entry);
        }
        return out;
    }

    /** A boolean field as written; empty when absent or null. The schema has checked that a present value is one. */
    private static Optional<Boolean> bool(Map<?, ?> owner, String key) {
        return owner.get(key) instanceof Boolean value ? Optional.of(value) : Optional.empty();
    }
}
