// The charter-api module: server interfaces and transport DTOs generated from contracts/charter/openapi.yaml.
// Nothing here is hand-written; a contract change the controllers do not follow fails their compile.
// It builds on the base conventions only and never applies `charter.hand-written`: no gate reaches generated code.
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask

plugins {
    id("charter.spring-conventions")
    id("org.openapi.generator")
}

val contract = rootProject.layout.projectDirectory.file("contracts/charter/openapi.yaml")
// The generator reads the charter's split contract and the vendored shared components it references, and
// nothing else under contracts/: the checker's sources and the lint fixtures are not inputs.
val contractDirs = listOf("contracts/charter", "contracts/_shared").map { rootProject.layout.projectDirectory.dir(it) }
val generatedDir = layout.buildDirectory.dir("generated/openapi")

val validateContract = tasks.register<ValidateTask>("validateContract") {
    group = "verification"
    description = "Validates the split charter contract with the generator's OpenAPI parser."
    inputSpec = contract
    recommend = true
}

val generateContract = tasks.register<GenerateTask>("generateContract") {
    group = "build"
    description = "Generates the Spring server interfaces and DTOs of the charter contract."
    dependsOn(validateContract)
    contractDirs.forEachIndexed { i, dir ->
        inputs.dir(dir).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("contractTree$i")
    }
    generatorName = "spring"
    inputSpec = contract
    outputDir = generatedDir
    apiPackage = "ai.speckitty.charter.api"
    modelPackage = "ai.speckitty.charter.api.model"
    cleanupOutput = true
    globalProperties = mapOf("apis" to "", "models" to "")
    // The parser makes a second copy of a schema file referenced from two places; this folds it back (#119).
    openapiNormalizer = mapOf("NORMALIZER_CLASS" to SingleModelPerSchemaNormalizer::class.java.name)
    configOptions = mapOf(
        "interfaceOnly" to "true",
        "useSpringBoot4" to "true",
        "useJakartaEe" to "true",
        "useTags" to "true",
        "openApiNullable" to "false",
        "skipDefaultInterface" to "true",
        "documentationProvider" to "none",
        "annotationLibrary" to "none",
        "useBeanValidation" to "true",
        "performBeanValidation" to "false",
        "serializableModel" to "false",
        "hideGenerationTimestamp" to "true",
        // An optional list stays null until set, so an absent member is told apart from [] (#125, research D-12).
        "containerDefaultToNull" to "true",
        // Pinned to the generator's default so the output stays as is and the notices stay quiet.
        "generateJsonIncludeAnnotations" to "false",
        "generateJsonSetterNullsAnnotations" to "false",
    )
}

sourceSets.main { java.srcDir(generatedDir.map { it.dir("src/main/java") }) }
tasks.named("compileJava") { dependsOn(generateContract) }
tasks.named("check") { dependsOn(validateContract) }

dependencies {
    api(catalog.lib("spring-web"))
    api(catalog.lib("spring-context"))
    api(catalog.lib("jackson-annotations"))
    api(catalog.lib("jakarta-validation-api"))
    api(catalog.lib("jakarta-annotation-api"))
}
