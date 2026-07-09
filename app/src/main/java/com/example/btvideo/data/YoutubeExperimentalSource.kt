package com.example.btvideo.data

import android.content.Context
import android.media.MediaMetadataRetriever
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
    private val downloadDir: File = File(context.cacheDir, "youtube_cache_h264_v4").apply { mkdirs() }

    private data class DownloadStrategy(
        val name: String,
        val selector: String,
        val forceRecode: Boolean
    )

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

        // YouTube no siempre ofrece el mismo conjunto de formatos para todos los videos.
        // Algunos videos no tienen el formato 18/22 (MP4 progresivo), por eso se intenta
        // primero con MP4 compatible y después con una estrategia universal que permite
        // video+audio separados y recodifica a MP4/H.264/AAC para VideoView.
        val errors = mutableListOf<String>()
        for (strategy in downloadStrategies(lowPower)) {
            clearCachedFiles(prefix)
            try {
                val downloaded = executeDownload(url, prefix, strategy)
                if (!downloaded.hasVideoTrack()) {
                    downloaded.delete()
                    errors.add("${strategy.name}: el archivo no contiene pista de video")
                    continue
                }
                return downloaded.toTransferVideo()
            } catch (e: Exception) {
                errors.add("${strategy.name}: ${e.message ?: e.javaClass.simpleName}")
            }
        }

        throw IllegalStateException(
            "No se pudo preparar una versión compatible del video. Intenta con otro resultado o con un video más corto. Detalle: " +
                errors.joinToString(" | ")
        )
    }

    private fun executeDownload(url: String, prefix: String, strategy: DownloadStrategy): File {
        val outputTemplate = File(downloadDir, "$prefix.%(ext)s").absolutePath
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--restrict-filenames")
            addOption("--no-mtime")
            addOption("--force-overwrites")
            addOption("--merge-output-format", "mp4")
            addOption("--remux-video", "mp4")
            if (strategy.forceRecode) {
                addOption("--recode-video", "mp4")
                addOption(
                    "--postprocessor-args",
                    "ffmpeg:-c:v libx264 -preset ultrafast -profile:v baseline -level 3.0 -pix_fmt yuv420p -c:a aac -b:a 96k -movflags +faststart"
                )
            }
            addOption("-o", outputTemplate)
            addOption("-f", strategy.selector)
        }

        YoutubeDL.getInstance().execute(request)
        return findCachedFile(prefix)
            ?: throw IllegalStateException("no se encontró el archivo generado en ${downloadDir.absolutePath}")
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

    private fun downloadStrategies(lowPower: Boolean): List<DownloadStrategy> {
        val first = if (lowPower) {
            // Intenta usar MP4 progresivo o MP4 de baja resolución si existe.
            "best[ext=mp4][vcodec^=avc1][acodec^=mp4a][height<=360]/best[ext=mp4][height<=360]/best[height<=360]"
        } else {
            "best[ext=mp4][vcodec^=avc1][acodec^=mp4a][height<=480]/best[ext=mp4][height<=480]/best[height<=480]"
        }

        val fallback = if (lowPower) {
            // Fallback universal: permite DASH/WebM/otros formatos, pero luego recodifica.
            "bestvideo[height<=360]+bestaudio/bestvideo+bestaudio/best[height<=360]/best"
        } else {
            "bestvideo[height<=480]+bestaudio/bestvideo+bestaudio/best[height<=480]/best"
        }

        return listOf(
            DownloadStrategy("MP4 compatible", first, forceRecode = true),
            DownloadStrategy("Formato universal recodificado", fallback, forceRecode = true)
        )
    }

    private fun clearCachedFiles(prefix: String) {
        downloadDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) }
            ?.forEach { it.delete() }
    }

    private fun File.hasVideoTrack(): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            width > 0 && height > 0
        } catch (_: Exception) {
            false
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
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
