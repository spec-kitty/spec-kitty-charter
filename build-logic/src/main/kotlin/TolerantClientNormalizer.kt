import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import org.openapitools.codegen.OpenAPINormalizer

/**
 * Reads the contract the way a client generated from it must: every closed object (`additionalProperties: false`)
 * as open, so a member a later 0.x minor adds does not fail the decode (#217, #218).
 *
 * The contract closes every response object, and the generator turns a closed object into a decode-time check that
 * throws on any member it does not declare. A client must tolerate those members instead; the generated models keep
 * them in their additional-properties map. An open map (`Links`) and every real `additionalProperties` schema stay
 * as they are. It applies the duplicate-model fix of `SingleModelPerSchemaNormalizer` first (#119).
 *
 * The generator loads it by name, from `openapiNormalizer`'s `NORMALIZER_CLASS` in `charter.client-java`. The
 * server build (`charter.contract-api`) keeps the closed objects.
 */
class TolerantClientNormalizer(openAPI: OpenAPI, rules: Map<String, String>) : OpenAPINormalizer(openAPI, rules) {

    public override fun normalize() {
        SchemaWalker(::openClosedObject).openApi(openAPI)
        super.normalize()
    }

    private fun openClosedObject(s: Schema<*>) {
        SingleModelPerSchemaNormalizer.readBooleanSchema(s)
        if (s.additionalProperties == false) s.additionalProperties = true
    }
}
