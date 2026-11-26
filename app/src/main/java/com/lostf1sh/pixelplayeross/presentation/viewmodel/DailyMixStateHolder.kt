package com.lostf1sh.pixelplayeross.presentation.viewmodel

import com.lostf1sh.pixelplayeross.data.model.preferLocalCopies
import com.lostf1sh.pixelplayeross.data.DailyMixManager
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.navidrome.NavidromeMixSync
import com.lostf1sh.pixelplayeross.data.navidrome.SharedMix
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.repository.MusicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Daily Mix and Your Mix state.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - Generate and update daily/your mixes
 * - Persist and restore mix state
 * - Check if mix needs updating based on day change
 */
@Singleton
class DailyMixStateHolder @Inject constructor(
    private val dailyMixManager: DailyMixManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val mixSync: NavidromeMixSync
) {
    private var scope: CoroutineScope? = null
    private var updateJob: Job? = null
    private var checkJob: Job? = null
    private var mixWriteJob: Job? = null

    /** Set by every [checkAndUpdateIfNeeded] call; the running check repeats until it is clear. */
    @Volatile
    private var checkRequested = false

    /**
     * When [setDailyMixSongs] last replaced the Daily Mix, set at once; the saved copy is written
     * a moment later and covers restarts. A generation that day keeps the replacement.
     */
    @Volatile
    private var dailyMixReplacedAtMillis = 0L

    /**
     * Guards [dailyMixReplacements] together with the shown Daily Mix, so a generated mix is only
     * shown if no replacement arrived while it was being made.
     */
    private val dailyMixLock = Any()

    /** Counts [setDailyMixSongs] calls; guarded by [dailyMixLock]. */
    private var dailyMixReplacements = 0

    /** Orders the saved Daily Mix ids the same way the shown ones changed. */
    private val dailyMixSaveMutex = Mutex()
    private val owners = OwnerStack<CoroutineScope> { it }

    private val _dailyMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val dailyMixSongs: StateFlow<ImmutableList<Song>> = _dailyMixSongs.asStateFlow()

    private val _yourMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val yourMixSongs: StateFlow<ImmutableList<Song>> = _yourMixSongs.asStateFlow()

    /**
     * Initialize with coroutine scope from ViewModel.
     */
    fun initialize(coroutineScope: CoroutineScope) {
        owners.push(coroutineScope)
        scope = coroutineScope
    }

    /**
     * Remove a song from the daily mix.
     */
    fun removeFromDailyMix(songId: String) {
        _dailyMixSongs.update { currentList ->
            currentList.filterNot { it.id == songId }.toImmutableList()
        }
        writeDailyMixSoon()
    }

    /** Replaces today's Daily Mix (e.g. with a Smart Mix preview) and persists it. */
    fun setDailyMixSongs(songs: List<Song>) {
        updateJob?.cancel()
        val replacedAt = System.currentTimeMillis()
        val replacement = synchronized(dailyMixLock) {
            dailyMixReplacedAtMillis = replacedAt
            _dailyMixSongs.value = songs.toImmutableList()
            ++dailyMixReplacements
        }
        updateJob = scope?.launch {
            dailyMixSaveMutex.withLock {
                // A newer replacement saves its own songs.
                if (replacement != synchronized(dailyMixLock) { dailyMixReplacements }) return@withLock
                userPreferencesRepository.saveDailyMixSongIds(songs.map { it.id })
                userPreferencesRepository.saveDailyMixReplacedAt(replacedAt)
            }
        }
        writeDailyMixSoon()
    }

    /**
     * Mod: a change to today's Daily Mix goes into the same playlist on Navidrome, so the other apps take it over.
     * Waits a moment, so removing a few songs in a row writes once.
     */
    private fun writeDailyMixSoon() {
        mixWriteJob?.cancel()
        mixWriteJob = scope?.launch(Dispatchers.IO) {
            delay(MIX_WRITE_DELAY_MS)
            if (!mixSync.isSaving()) return@launch
            val songs = _dailyMixSongs.value
            if (songs.isEmpty()) return@launch
            // A write that started finishes, so the playlist never keeps new songs with yesterday's marker.
            withContext(NonCancellable) {
                val serverIds = mixSync.serverIds(musicRepository.getAllSongsOnce())
                mixSync.publish(SharedMix.DAILY, songs.mapNotNull { serverIds[it.id] }, LocalDate.now(), replaceToday = true)
            }
        }
    }

    /**
     * Mod: puts the mixes shown now on Navidrome, for when saving was just turned on or from the developer page. A mix
     * of today that another app already wrote wins and is shown instead. With saving off it only looks for those.
     */
    fun syncMixesNow() {
        scope?.launch(Dispatchers.IO) {
            val today = LocalDate.now()
            val library = musicRepository.getAllSongsOnce()
            if (!mixSync.isSaving()) {
                val found = mixSync.todaysMixes(today, onlyChanged = false) ?: return@launch
                if (found.isEmpty()) mixSync.recordUpToDate()
                val serverIds = mixSync.serverIds(library)
                found.forEach { (mix, server) ->
                    val songs = mixSync.songsFor(server.serverSongIds, library, serverIds)
                    if (songs.isNotEmpty()) showServerMix(mix, songs)
                }
                return@launch
            }
            val serverIds = mixSync.serverIds(library)
            for (mix in SharedMix.entries) {
                val shown = if (mix == SharedMix.DAILY) _dailyMixSongs.value else _yourMixSongs.value
                if (shown.isEmpty()) continue
                val result = mixSync.publish(mix, shown.mapNotNull { serverIds[it.id] }, today, replaceToday = false)
                if (result is NavidromeMixSync.Publish.TookOver) {
                    val songs = mixSync.songsFor(result.mix.serverSongIds, library, serverIds)
                    if (songs.isNotEmpty()) showServerMix(mix, songs)
                }
            }
        }
    }

    /** Shows and saves a mix that came from the server. */
    private suspend fun showServerMix(mix: SharedMix, songs: List<Song>) {
        when (mix) {
            SharedMix.DAILY -> dailyMixSaveMutex.withLock {
                synchronized(dailyMixLock) { _dailyMixSongs.value = songs.toImmutableList() }
                userPreferencesRepository.saveDailyMixSongIds(songs.map { it.id })
            }
            SharedMix.YOUR -> {
                _yourMixSongs.value = songs.toImmutableList()
                userPreferencesRepository.saveYourMixSongIds(songs.map { it.id })
            }
        }
    }

    /**
     * Update the daily mix with new songs.
     * Uses getAllSongsOnce() to load songs on-demand instead of keeping a permanent subscription.
     */
    fun updateDailyMix(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        updateJob?.cancel()
        checkJob?.cancel()
        updateJob = scope?.launch(Dispatchers.IO) {
            generateMixes(favoriteSongIdsFlow, keepReplacedDailyMix = false, replaceToday = true)
        }
    }

    /**
     * Makes today's mixes. Mod: a mix of today that another app put on Navidrome is taken over instead, and with
     * saving on a new mix goes to the server; [replaceToday] (Regenerate Daily Mix) overwrites today's there.
     */
    private suspend fun generateMixes(
        favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>,
        keepReplacedDailyMix: Boolean,
        replaceToday: Boolean = false
    ) {
        val replacementsBefore = synchronized(dailyMixLock) { dailyMixReplacements }
        val allSongs = musicRepository.getAllSongsOnce()
        if (allSongs.isNotEmpty()) {
            val favoriteIds = favoriteSongIdsFlow.first()
            val today = LocalDate.now()
            val serverRead = if (replaceToday) emptyMap() else mixSync.todaysMixes(today, onlyChanged = false)
            val serverMixes = serverRead.orEmpty()
            // A server that could not be read is not tried again for writing now, so a new day's mix is not held up
            // twice by a timeout (on the road, for instance).
            val saving = serverRead != null && mixSync.isSaving()
            val serverIds = if (saving || serverMixes.isNotEmpty()) mixSync.serverIds(allSongs) else emptyMap()
            // With saving on, a mix only picks songs that are on the server, so the phone and the server hold the
            // same list. The local file still plays.
            val pool = if (saving) allSongs.filter { it.id in serverIds }.ifEmpty { allSongs } else allSongs

            if (!(keepReplacedDailyMix && isKeepingReplacedDailyMix())) {
                val mix = sharedMix(SharedMix.DAILY, serverMixes, allSongs, serverIds, today, replaceToday, saving) {
                    dailyMixManager.generateDailyMix(pool, favoriteIds)
                }
                dailyMixSaveMutex.withLock {
                    val shown = synchronized(dailyMixLock) {
                        // A Daily Mix the user applied while this one was made wins, even if
                        // the checks above still saw the one before it.
                        (dailyMixReplacements == replacementsBefore).also { unchanged ->
                            if (unchanged) _dailyMixSongs.value = mix.toImmutableList()
                        }
                    }
                    if (shown) userPreferencesRepository.saveDailyMixSongIds(mix.map { it.id })
                }
            }

            val yourMix = sharedMix(SharedMix.YOUR, serverMixes, allSongs, serverIds, today, replaceToday, saving) {
                dailyMixManager.generateYourMix(pool, favoriteIds)
            }
            _yourMixSongs.value = yourMix.toImmutableList()
            userPreferencesRepository.saveYourMixSongIds(yourMix.map { it.id })
        } else {
            _yourMixSongs.value = persistentListOf()
        }
    }

    /** Mod: today's [mix] from the server if another app made it, otherwise a new one, written there when saving. */
    private suspend fun sharedMix(
        mix: SharedMix,
        serverMixes: Map<SharedMix, NavidromeMixSync.ServerMix>,
        library: List<Song>,
        serverIds: Map<String, String>,
        today: LocalDate,
        replaceToday: Boolean,
        write: Boolean,
        generate: suspend () -> List<Song>
    ): List<Song> {
        serverMixes[mix]?.let { server ->
            val songs = mixSync.songsFor(server.serverSongIds, library, serverIds)
            if (songs.isNotEmpty()) return songs
        }
        val made = generate().preferLocalCopies(library)
        if (!write) return made
        val result = withContext(NonCancellable) {
            mixSync.publish(mix, made.mapNotNull { serverIds[it.id] }, today, replaceToday)
        }
        if (result is NavidromeMixSync.Publish.TookOver) {
            mixSync.songsFor(result.mix.serverSongIds, library, serverIds).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return made
    }

    /**
     * Mod: takes over a mix of today that another app changed or wrote on Navidrome since the last look. A Daily Mix
     * the user replaced today stays when this app does not save, as nothing of it is on the server.
     */
    private suspend fun takeOverChangedServerMixes() {
        val changed = mixSync.todaysMixes(LocalDate.now(), onlyChanged = true) ?: return
        if (changed.isEmpty()) {
            mixSync.recordUpToDate()
            return
        }
        val library = musicRepository.getAllSongsOnce()
        val serverIds = mixSync.serverIds(library)
        val keepReplacement = !mixSync.isSaving() && isKeepingReplacedDailyMix()
        changed.forEach { (mix, server) ->
            if (mix == SharedMix.DAILY && keepReplacement) return@forEach
            val songs = mixSync.songsFor(server.serverSongIds, library, serverIds)
            if (songs.isNotEmpty()) showServerMix(mix, songs)
        }
    }

    /**
     * Load persisted daily mix from storage using direct DB queries by IDs
     * instead of combining with the full allSongs flow.
     */
    fun loadPersistedDailyMix() {
        scope?.launch {
            val dailyMixIds = userPreferencesRepository.dailyMixSongIdsFlow.first()
            if (dailyMixIds.isNotEmpty() && _dailyMixSongs.value.isEmpty()) {
                val (songs, library) = withContext(Dispatchers.IO) {
                    musicRepository.getSongsByIds(dailyMixIds).first() to musicRepository.getAllSongsOnce()
                }
                if (songs.isNotEmpty()) {
                    val songMap = songs.associateBy { it.id }
                    val orderedSongs = dailyMixIds.mapNotNull { songMap[it] }.preferLocalCopies(library)
                    _dailyMixSongs.value = orderedSongs.toImmutableList()
                }
            }
        }

        scope?.launch {
            val yourMixIds = userPreferencesRepository.yourMixSongIdsFlow.first()
            if (yourMixIds.isNotEmpty() && _yourMixSongs.value.isEmpty()) {
                val (songs, library) = withContext(Dispatchers.IO) {
                    musicRepository.getSongsByIds(yourMixIds).first() to musicRepository.getAllSongsOnce()
                }
                if (songs.isNotEmpty()) {
                    val songMap = songs.associateBy { it.id }
                    val orderedSongs = yourMixIds.mapNotNull { songMap[it] }.preferLocalCopies(library)
                    _yourMixSongs.value = orderedSongs.toImmutableList()
                }
            }
        }
    }

    /**
     * Force update the daily mix regardless of day.
     */
    fun forceUpdate(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        scope?.launch {
            updateDailyMix(favoriteSongIdsFlow)
            userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
        }
    }

    /**
     * Generates the mixes again on a new day, or when none of today's Daily Mix is in the
     * library (e.g. the library was still empty at the day's first check). Generating ranks the
     * whole library, so finished syncs and cold starts call this rather than [updateDailyMix].
     */
    fun checkAndUpdateIfNeeded(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        checkRequested = true
        // A check already running goes again once it finishes (e.g. to see the songs of a sync
        // that ended meanwhile), rather than a second check reading the whole library while the
        // first one's copy is still in memory.
        if (checkJob?.isActive == true) return
        checkJob = scope?.launch {
            while (checkRequested) {
                checkRequested = false
                // A regeneration from Regenerate Daily Mix finishes first, so two never read
                // the library at once.
                updateJob?.join()
                updateIfDue(favoriteSongIdsFlow)
            }
        }
    }

    private suspend fun updateIfDue(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        val lastUpdate = userPreferencesRepository.lastDailyMixUpdateFlow.first()
        val now = System.currentTimeMillis()
        if (!isDifferentDay(lastUpdate, now) && hasDailyMixInLibrary()) {
            refreshTodaysMixes()
            withContext(Dispatchers.IO) { takeOverChangedServerMixes() }
            return
        }
        withContext(Dispatchers.IO) { generateMixes(favoriteSongIdsFlow, keepReplacedDailyMix = true) }
        userPreferencesRepository.saveLastDailyMixUpdateTimestamp(now)
    }

    /**
     * Whether the user replaced the Daily Mix today and some of it is still in the library. A
     * replacement whose songs a sync removed is generated over like any other mix.
     */
    private suspend fun isKeepingReplacedDailyMix(): Boolean {
        val replacedAt = maxOf(dailyMixReplacedAtMillis, userPreferencesRepository.dailyMixReplacedAtFlow.first())
        if (isDifferentDay(replacedAt, System.currentTimeMillis())) return false
        // The shown mix is the replacement as soon as it is applied, while its saved ids may still
        // be on their way; after a restart nothing may be shown yet, so the saved ids decide.
        val replacementIds = _dailyMixSongs.value.map { it.id }
            .ifEmpty { userPreferencesRepository.dailyMixSongIdsFlow.first() }
        return replacementIds.isNotEmpty() &&
            musicRepository.getSongsByIds(replacementIds).first().isNotEmpty()
    }

    private suspend fun hasDailyMixInLibrary(): Boolean {
        val dailyMixIds = userPreferencesRepository.dailyMixSongIdsFlow.first()
        return dailyMixIds.isNotEmpty() && musicRepository.getSongsByIds(dailyMixIds).first().isNotEmpty()
    }

    /** Keeps today's picks and their order, minus songs a sync removed, with current details. */
    private suspend fun refreshTodaysMixes() {
        val shownIds = (_dailyMixSongs.value + _yourMixSongs.value).map { it.id }.distinct()
        if (shownIds.isEmpty()) return
        val inLibrary = musicRepository.getSongsByIds(shownIds).first().associateBy { it.id }
        val checkedIds = shownIds.toSet()
        // A song that joined a mix after the read (e.g. the saved mix finished loading) stays.
        fun List<Song>.refreshed() = mapNotNull { song ->
            if (song.id in checkedIds) inLibrary[song.id] else song
        }.toImmutableList()
        _dailyMixSongs.update { it.refreshed() }
        _yourMixSongs.update { it.refreshed() }
    }

    /**
     * A stale PlayerViewModel clearing leaves the newer binding alone; the current one clearing
     * hands the holder back to the previous live PlayerViewModel instead of leaving it
     * scope-less, which would stop Daily Mix updates until process restart.
     */
    fun onCleared(owner: CoroutineScope) {
        owners.remove(owner) { next ->
            if (next == null) {
                updateJob?.cancel()
                checkJob?.cancel()
                mixWriteJob?.cancel()
            }
            scope = next
        }
    }
}

private const val MIX_WRITE_DELAY_MS = 1_500L

/** Whether the two instants fall on different calendar days in [zoneId]. */
internal fun isDifferentDay(
    firstMillis: Long,
    secondMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): Boolean =
    Instant.ofEpochMilli(firstMillis).atZone(zoneId).toLocalDate() !=
        Instant.ofEpochMilli(secondMillis).atZone(zoneId).toLocalDate()
