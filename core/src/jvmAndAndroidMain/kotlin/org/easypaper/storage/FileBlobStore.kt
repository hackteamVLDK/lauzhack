package org.easypaper.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * [BlobStore] on disk for Desktop and Android: one file per key in [dir] (file name = base64url of the key).
 * Writes go to a temp file that is synced and renamed over the target, so a crash never leaves half a record.
 * Only java.io is used: Android minSdk 24 has no java.nio.file.
 */
@OptIn(ExperimentalEncodingApi::class)
class FileBlobStore(private val dir: File) : BlobStore {
    private val names = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    init {
        dir.mkdirs()
        require(dir.isDirectory) { "Not a directory: $dir" }
    }

    private fun file(key: String) = File(dir, names.encode(key.encodeToByteArray()) + SUFFIX)

    override suspend fun put(key: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val target = file(key)
        val tmp = File.createTempFile("blob", ".tmp", dir)
        try {
            FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
            // POSIX rename replaces atomically; Windows refuses to replace, so the old file goes first there
            if (!tmp.renameTo(target)) {
                target.delete()
                check(tmp.renameTo(target)) { "Cannot write $target" }
            }
        } finally {
            tmp.delete()
        }
    }

    override suspend fun get(key: String): ByteArray? = withContext(Dispatchers.IO) {
        file(key).takeIf { it.isFile }?.readBytes()
    }

    override suspend fun keys(): List<String> = withContext(Dispatchers.IO) {
        dir.listFiles().orEmpty().map { it.name }.filter { it.endsWith(SUFFIX) }
            .map { names.decode(it.removeSuffix(SUFFIX)).decodeToString() }
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) { file(key).delete() }
    }

    private companion object {
        const val SUFFIX = ".bin"
    }
}
