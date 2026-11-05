// The activations vertical slice (ADR-0006, ADR-0020): the activation register under ai.speckitty.charter.activations,
// with its domain, application, infra.api and infra.packs rings. It sits over the packs slice and asks it only through
// its own application port (AvailablePacks), which only infra.packs implements (docs/architecture/hexagonal-service.md).
plugins {
    id("charter.spring-conventions")
    id("charter.hand-written")
}

dependencies {
    api(project(":kernel"))
    // infra.packs: the one adapter that names the packs slice, and only its application ring.
    implementation(project(":packs"))

    // infra.api: the inbound REST adapter implements the generated activation contract interface.
    api(project(":charter-api"))
    implementation(catalog.lib("spring-boot-starter-webmvc"))
    implementation(catalog.lib("jackson-databind"))

    testImplementation(catalog.lib("spring-boot-starter-test"))
    testImplementation(catalog.lib("archunit"))
}
