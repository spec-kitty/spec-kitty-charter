plugins {
    id("charter.spring-conventions")
}

dependencies {
    api(project(":charter-api"))
    implementation(project(":charter-read-app"))
    implementation(catalog.lib("spring-boot-starter-webmvc"))
}
