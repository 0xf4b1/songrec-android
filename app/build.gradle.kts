plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "io.github.tiefensuche"
    compileSdk = 34

    defaultConfig {
        minSdk = 16
        targetSdk = 34
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
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.tiefensuche"
            artifactId = "songrec-android"
            version = "0.1"

            afterEvaluate {
                from(components["release"])
            }
        }
    }

    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/0xf4b1/songrec-android")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}