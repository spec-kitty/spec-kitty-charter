// The pack-check CLI (ADR-0075 decision 9, #478): a thin main over pack-validation, with no Spring and no CLI framework,
// that checks a set of packs and writes the contract's problem JSON. Its main, PackCheckMain, is the composition root:
// it plugs the packs slice's file-system reader (FileSystemPackInspector) and the production round trip (PackRoundTrip)
// into pack-validation's pack-set check, so it names :packs too. The jar that ships on the service-v* release is added by
// a build-logic convention (#478, WP06), not here.
//
// It may depend on pack-validation, :packs and a JSON writer only, pinned below. HexagonalArchitectureTest's
// PACK_VALIDATION_IS_FRAMEWORK_FREE pins the classes it uses: no Spring, HTTP, commit store or lock, and only
// PackCheckMain may name the two adapters.
plugins {
    id("charter.java-base")
    id("charter.hand-written")
    // The nightly mutation floor (#239), as pack-validation's.
    id("charter.mutation-conventions")
}

ModuleDependencyAllowlist(
    projects = setOf(":pack-validation", ":packs"),
    modules = setOf(
        // The JSON writer of the report.
        "tools.jackson.core:jackson-databind",
    ),
).enforceOn(project)

dependencies {
    implementation(project(":pack-validation"))
    // The file-system reader and the production round trip the main plugs in (ADR-0075 as amended 2026-11-13, #553).
    implementation(project(":packs"))
    // Its version comes from the BOM pack-validation exports, as :packs' does.
    implementation(catalog.lib("jackson-databind"))

    // The charter-owned example packs (ADR-0070) and the planted packs the CLI's tests run over.
    testImplementation(testFixtures(project(":packs")))
}

// The nightly mutation floor (#239; ADR-0062): one group for the module, the CLI's arguments, config root, problems,
// report and main. Measured on #478's WP04 head: 115 of 126 mutants detected (91.2 %), so floor5(score - 3) is 85 and
// the margin is 7 mutants. Raised by PR once the score clears the floor by 5.
mutationFloors {
    group("pack-check") {
        packages.add("ai.speckitty.charter.packcheck")
        floor = 85
    }
}

// The link plants skip where the file system cannot make a symbolic link; -PrequireSymlinks=true, which CI passes, makes
// them fail there instead (#286), as in :packs and :pack-validation.
val requireSymlinks = providers.gradleProperty("requireSymlinks").orElse("false")

tasks.test {
    inputs.property("requireSymlinks", requireSymlinks)
    systemProperty("charter.requireSymlinks", requireSymlinks.get())
}
