package ai.speckitty.charter.packs.infra.storage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The persistence shape of a pack skill: a flat mirror of the fields of a {@code *.skill.yaml}, not the domain entity,
 * plus the text of its prompt body (the companion its {@code body_path} names, ADR-0039). Its record equality is the
 * round-trip fidelity assertion: lists are order- and duplicate-sensitive, and an absent optional differs from a
 * present one, so a flag written {@code false} stays written and an omitted one stays omitted. An explicit
 * {@code null}, an absent key, an empty list and an empty mapping read alike and are written as an omitted key, which
 * ADR-0036 counts as the same meaning.
 *
 * @param schemaVersion the {@code schema_version}
 * @param id the skill's {@code id}
 * @param title the {@code title}
 * @param description the {@code description}
 * @param triggers the {@code triggers}, in authored order
 * @param form the {@code form}, as text
 * @param bodyPath the {@code body_path}, when written
 * @param expandsTo the {@code expands_to}, when written
 * @param parameters the {@code parameters}, in authored order
 * @param invocation the {@code invocation}, when written with at least one field
 * @param tools the {@code tools}, in authored order
 * @param version the {@code version}, when written
 * @param maintainers the {@code maintainers}, in authored order
 * @param overrides the {@code overrides}, when written
 * @param enhances the {@code enhances}, when written
 * @param body the text of the file {@code body_path} names, once the store has read it
 */
record SkillDso(
        String schemaVersion,
        String id,
        String title,
        String description,
        List<String> triggers,
        String form,
        Optional<String> bodyPath,
        Optional<Expansion> expandsTo,
        List<Parameter> parameters,
        Optional<Invocation> invocation,
        List<String> tools,
        Optional<String> version,
        List<String> maintainers,
        Optional<String> overrides,
        Optional<String> enhances,
        Optional<String> body) {

    /**
     * Checks that the required fields are present and copies the lists.
     *
     * @param schemaVersion the schema version
     * @param id the id
     * @param title the title
     * @param description the description
     * @param triggers the triggers
     * @param form the form
     * @param bodyPath the body path, possibly empty
     * @param expandsTo the expansion, possibly empty
     * @param parameters the parameters
     * @param invocation the invocation, possibly empty
     * @param tools the tools
     * @param version the version, possibly empty
     * @param maintainers the maintainers
     * @param overrides the overridden skill, possibly empty
     * @param enhances the enhanced skill, possibly empty
     * @param body the body, possibly empty
     */
    SkillDso {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(bodyPath, "bodyPath");
        Objects.requireNonNull(expandsTo, "expandsTo");
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(overrides, "overrides");
        Objects.requireNonNull(enhances, "enhances");
        Objects.requireNonNull(body, "body");
        triggers = List.copyOf(triggers);
        parameters = List.copyOf(parameters);
        tools = List.copyOf(tools);
        maintainers = List.copyOf(maintainers);
    }

    /**
     * The same shape holding a body.
     *
     * @param text the body, possibly empty
     * @return a copy with {@code body} replaced
     */
    SkillDso withBody(Optional<String> text) {
        return new SkillDso(
                schemaVersion,
                id,
                title,
                description,
                triggers,
                form,
                bodyPath,
                expandsTo,
                parameters,
                invocation,
                tools,
                version,
                maintainers,
                overrides,
                enhances,
                text);
    }

    /**
     * What a wrapper skill expands to.
     *
     * @param target the {@code target}
     * @param args the {@code args}, when written
     */
    record Expansion(String target, Optional<String> args) {
        /**
         * Checks the fields.
         *
         * @param target the target
         * @param args the args, possibly empty
         */
        Expansion {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(args, "args");
        }
    }

    /**
     * One declared parameter.
     *
     * @param name the {@code name}
     * @param required the {@code required} flag, when written
     * @param description the {@code description}, when written
     * @param defaultValue the {@code default}, when written
     */
    record Parameter(
            String name, Optional<Boolean> required, Optional<String> description, Optional<String> defaultValue) {
        /**
         * Checks the fields.
         *
         * @param name the name
         * @param required the required flag, possibly empty
         * @param description the description, possibly empty
         * @param defaultValue the default, possibly empty
         */
        Parameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(required, "required");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(defaultValue, "defaultValue");
        }
    }

    /**
     * Who may invoke the skill.
     *
     * @param userInvocable the {@code user_invocable} flag, when written
     * @param modelInvocable the {@code model_invocable} flag, when written
     * @param sideEffects the {@code side_effects}, in authored order
     */
    record Invocation(Optional<Boolean> userInvocable, Optional<Boolean> modelInvocable, List<String> sideEffects) {
        /**
         * Checks the fields and copies the list.
         *
         * @param userInvocable the user flag, possibly empty
         * @param modelInvocable the model flag, possibly empty
         * @param sideEffects the side effects
         */
        Invocation {
            Objects.requireNonNull(userInvocable, "userInvocable");
            Objects.requireNonNull(modelInvocable, "modelInvocable");
            sideEffects = List.copyOf(sideEffects);
        }

        /** Whether no field is written: such an invocation means the same as none (ADR-0036 decision 2). */
        boolean isEmpty() {
            return userInvocable.isEmpty() && modelInvocable.isEmpty() && sideEffects.isEmpty();
        }
    }
}
