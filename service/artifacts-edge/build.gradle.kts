// The edge rings of the artifacts reads (ADR-0006, ADR-0037 as amended 2026-11-13; research R13 of the slice-a2
// cutover reads): application, infra.api, infra.packs and infra.activations under ai.speckitty.charter.artifacts. They
// hold the asset, skill and references reads of both trees, the pack and the activation, with one body mapper. They
// cannot live in :activations, which names packs only from its infra.packs adapter, and they are not the packs slice's
// own reads. The edge has no domain module: its application ring speaks in kernel types and its own value records. It
// asks the packs and activations slices only through its own infra.packs and infra.activations adapters, and only
// their application and domain rings (docs/architecture/hexagonal-service.md).
plugins {
    id("charter.spring-conventions")
    id("charter.hand-written")
    // The nightly mutation floor (#239, ADR-0062 as amended 2026-11-12). Never charter.domain-conventions: it refuses
    // the external dependencies a ring of the edge needs.
    id("charter.mutation-conventions")
}

dependencies {
    // The shared kernel, which the application ring speaks in: the edge has no domain module of its own.
    api(project(":kernel"))
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
    // infra.api: the Contextive document is answered as YAML text (ADR-0076 decision 6). Already in the catalog and
    // the verification metadata, and on the runtime classpath through :packs, so nothing new is fetched.
    implementation(catalog.lib("snakeyaml-engine"))

    testImplementation(catalog.lib("spring-boot-starter-test"))
    // The example packs (ADR-0070): SC-001 reads every term of the built-in vocabulary through the real store.
    testImplementation(testFixtures(project(":packs")))
}

// ContextiveYamlTest checks the contract's Contextive example against the rendered text (#106): the contract directory
// is a declared input, handed over as charter.contractsDir, so the test never searches for it.
tasks.test {
    val contracts = rootProject.layout.projectDirectory.dir("contracts/charter")
    inputs.dir(contracts.dir("examples")).withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("contractExamples")
    systemProperty("charter.contractsDir", contracts.asFile.absolutePath)
}

// The nightly mutation floor (#239): one group for the whole module, at the floor of :activations (85) until the first
// score is known; raised by PR once the score clears it by 5.
mutationFloors {
    group("artifacts-edge") {
        packages.addAll("ai.speckitty.charter.artifacts.application", "ai.speckitty.charter.artifacts.infra")
        excludes.addAll(
            "ai.speckitty.charter.artifacts.application.config",
            "ai.speckitty.charter.artifacts.infra.api.config",
            "ai.speckitty.charter.artifacts.infra.packs.config",
            "ai.speckitty.charter.artifacts.infra.activations.config",
        )
        floor = 85
    }
}
