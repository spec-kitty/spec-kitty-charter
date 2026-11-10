// The edge rings of the missions slice (ADR-0006, ADR-0037 as amended 2026-11-09): application, infra.api, infra.packs
// and infra.activations under ai.speckitty.charter.missions, in the slice's own package names. They sit apart from the
// domain (:missions) because packs.infra.storage binds MissionType, so :packs depends on :missions and a ring that asks
// packs cannot live there. The slice asks the packs and activations slices only through its own infra.packs and
// infra.activations adapters, and only their application rings (docs/architecture/hexagonal-service.md).
plugins {
    id("charter.spring-conventions")
    id("charter.hand-written")
    // The nightly mutation floor (#239, ADR-0062 as amended 2026-11-06). Never charter.domain-conventions: it refuses
    // the external dependencies a ring of the edge needs.
    id("charter.mutation-conventions")
}

dependencies {
    // The slice's domain, which the application ring and the adapters speak in.
    api(project(":missions"))
    // infra.packs: the one adapter that names the packs slice, and only its application and domain rings.
    // :activations does not pass :packs on, so it is declared here.
    implementation(project(":packs"))
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

// The nightly mutation floor (#239): one group for the whole module, at the floor of :activations (85) until the first
// score is known; raised by PR once the score clears it by 5. The skeleton holds no logic yet, so the group has no
// mutant until the rings are built.
mutationFloors {
    group("missions-edge") {
        packages.addAll("ai.speckitty.charter.missions.application", "ai.speckitty.charter.missions.infra")
        excludes.addAll(
            "ai.speckitty.charter.missions.application.config",
            "ai.speckitty.charter.missions.infra.api.config",
            "ai.speckitty.charter.missions.infra.packs.config",
            "ai.speckitty.charter.missions.infra.activations.config",
        )
        floor = 85
    }
}
