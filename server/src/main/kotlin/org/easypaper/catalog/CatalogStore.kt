package org.easypaper.catalog

import org.easypaper.model.EasyPaperJson
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * One gzip'd JSON snapshot per source in [dir] ("fr-bdpm.json.gz", …). An import replaces a source's file
 * atomically, so a failed import leaves the previous snapshot, and loading one broken file never stops the others.
 */
class CatalogStore(val dir: File) {
    fun file(registry: String) = File(dir, "$registry.json.gz")

    fun write(snapshot: SourceSnapshot) {
        dir.mkdirs()
        // products keyed by their registry id: running the import twice never stores a product twice
        val unique = snapshot.products.associateBy { it.id }.values.toList()
        val tmp = File(dir, "${snapshot.info.registry}.json.gz.tmp")
        GZIPOutputStream(tmp.outputStream().buffered()).bufferedWriter().use { w ->
            w.write(EasyPaperJson.encodeToString(SourceSnapshot.serializer(), snapshot.copy(info = snapshot.info.copy(records = unique.size), products = unique)))
        }
        val target = file(snapshot.info.registry)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "Cannot replace ${target.path}" }
        }
    }

    /** Every snapshot in [dir], each loaded on its own: a source that fails is reported, not thrown. */
    fun loadAll(): List<Result<SourceSnapshot>> =
        (dir.listFiles { f -> f.name.endsWith(".json.gz") }?.sortedBy { it.name } ?: emptyList()).map { f ->
            runCatching {
                GZIPInputStream(f.inputStream().buffered()).bufferedReader().use { r ->
                    EasyPaperJson.decodeFromString(SourceSnapshot.serializer(), r.readText())
                }
            }.recoverCatching { e -> throw IllegalStateException("${f.name}: ${e.message}", e) }
        }

    companion object {
        fun fromEnv(): CatalogStore = CatalogStore(File(System.getenv("EASYPAPER_CATALOG_DIR") ?: "data/catalog"))
    }
}
