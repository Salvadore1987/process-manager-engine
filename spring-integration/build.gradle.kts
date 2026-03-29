plugins {
    id("java-library")
}

dependencies {
    api(project(":core"))

    implementation(project(":rabbitmq-transport"))
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.actuator)
    implementation(libs.micrometer.registry.prometheus)

    compileOnly(libs.spring.boot.configuration.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
}
