import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.JsonSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.parser.core.models.ParseOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TolerantClientNormalizerTest {

    private val spec = """
        openapi: 3.1.0
        info: {title: t, version: 0.0.0}
        paths:
          /x:
            get:
              responses:
                '200':
                  description: ok
                  content:
                    application/json:
                      schema:
                        type: object
                        additionalProperties: false
                        properties:
                          inner: {type: object, additionalProperties: false, properties: {a: {type: string}}}
        components:
          schemas:
            Closed: {type: object, additionalProperties: false, properties: {a: {type: string}}}
            Composed:
              allOf:
                - ${'$'}ref: '#/components/schemas/Closed'
                - {type: object, additionalProperties: false, properties: {b: {type: string}}}
            Links: {type: object, additionalProperties: {${'$'}ref: '#/components/schemas/Closed'}}
            Empty: {type: object, additionalProperties: {}}
    """.trimIndent()

    private fun normalized(): OpenAPI =
        OpenAPIParser().readContents(spec, null, ParseOptions().apply { isResolve = true }).openAPI
            .apply { TolerantClientNormalizer(this, emptyMap()).normalize() }

    private fun OpenAPI.schema(name: String): Schema<*> = components.schemas.getValue(name)

    @Test
    fun everyClosedObjectIsReadAsOpenWhereverItSits() {
        val api = normalized()
        assertThat(api.schema("Closed").additionalProperties).isEqualTo(true)
        assertThat(api.schema("Composed").allOf[1].additionalProperties).isEqualTo(true)
        val response = api.paths.getValue("/x").get.responses.getValue("200").content.getValue("application/json").schema
        assertThat(response.additionalProperties).isEqualTo(true)
        assertThat(response.properties.getValue("inner").additionalProperties).isEqualTo(true)
    }

    @Test
    fun aClosedObjectReadAsABooleanSchemaIsOpenedToo() {
        val api = OpenAPIParser().readContents(spec, null, ParseOptions().apply { isResolve = true }).openAPI
        api.components.schemas.getValue("Closed").additionalProperties = JsonSchema().booleanSchemaValue(false)
        TolerantClientNormalizer(api, emptyMap()).normalize()
        assertThat(api.schema("Closed").additionalProperties).isEqualTo(true)
    }

    @Test
    fun anOpenMapAndARealSchemaStayAsTheyAre() {
        val api = normalized()
        val links = api.schema("Links").additionalProperties as Schema<*>
        assertThat(links.`$ref`).isEqualTo("#/components/schemas/Closed")
        assertThat(api.schema("Empty").additionalProperties).isInstanceOf(Schema::class.java)
    }
}
