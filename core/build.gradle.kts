plugins {
    id("java-library")
}

dependencies {
    api(libs.jackson.databind)
    api(libs.jackson.datatype.jsr310)
    api(libs.jaxb.api)
    api(libs.slf4j.api)
    api(libs.micrometer.core)

    implementation(libs.jaxb.runtime)

    testRuntimeOnly(libs.slf4j.simple)
}
