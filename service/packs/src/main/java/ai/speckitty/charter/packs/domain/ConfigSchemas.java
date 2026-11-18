package ai.speckitty.charter.packs.domain;

import ai.speckitty.charter.kernel.domain.ConfigValue;
import java.util.List;
import java.util.Optional;

/**
 * The port the closure rule checks a guard's {@code config_schema} and a guard entry's {@code config} through (#355,
 * ADR-0054 decision 4): compiling a JSON Schema offline is not pure domain, so the storage edge implements it and the
 * rule is given it.
 */
public interface ConfigSchemas {

    /**
     * Why a schema does not compile offline as a Draft 2020-12 JSON Schema.
     *
     * @param schema the schema, as a tree
     * @return the problems, each a sentence; empty when it compiles
     */
    List<String> schemaProblems(ConfigValue schema);

    /**
     * Why a value does not fit a schema that compiles.
     *
     * @param schema the schema, as a tree
     * @param value the value; empty when none is given
     * @return the problems, each naming the JSON pointer it is at; empty when it fits
     */
    List<String> valueProblems(ConfigValue schema, Optional<ConfigValue> value);
}
