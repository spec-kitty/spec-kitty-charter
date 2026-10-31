// The pack vertical slice: domain, application, infra.storage, infra.api under ai.speckitty.charter.packs.
// WP05 activated the infra.storage dependencies; WP06 activated the infra.api ones.
plugins {
    id("charter.spring-conventions")
    id("charter.hand-written")
}

dependencies {
    api(project(":kernel"))
    // The profiles slice's domain: the pack store binds the agent-profile kind (ADR-0037, amendment of 2026-10-27).
    api(project(":profiles"))
    // The missions slice's domain: the pack store binds the mission-type kind (ADR-0044).
    api(project(":missions"))

    // infra.storage: YAML codec and pack-file schema validation (confined to this module, ADR-0017).
    implementation(catalog.lib("snakeyaml-engine"))
    // networknt json-schema-validator validates pack files against contracts/packs/schemas (ADR-0017).
    // The Spring Boot BOM is used here only to pin Jackson 3 and slf4j to the versions the service runs;
    // networknt's own Jackson (3.2.x) and slf4j pins are excluded so the service classpath does not move.
    implementation(platform(catalog.lib("spring-boot-bom")))
    implementation(catalog.lib("json-schema-validator")) {
        exclude(group = "tools.jackson.core")
        exclude(group = "tools.jackson.dataformat", module = "jackson-dataformat-yaml")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation(catalog.lib("jackson-databind"))
    implementation(catalog.lib("slf4j-api"))

    // infra.api: the inbound REST adapter implements the generated contract interfaces.
    api(project(":charter-api"))
    implementation(catalog.lib("spring-boot-starter-webmvc"))

    testImplementation(catalog.lib("spring-boot-starter-test"))
    // NetworkCodeBoundaryTest: the inner rings name no java.net type but URI (WP08; the version deployable pins).
    testImplementation(catalog.lib("archunit"))
}

// The contract version the service reports (ContractVersion): the info.version of contracts/charter/openapi.yaml,
// written as a resource so the service never parses the contract at run time.
val contractVersion = tasks.register("contractVersionResource") {
    description = "Writes the contract's info.version into contract-version.properties."
    val spec = rootProject.layout.projectDirectory.file("contracts/charter/openapi.yaml")
    val out = layout.buildDirectory.dir("generated/contract-version")
    inputs.file(spec).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("contract")
    outputs.dir(out).withPropertyName("resources")
    doLast {
        val version = Regex("""(?m)^info:\n(?:[ ]+.*\n)*?[ ]{2}version:[ ]*(\S+)""")
            .find(spec.asFile.readText())
            ?.groupValues?.get(1)
            ?: throw GradleException("contracts/charter/openapi.yaml has no info.version")
        val file = out.get().file("ai/speckitty/charter/packs/infra/api/contract-version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$version\n")
    }
}
sourceSets.main { resources.srcDir(contractVersion) }

// The service's own version (ServiceVersion): the root project's `version`, from gradle.properties, written as a
// resource. It is independent of the contract version above. The value is read at configuration time, so the task
// holds no Project at execution and is configuration-cache safe.
val serviceVersion = tasks.register("serviceVersionResource") {
    description = "Writes the root project's version into service-version.properties."
    val value = rootProject.version.toString()
    val out = layout.buildDirectory.dir("generated/service-version")
    inputs.property("version", value)
    outputs.dir(out).withPropertyName("resources")
    doLast {
        val file = out.get().file("ai/speckitty/charter/packs/infra/api/service-version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$value\n")
    }
}
sourceSets.main { resources.srcDir(serviceVersion) }

// ServiceVersionTest checks the written version against the root project's own, which can equal the contract version
// (both are 0.1.0-SNAPSHOT today), so the test cannot tell the two sources apart by their values alone.
tasks.named<Test>("test") {
    val projectVersion = rootProject.version.toString()
    inputs.property("projectVersion", projectVersion)
    systemProperty("charter.projectVersion", projectVersion)
}

// The pack schemas and the file-kind table are bundled as main resources under packs/, so that
// classpath:packs/schemas/<kind>.schema.json and classpath:packs/file-kinds.yaml resolve offline. The copy
// is a declared input of processResources, so a schema change re-runs the tests.
val packContracts = rootProject.layout.projectDirectory.dir("contracts/packs")
tasks.processResources {
    from(packContracts) {
        include("schemas/*.schema.json", "file-kinds.yaml")
        into("packs")
    }
}

// The pinned spec-kitty-open-packs checkout the corpus tests read. Point -PopenPacksDir at a checkout; by default the
// sibling directory ../spec-kitty-open-packs. A missing checkout fails the task, it never skips.
val openPacksDir = providers.gradleProperty("openPacksDir")
    .orElse(rootProject.layout.projectDirectory.dir("../spec-kitty-open-packs").asFile.absolutePath)

// The pin: gradle.properties (openPacksRef) is its single source of truth, read here and by CI. It is validated
// when the task runs, never at configuration time, and is a task input so a new pin is never answered from the cache.
val openPacksRef = providers.gradleProperty("openPacksRef")

val testSourceSet = sourceSets.test.get()
val openPacksRoundTrip = tasks.register<Test>("openPacksRoundTrip") {
    group = "verification"
    description = "Corpus checks over the pinned spec-kitty-open-packs checkout (part of check)."
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform {
        includeTags("open-packs")
        excludeTags("whole-pack")
    }
    val dir = openPacksDir
    inputs.dir(dir).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("openPacks")
    systemProperty("charter.openPacksDir", dir.get())
    // VendoredPacksTest compares the vendored packs of this repository with the pinned checkout.
    val vendoredPacks = rootProject.layout.projectDirectory.dir("packs")
    inputs.dir(vendoredPacks).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("vendoredPacks")
    systemProperty("charter.repositoryRoot", rootProject.layout.projectDirectory.asFile.absolutePath)
    val ref = openPacksRef
    inputs.property("openPacksRef", ref.orElse(""))
    doFirst {
        val pin = ref.orNull
        check(pin != null && Regex("[0-9a-f]{40}").matches(pin)) {
            "openPacksRef in gradle.properties must be 40 lowercase hex characters, but is '${pin ?: ""}'"
        }
        systemProperty("charter.openPacksRef", pin)
    }
}

// The whole-pack round-trip gate (#187): every file of the pinned built-in pack is read into the domain, written back,
// read again and compared with its source by meaning, against the checked-in expected-red baseline
// (src/test/resources/whole-pack). It lands red-first and is NOT part of check until it is green; CI runs it in its own
// non-required workflow (.github/workflows/whole-pack.yml).
val wholePackReport = layout.buildDirectory.file("reports/whole-pack/report.md")
tasks.register<Test>("wholePackRoundTrip") {
    group = "verification"
    description = "The whole-pack round-trip gate over the pinned built-in pack (#187); not part of check."
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform { includeTags("whole-pack") }
    val dir = openPacksDir
    inputs.dir(dir).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("openPacks")
    systemProperty("charter.openPacksDir", dir.get())
    val report = wholePackReport
    outputs.file(report).withPropertyName("wholePackReport")
    systemProperty("charter.wholePackReport", report.get().asFile.absolutePath)
    val ref = openPacksRef
    inputs.property("openPacksRef", ref.orElse(""))
    doFirst {
        val pin = ref.orNull
        check(pin != null && Regex("[0-9a-f]{40}").matches(pin)) {
            "openPacksRef in gradle.properties must be 40 lowercase hex characters, but is '${pin ?: ""}'"
        }
        systemProperty("charter.openPacksRef", pin)
    }
}

tasks.test {
    useJUnitPlatform { excludeTags("open-packs", "whole-pack") }
    // PackSchemasTest checks that every schema file in the repository is bundled and listed in file-kinds.yaml;
    // PlantedFilesTest reads the repository's conformance files. Both find the repository by this property.
    val schemaFiles = packContracts.dir("schemas")
    inputs.dir(schemaFiles).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("packSchemaFiles")
    // PlantedFilesTest and DivergenceRegisterTest read the register in both forms.
    val conformance = rootProject.layout.projectDirectory.dir("docs/conformance")
    inputs.dir(conformance).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("conformanceDocs")
    // ProjectCharterYamlSchemaTest validates the project's own charter.yaml against the charter schema (#131).
    val projectCharter = rootProject.layout.projectDirectory.file(".kittify/charter/charter.yaml")
    inputs.file(projectCharter).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("projectCharterYaml")
    systemProperty("charter.repositoryRoot", rootProject.layout.projectDirectory.asFile.absolutePath)
    // VocabularyEqualityTest checks that every load-path wire code is in the contract's code enums.
    val contractSchemas = rootProject.layout.projectDirectory.dir("contracts/charter/schemas")
    inputs.dir(contractSchemas).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("contractSchemas")
    systemProperty("charter.contractSchemas", contractSchemas.asFile.absolutePath)
    // The link tests skip where the file system cannot make a symbolic link; -PrequireSymlinks=true, which CI passes
    // (service.yml), makes them fail there instead, so a runner without links cannot stay green by skipping (#286).
    val requireSymlinks = providers.gradleProperty("requireSymlinks").orElse("false")
    inputs.property("requireSymlinks", requireSymlinks)
    systemProperty("charter.requireSymlinks", requireSymlinks.get())
}
tasks.check { dependsOn(openPacksRoundTrip) }

