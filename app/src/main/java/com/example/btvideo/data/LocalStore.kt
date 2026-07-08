package com.example.btvideo.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LocalStore(context: Context) : SQLiteOpenHelper(context, "bt_video_store.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE history(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                video_id TEXT NOT NULL,
                title TEXT NOT NULL,
                played_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE favorites(
                video_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS history")
        db.execSQL("DROP TABLE IF EXISTS favorites")
        onCreate(db)
    }

    fun addHistory(videoId: String, title: String) {
        writableDatabase.insert("history", null, ContentValues().apply {
            put("video_id", videoId)
            put("title", title)
            put("played_at", System.currentTimeMillis())
        })
    }

    fun toggleFavorite(videoId: String, title: String): Boolean {
        val db = writableDatabase
        val exists = db.rawQuery("SELECT video_id FROM favorites WHERE video_id = ?", arrayOf(videoId)).use { it.moveToFirst() }
        return if (exists) {
            db.delete("favorites", "video_id = ?", arrayOf(videoId))
            false
        } else {
            db.insert("favorites", null, ContentValues().apply {
                put("video_id", videoId)
                put("title", title)
                put("created_at", System.currentTimeMillis())
            })
            true
        }
    }
}
