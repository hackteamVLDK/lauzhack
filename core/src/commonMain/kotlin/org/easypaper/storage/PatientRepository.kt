package org.easypaper.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.easypaper.diary.EntryProblem
import org.easypaper.diary.check
import org.easypaper.model.CareRequest
import org.easypaper.model.DiaryNote
import org.easypaper.model.Entry
import org.easypaper.model.Food
import org.easypaper.model.PatientRecord
import org.easypaper.report.stripJpegMetadata

private const val PHOTO_PREFIX = "photo/"

/**
 * Photos of entries, encrypted with the record key in the same [BlobStore] as the records, under
 * "photo/<entryId>" — that string is the entry's photoRef. Metadata (GPS, phone model) is stripped before saving.
 * Entry ids must be unique on the device (the app uses UUIDs); an existing photo is never overwritten.
 */
class PhotoStore(private val blobs: BlobStore, private val key: ByteArray) {
    init {
        require(key.size == 32) { "AES-256 key must be 32 bytes" }
    }

    fun refFor(entryId: String) = PHOTO_PREFIX + entryId

    /** Saves a JPEG for [entryId] and returns its photoRef. */
    suspend fun put(entryId: String, jpeg: ByteArray): String {
        require(jpeg.size > 2 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte()) { "Photo must be a JPEG" }
        val ref = refFor(entryId)
        require(blobs.get(ref) == null) { "A photo for entry $entryId already exists" }
        blobs.put(ref, aesGcm(key).encrypt(stripJpegMetadata(jpeg), ref.encodeToByteArray()))
        return ref
    }

    // A ref from elsewhere (the demo's "demo-photo-1", a doctor's report) is no photo on this device: null / no-op.
    // The prefix check also keeps a crafted ref like "record/<id>" from reading or deleting a record.
    suspend fun get(ref: String): ByteArray? {
        if (!ref.startsWith(PHOTO_PREFIX)) return null
        return blobs.get(ref)?.let { aesGcm(key).decrypt(it, ref.encodeToByteArray()) }
    }

    suspend fun delete(ref: String) {
        if (ref.startsWith(PHOTO_PREFIX)) blobs.delete(ref)
    }
}

private val Entry.photoRef: String?
    get() = when (val p = payload) { is Food -> p.photoRef; is DiaryNote -> p.photoRef; else -> null }

/**
 * What the patient UI talks to: every request on this device with its entries as a [StateFlow], updated after each
 * change. Entries are checked against the request before they are saved; nothing invalid reaches the disk.
 */
class PatientRepository(private val store: RecordStore, private val photos: PhotoStore) {
    private val mutex = Mutex()
    private val state = MutableStateFlow<List<PatientRecord>>(emptyList())
    val records: StateFlow<List<PatientRecord>> = state.asStateFlow()

    /** Loads everything stored on the device; call once when the app starts. */
    suspend fun refresh() = mutex.withLock { reload() }

    /** Starts filling [request] (from the doctor's invite); keeps existing entries if it was started before. */
    suspend fun start(request: CareRequest) = mutex.withLock {
        if (store.load(request.id) == null) store.save(PatientRecord(request))
        reload()
    }

    /**
     * Checks and saves [entry]. With [photo] (JPEG) the photo is stored and its ref set on the Food / DiaryNote
     * payload. Returns the input problems; empty = saved.
     */
    suspend fun add(requestId: String, entry: Entry, photo: ByteArray? = null): List<EntryProblem> = mutex.withLock {
        val record = requireNotNull(store.load(requestId)) { "No request $requestId on this device" }
        require(record.entries.none { it.id == entry.id }) { "Entry ${entry.id} already exists" }
        val withPhoto = if (photo == null) entry else entry.copy(
            payload = when (val p = entry.payload) {
                is Food -> p.copy(photoRef = photos.refFor(entry.id))
                is DiaryNote -> p.copy(photoRef = photos.refFor(entry.id))
                else -> throw IllegalArgumentException("Only food and diary entries carry a photo")
            },
        )
        val problems = record.request.check(withPhoto)
        if (problems.isNotEmpty()) return problems
        if (photo != null) photos.put(entry.id, photo)
        store.save(record.copy(entries = record.entries + withPhoto))
        reload()
        emptyList()
    }

    /** Removes one entry (and its photo), e.g. a mistake the patient spotted. */
    suspend fun removeEntry(requestId: String, entryId: String) = mutex.withLock {
        val record = store.load(requestId) ?: return@withLock
        val entry = record.entries.firstOrNull { it.id == entryId } ?: return@withLock
        store.save(record.copy(entries = record.entries - entry))
        entry.photoRef?.let { photos.delete(it) }
        reload()
    }

    suspend fun photo(ref: String): ByteArray? = photos.get(ref)

    /** Erases the request, all its entries and photos from the device. */
    suspend fun delete(requestId: String) = mutex.withLock {
        store.load(requestId)?.entries?.mapNotNull { it.photoRef }?.forEach { photos.delete(it) }
        store.delete(requestId)
        reload()
    }

    private suspend fun reload() {
        state.value = store.list().sorted().mapNotNull { store.load(it) }
    }
}
