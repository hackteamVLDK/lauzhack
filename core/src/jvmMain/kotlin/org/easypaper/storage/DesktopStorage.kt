package org.easypaper.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission

/** Where the desktop app keeps the patient's data: ~/.easypaper (override for tests). */
fun defaultDesktopDir() = File(System.getProperty("user.home"), ".easypaper")

/**
 * The record key of this desktop: 32 random bytes in [file], readable and writable only by the current user
 * (POSIX 600, or an ACL with the owner only on Windows). Created on first start.
 * shortcut: the key file sits next to the data and protects against other users and copied data folders, not
 * against this user's own malware; an OS keychain (Keychain / DPAPI / Secret Service) would be the next step.
 */
suspend fun loadOrCreateDesktopKey(file: File): ByteArray = withContext(Dispatchers.IO) {
    if (file.exists()) {
        return@withContext file.readBytes().also { require(it.size == 32) { "Damaged key file $file" } }
    }
    file.parentFile?.mkdirs()
    val key = generateRecordKey()
    val tmp = File.createTempFile("key", ".tmp", file.parentFile)
    try {
        ownerOnly(tmp)
        tmp.writeBytes(key)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } finally {
        tmp.delete()
    }
    key
}

private fun ownerOnly(file: File) {
    val path = file.toPath()
    val posix = Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
    if (posix != null) {
        posix.setPermissions(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        return
    }
    val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java) ?: return
    val owner = Files.getOwner(path)
    acl.acl = listOf(
        AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
            .setPermissions(AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA, AclEntryPermission.APPEND_DATA,
                AclEntryPermission.READ_ATTRIBUTES, AclEntryPermission.WRITE_ATTRIBUTES, AclEntryPermission.READ_ACL,
                AclEntryPermission.WRITE_ACL, AclEntryPermission.DELETE, AclEntryPermission.SYNCHRONIZE)
            .build(),
    )
}

/** Patient data of the desktop app in [dir]: records and photos encrypted with the desktop's key. */
suspend fun desktopPatientRepository(dir: File = defaultDesktopDir()): PatientRepository {
    val key = loadOrCreateDesktopKey(File(dir, "record.key"))
    val blobs = FileBlobStore(File(dir, "data"))
    return PatientRepository(RecordStore(blobs, key), PhotoStore(blobs, key))
}
