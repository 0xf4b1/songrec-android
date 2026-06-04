plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
}

group = "io.github.tiefensuche"
version = project.findProperty("publishVersion")?.toString() ?: "1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

publishing {
    publications {
        create<MavenPublication>("common") {
            groupId = "io.github.tiefensuche"
            artifactId = "songrec-common"
            version = project.findProperty("publishVersion")?.toString() ?: "1.0"

            from(components["java"])
        }
    }
}
