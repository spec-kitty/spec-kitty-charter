plugins {
    id("charter.spring-conventions")
    id("charter.hand-written")
}

dependencies {
    api(project(":charter-api"))
    implementation(project(":charter-read-app"))
    implementation(catalog.lib("spring-boot-starter-webmvc"))
}
