plugins {
    id("java")
}

group = "uz.salvadore"
version = "1.0-SNAPSHOT"

subprojects {
    apply(plugin = "java")

    group = rootProject.group
    version = rootProject.version

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        testImplementation(platform(rootProject.libs.junit.bom))
        testImplementation(rootProject.libs.junit.jupiter)
        testImplementation(rootProject.libs.assertj.core)
        testRuntimeOnly(rootProject.libs.junit.platform.launcher)
    }

    tasks.test {
        useJUnitPlatform()
    }
}
