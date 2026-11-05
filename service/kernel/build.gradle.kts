// The kernel: shared Pack/DRG vocabulary (kernel.domain), the validation guard library (kernel.validation) and the
// markdown content form's line rules (kernel.markdown, #299).
// Pure Java by construction: charter.domain-conventions rejects any external dependency, so no Spring, SnakeYAML
// or networknt can enter. jspecify and the test stack arrive through charter.hand-written / charter.java-base.
plugins {
    id("charter.domain-conventions")
    id("charter.hand-written")
}
