plugins {
    id("java-library")
}

dependencies {
    api(project(":core"))

    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.autoconfigure)

    compileOnly(libs.spring.boot.configuration.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.security.test)
}
