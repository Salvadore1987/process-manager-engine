plugins {
    id("java-library")
}

dependencies {
    api(project(":core"))

    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.slf4j.api)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit5)
    testRuntimeOnly(libs.slf4j.simple)
}
