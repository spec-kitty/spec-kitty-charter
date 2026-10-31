plugins {
    id("charter.service-conventions")
    id("charter.hand-written")
}

dependencies {
    implementation(project(":kernel"))
    implementation(project(":packs"))
    implementation(catalog.lib("spring-boot-starter-webmvc"))
    implementation(catalog.lib("spring-boot-starter-validation"))

    testImplementation(catalog.lib("spring-boot-starter-test"))
    testImplementation(catalog.lib("spring-boot-webmvc-test"))
    testImplementation(catalog.lib("archunit"))
    testImplementation(platform(catalog.lib("cucumber-bom")))
    testImplementation(catalog.lib("cucumber-java"))
    testImplementation(catalog.lib("cucumber-spring"))
    testImplementation(catalog.lib("cucumber-junit-platform-engine"))
    testImplementation(catalog.lib("junit-platform-suite"))
    testImplementation(catalog.lib("snakeyaml-engine"))
}

// The bundled contract the service serves as GET /openapi.yaml (#209): the `contractBundle` variant :contracts
// publishes, which is the bundleContract output and its checksum. The bundle is packaged beside
// ContractDocumentWiring, which loads it; the tests receive both files of the build output, so they compare the
// answer with what the contract build wrote rather than with the packaged copy.
val contractBundle = configurations.dependencyScope("contractBundle")
val contractBundleFiles = configurations.resolvable("contractBundleFiles") {
    extendsFrom(contractBundle.get())
    attributes { attribute(Category.CATEGORY_ATTRIBUTE, objects.named("charter-contract-bundle")) }
}
dependencies {
    "contractBundle"(project(":contracts"))
}

tasks.processResources {
    from(contractBundleFiles) {
        include("charter-openapi.yaml")
        into("ai/speckitty/charter/deployable")
    }
}

/** Hands the bundle and its checksum, as the contract build wrote them, to the test JVM. */
abstract class ContractBundleArguments : CommandLineArgumentProvider {
    @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY) abstract val files: ConfigurableFileCollection

    override fun asArguments(): List<String> {
        val byName = files.files.associateBy { it.name }
        fun path(name: String) = checkNotNull(byName[name]) { "the contractBundle variant has no $name" }.absolutePath
        return listOf(
            "-Dcharter.test.contract-bundle=${path("charter-openapi.yaml")}",
            "-Dcharter.test.contract-bundle-checksum=${path("charter-openapi.yaml.sha256")}",
        )
    }
}

tasks.test {
    jvmArgumentProviders.add(objects.newInstance<ContractBundleArguments>().apply { files.from(contractBundleFiles) })
}

// The BDD scenarios are part of the contract, so they live beside the OpenAPI document and are put on the
// test classpath from there, under the resource name `scenarios` the suite selects. The OpenAPI document,
// its definitions and the vendored shared components go under `contract/`, where the suite checks every
// response against the schema the contract declares for it. Nothing else of the contract tree is a test
// resource: not the examples, the checker's sources or the lint fixtures.
tasks.processTestResources {
    val contracts = rootProject.layout.projectDirectory.dir("contracts")
    from(contracts.dir("charter")) { include("scenarios/**") }
    from(contracts) {
        include("charter/openapi.yaml", "charter/paths/**", "charter/responses/**", "charter/schemas/**")
        include("charter/parameters/**", "charter/headers/**", "_shared/responses/**", "_shared/schemas/**")
        into("contract")
    }
    // The ActivationConfig corpus (#125), which ActivationConfigConformanceTest runs through the scenario validator.
    from(contracts.dir("offering")) {
        include("corpus/**")
        into("offering")
    }
}

/**
 * Hands the repository root to the test that checks the shipped configuration files (#229): the compose example, its
 * env template and the ignore rules live outside this module, so they are declared as inputs and a change to them
 * reruns the tests.
 */
abstract class ShippedConfigurationArguments : CommandLineArgumentProvider {
    @get:Internal abstract val root: DirectoryProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val files: ConfigurableFileCollection

    /** The tracked paths, which the test scans for an env file or a secret: content, not the index's stat data. */
    @get:Input abstract val trackedFiles: Property<String>

    override fun asArguments(): List<String> = listOf("-Dcharter.test.repo-root=${root.get().asFile.absolutePath}")
}

tasks.test {
    val repository = rootProject.layout.projectDirectory
    jvmArgumentProviders.add(objects.newInstance<ShippedConfigurationArguments>().apply {
        root.set(repository)
        files.from(repository.file("deploy/compose/compose.yaml"), repository.file("deploy/compose/.env.example"))
        files.from(repository.file(".gitignore"))
        trackedFiles.set(providers.exec {
            commandLine("git", "-C", repository.asFile.absolutePath, "ls-files")
            isIgnoreExitValue = true
        }.standardOutput.asText)
    })
}
