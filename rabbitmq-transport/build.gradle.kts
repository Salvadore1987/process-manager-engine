plugins {
    id("java-library")
}

dependencies {
    api(project(":core"))

    implementation(libs.rabbitmq.client)
    implementation(libs.slf4j.api)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.testcontainers.rabbitmq)
    testRuntimeOnly(libs.slf4j.simple)
}
