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
 *
 * Flujo:
 * 1. Buscar videos en YouTube.
 * 2. Descargar un formato disponible.
 * 3. Fusionar/convertir a MP4.
 * 4. Validar que el archivo final tenga pista de video.
 */
class YoutubeExperimentalSource(private val context: Context) {

    private val downloadDir: File = File(context.cacheDir, "youtube_cache_h264_v7").apply {
        mkdirs()
    }

    private var updateAttempted = false

    private data class DownloadStrategy(
        val name: String,
        val selector: String,
        val sort: String,
        val forceRecode: Boolean
    )

    fun search(queryOrUrl: String, maxResults: Int = 5): List<SearchResult> {
        val cleaned = queryOrUrl.trim()
        if (cleaned.isBlank()) return emptyList()

        updateYoutubeDlIfPossible()

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
        updateYoutubeDlIfPossible()

        val url = normalizeYoutubeIdOrUrl(videoUrlOrId)
        val prefix = safeFilePrefix(url)

        findPreparedMp4(prefix)?.let { cachedFile ->
            return cachedFile.toTransferVideo()
        }

        val errors = mutableListOf<String>()

        for (strategy in downloadStrategies(lowPower)) {
            clearCachedFiles(prefix)

            try {
                val downloaded = executeDownload(url, prefix, strategy)

                if (!downloaded.hasVideoTrack()) {
                    val debug = debugFileMetadata(downloaded)
                    downloaded.delete()
                    errors.add("${strategy.name}: el archivo no contiene pista de video. $debug")
                    continue
                }

                return downloaded.toTransferVideo()
            } catch (e: Exception) {
                errors.add("${strategy.name}: ${e.message ?: e.javaClass.simpleName}")
            }
        }

        throw IllegalStateException(
            "No se pudo preparar una versión compatible del video. " +
                    "Intenta con otro resultado o con un video más corto. Detalle: " +
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
            addOption("--no-part")

            /*
             * Importante:
             * No usar --prefer-ffmpeg.
             * yt-dlp ya lo marcó como deprecated y en tus tests estaba apareciendo como warning/error.
             */

            addOption("-o", outputTemplate)

            /*
             * Este selector es intencionalmente menos estricto:
             *
             * bv*+ba  = mejor pista que tenga video + mejor audio.
             * b       = fallback a mejor formato combinado.
             *
             * Antes fallaba porque pedíamos formatos demasiado específicos:
             * mp4 + avc1 + m4a + altura fija.
             */
            addOption("-f", strategy.selector)

            /*
             * -S ordena/preferirá formatos compatibles, pero no bloquea todo si no encuentra
             * exactamente MP4/H264/AAC. Esto evita muchos "Requested format is not available".
             */
            addOption("-S", strategy.sort)
            addOption("--format-sort-force")

            /*
             * Intentamos que la salida final sea MP4.
             */
            addOption("--merge-output-format", "mp4")

            if (strategy.forceRecode) {
                /*
                 * Si YouTube entrega WebM, VP9, AV1, Opus, etc.,
                 * FFmpeg debe convertirlo a MP4/H264/AAC.
                 */
                addOption("--recode-video", "mp4")
                addOption(
                    "--postprocessor-args",
                    "ffmpeg:-c:v libx264 -preset ultrafast -profile:v baseline -level 3.0 -pix_fmt yuv420p -c:a aac -b:a 96k -movflags +faststart"
                )
            }
        }

        YoutubeDL.getInstance().execute(request)

        return findPreparedMp4(prefix)
            ?: throw IllegalStateException(
                "no se generó un MP4 válido con pista de video. Archivos generados: ${debugCachedFiles(prefix)}"
            )
    }

    private fun downloadStrategies(lowPower: Boolean): List<DownloadStrategy> {
        val maxHeight = if (lowPower) 360 else 480

        /*
         * Estrategia 1:
         * No fuerza recodificación. Primero intenta bajar algo ya compatible.
         * Usa selector amplio para evitar "Requested format is not available".
         */
        val directSelector =
            "bv*[height<=$maxHeight]+ba/" +
                    "b[height<=$maxHeight]/" +
                    "bv*+ba/" +
                    "b"

        val directSort =
            "res:$maxHeight,codec:avc:m4a,ext:mp4"

        /*
         * Estrategia 2:
         * Fuerza recodificación. Es más lenta, pero es el salvavidas para WebM/VP9/AV1.
         */
        val recodeSelector =
            "bv*[height<=$maxHeight]+ba/" +
                    "bv*+ba/" +
                    "b[height<=$maxHeight]/" +
                    "b"

        val recodeSort =
            "res:$maxHeight,codec:avc:m4a,ext:mp4"

        return listOf(
            DownloadStrategy(
                name = "Descarga directa compatible",
                selector = directSelector,
                sort = directSort,
                forceRecode = false
            ),
            DownloadStrategy(
                name = "Descarga universal recodificada",
                selector = recodeSelector,
                sort = recodeSort,
                forceRecode = true
            )
        )
    }

    /**
     * Actualiza yt-dlp una sola vez por ejecución de la fuente.
     *
     * Esto ayuda cuando la búsqueda funciona pero la descarga falla porque
     * el extractor de YouTube quedó viejo.
     */
    private fun updateYoutubeDlIfPossible() {
        if (updateAttempted) return
        updateAttempted = true

        try {
            YoutubeDL.getInstance().updateYoutubeDL(
                context,
                YoutubeDL.UpdateChannel.STABLE
            )
        } catch (_: Throwable) {
            /*
             * No detenemos la app si no se pudo actualizar.
             * La descarga todavía puede funcionar con el binario incluido.
             */
        }
    }

    private fun parseSearchOutput(
        output: String,
        originalQuery: String,
        maxResults: Int
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val trimmed = output.trim()

        if (trimmed.isBlank()) return emptyList()

        trimmed.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("{") && it.endsWith("}") }
            .forEach { line ->
                parseSingleVideoJson(line)?.let { result ->
                    results.add(result)
                }
            }

        if (results.isEmpty() && trimmed.startsWith("{")) {
            try {
                val root = JSONObject(trimmed)
                val entries = root.optJSONArray("entries")

                if (entries != null) {
                    for (i in 0 until entries.length()) {
                        val item = entries.optJSONObject(i) ?: continue
                        parseVideoObject(item)?.let { result ->
                            results.add(result)
                        }
                    }
                } else {
                    parseVideoObject(root)?.let { result ->
                        results.add(result)
                    }
                }
            } catch (_: Exception) {
                // Si no se puede parsear, regresamos vacío o fallback de URL.
            }
        }

        return results
            .distinctBy { it.id }
            .take(maxResults)
            .ifEmpty {
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

    private fun parseSingleVideoJson(line: String): SearchResult? {
        return try {
            parseVideoObject(JSONObject(line))
        } catch (_: Exception) {
            null
        }
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

        val duration = obj
            .optLong("duration", -1L)
            .takeIf { it >= 0 }
            ?.let { secondsToText(it) }
            .orEmpty()

        return SearchResult(
            id = finalUrl,
            title = title,
            source = "YouTube",
            verified = false,
            durationText = duration,
            playable = true
        )
    }

    private fun File.toTransferVideo(): TransferVideo {
        return TransferVideo(
            displayName = name,
            totalBytes = length(),
            mime = mimeFromExtension(name),
            inputStreamFactory = { inputStream() }
        )
    }

    private fun listCandidateFiles(prefix: String): List<File> {
        return downloadDir
            .listFiles()
            ?.filter { file ->
                file.isFile &&
                        file.name.startsWith(prefix) &&
                        file.length() > 0L
            }
            .orEmpty()
    }

    private fun findPreparedMp4(prefix: String): File? {
        return listCandidateFiles(prefix)
            .filter { file ->
                file.extension.equals("mp4", ignoreCase = true)
            }
            .sortedByDescending { file ->
                file.lastModified()
            }
            .firstOrNull { file ->
                file.hasVideoTrack()
            }
    }

    private fun debugCachedFiles(prefix: String): String {
        val files = listCandidateFiles(prefix)

        if (files.isEmpty()) return "ninguno"

        return files.joinToString { file ->
            "${file.name} (${file.length()} bytes, ${debugFileMetadata(file)})"
        }
    }

    private fun debugFileMetadata(file: File): String {
        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(file.absolutePath)

            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                .orEmpty()

            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                .orEmpty()

            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                .orEmpty()

            "metadata width=$width height=$height durationMs=$duration"
        } catch (e: Exception) {
            "metadata no legible: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignorar error al liberar.
            }
        }
    }

    private fun clearCachedFiles(prefix: String) {
        downloadDir
            .listFiles()
            ?.filter { file ->
                file.isFile && file.name.startsWith(prefix)
            }
            ?.forEach { file ->
                file.delete()
            }
    }

    private fun File.hasVideoTrack(): Boolean {
        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(absolutePath)

            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?: 0

            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?: 0

            width > 0 && height > 0
        } catch (_: Exception) {
            false
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignorar error al liberar.
            }
        }
    }

    private fun mimeFromExtension(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "3gp", "3gpp" -> "video/3gpp"
            else -> "video/mp4"
        }
    }

    private fun normalizeYoutubeIdOrUrl(value: String): String {
        val cleaned = value.trim()

        return if (looksLikeUrl(cleaned)) {
            cleaned
        } else {
            "https://www.youtube.com/watch?v=$cleaned"
        }
    }

    private fun safeFilePrefix(value: String): String {
        val encoded = URLEncoder.encode(value, "UTF-8")

        return "yt_" +
                encoded
                    .lowercase(Locale.US)
                    .replace(Regex("[^a-z0-9]+"), "_")
                    .trim('_')
                    .take(60)
    }

    private fun looksLikeUrl(value: String): Boolean {
        return value.startsWith("http://") || value.startsWith("https://")
    }

    private fun secondsToText(seconds: Long): String {
        val minutes = seconds / 60
        val remaining = seconds % 60

        return String.format(Locale.US, "%02d:%02d", minutes, remaining)
    }

    fun debugFormats(videoUrlOrId: String): String {
        val url = normalizeYoutubeIdOrUrl(videoUrlOrId)

        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("-F")
        }

        return YoutubeDL.getInstance().execute(request).out.orEmpty()
    }
}