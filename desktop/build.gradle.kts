plugins {
    alias(libs.plugins.kotlin.jvm)
    application
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

dependencies {
    implementation(project(":common"))
}

application {
    mainClass.set("io.github.tiefensuche.DesktopAppKt")
}

// Native libs copied by buildRustDesktop land here and get bundled into the JAR.
// SongRecLibLoader extracts them at runtime from /native/<os-arch>/.
sourceSets {
    main {
        resources {
            srcDir("src/main/resources")
        }
    }
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.withType<JavaExec> {
    systemProperty("java.library.path", sourceSets.main.get().output.resourcesDir?.absolutePath ?: ".")
}

publishing {
    publications {
        create<MavenPublication>("desktop") {
            groupId = "io.github.tiefensuche"
            artifactId = "songrec-kt"
            version = project.findProperty("publishVersion")?.toString() ?: "1.0"

            from(components["java"])
        }
    }
}
