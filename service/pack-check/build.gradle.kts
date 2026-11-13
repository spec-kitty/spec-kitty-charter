// The pack-check CLI (ADR-0075 decision 9): a thin main over pack-validation, with no Spring and no CLI framework, that
// writes the contract's problem JSON. Its jar ships on the service-v* release (#478). Empty until then (epic #549).
//
// It may depend on pack-validation and a JSON writer only, pinned below. HexagonalArchitectureTest's
// PACK_VALIDATION_IS_FRAMEWORK_FREE pins the classes it uses: no Spring, HTTP, commit store or lock.
plugins {
    id("charter.java-base")
    id("charter.hand-written")
}

ModuleDependencyAllowlist(
    projects = setOf(":pack-validation"),
    modules = setOf(
        // The JSON writer of the problem output.
        "tools.jackson.core:jackson-databind",
        // A BOM only, which pins the Jackson version the service runs; it brings no Spring class.
        "org.springframework.boot:spring-boot-dependencies",
    ),
).enforceOn(project)

dependencies {
    implementation(project(":pack-validation"))
}

// The javadoc tool refuses a source set of package-info files alone ("No public or protected classes found to
// document"). This skeleton has no type yet, so its javadoc is skipped until it has one, and then runs with every gate.
// Remove this block with the first type (#478).
tasks.javadoc {
    val sources = source
    onlyIf("the module holds a type, not only package-info") { sources.any { it.name != "package-info.java" } }
}
