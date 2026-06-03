// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

val rustDir = file("songrec")

// ---------------------------------------------------------------------------
// Desktop helpers (evaluated at configuration time, safe to call anywhere)
// ---------------------------------------------------------------------------

fun hostOsArch(): Pair<String, String> {
    val os = System.getProperty("os.name", "").lowercase()
    val arch = System.getProperty("os.arch", "").lowercase()
    val osName = when {
        os.contains("win") -> "windows"
        os.contains("mac") -> "macos"
        else               -> "linux"
    }
    val archName = when {
        arch.contains("aarch64") || arch.contains("arm64")  -> "arm64"
        arch.contains("amd64")   || arch.contains("x86_64") -> "x64"
        else -> arch
    }
    return osName to archName
}

fun libFileName(): String {
    val os = System.getProperty("os.name", "").lowercase()
    return when {
        os.contains("win") -> "songrec.dll"
        os.contains("mac") -> "libsongrec.dylib"
        else               -> "libsongrec.so"
    }
}

// ---------------------------------------------------------------------------
// Helper: run a process, stream output, return exit code
// ---------------------------------------------------------------------------

fun runProcess(args: List<String>, dir: File, env: Map<String, String> = emptyMap()): Int {
    val logFile = file("build/${args.first()}-${System.currentTimeMillis()}.log")
    logFile.parentFile.mkdirs()

    val pb = ProcessBuilder(args).apply {
        directory(dir)
        redirectErrorStream(true)
        redirectOutput(logFile)
        environment()["CARGO_TERM_COLOR"] = "never"
        environment()["CARGO_TERM_PROGRESS_WHEN"] = "never"
        env.forEach { (k, v) -> environment()[k] = v }
    }.start()

    val reader = Thread { logFile.bufferedReader().forEachLine { println(it) } }
    reader.isDaemon = true
    reader.start()

    val exit = pb.waitFor()
    reader.join(5000)
    if (exit != 0) println("--- log: ${logFile.absolutePath} ---\n${logFile.readText()}")
    return exit
}

// ---------------------------------------------------------------------------
// Android – build via cargo-ndk
//   Outputs: app/src/main/jniLibs/<abi>/libsongrec.so
// ---------------------------------------------------------------------------

tasks.register("buildRustAndroid") {
    description = "Compile libsongrec for Android ABIs via cargo-ndk"
    group = "rust"

    // Declare inputs/outputs so Gradle skips this task when nothing has changed
    inputs.files(fileTree(rustDir) {
        include("src/**/*.rs", "Cargo.toml", "Cargo.lock")
    })
    outputs.files(
        "app/src/main/jniLibs/arm64-v8a/libsongrec.so",
        "app/src/main/jniLibs/armeabi-v7a/libsongrec.so",
        "app/src/main/jniLibs/x86_64/libsongrec.so"
    )

    doLast {
        val jniLibsDir = file("app/src/main/jniLibs")

        // Resolve NDK: env var > local.properties ndk.dir
        val ndkRoot: String = System.getenv("ANDROID_NDK_ROOT")
            ?: System.getenv("ANDROID_NDK_HOME")
            ?: run {
                val localProps = java.util.Properties().also { props ->
                    val f = file("local.properties")
                    if (f.exists()) f.inputStream().use { props.load(it) }
                }
                localProps.getProperty("ndk.dir")
                    ?: throw GradleException(
                        "Android NDK not found. Set ANDROID_NDK_ROOT or add ndk.dir to local.properties."
                    )
            }

        println("Using NDK: $ndkRoot")

        // Ensure Android Rust targets are installed
        listOf(
            "aarch64-linux-android",
            "armv7-linux-androideabi",
            "x86_64-linux-android"
        ).forEach { target ->
            runProcess(listOf("rustup", "target", "add", target), rustDir)
        }

        val exit = runProcess(
            listOf(
                "cargo", "ndk",
                "-t", "arm64-v8a",
                "-t", "armeabi-v7a",
                "-t", "x86_64",
                "-o", jniLibsDir.absolutePath,
                "build", "--release"
            ),
            rustDir,
            mapOf(
                "ANDROID_NDK_ROOT" to ndkRoot,
                "ANDROID_NDK_HOME" to ndkRoot
            )
        )

        if (exit != 0) throw GradleException("cargo-ndk failed (exit $exit)")
        println("✓ Android native libs in $jniLibsDir")
    }
}

// ---------------------------------------------------------------------------
// Desktop – build for the host platform
//   Output: desktop/src/main/resources/native/<os>-<arch>/<libsongrec.*>
// ---------------------------------------------------------------------------

val (desktopOs, desktopArch) = hostOsArch()
val desktopLibName = libFileName()
val desktopDestDir = file("desktop/src/main/resources/native/$desktopOs-$desktopArch")
val desktopLibOut  = desktopDestDir.resolve(desktopLibName)

tasks.register("buildRustDesktop") {
    description = "Compile libsongrec for the host platform"
    group = "rust"

    inputs.files(fileTree(rustDir) {
        include("src/**/*.rs", "Cargo.toml", "Cargo.lock")
    })
    outputs.file(desktopLibOut)

    doLast {
        val exit = runProcess(
            listOf("cargo", "build", "--release"),
            rustDir
        )
        if (exit != 0) throw GradleException("cargo build failed (exit $exit)")

        val libFile = rustDir.resolve("target/release/$desktopLibName")
        if (!libFile.exists()) throw GradleException("Expected output not found: $libFile")

        desktopDestDir.mkdirs()
        libFile.copyTo(desktopLibOut, overwrite = true)
        println("✓ Desktop native lib copied to $desktopDestDir")
    }
}

// ---------------------------------------------------------------------------
// Convenience aggregates
// ---------------------------------------------------------------------------

tasks.register("buildAll") {
    description = "Build Rust + Gradle for both Android and Desktop"
    group = "build"
    dependsOn("buildRustAndroid", "buildRustDesktop", ":app:build", ":desktop:build")
}

tasks.register("buildAndroid") {
    description = "Build Android library (includes Rust compile)"
    group = "build"
    dependsOn("buildRustAndroid", ":app:build")
}

tasks.register("buildDesktop") {
    description = "Build Desktop library (includes Rust compile)"
    group = "build"
    dependsOn("buildRustDesktop", ":desktop:build")
}

// Wire mustRunAfter at the top level, after all tasks are registered
gradle.projectsEvaluated {
    project(":app").tasks.named("build").configure { mustRunAfter(":buildRustAndroid") }
    project(":desktop").tasks.named("build").configure { mustRunAfter(":buildRustDesktop") }

    // mergeDebugJniLibFolders / mergeReleaseJniLibFolders consume the jniLibs output
    // of buildRustAndroid — declare an explicit dependency so Gradle knows the order.
    project(":app").tasks.matching { it.name.matches(Regex("merge.*JniLibFolders")) }.configureEach {
        dependsOn(":buildRustAndroid")
    }

    // processResources bundles the native lib copied by buildRustDesktop into the JAR.
    project(":desktop").tasks.named("processResources").configure {
        dependsOn(":buildRustDesktop")
    }
}
