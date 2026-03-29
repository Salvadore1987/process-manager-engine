plugins {
    id("java")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":spring-integration"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.actuator)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.testcontainers.rabbitmq)
}
