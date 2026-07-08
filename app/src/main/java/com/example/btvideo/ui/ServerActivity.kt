package com.example.btvideo.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.btvideo.R
import com.example.btvideo.bluetooth.BluetoothConnection
import com.example.btvideo.bluetooth.FrameType
import com.example.btvideo.bluetooth.Protocol
import com.example.btvideo.data.TransferVideo
import com.example.btvideo.data.VideoCatalog
import com.example.btvideo.data.YoutubeExperimentalSource
import com.example.btvideo.model.SearchResult
import com.example.btvideo.model.VideoSourceMode
import com.example.btvideo.util.PermissionHelper
import com.example.btvideo.util.ThemePrefs
import org.json.JSONObject
import java.io.IOException

class ServerActivity : Activity(), BluetoothConnection.Listener {
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var cacheInfo: TextView
    private lateinit var connection: BluetoothConnection
    private lateinit var catalog: VideoCatalog
    private lateinit var youtubeExperimental: YoutubeExperimentalSource

    private val lastResults = mutableMapOf<String, SearchResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)
        PermissionHelper.request(this)

        status = findViewById(R.id.serverStatus)
        logView = findViewById(R.id.serverLog)
        cacheInfo = findViewById(R.id.cacheInfo)
        catalog = VideoCatalog(this)
        youtubeExperimental = YoutubeExperimentalSource(this)
        connection = BluetoothConnection(this, this)

        cacheInfo.text = "Caché local: assets/videos/*.mp4\nCaché YouTube experimental: cacheDir/youtube_experimental_cache"

        findViewById<Button>(R.id.startServerButton).setOnClickListener {
            status.text = "Estado: esperando cliente..."
            connection.startServer()
        }
    }

    override fun onConnected(role: String) = runOnUiThread {
        status.text = "Estado: conectado como $role"
        Toast.makeText(this, "Cliente conectado", Toast.LENGTH_SHORT).show()
    }

    override fun onDisconnected(reason: String) = runOnUiThread {
        status.text = "Estado: desconectado"
        appendLog(reason)
        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
    }

    override fun onFrame(type: FrameType, payload: ByteArray) {
        when (type) {
            FrameType.SEARCH_REQUEST -> handleSearch(payload)
            FrameType.PLAY_REQUEST -> handlePlay(payload)
            FrameType.PING -> connection.send(FrameType.PONG)
            else -> appendLog("Frame no esperado en servidor: $type")
        }
    }

    private fun handleSearch(payload: ByteArray) {
        val json = JSONObject(String(payload))
        val query = json.getString("query")
        val sourceMode = VideoSourceMode.fromWireName(json.optString("sourceMode", VideoSourceMode.LOCAL.wireName))
        appendLog("Búsqueda [$sourceMode]: $query")

        val results = try {
            when (sourceMode) {
                VideoSourceMode.LOCAL -> catalog.search(query)
                VideoSourceMode.YOUTUBE_EXPERIMENTAL -> youtubeExperimental.search(query)
            }
        } catch (e: Exception) {
            connection.send(FrameType.ERROR, Protocol.error("Error buscando en $sourceMode: ${e.message}"))
            return
        }

        lastResults.clear()
        results.forEach { lastResults[it.id] = it }
        connection.send(FrameType.SEARCH_RESPONSE, Protocol.searchResponse(results))
        appendLog("Resultados enviados: ${results.size}")
    }

    private fun handlePlay(payload: ByteArray) {
        val json = JSONObject(String(payload))
        val videoId = json.getString("videoId")
        val sourceMode = VideoSourceMode.fromWireName(json.optString("sourceMode", VideoSourceMode.LOCAL.wireName))
        val lowPower = json.optBoolean("lowPower", false)

        val result = when (sourceMode) {
            VideoSourceMode.LOCAL -> catalog.find(videoId)
            VideoSourceMode.YOUTUBE_EXPERIMENTAL -> lastResults[videoId] ?: SearchResult(
                id = videoId,
                title = videoId,
                source = "youtube-experimental",
                verified = false
            )
        }

        if (sourceMode == VideoSourceMode.YOUTUBE_EXPERIMENTAL) {
            appendLog("Aviso: usando modo YouTube experimental/no verificado. Puede tardar y puede fallar.")
        }

        val video = try {
            when (sourceMode) {
                VideoSourceMode.LOCAL -> catalog.openVideoAsset(videoId, lowPower)
                VideoSourceMode.YOUTUBE_EXPERIMENTAL -> youtubeExperimental.getVideo(videoId, lowPower)
            }
        } catch (e: Exception) {
            connection.send(FrameType.ERROR, Protocol.error("No se pudo preparar el video: ${e.message}"))
            return
        }

        if (result == null || video == null) {
            connection.send(FrameType.ERROR, Protocol.error("No existe o no se pudo abrir el video $videoId"))
            return
        }

        sendVideo(videoId = videoId, title = result.title, video = video)
    }

    private fun sendVideo(videoId: String, title: String, video: TransferVideo) {
        appendLog("Enviando: $title (${video.totalBytes} bytes)")
        connection.send(FrameType.VIDEO_META, Protocol.videoMeta(videoId, title, video.totalBytes, video.mime))

        try {
            video.inputStreamFactory().use { input ->
                val buffer = ByteArray(Protocol.CHUNK_SIZE)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    val exact = if (read == buffer.size) buffer else buffer.copyOf(read)
                    connection.send(FrameType.VIDEO_CHUNK, exact)
                }
            }
            connection.send(FrameType.VIDEO_END)
            appendLog("Transferencia completa: $title")
        } catch (e: IOException) {
            connection.send(FrameType.ERROR, Protocol.error("Error enviando video: ${e.message}"))
        }
    }

    override fun onLog(message: String) = appendLog(message)

    private fun appendLog(message: String) = runOnUiThread {
        logView.append("\n$message")
    }

    override fun onDestroy() {
        super.onDestroy()
        connection.close()
    }
}
