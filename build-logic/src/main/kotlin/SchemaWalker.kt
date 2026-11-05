import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.callbacks.Callback
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import java.util.IdentityHashMap

/**
 * Visits every schema of a parsed document once, wherever it sits: under components, paths, webhooks, and nested in
 * other schemas. The visitor runs on a schema before its subschemas, so a subschema it replaces is not walked.
 * The contract normalizers (`SingleModelPerSchemaNormalizer`, `TolerantClientNormalizer`) share it.
 */
internal class SchemaWalker(private val visit: (Schema<*>) -> Unit) {
    private val seen = IdentityHashMap<Any, Unit>()

    fun openApi(api: OpenAPI) {
        api.paths?.values?.forEach(::pathItem)
        api.webhooks?.values?.forEach(::pathItem)
        api.components?.let(::components)
    }

    private fun components(c: Components) {
        c.schemas?.values?.forEach(::schema)
        c.responses?.values?.forEach(::response)
        c.parameters?.values?.forEach(::parameter)
        c.requestBodies?.values?.forEach(::requestBody)
        c.headers?.values?.forEach(::header)
        c.callbacks?.values?.forEach(::callback)
        c.pathItems?.values?.forEach(::pathItem)
    }

    private fun pathItem(p: PathItem) {
        if (seen.put(p, Unit) != null) return
        p.parameters?.forEach(::parameter)
        p.readOperations().forEach(::operation)
    }

    private fun operation(o: Operation) {
        o.parameters?.forEach(::parameter)
        o.requestBody?.let(::requestBody)
        o.responses?.values?.forEach(::response)
        o.callbacks?.values?.forEach(::callback)
    }

    private fun callback(c: Callback) = c.values.forEach(::pathItem)

    private fun parameter(p: Parameter) {
        p.schema?.let(::schema)
        p.content?.let(::content)
    }

    private fun requestBody(r: RequestBody) = r.content?.let(::content)

    private fun response(r: ApiResponse) {
        r.headers?.values?.forEach(::header)
        r.content?.let(::content)
    }

    private fun header(h: Header) {
        h.schema?.let(::schema)
        h.content?.let(::content)
    }

    private fun content(c: Content) = c.values.forEach { media ->
        media.schema?.let(::schema)
        media.encoding?.values?.forEach { it.headers?.values?.forEach(::header) }
    }

    private fun schema(s: Schema<*>) {
        if (seen.put(s, Unit) != null) return
        visit(s)
        (s.additionalProperties as? Schema<*>)?.let(::schema)
        s.properties?.values?.forEach(::schema)
        s.patternProperties?.values?.forEach(::schema)
        s.dependentSchemas?.values?.forEach(::schema)
        listOfNotNull(
            s.items, s.not, s.contains, s.propertyNames, s.`if`, s.then, s.`else`,
            s.unevaluatedItems, s.unevaluatedProperties, s.contentSchema,
        ).forEach(::schema)
        listOfNotNull(s.allOf, s.anyOf, s.oneOf, s.prefixItems).flatten().forEach(::schema)
    }
}
