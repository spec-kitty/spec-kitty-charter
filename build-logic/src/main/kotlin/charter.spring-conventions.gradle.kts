// Modules that use Spring (inbound adapters and the service wiring). Versions come from the Spring Boot BOM.
plugins {
    id("charter.java-base")
}

dependencies {
    val bom = platform(catalog.lib("spring-boot-bom"))
    implementation(bom)
    testImplementation(bom)
    annotationProcessor(bom)
}
