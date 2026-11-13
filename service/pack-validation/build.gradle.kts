// The pack-validation module (ADR-0075 decision 3, as amended 2026-11-13): every pack check as a kernel ruleset, run
// over the pack's contents that the packs slice's own Spring-free load builds. The service and the pack-check CLI both
// run it.
//
// It depends on :packs and reads a pack through its public load (PackInspector, FileSystemPackInspector, PackContents,
// PackPolicy): the packs domain model stays in :packs (operator ruling of 2026-11-13, #553). :packs brings Spring onto
// the classpath, but nothing this module reaches uses it: HexagonalArchitectureTest's PACK_VALIDATION_IS_FRAMEWORK_FREE
// pins that transitively, and pack-check leaves Spring off its runtime.
//
// Not charter.domain-conventions: that refuses every external dependency. The module pins its own instead, below.
plugins {
    id("charter.java-base")
    id("charter.hand-written")
    // The nightly mutation floor (#239). Never charter.domain-conventions: it refuses the external dependencies this
    // module declares.
    id("charter.mutation-conventions")
}

ModuleDependencyAllowlist(
    projects = setOf(":kernel", ":profiles", ":missions", ":packs"),
    modules = setOf(
        // The parsing libraries the codecs and the schema validation use (ADR-0017), for rules that read them directly.
        "org.snakeyaml:snakeyaml-engine",
        "com.networknt:json-schema-validator",
        "tools.jackson.core:jackson-databind",
        "com.google.re2j:re2j",
        // A BOM only: it gives :packs' versionless dependencies (charter-api's) the versions the service runs, as the
        // Spring conventions do for :packs. It brings no class.
        "org.springframework.boot:spring-boot-dependencies",
    ),
).enforceOn(project)

dependencies {
    api(project(":kernel"))
    api(project(":profiles"))
    api(project(":missions"))
    // The packs slice's Spring-free load and the domain it builds (operator ruling of 2026-11-13, #553).
    api(project(":packs"))
    // Exported, so a consumer (pack-check) resolves :packs' versionless dependencies at the same versions.
    api(platform(catalog.lib("spring-boot-bom")))

    // The charter-owned example packs (ADR-0070).
    testImplementation(testFixtures(project(":packs")))
}

// The nightly mutation floor (#239): one group for the whole module. Its first run (#553) scored 100 % of 2 mutants;
// the floor is 85, as for the packs domain and application, below floor5(score - 3) because the module's rulesets land
// next (#554) and two mutants say little. Raised by PR once the score clears it by 5.
mutationFloors {
    group("pack-validation") {
        packages.add("ai.speckitty.charter.packvalidation")
        floor = 85
    }
}
