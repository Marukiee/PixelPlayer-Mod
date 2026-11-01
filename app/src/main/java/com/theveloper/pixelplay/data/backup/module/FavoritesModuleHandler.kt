package com.theveloper.pixelplay.data.backup.module

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.database.FavoritesEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SongSummary
import com.theveloper.pixelplay.di.BackupGson
import com.theveloper.pixelplay.data.worker.SyncManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Backs up favorite songs. Historically favorites were stored (and exported) as raw MediaStore
 * `_ID` values, which are device- and scan-specific — so a backup restored on another device, or
 * after a re-scan / small metadata edit, matched nothing and silently lost every like.
 *
 * This handler now exports song identity (title/artist/album/duration + file name) alongside each
 * favorite, and on restore resolves those back to the current library's IDs — file name first
 * (survives tag edits and re-scans as long as the file keeps its name, e.g. via Syncthing), then
 * title+artist with album/duration disambiguation. Favorites whose song isn't scanned yet are
 * parked in a pending file and retried when the next library sync finishes.
 *
 * Old raw-ID backups (a bare JSON array) still restore as-is for same-device use.
 */
@Singleton
class FavoritesModuleHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val favoritesDao: FavoritesDao,
    private val musicDao: MusicDao,
    @BackupGson private val gson: Gson,
    private val syncManagerProvider: Provider<SyncManager>
) : BackupModuleHandler {

    private val handlerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        resolvePendingFavorites()
        observeSyncProgress()
    }

    override val section = BackupSection.FAVORITES

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        val favorites = favoritesDao.getAllFavoritesOnce()
        val summaryById = musicDao.getAllLocalSongSummaries().associateBy { it.id }
        val entries = favorites.map { fav ->
            val summary = summaryById[fav.songId]
            FavoriteEntry(
                songId = fav.songId,
                isFavorite = fav.isFavorite,
                timestamp = fav.timestamp,
                title = summary?.title,
                artist = summary?.artistName,
                album = summary?.albumName,
                duration = summary?.duration ?: 0L,
                fileName = summary?.filePath?.fileName()
            )
        }
        gson.toJson(FavoritesBackupPayload(favorites = entries))
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        favoritesDao.getAllFavoritesOnce().size
    }

    override suspend fun snapshot(): String = withContext(Dispatchers.IO) {
        // Snapshot for rollback keeps the raw current rows (same-device restore).
        gson.toJson(favoritesDao.getAllFavoritesOnce())
    }

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val element = runCatching { JsonParser.parseString(payload) }.getOrNull()

        // Legacy format: a bare JSON array of FavoritesEntity (raw IDs, no metadata).
        if (element != null && element.isJsonArray) {
            val type = TypeToken.getParameterized(List::class.java, FavoritesEntity::class.java).type
            val favorites: List<FavoritesEntity> = gson.fromJson(payload, type)
            favoritesDao.replaceAll(favorites)
            return@withContext
        }

        val parsed = runCatching {
            gson.fromJson(payload, FavoritesBackupPayload::class.java)
        }.getOrNull() ?: FavoritesBackupPayload()
        val entries = parsed.favorites.orEmpty()

        val result = resolveFavorites(entries)
        favoritesDao.replaceAll(result.resolved)

        val pendingFile = pendingFile()
        if (result.unresolvedCount > 0) {
            runCatching { pendingFile.writeText(payload) }
                .onFailure { Log.e(TAG, "Failed to save pending favorites restore", it) }
            Log.i(TAG, "Favorites restore: ${result.resolved.size} resolved, ${result.unresolvedCount} pending scan")
        } else if (pendingFile.exists()) {
            pendingFile.delete()
        }
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)

    // ---- Pending resolution (songs not yet scanned when the restore ran) ----

    private fun observeSyncProgress() {
        handlerScope.launch {
            val syncManager = syncManagerProvider.get()
            var wasRunning = false
            syncManager.syncProgress.collect { progress ->
                val isRunning = progress.isRunning
                if (wasRunning && !isRunning) resolvePendingFavorites()
                wasRunning = isRunning
            }
        }
    }

    private fun resolvePendingFavorites() {
        handlerScope.launch {
            val pendingFile = pendingFile()
            if (!pendingFile.exists()) return@launch

            val payload = runCatching { pendingFile.readText() }.getOrNull() ?: return@launch
            val parsed = runCatching {
                gson.fromJson(payload, FavoritesBackupPayload::class.java)
            }.getOrNull() ?: return@launch
            val entries = parsed.favorites.orEmpty()
            if (entries.isEmpty()) { pendingFile.delete(); return@launch }

            val result = resolveFavorites(entries)
            // Merge newly resolved favorites with whatever is already marked, don't wipe.
            val existing = favoritesDao.getAllFavoritesOnce().associateBy { it.songId }.toMutableMap()
            result.resolved.forEach { existing[it.songId] = it }
            favoritesDao.replaceAll(existing.values.toList())

            if (result.unresolvedCount == 0) {
                pendingFile.delete()
                Log.i(TAG, "All pending favorites resolved.")
            } else {
                Log.i(TAG, "Favorites still pending: ${result.unresolvedCount}")
            }
        }
    }

    // ---- Matching ----

    private suspend fun resolveFavorites(entries: List<FavoriteEntry>): FavoritesResolution {
        val summaries = musicDao.getAllLocalSongSummaries()
        val byId = summaries.associateBy { it.id }
        val byFileName = mutableMapOf<String, MutableList<SongSummary>>()
        val byTitleArtist = mutableMapOf<String, MutableList<SongSummary>>()
        summaries.forEach { s ->
            s.filePath.fileName()?.let { fn ->
                byFileName.getOrPut(normalize(fn)) { mutableListOf() }.add(s)
            }
            byTitleArtist.getOrPut(matchKey(s.title, s.artistName)) { mutableListOf() }.add(s)
        }

        val resolved = mutableListOf<FavoritesEntity>()
        var unresolved = 0
        val seen = mutableSetOf<Long>()

        entries.forEach { entry ->
            val newId = resolveOne(entry, byId, byFileName, byTitleArtist)
            if (newId == null) {
                unresolved++
            } else if (seen.add(newId)) {
                resolved.add(
                    FavoritesEntity(
                        songId = newId,
                        isFavorite = entry.isFavorite,
                        timestamp = entry.timestamp
                    )
                )
            }
        }
        return FavoritesResolution(resolved, unresolved)
    }

    private fun resolveOne(
        entry: FavoriteEntry,
        byId: Map<Long, SongSummary>,
        byFileName: Map<String, List<SongSummary>>,
        byTitleArtist: Map<String, List<SongSummary>>
    ): Long? {
        val hasMeta = entry.title != null || entry.fileName != null

        // 1. Direct ID match — verify with metadata when we have it (guards reused MediaStore IDs).
        byId[entry.songId]?.let { direct ->
            if (!hasMeta || metadataMatches(entry, direct)) return entry.songId
            // else: ID now points at a different song → keep matching by metadata.
        }
        if (!hasMeta) return byId[entry.songId]?.let { entry.songId }

        // 2. File name — strongest across re-scans / tag edits (Syncthing keeps names).
        entry.fileName?.let { fn ->
            val cands = byFileName[normalize(fn)]
            if (cands != null) {
                if (cands.size == 1) return cands[0].id
                pickBest(entry, cands)?.let { return it }
            }
        }

        // 3. Title + artist, disambiguate with album then duration.
        entry.title?.let { title ->
            val cands = byTitleArtist[matchKey(title, entry.artist ?: "")]
            if (cands != null) {
                if (cands.size == 1) return cands[0].id
                pickBest(entry, cands)?.let { return it }
            }
        }
        return null
    }

    private fun pickBest(entry: FavoriteEntry, candidates: List<SongSummary>): Long? {
        val album = candidates.filter { normalize(it.albumName) == normalize(entry.album ?: "") }
        if (album.size == 1) return album[0].id
        val pool = album.ifEmpty { candidates }
        val byDur = pool.filter { kotlin.math.abs(it.duration - entry.duration) <= DURATION_TOLERANCE_MS }
        if (byDur.size == 1) return byDur[0].id
        return null
    }

    private fun metadataMatches(entry: FavoriteEntry, song: SongSummary): Boolean {
        entry.fileName?.let { fn ->
            song.filePath.fileName()?.let { return normalize(fn) == normalize(it) }
        }
        return entry.title != null &&
            matchKey(entry.title, entry.artist ?: "") == matchKey(song.title, song.artistName)
    }

    private fun matchKey(title: String, artist: String) = "${normalize(title)}|${normalize(artist)}"
    private fun normalize(text: String) = text.trim().lowercase()
    private fun String.fileName(): String? = substringAfterLast('/').ifBlank { null }
    private fun pendingFile() = File(context.filesDir, PENDING_FILE)

    // ---- Data classes ----

    data class FavoriteEntry(
        val songId: Long,
        val isFavorite: Boolean = true,
        val timestamp: Long = System.currentTimeMillis(),
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val duration: Long = 0L,
        val fileName: String? = null
    )

    data class FavoritesBackupPayload(
        val favorites: List<FavoriteEntry>? = null
    )

    private data class FavoritesResolution(
        val resolved: List<FavoritesEntity>,
        val unresolvedCount: Int
    )

    companion object {
        private const val TAG = "FavoritesModuleHandler"
        private const val DURATION_TOLERANCE_MS = 2000L
        private const val PENDING_FILE = "pending_favorites_restore.json"
    }
}
