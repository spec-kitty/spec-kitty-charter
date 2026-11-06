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
 * For the service's generated code only, it also binds the enum schemas named in [BOUND_AS_STRING] as plain
 * strings. The contract, and so the wire and the generated client, keeps the enum; the service judges the token
 * itself, after the request's preconditions, so a token outside the enum is a `422` with the service's own violation
 * (the cascade's `unknown_kind` at `/cascade/<i>`) and never a refusal of the body read that would come before
 * `428`, `404` and `412`.
 *
 * The generator loads it by name, from `openapiNormalizer`'s `NORMALIZER_CLASS` in `charter.contract-api`.
 */
class SingleModelPerSchemaNormalizer(openAPI: OpenAPI, rules: Map<String, String>) :
    OpenAPINormalizer(openAPI, rules) {

    public override fun normalize() {
        SchemaWalker { s ->
            readBooleanSchema(s)
            bindAsString(s)
        }.openApi(openAPI)
        super.normalize()
    }

    companion object {
        /** The enum schemas, by title, that the service's generated code binds as plain strings (see the class comment). */
        private val BOUND_AS_STRING = setOf("CascadeKind")

        /** Reads a boolean-schema `additionalProperties` back as its boolean; a real schema, `{}` included, stays. */
        fun readBooleanSchema(s: Schema<*>) {
            (s.additionalProperties as? Schema<*>)?.booleanSchemaValue?.let { s.additionalProperties = it }
        }

        private fun bindAsString(s: Schema<*>) {
            if (s.title in BOUND_AS_STRING) s.enum = null
        }
    }
}
