package com.lostf1sh.pixelplayeross.data.migration

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lostf1sh.pixelplayeross.data.database.PixelPlayerDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings Marks data over from the mod on the old PixelPlayer base, once. That app kept its library in
 * `pixelplay_database`; this one opens `pixelplayer_database`, so after installing over it the old file is still there
 * but nothing reads it. Likes, playlists, play counts, search history, lyrics and transition rules are copied across,
 * column by column where both sides have the column. The songs themselves come from the normal library scan.
 * Logins, settings and the listening history file need nothing: they use the same names in both apps.
 */
@Singleton
class LegacyModDataImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: PixelPlayerDatabase
) {
    fun importOnce() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return
        val legacy = context.getDatabasePath(LEGACY_DATABASE)
        if (!legacy.exists()) {
            prefs.edit().putBoolean(KEY_DONE, true).apply()
            return
        }
        // Read on a connection of its own: ATTACH on Room's connection makes Android turn off write-ahead logging,
        // which reopens the connections and drops the temporary table Room uses to track changes.
        runCatching {
            SQLiteDatabase.openDatabase(legacy.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { old ->
                val db = database.openHelper.writableDatabase
                db.beginTransaction()
                try {
                    val copied = TABLES.associateWith { copyTable(old, db, it) }
                    // The library scan may already have run; a like on a song in the library then shows straight away.
                    db.execSQL("UPDATE songs SET is_favorite = 1 WHERE id IN (SELECT songId FROM favorites WHERE isFavorite = 1)")
                    db.setTransactionSuccessful()
                    Timber.tag(TAG).i("Imported from the old mod: %s", copied)
                } finally {
                    db.endTransaction()
                }
            }
        }.onSuccess {
            prefs.edit().putBoolean(KEY_DONE, true).apply()
        }.onFailure {
            // Tried again on the next start; the old file stays as it is.
            Timber.tag(TAG).w(it, "Import from the old mod failed")
        }
    }

    /** Copies the rows of [table] for the columns both databases have. Returns how many rows were added, or -1. */
    private fun copyTable(old: SQLiteDatabase, db: SupportSQLiteDatabase, table: String): Int {
        val target = db.query("PRAGMA table_info(`$table`)").use { it.names() }
        val source = runCatching { old.rawQuery("PRAGMA table_info(`$table`)", null).use { it.names() } }
            .getOrDefault(emptyList()).toSet()
        // A fresh id avoids clashing with rows this app already made.
        val shared = target.filter { it in source && !(table in FRESH_ID_TABLES && it == "id") }
        if (shared.isEmpty()) return 0
        return runCatching {
            var added = 0
            old.rawQuery("SELECT ${shared.joinToString(",") { "`$it`" }} FROM `$table`", null).use { rows ->
                while (rows.moveToNext()) {
                    val values = ContentValues(shared.size)
                    shared.forEachIndexed { i, column ->
                        when (rows.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> values.putNull(column)
                            Cursor.FIELD_TYPE_INTEGER -> values.put(column, rows.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> values.put(column, rows.getDouble(i))
                            Cursor.FIELD_TYPE_BLOB -> values.put(column, rows.getBlob(i))
                            else -> values.put(column, rows.getString(i))
                        }
                    }
                    if (db.insert(table, SQLiteDatabase.CONFLICT_IGNORE, values) != -1L) added++
                }
            }
            added
        }.getOrElse {
            Timber.tag(TAG).w(it, "Could not copy %s", table)
            -1
        }
    }

    private fun Cursor.names(): List<String> {
        val name = getColumnIndexOrThrow("name")
        return buildList { while (moveToNext()) add(getString(name)) }
    }

    private companion object {
        const val TAG = "LegacyImport"
        const val PREFS = "mod_migration"
        const val KEY_DONE = "legacy_mod_imported"
        const val LEGACY_DATABASE = "pixelplay_database"
        val TABLES = listOf(
            "favorites",
            "playlists",
            "playlist_songs",
            "song_engagements",
            "search_history",
            "lyrics",
            "transition_rules"
        )
        val FRESH_ID_TABLES = setOf("search_history")
    }
}
