import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.JsonSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.parser.core.models.ParseOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SingleModelPerSchemaNormalizerTest {

    private val spec = """
        openapi: 3.1.0
        info: {title: t, version: 0.0.0}
        paths: {}
        components:
          schemas:
            Closed: {type: object, additionalProperties: false}
            Open: {type: object, additionalProperties: true}
            Empty: {type: object, additionalProperties: {}}
            Map: {type: object, additionalProperties: {type: string}}
    """.trimIndent()

    private fun parsed(): OpenAPI =
        OpenAPIParser().readContents(spec, null, ParseOptions().apply { isResolve = true }).openAPI

    private fun OpenAPI.additional(name: String): Any? = components.schemas.getValue(name).additionalProperties

    private fun OpenAPI.normalized(): OpenAPI = apply { SingleModelPerSchemaNormalizer(this, emptyMap()).normalize() }

    @Test
    fun aClosedSchemaCollapsesToFalseWhetherReadAsBooleanOrSchemaObject() {
        // A naturally-parsed closed schema already reads as boolean `false`; normalization must not open it.
        assertThat(parsed().normalized().additional("Closed")).isEqualTo(false)
        // The parser's *second* reading of the same file yields a schema object standing for `false`
        // (the #119 bug shape); the normalizer must collapse that back to boolean `false` as well.
        val api = parsed()
        api.components.schemas.getValue("Closed").additionalProperties = JsonSchema().booleanSchemaValue(false)
        assertThat(api.normalized().additional("Closed")).isEqualTo(false)
    }

    @Test
    fun anOpenSchemaStaysOpen() {
        val api = parsed().normalized()
        assertThat(api.additional("Open")).isIn(true, JsonSchema().booleanSchemaValue(true))
        assertThat(api.additional("Empty")).isInstanceOf(Schema::class.java)
        assertThat((api.additional("Empty") as Schema<*>).booleanSchemaValue as Any?).isNull()
        assertThat((api.additional("Map") as Schema<*>).types).containsExactly("string")
    }
}
