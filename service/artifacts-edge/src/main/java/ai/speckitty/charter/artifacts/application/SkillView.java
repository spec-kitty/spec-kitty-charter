package ai.speckitty.charter.artifacts.application;

import ai.speckitty.charter.kernel.domain.PackIdentifier;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One skill of a pack as the edge speaks of it (#107, research R8): the fields its file declares, in the edge's own
 * words, so that the inbound adapter names no packs type. The packs adapter maps the packs slice's skill into it. The
 * body path, {@code overrides} and {@code enhances} are not in it: the contract answers content, not paths, and edges
 * are read through the references.
 *
 * @param id the skill's id within its pack
 * @param packId the pack that holds the skill, by the identifier the request named it with
 * @param schemaVersion the schema version the file declares
 * @param title the title
 * @param description when the skill applies
 * @param form the form's token, {@code prompt} or {@code wrapper}
 * @param triggers the trigger phrases, in authored order
 * @param parameters the declared parameters, in authored order
 * @param invocation who may invoke the skill, empty when the file writes no invocation block
 * @param expandsTo what a wrapper skill expands to, empty for a prompt skill
 * @param tools the tools the skill uses, in authored order
 * @param version the skill's own version text, empty when the file writes none
 * @param maintainers the maintainers, in authored order
 * @param body the text of the file a prompt skill's body path names, exactly as held; empty for a wrapper skill
 */
public record SkillView(
        String id,
        PackIdentifier packId,
        String schemaVersion,
        String title,
        String description,
        String form,
        List<String> triggers,
        List<Parameter> parameters,
        Optional<Invocation> invocation,
        Optional<Expansion> expandsTo,
        List<String> tools,
        Optional<String> version,
        List<String> maintainers,
        Optional<String> body) {

    /**
     * Checks and copies the fields.
     *
     * @param id the id
     * @param packId the pack
     * @param schemaVersion the schema version
     * @param title the title
     * @param description the description
     * @param form the form token
     * @param triggers the triggers
     * @param parameters the parameters
     * @param invocation the invocation block
     * @param expandsTo the expansion
     * @param tools the tools
     * @param version the version
     * @param maintainers the maintainers
     * @param body the body
     */
    public SkillView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(form, "form");
        triggers = List.copyOf(Objects.requireNonNull(triggers, "triggers"));
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(expandsTo, "expandsTo");
        tools = List.copyOf(Objects.requireNonNull(tools, "tools"));
        Objects.requireNonNull(version, "version");
        maintainers = List.copyOf(Objects.requireNonNull(maintainers, "maintainers"));
        Objects.requireNonNull(body, "body");
    }

    /**
     * One declared parameter, kept as written: a member the file does not write is empty, so an absent
     * {@code required} is not {@code false}.
     *
     * @param name the name
     * @param required the required flag, when written
     * @param description the description, when written
     * @param defaultValue the default, when written
     */
    public record Parameter(
            String name, Optional<Boolean> required, Optional<String> description, Optional<String> defaultValue) {

        /**
         * Checks the fields.
         *
         * @param name the name
         * @param required the required flag
         * @param description the description
         * @param defaultValue the default
         */
        public Parameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(required, "required");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(defaultValue, "defaultValue");
        }
    }

    /**
     * Who may invoke the skill, exactly as the file writes it.
     *
     * @param userInvocable the user flag, when written
     * @param modelInvocable the model flag, when written
     * @param sideEffects the declared side effects, in authored order
     */
    public record Invocation(
            Optional<Boolean> userInvocable, Optional<Boolean> modelInvocable, List<String> sideEffects) {

        /**
         * Checks and copies the fields.
         *
         * @param userInvocable the user flag
         * @param modelInvocable the model flag
         * @param sideEffects the side effects
         */
        public Invocation {
            Objects.requireNonNull(userInvocable, "userInvocable");
            Objects.requireNonNull(modelInvocable, "modelInvocable");
            sideEffects = List.copyOf(Objects.requireNonNull(sideEffects, "sideEffects"));
        }
    }

    /**
     * What a wrapper skill expands to.
     *
     * @param target the target, a builtin command or a spec-kitty argv
     * @param args the arguments the expansion takes, when written
     */
    public record Expansion(String target, Optional<String> args) {

        /**
         * Checks the fields.
         *
         * @param target the target
         * @param args the arguments
         */
        public Expansion {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(args, "args");
        }
    }
}
