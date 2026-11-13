// The pack-validation module (ADR-0075 decision 3): every pack check as a kernel ruleset, the codecs and classification
// those checks need, and a load that builds the pack's contents and census, all without Spring. The packs slice and the
// pack-check CLI both run it. Empty until slice #553 moves the load in (epic #549).
//
// Not charter.domain-conventions: that refuses every external dependency, and this module holds the parsing libraries.
// It pins its own instead, below. HexagonalArchitectureTest's PACK_VALIDATION_IS_FRAMEWORK_FREE pins the classes it
// uses: no Spring, HTTP, commit store or lock.
plugins {
    id("charter.java-base")
    id("charter.hand-written")
}

ModuleDependencyAllowlist(
    projects = setOf(":kernel", ":profiles", ":missions"),
    modules = setOf(
        // The parsing libraries the codecs and the schema validation use (ADR-0017), as :packs declares them today.
        "org.snakeyaml:snakeyaml-engine",
        "com.networknt:json-schema-validator",
        "tools.jackson.core:jackson-databind",
        "com.google.re2j:re2j",
        // A BOM only, which aligns the Jackson and slf4j versions with the service's; it brings no Spring class.
        "org.springframework.boot:spring-boot-dependencies",
    ),
).enforceOn(project)

dependencies {
    api(project(":kernel"))
    api(project(":profiles"))
    api(project(":missions"))
}
