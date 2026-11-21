package org.easypaper.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.easypaper.diary.Diary
import org.easypaper.diary.EntryProblem
import org.easypaper.diary.check
import org.easypaper.diary.migrateToDiary
import org.easypaper.model.CareRequest
import org.easypaper.model.DiaryNote
import org.easypaper.model.EasyPaperJson
import org.easypaper.model.Entry
import org.easypaper.model.Food

private const val DIARY_KEY = "diary"

/**
 * The person's diary on this device (patient app v2, #47): every record and the doctors' plans, encrypted with
 * the record key in the same [BlobStore] as the per-request records and photos. On the first open the per-request
 * records are copied in once (each request becomes a plan); they stay where they are until the app has switched
 * to this repository, so nothing the current screens show is lost.
 */
class DiaryRepository(private val blobs: BlobStore, private val key: ByteArray, private val photos: PhotoStore = PhotoStore(blobs, key)) {
    init {
        require(key.size == 32) { "AES-256 key must be 32 bytes" }
    }

    private val mutex = Mutex()
    private val state = MutableStateFlow(Diary())
    val diary: StateFlow<Diary> = state.asStateFlow()

    /** Loads the diary; the first time, it is made from the per-request records already on the device. */
    suspend fun refresh() = mutex.withLock {
        state.value = load() ?: RecordStore(blobs, key).let { old -> migrateToDiary(old.list().sorted().mapNotNull { old.load(it) }) }
            .also { save(it) }
    }

    /** Checks and saves a record (own or for a plan); with [photo] (JPEG) the photo is stored and linked. */
    suspend fun add(entry: Entry, photo: ByteArray? = null): List<EntryProblem> = mutex.withLock {
        val current = state.value
        require(current.entries.none { it.id == entry.id }) { "Entry ${entry.id} already exists" }
        val linked = if (photo == null) entry else entry.copy(
            payload = when (val p = entry.payload) {
                is Food -> p.copy(photoRef = photos.refFor(entry.id))
                is DiaryNote -> p.copy(photoRef = photos.refFor(entry.id))
                else -> throw IllegalArgumentException("Only food and diary records carry a photo")
            },
        )
        val problems = current.check(linked)
        if (problems.isNotEmpty()) return problems
        if (photo != null) photos.put(entry.id, photo)
        commit(current.copy(entries = current.entries + linked))
        emptyList()
    }

    /** Removes one record and its photo. */
    suspend fun remove(entryId: String) = mutex.withLock {
        val current = state.value
        val entry = current.entries.firstOrNull { it.id == entryId } ?: return@withLock
        commit(current.copy(entries = current.entries - entry))
        entry.photoRef()?.let { photos.delete(it) }
    }

    /** Adds a doctor's request as a plan (from an invite); an existing plan with the same id is kept. */
    suspend fun addPlan(plan: CareRequest) = mutex.withLock {
        val current = state.value
        if (current.plans.none { it.id == plan.id }) commit(current.copy(plans = current.plans + plan))
    }

    /** Ends a plan; the person's records stay (they belong to the person, not to the request). */
    suspend fun removePlan(planId: String) = mutex.withLock {
        val current = state.value
        commit(current.copy(plans = current.plans.filterNot { it.id == planId }))
    }

    suspend fun photo(ref: String): ByteArray? = photos.get(ref)

    /** Erases everything: every record, photo and plan of the diary. */
    suspend fun deleteAll() = mutex.withLock {
        state.value.entries.mapNotNull { it.photoRef() }.forEach { photos.delete(it) }
        commit(Diary())
    }

    private suspend fun commit(diary: Diary) {
        save(diary)
        state.value = diary
    }

    private suspend fun save(diary: Diary) {
        val json = EasyPaperJson.encodeToString(Diary.serializer(), diary)
        blobs.put(DIARY_KEY, aesGcm(key).encrypt(json.encodeToByteArray(), DIARY_KEY.encodeToByteArray()))
    }

    private suspend fun load(): Diary? {
        val sealed = blobs.get(DIARY_KEY) ?: return null
        return EasyPaperJson.decodeFromString(Diary.serializer(), aesGcm(key).decrypt(sealed, DIARY_KEY.encodeToByteArray()).decodeToString())
    }
}

private fun Entry.photoRef(): String? = when (val p = payload) { is Food -> p.photoRef; is DiaryNote -> p.photoRef; else -> null }
