plugins {
    id("charter.spring-conventions")
    id("charter.hand-written")
}

dependencies {
    api(project(":charter-api"))
    implementation(project(":charter-read-app"))
    implementation(catalog.lib("spring-boot-starter-webmvc"))
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
        val file = out.get().file("ai/speckitty/charter/rest/contract-version.properties").asFile
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
        val file = out.get().file("ai/speckitty/charter/rest/service-version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$value\n")
    }
}
sourceSets.main { resources.srcDir(serviceVersion) }
