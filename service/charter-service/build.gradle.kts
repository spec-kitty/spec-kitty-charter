plugins {
    id("charter.service-conventions")
    id("charter.hand-written")
}

dependencies {
    implementation(project(":adapter-in-rest"))
    implementation(project(":adapter-out-yaml"))
    implementation(project(":charter-read-app"))
    implementation(catalog.lib("spring-boot-starter-webmvc"))

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
        include("charter/parameters/**", "_shared/responses/**", "_shared/schemas/**")
        into("contract")
    }
}
