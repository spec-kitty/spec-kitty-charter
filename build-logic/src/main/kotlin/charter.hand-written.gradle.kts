// Every hand-written Java module: the quality gates. Applied beside a base plugin (`charter.java-base` or one
// built on it) in each hand-written module, and never in charter-api, whose sources are generated (spec C-005).
// Aggregations select projects by `plugins.hasPlugin("charter.hand-written")`, never by a module list.
plugins {
    java
}

tasks.withType<JavaCompile>().configureEach {
    // Warnings are errors; processing and serial are off because no hand-written code has a processor or a
    // serialized form, and their warnings would only name generated or framework types.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial", "-Werror"))
}
