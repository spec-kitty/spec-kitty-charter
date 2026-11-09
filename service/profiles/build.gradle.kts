// The profiles vertical slice (ADR-0006, ADR-0035): the agent-profile domain under ai.speckitty.charter.profiles.
// Plain Java, like the kernel; the pack store binds the kind and depends on this module, never the other way.
plugins {
    id("charter.domain-conventions")
    id("charter.hand-written")
}

dependencies {
    api(project(":kernel"))
}

// The nightly mutation floor (#239): one group for the whole module; raised by PR once the score clears it by 5.
mutationFloors {
    group("profiles") {
        packages.add("ai.speckitty.charter.profiles")
        floor = 50
    }
}
