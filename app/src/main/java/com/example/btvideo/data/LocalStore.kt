package com.example.btvideo.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class StoredVideo(
    val videoId: String,
    val title: String,
    val timestamp: Long
)

class LocalStore(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE history(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                video_id TEXT NOT NULL,
                title TEXT NOT NULL,
                played_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE favorites(
                video_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS history")
        db.execSQL("DROP TABLE IF EXISTS favorites")
        onCreate(db)
    }

    fun addHistory(videoId: String, title: String) {
        if (videoId.isBlank() || title.isBlank()) return

        writableDatabase.insert(
            "history",
            null,
            ContentValues().apply {
                put("video_id", videoId)
                put("title", title)
                put("played_at", System.currentTimeMillis())
            }
        )
    }

    fun getHistory(limit: Int = 30): List<StoredVideo> {
        val safeLimit = limit.coerceIn(1, 100)
        val items = mutableListOf<StoredVideo>()

        readableDatabase.rawQuery(
            """
            SELECT video_id, title, played_at
            FROM history
            ORDER BY played_at DESC, id DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(safeLimit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items.add(
                    StoredVideo(
                        videoId = cursor.getString(0),
                        title = cursor.getString(1),
                        timestamp = cursor.getLong(2)
                    )
                )
            }
        }

        return items
    }

    fun clearHistory() {
        writableDatabase.delete("history", null, null)
    }

    fun toggleFavorite(videoId: String, title: String): Boolean {
        if (videoId.isBlank() || title.isBlank()) return false

        val db = writableDatabase
        val exists = isFavorite(videoId)

        return if (exists) {
            db.delete("favorites", "video_id = ?", arrayOf(videoId))
            false
        } else {
            db.insert(
                "favorites",
                null,
                ContentValues().apply {
                    put("video_id", videoId)
                    put("title", title)
                    put("created_at", System.currentTimeMillis())
                }
            )
            true
        }
    }

    fun isFavorite(videoId: String): Boolean {
        if (videoId.isBlank()) return false

        return readableDatabase.rawQuery(
            "SELECT video_id FROM favorites WHERE video_id = ? LIMIT 1",
            arrayOf(videoId)
        ).use { cursor ->
            cursor.moveToFirst()
        }
    }

    fun getFavorites(): List<StoredVideo> {
        val items = mutableListOf<StoredVideo>()

        readableDatabase.rawQuery(
            """
            SELECT video_id, title, created_at
            FROM favorites
            ORDER BY created_at DESC, title COLLATE NOCASE ASC
            """.trimIndent(),
            emptyArray<String>()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items.add(
                    StoredVideo(
                        videoId = cursor.getString(0),
                        title = cursor.getString(1),
                        timestamp = cursor.getLong(2)
                    )
                )
            }
        }

        return items
    }

    fun clearFavorites() {
        writableDatabase.delete("favorites", null, null)
    }

    companion object {
        const val DATABASE_NAME = "bt_video_store.db"
        private const val DATABASE_VERSION = 1
    }
}
