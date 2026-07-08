package com.example.btvideo.data

import android.content.Context
import com.example.btvideo.model.SearchResult
import org.json.JSONArray

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

    fun openVideoAsset(videoId: String, lowPower: Boolean): TransferVideo? {
        val fileName = if (lowPower) "videos/${videoId}_low.mp4" else "videos/$videoId.mp4"
        return openAsset(fileName) ?: openAsset("videos/$videoId.mp4")
    }

    private fun openAsset(fileName: String): TransferVideo? = try {
        val afd = context.assets.openFd(fileName)
        TransferVideo(
            displayName = fileName,
            totalBytes = afd.length,
            mime = "video/mp4",
            inputStreamFactory = { context.assets.open(fileName) }
        )
    } catch (_: Exception) {
        null
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
                durationText = obj.optString("durationText", ""),
                playable = true
            )
        }
    }
}
