// The routing vertical slice (ADR-0006, ADR-0080): the routing catalog's domain and the ranker under
// ai.speckitty.charter.routing. Plain Java, like the kernel; the pack store binds the kind and depends on this module,
// never the other way (ADR-0037, amendment of 2026-11-18).
plugins {
    id("charter.domain-conventions")
    id("charter.hand-written")
}

dependencies {
    api(project(":kernel"))
}

// The weekly mutation floor (#239): one group for the whole module; raised by PR once the score clears it by 5.
mutationFloors {
    group("routing") {
        packages.add("ai.speckitty.charter.routing.domain")
        floor = 90
    }
}
