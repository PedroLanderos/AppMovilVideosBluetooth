package com.example.btvideo.data

import android.content.Context
import com.example.btvideo.model.SearchResult
import org.json.JSONArray
import java.io.File

class VideoCatalog(private val context: Context) {

    fun search(query: String): List<SearchResult> {
        val normalized = query.trim().lowercase()
        val all = loadCatalog()
        if (normalized.isBlank()) return all.take(10)
        return all.filter {
            it.title.lowercase().contains(normalized) || it.id.lowercase().contains(normalized)
        }.take(10)
    }

    fun find(videoId: String): SearchResult? = loadCatalog().firstOrNull { it.id == videoId }

    fun openVideoAsset(videoId: String, lowPower: Boolean): AssetVideo? {
        val fileName = if (lowPower) "videos/${videoId}_low.mp4" else "videos/$videoId.mp4"
        return try {
            val afd = context.assets.openFd(fileName)
            AssetVideo(fileName, afd.length, context.assets.open(fileName))
        } catch (_: Exception) {
            try {
                val fallback = "videos/$videoId.mp4"
                val afd = context.assets.openFd(fallback)
                AssetVideo(fallback, afd.length, context.assets.open(fallback))
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun loadCatalog(): List<SearchResult> {
        val text = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        return List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            SearchResult(
                id = obj.getString("id"),
                title = obj.getString("title"),
                source = obj.optString("source", "local-cache"),
                verified = obj.optBoolean("verified", true),
                durationText = obj.optString("durationText", "")
            )
        }
    }
}

data class AssetVideo(
    val assetPath: String,
    val totalBytes: Long,
    val inputStream: java.io.InputStream
)
