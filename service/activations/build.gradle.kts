// The activations vertical slice (ADR-0006, ADR-0020): the activation register under ai.speckitty.charter.activations,
// with its domain, application, infra.api and infra.packs rings. It sits over the packs slice and asks it only through
// its own application port (AvailablePacks), which only infra.packs implements (docs/architecture/hexagonal-service.md).
plugins {
    id("charter.spring-conventions")
    id("charter.hand-written")
    // The nightly mutation floor (#239, ADR-0062 as amended 2026-11-06).
    id("charter.mutation-conventions")
}

dependencies {
    api(project(":kernel"))
    // infra.packs: the one adapter that names the packs slice, and only its application ring.
    implementation(project(":packs"))

    // infra.api: the inbound REST adapter implements the generated activation contract interface.
    api(project(":charter-api"))
    // The service-wide transport: problem rendering, refusal mapping and the JSON body guard.
    implementation(project(":web-transport"))
    implementation(catalog.lib("spring-boot-starter-webmvc"))
    implementation(catalog.lib("jackson-databind"))

    testImplementation(catalog.lib("spring-boot-starter-test"))
    // The standalone MockMvc tests of infra.api validate the generated interface's constraints, as the service does.
    testImplementation(catalog.lib("spring-boot-starter-validation"))
    testImplementation(catalog.lib("archunit"))
}

// The nightly mutation floor (#239): one group for the whole module, ruled at 85 by the operator on 2026-11-06
// (a trial run scored 91.4 %); raised by PR once the score clears it by 5.
mutationFloors {
    group("activations") {
        packages.add("ai.speckitty.charter.activations")
        floor = 85
    }
}
