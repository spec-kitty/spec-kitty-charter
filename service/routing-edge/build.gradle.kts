// The edge rings of the routing slice (ADR-0006, ADR-0037, ADR-0080): application, infra.api and infra.activations under
// ai.speckitty.charter.routing, in the slice's own package names. They sit apart from the domain (:routing) because
// packs.infra.storage binds RoutingCatalog, so :packs depends on :routing and a ring that asks the activations slice
// cannot live there. The slice asks the activations slice only through its own infra.activations adapter, and only its
// application and domain rings (docs/architecture/hexagonal-service.md).
plugins {
    id("charter.spring-conventions")
    id("charter.hand-written")
    // The weekly mutation floor (#239, ADR-0062). Never charter.domain-conventions: it refuses the external
    // dependencies a ring of the edge needs.
    id("charter.mutation-conventions")
}

dependencies {
    // The slice's domain, which the application ring and the adapters speak in: the catalog and the ranker.
    api(project(":routing"))
    // infra.activations: the one adapter that names the activations slice, and only its application and domain rings.
    implementation(project(":activations"))
    // The service-wide transport: problem rendering, refusal mapping and the JSON body guard.
    implementation(project(":web-transport"))

    // infra.api: the inbound REST adapter implements the generated contract interface.
    api(project(":charter-api"))
    implementation(catalog.lib("spring-boot-starter-webmvc"))
    implementation(catalog.lib("jackson-databind"))

    testImplementation(catalog.lib("spring-boot-starter-test"))
}

// The weekly mutation floor (#239): one group for the whole module, at the floor of :profiles-edge (85). Raised by PR
// once the nightly score clears it by 5.
mutationFloors {
    group("routing-edge") {
        packages.addAll("ai.speckitty.charter.routing.application", "ai.speckitty.charter.routing.infra")
        excludes.addAll(
            "ai.speckitty.charter.routing.application.config",
            "ai.speckitty.charter.routing.infra.api.config",
            "ai.speckitty.charter.routing.infra.activations.config",
        )
        floor = 85
    }
}
