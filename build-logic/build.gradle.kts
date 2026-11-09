plugins {
    `kotlin-dsl`
}

// The contract normalizer compiles against the generator, which the plugin brings only at run time. It is
// resolved with the versions the runtime classpath already holds, so compiling against it adds no artifact
// that the build does not already verify and run.
val generatorApi = configurations.register("generatorApi") {
    isCanBeConsumed = false
    shouldResolveConsistentlyWith(configurations.runtimeClasspath.get())
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}

dependencies {
    implementation(libs.gradle.plugin.spring.boot)
    implementation(libs.gradle.plugin.openapi.generator)
    implementation(libs.gradle.plugin.spotless)
    implementation(libs.gradle.plugin.errorprone)
    implementation(libs.gradle.plugin.pitest)
    implementation(libs.gradle.plugin.graalvm.native)
    generatorApi(libs.openapi.generator.lib)
    compileOnly(files(generatorApi))
    testImplementation(files(generatorApi))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // SonarWorkflowGuardTest reads the workflows; without them as inputs a bad workflow gets a cached pass.
    inputs.dir(layout.projectDirectory.dir("../.github/workflows"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("workflows")
    // RegexRecursionGuardTest reads the hand-written main sources; without them as inputs a new pattern gets a cached pass.
    inputs.files(fileTree(layout.projectDirectory.dir("../service")) { include("*/src/main/java/**/*.java") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("serviceMainSources")
    // MutationFloorsTest holds this fixture, read by scripts/ci/test_mutation_summary.py, to what
    // MutationFloors.toJson writes; -PupdateMutationFixture=true rewrites it.
    val fixture = layout.projectDirectory.file("../scripts/ci/fixtures/floors.json").asFile
    inputs.files(fixture).withPathSensitivity(PathSensitivity.NONE).withPropertyName("mutationFloorsFixture")
    systemProperty("mutationFloors.fixture", fixture.absolutePath)
    val update = providers.gradleProperty("updateMutationFixture").orElse("false")
    inputs.property("updateMutationFixture", update)
    systemProperty("mutationFloors.updateFixture", update.get())
}
