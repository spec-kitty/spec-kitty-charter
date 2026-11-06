import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import org.openapitools.codegen.OpenAPINormalizer

/**
 * Gives the generator one shape per schema file, so each is generated as one model (issue #119).
 *
 * The parser inlines every schema file it meets, and reads `additionalProperties: false` sometimes as the boolean
 * `false` and sometimes as a schema object that stands for `false` (a boolean schema). The generator takes any
 * schema object for an open map, so it holds two different inline schemas under one title and emits the second as
 * a copy: `Tactic` and `Tactic1`. This reads every boolean schema in an `additionalProperties` back as its boolean,
 * so `false` stays closed and `true` stays open; a real schema, `{}` included, is left as it is. The generator writes
 * the same class for both readings, so the copies are the only output that changes.
 *
 * The generator loads it by name, from `openapiNormalizer`'s `NORMALIZER_CLASS` in `charter.contract-api`.
 */
class SingleModelPerSchemaNormalizer(openAPI: OpenAPI, rules: Map<String, String>) :
    OpenAPINormalizer(openAPI, rules) {

    public override fun normalize() {
        SchemaWalker(::readBooleanSchema).openApi(openAPI)
        super.normalize()
    }

    companion object {
        /** Reads a boolean-schema `additionalProperties` back as its boolean; a real schema, `{}` included, stays. */
        fun readBooleanSchema(s: Schema<*>) {
            (s.additionalProperties as? Schema<*>)?.booleanSchemaValue?.let { s.additionalProperties = it }
        }
    }
}
