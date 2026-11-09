// The kernel: shared Pack/DRG vocabulary (kernel.domain) and the validation guard library (kernel.validation).
// Pure Java by construction: charter.domain-conventions rejects any external dependency, so no Spring, SnakeYAML
// or networknt can enter. jspecify and the test stack arrive through charter.hand-written / charter.java-base.
plugins {
    id("charter.domain-conventions")
    id("charter.hand-written")
}

// The nightly mutation floor (#239): one group for the whole module; raised by PR once the score clears it by 5.
mutationFloors {
    group("kernel") {
        packages.add("ai.speckitty.charter.kernel")
        floor = 65
    }
}
