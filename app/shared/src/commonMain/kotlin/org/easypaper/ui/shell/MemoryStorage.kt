package org.easypaper.ui.shell

import org.easypaper.storage.BlobStore
import org.easypaper.storage.PatientRepository
import org.easypaper.storage.PhotoStore
import org.easypaper.storage.RecordStore
import kotlin.random.Random

/** Blobs in memory: until the device storage of task 27 is wired in, and in tests. Lost when the app closes. */
class MemoryBlobStore : BlobStore {
    private val map = LinkedHashMap<String, ByteArray>()
    override suspend fun put(key: String, bytes: ByteArray) { map[key] = bytes }
    override suspend fun get(key: String): ByteArray? = map[key]
    override suspend fun keys(): List<String> = map.keys.toList()
    override suspend fun delete(key: String) { map.remove(key) }
}

/**
 * A [PatientRepository] that keeps everything in memory. The key never leaves the process and nothing is
 * written to disk, so a non-cryptographic random key is enough here.
 */
fun inMemoryPatientRepository(): PatientRepository {
    val blobs = MemoryBlobStore()
    val key = Random.Default.nextBytes(32)
    return PatientRepository(RecordStore(blobs, key), PhotoStore(blobs, key))
}
