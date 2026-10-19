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
}

// The BDD scenarios are part of the contract, so they live beside the OpenAPI document and are put on the
// test classpath from there, under the resource name `scenarios` the suite selects. Only that subtree:
// the rest of the contract tree is not a test resource.
tasks.processTestResources {
    from(rootProject.layout.projectDirectory.dir("contracts/charter")) { include("scenarios/**") }
}
