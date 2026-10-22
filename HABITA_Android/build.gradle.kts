import java.security.MessageDigest

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

// Empaqueta el MISMO feed del equipo sin duplicar 12 MB de datos en Git.
val bundledFeed = layout.buildDirectory.file("generated/habitaAssets/gtfs/feed.zip")

tasks.register("verifyReferenceCatalog") {
    val catalog = file("app/src/main/assets/reference/catalog.json")
    val checksum = file("app/src/main/assets/reference/catalog.sha256")
    inputs.files(catalog, checksum)
    doLast {
        val digest = MessageDigest.getInstance("SHA-256").digest(catalog.readBytes()).joinToString("") { "%02x".format(it) }
        require(digest == checksum.readText().trim()) { "El catálogo del entorno no coincide con su SHA-256. Regenera con tools/prepare_reference.py." }
    }
}
tasks.register("prepareBundledGtfs") {
    val source = file("../HABITA_GTFS_Movilidad_v1.2.1_GUI/data")
    val parts = (1..9).map { source.resolve("GTFS_20260829.zip.part%02d".format(it)) }
    inputs.files(parts)
    inputs.file(source.resolve("manifest.json"))
    outputs.file(bundledFeed)
    doLast {
        require(parts.all { it.isFile }) { "Conserva el repositorio completo: faltan las partes del feed GTFS en $source" }
        val destination = bundledFeed.get().asFile
        destination.parentFile.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        destination.outputStream().buffered().use { out ->
            parts.forEach { part -> part.inputStream().buffered().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    out.write(buffer, 0, count)
                }
            } }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        require(hash == "8c31a7f68676f9a1cf2b3d86a40c8bea7adbd1f7478ac57d57eb826f6de21e77") { "El feed GTFS está incompleto o fue alterado." }
        require(destination.length() == 12420603L) { "Tamaño GTFS incorrecto." }
    }
}
