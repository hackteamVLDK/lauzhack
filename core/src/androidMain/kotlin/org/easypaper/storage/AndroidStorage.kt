package org.easypaper.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Protects the record key at rest; on a phone that is [AndroidKeystoreWrapper]. */
interface KeyWrapper {
    fun wrap(key: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
}

/**
 * Wraps the record key with an AES-256-GCM key that lives in Android Keystore (hardware-backed where the phone has
 * it) and can never be exported. The wrapped key file alone, e.g. from a backup or a copied data folder, is useless.
 * Format: IV (12 bytes) | ciphertext with tag.
 */
class AndroidKeystoreWrapper(private val alias: String = "easypaper-record-key") : KeyWrapper {
    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun wrap(key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        return cipher.iv + cipher.doFinal(key) // Keystore picks the IV itself
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        require(wrapped.size > 12) { "Damaged wrapped key" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, wrapped, 0, 12))
        return cipher.doFinal(wrapped, 12, wrapped.size - 12)
    }
}

/** The record key: unwrapped from [file], or created, wrapped and written there on first start. */
suspend fun loadOrCreateWrappedKey(file: File, wrapper: KeyWrapper): ByteArray = withContext(Dispatchers.IO) {
    if (file.exists()) {
        return@withContext wrapper.unwrap(file.readBytes()).also { require(it.size == 32) { "Damaged key file $file" } }
    }
    file.parentFile?.mkdirs()
    val key = generateRecordKey()
    val tmp = File.createTempFile("key", ".tmp", file.parentFile)
    try {
        FileOutputStream(tmp).use { it.write(wrapper.wrap(key)); it.fd.sync() }
        check(tmp.renameTo(file)) { "Cannot write $file" }
    } finally {
        tmp.delete()
    }
    key
}

/** Patient data of the Android app in the app's private files: records and photos, key protected by Keystore. */
suspend fun androidPatientRepository(dir: File, wrapper: KeyWrapper = AndroidKeystoreWrapper()): PatientRepository {
    val key = loadOrCreateWrappedKey(File(dir, "record.key.wrapped"), wrapper)
    val blobs = FileBlobStore(File(dir, "data"))
    return PatientRepository(RecordStore(blobs, key), PhotoStore(blobs, key))
}

/** Same, in Context.filesDir/easypaper (private to the app, removed with it). */
suspend fun androidPatientRepository(context: Context): PatientRepository =
    androidPatientRepository(File(context.filesDir, "easypaper"))
