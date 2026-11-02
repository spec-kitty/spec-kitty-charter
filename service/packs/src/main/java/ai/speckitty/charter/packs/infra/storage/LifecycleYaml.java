package ai.speckitty.charter.packs.infra.storage;

import static ai.speckitty.charter.packs.infra.storage.YamlFields.mappings;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.optional;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.required;
import static ai.speckitty.charter.packs.infra.storage.YamlFields.strings;

import ai.speckitty.charter.kernel.domain.ArtifactKind;
import ai.speckitty.charter.kernel.domain.ConfigValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The YAML form of the lifecycle (ADR-0050 decision 17), in schema key order. The schema has already refused unknown
 * keys and wrong types before {@link #read(Map)} runs. A guard entry's {@code config} is an open tree (ADR-0042 S4 as
 * amended), held as parsed and written back as held.
 */
enum LifecycleYaml implements YamlFormat<LifecycleDso> {
    /** The format. */
    INSTANCE;

    @Override
    public ArtifactKind kind() {
        return ArtifactKind.LIFECYCLE;
    }

    @Override
    public Class<LifecycleDso> dsoType() {
        return LifecycleDso.class;
    }

    @Override
    public LifecycleDso read(Map<?, ?> root) {
        List<LifecycleDso.State> states = new ArrayList<>();
        for (Map<?, ?> s : mappings(root, "states")) {
            states.add(new LifecycleDso.State(
                    required(s, "id"), required(s, "title"), optional(s, "description"), flag(s, "terminal")));
        }
        List<LifecycleDso.Transition> transitions = new ArrayList<>();
        for (Map<?, ?> t : mappings(root, "transitions")) {
            List<LifecycleDso.GuardEntry> guard = new ArrayList<>();
            for (Map<?, ?> g : mappings(t, "guard")) {
                guard.add(new LifecycleDso.GuardEntry(
                        required(g, "ref"),
                        g.get("config") instanceof Map<?, ?> config
                                ? Optional.of(ConfigValue.of(config))
                                : Optional.empty(),
                        flag(g, "blocking")));
            }
            boolean listed = t.get("from") instanceof List<?>;
            transitions.add(new LifecycleDso.Transition(
                    listed ? Optional.empty() : Optional.of(required(t, "from")),
                    listed ? strings(t, "from") : List.of(),
                    required(t, "to"),
                    optional(t, "event"),
                    guard));
        }
        return new LifecycleDso(
                required(root, "id"),
                required(root, "schema_version"),
                required(root, "title"),
                optional(root, "description"),
                required(root, "initial"),
                states,
                transitions);
    }

    @Override
    public String write(LifecycleDso lifecycle) {
        List<Map<String, Object>> states = new ArrayList<>();
        for (LifecycleDso.State state : lifecycle.states()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            out.put("id", state.id());
            out.put("title", state.title());
            YamlDumpEmitter.scalarInto(out, "description", state.description());
            state.terminal().ifPresent(terminal -> out.put("terminal", terminal));
            states.add(out);
        }
        List<Map<String, Object>> transitions = new ArrayList<>();
        for (LifecycleDso.Transition transition : lifecycle.transitions()) {
            Map<String, Object> out = YamlDumpEmitter.mapping();
            transition.from().ifPresent(from -> out.put("from", from));
            YamlDumpEmitter.stringsInto(out, "from", transition.fromList());
            out.put("to", transition.to());
            YamlDumpEmitter.scalarInto(out, "event", transition.event());
            List<Map<String, Object>> guard = new ArrayList<>();
            for (LifecycleDso.GuardEntry entry : transition.guard()) {
                Map<String, Object> g = YamlDumpEmitter.mapping();
                g.put("ref", entry.ref());
                entry.config().ifPresent(config -> g.put("config", config.toParsed()));
                entry.blocking().ifPresent(blocking -> g.put("blocking", blocking));
                guard.add(g);
            }
            YamlDumpEmitter.mappingsInto(out, "guard", guard);
            transitions.add(out);
        }
        return new YamlDumpEmitter()
                .scalar("schema_version", lifecycle.schemaVersion())
                .scalar("id", lifecycle.id())
                .scalar("title", lifecycle.title())
                .scalar("description", lifecycle.description())
                .scalar("initial", lifecycle.initial())
                .mappings("states", states)
                .mappings("transitions", transitions)
                .dump();
    }

    private static Optional<Boolean> flag(Map<?, ?> owner, String key) {
        return owner.get(key) instanceof Boolean b ? Optional.of(b) : Optional.empty();
    }
}
