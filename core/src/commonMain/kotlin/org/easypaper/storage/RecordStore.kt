package org.easypaper.storage

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import org.easypaper.model.EasyPaperJson
import org.easypaper.model.PatientRecord

/** Raw bytes by key. Platform implementations: a file per key (JVM/Android), localStorage (web). */
interface BlobStore {
    suspend fun put(key: String, bytes: ByteArray)
    suspend fun get(key: String): ByteArray?
    suspend fun keys(): List<String>

    /** Removes the key; no error when it is already gone. */
    suspend fun delete(key: String)
}

/** New random AES-256 key (32 raw bytes). The platform keeps it in its keystore / secure storage. */
suspend fun generateRecordKey(): ByteArray =
    CryptographyProvider.Default.get(AES.GCM).keyGenerator(AES.Key.Size.B256).generateKey()
        .encodeToByteArray(AES.Key.Format.RAW)

/** AES-256-GCM cipher for a raw 32-byte key; encrypt() prepends a random IV, the tag is 128 bits. */
internal suspend fun aesGcm(key: ByteArray) =
    CryptographyProvider.Default.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key).cipher()

/**
 * Patient records encrypted at rest with AES-256-GCM (random IV per save, 128-bit tag).
 * The record id is bound as associated data, so a blob copied under another id does not decrypt.
 * Wrong key or damaged bytes throw on [load].
 */
class RecordStore(private val blobs: BlobStore, private val key: ByteArray) {
    init {
        require(key.size == 32) { "AES-256 key must be 32 bytes" }
    }

    suspend fun save(record: PatientRecord) {
        val id = record.request.id
        val json = EasyPaperJson.encodeToString(PatientRecord.serializer(), record)
        blobs.put(id, aesGcm(key).encrypt(json.encodeToByteArray(), id.encodeToByteArray()))
    }

    suspend fun load(id: String): PatientRecord? {
        val sealed = blobs.get(id) ?: return null
        val json = aesGcm(key).decrypt(sealed, id.encodeToByteArray()).decodeToString()
        return EasyPaperJson.decodeFromString(PatientRecord.serializer(), json)
    }

    suspend fun list(): List<String> = blobs.keys()

    /** Erases the record, e.g. after the visit or when the patient asks; no error when it is already gone. */
    suspend fun delete(id: String) = blobs.delete(id)
}
