package io.github.tiefensuche

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Platform-independent native library loader for SongRec.
 * Handles loading the Rust JNI library on both Android and desktop platforms.
 */
object SongRecLibLoader {
    private var loaded = false
    private val lock = Any()

    fun load() {
        synchronized(lock) {
            if (loaded) return

            try {
                when {
                    isAndroid() -> loadAndroid()
                    else -> loadDesktop()
                }
                loaded = true
            } catch (e: Exception) {
                throw RuntimeException("Failed to load SongRec native library", e)
            }
        }
    }

    private fun isAndroid(): Boolean {
        return try {
            Class.forName("android.os.Build")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun loadAndroid() {
        // On Android, the native library is packaged in jniLibs
        System.loadLibrary("songrec")
    }

    private fun loadDesktop() {
        val os = System.getProperty("os.name", "").lowercase()
        val arch = System.getProperty("os.arch", "").lowercase()

        val libName = when {
            os.contains("win") -> "songrec.dll"
            os.contains("mac") -> "libsongrec.dylib"
            else -> "libsongrec.so"
        }

        val libPath = findLibrary(libName, arch)
        System.load(libPath)
    }

    private fun findLibrary(libName: String, arch: String): String {
        // Try to load from resources first (for packaged applications)
        val resourcePath = "/native/$arch/$libName"
        val resourceUrl = this::class.java.getResource(resourcePath)

        if (resourceUrl != null) {
            return extractLibraryFromResources(resourceUrl, libName)
        }

        // Try system library paths
        val systemPaths = mutableListOf<String>()
        systemPaths.add(".")
        systemPaths.add("./lib")
        systemPaths.add("./native/$arch")
        systemPaths.addAll(
            System.getProperty("java.library.path", "")
                .split(File.pathSeparator)
                .filter { it.isNotEmpty() }
        )

        for (path in systemPaths) {
            val file = File(path, libName)
            if (file.exists()) {
                return file.absolutePath
            }
        }

        throw RuntimeException(
            "Failed to find SongRec native library: $libName\n" +
            "Searched paths: ${systemPaths.joinToString(", ")}"
        )
    }

    private fun extractLibraryFromResources(resourceUrl: java.net.URL, libName: String): String {
        val tempDir = Files.createTempDirectory("songrec-")
        val tempFile = tempDir.resolve(libName).toFile()
        tempFile.deleteOnExit()

        resourceUrl.openStream().use { input ->
            Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        return tempFile.absolutePath
    }
}
