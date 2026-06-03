plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

dependencies {
    implementation(project(":common"))
}

android {
    namespace = "io.github.tiefensuche"
    compileSdk = 36
    ndkVersion = "30.0.14904198"

    defaultConfig {
        minSdk = 16
        targetSdk = 36
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "io.github.tiefensuche"
            artifactId = "songrec-kt"
            version = project.findProperty("publishVersion")?.toString() ?: "1.0"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
