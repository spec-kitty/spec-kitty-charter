// Root project: aggregation only. Module builds live under service/, conventions under build-logic/.
tasks.register("verifyAll") {
    group = "verification"
    description = "Runs every module's check, including the contract validation and the open-packs round-trip."
    dependsOn(subprojects.map { "${it.path}:check" })
}
