package com.example.btvideo.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import com.example.btvideo.model.SearchResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class ServerVideoLibrary(private val context: Context) {
    private val prefs = context.getSharedPreferences("server_video_library", Context.MODE_PRIVATE)

    data class LibraryVideo(
        val id: String,
        val title: String,
        val uri: String,
        val mime: String,
        val sizeBytes: Long,
        val durationText: String
    )

    fun addVideo(uri: Uri): Boolean {
        val uriString = uri.toString()
        val current = loadVideos().toMutableList()
        if (current.any { it.uri == uriString }) return false

        val metadata = readMetadata(uri)
        val video = LibraryVideo(
            id = "server_${System.currentTimeMillis()}_${current.size}",
            title = metadata.title,
            uri = uriString,
            mime = metadata.mime,
            sizeBytes = metadata.sizeBytes,
            durationText = metadata.durationText
        )
        current.add(video)
        saveVideos(current)
        return true
    }

    fun clear() {
        prefs.edit().remove(KEY_VIDEOS).apply()
    }

    fun search(query: String): List<SearchResult> {
        val normalized = query.trim().lowercase()
        val all = loadVideos()
        val filtered = if (normalized.isBlank()) {
            all
        } else {
            all.filter {
                it.title.lowercase().contains(normalized) || it.id.lowercase().contains(normalized)
            }
        }

        return filtered.take(20).map {
            SearchResult(
                id = it.id,
                title = it.title,
                source = "biblioteca-servidor",
                verified = true,
                durationText = if (it.durationText.isBlank()) "" else " | ${it.durationText}",
                playable = true
            )
        }
    }

    fun find(videoId: String): SearchResult? = loadVideos().firstOrNull { it.id == videoId }?.let {
        SearchResult(
            id = it.id,
            title = it.title,
            source = "biblioteca-servidor",
            verified = true,
            durationText = if (it.durationText.isBlank()) "" else " | ${it.durationText}",
            playable = true
        )
    }

    fun openVideo(videoId: String): TransferVideo? {
        val video = loadVideos().firstOrNull { it.id == videoId } ?: return null
        val uri = Uri.parse(video.uri)
        val resolvedSize = resolveSize(uri).takeIf { it > 0 } ?: video.sizeBytes

        return TransferVideo(
            displayName = video.title,
            totalBytes = resolvedSize.coerceAtLeast(0),
            mime = video.mime.ifBlank { "video/mp4" },
            inputStreamFactory = {
                context.contentResolver.openInputStream(uri)
                    ?: throw IOException("No se pudo abrir el video seleccionado")
            }
        )
    }

    fun summary(): String {
        val videos = loadVideos()
        if (videos.isEmpty()) return "Biblioteca del servidor: 0 videos agregados"
        val names = videos.take(5).joinToString(separator = "\n") { "• ${it.title}${if (it.durationText.isBlank()) "" else " (${it.durationText})"}" }
        val extra = if (videos.size > 5) "\n• ... y ${videos.size - 5} más" else ""
        return "Biblioteca del servidor: ${videos.size} video(s) agregado(s)\n$names$extra"
    }

    private data class Metadata(
        val title: String,
        val mime: String,
        val sizeBytes: Long,
        val durationText: String
    )

    private fun readMetadata(uri: Uri): Metadata {
        val displayName = readDisplayName(uri) ?: "Video seleccionado"
        val mime = context.contentResolver.getType(uri) ?: "video/mp4"
        val sizeBytes = resolveSize(uri)
        val durationText = readDurationText(uri)
        return Metadata(displayName, mime, sizeBytes, durationText)
    }

    private fun readDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }

    private fun resolveSize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0) {
                    val size = cursor.getLong(index)
                    if (size > 0) return size
                }
            }
        }

        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length.takeIf { it > 0 } ?: 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun readDurationText(uri: Uri): String {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            if (durationMs <= 0) "" else formatDuration(durationMs)
        } catch (_: Exception) {
            ""
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun loadVideos(): List<LibraryVideo> {
        val raw = prefs.getString(KEY_VIDEOS, "[]") ?: "[]"
        val array = JSONArray(raw)
        return List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            LibraryVideo(
                id = obj.getString("id"),
                title = obj.getString("title"),
                uri = obj.getString("uri"),
                mime = obj.optString("mime", "video/mp4"),
                sizeBytes = obj.optLong("sizeBytes", 0L),
                durationText = obj.optString("durationText", "")
            )
        }
    }

    private fun saveVideos(videos: List<LibraryVideo>) {
        val array = JSONArray()
        videos.forEach { video ->
            array.put(JSONObject()
                .put("id", video.id)
                .put("title", video.title)
                .put("uri", video.uri)
                .put("mime", video.mime)
                .put("sizeBytes", video.sizeBytes)
                .put("durationText", video.durationText))
        }
        prefs.edit().putString(KEY_VIDEOS, array.toString()).apply()
    }

    companion object {
        private const val KEY_VIDEOS = "videos"
    }
}
