// The service-wide HTTP transport under ai.speckitty.charter.web: problem rendering, refusal mapping and the JSON body
// guard, shared by every slice's inbound adapter (packs.infra.api, activations.infra.api). It is an adapter ring that
// names no slice, and no domain or application ring names it (docs/architecture/hexagonal-service.md).
plugins {
    id("charter.spring-conventions")
    id("charter.hand-written")
}

dependencies {
    api(project(":kernel"))
    api(project(":charter-api"))
    implementation(catalog.lib("spring-boot-starter-webmvc"))
    implementation(catalog.lib("jackson-databind"))

    testImplementation(catalog.lib("spring-boot-starter-test"))
    // The method-validation violations ValidationProblemTest builds come from a real validator.
    testImplementation(catalog.lib("spring-boot-starter-validation"))
    testImplementation(catalog.lib("archunit"))
}
