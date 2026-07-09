package com.example.btvideo.data

import android.content.Context
import com.example.btvideo.model.SearchResult
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.Locale

/**
 * Fuente de video basada en yt-dlp/youtubedl-android para preparar contenido desde el servidor.
 */
class YoutubeExperimentalSource(private val context: Context) {
    private val downloadDir: File = File(context.cacheDir, "youtube_cache").apply { mkdirs() }

    fun search(queryOrUrl: String, maxResults: Int = 5): List<SearchResult> {
        val cleaned = queryOrUrl.trim()
        if (cleaned.isBlank()) return emptyList()

        val target = if (looksLikeUrl(cleaned)) cleaned else "ytsearch$maxResults:$cleaned"
        val request = YoutubeDLRequest(target).apply {
            addOption("--dump-json")
            addOption("--flat-playlist")
            addOption("--no-warnings")
            addOption("--ignore-errors")
        }

        val output = YoutubeDL.getInstance().execute(request).out.orEmpty()
        return parseSearchOutput(output, cleaned, maxResults)
    }

    fun getVideo(videoUrlOrId: String, lowPower: Boolean): TransferVideo {
        val url = normalizeYoutubeIdOrUrl(videoUrlOrId)
        val prefix = safeFilePrefix(url)
        findCachedFile(prefix)?.let { return it.toTransferVideo() }

        val outputTemplate = File(downloadDir, "$prefix.%(ext)s").absolutePath
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--ignore-errors")
            addOption("--restrict-filenames")
            addOption("--no-mtime")
            addOption("--force-overwrites")
            addOption("--merge-output-format", "mp4")
            addOption("--remux-video", "mp4")
            addOption("-o", outputTemplate)
            addOption("-f", formatSelector(lowPower))
        }

        YoutubeDL.getInstance().execute(request)
        val downloaded = findCachedFile(prefix)
            ?: throw IllegalStateException("yt-dlp terminó, pero no se encontró el archivo descargado en ${downloadDir.absolutePath}")
        return downloaded.toTransferVideo()
    }

    private fun parseSearchOutput(output: String, originalQuery: String, maxResults: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val trimmed = output.trim()
        if (trimmed.isBlank()) return emptyList()

        // Caso 1: yt-dlp puede devolver JSON por línea.
        trimmed.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("{") && it.endsWith("}") }
            .forEach { line -> parseSingleVideoJson(line)?.let { results.add(it) } }

        // Caso 2: algunos extractores devuelven un objeto con entries.
        if (results.isEmpty() && trimmed.startsWith("{")) {
            try {
                val root = JSONObject(trimmed)
                val entries = root.optJSONArray("entries")
                if (entries != null) {
                    for (i in 0 until entries.length()) {
                        val item = entries.optJSONObject(i) ?: continue
                        parseVideoObject(item)?.let { results.add(it) }
                    }
                } else {
                    parseVideoObject(root)?.let { results.add(it) }
                }
            } catch (_: Exception) {
                // Se reporta abajo como resultado vacío.
            }
        }

        return results.distinctBy { it.id }.take(maxResults).ifEmpty {
            if (looksLikeUrl(originalQuery)) {
                listOf(
                    SearchResult(
                        id = originalQuery,
                        title = "Video solicitado: $originalQuery",
                        source = "YouTube",
                        verified = false,
                        durationText = "",
                        playable = true
                    )
                )
            } else {
                emptyList()
            }
        }
    }

    private fun parseSingleVideoJson(line: String): SearchResult? = try {
        parseVideoObject(JSONObject(line))
    } catch (_: Exception) {
        null
    }

    private fun parseVideoObject(obj: JSONObject): SearchResult? {
        val id = obj.optString("id").takeIf { it.isNotBlank() }
        val webpageUrl = obj.optString("webpage_url").takeIf { it.isNotBlank() }
        val url = obj.optString("url").takeIf { it.isNotBlank() }
        val finalUrl = when {
            !webpageUrl.isNullOrBlank() -> webpageUrl
            !id.isNullOrBlank() && id.length == 11 -> "https://www.youtube.com/watch?v=$id"
            !url.isNullOrBlank() && url.length == 11 -> "https://www.youtube.com/watch?v=$url"
            !url.isNullOrBlank() -> url
            else -> return null
        }
        val title = obj.optString("title").takeIf { it.isNotBlank() } ?: finalUrl
        val duration = obj.optLong("duration", -1L).takeIf { it >= 0 }?.let { secondsToText(it) }.orEmpty()
        return SearchResult(
            id = finalUrl,
            title = title,
            source = "YouTube",
            verified = false,
            durationText = duration,
            playable = true
        )
    }

    private fun File.toTransferVideo(): TransferVideo = TransferVideo(
        displayName = name,
        totalBytes = length(),
        mime = mimeFromExtension(name),
        inputStreamFactory = { inputStream() }
    )

    private fun findCachedFile(prefix: String): File? {
        val files = downloadDir
            .listFiles()
            ?.filter { file -> file.isFile && file.name.startsWith(prefix) && file.length() > 0L }
            .orEmpty()

        // Preferimos MP4 porque VideoView suele fallar con WebM/AV1/VP9 en algunos equipos.
        return files
            .filter { it.extension.equals("mp4", ignoreCase = true) }
            .maxByOrNull { it.lastModified() }
            ?: files.maxByOrNull { it.lastModified() }
    }

    private fun formatSelector(lowPower: Boolean): String = if (lowPower) {
        "bestvideo[ext=mp4][vcodec^=avc1][height<=240]+bestaudio[ext=m4a][acodec^=mp4a]/best[ext=mp4][vcodec^=avc1][height<=240]/best[ext=mp4][height<=240]/worst[ext=mp4]/worst"
    } else {
        "bestvideo[ext=mp4][vcodec^=avc1][height<=360]+bestaudio[ext=m4a][acodec^=mp4a]/best[ext=mp4][vcodec^=avc1][height<=360]/best[ext=mp4][height<=360]/best[ext=mp4]/best"
    }

    private fun mimeFromExtension(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "3gp", "3gpp" -> "video/3gpp"
        else -> "video/mp4"
    }

    private fun normalizeYoutubeIdOrUrl(value: String): String {
        val cleaned = value.trim()
        return if (looksLikeUrl(cleaned)) cleaned else "https://www.youtube.com/watch?v=$cleaned"
    }

    private fun safeFilePrefix(value: String): String {
        val encoded = URLEncoder.encode(value, "UTF-8")
        return "yt_" + encoded.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').take(60)
    }

    private fun looksLikeUrl(value: String): Boolean = value.startsWith("http://") || value.startsWith("https://")

    private fun secondsToText(seconds: Long): String {
        val minutes = seconds / 60
        val remaining = seconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, remaining)
    }
}
