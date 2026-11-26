// The kernel: shared Pack/DRG vocabulary (kernel.domain), the validation guard library (kernel.validation), the
// markdown content form's line rules (kernel.markdown, #299) and the work meter that counts the units of work one call
// does (kernel.metering, #659).
// Pure Java by construction: charter.domain-conventions rejects any external dependency, so no Spring, SnakeYAML
// or networknt can enter. jspecify and the test stack arrive through charter.hand-written / charter.java-base.
plugins {
    id("charter.domain-conventions")
    id("charter.hand-written")
}

// The weekly mutation floor (#239): one group for the whole module; raised by PR once the score clears it by 5.
mutationFloors {
    group("kernel") {
        packages.add("ai.speckitty.charter.kernel")
        floor = 75
    }
}
