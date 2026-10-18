// The charter-api module: server interfaces and transport DTOs generated from contracts/charter/openapi.yaml.
// Nothing here is hand-written; a contract change the controllers do not follow fails their compile.
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.openapitools.generator.gradle.plugin.tasks.ValidateTask

plugins {
    id("charter.spring-conventions")
    id("org.openapi.generator")
}

val contract = rootProject.layout.projectDirectory.file("contracts/charter/openapi.yaml")
val contractDir = rootProject.layout.projectDirectory.dir("contracts")
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
    inputs.dir(contractDir)
    generatorName = "spring"
    inputSpec = contract
    outputDir = generatedDir
    apiPackage = "ai.speckitty.charter.api"
    modelPackage = "ai.speckitty.charter.api.model"
    cleanupOutput = true
    globalProperties = mapOf("apis" to "", "models" to "")
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
        "containerDefaultToNull" to "false",
        // Pinned to the generator's default so the output stays as is and the notices stay quiet.
        "generateJsonIncludeAnnotations" to "false",
        "generateJsonSetterNullsAnnotations" to "false",
    )
}

sourceSets.main { java.srcDir(generatedDir.map { it.dir("src/main/java") }) }
tasks.named("compileJava") { dependsOn(generateContract) }
// Generated sources are not held to the hand-written lint bar.
tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.removeAll(listOf("-Werror", "-Xlint:all"))
    options.compilerArgs.add("-Xlint:none")
}
tasks.named("check") { dependsOn(validateContract) }

dependencies {
    api(catalog.lib("spring-web"))
    api(catalog.lib("spring-context"))
    api(catalog.lib("jackson-annotations"))
    api(catalog.lib("jakarta-validation-api"))
    api(catalog.lib("jakarta-annotation-api"))
}
