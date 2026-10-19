plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.gradle.plugin.spring.boot)
    implementation(libs.gradle.plugin.openapi.generator)
    implementation(libs.gradle.plugin.spotless)
    implementation(libs.gradle.plugin.errorprone)
    implementation(libs.gradle.plugin.pitest)
}
