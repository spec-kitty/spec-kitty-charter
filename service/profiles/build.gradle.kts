// The profiles vertical slice (ADR-0006, ADR-0035): the agent-profile domain under ai.speckitty.charter.profiles.
// Plain Java, like the kernel; the pack store binds the kind and depends on this module, never the other way.
plugins {
    id("charter.domain-conventions")
    id("charter.hand-written")
}

dependencies {
    api(project(":kernel"))
}
